package com.todo.desktop.ui.controller;

import com.todo.desktop.domain.model.EmailAttachment;
import com.todo.desktop.domain.model.EmailMessage;
import com.todo.desktop.domain.usecase.OutlookService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class EmailDetailController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("'Thứ' E, dd/MM/yyyy 'lúc' HH:mm", new Locale("vi"));

    private OutlookService outlookService;
    private EmailMessage currentEmail;
    private Runnable onBackCallback;

    @FXML private Button backButton;
    @FXML private Label subjectLabel;
    @FXML private Label senderAvatarLabel;
    @FXML private Label senderNameLabel;
    @FXML private Label senderEmailLabel;
    @FXML private Label timeLabel;
    @FXML private Label toLabel;
    @FXML private Label dateLabel;
    @FXML private Label bodyLabel;
    @FXML private VBox attachmentsSection;
    @FXML private VBox attachmentsList;
    @FXML private Button replyButton;
    @FXML private Button forwardButton;
    @FXML private Button deleteButton;
    @FXML private Label statusLabel;

    public void setOutlookService(OutlookService outlookService) {
        this.outlookService = outlookService;
    }

    public void setOnBack(Runnable callback) {
        this.onBackCallback = callback;
    }

    public void displayEmail(EmailMessage email) {
        this.currentEmail = email;
        
        subjectLabel.setText(email.subject());
        senderNameLabel.setText(email.from());
        senderEmailLabel.setText(email.fromEmail());
        senderAvatarLabel.setText(extractInitial(email.from()));
        
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
        timeLabel.setText(email.receivedDateTime().atZone(ZoneId.systemDefault()).format(timeFormat));
        
        String toText = "Tới: " + String.join(", ", email.toRecipients());
        toLabel.setText(toText);
        
        dateLabel.setText("Ngày: " + email.receivedDateTime().atZone(ZoneId.systemDefault()).format(DATE_FORMATTER));
        
        // Load full body if needed
        if (email.bodyContent() == null || email.bodyContent().isEmpty()) {
            bodyLabel.setText(email.bodyPreview());
            if (outlookService != null && outlookService.isConnected()) {
                loadFullEmail(email.id());
            }
        } else {
            bodyLabel.setText(stripHtml(email.bodyContent()));
        }
        
        // Show attachments
        if (email.hasAttachments() && !email.attachments().isEmpty()) {
            attachmentsSection.setVisible(true);
            attachmentsSection.setManaged(true);
            attachmentsList.getChildren().clear();
            
            for (EmailAttachment att : email.attachments()) {
                attachmentsList.getChildren().add(createAttachmentRow(att));
            }
        } else {
            attachmentsSection.setVisible(false);
            attachmentsSection.setManaged(false);
        }
    }

    private void loadFullEmail(String messageId) {
        if (outlookService == null) return;
        
        outlookService.getMessageById(messageId)
                .thenAccept(fullEmail -> Platform.runLater(() -> {
                    if (fullEmail != null) {
                        bodyLabel.setText(stripHtml(fullEmail.bodyContent()));
                        
                        // Update attachments if loaded
                        if (fullEmail.hasAttachments() && !fullEmail.attachments().isEmpty()) {
                            attachmentsSection.setVisible(true);
                            attachmentsSection.setManaged(true);
                            attachmentsList.getChildren().clear();
                            
                            for (EmailAttachment att : fullEmail.attachments()) {
                                attachmentsList.getChildren().add(createAttachmentRow(att));
                            }
                        }
                    }
                }))
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }

    private HBox createAttachmentRow(EmailAttachment attachment) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: white; -fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #e5e7eb; -fx-border-width: 1; -fx-border-radius: 8;");
        
        // Icon
        Label icon = new Label(getFileIcon(attachment.name()));
        icon.setStyle("-fx-font-size: 24px;");
        
        // File info
        VBox info = new VBox(2);
        Label nameLabel = new Label(attachment.name());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #1a1a1a;");
        
        Label sizeLabel = new Label(formatFileSize(attachment.size()));
        sizeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");
        
        info.getChildren().addAll(nameLabel, sizeLabel);
        HBox.setHgrow(info, Priority.ALWAYS);
        
        // Download button
        Button downloadBtn = new Button("⬇");
        downloadBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 8; -fx-background-radius: 6; -fx-cursor: hand;");
        downloadBtn.setOnAction(e -> downloadAttachment(attachment));
        
        row.getChildren().addAll(icon, info, downloadBtn);
        return row;
    }

    private void downloadAttachment(EmailAttachment attachment) {
        if (outlookService == null || currentEmail == null) return;
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Lưu tệp đính kèm");
        fileChooser.setInitialFileName(attachment.name());
        File file = fileChooser.showSaveDialog(attachmentsList.getScene().getWindow());
        
        if (file != null) {
            statusLabel.setText("Đang tải xuống...");
            outlookService.downloadAttachment(currentEmail.id(), attachment.id())
                    .thenAccept(bytes -> Platform.runLater(() -> {
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            fos.write(bytes);
                            statusLabel.setText("Đã lưu: " + file.getName());
                        } catch (Exception e) {
                            statusLabel.setText("Lỗi: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> statusLabel.setText("Tải xuống thất bại: " + ex.getMessage()));
                        return null;
                    });
        }
    }

    @FXML
    private void onBack() {
        if (onBackCallback != null) {
            onBackCallback.run();
        }
    }

    @FXML
    private void onReply() {
        statusLabel.setText("Tính năng trả lời đang được phát triển");
    }

    @FXML
    private void onForward() {
        statusLabel.setText("Tính năng chuyển tiếp đang được phát triển");
    }

    @FXML
    private void onDelete() {
        statusLabel.setText("Tính năng xóa đang được phát triển");
    }

    private String extractInitial(String text) {
        if (text == null || text.isBlank()) {
            return "?";
        }
        return text.substring(0, 1).toUpperCase();
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }

    private String getFileIcon(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "📄";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "📝";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "📊";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "📊";
        if (lower.matches(".*\\.(jpg|jpeg|png|gif|bmp)$")) return "🖼";
        if (lower.matches(".*\\.(zip|rar|7z)$")) return "📦";
        return "📎";
    }

    private String formatFileSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
