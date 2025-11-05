package com.todo.desktop.data.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.sun.net.httpserver.HttpServer;
import com.todo.desktop.domain.model.UserProfile;
import com.todo.desktop.domain.usecase.AuthService;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FirebaseAuthService implements AuthService {

    private static final URI SIGN_IN_WITH_PASSWORD_ENDPOINT = URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword");
    private static final URI SIGN_UP_ENDPOINT = URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signUp");
    private static final URI SIGN_IN_WITH_IDP_ENDPOINT = URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final FirebaseAuth firebaseAuth;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String firebaseOAuthRequestUri;
    private final String microsoftClientId;
    private final String microsoftClientSecret;
    private final URI microsoftRedirectUri;
    private final URI microsoftAuthorizeEndpoint;
    private final URI microsoftTokenEndpoint;
    private final List<String> microsoftScopes;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private volatile UserProfile current;

    public FirebaseAuthService(
            FirebaseAuth firebaseAuth,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String apiKey,
            String firebaseOAuthRequestUri,
            String microsoftClientId,
            String microsoftClientSecret,
            URI microsoftRedirectUri,
            String microsoftAuthority,
            List<String> microsoftScopes
    ) {
        this.firebaseAuth = firebaseAuth;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.apiKey = apiKey;
        this.firebaseOAuthRequestUri = firebaseOAuthRequestUri;
        this.microsoftClientId = normalize(microsoftClientId);
        this.microsoftClientSecret = normalize(microsoftClientSecret);
        this.microsoftRedirectUri = microsoftRedirectUri;
        String authority = normalizeAuthority(microsoftAuthority);
        this.microsoftAuthorizeEndpoint = authority != null ? URI.create(authority + "/oauth2/v2.0/authorize") : null;
        this.microsoftTokenEndpoint = authority != null ? URI.create(authority + "/oauth2/v2.0/token") : null;
        this.microsoftScopes = microsoftScopes == null ? List.of() : List.copyOf(microsoftScopes);
    }

    @Override
    public Optional<UserProfile> currentUser() {
        return Optional.ofNullable(current);
    }

    @Override
    public CompletableFuture<UserProfile> signInWithPassword(String email, String password) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Thiếu Firebase API key trong cấu hình"));
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "email", email,
                    "password", password,
                    "returnSecureToken", true
            ));

            URI requestUri = URI.create(SIGN_IN_WITH_PASSWORD_ENDPOINT.toString() + "?key=" + apiKey);
            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApplyAsync(response -> {
                        if (response.statusCode() >= 400) {
                            throw new IllegalStateException("Đăng nhập Firebase thất bại: " + response.body());
                        }
                        try {
                            JsonNode node = objectMapper.readTree(response.body());
                            String uid = node.path("localId").asText();
                            String displayName = node.path("displayName").asText(null);
                            String emailValue = node.path("email").asText(email);
                            String photoUrl = node.path("photoUrl").asText(null);
                            UserProfile profile = new UserProfile(uid, displayName, emailValue, photoUrl != null && !photoUrl.isBlank() ? URI.create(photoUrl) : null);
                            this.current = profile;
                            return profile;
                        } catch (Exception e) {
                            throw new RuntimeException("Không thể phân tích phản hồi đăng nhập Firebase", e);
                        }
                    }, executor);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<UserProfile> register(String email, String password, String displayName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (firebaseAuth != null) {
                    UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                            .setEmail(email)
                            .setPassword(password)
                            .setDisplayName(displayName);
                    UserRecord record = firebaseAuth.createUser(request);
                    UserProfile profile = new UserProfile(record.getUid(), record.getDisplayName(), record.getEmail(), record.getPhotoUrl() != null ? URI.create(record.getPhotoUrl()) : null);
                    current = profile;
                    return profile;
                }
                return registerWithRest(email, password, displayName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private UserProfile registerWithRest(String email, String password, String displayName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Thiếu Firebase API key trong cấu hình");
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("displayName", displayName);
        payload.put("returnSecureToken", Boolean.TRUE);

        try {
            String json = objectMapper.writeValueAsString(payload);
            URI requestUri = URI.create(SIGN_UP_ENDPOINT.toString() + "?key=" + apiKey);
            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Đăng ký Firebase thất bại: " + response.body());
            }
            JsonNode node = objectMapper.readTree(response.body());
            String uid = node.path("localId").asText();
            String emailValue = node.path("email").asText(email);
            String nameValue = node.path("displayName").asText(displayName);
            UserProfile profile = new UserProfile(uid, nameValue, emailValue, null);
            current = profile;
            return profile;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Không thể đăng ký Firebase", e);
        }
    }



    @Override
    public CompletableFuture<Void> signOut() {
        current = null;
        return CompletableFuture.completedFuture(null);
    }

    private boolean isMicrosoftConfigured() {
        return microsoftClientId != null
                && microsoftRedirectUri != null
                && microsoftAuthorizeEndpoint != null
                && microsoftTokenEndpoint != null;
    }

    private HttpServer createCallbackServer(CompletableFuture<Map<String, String>> callbackFuture) throws IOException {
        String host = microsoftRedirectUri.getHost();
        int port = microsoftRedirectUri.getPort() == -1 ? 80 : microsoftRedirectUri.getPort();
        String path = microsoftRedirectUri.getPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        String finalPath = path;
        server.createContext(finalPath, exchange -> {
            try {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                if (!callbackFuture.isDone()) {
                    if (params.containsKey("error")) {
                        String message = params.getOrDefault("error_description", params.get("error"));
                        callbackFuture.completeExceptionally(new IllegalStateException("Microsoft trả về lỗi: " + message));
                    } else {
                        callbackFuture.complete(params);
                    }
                }
                String html = params.containsKey("error")
                        ? "<html><body>Đăng nhập Microsoft thất bại. Bạn có thể đóng cửa sổ này.</body></html>"
                        : "<html><body>Đăng nhập thành công. Bạn có thể đóng cửa sổ này.</body></html>";
                byte[] body = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private URI buildAuthorizationUri(String state, String codeChallenge, String nonce) {
        java.util.LinkedHashMap<String, String> params = new java.util.LinkedHashMap<>();
        params.put("client_id", microsoftClientId);
        params.put("response_type", "code");
        params.put("redirect_uri", microsoftRedirectUri.toString());
        params.put("response_mode", "query");
        params.put("scope", scopesAsString());
        params.put("state", state);
        params.put("prompt", "select_account");
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");
        params.put("nonce", nonce);
        return URI.create(microsoftAuthorizeEndpoint.toString() + "?" + buildQuery(params));
    }

    private Map<String, String> waitForCallback(CompletableFuture<Map<String, String>> callbackFuture) throws InterruptedException, TimeoutException {
        try {
            return callbackFuture.get(180, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Không thể hoàn tất đăng nhập Microsoft", cause);
        }
    }

    private OAuthTokens exchangeAuthorizationCode(String code, String codeVerifier) throws IOException, InterruptedException {
        Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("client_id", microsoftClientId);
        body.put("scope", scopesAsString());
        body.put("code", code);
        body.put("redirect_uri", microsoftRedirectUri.toString());
        body.put("grant_type", "authorization_code");
        if (microsoftClientSecret != null && !microsoftClientSecret.isBlank()) {
            body.put("client_secret", microsoftClientSecret);
        }
        body.put("code_verifier", codeVerifier);

        HttpRequest request = HttpRequest.newBuilder(microsoftTokenEndpoint)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(buildQuery(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Trao đổi mã xác thực thất bại: " + response.body());
        }

        JsonNode node = objectMapper.readTree(response.body());
        String idToken = node.path("id_token").asText(null);
        String accessToken = node.path("access_token").asText(null);
        if ((idToken == null || idToken.isBlank()) && (accessToken == null || accessToken.isBlank())) {
            throw new IllegalStateException("Microsoft không trả về token hợp lệ");
        }
        String refreshToken = node.path("refresh_token").asText(null);
        return new OAuthTokens(idToken, accessToken, refreshToken, objectMapper);
    }

    private UserProfile signInWithFirebase(OAuthTokens tokens, String nonce) throws IOException, InterruptedException {
        StringBuilder postBody = new StringBuilder();
        appendPostBody(postBody, "providerId", "microsoft.com");
        if (tokens.idToken() != null) {
            appendPostBody(postBody, "id_token", tokens.idToken());
        }
        if (tokens.accessToken() != null) {
            appendPostBody(postBody, "access_token", tokens.accessToken());
        }
        if (nonce != null && !nonce.isBlank()) {
            appendPostBody(postBody, "nonce", nonce);
        }

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("postBody", postBody.toString());
        String requestUriValue = firebaseOAuthRequestUri != null && !firebaseOAuthRequestUri.isBlank()
                ? firebaseOAuthRequestUri
                : "http://127.0.0.1:8765/oauth2/callback";
        payload.put("requestUri", requestUriValue);
        payload.put("returnSecureToken", Boolean.TRUE);
        payload.put("returnIdpCredential", Boolean.TRUE);

        String jsonPayload = objectMapper.writeValueAsString(payload);
        URI requestUri = URI.create(SIGN_IN_WITH_IDP_ENDPOINT.toString() + "?key=" + apiKey);
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "Đăng nhập Firebase qua Microsoft thất bại: " + response.body() + "\nPayload: " + jsonPayload);
        }

        JsonNode node = objectMapper.readTree(response.body());
        String uid = node.path("localId").asText();
        String displayName = node.path("displayName").asText(null);
        String email = node.path("email").asText(null);
        String photoUrl = node.path("photoUrl").asText(null);
        if ((email == null || email.isBlank()) && node.path("providerUserInfo").isArray()) {
            for (JsonNode info : node.path("providerUserInfo")) {
                String candidate = info.path("email").asText(null);
                if (candidate != null && !candidate.isBlank()) {
                    email = candidate;
                    break;
                }
            }
        }
        if (email == null || email.isBlank()) {
            email = tokens.emailFromIdToken();
        }
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Không xác định được email người dùng từ Microsoft");
        }
        URI avatar = photoUrl != null && !photoUrl.isBlank() ? URI.create(photoUrl) : null;
        return new UserProfile(uid, displayName, email, avatar);
    }

    private static void appendPostBody(StringBuilder builder, String key, String value) {
        if (value == null) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('&');
        }
        builder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
        builder.append('=');
        builder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private String scopesAsString() {
        List<String> scopes = microsoftScopes.isEmpty()
                ? List.of("openid", "profile", "email", "offline_access")
                : microsoftScopes;
        return String.join(" ", scopes);
    }

    private static String buildQuery(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            } else {
                first = false;
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private static Map<String, String> parseQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        String[] pairs = raw.split("&");
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (String pair : pairs) {
            String[] tokens = pair.split("=", 2);
            String key = URLDecoder.decode(tokens[0], StandardCharsets.UTF_8);
            String value = tokens.length > 1 ? URLDecoder.decode(tokens[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private String generateRandomString(int length) {
        byte[] buffer = new byte[length];
        SECURE_RANDOM.nextBytes(buffer);
        return base64Url(buffer).substring(0, Math.min(length, buffer.length * 2));
    }

    private String generateCodeVerifier() {
        byte[] buffer = new byte[32];
        SECURE_RANDOM.nextBytes(buffer);
        return base64Url(buffer);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return base64Url(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo code challenge", e);
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] decodeBase64Url(String value) {
        int padding = (4 - value.length() % 4) % 4;
        StringBuilder sb = new StringBuilder(value);
        for (int i = 0; i < padding; i++) {
            sb.append('=');
        }
        return Base64.getUrlDecoder().decode(sb.toString());
    }

    private record OAuthTokens(String idToken, String accessToken, String refreshToken, ObjectMapper mapper) {
        String emailFromIdToken() {
            if (idToken == null || idToken.isBlank()) {
                return null;
            }
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            try {
                byte[] decoded = decodeBase64Url(parts[1]);
                JsonNode node = mapper.readTree(decoded);
                String email = node.path("email").asText(null);
                if (email != null && !email.isBlank()) {
                    return email;
                }
                return node.path("preferred_username").asText(null);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private boolean openBrowser(URI uri) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(uri);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder builder;
        if (os.contains("win")) {
            builder = new ProcessBuilder("cmd", "/c", "start", "", uri.toString());
        } else if (os.contains("mac")) {
            builder = new ProcessBuilder("open", uri.toString());
        } else if (os.contains("nux") || os.contains("nix")) {
            builder = new ProcessBuilder("xdg-open", uri.toString());
        } else {
            return false;
        }
        try {
            builder.start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeAuthority(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

}
