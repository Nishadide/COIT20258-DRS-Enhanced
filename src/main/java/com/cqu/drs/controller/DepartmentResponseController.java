package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.StatusUpdate;
import com.cqu.drs.model.User;
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

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The screen a responder uses in the field. It shows only the work dispatched
 * to their own department, so one department cannot see or change another's
 * tasks.
 */
public class DepartmentResponseController implements Initializable {

    @FXML
    private Label userLabel;
    @FXML
    private TableView<ResponseTask> taskTable;
    @FXML
    private TableColumn<ResponseTask, String> taskIdColumn;
    @FXML
    private TableColumn<ResponseTask, String> referenceColumn;
    @FXML
    private TableColumn<ResponseTask, String> typeColumn;
    @FXML
    private TableColumn<ResponseTask, String> locationColumn;
    @FXML
    private TableColumn<ResponseTask, String> statusColumn;
    @FXML
    private ComboBox<ResponseStatus> statusCombo;
    @FXML
    private TextArea noteArea;
    @FXML
    private Label messageLabel;

    private final AppContext context = AppContext.getInstance();
    private String departmentId;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageLabel.setText("");
        statusCombo.setItems(FXCollections.observableArrayList(ResponseStatus.values()));

        Optional<User> current = context.getCurrentUser();
        if (current.isPresent() && current.get() instanceof DepartmentResponder) {
            DepartmentResponder responder = (DepartmentResponder) current.get();
            departmentId = responder.getDepartmentId();
            userLabel.setText(responder.getFullName() + "  |  department " + departmentId);
        } else {
            userLabel.setText("Not signed in as a responder");
        }

        configureColumns();
        refreshTasks();
    }

    private void configureColumns() {
        taskIdColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTaskId()));
        statusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStatus().getLabel()));

        referenceColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                reportFor(cell.getValue()).map(DisasterReport::getReferenceNumber).orElse("-")));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                reportFor(cell.getValue())
                        .map(r -> r.getType().getLabel()).orElse("-")));
        locationColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                reportFor(cell.getValue())
                        .map(r -> r.getLocation().getAddress()).orElse("-")));
    }

    /**
     * The disaster a task belongs to, which the table needs in order to show
     * the responder what the work is for.
     *
     * @param task the task being shown
     * @return the report holding it, or an empty Optional
     */
    private Optional<DisasterReport> reportFor(ResponseTask task) {
        return context.getCoordinationService().findReportForTask(task.getTaskId());
    }

    /**
     * Reloads the open work for this responder's department.
     */
    private void refreshTasks() {
        if (departmentId == null) {
            taskTable.setItems(FXCollections.observableArrayList());
            return;
        }
        taskTable.setItems(FXCollections.observableArrayList(
                context.getCoordinationService().findTasksForDepartment(departmentId)));
    }

    /**
     * Records progress against the selected task.
     *
     * @param event the button press
     */
    @FXML
    private void handleUpdateStatus(ActionEvent event) {
        messageLabel.setText("");

        ResponseTask task = taskTable.getSelectionModel().getSelectedItem();
        if (task == null) {
            messageLabel.setText("Select a task first.");
            return;
        }
        if (statusCombo.getValue() == null) {
            messageLabel.setText("Choose the status to record.");
            return;
        }

        Optional<DisasterReport> report = reportFor(task);
        if (report.isEmpty()) {
            messageLabel.setText("The disaster for this task could not be found.");
            return;
        }

        try {
            context.getCoordinationService().updateStatus(
                    report.get().getReferenceNumber(), task.getTaskId(),
                    statusCombo.getValue(), noteArea.getText(),
                    context.getCurrentUsername());

            Navigator.showInfo("Update recorded",
                    "Task " + task.getTaskId() + " is now "
                            + statusCombo.getValue().getLabel() + ".");
            noteArea.clear();
            statusCombo.getSelectionModel().clearSelection();
            refreshTasks();
        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Shows every update recorded against the selected task.
     *
     * @param event the button press
     */
    @FXML
    private void handleViewHistory(ActionEvent event) {
        ResponseTask task = taskTable.getSelectionModel().getSelectedItem();
        if (task == null) {
            messageLabel.setText("Select a task first.");
            return;
        }
        if (task.getHistory().isEmpty()) {
            Navigator.showInfo("No updates yet",
                    "Nothing has been recorded against " + task.getTaskId() + ".");
            return;
        }

        StringBuilder text = new StringBuilder();
        for (StatusUpdate update : task.getHistory()) {
            text.append(update).append("\n");
        }
        Navigator.showInfo("History for " + task.getTaskId(), text.toString());
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        refreshTasks();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        context.getAuthenticationService().logout();
        Navigator.goTo("WelcomeView.fxml", "Disaster Response System");
    }
}
