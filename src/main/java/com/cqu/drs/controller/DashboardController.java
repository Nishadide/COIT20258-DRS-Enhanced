package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.Priority;
import com.cqu.drs.model.ReportStatus;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * The coordinator's working screen. Active disasters are listed in priority
 * order, and the assessment, coordination and alert screens are opened from
 * here for the report selected.
 */
public class DashboardController implements Initializable {

    /** Value shown in a filter box to mean no filtering on that field. */
    private static final String ALL = "All";

    @FXML
    private Label userLabel;
    @FXML
    private Label countLabel;
    @FXML
    private TableView<DisasterReport> reportTable;
    @FXML
    private TableColumn<DisasterReport, String> referenceColumn;
    @FXML
    private TableColumn<DisasterReport, String> typeColumn;
    @FXML
    private TableColumn<DisasterReport, String> locationColumn;
    @FXML
    private TableColumn<DisasterReport, String> severityColumn;
    @FXML
    private TableColumn<DisasterReport, String> priorityColumn;
    @FXML
    private TableColumn<DisasterReport, String> statusColumn;
    @FXML
    private ComboBox<String> typeFilter;
    @FXML
    private ComboBox<String> priorityFilter;
    @FXML
    private ComboBox<String> statusFilter;

    private final AppContext context = AppContext.getInstance();

    /** Holds the report selected across a move to another screen and back. */
    private static String selectedReference;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        userLabel.setText("Signed in as "
                + context.getCurrentUser().map(u -> u.getFullName()).orElse("unknown"));

        configureColumns();
        configureFilters();
        refreshTable();
    }

    /**
     * Tells each column which part of a report to show. The values are read
     * once when the table is filled, which suits a list that is refreshed
     * rather than edited in place.
     */
    private void configureColumns() {
        referenceColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getReferenceNumber()));
        typeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getType().getLabel()));
        locationColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getLocation().getAddress()));
        severityColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isAssessed()
                        ? cell.getValue().getAssessment().getSeverity().name()
                        : cell.getValue().getReportedSeverity() + " (reported)"));
        priorityColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getPriority() == null
                        ? "-" : cell.getValue().getPriority().name()));
        statusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().currentStatus().getLabel()));
    }

    /**
     * Fills the filter boxes and refreshes the table whenever one changes.
     */
    private void configureFilters() {
        List<String> types = new ArrayList<>();
        types.add(ALL);
        for (DisasterType type : DisasterType.values()) {
            types.add(type.getLabel());
        }
        typeFilter.setItems(FXCollections.observableArrayList(types));
        typeFilter.setValue(ALL);

        List<String> priorities = new ArrayList<>();
        priorities.add(ALL);
        for (Priority priority : Priority.values()) {
            priorities.add(priority.name());
        }
        priorityFilter.setItems(FXCollections.observableArrayList(priorities));
        priorityFilter.setValue(ALL);

        List<String> statuses = new ArrayList<>();
        statuses.add(ALL);
        for (ReportStatus status : ReportStatus.values()) {
            statuses.add(status.getLabel());
        }
        statusFilter.setItems(FXCollections.observableArrayList(statuses));
        statusFilter.setValue(ALL);

        typeFilter.setOnAction(e -> refreshTable());
        priorityFilter.setOnAction(e -> refreshTable());
        statusFilter.setOnAction(e -> refreshTable());
    }

    /**
     * Reloads the table, showing assessed reports in priority order followed
     * by those still waiting to be assessed.
     */
    private void refreshTable() {
        List<DisasterReport> rows = new ArrayList<>();
        rows.addAll(context.getAssessmentService().queueByPriority());
        rows.addAll(context.getAssessmentService().awaitingAssessment());

        List<DisasterReport> filtered = new ArrayList<>();
        for (DisasterReport report : rows) {
            if (matchesFilters(report)) {
                filtered.add(report);
            }
        }

        ObservableList<DisasterReport> items = FXCollections.observableArrayList(filtered);
        reportTable.setItems(items);
        countLabel.setText(filtered.size() + " of " + rows.size()
                + " active disasters shown, ordered by priority.");
    }

    /**
     * Reports whether a disaster passes the filters currently set.
     *
     * @param report the report being considered
     * @return true when it should be shown
     */
    private boolean matchesFilters(DisasterReport report) {
        String type = typeFilter.getValue();
        if (type != null && !ALL.equals(type)
                && !report.getType().getLabel().equals(type)) {
            return false;
        }

        String priority = priorityFilter.getValue();
        if (priority != null && !ALL.equals(priority)) {
            if (report.getPriority() == null
                    || !report.getPriority().name().equals(priority)) {
                return false;
            }
        }

        String status = statusFilter.getValue();
        return status == null || ALL.equals(status)
                || report.currentStatus().getLabel().equals(status);
    }

    /**
     * The report highlighted in the table, if any.
     *
     * @return the selected report, or null
     */
    private DisasterReport selected() {
        DisasterReport report = reportTable.getSelectionModel().getSelectedItem();
        if (report == null) {
            Navigator.showWarning("Nothing selected",
                    "Select a disaster in the list first.");
        }
        return report;
    }

    /**
     * The report the coordinator chose before moving to another screen.
     *
     * @return the reference number, or null
     */
    public static String getSelectedReference() {
        return selectedReference;
    }

    @FXML
    private void handleAssess(ActionEvent event) {
        DisasterReport report = selected();
        if (report == null) {
            return;
        }
        if (report.isAssessed()) {
            Navigator.showWarning("Already assessed",
                    report.getReferenceNumber() + " has already been assessed.");
            return;
        }
        selectedReference = report.getReferenceNumber();
        Navigator.goTo("AssessmentView.fxml", "Assess a disaster");
    }

    @FXML
    private void handleCoordinate(ActionEvent event) {
        DisasterReport report = selected();
        if (report == null) {
            return;
        }
        if (!report.isAssessed()) {
            Navigator.showWarning("Not yet assessed",
                    "Assess " + report.getReferenceNumber()
                            + " before dispatching a response.");
            return;
        }
        selectedReference = report.getReferenceNumber();
        Navigator.goTo("CoordinationView.fxml", "Coordinate a response");
    }

    @FXML
    private void handleIssueAlert(ActionEvent event) {
        DisasterReport report = selected();
        if (report == null) {
            return;
        }
        selectedReference = report.getReferenceNumber();
        Navigator.goTo("AlertView.fxml", "Issue an alert");
    }

    @FXML
    private void handleResolve(ActionEvent event) {
        DisasterReport report = selected();
        if (report == null) {
            return;
        }
        try {
            context.getAssessmentService().resolve(
                    report.getReferenceNumber(), context.getCurrentUsername());
            Navigator.showInfo("Resolved",
                    report.getReferenceNumber() + " has been closed.");
            refreshTable();
        } catch (IllegalStateException | IllegalArgumentException e) {
            Navigator.showError("Cannot resolve yet", e.getMessage());
        }
    }

    /**
     * Lists the disasters lodged near the one selected. Several people
     * witnessing one event lodge several reports of it, and this is how the
     * coordinator sees that a report may describe an event already being
     * responded to rather than a new one.
     *
     * @param event the button press
     */
    @FXML
    private void handleNearby(ActionEvent event) {
        DisasterReport report = selected();
        if (report == null) {
            return;
        }
        if (!report.getLocation().hasCoordinates()) {
            Navigator.showWarning("No coordinates",
                    report.getReferenceNumber() + " was lodged with an address only, "
                            + "so there is no point to measure from.");
            return;
        }

        List<DisasterReport> nearby =
                context.getReportService().nearbyReports(report.getReferenceNumber());
        if (nearby.isEmpty()) {
            Navigator.showInfo("Nothing nearby",
                    "No other disaster has been lodged within "
                            + DrsConstants.DEFAULT_NEARBY_RADIUS_KM
                            + " km of " + report.getReferenceNumber() + ".");
            return;
        }

        StringBuilder text = new StringBuilder();
        for (DisasterReport other : nearby) {
            double km = report.getLocation().distanceTo(other.getLocation());
            text.append(String.format("%s  %s at %s%n     %.2f km away, %s%n%n",
                    other.getReferenceNumber(), other.getType().getLabel(),
                    other.getLocation().getAddress(), km,
                    other.currentStatus().getLabel()));
        }
        Navigator.showInfo(nearby.size() + " disaster(s) within "
                        + DrsConstants.DEFAULT_NEARBY_RADIUS_KM + " km of "
                        + report.getReferenceNumber(),
                text.toString().trim());
    }

    @FXML
    private void handleClearFilters(ActionEvent event) {
        typeFilter.setValue(ALL);
        priorityFilter.setValue(ALL);
        statusFilter.setValue(ALL);
        refreshTable();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        refreshTable();
    }

    @FXML
    private void handleAccounts(ActionEvent event) {
        Navigator.goTo("AccountManagementView.fxml", "Accounts and roles");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        context.getAuthenticationService().logout();
        Navigator.goTo("WelcomeView.fxml", "Disaster Response System");
    }
}
