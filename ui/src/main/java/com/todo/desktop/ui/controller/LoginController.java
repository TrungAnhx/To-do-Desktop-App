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
            emailField.setOnAction(e -> onSignIn());
            // Cho phép tab để di chuyển focus
            emailField.setFocusTraversable(false);
        }
        if (passwordField != null) {
            passwordField.setOnAction(e -> onSignIn());
            passwordField.setFocusTraversable(false);
        }
        
        // Focus vào dummy node thay vì email field
        if (focusDummy != null) {
            javafx.application.Platform.runLater(() -> {
                focusDummy.requestFocus();
            });
        }
        
        // Khi click vào vùng ngoài textfield thì remove focus (ẩn cursor)
        if (loginRoot != null && focusDummy != null) {
            loginRoot.setOnMousePressed(event -> {
                javafx.scene.Node target = (javafx.scene.Node) event.getTarget();
                
                // Check if clicked on or inside a TextField/PasswordField
                boolean isTextFieldClick = false;
                javafx.scene.Node node = target;
                while (node != null) {
                    if (node == emailField || node == passwordField) {
                        isTextFieldClick = true;
                        break;
                    }
                    node = node.getParent();
                }
                
                // If not clicking on text field, remove focus and hide cursor
                if (!isTextFieldClick) {
                    // Workaround: Tạm thời ẩn và hiện lại để force ẩn cursor
                    boolean emailVisible = emailField != null && emailField.isVisible();
                    boolean passVisible = passwordField != null && passwordField.isVisible();
                    
                    if (emailField != null) {
                        emailField.setVisible(false);
                    }
                    if (passwordField != null) {
                        passwordField.setVisible(false);
                    }
                    
                    // Remove focus
                    focusDummy.requestFocus();
                    
                    // Hiện lại ngay lập tức
                    javafx.application.Platform.runLater(() -> {
                        if (emailField != null && emailVisible) {
                            emailField.setVisible(true);
                        }
                        if (passwordField != null && passVisible) {
                            passwordField.setVisible(true);
                        }
                    });
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
