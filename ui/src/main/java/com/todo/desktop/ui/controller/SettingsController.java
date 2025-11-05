package com.todo.desktop.ui.controller;

import com.todo.desktop.domain.model.UserProfile;
import com.todo.desktop.domain.usecase.OutlookService;
import com.todo.desktop.domain.usecase.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;

public final class SettingsController {

    private AuthService authService;
    private OutlookService outlookService;

    @FXML
    private CheckBox darkModeToggle;

    @FXML
    private CheckBox desktopNotificationToggle;

    @FXML
    private Label accountNameLabel;

    @FXML
    private Label accountEmailLabel;

    @FXML
    private Label accountAvatarLabel;

    @FXML
    private Button connectOutlookButton;

    @FXML
    private Button disconnectOutlookButton;

    @FXML
    private Label outlookStatusLabel;

    @FXML
    private Label outlookMessageLabel;

    public void setAuthService(AuthService authService) {
        this.authService = authService;
        updateAccountInfo();
    }

    public void setOutlookService(OutlookService outlookService) {
        this.outlookService = outlookService;
        updateOutlookStatus();
    }

    @FXML
    private void initialize() {
        updateAccountInfo();
        updateOutlookStatus();
    }

    @FXML
    private void onConnectOutlook() {
        if (outlookService == null) {
            return;
        }
        connectOutlookButton.setDisable(true);
        setOutlookMessage("Đang mở trình duyệt...", false);
        
        outlookService.connectOutlook()
                .thenAccept(v -> Platform.runLater(() -> {
                    setOutlookMessage("Kết nối thành công!", false);
                    updateOutlookStatus();
                    connectOutlookButton.setDisable(false);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        ex.printStackTrace();
                        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        setOutlookMessage("Lỗi: " + message, true);
                        connectOutlookButton.setDisable(false);
                    });
                    return null;
                });
    }

    @FXML
    private void onDisconnectOutlook() {
        if (outlookService == null) {
            return;
        }
        outlookService.disconnect();
        setOutlookMessage("Đã ngắt kết nối", false);
        updateOutlookStatus();
    }

    private void updateAccountInfo() {
        if (accountNameLabel == null || accountEmailLabel == null || accountAvatarLabel == null || authService == null) {
            return;
        }
        authService.currentUser().ifPresentOrElse(this::applyProfile, this::applyGuest);
    }

    private void applyProfile(UserProfile profile) {
        String displayName = profile.displayName() == null || profile.displayName().isBlank()
                ? profile.email()
                : profile.displayName();
        accountNameLabel.setText(displayName);
        accountEmailLabel.setText(profile.email());
        accountAvatarLabel.setText(extractInitial(displayName));
    }

    private void applyGuest() {
        accountNameLabel.setText("Khách");
        accountEmailLabel.setText("Chưa đăng nhập");
        accountAvatarLabel.setText("?");
    }

    private String extractInitial(String source) {
        if (source == null || source.isBlank()) {
            return "U";
        }
        return new String(Character.toChars(Character.toUpperCase(source.codePointAt(0))));
    }

    private void updateOutlookStatus() {
        if (outlookService == null || connectOutlookButton == null || disconnectOutlookButton == null) {
            return;
        }

        boolean connected = outlookService.isConnected();
        connectOutlookButton.setVisible(!connected);
        connectOutlookButton.setManaged(!connected);
        disconnectOutlookButton.setVisible(connected);
        disconnectOutlookButton.setManaged(connected);

        if (outlookStatusLabel != null) {
            if (connected) {
                outlookStatusLabel.setText("✓ Đã kết nối với Outlook");
                outlookStatusLabel.setStyle("-fx-text-fill: #34c759; -fx-font-size: 13px;");
            } else {
                outlookStatusLabel.setText("Chưa kết nối");
                outlookStatusLabel.setStyle("-fx-text-fill: rgba(148,163,184,0.75); -fx-font-size: 13px;");
            }
        }
    }

    private void setOutlookMessage(String message, boolean isError) {
        if (outlookMessageLabel != null) {
            outlookMessageLabel.setText(message);
            if (isError) {
                outlookMessageLabel.setStyle("-fx-text-fill: #ff3b30; -fx-font-size: 12px;");
            } else {
                outlookMessageLabel.setStyle("-fx-text-fill: rgba(226,232,240,0.8); -fx-font-size: 12px;");
            }
        }
    }
}
