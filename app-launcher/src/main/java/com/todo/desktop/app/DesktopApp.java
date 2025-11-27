package com.todo.desktop.app;

import com.todo.desktop.ui.controller.LoginController;
import com.todo.desktop.ui.controller.MainShellController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class DesktopApp extends Application {

    private final AppModule module = new AppModule();

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("Everyday Planner");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        if (module.authService().currentUser().isPresent()) {
            showMain(stage);
        } else {
            showLogin(stage);
        }
        stage.show();
    }

    private void showLogin(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/login.fxml"));
        loader.setControllerFactory(module);
        Parent root = loader.load();
        Scene scene = new Scene(root, 1024, 720);
        stage.setScene(scene);

        LoginController controller = loader.getController();
        controller.setOnLoginSuccess(profile -> Platform.runLater(() -> switchToMain(stage)));
    }

    private void switchToMain(Stage stage) {
        try {
            System.out.println("DEBUG: Attempting to switch to Main Shell...");
            showMain(stage);
            System.out.println("DEBUG: Switched to Main Shell successfully.");
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: Failed to switch to main screen.");
            e.printStackTrace();
            // Optional: Show error alert to user
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Lỗi ứng dụng");
            alert.setHeaderText("Không thể tải giao diện chính");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    private void switchToLogin(Stage stage) {
        try {
            showLogin(stage);
        } catch (Exception e) {
            throw new RuntimeException("Không thể quay lại màn hình đăng nhập", e);
        }
    }

    private void showMain(Stage stage) throws Exception {
        System.out.println("DEBUG: showMain started");
        try {
            System.out.println("DEBUG: Loading main-shell.fxml...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main-shell.fxml"));
            loader.setControllerFactory(module);
            Parent root = loader.load();
            System.out.println("DEBUG: main-shell.fxml loaded successfully");
            
            Scene scene = new Scene(root, 1360, 860);
            System.out.println("DEBUG: Scene created");
            
            stage.setScene(scene);
            stage.centerOnScreen();
            System.out.println("DEBUG: Stage scene set");

            com.todo.desktop.ui.controller.MainShellController controller = loader.getController();
            controller.setOnSignOut(() -> Platform.runLater(() -> switchToLogin(stage)));
            System.out.println("DEBUG: Controller configured");
        } catch (Throwable t) {
            System.err.println("CRITICAL ERROR in showMain:");
            t.printStackTrace();
            throw t;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
