package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Allocates departments to an assessed disaster and sends them their work.
 * Departments are picked straight from the list, holding Ctrl to pick more than
 * one, and sent in a single dispatch.
 *
 * Every department is listed, not only those currently available, and each is
 * labelled with whether it can take work. Listing only the available ones would
 * hide a department from the one screen that can dispatch to it, and the failure
 * path exists precisely so that dispatching to an unreachable department is
 * possible and the outcome recorded.
 */
public class CoordinationController implements Initializable {

    @FXML
    private TextArea reportArea;
    @FXML
    private ListView<Department> departmentList;
    @FXML
    private Label messageLabel;
    @FXML
    private Label availabilityHint;
    @FXML
    private TableView<ResponseTask> taskTable;
    @FXML
    private TableColumn<ResponseTask, String> taskIdColumn;
    @FXML
    private TableColumn<ResponseTask, String> departmentColumn;
    @FXML
    private TableColumn<ResponseTask, String> taskStatusColumn;
    @FXML
    private TableColumn<ResponseTask, String> notifiedColumn;

    private final AppContext context = AppContext.getInstance();
    private String reference;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageLabel.setText("");
        reference = DashboardController.getSelectedReference();

        departmentList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        departmentList.setCellFactory(list -> departmentCell());

        configureColumns();
        refreshDepartments();
        loadReport();
    }

    /**
     * Builds a cell that names a department and says whether it can currently
     * take work, so that the consequence of dispatching to it is visible at the
     * point the choice is made.
     *
     * @return a cell for a department
     */
    private ListCell<Department> departmentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Department department, boolean empty) {
                super.updateItem(department, empty);
                if (empty || department == null) {
                    setText(null);
                    return;
                }
                setText(department.getName()
                        + (department.isAvailable()
                        ? "  (available)" : "  (unavailable, notice will fail)"));
            }
        };
    }

    /**
     * Tells each column of the dispatched work table what to show.
     */
    private void configureColumns() {
        taskIdColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTaskId()));
        departmentColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getDepartmentName()));
        taskStatusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStatus().getLabel()));
        notifiedColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isNotified() ? "Yes" : "No, follow up"));
    }

    /**
     * Reloads the list from the department records, so that availability changed
     * on the account management screen is reflected here without the application
     * being restarted.
     */
    private void refreshDepartments() {
        List<Department> departments = context.getDepartmentService().findAll();
        departmentList.setItems(FXCollections.observableArrayList(departments));

        long available = departments.stream().filter(Department::isAvailable).count();
        availabilityHint.setText(departments.isEmpty()
                ? "No departments are on record."
                : available + " of " + departments.size() + " departments are available.");
    }

    /**
     * Shows the disaster being responded to, and the work already dispatched.
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
                + "Assessed " + report.getAssessment().getSeverity()
                + ", priority " + report.getPriority().name() + "\n"
                + "Status " + report.currentStatus());

        taskTable.setItems(FXCollections.observableArrayList(report.getTasks()));
    }

    /**
     * Reports whether a department already holds a task for this disaster.
     *
     * @param department the department being considered
     * @return true when work has already been dispatched to it
     */
    private boolean alreadyDispatched(Department department) {
        return taskTable.getItems().stream()
                .anyMatch(task -> department.getDepartmentId()
                        .equals(task.getDepartmentId()));
    }

    /**
     * Sends the selected departments their work. A department already holding a
     * task for this disaster is refused, so that it is not sent the same work
     * twice.
     *
     * @param event the button press
     */
    @FXML
    private void handleDispatch(ActionEvent event) {
        messageLabel.setText("");

        if (reference == null) {
            messageLabel.setText("No disaster was selected.");
            return;
        }

        List<Department> chosen =
                new ArrayList<>(departmentList.getSelectionModel().getSelectedItems());
        if (chosen.isEmpty()) {
            messageLabel.setText("Select at least one department. "
                    + "Hold Ctrl to select more than one.");
            return;
        }

        List<String> repeats = new ArrayList<>();
        for (Department department : chosen) {
            if (alreadyDispatched(department)) {
                repeats.add(department.getName());
            }
        }
        if (!repeats.isEmpty()) {
            chosen.removeIf(this::alreadyDispatched);
            if (chosen.isEmpty()) {
                messageLabel.setText(String.join(", ", repeats)
                        + " already has work for this disaster.");
                return;
            }
            messageLabel.setText("Skipped " + String.join(", ", repeats)
                    + ", which already has work for this disaster.");
        }

        try {
            List<ResponseTask> created = context.getCoordinationService()
                    .allocate(reference, chosen, context.getCurrentUsername());

            long unreached = created.stream().filter(t -> !t.isNotified()).count();
            if (unreached > 0) {
                Navigator.showWarning("Some notices did not get through",
                        unreached + " of " + created.size()
                                + " departments could not be reached. "
                                + "Follow up with them through another channel.");
            } else {
                Navigator.showInfo("Dispatched",
                        created.size() + " department(s) have been sent their work.");
            }

            departmentList.getSelectionModel().clearSelection();
            refreshDepartments();
            loadReport();
        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Reloads the departments from their records, so that an availability change
     * made while this screen was open is picked up.
     *
     * @param event the button press
     */
    @FXML
    private void handleRefreshDepartments(ActionEvent event) {
        messageLabel.setText("");
        refreshDepartments();
    }

    /**
     * Closes a task whose dispatch notice failed, recording that the department
     * was followed up through another channel. Only a task that was not notified
     * can be closed this way, so work a department is actually doing cannot be
     * signed off on its behalf. Without this the response to a disaster could
     * never be resolved once a notice had failed.
     *
     * @param event the button press
     */
    @FXML
    private void handleRecordFollowUp(ActionEvent event) {
        messageLabel.setText("");

        ResponseTask task = taskTable.getSelectionModel().getSelectedItem();
        if (task == null) {
            messageLabel.setText("Select a task in the dispatched work table first.");
            return;
        }
        if (task.isNotified()) {
            messageLabel.setText(task.getDepartmentName()
                    + " received its notice, so only that department can close "
                    + task.getTaskId() + ".");
            return;
        }
        if (task.isComplete()) {
            messageLabel.setText(task.getTaskId() + " is already complete.");
            return;
        }

        try {
            context.getCoordinationService().updateStatus(reference, task.getTaskId(),
                    ResponseStatus.COMPLETE,
                    "Notice failed; the department was followed up through another "
                            + "channel and the work is confirmed done.",
                    context.getCurrentUsername());

            Navigator.showInfo("Follow-up recorded",
                    task.getTaskId() + " for " + task.getDepartmentName()
                            + " is closed. The disaster can now be resolved once the "
                            + "remaining work is complete.");
            loadReport();
        } catch (IllegalArgumentException | IllegalStateException e) {
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
