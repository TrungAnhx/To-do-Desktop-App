package com.todo.desktop.ui.controller;

import com.todo.desktop.domain.model.Deadline;
import com.todo.desktop.domain.model.Task;
import com.todo.desktop.domain.usecase.DeadlineService;
import com.todo.desktop.domain.usecase.TaskService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class DeadlineOverviewController {

    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ObservableList<Deadline> deadlines = FXCollections.observableArrayList();
    private final FilteredList<Deadline> filteredDeadlines = new FilteredList<>(deadlines);
    private final Map<String, Task> tasksById = new ConcurrentHashMap<>();
    private DeadlineService deadlineService;
    private TaskService taskService;
    private boolean initialized;

    @FXML
    private ListView<Deadline> deadlineList;

    @FXML
    private TextField searchField;
    
    @FXML
    private javafx.scene.Parent placeholderView;
    
    @FXML
    private PlaceholderController placeholderViewController;

    public void setDeadlineService(DeadlineService deadlineService) {
        this.deadlineService = Objects.requireNonNull(deadlineService);
        loadDeadlinesIfReady();
    }

    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @FXML
    private void initialize() {
        deadlineList.setItems(filteredDeadlines);
        deadlineList.setCellFactory(createCellFactory());
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldValue, newValue) -> applySearchFilter(newValue));
        }
        
        if (placeholderViewController != null) {
            placeholderViewController.setData("📝", "Không có công việc nào", "Bạn chưa có công việc nào trong danh sách này.");
            placeholderViewController.setAction("Tạo công việc mới", this::onAddDeadline);
        }
        
        filteredDeadlines.addListener((javafx.collections.ListChangeListener<Deadline>) c -> updatePlaceholder());
        
        initialized = true;
        loadDeadlinesIfReady();
    }
    
    private void updatePlaceholder() {
        boolean isEmpty = filteredDeadlines.isEmpty();
        if (placeholderView != null) {
            placeholderView.setVisible(isEmpty);
            placeholderView.setManaged(isEmpty);
        }
        if (deadlineList != null) {
            deadlineList.setVisible(!isEmpty);
            deadlineList.setManaged(!isEmpty);
        }
    }

    @FXML
    public void onAddDeadline() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/ui/add-task-dialog.fxml"));
            javafx.scene.control.DialogPane dialogPane = loader.load();
            
            javafx.scene.control.Dialog<Task> dialog = new javafx.scene.control.Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("Thêm công việc");
            
            // Lookup controls
            TextField titleField = (TextField) dialogPane.lookup("#titleField");
            javafx.scene.control.TextArea descriptionArea = (javafx.scene.control.TextArea) dialogPane.lookup("#descriptionArea");
            javafx.scene.control.DatePicker dueDatePicker = (javafx.scene.control.DatePicker) dialogPane.lookup("#dueDatePicker");
            
            dialog.setResultConverter(buttonType -> {
                if (buttonType == javafx.scene.control.ButtonType.OK) {
                    String title = titleField.getText();
                    String desc = descriptionArea.getText();
                    java.time.LocalDate date = dueDatePicker.getValue();
                    
                    if (title == null || title.isBlank()) {
                        return null; // Validation could be better
                    }
                    
                    Instant dueAt = date != null 
                        ? date.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant()
                        : Instant.now().plus(Duration.ofDays(1)); // Default 1 day
                        
                    // Generate ID here as Task record requires non-null ID
                    String newId = java.util.UUID.randomUUID().toString();
                    return new Task(newId, title, desc, dueAt, Task.TaskStatus.TODO, false);
                }
                return null;
            });
            
            dialog.showAndWait().ifPresent(task -> {
                if (taskService != null) {
                    taskService.saveTask(task)
                        .thenRun(() -> javafx.application.Platform.runLater(this::loadDeadlinesIfReady));
                }
            });
            
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private Callback<ListView<Deadline>, ListCell<Deadline>> createCellFactory() {
        return listView -> new DeadlineCell();
    }

    private void loadDeadlinesIfReady() {
        if (!initialized || deadlineService == null) {
            return;
        }
        CompletableFuture<List<Deadline>> deadlineFuture = deadlineService.listDeadlines();
        CompletableFuture<List<Task>> taskFuture = taskService != null ? taskService.listTasks() : CompletableFuture.completedFuture(List.of());

        deadlineFuture.thenCombine(taskFuture, (deadlineList, taskList) -> {
                    tasksById.clear();
                    taskList.forEach(task -> tasksById.put(task.id(), task));
                    return deadlineList;
                })
                .thenAccept(list -> javafx.application.Platform.runLater(() -> {
                    deadlines.setAll(list);
                    updatePlaceholder();
                }))
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }

    private void applySearchFilter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (normalized.isEmpty()) {
            filteredDeadlines.setPredicate(deadline -> true);
            updatePlaceholder();
            return;
        }
        filteredDeadlines.setPredicate(deadline -> {
            Task task = tasksById.get(deadline.taskId());
            String title = task != null ? task.title() : deadline.taskId();
            String description = task != null ? task.description() : "";
            return (title != null && title.toLowerCase().contains(normalized))
                    || (description != null && description.toLowerCase().contains(normalized));
        });
        updatePlaceholder();
    }

    private final class DeadlineCell extends javafx.scene.control.ListCell<Deadline> {

        private final Region accent = new Region();
        private final Label titleLabel = new Label();
        private final Label descriptionLabel = new Label();
        private final Label dueLabel = new Label();
        private final Label statusLabel = new Label();
        private final VBox textContainer = new VBox(4);
        private final VBox trailingContainer = new VBox(6);
        private final HBox content = new HBox(16);
        private final StackPane cardContainer = new StackPane();

        private DeadlineCell() {
            // Card Accent (Left colored bar) - Controlled by status class now
            accent.setPrefWidth(4);
            accent.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(accent, Priority.ALWAYS);
            accent.setVisible(false); // We use border for accent now
            accent.setManaged(false);

            // Typography
            titleLabel.getStyleClass().add("text-subject");
            titleLabel.setStyle("-fx-font-weight: bold;");
            
            descriptionLabel.getStyleClass().add("text-muted");
            descriptionLabel.setWrapText(false);
            descriptionLabel.setMaxWidth(400);
            
            dueLabel.getStyleClass().add("text-caption");

            // Status Badge
            statusLabel.getStyleClass().add("status-badge");

            // Layout
            textContainer.getChildren().addAll(titleLabel, descriptionLabel, dueLabel);
            textContainer.setAlignment(Pos.CENTER_LEFT);

            trailingContainer.setAlignment(Pos.CENTER_RIGHT);
            trailingContainer.getChildren().add(statusLabel);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            content.setAlignment(Pos.CENTER_LEFT);
            content.setPadding(new Insets(16));
            content.getChildren().addAll(textContainer, spacer, trailingContainer);
            
            // Card wrapper
            cardContainer.getStyleClass().add("card-cell");
            cardContainer.getChildren().add(content);

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            getStyleClass().add("list-cell");
        }

        @Override
        protected void updateItem(Deadline item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            Task task = tasksById.get(item.taskId());
            String title = task != null && task.title() != null ? task.title() : item.taskId();
            String description = task != null ? task.description() : null;
            titleLabel.setText(title == null || title.isBlank() ? "(Chưa đặt tên)" : title);
            descriptionLabel.setText(description == null || description.isBlank() ? "Không có mô tả" : description);

            ZoneId zoneId = item.timeZoneId() == null || item.timeZoneId().isBlank()
                    ? ZoneId.systemDefault()
                    : ZoneId.of(item.timeZoneId());
            ZonedDateTime zonedDateTime = item.dueAt().atZone(zoneId);
            dueLabel.setText("Hết hạn: " + zonedDateTime.format(DEADLINE_FORMATTER));

            // Apply Status Style
            StatusStyle style = computeStatusStyle(item.dueAt());
            statusLabel.setText(style.label());
            
            // Clear old status classes
            statusLabel.getStyleClass().removeAll("status-overdue", "status-soon", "status-normal", "status-safe");
            statusLabel.getStyleClass().add(style.cssClass());

            setGraphic(cardContainer);
        }
    }

    private StatusStyle computeStatusStyle(Instant dueAt) {
        Instant now = Instant.now();
        Duration duration = Duration.between(now, dueAt);
        
        // Overdue
        if (duration.isNegative()) {
            long hours = Math.abs(duration.toHours());
            String label = hours >= 24 ? "Đã quá hạn" : "Quá hạn " + hours + "h";
            return new StatusStyle(label, "status-overdue");
        }
        
        long minutes = duration.toMinutes();
        // Closing soon (< 2h)
        if (minutes <= 120) {
            return new StatusStyle("Sắp đến hạn", "status-soon");
        }
        
        long hours = duration.toHours();
        // Today (< 24h)
        if (hours < 24) {
            return new StatusStyle("Còn " + hours + "h", "status-normal");
        }
        
        long days = duration.toDays();
        return new StatusStyle("Còn " + days + " ngày", "status-safe");
    }

    private record StatusStyle(String label, String cssClass) {
    }
}