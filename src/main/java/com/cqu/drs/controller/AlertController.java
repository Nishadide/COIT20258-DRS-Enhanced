package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.DisasterReport;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Issues a public warning or an evacuation instruction for a disaster.
 * Whether the alert reached its recipients is shown in the table, so a failed
 * broadcast is visible rather than assumed to have worked.
 */
public class AlertController implements Initializable {

    @FXML
    private TextArea reportArea;
    @FXML
    private ComboBox<AlertType> alertTypeCombo;
    @FXML
    private TextField areaField;
    @FXML
    private TextArea messageArea;
    @FXML
    private Label messageLabel;
    @FXML
    private TableView<Alert> alertTable;
    @FXML
    private TableColumn<Alert, String> alertTypeColumn;
    @FXML
    private TableColumn<Alert, String> alertAreaColumn;
    @FXML
    private TableColumn<Alert, String> alertIssuedColumn;
    @FXML
    private TableColumn<Alert, String> alertDeliveredColumn;

    private final AppContext context = AppContext.getInstance();
    private String reference;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageLabel.setText("");
        alertTypeCombo.setItems(FXCollections.observableArrayList(AlertType.values()));
        reference = DashboardController.getSelectedReference();

        configureColumns();
        loadReport();
    }

    private void configureColumnsk() {
        alertTypeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getType().getLabel()));
        alertAreaColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getAffectedArea()));
        alertIssuedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().getIssuedAt())));
        alertDeliveredColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isDelivered() ? "Yes" : "No"));
    }

    /**
     * Shows the disaster the alert concerns, and any alerts already issued.
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
        reportArea.setText(report.getReferenceNumber() + "  "
                + report.getType().getLabel() + " at " + report.getLocation() + "\n"
                + "Status " + report.currentStatus());

        if (areaField.getText() == null || areaField.getText().isEmpty()) {
            areaField.setText(report.getLocation().getAddress());
        }
        alertTable.setItems(FXCollections.observableArrayList(report.getAlerts()));
    }

    /**
     * Issues the alert and reports whether it was delivered.
     *
     * @param event the button press
     */
    @FXML
    private void handleIssue(ActionEvent event) {
        messageLabel.setText("");

        if (reference == null) {
            messageLabel.setText("No disaster was selected.");
            return;
        }
        if (alertTypeCombo.getValue() == null) {
            messageLabel.setText("Choose whether this is a warning or an evacuation.");
            return;
        }

        try {
            Alert alert = context.getAlertService().issue(reference,
                    alertTypeCombo.getValue(), areaField.getText(),
                    messageArea.getText(), context.getCurrentUsername());

            if (alert.isDelivered()) {
                Navigator.showInfo("Alert issued",
                        "The " + alert.getType().getLabel().toLowerCase()
                                + " has been broadcast for " + alert.getAffectedArea() + ".");
            } else {
                Navigator.showWarning("Alert not delivered",
                        "The alert was recorded but could not be broadcast. "
                                + "Use another channel to reach the area.");
            }
            messageArea.clear();
            loadReport();
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Returns to the dashboard.
     *
     * @param event the button press
     */
    @FXML
    private void handleBack(ActionEvent event) {
        Navigator.goTo("DashboardView.fxml", "Coordinator dashboard");
    }
}
