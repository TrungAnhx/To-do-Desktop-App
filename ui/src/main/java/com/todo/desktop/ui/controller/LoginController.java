package com.todo.desktop.ui.controller;

import com.todo.desktop.domain.model.UserProfile;
import com.todo.desktop.domain.usecase.AuthService;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class LoginController {

    private AuthService authService;
    private Consumer<UserProfile> onLoginSuccess = profile -> { };

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button signInButton;

    @FXML
    private Button registerButton;

    @FXML
    private Hyperlink forgotPasswordLink;

    @FXML
    private Label statusLabel;

    @FXML
    private javafx.scene.layout.VBox loginFormContainer;

    @FXML
    private javafx.scene.layout.Region focusDummy;

    @FXML
    private javafx.scene.layout.BorderPane loginRoot;

    @FXML
    private void initialize() {
        // Enter key để đăng nhập (thay thế click nút)
        if (emailField != null) {
            emailField.setOnAction(e -> {
                passwordField.requestFocus();
                // Automatically go to password field when Enter is pressed in email field
                javafx.application.Platform.runLater(() -> passwordField.selectAll());
            });
        }
        if (passwordField != null) {
            passwordField.setOnAction(e -> onSignIn());
        }
        
        // Set up tab navigation between fields
        if (emailField != null) {
            emailField.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.TAB) {
                    if (!event.isShiftDown()) {
                        // Tab forward: move to password field
                        passwordField.requestFocus();
                        javafx.application.Platform.runLater(() -> passwordField.selectAll());
                        event.consume();
                    }
                }
            });
        }
        
        if (passwordField != null) {
            passwordField.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.TAB) {
                    if (event.isShiftDown()) {
                        // Shift+Tab back: move to email field
                        emailField.requestFocus();
                        javafx.application.Platform.runLater(() -> emailField.selectAll());
                        event.consume();
                    }
                }
            });
        }
        
        // Add focus styling for better UX
        addFocusListeners();
        
        // Focus vào dummy node ban đầu
        if (focusDummy != null) {
            javafx.application.Platform.runLater(() -> {
                focusDummy.requestFocus();
            });
        }
    }

    private void addFocusListeners() {
        if (emailField != null) {
            emailField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    emailField.setStyle(emailField.getStyle() + "; -fx-border-color: #3b82f6; -fx-background-color: #ffffff;");
                } else {
                    emailField.setStyle("-fx-background-radius: 14; -fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 14; -fx-padding: 0 18; -fx-font-size: 15px; -fx-prompt-text-fill: #9ca3af; -fx-text-fill: #111827; -fx-border-width: 2;");
                }
            });
        }
        
        if (passwordField != null) {
            passwordField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    passwordField.setStyle(passwordField.getStyle() + "; -fx-border-color: #3b82f6; -fx-background-color: #ffffff;");
                } else {
                    passwordField.setStyle("-fx-background-radius: 14; -fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 14; -fx-padding: 0 18; -fx-font-size: 15px; -fx-prompt-text-fill: #9ca3af; -fx-text-fill: #111827; -fx-border-width: 2;");
                }
            });
        }
    }

    public void setAuthService(AuthService authService) {
        this.authService = Objects.requireNonNull(authService);
    }

    public void setOnLoginSuccess(Consumer<UserProfile> onLoginSuccess) {
        this.onLoginSuccess = Objects.requireNonNull(onLoginSuccess);
    }

    @FXML
    private void onSignIn() {
        if (authService == null) {
            return;
        }
        String email = emailField.getText();
        String password = passwordField.getText();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            updateStatus("Vui lòng nhập email và mật khẩu", true);
            return;
        }
        setBusy(true);
        authService.signInWithPassword(email, password)
                .thenAccept(profile -> Platform.runLater(() -> {
                    updateStatus("Đăng nhập thành công", false);
                    setBusy(false);
                    onLoginSuccess.accept(profile);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setBusy(false);
                        updateStatus(resolvePasswordSignInError(ex), true);
                    });
                    return null;
                });
    }

    @FXML
    private void onForgotPassword() {
        updateStatus("Tính năng đặt lại mật khẩu sẽ được bổ sung sau.", false);
    }

    @FXML
    private void onRegister() {
        if (authService == null) {
            return;
        }
        String email = emailField.getText();
        String password = passwordField.getText();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            updateStatus("Vui lòng nhập email và mật khẩu", true);
            return;
        }
        setBusy(true);
        authService.register(email, password, email)
                .thenAccept(profile -> Platform.runLater(() -> {
                    updateStatus("Tạo tài khoản thành công", false);
                    setBusy(false);
                    onLoginSuccess.accept(profile);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setBusy(false);
                        updateStatus("Tạo tài khoản thất bại: " + ex.getMessage(), true);
                    });
                    return null;
                });
    }

    private void setBusy(boolean busy) {
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        signInButton.setDisable(busy);
        registerButton.setDisable(busy);
        forgotPasswordLink.setDisable(busy);
    }

    private void updateStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle(error ? "-fx-text-fill: #ff3b30;" : "-fx-text-fill: #34c759;");
    }

    private String resolvePasswordSignInError(Throwable throwable) {
        Throwable cause = unwrapCause(throwable);
        String message = cause.getMessage();
        if (message != null) {
            String upper = message.toUpperCase();
            if (upper.contains("INVALID_PASSWORD") || upper.contains("EMAIL_NOT_FOUND")) {
                return "Sai email hoặc mật khẩu";
            }
            if (upper.contains("USER_DISABLED")) {
                return "Tài khoản của bạn đã bị vô hiệu hóa";
            }
            if (upper.contains("NETWORK") || upper.contains("CONNECT")) {
                return "Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng";
            }
        }
        return "Đăng nhập thất bại. Vui lòng thử lại";
    }

    private Throwable unwrapCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
