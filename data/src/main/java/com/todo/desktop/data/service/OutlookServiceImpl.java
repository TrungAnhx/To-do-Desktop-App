package com.todo.desktop.data.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.todo.desktop.domain.model.EmailAttachment;
import com.todo.desktop.domain.model.EmailMessage;
import com.todo.desktop.domain.usecase.OutlookService;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;

public final class OutlookServiceImpl implements OutlookService {

    private static final String AUTHORITY = "https://login.microsoftonline.com/common";
    private static final String TOKEN_ENDPOINT = AUTHORITY + "/oauth2/v2.0/token";
    private static final String AUTHORIZE_ENDPOINT = AUTHORITY + "/oauth2/v2.0/authorize";
    private static final String GRAPH_API_ENDPOINT = "https://graph.microsoft.com/v1.0";
    private static final String SCOPES = "openid profile email offline_access Mail.Send Mail.ReadWrite";
    private static final int CALLBACK_PORT = 8765;
    private static final String REDIRECT_URI = "http://127.0.0.1:" + CALLBACK_PORT + "/oauth2/callback";

    private final String clientId;
    private final TokenStorage tokenStorage;
    private final Gson gson = new Gson();

    public OutlookServiceImpl(String clientId, TokenStorage tokenStorage) {
        this.clientId = clientId;
        this.tokenStorage = tokenStorage;
    }

    @Override
    public CompletableFuture<Void> connectOutlook() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String codeVerifier = generateCodeVerifier();
                String codeChallenge = generateCodeChallenge(codeVerifier);
                String state = generateRandomString(16);

                String authUrl = buildAuthorizationUrl(codeChallenge, state);

                CountDownLatch latch = new CountDownLatch(1);
                String[] authCodeHolder = new String[1];
                String[] stateHolder = new String[1];

                HttpServer server = createCallbackServer(authCodeHolder, stateHolder, latch);
                server.start();

                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI(authUrl));
                }

                boolean completed = latch.await(5, TimeUnit.MINUTES);
                server.stop(0);

                if (!completed || authCodeHolder[0] == null) {
                    throw new IllegalStateException("Không nhận được mã xác thực từ Microsoft");
                }

                if (!state.equals(stateHolder[0])) {
                    throw new IllegalStateException("State không khớp - có thể bị tấn công CSRF");
                }

                exchangeCodeForTokens(authCodeHolder[0], codeVerifier);

                return null;
            } catch (Exception e) {
                throw new RuntimeException("Kết nối Outlook thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendEmail(String toEmail, String subject, String body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String accessToken = getValidAccessToken();

                JsonObject emailAddressObj = new JsonObject();
                emailAddressObj.addProperty("address", toEmail);

                JsonObject toRecipient = new JsonObject();
                toRecipient.add("emailAddress", emailAddressObj);

                com.google.gson.JsonArray toRecipientsArray = new com.google.gson.JsonArray();
                toRecipientsArray.add(toRecipient);

                JsonObject bodyContent = new JsonObject();
                bodyContent.addProperty("contentType", "Text");
                bodyContent.addProperty("content", body != null ? body : "");

                JsonObject messageContent = new JsonObject();
                String emailSubject = (subject == null || subject.isBlank()) ? "Email từ To-do Desktop App" : subject;
                messageContent.addProperty("subject", emailSubject);
                messageContent.add("body", bodyContent);
                messageContent.add("toRecipients", toRecipientsArray);

                JsonObject requestBody = new JsonObject();
                requestBody.add("message", messageContent);
                requestBody.addProperty("saveToSentItems", true);

                String jsonPayload = gson.toJson(requestBody);
                System.out.println("=== SEND EMAIL DEBUG ===");
                System.out.println("Payload: " + jsonPayload);

                URL url = new URL(GRAPH_API_ENDPOINT + "/me/sendMail");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                System.out.println("Response Code: " + responseCode);
                
                if (responseCode >= 400) {
                    String error = readErrorResponse(conn);
                    System.out.println("Error Response: " + error);
                    throw new IOException("Gửi email thất bại (HTTP " + responseCode + "): " + error);
                } else {
                    String response = readResponse(conn);
                    System.out.println("Success Response: " + response);
                }

                return null;
            } catch (Exception e) {
                throw new RuntimeException("Gửi email thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public boolean isConnected() {
        try {
            MicrosoftToken token = tokenStorage.loadToken();
            return token != null && !token.isExpired();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void disconnect() {
        tokenStorage.clearToken();
    }

    private String getValidAccessToken() throws Exception {
        MicrosoftToken token = tokenStorage.loadToken();
        if (token == null) {
            throw new IllegalStateException("Chưa kết nối với Outlook");
        }

        if (token.isExpired()) {
            token = refreshAccessToken(token.refreshToken());
            tokenStorage.saveToken(token);
        }

        return token.accessToken();
    }

    private MicrosoftToken refreshAccessToken(String refreshToken) throws Exception {
        String params = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                + "&grant_type=refresh_token"
                + "&scope=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8);

        URL url = new URL(TOKEN_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            String error = readErrorResponse(conn);
            throw new IOException("Làm mới token thất bại: " + error);
        }

        String response = readResponse(conn);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        return new MicrosoftToken(
                json.get("access_token").getAsString(),
                json.get("refresh_token").getAsString(),
                Instant.now().plusSeconds(json.get("expires_in").getAsLong())
        );
    }

    private void exchangeCodeForTokens(String authCode, String codeVerifier) throws Exception {
        String params = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&code=" + URLEncoder.encode(authCode, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code"
                + "&code_verifier=" + codeVerifier;

        URL url = new URL(TOKEN_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            String error = readErrorResponse(conn);
            throw new IOException("Đổi mã xác thực thất bại: " + error);
        }

        String response = readResponse(conn);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        MicrosoftToken token = new MicrosoftToken(
                json.get("access_token").getAsString(),
                json.get("refresh_token").getAsString(),
                Instant.now().plusSeconds(json.get("expires_in").getAsLong())
        );

        tokenStorage.saveToken(token);
    }

    private String buildAuthorizationUrl(String codeChallenge, String state) {
        try {
            return AUTHORIZE_ENDPOINT
                    + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8)
                    + "&code_challenge=" + codeChallenge
                    + "&code_challenge_method=S256"
                    + "&state=" + state
                    + "&response_mode=query";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpServer createCallbackServer(String[] authCodeHolder, String[] stateHolder, CountDownLatch latch) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", CALLBACK_PORT), 0);
        server.createContext("/oauth2/callback", exchange -> {
            try {
                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    String[] pairs = query.split("&");
                    for (String pair : pairs) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) {
                            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                            String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                            if ("code".equals(key)) {
                                authCodeHolder[0] = value;
                            } else if ("state".equals(key)) {
                                stateHolder[0] = value;
                            }
                        }
                    }
                }

                String response = "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
                        + "<body style='font-family: Arial, sans-serif; text-align: center; padding: 50px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white;'>"
                        + "<div style='background: white; color: #333; padding: 40px; border-radius: 16px; box-shadow: 0 10px 40px rgba(0,0,0,0.2); max-width: 500px; margin: 0 auto;'>"
                        + "<h1 style='color: #2563eb; margin: 0 0 20px 0;'>&#10004; Ket noi thanh cong!</h1>"
                        + "<p style='font-size: 16px; color: #666; line-height: 1.6;'>Ban co the dong cua so nay va quay lai ung dung.</p>"
                        + "<p style='font-size: 14px; color: #999; margin-top: 30px;'>Outlook da duoc ket noi voi To-do Desktop App</p>"
                        + "</div></body></html>";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
                        + "<body style='font-family: Arial, sans-serif; text-align: center; padding: 50px; background: #fee; color: #333;'>"
                        + "<div style='background: white; padding: 40px; border-radius: 16px; box-shadow: 0 10px 40px rgba(0,0,0,0.2); max-width: 500px; margin: 0 auto;'>"
                        + "<h1 style='color: #ef4444; margin: 0 0 20px 0;'>&#10008; Loi</h1>"
                        + "<p style='font-size: 14px; color: #666;'>" + (e.getMessage() != null ? e.getMessage() : "Unknown error") + "</p>"
                        + "</div></body></html>";
                exchange.sendResponseHeaders(500, errorResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            } finally {
                latch.countDown();
            }
        });
        return server;
    }

    private String generateCodeVerifier() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String codeVerifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String readErrorResponse(HttpURLConnection conn) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            try {
                return "HTTP " + conn.getResponseCode();
            } catch (IOException ioe) {
                return "Unknown error";
            }
        }
    }

    @Override
    public CompletableFuture<List<EmailMessage>> getInboxMessages(int top) {
        return getInboxMessages(top, 0);
    }
    
    @Override
    public CompletableFuture<List<EmailMessage>> getInboxMessages(int top, int skip) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String accessToken = getValidAccessToken();
                
                URL url = new URL(GRAPH_API_ENDPOINT + "/me/messages?$top=" + top + "&$skip=" + skip + "&$select=id,subject,from,toRecipients,bodyPreview,receivedDateTime,isRead,hasAttachments&$orderby=receivedDateTime desc&$count=true");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                
                int responseCode = conn.getResponseCode();
                System.out.println("DEBUG: Graph API response code: " + responseCode);
                if (responseCode >= 400) {
                    String errorResponse = readErrorResponse(conn);
                    System.out.println("DEBUG: Graph API error: " + errorResponse);
                    throw new IOException("Lấy email thất bại (HTTP " + responseCode + "): " + errorResponse);
                }
                
                String response = readResponse(conn);
                System.out.println("DEBUG: Graph API response length: " + response.length());
                JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
                JsonArray messages = jsonResponse.getAsJsonArray("value");
                System.out.println("DEBUG: Number of emails in response: " + messages.size());
                
                List<EmailMessage> emailList = new ArrayList<>();
                for (JsonElement msgElement : messages) {
                    JsonObject msg = msgElement.getAsJsonObject();
                    emailList.add(parseEmailMessage(msg, false));
                }
                
                return emailList;
            } catch (Exception e) {
                throw new RuntimeException("Lấy email thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<EmailMessage> getMessageById(String messageId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String accessToken = getValidAccessToken();
                
                URL url = new URL(GRAPH_API_ENDPOINT + "/me/messages/" + messageId + "?$expand=attachments");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                
                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    throw new IOException("Lấy chi tiết email thất bại (HTTP " + responseCode + "): " + readErrorResponse(conn));
                }
                
                String response = readResponse(conn);
                JsonObject msg = JsonParser.parseString(response).getAsJsonObject();
                
                return parseEmailMessage(msg, true);
            } catch (Exception e) {
                throw new RuntimeException("Lấy chi tiết email thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<byte[]> downloadAttachment(String messageId, String attachmentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String accessToken = getValidAccessToken();
                
                URL url = new URL(GRAPH_API_ENDPOINT + "/me/messages/" + messageId + "/attachments/" + attachmentId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                
                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    throw new IOException("Download attachment thất bại (HTTP " + responseCode + "): " + readErrorResponse(conn));
                }
                
                String response = readResponse(conn);
                JsonObject attachment = JsonParser.parseString(response).getAsJsonObject();
                String contentBytes = attachment.get("contentBytes").getAsString();
                
                return Base64.getDecoder().decode(contentBytes);
            } catch (Exception e) {
                throw new RuntimeException("Download attachment thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendEmailWithAttachments(String toEmail, String subject, String body, List<File> attachments) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String accessToken = getValidAccessToken();

                JsonObject emailAddressObj = new JsonObject();
                emailAddressObj.addProperty("address", toEmail);

                JsonObject toRecipient = new JsonObject();
                toRecipient.add("emailAddress", emailAddressObj);

                JsonArray toRecipientsArray = new JsonArray();
                toRecipientsArray.add(toRecipient);

                JsonObject bodyContent = new JsonObject();
                bodyContent.addProperty("contentType", "Text");
                bodyContent.addProperty("content", body != null ? body : "");

                JsonObject messageContent = new JsonObject();
                String emailSubject = (subject == null || subject.isBlank()) ? "Email từ To-do Desktop App" : subject;
                messageContent.addProperty("subject", emailSubject);
                messageContent.add("body", bodyContent);
                messageContent.add("toRecipients", toRecipientsArray);

                if (attachments != null && !attachments.isEmpty()) {
                    JsonArray attachmentsArray = new JsonArray();
                    for (File file : attachments) {
                        JsonObject attachment = new JsonObject();
                        attachment.addProperty("@odata.type", "#microsoft.graph.fileAttachment");
                        attachment.addProperty("name", file.getName());
                        attachment.addProperty("contentBytes", encodeFileToBase64(file));
                        attachmentsArray.add(attachment);
                    }
                    messageContent.add("attachments", attachmentsArray);
                }

                JsonObject requestBody = new JsonObject();
                requestBody.add("message", messageContent);
                requestBody.addProperty("saveToSentItems", true);

                String jsonPayload = gson.toJson(requestBody);

                URL url = new URL(GRAPH_API_ENDPOINT + "/me/sendMail");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                
                if (responseCode >= 400) {
                    String error = readErrorResponse(conn);
                    throw new IOException("Gửi email thất bại (HTTP " + responseCode + "): " + error);
                }

                return null;
            } catch (Exception e) {
                throw new RuntimeException("Gửi email thất bại: " + e.getMessage(), e);
            }
        });
    }

    private EmailMessage parseEmailMessage(JsonObject msg, boolean includeBody) {
        String id = msg.get("id").getAsString();
        String subject = msg.has("subject") && !msg.get("subject").isJsonNull() ? msg.get("subject").getAsString() : "(No subject)";
        
        JsonObject fromObj = msg.has("from") && !msg.get("from").isJsonNull() ? msg.getAsJsonObject("from") : null;
        String from = "";
        String fromEmail = "";
        if (fromObj != null && fromObj.has("emailAddress")) {
            JsonObject emailAddr = fromObj.getAsJsonObject("emailAddress");
            from = emailAddr.has("name") ? emailAddr.get("name").getAsString() : "";
            fromEmail = emailAddr.has("address") ? emailAddr.get("address").getAsString() : "";
        }
        
        List<String> toRecipients = new ArrayList<>();
        if (msg.has("toRecipients") && msg.get("toRecipients").isJsonArray()) {
            for (JsonElement recipientElem : msg.getAsJsonArray("toRecipients")) {
                JsonObject recipientObj = recipientElem.getAsJsonObject();
                if (recipientObj.has("emailAddress")) {
                    String addr = recipientObj.getAsJsonObject("emailAddress").get("address").getAsString();
                    toRecipients.add(addr);
                }
            }
        }
        
        String bodyPreview = msg.has("bodyPreview") ? msg.get("bodyPreview").getAsString() : "";
        String bodyContent = "";
        if (includeBody && msg.has("body") && !msg.get("body").isJsonNull()) {
            JsonObject bodyObj = msg.getAsJsonObject("body");
            bodyContent = bodyObj.has("content") ? bodyObj.get("content").getAsString() : "";
        }
        
        boolean isRead = msg.has("isRead") && msg.get("isRead").getAsBoolean();
        boolean hasAttachments = msg.has("hasAttachments") && msg.get("hasAttachments").getAsBoolean();
        
        Instant receivedDateTime = msg.has("receivedDateTime") ? Instant.parse(msg.get("receivedDateTime").getAsString()) : Instant.now();
        
        List<EmailAttachment> attachments = new ArrayList<>();
        if (includeBody && msg.has("attachments") && msg.get("attachments").isJsonArray()) {
            for (JsonElement attElem : msg.getAsJsonArray("attachments")) {
                JsonObject attObj = attElem.getAsJsonObject();
                String attId = attObj.get("id").getAsString();
                String name = attObj.has("name") ? attObj.get("name").getAsString() : "attachment";
                String contentType = attObj.has("contentType") ? attObj.get("contentType").getAsString() : "application/octet-stream";
                int size = attObj.has("size") ? attObj.get("size").getAsInt() : 0;
                boolean isInline = attObj.has("isInline") && attObj.get("isInline").getAsBoolean();
                
                attachments.add(new EmailAttachment(attId, name, contentType, size, isInline));
            }
        }
        
        return new EmailMessage(id, subject, from, fromEmail, toRecipients, bodyPreview, bodyContent, isRead, hasAttachments, receivedDateTime, attachments);
    }

    private String encodeFileToBase64(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = fis.readAllBytes();
            return Base64.getEncoder().encodeToString(bytes);
        }
    }
}
