package com.cqu.drs;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Starts the Disaster Response System. The window opens on the welcome screen,
 * from which a member of the public can report a disaster without signing in,
 * and staff can sign in to reach the functions for their role.
 */
public class MainApp extends Application {

    private static final int WINDOW_WIDTH = 980;
    private static final int WINDOW_HEIGHT = 680;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Building the context here creates the data folder and seeds the
        // departments before any screen asks for them.
        AppContext.getInstance();

        Navigator.setStage(primaryStage);

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/WelcomeView.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setTitle("DRS  |  Disaster Response System");
        primaryStage.setMinWidth(820);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    /**
     * Entry point.
     *
     * @param args command line arguments, unused
     */
    public static void main(String[] args) {
        launch(args);
    }
}
