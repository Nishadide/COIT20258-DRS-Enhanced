package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.Severity;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Records a coordinator's judgement of how serious a reported disaster is.
 * The priority is shown as soon as a severity is chosen, so the coordinator
 * can see what the assessment will mean before committing to it.
 */
public class AssessmentController implements Initializable {

    @FXML
    private TextArea reportArea;
    @FXML
    private ComboBox<Severity> severityCombo;
    @FXML
    private Label priorityLabel;
    @FXML
    private TextArea notesArea;
    @FXML
    private Label messageLabel;

    private final AppContext context = AppContext.getInstance();
    private String reference;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageLabel.setText("");
        severityCombo.setItems(FXCollections.observableArrayList(Severity.values()));
        severityCombo.setOnAction(e -> showDerivedPriority());

        reference = DashboardController.getSelectedReference();
        loadReport();
    }

    /**
     * Shows the report being assessed, or explains that none was chosen.
     */
    private void loadReport() {
        if (reference == null) {
            reportArea.setText("No disaster was selected. Go back and choose one.");
            return;
        }
        Optional<DisasterReport> found =
                context.getAssessmentService().findByReference(reference);
        if (found.isEmpty()) {
            reportArea.setText("Report " + reference + " could not be found.");
            return;
        }

        DisasterReport report = found.get();
        StringBuilder text = new StringBuilder();
        text.append("Reference:  ").append(report.getReferenceNumber()).append("\n");
        text.append("Type:       ").append(report.getType().getLabel()).append("\n");
        text.append("Location:   ").append(report.getLocation()).append("\n");
        text.append("Lodged:     ").append(report.getReportedAt()).append("\n");
        text.append("Reported as: ").append(report.getReportedSeverity())
                .append(" by ").append(report.isAnonymous()
                        ? "an anonymous reporter" : report.getReporter().getName()).append("\n");
        if (report.getDescription() != null) {
            text.append("\n").append(report.getDescription());
        }
        reportArea.setText(text.toString());
    }

    /**
     * Shows the priority the chosen severity will produce, so that the rule is
     * visible rather than applied silently.
     */
    private void showDerivedPriority() {
        Severity severity = severityCombo.getValue();
        if (severity == null) {
            priorityLabel.setText("Priority will be set from the severity.");
            return;
        }
        priorityLabel.setText("This will be given priority "
                + severity.toPriority().name() + " (" + severity.toPriority().getLabel() + ").");
    }

    /**
     * Records the assessment and returns to the dashboard.
     *
     * @param event the button press
     */
    @FXML
    private void handleAssess(ActionEvent event) {
        messageLabel.setText("");

        if (reference == null) {
            messageLabel.setText("No disaster was selected.");
            return;
        }
        if (severityCombo.getValue() == null) {
            messageLabel.setText("Choose a severity before recording the assessment.");
            return;
        }

        try {
            Severity severity = severityCombo.getValue();
            context.getAssessmentService().assess(reference, severity,
                    notesArea.getText(), context.getCurrentUsername());

            if (context.getAlertService().warrantsEvacuation(severity)) {
                Navigator.showWarning("Consider an evacuation",
                        "This disaster was assessed as " + severity
                                + ". You may want to issue an evacuation alert "
                                + "from the dashboard.");
            }

            Navigator.showInfo("Assessment recorded",
                    reference + " is now priority " + severity.toPriority().name() + ".");
            Navigator.goTo("DashboardView.fxml", "Coordinator dashboard");
        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Returns to the dashboard without recording anything.
     *
     * @param event the button press
     */
    @FXML
    private void handleBack(ActionEvent event) {
        Navigator.goTo("DashboardView.fxml", "Coordinator dashboard");
    }
}
