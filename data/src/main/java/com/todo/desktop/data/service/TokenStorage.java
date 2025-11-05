package com.todo.desktop.data.service;

public interface TokenStorage {
    void saveToken(MicrosoftToken token);
    MicrosoftToken loadToken();
    void clearToken();
}
