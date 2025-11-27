package com.todo.desktop.app;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import com.todo.desktop.data.firebase.FirebaseClientFactory;
import com.todo.desktop.data.graph.GraphMailClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;
import com.todo.desktop.data.graph.ClientCredentialAuthProvider;
import com.todo.desktop.data.repository.FirebaseAuthService;
import com.todo.desktop.data.repository.FirestoreDeadlineRepository;
import com.todo.desktop.data.repository.FirestoreTaskRepository;
import com.todo.desktop.data.repository.GraphEmailRepository;
import com.todo.desktop.data.repository.LocalAuthService;
import com.todo.desktop.data.repository.LocalDeadlineService;
import com.todo.desktop.data.repository.LocalEmailService;
import com.todo.desktop.data.repository.LocalTaskService;
import com.todo.desktop.data.service.FileTokenStorage;
import com.todo.desktop.data.service.OutlookServiceImpl;
import com.todo.desktop.data.service.TokenStorage;
import com.todo.desktop.domain.usecase.OutlookService;
import com.todo.desktop.domain.usecase.AuthService;
import com.todo.desktop.domain.usecase.DeadlineService;
import com.todo.desktop.domain.usecase.EmailService;
import com.todo.desktop.domain.usecase.TaskService;
import com.todo.desktop.ui.controller.DeadlineOverviewController;
import com.todo.desktop.ui.controller.InboxController;
import com.todo.desktop.ui.controller.LoginController;
import com.todo.desktop.ui.controller.MainShellController;
import com.todo.desktop.ui.controller.SettingsController;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javafx.util.Callback;

public final class AppModule implements Callback<Class<?>, Object> {

    private final AppConfig config;

    private final TaskService taskService;
    private final DeadlineService deadlineService;
    private final EmailService emailService;
    private final AuthService authService;
    private final OutlookService outlookService;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AppModule() {
        this.config = AppConfig.load();
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();

        AuthService resolvedAuthService;
        TaskService resolvedTaskService;
        DeadlineService resolvedDeadlineService;
        EmailService resolvedEmailService;

        Optional<FirebaseApp> firebaseApp = initializeFirebase(config);
        List<String> microsoftScopes = List.copyOf(config.microsoftScopes());
        Optional<String> microsoftClientId = config.microsoftClientId();
        Optional<String> microsoftClientSecret = config.microsoftClientSecret();
        Optional<URI> microsoftRedirectUri = config.microsoftRedirectUri();
        String microsoftAuthority = config.microsoftAuthority().orElse("https://login.microsoftonline.com/common");
        Optional<String> firebaseOAuthRequestUri = config.firebaseOAuthRequestUri();
        if (firebaseApp.isPresent()) {
            Firestore firestore = FirestoreClient.getFirestore(firebaseApp.get());
            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance(firebaseApp.get());
            resolvedAuthService = new FirebaseAuthService(
                    firebaseAuth,
                    httpClient,
                    objectMapper,
                    config.firebaseApiKey().orElse(null),
                    firebaseOAuthRequestUri.orElse(null),
                    microsoftClientId.orElse(null),
                    microsoftClientSecret.orElse(null),
                    microsoftRedirectUri.orElse(null),
                    microsoftAuthority,
                    microsoftScopes
            );
            resolvedTaskService = new FirestoreTaskRepository(firestore, resolvedAuthService);
            resolvedDeadlineService = new FirestoreDeadlineRepository(firestore, resolvedAuthService);
        } else {
            resolvedTaskService = new LocalTaskService();
            resolvedDeadlineService = new LocalDeadlineService();
            Optional<String> firebaseApiKey = config.firebaseApiKey();
            if (firebaseApiKey.isPresent()) {
                resolvedAuthService = new FirebaseAuthService(
                        null,
                        httpClient,
                        objectMapper,
                        firebaseApiKey.get(),
                        firebaseOAuthRequestUri.orElse(null),
                        microsoftClientId.orElse(null),
                        microsoftClientSecret.orElse(null),
                        microsoftRedirectUri.orElse(null),
                        microsoftAuthority,
                        microsoftScopes
                );
            } else {
                resolvedAuthService = new LocalAuthService(null);
            }
        }

        Optional<GraphMailClient> graphClient = initializeGraph(config);
        resolvedEmailService = graphClient
                .<EmailService>map(GraphEmailRepository::new)
                .orElseGet(LocalEmailService::new);

        this.taskService = resolvedTaskService;
        this.deadlineService = resolvedDeadlineService;
        this.authService = resolvedAuthService;
        this.emailService = resolvedEmailService;

        TokenStorage tokenStorage = new FileTokenStorage();
        this.outlookService = new OutlookServiceImpl(
                microsoftClientId.orElse("36892293-3eb2-460a-8061-e9ad79438b59"),
                tokenStorage
        );
    }

    public AuthService authService() {
        return authService;
    }

    public TaskService taskService() {
        return taskService;
    }

    public DeadlineService deadlineService() {
        return deadlineService;
    }

    public EmailService emailService() {
        return emailService;
    }

    private Optional<FirebaseApp> initializeFirebase(AppConfig config) {
        Optional<Path> serviceAccountPath = config.firebaseServiceAccountPath();
        Optional<String> bucket = config.firebaseStorageBucket();
        if (serviceAccountPath.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream input = Files.newInputStream(serviceAccountPath.get())) {
            FirebaseClientFactory factory = new FirebaseClientFactory();
            FirebaseApp app = factory.initializeIfNeeded(
                    input,
                    config.firebaseProjectId().orElse(null),
                    bucket.orElse(null)
            );
            return Optional.of(app);
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private Optional<GraphMailClient> initializeGraph(AppConfig config) {
        Optional<String> clientId = config.graphClientId();
        Optional<String> tenantId = config.graphTenantId();
        Optional<Path> clientSecretPath = config.graphClientSecretPath();
        if (clientId.isEmpty() || tenantId.isEmpty() || clientSecretPath.isEmpty()) {
            return Optional.empty();
        }

        try {
            String secret = Files.readString(clientSecretPath.get(), StandardCharsets.UTF_8).trim();
            if (secret.isEmpty()) {
                return Optional.empty();
            }

            ConfidentialClientApplication application = ConfidentialClientApplication
                    .builder(clientId.get(), ClientCredentialFactory.createFromSecret(secret))
                    .authority("https://login.microsoftonline.com/" + tenantId.get())
                    .build();

            Set<String> scopes = Set.copyOf(config.graphScopes());
            ClientCredentialAuthProvider authProvider = new ClientCredentialAuthProvider(application, scopes);

            GraphServiceClient<okhttp3.Request> graphServiceClient = GraphServiceClient
                    .builder()
                    .authenticationProvider(authProvider)
                    .buildClient();

            return Optional.of(new GraphMailClient(graphServiceClient));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public Object call(Class<?> type) {
        if (type == MainShellController.class) {
            MainShellController controller = new MainShellController();
            controller.setTaskService(taskService);
            controller.setDeadlineService(deadlineService);
            controller.setEmailService(emailService);
            controller.setAuthService(authService);
            controller.setOutlookService(outlookService);
            return controller;
        }
        if (type == DeadlineOverviewController.class) {
            DeadlineOverviewController controller = new DeadlineOverviewController();
            controller.setDeadlineService(deadlineService);
            controller.setTaskService(taskService);
            return controller;
        }
        if (type == InboxController.class) {
            InboxController controller = new InboxController();
            controller.setEmailService(emailService);
            controller.setOutlookService(outlookService);
            return controller;
        }
        if (type == SettingsController.class) {
            SettingsController controller = new SettingsController();
            controller.setAuthService(authService);
            controller.setOutlookService(outlookService);
            return controller;
        }
        if (type == LoginController.class) {
            LoginController controller = new LoginController();
            controller.setAuthService(authService);
            return controller;
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo controller: " + type.getName(), e);
        }
    }

}
