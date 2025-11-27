package com.todo.desktop.ui.util;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class LoadingService {

    private static Stage loadingStage;
    private static Label statusLabel;

    public static void show(Stage owner, String message) {
        if (loadingStage != null && loadingStage.isShowing()) {
            if (statusLabel != null) statusLabel.setText(message);
            return;
        }

        loadingStage = new Stage();
        loadingStage.initOwner(owner);
        loadingStage.initModality(Modality.APPLICATION_MODAL);
        loadingStage.initStyle(StageStyle.TRANSPARENT);

        ProgressIndicator pi = new ProgressIndicator();
        pi.setStyle("-fx-progress-color: white;");
        pi.setPrefSize(40, 40);

        statusLabel = new Label(message);
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        VBox root = new VBox(16, pi, statusLabel);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-background-radius: 16; -fx-padding: 24;");
        root.setPrefSize(200, 150);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        
        loadingStage.setScene(scene);
        loadingStage.centerOnScreen();
        loadingStage.show();
    }

    public static void hide() {
        if (loadingStage != null) {
            // Add small delay to prevent flickering on fast ops
            Timeline timeline = new Timeline(new KeyFrame(Duration.millis(300), e -> {
                if (loadingStage != null) {
                    loadingStage.close();
                    loadingStage = null;
                }
            }));
            timeline.play();
        }
    }
}
