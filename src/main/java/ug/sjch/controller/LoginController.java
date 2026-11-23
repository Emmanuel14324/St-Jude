package ug.sjch.controller;

import java.sql.SQLException;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import java.util.prefs.Preferences;

import ug.sjch.MainApp;
import ug.sjch.model.User;
import ug.sjch.security.UserSession;
import ug.sjch.service.AuthenticationService;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private Button visibilityButton;
    @FXML private CheckBox rememberCheckBox;
    @FXML private Label errorLabel;
    private boolean passwordVisible;
    private final Preferences preferences =
            Preferences.userRoot().node("ug/sjch");

    private final AuthenticationService authenticationService =
            new AuthenticationService();

    @FXML
    private void initialize() {
        usernameField.setText(preferences.get("rememberedUsername", ""));
        rememberCheckBox.setSelected(
                !preferences.get("rememberedUsername", "").isBlank()
        );
        visiblePasswordField.setManaged(false);
        visiblePasswordField.setVisible(false);
    }

    @FXML
    private void handleLogin() {
        errorLabel.setText("");
        try {
            User user = authenticationService.authenticate(
                    usernameField.getText(),
                    passwordVisible
                            ? visiblePasswordField.getText()
                            : passwordField.getText()
            );
            if (rememberCheckBox.isSelected()) {
                preferences.put("rememberedUsername", usernameField.getText().trim());
            } else {
                preferences.remove("rememberedUsername");
            }
            openDashboard(user);
        } catch (IllegalArgumentException exception) {
            errorLabel.setText(exception.getMessage());
        } catch (SQLException exception) {
            errorLabel.setText(
                    "Cannot connect to the hospital database. Please try again."
            );
        } catch (IllegalStateException exception) {
            errorLabel.setText(exception.getMessage());
        } catch (Exception exception) {
            errorLabel.setText("The dashboard could not be opened: "
                    + exception.getMessage());
        }
    }

    private void openDashboard(User user) throws Exception {
        String dashboardPath = dashboardPathForRole(user.roleName());
        Parent root = FXMLLoader.load(
                MainApp.class.getResource(dashboardPath)
        );
        Stage stage = (Stage) usernameField.getScene().getWindow();
        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource("/ug/sjch/styles/application.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.setTitle("SJCH " + user.roleName() + " Dashboard");
    }

    static String dashboardPathForRole(String roleName) {
        String normalizedRole = roleName == null
                ? "" : roleName.toLowerCase(Locale.ROOT);
        return switch (normalizedRole) {
            case "administrator" ->
                    "/ug/sjch/views/admin-dashboard.fxml";
            case "doctor", "medical officer" ->
                    "/ug/sjch/views/outpatient-dashboard.fxml";
            case "nurse", "senior nurse" ->
                    "/ug/sjch/views/nursing-dashboard.fxml";
            case "lab technician", "laboratory technician" ->
                    "/ug/sjch/views/laboratory-dashboard.fxml";
            case "pharmacist", "pharmacy assistant" ->
                    "/ug/sjch/views/pharmacy-dashboard.fxml";
            case "ward manager" ->
                    "/ug/sjch/views/inpatient-dashboard.fxml";
            case "accountant", "finance officer" ->
                    "/ug/sjch/views/finance-dashboard.fxml";
            case "receptionist" -> "/ug/sjch/views/reception-dashboard.fxml";
            default -> "/ug/sjch/views/role-dashboard.fxml";
        };
    }

    @FXML
    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            visiblePasswordField.setText(passwordField.getText());
            visiblePasswordField.setManaged(true);
            visiblePasswordField.setVisible(true);
            passwordField.setManaged(false);
            passwordField.setVisible(false);
            visibilityButton.setText("Hide");
        } else {
            passwordField.setText(visiblePasswordField.getText());
            passwordField.setManaged(true);
            passwordField.setVisible(true);
            visiblePasswordField.setManaged(false);
            visiblePasswordField.setVisible(false);
            visibilityButton.setText("Show");
        }
    }
}
