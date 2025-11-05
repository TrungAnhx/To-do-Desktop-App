package com.todo.desktop.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Properties;

public final class AppConfig {

    private static final String CONFIG_RESOURCE = "/application.properties";

    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream input = AppConfig.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không thể đọc cấu hình ứng dụng", e);
        }
        return new AppConfig(props);
    }

    public Optional<Path> firebaseServiceAccountPath() {
        return readPath("firebase.serviceAccount");
    }

    public Optional<String> firebaseStorageBucket() {
        return readString("firebase.storageBucket");
    }

    public Optional<String> firebaseProjectId() {
        return readString("firebase.projectId");
    }

    public Optional<String> firebaseApiKey() {
        return readString("firebase.apiKey");
    }

    public Optional<String> firebaseOAuthRequestUri() {
        return readString("firebase.oauthRequestUri");
    }

    public Optional<String> graphClientId() {
        return readString("graph.clientId");
    }

    public Optional<String> graphTenantId() {
        return readString("graph.tenantId");
    }

    public Optional<Path> graphClientSecretPath() {
        return readPath("graph.clientSecretPath");
    }

    public List<String> graphScopes() {
        return readString("graph.scopes")
                .map(raw -> Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(token -> !token.isEmpty())
                        .collect(Collectors.toList()))
                .filter(list -> !list.isEmpty())
                .orElseGet(() -> List.of("https://graph.microsoft.com/.default"));
    }

    public Optional<String> microsoftClientId() {
        return readString("microsoft.clientId");
    }

    public Optional<String> microsoftClientSecret() {
        return readString("microsoft.clientSecret");
    }

    public Optional<URI> microsoftRedirectUri() {
        return readUri("microsoft.redirectUri");
    }

    public List<String> microsoftScopes() {
        return readString("microsoft.scopes")
                .map(raw -> Arrays.stream(raw.split("\\s+"))
                        .map(String::trim)
                        .filter(token -> !token.isEmpty())
                        .collect(Collectors.toList()))
                .filter(list -> !list.isEmpty())
                .orElseGet(() -> List.of("openid", "profile", "email", "offline_access"));
    }

    public Optional<String> microsoftAuthority() {
        return readString("microsoft.authority");
    }

    private Optional<String> readString(String key) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(raw.trim());
    }

    private Optional<Path> readPath(String key) {
        return readString(key).map(Path::of).filter(Files::exists);
    }

    private Optional<URI> readUri(String key) {
        return readString(key).map(String::trim).filter(value -> !value.isBlank()).map(URI::create);
    }
}
