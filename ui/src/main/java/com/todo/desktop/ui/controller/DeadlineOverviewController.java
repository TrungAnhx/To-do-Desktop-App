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
    private static final String BASE_STATUS_STYLE = "-fx-background-radius: 10; -fx-padding: 6 12; -fx-font-weight: 600; -fx-font-size: 12px;";

    @FXML
    private ListView<Deadline> deadlineList;

    @FXML
    private TextField searchField;

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
        initialized = true;
        loadDeadlinesIfReady();
    }

    @FXML
    private void onAddDeadline() {
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
                .thenAccept(list -> javafx.application.Platform.runLater(() -> deadlines.setAll(list)))
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }

    private void applySearchFilter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (normalized.isEmpty()) {
            filteredDeadlines.setPredicate(deadline -> true);
            return;
        }
        filteredDeadlines.setPredicate(deadline -> {
            Task task = tasksById.get(deadline.taskId());
            String title = task != null ? task.title() : deadline.taskId();
            String description = task != null ? task.description() : "";
            return (title != null && title.toLowerCase().contains(normalized))
                    || (description != null && description.toLowerCase().contains(normalized));
        });
    }

    private final class DeadlineCell extends javafx.scene.control.ListCell<Deadline> {

        private final Region accent = new Region();
        private final Label titleLabel = new Label();
        private final Label descriptionLabel = new Label();
        private final Label dueLabel = new Label();
        private final Label statusLabel = new Label();
        private final VBox textContainer = new VBox(6);
        private final VBox trailingContainer = new VBox(6);
        private final HBox root = new HBox(18);

        private DeadlineCell() {
            accent.setPrefWidth(6);
            accent.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(accent, Priority.ALWAYS);

            titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: 600;");
            descriptionLabel.setStyle("-fx-text-fill: rgba(226,232,240,0.75); -fx-font-size: 13px;");
            dueLabel.setStyle("-fx-text-fill: rgba(148,163,184,0.85); -fx-font-size: 12px;");
            statusLabel.setStyle(BASE_STATUS_STYLE);

            textContainer.getChildren().addAll(titleLabel, descriptionLabel, dueLabel);
            textContainer.setAlignment(Pos.CENTER_LEFT);

            trailingContainer.setAlignment(Pos.CENTER_RIGHT);
            trailingContainer.getChildren().add(statusLabel);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            root.setPadding(new Insets(18));
            root.setAlignment(Pos.CENTER_LEFT);
            root.setStyle("-fx-background-color: rgba(15,23,42,0.78); -fx-background-radius: 20;");
            root.getChildren().addAll(accent, textContainer, spacer, trailingContainer);

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
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

            StatusStyle style = computeStatusStyle(item.dueAt());
            statusLabel.setText(style.label());
            statusLabel.setStyle(BASE_STATUS_STYLE + " -fx-text-fill: " + style.textColor() + "; -fx-background-color: " + style.backgroundColor() + ";");
            accent.setStyle("-fx-background-color: " + style.accentColor() + "; -fx-background-radius: 4 0 0 4;");

            setGraphic(root);
        }
    }

    private StatusStyle computeStatusStyle(Instant dueAt) {
        Instant now = Instant.now();
        Duration duration = Duration.between(now, dueAt);
        if (duration.isNegative()) {
            long hours = Math.abs(duration.toHours());
            String label = hours >= 24 ? "Đã quá hạn" : "Quá hạn " + hours + " giờ";
            return new StatusStyle(label, "#f87171", "rgba(248,113,113,0.18)", "#ef4444");
        }
        long minutes = duration.toMinutes();
        if (minutes <= 120) {
            return new StatusStyle("Sắp đến hạn", "#facc15", "rgba(250,204,21,0.18)", "#eab308");
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return new StatusStyle("Còn " + hours + " giờ", "#38bdf8", "rgba(56,189,248,0.18)", "#0ea5e9");
        }
        long days = duration.toDays();
        return new StatusStyle("Còn " + days + " ngày", "#34d399", "rgba(52,211,153,0.18)", "#22c55e");
    }

    private record StatusStyle(String label, String textColor, String backgroundColor, String accentColor) {
    }
}
