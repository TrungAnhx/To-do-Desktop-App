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
            showMain(stage);
        } catch (Exception e) {
            throw new RuntimeException("Không thể mở giao diện chính", e);
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
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main-shell.fxml"));
        loader.setControllerFactory(module);
        Parent root = loader.load();
        Scene scene = new Scene(root, 1360, 860);
        stage.setScene(scene);
        stage.centerOnScreen();

        com.todo.desktop.ui.controller.MainShellController controller = loader.getController();
        controller.setOnSignOut(() -> Platform.runLater(() -> switchToLogin(stage)));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
