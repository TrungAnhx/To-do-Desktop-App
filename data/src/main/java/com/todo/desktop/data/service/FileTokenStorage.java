package com.todo.desktop.data.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public final class FileTokenStorage implements TokenStorage {

    private static final String TOKEN_FILE = "outlook_token.json";
    private final Path tokenPath;
    private final Gson gson;

    public FileTokenStorage() {
        String userHome = System.getProperty("user.home");
        Path appDir = Paths.get(userHome, ".todo-desktop");
        try {
            Files.createDirectories(appDir);
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thư mục config", e);
        }
        this.tokenPath = appDir.resolve(TOKEN_FILE);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();
    }

    @Override
    public void saveToken(MicrosoftToken token) {
        try {
            String json = gson.toJson(token);
            System.out.println("DEBUG: Saving token to file: " + tokenPath);
            System.out.println("DEBUG: Token expires at: " + token.expiresAt());
            Files.writeString(tokenPath, json);
            System.out.println("DEBUG: Token saved successfully");
        } catch (IOException e) {
            System.out.println("DEBUG: Failed to save token: " + e.getMessage());
            throw new RuntimeException("Không thể lưu token", e);
        }
    }

    @Override
    public MicrosoftToken loadToken() {
        try {
            if (!Files.exists(tokenPath)) {
                return null;
            }
            String json = Files.readString(tokenPath);
            return gson.fromJson(json, MicrosoftToken.class);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void clearToken() {
        try {
            Files.deleteIfExists(tokenPath);
        } catch (IOException e) {
            // Ignore
        }
    }

    private static class InstantTypeAdapter extends com.google.gson.TypeAdapter<Instant> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }
}
