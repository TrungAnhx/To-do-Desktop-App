package com.todo.desktop.ui.controller;

import com.todo.desktop.domain.model.EmailMessage;
import com.todo.desktop.domain.usecase.EmailService;
import com.todo.desktop.domain.usecase.OutlookService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public final class InboxController {

    private static final DateTimeFormatter INBOX_FORMATTER = DateTimeFormatter.ofPattern("HH:mm • dd/MM", Locale.getDefault());

    private final ObservableList<EmailMessage> inboxItems = FXCollections.observableArrayList();
    private final FilteredList<EmailMessage> filteredItems = new FilteredList<>(inboxItems);
    private EmailService emailService;
    private OutlookService outlookService;
    private boolean initialized;
    private Runnable onEmailSelected;

    @FXML
    private ListView<EmailMessage> messageList;

    @FXML
    private ToggleButton unreadToggle;

    @FXML
    private ToggleButton attachmentToggle;

    @FXML
    private VBox inboxMainView;

    @FXML
    private VBox composeEmailView;

    @FXML
    private TextField composeToField;

    @FXML
    private TextField composeSubjectField;

    @FXML
    private TextArea composeBodyField;

    @FXML
    private Button attachFilesButton;

    @FXML
    private Label attachmentCountLabel;

    @FXML
    private VBox attachmentListBox;

    @FXML
    private Button sendEmailButton;

    @FXML
    private Label composeStatusLabel;

    private final List<File> selectedAttachments = new ArrayList<>();
    private int currentSkip = 0;
    private static final int PAGE_SIZE = 500;  // Increase initial load to 500
    private boolean isLoadingMore = false;

    public void setEmailService(EmailService emailService) {
        this.emailService = Objects.requireNonNull(emailService);
        refreshInboxIfReady();
    }

    public void setOutlookService(OutlookService outlookService) {
        this.outlookService = outlookService;
        refreshInboxIfReady();
    }

    public void setOnEmailSelected(Runnable callback) {
        this.onEmailSelected = callback;
    }

    @FXML
    private void initialize() {
        messageList.setItems(filteredItems);
        messageList.setCellFactory(view -> new EmailCell());
        messageList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                EmailMessage selected = messageList.getSelectionModel().getSelectedItem();
                if (selected != null && onEmailSelected != null) {
                    onEmailSelected.run();
                }
            }
        });
        
        // Auto-load more when scrolling near bottom
        messageList.setOnScroll(event -> {
            ScrollBar scrollBar = getVerticalScrollbar(messageList);
            if (scrollBar != null && !isLoadingMore) {
                double scrollPosition = scrollBar.getValue();
                double maxScroll = scrollBar.getMax();
                // Load more when scrolled to 90% of the list
                if (scrollPosition >= (maxScroll * 0.9)) {
                    loadMoreInbox();
                }
            }
        });
        
        if (unreadToggle != null) {
            unreadToggle.selectedProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        }
        if (attachmentToggle != null) {
            attachmentToggle.selectedProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        }
        initialized = true;
        refreshInboxIfReady();
    }
    
    private ScrollBar getVerticalScrollbar(ListView<?> listView) {
        for (var node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar) {
                ScrollBar scrollBar = (ScrollBar) node;
                if (scrollBar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                    return scrollBar;
                }
            }
        }
        return null;
    }
    
    public EmailMessage getSelectedEmail() {
        return messageList.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void onLoadMore() {
        if (isLoadingMore) {
            return;
        }
        // Allow manual load even if > 500 emails
        loadMoreInbox();
    }
    
    @FXML
    private void onRefresh() {
        currentSkip = 0;
        refreshInboxIfReady();
    }

    @FXML
    private void onCompose() {
        if (outlookService == null || !outlookService.isConnected()) {
            // Show message to connect Outlook first
            return;
        }
        composeEmailView.setVisible(true);
        composeEmailView.setManaged(true);
        inboxMainView.setVisible(false);
        inboxMainView.setManaged(false);
    }

    @FXML
    private void onCloseCompose() {
        composeEmailView.setVisible(false);
        composeEmailView.setManaged(false);
        inboxMainView.setVisible(true);
        inboxMainView.setManaged(true);
        clearComposeForm();
    }

    @FXML
    private void onAttachFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn tệp đính kèm");
        List<File> files = fileChooser.showOpenMultipleDialog(attachFilesButton.getScene().getWindow());
        
        if (files != null && !files.isEmpty()) {
            selectedAttachments.addAll(files);
            updateAttachmentList();
        }
    }

    @FXML
    private void onSendComposedEmail() {
        if (outlookService == null || !outlookService.isConnected()) {
            setComposeStatus("Vui lòng kết nối Outlook trước", true);
            return;
        }

        String to = composeToField.getText();
        String subject = composeSubjectField.getText();
        String body = composeBodyField.getText();

        if (to == null || to.isBlank()) {
            setComposeStatus("Vui lòng nhập email người nhận", true);
            return;
        }

        sendEmailButton.setDisable(true);
        setComposeStatus("Đang gửi email...", false);

        if (selectedAttachments.isEmpty()) {
            outlookService.sendEmail(to, subject, body)
                    .thenAccept(v -> Platform.runLater(() -> handleSendSuccess()))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> handleSendError(ex));
                        return null;
                    });
        } else {
            outlookService.sendEmailWithAttachments(to, subject, body, selectedAttachments)
                    .thenAccept(v -> Platform.runLater(() -> handleSendSuccess()))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> handleSendError(ex));
                        return null;
                    });
        }
    }

    private void handleSendSuccess() {
        setComposeStatus("✓ Gửi email thành công!", false);
        sendEmailButton.setDisable(false);
        // Auto close after 1.5 seconds
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                Platform.runLater(this::onCloseCompose);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleSendError(Throwable ex) {
        ex.printStackTrace();
        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        setComposeStatus("Gửi email thất bại: " + message, true);
        sendEmailButton.setDisable(false);
    }

    private void clearComposeForm() {
        composeToField.clear();
        composeSubjectField.clear();
        composeBodyField.clear();
        selectedAttachments.clear();
        updateAttachmentList();
        setComposeStatus("", false);
    }

    private void setComposeStatus(String message, boolean isError) {
        if (composeStatusLabel != null) {
            composeStatusLabel.setText(message);
            if (isError) {
                composeStatusLabel.setStyle("-fx-text-fill: #f87171; -fx-font-size: 13px;");
            } else {
                composeStatusLabel.setStyle("-fx-text-fill: rgba(226,232,240,0.8); -fx-font-size: 13px;");
            }
        }
    }

    private void updateAttachmentList() {
        if (selectedAttachments.isEmpty()) {
            attachmentCountLabel.setText("");
            attachmentListBox.setVisible(false);
            attachmentListBox.setManaged(false);
        } else {
            attachmentCountLabel.setText(selectedAttachments.size() + " tệp đã chọn");
            attachmentListBox.getChildren().clear();
            
            for (File file : selectedAttachments) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-background-color: rgba(59,130,246,0.15); -fx-padding: 6 10; -fx-background-radius: 6;");
                
                Label nameLabel = new Label("📎 " + file.getName());
                nameLabel.setStyle("-fx-text-fill: rgba(226,232,240,0.9); -fx-font-size: 12px;");
                
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                Button removeBtn = new Button("✕");
                removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #f87171; -fx-cursor: hand; -fx-font-size: 12px;");
                removeBtn.setOnAction(e -> {
                    selectedAttachments.remove(file);
                    updateAttachmentList();
                });
                
                row.getChildren().addAll(nameLabel, spacer, removeBtn);
                attachmentListBox.getChildren().add(row);
            }
            
            attachmentListBox.setVisible(true);
            attachmentListBox.setManaged(true);
        }
    }

    private void refreshInboxIfReady() {
        if (!initialized) {
            return;
        }
        
        System.out.println("DEBUG: refreshInboxIfReady called");
        System.out.println("DEBUG: Outlook connected: " + (outlookService != null && outlookService.isConnected()));
        
        // Ưu tiên OutlookService nếu đã kết nối
        if (outlookService != null && outlookService.isConnected()) {
            System.out.println("DEBUG: Using Outlook service");
            isLoadingMore = true;
            outlookService.getInboxMessages(PAGE_SIZE, currentSkip)
                    .thenAccept(messages -> Platform.runLater(() -> {
                        System.out.println("DEBUG: Loaded " + messages.size() + " emails from Outlook");
                        inboxItems.setAll(messages);
                        currentSkip = PAGE_SIZE;
                        applyFilters();
                        isLoadingMore = false;
                    }))
                    .exceptionally(ex -> {
                        ex.printStackTrace();
                        isLoadingMore = false;
                        loadFromLocalService();
                        return null;
                    });
        } else if (emailService != null) {
            System.out.println("DEBUG: Using local email service (Outlook not connected)");
            loadFromLocalService();
        } else {
            System.out.println("DEBUG: No email service available");
        }
    }
    
    private void loadMoreInboxManual() {
        if (!initialized || isLoadingMore) {
            return;
        }
        
        if (outlookService != null && outlookService.isConnected()) {
            isLoadingMore = true;
            outlookService.getInboxMessages(PAGE_SIZE, currentSkip)
                    .thenAccept(messages -> Platform.runLater(() -> {
                        System.out.println("DEBUG: Added " + messages.size() + " more emails (manual)");
                        inboxItems.addAll(messages);
                        currentSkip += PAGE_SIZE;
                        applyFilters();
                        isLoadingMore = false;
                    }))
                    .exceptionally(ex -> {
                        ex.printStackTrace();
                        Platform.runLater(() -> {
                            isLoadingMore = false;
                        });
                        return null;
                    });
        }
    }
    
    private void loadMoreInbox() {
        if (!initialized || isLoadingMore) {
            return;
        }
        
        // If we already have lots of emails loaded, don't auto-load more
        if (inboxItems.size() >= 500) {
            return;
        }
        
        if (outlookService != null && outlookService.isConnected()) {
            isLoadingMore = true;
            outlookService.getInboxMessages(PAGE_SIZE, currentSkip)
                    .thenAccept(messages -> Platform.runLater(() -> {
                        System.out.println("DEBUG: Added " + messages.size() + " more emails");
                        inboxItems.addAll(messages);
                        currentSkip += PAGE_SIZE;
                        applyFilters();
                        isLoadingMore = false;
                    }))
                    .exceptionally(ex -> {
                        ex.printStackTrace();
                        Platform.runLater(() -> {
                            isLoadingMore = false;
                        });
                        return null;
                    });
        }
    }
    
    private void loadFromLocalService() {
        emailService.listInbox()
                .thenAccept(messages -> Platform.runLater(() -> {
                    inboxItems.setAll(messages);
                    applyFilters();
                }))
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }

    private void applyFilters() {
        filteredItems.setPredicate(message -> {
            if (message == null) {
                return false;
            }
            boolean unreadOnly = unreadToggle != null && unreadToggle.isSelected();
            boolean attachmentsOnly = attachmentToggle != null && attachmentToggle.isSelected();
            if (unreadOnly && message.isRead()) {
                return false;
            }
            if (attachmentsOnly && !message.hasAttachments()) {
                return false;
            }
            return true;
        });
    }

    private final class EmailCell extends ListCell<EmailMessage> {

        private final Label avatarLabel = new Label();
        private final StackPane avatarContainer = new StackPane();
        private final Label subjectLabel = new Label();
        private final Label senderLabel = new Label();
        private final Label previewLabel = new Label();
        private final Label timeLabel = new Label();
        private final HBox root = new HBox(16);

        private static final String UNREAD_STYLE = "-fx-background-color: rgba(49,130,206,0.15); -fx-background-radius: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0.2, 0, 2);";
        private static final String READ_STYLE = "-fx-background-color: rgba(45,55,72,0.5); -fx-background-radius: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 6, 0.15, 0, 1);";
        private static final String SUBJECT_BASE_STYLE = "-fx-text-fill: #f7fafc; -fx-font-size: 16px; -fx-font-weight: 600;";
        private static final String SENDER_STYLE = "-fx-text-fill: #e2e8f0; -fx-font-size: 14px; -fx-font-weight: 500;";
        private static final String PREVIEW_STYLE = "-fx-text-fill: #a0aec0; -fx-font-size: 13px;";
        private static final String TIME_STYLE = "-fx-text-fill: #a0aec0; -fx-font-size: 12px; -fx-font-weight: 600;";

        private EmailCell() {
            avatarLabel.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: 600;");
            avatarContainer.setPrefSize(52, 52);
            avatarContainer.setMaxSize(52, 52);
            avatarContainer.setStyle("-fx-background-color: linear-gradient(135deg, #3182ce, #1e40af); -fx-background-radius: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 6, 0.3, 0, 2);");
            avatarContainer.getChildren().add(avatarLabel);

            subjectLabel.setStyle(SUBJECT_BASE_STYLE);
            senderLabel.setStyle(SENDER_STYLE);
            previewLabel.setStyle(PREVIEW_STYLE);
            previewLabel.setWrapText(false);

            timeLabel.setStyle(TIME_STYLE);

            VBox textContainer = new VBox(6, subjectLabel, senderLabel, previewLabel);
            textContainer.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(18));
            root.setStyle("-fx-cursor: hand;");
            root.getChildren().addAll(avatarContainer, textContainer, spacer, timeLabel);

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(EmailMessage item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            subjectLabel.setText(item.subject() == null || item.subject().isBlank() ? "(Không tiêu đề)" : item.subject());
            senderLabel.setText(item.from());
            previewLabel.setText(item.bodyPreview() == null ? "" : item.bodyPreview());
            timeLabel.setText(item.receivedDateTime() == null ? "" : item.receivedDateTime().atZone(ZoneId.systemDefault()).format(INBOX_FORMATTER));

            String initial = extractInitial(item.from());
            avatarLabel.setText(initial);
            avatarContainer.setStyle(!item.isRead()
                    ? "-fx-background-color: linear-gradient(135deg, #3182ce, #1e40af); -fx-background-radius: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0.3, 0, 2);"
                    : "-fx-background-color: linear-gradient(135deg, #38a169, #2f855a); -fx-background-radius: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 6, 0.3, 0, 2);");

            root.setStyle(!item.isRead() ? UNREAD_STYLE : READ_STYLE);
            subjectLabel.setStyle(SUBJECT_BASE_STYLE + (!item.isRead() ? " -fx-font-weight: 700;" : " -fx-font-weight: 600;"));
            setGraphic(root);
        }

        private String extractInitial(String text) {
            if (text == null || text.isBlank()) {
                return "?";
            }
            int codePoint = text.codePointAt(0);
            return new String(Character.toChars(Character.toUpperCase(codePoint)));
        }
    }
}
