package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.Severity;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * The reporting form. This screen is reachable without signing in, so nothing
 * here depends on a session. Leaving the name blank lodges the report
 * anonymously.
 */
public class ReportDisasterController implements Initializable {

    @FXML
    private ComboBox<DisasterType> typeCombo;
    @FXML
    private ComboBox<Severity> severityCombo;
    @FXML
    private TextField addressField;
    @FXML
    private CheckBox useLocationCheck;
    @FXML
    private TextField latitudeField;
    @FXML
    private TextField longitudeField;
    @FXML
    private Label coordinateHint;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private TextField nameField;
    @FXML
    private TextField contactField;
    @FXML
    private Label messageLabel;

    private final AppContext context = AppContext.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        typeCombo.setItems(FXCollections.observableArrayList(DisasterType.values()));
        severityCombo.setItems(FXCollections.observableArrayList(Severity.values()));
        messageLabel.setText("");
    }

    /**
     * Turns the coordinate fields on or off. When switched on, a position is
     * filled in to stand for the one a device would supply; when switched off,
     * the report still goes ahead with an address alone.
     *
     * @param event the check box being ticked or cleared
     */
    @FXML
    private void handleUseMyLocation(ActionEvent event) {
        boolean wanted = useLocationCheck.isSelected();
        latitudeField.setDisable(!wanted);
        longitudeField.setDisable(!wanted);

        if (wanted) {
            // Stands in for a reading from the device.
            latitudeField.setText("-37.8136");
            longitudeField.setText("144.9631");
            coordinateHint.setText("Position captured. You may correct it if it is wrong.");
        } else {
            latitudeField.clear();
            longitudeField.clear();
            coordinateHint.setText("Optional. Helps responders find the place.");
        }
    }

    /**
     * Checks the details and lodges the report.
     *
     * @param event the button press
     */
    @FXML
    private void handleSubmit(ActionEvent event) {
        messageLabel.setText("");

        Double latitude = parseCoordinate(latitudeField.getText(), "Latitude");
        Double longitude = parseCoordinate(longitudeField.getText(), "Longitude");
        if (messageLabel.getText().length() > 0) {
            return;
        }

        String name = textOrNull(nameField.getText());
        String contact = textOrNull(contactField.getText());

        List<String> errors = context.getReportService().validate(
                typeCombo.getValue(), addressField.getText(), latitude, longitude,
                severityCombo.getValue(), descriptionArea.getText(), contact);

        if (!errors.isEmpty()) {
            messageLabel.setText(String.join("\n", errors));
            return;
        }

        try {
            Reporter reporter = buildReporter(name, contact);
            String reference = context.getReportService().submitReport(
                    typeCombo.getValue(), addressField.getText(), latitude, longitude,
                    severityCombo.getValue(), descriptionArea.getText(), reporter);

            Navigator.showInfo("Report lodged",
                    "Your reference number is " + reference
                            + ".\n\nKeep this number. You can use it to check "
                            + "what is happening with your report.");
            Navigator.goTo("WelcomeView.fxml", "Disaster Response System");
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Builds the reporter to attach to the report, or null when no details
     * were given, which lodges the report anonymously.
     *
     * @param name    the reporter's name, or null
     * @param contact the contact number, or null
     * @return a reporter, or null for an anonymous report
     */
    private Reporter buildReporter(String name, String contact) {
        if (name == null) {
            return null;
        }
        return new Reporter(DrsConstants.REPORTER_PREFIX + UUID.randomUUID().toString()
                .substring(0, DrsConstants.SHORT_ID_LENGTH), name, contact);
    }

    /**
     * Reads a coordinate, recording a message when the text is not a number.
     *
     * @param text  the text entered
     * @param label the field name to use in the message
     * @return the value, or null when the field was left empty
     */
    private Double parseCoordinate(String text, String label) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            messageLabel.setText(label + " must be a number.");
            return null;
        }
    }

    /**
     * Treats a blank field as absent.
     *
     * @param text the text entered
     * @return the trimmed text, or null when nothing was entered
     */
    private String textOrNull(String text) {
        return (text == null || text.trim().isEmpty()) ? null : text.trim();
    }

    /**
     * Abandons the report and returns to the welcome screen.
     *
     * @param event the button press
     */
    @FXML
    private void handleCancel(ActionEvent event) {
        Navigator.goTo("WelcomeView.fxml", "Disaster Response System");
    }
}
