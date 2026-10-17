package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Signs a member of staff in and sends them to the screen for their role.
 */
public class LoginController implements Initializable {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label messageLabel;

    private final AppContext context = AppContext.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageLabel.setText("");
    }

    /**
     * Checks the credentials and opens the screen for the user's role. A
     * failed sign-in gives one message whatever the cause, so that an attacker
     * cannot learn which usernames exist.
     *
     * @param event the button press
     */
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.trim().isEmpty()
                || password == null || password.isEmpty()) {
            messageLabel.setText("Enter both a username and a password.");
            return;
        }

        Optional<User> result = context.getAuthenticationService()
                .login(username.trim(), password);

        if (result.isEmpty()) {
            messageLabel.setText("Those details were not accepted. "
                    + "Check them and try again.");
            passwordField.clear();
            return;
        }

        User user = result.get();

        if (user.isMustChangePassword() && !changePasswordNow(user)) {
            // The change was refused or abandoned. The session is ended rather
            // than left open, because a seeded account whose password is
            // published in the documentation must not reach a dashboard still
            // using it.
            context.getAuthenticationService().logout();
            messageLabel.setText("The password was not changed, so you have not "
                    + "been signed in. Sign in again to set a new password.");
            passwordField.clear();
            return;
        }

        openScreenFor(user);
    }

    /**
     * Asks a user signing in on a starting password to choose a new one.
     *
     * The three seeded accounts exist so that the system can be used on a
     * fresh database, and their passwords are written in the README and the
     * report. An account that reaches a dashboard still using one of them is
     * an account anybody who has read the documentation can sign in as, so
     * the flag the server sets on those accounts is acted on here rather than
     * merely carried.
     *
     * The change goes through the server like any other operation. The client
     * does not decide that the password is acceptable, it asks, and shows
     * whatever the server says when it refuses.
     *
     * @param user the user who has just signed in
     * @return true when the password was changed and the user may continue
     */
    private boolean changePasswordNow(User user) {
        while (true) {
            Optional<String> chosen = askForNewPassword(user);
            if (chosen.isEmpty()) {
                return false;
            }

            try {
                context.getAuthenticationService().changeCurrentPassword(chosen.get());
                Navigator.showInfo("Password changed",
                        "Your new password is in use from now on. The starting "
                                + "password no longer works.");
                return true;

            } catch (IllegalArgumentException | IllegalStateException e) {
                // Refused by the server, usually for being too short. Say so
                // and ask again rather than sending the person back to the
                // sign-in screen to start over.
                Navigator.showWarning("That password was not accepted", e.getMessage());
            }
        }
    }

    /**
     * Asks for a new password twice, and returns it when both entries agree.
     *
     * @param user the user choosing the password
     * @return the new password, or empty when the person cancelled
     */
    private Optional<String> askForNewPassword(User user) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Choose a new password");
        dialog.setHeaderText(user.getFullName() + ", this account is still using "
                + "the password it was created with.\nChoose a new one to continue.");

        ButtonType change = new ButtonType("Change password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(change, ButtonType.CANCEL);

        PasswordField first = new PasswordField();
        first.setPromptText("At least " + DrsConstants.PASSWORD_MIN_LENGTH + " characters");
        PasswordField second = new PasswordField();
        second.setPromptText("Type it again");
        Label mismatch = new Label();
        mismatch.setStyle("-fx-text-fill: #B3261E;");

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(8);
        fields.add(new Label("New password"), 0, 0);
        fields.add(first, 1, 0);
        fields.add(new Label("Confirm"), 0, 1);
        fields.add(second, 1, 1);
        fields.add(mismatch, 1, 2);
        dialog.getDialogPane().setContent(fields);

        Node confirm = dialog.getDialogPane().lookupButton(change);
        confirm.setDisable(true);

        Runnable check = () -> {
            String a = first.getText();
            String b = second.getText();
            boolean longEnough = a != null && a.length() >= DrsConstants.PASSWORD_MIN_LENGTH;
            boolean same = a != null && a.equals(b);
            confirm.setDisable(!(longEnough && same));
            if (b != null && !b.isEmpty() && !same) {
                mismatch.setText("The two entries do not match.");
            } else if (!longEnough && a != null && !a.isEmpty()) {
                mismatch.setText("At least " + DrsConstants.PASSWORD_MIN_LENGTH
                        + " characters.");
            } else {
                mismatch.setText("");
            }
        };
        first.textProperty().addListener((observed, was, now) -> check.run());
        second.textProperty().addListener((observed, was, now) -> check.run());

        dialog.setResultConverter(button -> button == change ? first.getText() : null);
        Platform.runLater(first::requestFocus);

        return dialog.showAndWait();
    }

    /**
     * Sends a signed-in user to the screen their role uses.
     *
     * @param user the user who signed in
     */
    private void openScreenFor(User user) {
        UserRole role = user.getRole();
        if (role == UserRole.COORDINATOR) {
            Navigator.goTo("DashboardView.fxml", "Coordinator dashboard");
        } else if (role == UserRole.RESPONDER) {
            Navigator.goTo("DepartmentResponseView.fxml", "Department response");
        } else {
            Navigator.goTo("AccountManagementView.fxml", "Accounts and roles");
        }
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
