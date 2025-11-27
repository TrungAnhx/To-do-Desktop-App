package com.todo.desktop.ui.controller;

import com.todo.desktop.domain.model.EmailMessage;
import com.todo.desktop.domain.usecase.EmailService;
import com.todo.desktop.domain.usecase.OutlookService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    private Button allToggle;

    @FXML
    private Button unreadToggle;

    @FXML
    private ToggleButton attachmentToggle;

    @FXML
    private VBox inboxMainView;

    @FXML
    private StackPane composeEmailView;

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

    @FXML
    private javafx.scene.Parent placeholderView;
    
    @FXML
    private PlaceholderController placeholderViewController;

    private final List<File> selectedAttachments = new ArrayList<>();
    private int currentSkip = 0;
    private static final int PAGE_SIZE = 50;
    private boolean isLoadingMore = false;
    private boolean isUnreadFilter = false;

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
        
        // Setup Placeholder
        if (placeholderViewController != null) {
            placeholderViewController.setData("📭", "Hộp thư trống", "Hiện tại không có email nào để hiển thị.");
            placeholderViewController.setAction("Làm mới ngay", this::onRefresh);
        }
        
        // --- Tab Logic with Regular Buttons ---
        
        // Default State: All
        setTabActive(allToggle, true);
        setTabActive(unreadToggle, false);
        isUnreadFilter = false;

        if (allToggle != null) {
            allToggle.setOnAction(e -> {
                System.out.println("DEBUG: Clicked ALL Toggle");
                // Switch to ALL
                isUnreadFilter = false;
                setTabActive(allToggle, true);
                setTabActive(unreadToggle, false);
                applyFilters();
            });
        }

        if (unreadToggle != null) {
            unreadToggle.setOnAction(e -> {
                System.out.println("DEBUG: Clicked UNREAD Toggle");
                // Switch to UNREAD
                isUnreadFilter = true;
                setTabActive(unreadToggle, true);
                setTabActive(allToggle, false);
                applyFilters();
            });
        }
        
        if (attachmentToggle != null) {
            attachmentToggle.selectedProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        }
        
        // Listen for list changes to update placeholder
        filteredItems.addListener((javafx.collections.ListChangeListener<EmailMessage>) c -> updatePlaceholder());
        
        // Auto-load more on scroll
        messageList.setOnScroll(event -> {
            ScrollBar scrollBar = getVerticalScrollbar(messageList);
            if (scrollBar != null && !isLoadingMore) {
                double scrollPosition = scrollBar.getValue();
                double maxScroll = scrollBar.getMax();
                if (scrollPosition >= (maxScroll * 0.9)) {
                    loadMoreInbox();
                }
            }
        });

        initialized = true;
        refreshInboxIfReady();
    }
    
    private void setTabActive(Button btn, boolean active) {
        if (btn == null) return;
        if (active) {
            btn.setStyle("-fx-background-color: linear-gradient(to right, #3B82F6, #2563EB); -fx-text-fill: white; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, rgba(0, 122, 255, 0.3), 8, 0.0, 0, 2);");
        } else {
            btn.setStyle("-fx-background-color: white; -fx-text-fill: #64748B; -fx-font-weight: normal; -fx-border-color: #E2E8F0; -fx-border-width: 1;");
        }
    }

    private void applyFilters() {
        filteredItems.setPredicate(message -> {
            if (message == null) return false;
            
            boolean unreadOnly = isUnreadFilter;
            boolean attachmentsOnly = attachmentToggle != null && attachmentToggle.isSelected();
            
            if (unreadOnly && message.isRead()) return false;
            if (attachmentsOnly && !message.hasAttachments()) return false;
            
            return true;
        });
        updatePlaceholder();
    }

    private void updatePlaceholder() {
        boolean isEmpty = filteredItems.isEmpty();
        if (placeholderView != null) {
            placeholderView.setVisible(isEmpty);
            placeholderView.setManaged(isEmpty);
        }
        if (messageList != null) {
            messageList.setVisible(!isEmpty);
            messageList.setManaged(!isEmpty);
        }
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
        loadMoreInbox();
    }
    
    @FXML
    public void onRefresh() {
        currentSkip = 0;
        refreshInboxIfReady();
    }

    @FXML
    public void onCompose() {
        if (outlookService == null || !outlookService.isConnected()) {
             System.out.println("Outlook not connected");
             return;
        }
        composeEmailView.setVisible(true);
        composeEmailView.setManaged(true);
    }

    @FXML
    private void onCloseCompose() {
        composeEmailView.setVisible(false);
        composeEmailView.setManaged(false);
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
        setComposeStatus("✓ Gửi thành công", false);
        sendEmailButton.setDisable(false);
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                Platform.runLater(this::onCloseCompose);
            } catch (InterruptedException e) { e.printStackTrace(); }
        }).start();
    }

    private void handleSendError(Throwable ex) {
        ex.printStackTrace();
        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        setComposeStatus("Lỗi: " + message, true);
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
            composeStatusLabel.setStyle(isError ? "-fx-text-fill: -fx-danger;" : "-fx-text-fill: -fx-text-secondary;");
        }
    }

    private void updateAttachmentList() {
        if (selectedAttachments.isEmpty()) {
            attachmentCountLabel.setText("");
            attachmentListBox.setVisible(false);
            attachmentListBox.setManaged(false);
        } else {
            attachmentCountLabel.setText(selectedAttachments.size() + " tệp");
            attachmentListBox.getChildren().clear();
            
            for (File file : selectedAttachments) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                
                Label nameLabel = new Label("📎 " + file.getName());
                nameLabel.setStyle("-fx-text-fill: -fx-text-secondary; -fx-font-size: 12px;");
                
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                Button removeBtn = new Button("✕");
                removeBtn.getStyleClass().add("button-icon");
                removeBtn.setStyle("-fx-text-fill: -fx-danger; -fx-font-size: 10px;");
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
        if (!initialized) return;
        
        if (outlookService != null && outlookService.isConnected()) {
            isLoadingMore = true;
            if (currentSkip == 0 && messageList.getScene() != null) {
                com.todo.desktop.ui.util.LoadingService.show((javafx.stage.Stage) messageList.getScene().getWindow(), "Đang tải email...");
            }
            
            outlookService.getInboxMessages(PAGE_SIZE, currentSkip)
                    .whenComplete((messages, ex) -> Platform.runLater(() -> {
                        if (currentSkip == 0) {
                            com.todo.desktop.ui.util.LoadingService.hide();
                        }
                        
                        if (ex != null) {
                            ex.printStackTrace();
                            isLoadingMore = false;
                            if (currentSkip == 0) loadFromLocalService();
                        } else {
                            if (currentSkip == 0) inboxItems.clear();
                            if (messages != null) inboxItems.addAll(messages);
                            currentSkip += PAGE_SIZE;
                            applyFilters();
                            updatePlaceholder();
                            isLoadingMore = false;
                        }
                    }));
        } else if (emailService != null) {
            loadFromLocalService();
        }
    }
    
    private void loadMoreInbox() {
        if (!initialized || isLoadingMore || currentSkip >= 1000) return;
        
        if (outlookService != null && outlookService.isConnected()) {
            isLoadingMore = true;
            outlookService.getInboxMessages(PAGE_SIZE, currentSkip)
                    .thenAccept(messages -> Platform.runLater(() -> {
                        if (messages != null && !messages.isEmpty()) {
                            inboxItems.addAll(messages);
                            currentSkip += PAGE_SIZE;
                        }
                        applyFilters();
                        updatePlaceholder();
                        isLoadingMore = false;
                    }))
                    .exceptionally(ex -> {
                        ex.printStackTrace();
                        isLoadingMore = false;
                        return null;
                    });
        }
    }
    
    private void loadFromLocalService() {
        emailService.listInbox()
                .thenAccept(messages -> Platform.runLater(() -> {
                    inboxItems.setAll(messages);
                    applyFilters();
                    updatePlaceholder();
                }));
    }

    private final class EmailCell extends ListCell<EmailMessage> {

        private final Label avatarLabel = new Label();
        private final StackPane avatarContainer = new StackPane();
        private final Label subjectLabel = new Label();
        private final Label senderLabel = new Label();
        private final Label previewLabel = new Label();
        private final Label timeLabel = new Label();
        private final Label attachmentIcon = new Label("📎");
        
        private final HBox content = new HBox(16);
        private final StackPane cardContainer = new StackPane();

        private EmailCell() {
            // Avatar
            avatarLabel.getStyleClass().add("avatar-text");
            avatarContainer.getStyleClass().add("avatar-circle");
            avatarContainer.setPrefSize(48, 48);
            avatarContainer.setMaxSize(48, 48);
            avatarContainer.getChildren().add(avatarLabel);

            // Text Content
            senderLabel.getStyleClass().add("text-sender");
            subjectLabel.getStyleClass().add("text-subject");
            previewLabel.getStyleClass().add("text-muted");
            previewLabel.setWrapText(false);
            previewLabel.setMaxWidth(500);

            // Layout: Sender (Top), Subject + Preview (Bottom)
            VBox textContainer = new VBox(4);
            textContainer.getChildren().addAll(senderLabel, subjectLabel, previewLabel);
            textContainer.setAlignment(Pos.CENTER_LEFT);

            // Meta (Time + Attachment)
            VBox metaContainer = new VBox(4);
            metaContainer.setAlignment(Pos.TOP_RIGHT);
            timeLabel.getStyleClass().add("text-caption");
            attachmentIcon.setStyle("-fx-font-size: 12px; -fx-text-fill: -fx-text-muted;");
            metaContainer.getChildren().add(timeLabel);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Inner Content Layout
            content.setAlignment(Pos.CENTER_LEFT);
            content.setPadding(new Insets(16));
            content.getChildren().addAll(avatarContainer, textContainer, spacer, metaContainer);
            
            // Card Wrapper
            cardContainer.getStyleClass().add("card-cell");
            cardContainer.getChildren().add(content);
            
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            getStyleClass().add("list-cell");
        }

        @Override
        protected void updateItem(EmailMessage item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            senderLabel.setText(item.from());
            subjectLabel.setText(item.subject() == null || item.subject().isBlank() ? "(Không tiêu đề)" : item.subject());
            previewLabel.setText(item.bodyPreview() == null ? "" : item.bodyPreview());
            timeLabel.setText(item.receivedDateTime() == null ? "" : item.receivedDateTime().atZone(ZoneId.systemDefault()).format(INBOX_FORMATTER));

            String initial = extractInitial(item.from());
            avatarLabel.setText(initial);
            
            // Unread Styling
            cardContainer.getStyleClass().remove("card-unread");
            if (!item.isRead()) {
                if (!cardContainer.getStyleClass().contains("card-unread")) {
                    cardContainer.getStyleClass().add("card-unread");
                }
                senderLabel.setStyle("-fx-font-weight: 800;"); // Extra emphasis
                timeLabel.setStyle("-fx-text-fill: -fx-primary; -fx-font-weight: 700;");
            } else {
                senderLabel.setStyle(""); 
                timeLabel.setStyle("");
            }
            
            // Attachment Icon
            VBox meta = (VBox) content.getChildren().get(3);
            if (item.hasAttachments() && !meta.getChildren().contains(attachmentIcon)) {
                meta.getChildren().add(attachmentIcon);
            } else if (!item.hasAttachments()) {
                meta.getChildren().remove(attachmentIcon);
            }

            setGraphic(cardContainer);
        }

        private String extractInitial(String text) {
            if (text == null || text.isBlank()) return "?";
            int codePoint = text.codePointAt(0);
            return new String(Character.toChars(Character.toUpperCase(codePoint)));
        }
    }
}
