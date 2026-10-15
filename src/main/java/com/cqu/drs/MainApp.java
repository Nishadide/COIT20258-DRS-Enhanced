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
        AppContext context = AppContext.getInstance();

        Navigator.setStage(primaryStage);

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/WelcomeView.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        primaryStage.setScene(scene);
        // The mode is in the title because the two look identical otherwise,
        // and someone demonstrating the three-tier application needs to be
        // able to see at a glance that it really is talking to the server.
        primaryStage.setTitle("DRS  |  Disaster Response System"
                + ("remote".equals(context.getClientMode())
                   ? "  |  server " + context.getClient().describeServer()
                   : "  |  local data"));
        primaryStage.setMinWidth(820);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    /**
     * Closes the connection to the server when the window closes.
     *
     * JavaFX calls this on shutdown. Without it the socket is left for the
     * operating system to tidy up when the process ends, and the server sees a
     * connection that was dropped rather than one that was closed.
     */
    @Override
    public void stop() {
        AppContext.getInstance().shutdown();
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
