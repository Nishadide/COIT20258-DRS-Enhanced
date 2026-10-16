package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.Casualty;
import com.cqu.drs.model.CasualtyStatus;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.TriageCategory;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Records the people hurt in a disaster, triages them, and refers them to
 * hospital.
 *
 * <h2>What this screen shows and what it never holds</h2>
 * It shows names and notes, because the people using it are the responders and
 * coordinators running the response and they need them. What it never holds is
 * the encryption key: the values are encrypted by the data tier, so a client
 * machine that is lost or stolen contains no means of reading the registry.
 *
 * <h2>Only the triage category is required</h2>
 * Everything else can be empty. At a scene people are recorded before anybody
 * knows who they are, and a form that insisted on a name would mean
 * unidentified casualties went unrecorded, which is the opposite of what a
 * registry is for. The table shows those records as unidentified with their
 * record number rather than inventing a placeholder name.
 */
public class CasualtyRegistryController implements Initializable {

    @FXML
    private TextArea reportArea;
    @FXML
    private ComboBox<TriageCategory> triageCombo;
    @FXML
    private ComboBox<TriageCategory> retriageCombo;
    @FXML
    private TextField nameField;
    @FXML
    private TextField ageField;
    @FXML
    private TextField contactField;
    @FXML
    private TextField locationField;
    @FXML
    private TextField notesField;
    @FXML
    private TextField hospitalField;
    @FXML
    private Label messageLabel;
    @FXML
    private Label summaryLabel;

    @FXML
    private TableView<Casualty> casualtyTable;
    @FXML
    private TableColumn<Casualty, String> casualtyIdColumn;
    @FXML
    private TableColumn<Casualty, String> casualtyNameColumn;
    @FXML
    private TableColumn<Casualty, String> casualtyTriageColumn;
    @FXML
    private TableColumn<Casualty, String> casualtyStatusColumn;
    @FXML
    private TableColumn<Casualty, String> casualtyLocationColumn;

    private final AppContext context = AppContext.getInstance();
    private String reference;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageLabel.setText("");
        reference = DashboardController.getSelectedReference();

        triageCombo.setItems(FXCollections.observableArrayList(TriageCategory.values()));
        retriageCombo.setItems(FXCollections.observableArrayList(TriageCategory.values()));

        configureColumns();
        loadReport();
        loadCasualties();
    }

    private void configureColumns() {
        casualtyIdColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getCasualtyId()));
        casualtyNameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getDisplayName()));
        casualtyTriageColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getTriage() == null
                        ? "" : cell.getValue().getTriage().toString()));
        casualtyStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                statusTextFor(cell.getValue())));
        casualtyLocationColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getLocation() == null
                        ? "" : cell.getValue().getLocation()));
    }

    /**
     * The status, with the hospital when there is one.
     *
     * "Referred to hospital" on its own leaves the reader asking which, and
     * that is the question somebody looking for a relative is asking.
     *
     * @param casualty the record
     * @return the text for the column
     */
    private String statusTextFor(Casualty casualty) {
        if (casualty.getStatus() == null) {
            return "";
        }
        if (casualty.getStatus() == CasualtyStatus.REFERRED
                && casualty.getReferredTo() != null) {
            return casualty.getStatus().getLabel() + ": " + casualty.getReferredTo();
        }
        return casualty.getStatus().getLabel();
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
                        + found.getType().getLabel() + "  at "
                        + found.getLocation().getAddress())
                .orElse("The report " + reference + " could not be found."));
    }

    private void loadCasualties() {
        if (reference == null) {
            return;
        }
        try {
            List<Casualty> casualties =
                    context.getCasualtyService().findForReport(reference);
            casualtyTable.setItems(FXCollections.observableArrayList(casualties));

            Map<TriageCategory, Integer> counts =
                    context.getCasualtyService().summaryFor(reference);

            StringBuilder summary = new StringBuilder();
            for (TriageCategory category : TriageCategory.values()) {
                if (summary.length() > 0) {
                    summary.append("   ");
                }
                summary.append(category.getLabel()).append(": ")
                        .append(counts.getOrDefault(category, 0));
            }
            summaryLabel.setText(summary.toString());

        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        messageLabel.setText("");

        if (reference == null) {
            messageLabel.setText("No disaster is selected.");
            return;
        }
        TriageCategory triage = triageCombo.getValue();
        if (triage == null) {
            messageLabel.setText("Choose a triage category. It is the one thing "
                    + "a casualty record cannot be without.");
            return;
        }

        Integer age;
        try {
            age = parseAge(ageField.getText());
        } catch (NumberFormatException e) {
            messageLabel.setText("Age must be a whole number, or left empty.");
            return;
        }

        try {
            Casualty registered = context.getCasualtyService().register(reference,
                    nameField.getText(), age, contactField.getText(), triage,
                    locationField.getText(), notesField.getText(),
                    context.getCurrentUsername());

            Navigator.showInfo("Casualty registered",
                    registered.getDisplayName() + " was recorded as "
                            + triage.getLabel() + ".");
            clearForm();

        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());

        } finally {
            loadCasualties();
        }
    }

    private Integer parseAge(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return Integer.valueOf(text.trim());
    }

    private void clearForm() {
        nameField.clear();
        ageField.clear();
        contactField.clear();
        locationField.clear();
        notesField.clear();
        triageCombo.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRetriage(ActionEvent event) {
        messageLabel.setText("");

        Casualty selected = casualtyTable.getSelectionModel().getSelectedItem();
        TriageCategory category = retriageCombo.getValue();

        if (selected == null) {
            messageLabel.setText("Choose a casualty first.");
            return;
        }
        if (category == null) {
            messageLabel.setText("Choose the new triage category.");
            return;
        }

        try {
            context.getCasualtyService().retriage(selected.getCasualtyId(), category,
                    context.getCurrentUsername());
        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        } finally {
            loadCasualties();
        }
    }

    @FXML
    private void handleRefer(ActionEvent event) {
        messageLabel.setText("");

        Casualty selected = casualtyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Choose a casualty first.");
            return;
        }
        String hospital = hospitalField.getText();
        if (hospital == null || hospital.trim().isEmpty()) {
            messageLabel.setText("Name the hospital. A referral that does not say "
                    + "where somebody was sent is not a referral.");
            return;
        }

        try {
            context.getCasualtyService().refer(selected.getCasualtyId(), hospital,
                    context.getCurrentUsername());
            hospitalField.clear();

        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        } finally {
            loadCasualties();
        }
    }

    @FXML
    private void handleTreatedOnSite(ActionEvent event) {
        messageLabel.setText("");

        Casualty selected = casualtyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Choose a casualty first.");
            return;
        }

        try {
            context.getCasualtyService().updateStatus(selected.getCasualtyId(),
                    CasualtyStatus.TREATED_ON_SITE, context.getCurrentUsername());
        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        } finally {
            loadCasualties();
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        messageLabel.setText("");
        loadCasualties();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        Navigator.goTo("DashboardView.fxml", "Coordinator dashboard");
    }
}
