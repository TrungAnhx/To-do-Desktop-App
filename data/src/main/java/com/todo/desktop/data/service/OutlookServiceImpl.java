package com.todo.desktop.data.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.UserSendMailParameterSet;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import com.todo.desktop.domain.model.EmailAttachment;
import com.todo.desktop.domain.model.EmailMessage;
import com.todo.desktop.domain.usecase.OutlookService;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import okhttp3.Request;

public final class OutlookServiceImpl implements OutlookService {

    private static final String AUTHORITY = "https://login.microsoftonline.com/common";
    private static final String TOKEN_ENDPOINT = AUTHORITY + "/oauth2/v2.0/token";
    private static final String AUTHORIZE_ENDPOINT = AUTHORITY + "/oauth2/v2.0/authorize";
    // GRAPH_API_ENDPOINT is handled by the SDK
    private static final String SCOPES = "openid profile email offline_access Mail.Send Mail.ReadWrite Mail.Read";
    private static final int CALLBACK_PORT = 8765;
    private static final String REDIRECT_URI = "http://127.0.0.1:" + CALLBACK_PORT + "/oauth2/callback";

    private final String clientId;
    private final TokenStorage tokenStorage;
    
    // We still use Gson for the manual token exchange parts
    private final Gson gson = new Gson();

    public OutlookServiceImpl(String clientId, TokenStorage tokenStorage) {
        this.clientId = clientId;
        this.tokenStorage = tokenStorage;
    }

    private GraphServiceClient<Request> getGraphClient() {
        return GraphServiceClient.builder()
                .authenticationProvider(new IAuthenticationProvider() {
                    @Override
                    public CompletableFuture<String> getAuthorizationTokenAsync(URL requestUrl) {
                        return CompletableFuture.supplyAsync(() -> {
                            try {
                                return getValidAccessToken();
                            } catch (Exception e) {
                                throw new RuntimeException("Unable to get access token", e);
                            }
                        });
                    }
                })
                .buildClient();
    }

    @Override
    public CompletableFuture<Void> connectOutlook() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("DEBUG: Starting Outlook connection process...");
                String codeVerifier = generateCodeVerifier();
                String codeChallenge = generateCodeChallenge(codeVerifier);
                String state = generateRandomString(16);

                String authUrl = buildAuthorizationUrl(codeChallenge, state);
                System.out.println("DEBUG: Auth URL built successfully");

                CountDownLatch latch = new CountDownLatch(1);
                String[] authCodeHolder = new String[1];
                String[] stateHolder = new String[1];

                HttpServer server = createCallbackServer(authCodeHolder, stateHolder, latch);
                server.start();
                System.out.println("DEBUG: Callback server started on port " + CALLBACK_PORT);

                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI(authUrl));
                    System.out.println("DEBUG: Browser opened with auth URL");
                }

                boolean completed = latch.await(5, TimeUnit.MINUTES);
                server.stop(0);

                if (!completed || authCodeHolder[0] == null) {
                    throw new IllegalStateException("Không nhận được mã xác thực từ Microsoft");
                }

                if (!state.equals(stateHolder[0])) {
                    throw new IllegalStateException("State không khớp - có thể bị tấn công CSRF");
                }

                System.out.println("DEBUG: Received auth code, exchanging for tokens...");
                exchangeCodeForTokens(authCodeHolder[0], codeVerifier);
                System.out.println("DEBUG: Tokens exchanged and saved successfully");

                return null;
            } catch (Exception e) {
                System.out.println("DEBUG: Outlook connection failed: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Kết nối Outlook thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendEmail(String toEmail, String subject, String body) {
        return sendEmailWithAttachments(toEmail, subject, body, Collections.emptyList());
    }

    @Override
    public CompletableFuture<Void> sendEmailWithAttachments(String toEmail, String subject, String body, List<File> attachments) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Message message = new Message();
                message.subject = (subject == null || subject.isBlank()) ? "Email từ To-do Desktop App" : subject;
                
                ItemBody bodyContent = new ItemBody();
                bodyContent.contentType = BodyType.TEXT;
                bodyContent.content = body != null ? body : "";
                message.body = bodyContent;
                
                LinkedList<Recipient> toRecipientsList = new LinkedList<>();
                Recipient toRecipient = new Recipient();
                EmailAddress emailAddress = new EmailAddress();
                emailAddress.address = toEmail;
                toRecipient.emailAddress = emailAddress;
                toRecipientsList.add(toRecipient);
                message.toRecipients = toRecipientsList;
                
                if (attachments != null && !attachments.isEmpty()) {
                    com.microsoft.graph.requests.AttachmentCollectionResponse attachmentCollectionResponse = new com.microsoft.graph.requests.AttachmentCollectionResponse();
                    attachmentCollectionResponse.value = new ArrayList<>();
                    
                    for (File file : attachments) {
                        FileAttachment attachment = new FileAttachment();
                        attachment.name = file.getName();
                        attachment.contentBytes = readFileToBytes(file);
                        attachment.oDataType = "#microsoft.graph.fileAttachment";
                        attachmentCollectionResponse.value.add(attachment);
                    }
                    // Fix: Wrap response in Page
                    message.attachments = new com.microsoft.graph.requests.AttachmentCollectionPage(attachmentCollectionResponse, null);
                }

                boolean saveToSentItems = true;

                getGraphClient().me()
                        .sendMail(UserSendMailParameterSet.newBuilder()
                                .withMessage(message)
                                .withSaveToSentItems(saveToSentItems)
                                .build())
                        .buildRequest()
                        .post();

                System.out.println("DEBUG: Email sent successfully");
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

    // ... Helper methods for Auth flow (createCallbackServer, etc.) remain similar but streamlined ...
    
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
                        + "</div></body></html>";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "Error";
                exchange.sendResponseHeaders(500, errorResponse.length());
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
                MessageCollectionPage messages = getGraphClient().me().messages()
                    .buildRequest()
                    .top(top)
                    .skip(skip)
                    .select("id,subject,from,toRecipients,bodyPreview,isRead,hasAttachments,receivedDateTime")
                    .get();

                List<EmailMessage> emailList = new ArrayList<>();
                if (messages != null && messages.getCurrentPage() != null) {
                    for (Message msg : messages.getCurrentPage()) {
                        emailList.add(convertSdkMessageToDomain(msg, false));
                    }
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
                Message msg = getGraphClient().me().messages(messageId)
                    .buildRequest()
                    .expand("attachments")
                    .get();
                
                return convertSdkMessageToDomain(msg, true);
            } catch (Exception e) {
                throw new RuntimeException("Lấy chi tiết email thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<byte[]> downloadAttachment(String messageId, String attachmentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                com.microsoft.graph.models.Attachment attachment = getGraphClient().me().messages(messageId)
                    .attachments(attachmentId)
                    .buildRequest()
                    .get();

                if (attachment instanceof FileAttachment) {
                    FileAttachment fileAttachment = (FileAttachment) attachment;
                    if (fileAttachment.contentBytes != null) {
                        return fileAttachment.contentBytes;
                    }
                }
                return new byte[0];
            } catch (Exception e) {
                throw new RuntimeException("Download attachment thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteMessage(String messageId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                getGraphClient().me().messages(messageId).buildRequest().delete();
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Xóa email thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> reply(String messageId, String comment) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Message replyMessage = new Message(); // Can be null if we just want to reply
                // If we have a comment, we might need to construct a reply object. 
                // The SDK createReply method takes a parameter parameterSet? No, it usually returns a request builder.
                // Let's check standard usage. 
                // client.me().messages(id).reply(parameterSet).buildRequest().post();
                
                // For simple reply with comment:
                com.microsoft.graph.models.MessageReplyParameterSet paramSet = 
                    com.microsoft.graph.models.MessageReplyParameterSet.newBuilder()
                    .withComment(comment)
                    .build();

                getGraphClient().me().messages(messageId)
                    .reply(paramSet)
                    .buildRequest()
                    .post();
                    
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Trả lời email thất bại: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> forward(String messageId, String toEmail, String comment) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LinkedList<Recipient> recipients = new LinkedList<>();
                Recipient recipient = new Recipient();
                EmailAddress emailAddress = new EmailAddress();
                emailAddress.address = toEmail;
                recipient.emailAddress = emailAddress;
                recipients.add(recipient);

                com.microsoft.graph.models.MessageForwardParameterSet paramSet = 
                    com.microsoft.graph.models.MessageForwardParameterSet.newBuilder()
                    .withToRecipients(recipients)
                    .withComment(comment)
                    .build();

                getGraphClient().me().messages(messageId)
                    .forward(paramSet)
                    .buildRequest()
                    .post();

                return null;
            } catch (Exception e) {
                throw new RuntimeException("Chuyển tiếp email thất bại: " + e.getMessage(), e);
            }
        });
    }

    private EmailMessage convertSdkMessageToDomain(Message msg, boolean includeBody) {
        String id = msg.id;
        String subject = msg.subject != null ? msg.subject : "(No subject)";
        
        String from = "";
        String fromEmail = "";
        if (msg.from != null && msg.from.emailAddress != null) {
            from = msg.from.emailAddress.name;
            fromEmail = msg.from.emailAddress.address;
        }
        
        List<String> toRecipients = new ArrayList<>();
        if (msg.toRecipients != null) {
            for (Recipient recipient : msg.toRecipients) {
                if (recipient.emailAddress != null) {
                    toRecipients.add(recipient.emailAddress.address);
                }
            }
        }
        
        String bodyPreview = msg.bodyPreview;
        String bodyContent = "";
        if (includeBody && msg.body != null) {
            bodyContent = msg.body.content;
        }
        
        boolean isRead = Boolean.TRUE.equals(msg.isRead);
        boolean hasAttachments = Boolean.TRUE.equals(msg.hasAttachments);
        
        Instant receivedDateTime = Instant.now();
        if (msg.receivedDateTime != null) {
            // The SDK usually returns OffsetDateTime or similar, but here it might be different depending on version
            // We'll check the type. In v5.57.0 it's usually OffsetDateTime.
            // However, we are safe to use toString and parse or get toInstant if available.
            // Let's assume it maps to java.time.OffsetDateTime
            receivedDateTime = msg.receivedDateTime.toInstant();
        }
        
        List<EmailAttachment> attachments = new ArrayList<>();
        if (includeBody && msg.attachments != null && msg.attachments.getCurrentPage() != null) {
            for (com.microsoft.graph.models.Attachment att : msg.attachments.getCurrentPage()) {
                String attId = att.id;
                String name = att.name;
                String contentType = att.contentType;
                int size = att.size != null ? att.size : 0;
                boolean isInline = Boolean.TRUE.equals(att.isInline);
                
                attachments.add(new EmailAttachment(attId, name, contentType, size, isInline));
            }
        }
        
        return new EmailMessage(id, subject, from, fromEmail, toRecipients, bodyPreview, bodyContent, isRead, hasAttachments, receivedDateTime, attachments);
    }

    private byte[] readFileToBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }
}
