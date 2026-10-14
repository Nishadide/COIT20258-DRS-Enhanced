package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.Alert;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.ResponseTask;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Lets someone who lodged a report see what has happened to it, using the
 * reference number they were given. Only the report matching the reference is
 * shown, so one reference cannot be used to browse other people's reports.
 */
public class TrackReportController implements Initializable {

    @FXML
    private TextField referenceField;
    @FXML
    private TextArea resultArea;
    @FXML
    private Label messageLabel;

    private final AppContext context = AppContext.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageLabel.setText("");
        resultArea.clear();
    }

    /**
     * Looks up one report by its reference.
     *
     * @param event the button press
     */
    @FXML
    private void handleSearch(ActionEvent event) {
        messageLabel.setText("");
        resultArea.clear();

        String reference = referenceField.getText();
        if (reference == null || reference.trim().isEmpty()) {
            messageLabel.setText("Enter the reference number from your report.");
            return;
        }

        Optional<DisasterReport> found =
                context.getReportService().findByReference(reference.trim().toUpperCase());

        if (found.isEmpty()) {
            messageLabel.setText("No report was found with that reference. "
                    + "Check the number and try again.");
            return;
        }

        resultArea.setText(describe(found.get()));
    }

    /**
     * Describes a report in a form a member of the public can read, leaving
     * out anything internal.
     *
     * @param report the report to describe
     * @return the text shown on screen
     */
    private String describe(DisasterReport report) {
        StringBuilder text = new StringBuilder();
        text.append("Reference:  ").append(report.getReferenceNumber()).append("\n");
        text.append("Type:       ").append(report.getType().getLabel()).append("\n");
        text.append("Location:   ").append(report.getLocation()).append("\n");
        text.append("Lodged:     ").append(report.getReportedAt()).append("\n");
        text.append("Status:     ").append(report.currentStatus()).append("\n");

        if (report.isAssessed()) {
            text.append("Assessed:   ").append(report.getAssessment().getSeverity())
                    .append(", priority ").append(report.getPriority().name()).append("\n");
        } else {
            text.append("Assessed:   not yet assessed\n");
        }

        if (!report.getTasks().isEmpty()) {
            text.append("\nResponse\n");
            for (ResponseTask task : report.getTasks()) {
                text.append("  ").append(task.getDepartmentName())
                        .append(" - ").append(task.getStatus().getLabel()).append("\n");
            }
        }

        if (!report.getAlerts().isEmpty()) {
            text.append("\nAlerts issued\n");
            for (Alert alert : report.getAlerts()) {
                text.append("  ").append(alert.getType().getLabel())
                        .append(" for ").append(alert.getAffectedArea()).append("\n");
            }
        }

        return text.toString();
    }

    /**
     * Returns to the welcome screen.
     *
     * @param event the button press
     */
    @FXML
    private void handleBack(ActionEvent event) {
        Navigator.goTo("WelcomeView.fxml", "Disaster Response System");
    }
}
