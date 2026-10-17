package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.repository.AuditChainStatus;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The administrator's screen for creating accounts, changing what a user may
 * do, and reviewing the audit log.
 */
public class AccountManagementController implements Initializable {

    /** How many audit entries to show, newest last. */
    private static final int AUDIT_ENTRIES_SHOWN = 30;

    @FXML
    private Label userLabel;
    @FXML
    private TableView<User> userTable;
    @FXML
    private TableColumn<User, String> usernameColumn;
    @FXML
    private TableColumn<User, String> nameColumn;
    @FXML
    private TableColumn<User, String> roleColumn;
    @FXML
    private TableColumn<User, String> departmentColumn;
    @FXML
    private TableColumn<User, String> activeColumn;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField fullNameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private ComboBox<UserRole> roleCombo;
    @FXML
    private ComboBox<Department> departmentCombo;
    @FXML
    private Label messageLabel;
    @FXML
    private ComboBox<Department> availabilityCombo;
    @FXML
    private Label availabilityLabel;
    @FXML
    private Button dashboardButton;

    private final AppContext context = AppContext.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageLabel.setText("");
        userLabel.setText("Signed in as "
                + context.getCurrentUser().map(User::getFullName).orElse("unknown"));

        roleCombo.setItems(FXCollections.observableArrayList(UserRole.values()));

        departmentCombo.setButtonCell(departmentCell(false));
        departmentCombo.setCellFactory(list -> departmentCell(false));
        departmentCombo.setItems(FXCollections.observableArrayList(
                context.getDepartmentService().findAll()));

        availabilityCombo.setButtonCell(departmentCell(true));
        availabilityCombo.setCellFactory(list -> departmentCell(true));
        refreshAvailabilityCombo();
        availabilityLabel.setText("");

        configureColumns();
        refreshUsers();
        hideWhatThisRoleCannotUse();
    }

    /**
     * Removes the link to the coordinator dashboard for anybody who cannot
     * use it.
     *
     * The dashboard fills its table from the assessment queue, and the
     * protocol permits those two operations to a coordinator only. An
     * administrator who followed this link therefore reached a screen that
     * asked the server for something it would refuse, and was shown a
     * failure for pressing a button the application had offered them.
     *
     * Hiding the button rather than handling the refusal is the right fix,
     * because the refusal is correct: an administrator manages accounts and
     * the audit trail, and does not coordinate a response. The server's rule
     * is unchanged, and the screen now agrees with it.
     */
    private void hideWhatThisRoleCannotUse() {
        boolean coordinator = context.getCurrentUser()
                .map(user -> user.getRole() == UserRole.COORDINATOR)
                .orElse(false);
        dashboardButton.setVisible(coordinator);
        dashboardButton.setManaged(coordinator);
    }

    private void configureColumns() {
        usernameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getUsername()));
        nameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getFullName()));
        roleColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getRole().getLabel()));
        departmentColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue() instanceof DepartmentResponder
                        ? ((DepartmentResponder) cell.getValue()).getDepartmentId() : "-"));
        activeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isActive() ? "Yes" : "No"));
    }

    /**
     * Reloads the list of accounts, keeping whichever account was highlighted
     * so that a change to it can be seen on the row it was made from.
     */
    private void refreshUsers() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        String selectedUsername = selected == null ? null : selected.getUsername();

        userTable.setItems(FXCollections.observableArrayList(
                context.getAccountService().findAll()));

        if (selectedUsername != null) {
            for (User user : userTable.getItems()) {
                if (selectedUsername.equals(user.getUsername())) {
                    userTable.getSelectionModel().select(user);
                    break;
                }
            }
        }
    }

    /**
     * Creates an account from the details entered.
     *
     * @param event the button press
     */
    @FXML
    private void handleCreate(ActionEvent event) {
        messageLabel.setText("");

        if (roleCombo.getValue() == null) {
            messageLabel.setText("Choose a role for the new account.");
            return;
        }

        String departmentId = null;
        if (roleCombo.getValue() == UserRole.RESPONDER) {
            if (departmentCombo.getValue() == null) {
                messageLabel.setText("A responder must be given a department.");
                return;
            }
            departmentId = departmentCombo.getValue().getDepartmentId();
        }

        try {
            context.getAccountService().create(
                    usernameField.getText(), passwordField.getText(),
                    fullNameField.getText(), roleCombo.getValue(),
                    departmentId, context.getCurrentUsername());

            Navigator.showInfo("Account created",
                    usernameField.getText()
                            + " must replace the initial password at first sign-in.");
            clearForm();
            refreshUsers();
        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Empties the form after an account is created.
     */
    private void clearForm() {
        usernameField.clear();
        fullNameField.clear();
        passwordField.clear();
        roleCombo.getSelectionModel().clearSelection();
        departmentCombo.getSelectionModel().clearSelection();
    }

    /**
     * Deactivates the selected account, which stops it being used to sign in
     * without removing the record of what that user did.
     *
     * @param event the button press
     */
    @FXML
    private void handleDeactivate(ActionEvent event) {
        messageLabel.setText("");

        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Select an account first.");
            return;
        }
        if (selected.getUsername().equals(context.getCurrentUsername())) {
            messageLabel.setText("You cannot deactivate the account you are using.");
            return;
        }

        if (!selected.isActive()) {
            messageLabel.setText(selected.getUsername()
                    + " is already deactivated.");
            return;
        }

        try {
            context.getAccountService().deactivate(
                    selected.getUsername(), context.getCurrentUsername());
            Navigator.showInfo("Account deactivated",
                    selected.getUsername() + " can no longer sign in. "
                            + "Use Reactivate selected to restore the account.");
            refreshUsers();
        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Returns a deactivated account to use. The account keeps the role and the
     * password it had, so an account suspended while a matter is looked into
     * can be restored without being created again.
     *
     * @param event the button press
     */
    @FXML
    private void handleReactivate(ActionEvent event) {
        messageLabel.setText("");

        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Select an account first.");
            return;
        }
        if (selected.isActive()) {
            messageLabel.setText(selected.getUsername() + " is already active.");
            return;
        }

        try {
            context.getAccountService().activate(
                    selected.getUsername(), context.getCurrentUsername());
            Navigator.showInfo("Account reactivated",
                    selected.getUsername() + " can sign in again.");
            refreshUsers();
        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Changes the role held by the selected account.
     *
     * @param event the button press
     */
    @FXML
    private void handleChangeRole(ActionEvent event) {
        messageLabel.setText("");

        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Select an account first.");
            return;
        }

        ChoiceDialog<UserRole> dialog =
                new ChoiceDialog<>(selected.getRole(), UserRole.values());
        dialog.setTitle("Change role");
        dialog.setHeaderText("Role for " + selected.getUsername());
        dialog.setContentText("New role");

        Optional<UserRole> chosen = dialog.showAndWait();
        if (chosen.isEmpty() || chosen.get() == selected.getRole()) {
            return;
        }

        try {
            context.getAccountService().assignRole(
                    selected.getUsername(), chosen.get(), context.getCurrentUsername());
            Navigator.showInfo("Role changed",
                    selected.getUsername() + " is now a " + chosen.get().getLabel() + ".");
            refreshUsers();

        } catch (IllegalArgumentException | IllegalStateException e) {
            // This call had no handling at all, because in DRS-Initial the
            // only way it could fail was an account that had been deleted
            // from under the screen, which nothing could do. The server now
            // refuses an administrator changing their own role, so a refusal
            // has become a thing that happens and has to be shown rather than
            // thrown out of a button press.
            messageLabel.setText(e.getMessage());
        }
    }

    /**
     * Shows the most recent entries in the audit log.
     *
     * @param event the button press
     */
    @FXML
    private void handleViewAudit(ActionEvent event) {
        List<AuditLogEntry> entries = context.getAuditService().findAll();
        if (entries.isEmpty()) {
            Navigator.showInfo("Audit log", "Nothing has been recorded yet.");
            return;
        }

        int from = Math.max(0, entries.size() - AUDIT_ENTRIES_SHOWN);
        StringBuilder text = new StringBuilder();
        for (AuditLogEntry entry : entries.subList(from, entries.size())) {
            text.append(entry).append("\n");
        }
        Navigator.showInfo("Audit log, most recent "
                + (entries.size() - from) + " entries", text.toString());
    }

    /**
     * Checks that the audit trail has not been altered, and reports what was
     * found.
     *
     * The check is done by the server over the stored entries, not here. A
     * client that verified its own copy would prove nothing: it would be
     * checking the figures it had just been handed. What this button does is
     * ask, and then say plainly which of the three answers came back, because
     * "the chain could not be checked" and "the chain is intact" are different
     * answers and showing them identically would hide the difference.
     *
     * @param event the button press
     */
    @FXML
    private void handleVerifyChain(ActionEvent event) {
        AuditChainStatus status = context.getAuditService().verifyChain();

        if (!status.supported()) {
            Navigator.showWarning("The audit chain cannot be checked",
                    status.detail());
            return;
        }
        if (status.intact()) {
            Navigator.showInfo("The audit chain is intact", status.detail());
            return;
        }
        Navigator.showError("The audit chain is broken",
                status.detail()
                        + "\n\nThe first entry that does not add up is number "
                        + status.brokenAtSequence() + ". Entries before it are "
                        + "unaffected; every entry after it depends on the one "
                        + "that broke, so the trail cannot be relied on from "
                        + "that point on.");
    }

    /**
     * Builds a cell naming a department, optionally with its availability.
     *
     * @param showAvailability true to append whether the department can take work
     * @return a cell for a department
     */
    private ListCell<Department> departmentCell(boolean showAvailability) {
        return new ListCell<>() {
            @Override
            protected void updateItem(Department department, boolean empty) {
                super.updateItem(department, empty);
                if (empty || department == null) {
                    setText(null);
                    return;
                }
                setText(showAvailability
                        ? department.getName() + "  ("
                        + (department.isAvailable() ? "available" : "unavailable") + ")"
                        : department.getName());
            }
        };
    }

    /**
     * Reloads the list of departments shown in the availability box, keeping
     * whichever one was selected. The value is cleared before the fresh record
     * is put back so that the box redraws with the availability just changed
     * rather than holding the text it was showing before.
     */
    private void refreshAvailabilityCombo() {
        Department previous = availabilityCombo.getValue();
        String previousId = previous == null ? null : previous.getDepartmentId();

        availabilityCombo.setValue(null);
        availabilityCombo.setItems(FXCollections.observableArrayList(
                context.getDepartmentService().findAll()));

        if (previousId != null) {
            for (Department department : availabilityCombo.getItems()) {
                if (previousId.equals(department.getDepartmentId())) {
                    availabilityCombo.setValue(department);
                    break;
                }
            }
        }
    }

    /**
     * Switches a department between available and unavailable. An unavailable
     * department still receives its dispatch notice, but the notice is
     * recorded as having failed, which is how the failure path in the response
     * coordination can be exercised from the interface.
     *
     * <h2>One call where there used to be three</h2>
     * This screen used to fetch the department, flip the field, save it
     * through the repository and then write its own audit entry. None of that
     * survives three tiers: a client holds no repository, and an audit entry
     * written by a client is a claim rather than a record, because the server
     * has no way to know whether the thing being claimed actually happened.
     *
     * <p>So the three steps are now one operation, and the department shown
     * afterwards is the one the operation returned rather than the object this
     * screen was holding with a field altered. A change that was refused
     * therefore cannot be displayed as though it had been made.
     *
     * @param event the button press
     */
    @FXML
    private void handleToggleAvailability(ActionEvent event) {
        messageLabel.setText("");

        Department selected = availabilityCombo.getValue();
        if (selected == null) {
            messageLabel.setText("Select a department first.");
            return;
        }

        try {
            Department changed = context.getDepartmentService().setAvailability(
                    selected.getDepartmentId(), !selected.isAvailable(),
                    context.getCurrentUsername());

            availabilityLabel.setText(changed.getName() + " is now "
                    + (changed.isAvailable() ? "available" : "unavailable") + ".");
            refreshAvailabilityCombo();

        } catch (IllegalArgumentException | IllegalStateException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        refreshUsers();
        refreshAvailabilityCombo();
    }

    @FXML
    private void handleDashboard(ActionEvent event) {
        Navigator.goTo("DashboardView.fxml", "Coordinator dashboard");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        context.getAuthenticationService().logout();
        Navigator.goTo("WelcomeView.fxml", "Disaster Response System");
    }
}
