package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceAllocation;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Commits a department's finite resources to a disaster, and releases them
 * again.
 *
 * <h2>What this screen is careful not to claim</h2>
 * The table shows what was free when it was last loaded. It does not show what
 * is free now, and it cannot: another coordinator on another machine may take
 * the last crew between the table being drawn and the button being pressed.
 *
 * <p>So the screen never decides that an allocation will succeed. It sends the
 * request and shows the answer, and when the answer is that the units have
 * gone it reloads the figures rather than leaving a number on the screen that
 * is known to be wrong. A screen that greyed out the button and then reported
 * success without asking would be lying to the coordinator about something
 * they are relying on during an emergency.
 *
 * <p>The spinner is capped at what the selected pool last reported, which
 * stops the obviously impossible request without pretending the cap is
 * binding.
 */
public class ResourceAllocationController implements Initializable {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd MMM HH:mm");

    @FXML
    private TextArea reportArea;
    @FXML
    private Label poolHint;
    @FXML
    private Label messageLabel;
    @FXML
    private Spinner<Integer> unitsSpinner;

    @FXML
    private TableView<Resource> resourceTable;
    @FXML
    private TableColumn<Resource, String> resourceNameColumn;
    @FXML
    private TableColumn<Resource, String> resourceDepartmentColumn;
    @FXML
    private TableColumn<Resource, String> resourceTypeColumn;
    @FXML
    private TableColumn<Resource, String> resourceAvailableColumn;

    @FXML
    private TableView<ResourceAllocation> allocationTable;
    @FXML
    private TableColumn<ResourceAllocation, String> allocationResourceColumn;
    @FXML
    private TableColumn<ResourceAllocation, String> allocationUnitsColumn;
    @FXML
    private TableColumn<ResourceAllocation, String> allocationStatusColumn;
    @FXML
    private TableColumn<ResourceAllocation, String> allocationWhoColumn;
    @FXML
    private TableColumn<ResourceAllocation, String> allocationWhenColumn;

    private final AppContext context = AppContext.getInstance();
    private String reference;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageLabel.setText("");
        reference = DashboardController.getSelectedReference();

        configureColumns();

        unitsSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1, 1));
        unitsSpinner.setDisable(true);

        // The spinner's ceiling follows the selected pool, so a coordinator
        // cannot ask for six crews from a pool of two by accident. The server
        // still checks: this is a convenience, not a control.
        resourceTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> capSpinnerTo(selected));

        loadReport();
        loadResources();
        loadAllocations();
    }

    private void configureColumns() {
        resourceNameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getName()));
        resourceDepartmentColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(departmentNameFor(cell.getValue())));
        resourceTypeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getType().getLabel()));
        resourceAvailableColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getType().describe(cell.getValue().getAvailableUnits())
                        + " of " + cell.getValue().getTotalUnits()));

        allocationResourceColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getResourceName()));
        allocationUnitsColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                String.valueOf(cell.getValue().getUnits())));
        allocationStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getStatus() == null
                        ? "" : cell.getValue().getStatus().getLabel()));
        allocationWhoColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getRequestedBy()));
        allocationWhenColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getAllocatedAt() == null
                        ? "" : WHEN.format(cell.getValue().getAllocatedAt())));
    }

    /**
     * The department's name rather than its identifier, because a coordinator
     * choosing between two pools reads "Hospital", not "DEP-02".
     *
     * @param resource the pool
     * @return the department name, or the identifier when it cannot be found
     */
    private String departmentNameFor(Resource resource) {
        return context.getDepartmentService().findById(resource.getDepartmentId())
                .map(department -> department.getName())
                .orElse(resource.getDepartmentId());
    }

    private void capSpinnerTo(Resource selected) {
        if (selected == null || selected.getAvailableUnits() < 1) {
            unitsSpinner.setDisable(true);
            unitsSpinner.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1, 1));
            return;
        }
        unitsSpinner.setDisable(false);
        unitsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, selected.getAvailableUnits(), 1));
    }

    private void loadReport() {
        if (reference == null) {
            reportArea.setText("No disaster was selected. Go back to the dashboard "
                    + "and choose one.");
            return;
        }
        Optional<DisasterReport> report =
                context.getAssessmentService().findByReference(reference);

        reportArea.setText(report.map(found -> found.getReferenceNumber() + "  "
                        + found.getType().getLabel() + "  at " + found.getLocation().getAddress()
                        + System.lineSeparator() + "Status: "
                        + found.currentStatus().getLabel())
                .orElse("The report " + reference + " could not be found."));
    }

    private void loadResources() {
        try {
            List<Resource> resources = context.getResourceService().findAll();
            resourceTable.setItems(FXCollections.observableArrayList(resources));

            long empty = resources.stream()
                    .filter(resource -> resource.getAvailableUnits() == 0).count();
            poolHint.setText(resources.isEmpty()
                    ? "No resources are on record."
                    : resources.size() + " pools, " + empty + " fully committed.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    private void loadAllocations() {
        if (reference == null) {
            return;
        }
        try {
            allocationTable.setItems(FXCollections.observableArrayList(
                    context.getResourceService().findAllocationsForReport(reference)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Sends the allocation and reports whatever came back.
     *
     * A refusal is shown and the figures are reloaded, because the reason for
     * the refusal is that the numbers on the screen are out of date. Leaving
     * them there would invite the coordinator to try the same impossible thing
     * again.
     *
     * @param event the button press
     */
    @FXML
    private void handleAllocate(ActionEvent event) {
        messageLabel.setText("");

        Resource selected = resourceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Choose a resource first.");
            return;
        }
        if (reference == null) {
            messageLabel.setText("No disaster is selected.");
            return;
        }

        int units = unitsSpinner.getValue() == null ? 0 : unitsSpinner.getValue();

        try {
            ResourceAllocation allocation = context.getResourceService().allocate(
                    selected.getResourceId(), reference, units,
                    context.getCurrentUsername());

            Navigator.showInfo("Resources allocated",
                    allocation.describe() + " have been committed.");

        } catch (IllegalStateException unavailable) {
            // Somebody else took them between the table being drawn and this
            // request arriving. This is the expected outcome of two
            // coordinators competing, not a fault.
            messageLabel.setText(unavailable.getMessage()
                    + " The figures below have been refreshed.");

        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());

        } finally {
            loadResources();
            loadAllocations();
        }
    }

    @FXML
    private void handleRelease(ActionEvent event) {
        messageLabel.setText("");

        ResourceAllocation selected =
                allocationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Choose an allocation to release.");
            return;
        }
        if (!selected.isHeld()) {
            messageLabel.setText("That allocation has already been released.");
            return;
        }

        try {
            ResourceAllocation released = context.getResourceService()
                    .release(selected.getAllocationId(), context.getCurrentUsername());

            Navigator.showInfo("Resources released",
                    released.describe() + " are back in the pool.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());

        } finally {
            loadResources();
            loadAllocations();
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        messageLabel.setText("");
        loadResources();
        loadAllocations();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        Navigator.goTo("DashboardView.fxml", "Coordinator dashboard");
    }
}
