package com.todo.desktop.ui.controller;

import com.todo.desktop.domain.model.UserProfile;
import com.todo.desktop.domain.usecase.AuthService;
import com.todo.desktop.domain.usecase.DeadlineService;
import com.todo.desktop.domain.usecase.EmailService;
import com.todo.desktop.domain.usecase.OutlookService;
import com.todo.desktop.domain.usecase.TaskService;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class MainShellController {

    private TaskService taskService;
    private DeadlineService deadlineService;
    private EmailService emailService;
    private AuthService authService;
    private OutlookService outlookService;
    private Runnable onSignOut = () -> { };

    @FXML
    private Parent root;

    @FXML
    private StackPane contentContainer;

    @FXML
    private ToggleButton deadlineToggle;

    @FXML
    private ToggleButton emailToggle;

    @FXML
    private ToggleButton settingsToggle;

    @FXML
    private Parent deadlineOverview;

    @FXML
    private Parent inboxView;

    @FXML
    private Parent emailDetailView;

    @FXML
    private Parent settingsView;

    @FXML
    private Label userNameLabel;

    @FXML
    private Label userEmailLabel;

    @FXML
    private ImageView avatarImageView;

    @FXML
    private Label avatarFallback;

    @FXML
    private Button signOutButton;

    @FXML
    private DeadlineOverviewController deadlineOverviewController;

    @FXML
    private InboxController inboxViewController;

    @FXML
    private EmailDetailController emailDetailViewController;

    @FXML
    private SettingsController settingsViewController;

    private Section currentSection = Section.EMAIL;

    private enum Section {
        DEADLINE,
        EMAIL,
        EMAIL_DETAIL,
        SETTINGS
    }

    public void setDeadlineService(DeadlineService deadlineService) {
        this.deadlineService = deadlineService;
    }

    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void setOutlookService(OutlookService outlookService) {
        this.outlookService = outlookService;
    }

    public void setAuthService(AuthService authService) {
        this.authService = authService;
        updateUserProfile();
        if (settingsViewController != null) {
            settingsViewController.setAuthService(authService);
            settingsViewController.setMainShellController(this);
        }
    }
    
    public InboxController getInboxViewController() {
        return inboxViewController;
    }

    public void setOnSignOut(Runnable onSignOut) {
        this.onSignOut = Objects.requireNonNull(onSignOut);
    }

    @FXML
    private void initialize() {
        if (deadlineOverviewController != null && deadlineService != null) {
            deadlineOverviewController.setDeadlineService(deadlineService);
            if (taskService != null) {
                deadlineOverviewController.setTaskService(taskService);
            }
        }
        if (inboxViewController != null) {
            if (emailService != null) {
                inboxViewController.setEmailService(emailService);
            }
            if (outlookService != null) {
                inboxViewController.setOutlookService(outlookService);
            }
            inboxViewController.setOnEmailSelected(this::showEmailDetail);
        }
        if (emailDetailViewController != null) {
            if (outlookService != null) {
                emailDetailViewController.setOutlookService(outlookService);
            }
            emailDetailViewController.setOnBack(this::showInboxFromDetail);
        }
        if (settingsViewController != null) {
            if (authService != null) {
                settingsViewController.setAuthService(authService);
            }
            if (outlookService != null) {
                settingsViewController.setOutlookService(outlookService);
            }
        }
        
        showSection(Section.EMAIL); 
        if (emailToggle != null) {
            emailToggle.setSelected(true);
        }
        updateUserProfile();
        
        // Global Keyboard Shortcuts
        if (root != null) {
            root.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    setupShortcuts(newScene);
                }
            });
        }
    }
    
    private void setupShortcuts(javafx.scene.Scene scene) {
        KeyCombination newCombo = new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN);
        KeyCombination refreshCombo = new KeyCodeCombination(KeyCode.F5);
        
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (newCombo.match(event)) {
                if (currentSection == Section.EMAIL && inboxViewController != null) {
                    inboxViewController.onCompose();
                    event.consume();
                } else if (currentSection == Section.DEADLINE && deadlineOverviewController != null) {
                    deadlineOverviewController.onAddDeadline();
                    event.consume();
                }
            } else if (refreshCombo.match(event)) {
                if (currentSection == Section.EMAIL && inboxViewController != null) {
                    inboxViewController.onRefresh();
                    event.consume();
                } else if (currentSection == Section.DEADLINE && deadlineOverviewController != null) {
                    // deadlineOverviewController.refresh(); // Need to expose refresh
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.DELETE) {
                if (currentSection == Section.EMAIL_DETAIL && emailDetailViewController != null) {
                    emailDetailViewController.onDelete();
                    event.consume();
                }
            }
        });
    }

    @FXML
    private void showDeadline() {
        showSection(Section.DEADLINE);
    }

    @FXML
    private void showEmail() {
        showSection(Section.EMAIL);
    }

    @FXML
    private void showSettings() {
        showSection(Section.SETTINGS);
    }

    private void showEmailDetail() {
        if (inboxViewController != null && emailDetailViewController != null) {
            var selectedEmail = inboxViewController.getSelectedEmail();
            if (selectedEmail != null) {
                emailDetailViewController.displayEmail(selectedEmail);
                showSection(Section.EMAIL_DETAIL);
            }
        }
    }

    private void showInboxFromDetail() {
        showSection(Section.EMAIL);
        if (emailToggle != null) {
            emailToggle.setSelected(true);
        }
    }

    @FXML
    private void onSignOut() {
        signOutButton.setDisable(true);
        CompletableFuture<Void> future = authService != null ? authService.signOut() : CompletableFuture.completedFuture(null);
        future.whenComplete((ignored, throwable) -> javafx.application.Platform.runLater(() -> {
            signOutButton.setDisable(false);
            if (throwable == null) {
                onSignOut.run();
            } else {
                signOutButton.setText("Thử lại");
            }
        }));
    }

    private void showSection(Section section) {
        this.currentSection = section;
        
        // Sync toggle state if changed programmatically
        if (deadlineToggle != null && section == Section.DEADLINE) {
            deadlineToggle.setSelected(true);
        }
        if (emailToggle != null && section == Section.EMAIL) {
            emailToggle.setSelected(true);
        }
        if (settingsToggle != null && section == Section.SETTINGS) {
            settingsToggle.setSelected(true);
        }

        // Visibility logic
        if (deadlineOverview != null) {
            boolean showDeadline = section == Section.DEADLINE;
            deadlineOverview.setVisible(showDeadline);
            deadlineOverview.setManaged(showDeadline);
        }
        if (inboxView != null) {
            boolean showEmail = section == Section.EMAIL;
            inboxView.setVisible(showEmail);
            inboxView.setManaged(showEmail);
        }
        if (emailDetailView != null) {
            boolean showEmailDetail = section == Section.EMAIL_DETAIL;
            emailDetailView.setVisible(showEmailDetail);
            emailDetailView.setManaged(showEmailDetail);
        }
        if (settingsView != null) {
            boolean showSettings = section == Section.SETTINGS;
            settingsView.setVisible(showSettings);
            settingsView.setManaged(showSettings);
        }
    }

    private void updateUserProfile() {
        if (authService == null) {
            return;
        }
        authService.currentUser().ifPresentOrElse(profile -> {
            if (userNameLabel != null) {
                userNameLabel.setText(profile.displayName() != null && !profile.displayName().isBlank()
                        ? profile.displayName()
                        : profile.email());
            }
            if (userEmailLabel != null) {
                userEmailLabel.setText(profile.email());
            }
            if (avatarFallback != null) {
                avatarFallback.setText(extractInitial(profile));
            }
            updateAvatar(profile.avatarUri());
        }, () -> {
            if (userNameLabel != null) {
                userNameLabel.setText("Khách");
            }
            if (userEmailLabel != null) {
                userEmailLabel.setText("Chưa đăng nhập");
            }
            if (avatarFallback != null) {
                avatarFallback.setText("?");
            }
            updateAvatar(null);
        });
    }

    private String extractInitial(UserProfile profile) {
        String source = profile.displayName();
        if (source == null || source.isBlank()) {
            source = profile.email();
        }
        if (source == null || source.isBlank()) {
            return "U";
        }
        int codePoint = source.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(codePoint)));
    }

    private void updateAvatar(URI avatarUri) {
        if (avatarImageView == null || avatarFallback == null) {
            return;
        }
        if (avatarUri == null) {
            avatarImageView.setImage(null);
            avatarImageView.setVisible(false);
            avatarFallback.setVisible(true);
            return;
        }
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(avatarUri).build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(HttpResponse::body)
                .exceptionally(error -> null)
                .thenAccept(bytes -> javafx.application.Platform.runLater(() -> {
                    if (bytes == null || bytes.length == 0) {
                        avatarImageView.setImage(null);
                        avatarImageView.setVisible(false);
                        avatarFallback.setVisible(true);
                    } else {
                        Image image = new Image(new ByteArrayInputStream(bytes));
                        avatarImageView.setImage(image);
                        avatarImageView.setVisible(true);
                        avatarFallback.setVisible(false);
                        
                        // Apply circular clip
                        Circle clip = new Circle(20, 20, 20);
                        avatarImageView.setClip(clip);
                    }
                }));
    }
}