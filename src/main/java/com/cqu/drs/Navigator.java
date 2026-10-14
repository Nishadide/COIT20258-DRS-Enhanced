package com.cqu.drs;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Moves the application from one screen to another. Holding the stage in one
 * place keeps the navigation out of the individual controllers.
 */
public final class Navigator {

    private static Stage stage;

    private Navigator() {
    }

    /**
     * Records the window the application draws into.
     *
     * @param primaryStage the main window
     */
    public static void setStage(Stage primaryStage) {
        stage = primaryStage;
    }

    /**
     * Replaces the current screen with another.
     *
     * @param fxmlFile the layout to show, for example LoginView.fxml
     * @param title    the window title for the new screen
     */
    public static void goTo(String fxmlFile, String title) {
        java.net.URL location = Navigator.class.getResource("/fxml/" + fxmlFile);
        if (location == null) {
            showWarning("Screen not available",
                    "The screen " + fxmlFile + " has not been added yet.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(location);
            Parent root = loader.load();
            Scene scene = stage.getScene();
            if (scene == null) {
                stage.setScene(new Scene(root));
            } else {
                scene.setRoot(root);
            }
            stage.setTitle("DRS  |  " + title);
        } catch (IOException | RuntimeException e) {
            showError("Cannot open screen", "The screen " + fxmlFile
                    + " could not be loaded: " + e.getMessage());
        }
    }

    /**
     * Shows a message telling the user something went wrong.
     *
     * @param header  a short summary
     * @param message the detail
     */
    public static void showError(String header, String message) {
        show(AlertType.ERROR, "Problem", header, message);
    }

    /**
     * Shows a message confirming something succeeded.
     *
     * @param header  a short summary
     * @param message the detail
     */
    public static void showInfo(String header, String message) {
        show(AlertType.INFORMATION, "Information", header, message);
    }

    /**
     * Shows a message drawing attention to something the user should note.
     *
     * @param header  a short summary
     * @param message the detail
     */
    public static void showWarning(String header, String message) {
        show(AlertType.WARNING, "Please note", header, message);
    }

    private static void show(AlertType type, String title, String header, String message) {
        Alert dialog = new Alert(type);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(message);
        dialog.showAndWait();
    }
}
