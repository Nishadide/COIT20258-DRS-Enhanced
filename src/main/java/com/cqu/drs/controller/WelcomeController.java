package com.cqu.drs.controller;

import com.cqu.drs.Navigator;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * The opening screen. Reporting is reachable from here without signing in,
 * which is what makes the system open to the public.
 */
public class WelcomeController implements Initializable {

    @FXML
    private Label statusLabel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        statusLabel.setText("Ready. Reporting a disaster does not require an account.");
    }

    /**
     * Opens the reporting form without asking the user to sign in.
     *
     * @param event the button press
     */
    @FXML
    private void handleReportDisaster(ActionEvent event) {
        Navigator.goTo("ReportDisasterView.fxml", "Report a disaster");
    }

    /**
     * Opens the screen for looking up a report by its reference.
     *
     * @param event the button press
     */
    @FXML
    private void handleTrackReport(ActionEvent event) {
        Navigator.goTo("TrackReportView.fxml", "Check a report");
    }

    /**
     * Opens the staff sign-in screen.
     *
     * @param event the button press
     */
    @FXML
    private void handleSignIn(ActionEvent event) {
        Navigator.goTo("LoginView.fxml", "Staff sign in");
    }
}
