package com.todo.desktop.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class PlaceholderController {

    @FXML private Label iconLabel;
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Button actionButton;

    public void setData(String icon, String title, String subtitle) {
        if (icon != null) iconLabel.setText(icon);
        if (title != null) titleLabel.setText(title);
        if (subtitle != null) subtitleLabel.setText(subtitle);
    }

    public void setAction(String buttonText, Runnable action) {
        if (buttonText != null && action != null) {
            actionButton.setText(buttonText);
            actionButton.setOnAction(e -> action.run());
            actionButton.setVisible(true);
            actionButton.setManaged(true);
        } else {
            actionButton.setVisible(false);
            actionButton.setManaged(false);
        }
    }
}
