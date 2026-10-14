package com.cqu.drs.controller;

import com.cqu.drs.AppContext;
import com.cqu.drs.Navigator;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

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

        openScreenFor(result.get());
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
