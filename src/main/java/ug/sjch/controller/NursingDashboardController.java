package ug.sjch.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

import ug.sjch.MainApp;
import ug.sjch.dao.NursingDashboardDao;
import ug.sjch.dao.NursingDashboardDao.AdmittedPatient;
import ug.sjch.dao.NursingDashboardDao.MedicationAdministrationRow;
import ug.sjch.dao.NursingDashboardDao.MedicationOption;
import ug.sjch.dao.NursingDashboardDao.Metrics;
import ug.sjch.dao.NursingDashboardDao.ObservationRow;
import ug.sjch.model.User;
import ug.sjch.security.UserSession;

public class NursingDashboardController {
    private static final Logger LOGGER =
            Logger.getLogger(NursingDashboardController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final String[] CARD_STYLES = {
            "reception-metric-blue", "reception-metric-pink",
            "reception-metric-amber", "reception-metric-green"
    };

    @FXML private Label sidebarUserLabel;
    @FXML private Label sidebarRoleLabel;
    @FXML private Label topUserLabel;
    @FXML private Label topRoleLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private Label assignedCountLabel;
    @FXML private Label attentionCountLabel;
    @FXML private Label vitalsCountLabel;
    @FXML private Label assignedStateLabel;
    @FXML private Label attentionStateLabel;
    @FXML private Label vitalsStateLabel;
    @FXML private Label activitiesStateLabel;
    @FXML private VBox sidebar;
    @FXML private Region sidebarResizeHandle;
    @FXML private Button inpatientDashboardButton;
    @FXML private Button dashboardButton;
    @FXML private Button assignedPatientsButton;
    @FXML private Button patientCheckupButton;
    @FXML private Button recordVitalsButton;
    @FXML private Button nursingNotesButton;
    @FXML private Button medicationAdministrationButton;
    @FXML private Button patientMonitoringButton;
    @FXML private Button wardReportsButton;
    @FXML private GridPane metricGrid;
    @FXML private ScrollPane contentScroll;
    @FXML private VBox dashboardContent;

    private final NursingDashboardDao dashboardDao = new NursingDashboardDao();
    private Runnable activeRefresh = this::loadDashboard;
    private Button activeNavigation = dashboardButton;

    @FXML
    private void initialize() {
        SidebarResizer.install(sidebar, sidebarResizeHandle);
        boolean seniorNurse = UserSession.hasRole("Senior Nurse");
        inpatientDashboardButton.setVisible(seniorNurse);
        inpatientDashboardButton.setManaged(seniorNurse);
        if (!isNursingUser()) {
            setMetricPlaceholders();
            setStatus("Nursing staff session is not available", "status-warning");
            setModuleStates("Sign in with a nursing account to view this section.");
            setActionsDisabled(true);
            return;
        }

        User user = UserSession.getCurrentUser();
        sidebarUserLabel.setText(user.username());
        sidebarRoleLabel.setText(user.roleName());
        topUserLabel.setText(user.username());
        topRoleLabel.setText(user.roleName());
        dateLabel.setText(LocalDate.now().format(DATE_FORMAT));
        setActionsDisabled(false);
        loadDashboard();
    }

    private boolean isNursingUser() {
        return UserSession.hasRole("Nurse")
                || UserSession.hasRole("Senior Nurse");
    }

    private void setActionsDisabled(boolean disabled) {
        for (Button button : new Button[] {
                dashboardButton, assignedPatientsButton, patientCheckupButton,
                recordVitalsButton, nursingNotesButton,
                medicationAdministrationButton, patientMonitoringButton,
                wardReportsButton
        }) {
            button.setDisable(disabled);
        }
    }

    @FXML
    private void handleDashboard() {
        if (!requireNurse()) {
            return;
        }
        contentScroll.setContent(dashboardContent);
        setActiveNavigation(dashboardButton);
        activeRefresh = this::loadDashboard;
        loadDashboard();
    }

    @FXML
    private void handleAssignedPatients() {
        if (requireNurse()) {
            showAdmittedPatientsPage();
        }
    }

    @FXML
    private void handlePatientCheckup() {
        if (requireNurse()) {
            showAdmittedPatientsPage();
        }
    }

    @FXML
    private void handleRecordVitals() {
        if (requireNurse()) {
            showObservationPage(true);
        }
    }

    @FXML
    private void handleNursingNotes() {
        if (requireNurse()) {
            showObservationPage(false);
        }
    }

    @FXML
    private void handleMedicationAdministration() {
        if (requireNurse()) {
            showMedicationAdministrationPage();
        }
    }

    @FXML
    private void handlePatientMonitoring() {
        if (requireNurse()) {
            showMonitoringPage();
        }
    }

    @FXML
    private void handleWardReports() {
        if (requireNurse()) {
            showWardReportsPage();
        }
    }

    private void loadDashboard() {
        try {
            Metrics metrics = dashboardDao.loadMetrics();
            metricGrid.getChildren().clear();
            addMetric("Admitted patients", metrics.assignedPatients(), "♙", 0);
            addMetric("Attention queue", metrics.patientsRequiringAttention(),
                    "!", 1);
            addMetric("Vitals awaiting today",
                    metrics.vitalsAwaitingRecording(), "♥", 2);
            addMetric("Current admissions", metrics.currentAdmissions(), "⌂", 3);

            List<AdmittedPatient> patients =
                    dashboardDao.loadAdmittedPatients();
            assignedCountLabel.setText(String.valueOf(patients.size()));
            assignedStateLabel.setText(patients.isEmpty()
                    ? "No patients are currently admitted."
                    : patients.size() + " admitted patient"
                    + (patients.size() == 1 ? "" : "s")
                    + " are available in the patient list.");
            if (metrics.patientsRequiringAttention() == null) {
                attentionCountLabel.setText("Not configured");
                attentionStateLabel.setText(
                        "A triage/attention flag is not configured in the database.");
            } else {
                attentionCountLabel.setText(
                        String.valueOf(metrics.patientsRequiringAttention()));
            }
            if (metrics.vitalsAwaitingRecording() == null) {
                vitalsCountLabel.setText("Not configured");
                vitalsStateLabel.setText(
                        "Observation records are not configured.");
            } else {
                vitalsCountLabel.setText(
                        String.valueOf(metrics.vitalsAwaitingRecording()));
                vitalsStateLabel.setText("Based on today's recorded observations.");
            }
            activitiesStateLabel.setText(
                    "Recorded observations and nursing notes are available "
                            + "under Patient Monitoring.");
            setStatus(metrics.currentAdmissions() == null
                            ? "Nursing modules need setup"
                            : "Nursing workspace connected",
                    metrics.currentAdmissions() == null
                            ? "status-warning" : "status-connected");
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load nursing dashboard data", exception);
            setMetricPlaceholders();
            setModuleStates(
                    "Admitted-patient or nursing data is unavailable.");
            setStatus("Nursing data is unavailable", "status-warning");
        }
    }

    private void setMetricPlaceholders() {
        metricGrid.getChildren().clear();
        addMetric("Admitted patients", null, "♙", 0);
        addMetric("Attention queue", null, "!", 1);
        addMetric("Vitals awaiting today", null, "♥", 2);
        addMetric("Current admissions", null, "⌂", 3);
    }

    private void addMetric(String title, Integer value, String icon, int column) {
        String displayValue = value == null
                ? "Not configured" : String.valueOf(value);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-label");
        titleLabel.setWrapText(true);
        Label valueLabel = new Label(displayValue);
        valueLabel.getStyleClass().add("metric-value");
        valueLabel.setWrapText(true);
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("metric-icon");
        VBox card = new VBox(iconLabel, valueLabel, titleLabel);
        card.getStyleClass().addAll(
                "metric-card", "nursing-metric-card", CARD_STYLES[column]);
        GridPane.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        metricGrid.add(card, column, 0);
        switch (column) {
            case 0 -> assignedCountLabel.setText(displayValue);
            case 1 -> attentionCountLabel.setText(displayValue);
            case 2 -> vitalsCountLabel.setText(displayValue);
            default -> {
            }
        }
    }

    private void setModuleStates(String message) {
        assignedStateLabel.setText(message);
        attentionStateLabel.setText(message);
        vitalsStateLabel.setText(message);
        activitiesStateLabel.setText(message);
    }

    private void setStatus(String text, String stateClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(
                "status-warning", "status-connected");
        statusLabel.getStyleClass().add(stateClass);
    }

    private void showAdmittedPatientsPage() {
        VBox page = createPage("Admitted Patients",
                "Current inpatient census from ADMISSIONS and the central PATIENTS register.");
        VBox panel = createPanel("Active patients",
                "Only admissions with status ADMITTED are shown.");
        TableView<AdmittedPatient> table = new TableView<>();
        table.getStyleClass().add("dashboard-table");
        table.getColumns().add(column("Patient No.", 120,
                AdmittedPatient::patientNumber));
        table.getColumns().add(column("Patient", 170,
                AdmittedPatient::patientName));
        table.getColumns().add(column("Ward", 130,
                row -> valueOrDash(row.ward())));
        table.getColumns().add(column("Bed", 90,
                row -> valueOrDash(row.bedNumber())));
        table.getColumns().add(column("Admitted", 170,
                AdmittedPatient::admittedAt));
        table.setPlaceholder(new Label("No patients are currently admitted."));
        panel.getChildren().add(table);
        Label feedback = feedbackLabel();
        page.getChildren().addAll(panel, feedback);
        refreshPatients(table, feedback);
        showPage(page, assignedPatientsButton,
                () -> refreshPatients(table, feedback));
    }

    private void showObservationPage(boolean includeVitals) {
        VBox page = createPage(includeVitals
                        ? "Record Vital Signs" : "Nursing Notes",
                "New entries are appended to the active admission. Existing "
                        + "clinical observations are immutable; the current "
                        + "schema has no linked correction/addendum workflow.");
        VBox form = createPanel(includeVitals
                        ? "Clinical observation" : "Patient care note",
                "Use the values actually observed; blank fields remain unrecorded. "
                        + "Saved observations cannot be edited or deleted.");
        ComboBox<AdmittedPatient> patient = new ComboBox<>();
        patient.setPromptText("Select admitted patient");
        patient.setMaxWidth(Double.MAX_VALUE);
        TextField temperature = textField("Temperature °C (25–45)");
        TextField pulse = textField("Pulse /min (20–250)");
        TextField systolic = textField("Systolic mmHg (40–300)");
        TextField diastolic = textField("Diastolic mmHg (20–200)");
        TextField respiration = textField("Respiratory rate /min (4–80)");
        TextField oxygen = textField("Oxygen saturation % (50–100)");
        TextArea notes = new TextArea();
        notes.setPromptText("Nursing notes (optional)");
        notes.setPrefRowCount(4);
        notes.setWrapText(true);
        if (!includeVitals) {
            temperature.setManaged(false);
            temperature.setVisible(false);
            pulse.setManaged(false);
            pulse.setVisible(false);
            systolic.setManaged(false);
            systolic.setVisible(false);
            diastolic.setManaged(false);
            diastolic.setVisible(false);
            respiration.setManaged(false);
            respiration.setVisible(false);
            oxygen.setManaged(false);
            oxygen.setVisible(false);
        }
        Label feedback = feedbackLabel();
        Button save = button("Save " + (includeVitals
                ? "observation" : "nursing note"), "primary-button");
        boolean configured = false;
        try {
            configured = dashboardDao.isObservationConfigured();
        } catch (SQLException exception) {
            LOGGER.log(Level.FINE, "Unable to inspect nursing schema", exception);
        }
        save.setDisable(!configured);
        if (!configured) {
            feedback.setText(
                    "Not configured: apply the additive nursing workflow DDL "
                            + "before recording observations or notes.");
        }
        save.setOnAction(event -> runAction(feedback, () -> {
            if (patient.getValue() == null) {
                throw new IllegalArgumentException(
                        "Select an active admitted patient.");
            }
            dashboardDao.recordObservation(
                    patient.getValue().admissionId(),
                    includeVitals ? temperature.getText() : null,
                    includeVitals ? pulse.getText() : null,
                    includeVitals ? systolic.getText() : null,
                    includeVitals ? diastolic.getText() : null,
                    includeVitals ? respiration.getText() : null,
                    includeVitals ? oxygen.getText() : null,
                    notes.getText(), currentUserId());
            temperature.clear();
            pulse.clear();
            systolic.clear();
            diastolic.clear();
            respiration.clear();
            oxygen.clear();
            notes.clear();
            feedback.setText("Observation saved to the patient record.");
        }));
        form.getChildren().addAll(patient);
        if (includeVitals) {
            form.getChildren().addAll(temperature, pulse, systolic, diastolic,
                    respiration, oxygen);
        }
        form.getChildren().addAll(notes, save, feedback);
        page.getChildren().add(form);
        refreshPatients(patient, feedback);
        showPage(page, includeVitals ? recordVitalsButton : nursingNotesButton,
                () -> refreshPatients(patient, feedback));
    }

    private void showMedicationAdministrationPage() {
        VBox page = createPage("Medication Administration",
                "Record only medicines prescribed for an active inpatient and "
                        + "confirmed dispensed. Existing administration history "
                        + "is immutable; this schema has no linked addendum flow.");
        VBox form = createPanel("Medication record",
                "The route must match the prescription. This record does not "
                        + "change stock. Saved administrations cannot be edited "
                        + "or deleted.");
        ComboBox<MedicationOption> medication = new ComboBox<>();
        medication.setPromptText("Select dispensed prescription item");
        medication.setMaxWidth(Double.MAX_VALUE);
        TextField dose = textField("Dose actually given");
        TextField route = textField("Route");
        TextArea notes = new TextArea();
        notes.setPromptText("Administration notes (optional)");
        notes.setPrefRowCount(3);
        notes.setWrapText(true);
        medication.valueProperty().addListener((observable, oldValue, value) -> {
            if (value != null) {
                route.setText(value.route() == null ? "" : value.route());
            }
        });
        TableView<MedicationAdministrationRow> history = new TableView<>();
        Label feedback = feedbackLabel();
        Button save = button("Record administration", "primary-button");
        boolean configuredResult = false;
        try {
            configuredResult =
                    dashboardDao.isMedicationAdministrationConfigured();
        } catch (SQLException exception) {
            LOGGER.log(Level.FINE, "Unable to inspect medication schema",
                    exception);
        }
        final boolean configured = configuredResult;
        save.setDisable(!configured);
        if (!configured) {
            feedback.setText(
                    "Not configured: apply the additive nursing medication "
                            + "administration DDL before recording.");
        }
        save.setOnAction(event -> runAction(feedback, () -> {
            MedicationOption selected = medication.getValue();
            if (selected == null) {
                throw new IllegalArgumentException(
                        "Select a dispensed medication for an admitted patient.");
            }
            dashboardDao.recordMedicationAdministration(
                    selected.admissionId(), selected.prescriptionItemId(),
                    dose.getText(), route.getText(), notes.getText(),
                    currentUserId());
            dose.clear();
            notes.clear();
            refreshMedicationAdministrations(history, feedback);
            feedback.setText("Medication administration recorded.");
        }));
        form.getChildren().addAll(medication, dose, route, notes, save,
                feedback);

        VBox historyPanel = createPanel("Recent administration records",
                "This administration record is separate from pharmacy dispensing.");
        history.getColumns().add(column("Administered", 150,
                MedicationAdministrationRow::administeredAt));
        history.getColumns().add(column("Patient No.", 115,
                MedicationAdministrationRow::patientNumber));
        history.getColumns().add(column("Patient", 150,
                MedicationAdministrationRow::patientName));
        history.getColumns().add(column("Medicine", 145,
                MedicationAdministrationRow::medicine));
        history.getColumns().add(column("Dose / route", 140,
                row -> row.dose() + " / " + row.route()));
        history.getColumns().add(column("By", 110,
                MedicationAdministrationRow::administeredBy));
        history.setPlaceholder(new Label(
                "Medication administration records are not configured."));
        historyPanel.getChildren().add(history);
        page.getChildren().addAll(form, historyPanel);
        refreshMedicationOptions(medication, feedback);
        if (configured) {
            refreshMedicationAdministrations(history, feedback);
        }
        showPage(page, medicationAdministrationButton, () -> {
            refreshMedicationOptions(medication, feedback);
            if (configured) {
                refreshMedicationAdministrations(history, feedback);
            }
        });
    }

    private void showMonitoringPage() {
        VBox page = createPage("Patient Monitoring",
                "Recent observations and nursing notes across active and previous "
                        + "inpatient stays. Records are preserved as immutable "
                        + "history; no correction/addendum schema is available.");
        VBox panel = createPanel("Observation history",
                "Only recorded observations are displayed. History is read-only.");
        TableView<ObservationRow> table = observationTable();
        panel.getChildren().add(table);
        Label feedback = feedbackLabel();
        page.getChildren().addAll(panel, feedback);
        refreshObservations(table, feedback);
        showPage(page, patientMonitoringButton,
                () -> refreshObservations(table, feedback));
    }

    private void showWardReportsPage() {
        VBox page = createPage("Nursing Ward Reports",
                "Current admitted patients and recent recorded nursing observations. "
                        + "Clinical history is immutable in the current schema.");
        VBox patientsPanel = createPanel("Admitted patient census",
                "No assignment table exists in the workflow schema; this is the current admitted census.");
        TableView<AdmittedPatient> patients = admittedPatientTable();
        patientsPanel.getChildren().add(patients);
        VBox observationsPanel = createPanel("Latest nursing activity",
                "Vital observations and notes are append-only and cannot be "
                        + "overwritten or deleted.");
        TableView<ObservationRow> observations = observationTable();
        observationsPanel.getChildren().add(observations);
        Label feedback = feedbackLabel();
        page.getChildren().addAll(patientsPanel, observationsPanel, feedback);
        refreshPatients(patients, feedback);
        refreshObservations(observations, feedback);
        showPage(page, wardReportsButton, () -> {
            refreshPatients(patients, feedback);
            refreshObservations(observations, feedback);
        });
    }

    private TableView<AdmittedPatient> admittedPatientTable() {
        TableView<AdmittedPatient> table = new TableView<>();
        table.getColumns().add(column("Patient No.", 120,
                AdmittedPatient::patientNumber));
        table.getColumns().add(column("Patient", 170,
                AdmittedPatient::patientName));
        table.getColumns().add(column("Ward", 130,
                row -> valueOrDash(row.ward())));
        table.getColumns().add(column("Bed", 90,
                row -> valueOrDash(row.bedNumber())));
        table.getColumns().add(column("Admitted", 170,
                AdmittedPatient::admittedAt));
        table.setPlaceholder(new Label("No patients are currently admitted."));
        return table;
    }

    private TableView<ObservationRow> observationTable() {
        TableView<ObservationRow> table = new TableView<>();
        table.getColumns().add(column("Recorded", 150,
                ObservationRow::recordedAt));
        table.getColumns().add(column("Patient No.", 115,
                ObservationRow::patientNumber));
        table.getColumns().add(column("Patient", 150,
                ObservationRow::patientName));
        table.getColumns().add(column("Temp °C", 85,
                row -> valueOrDash(row.temperature())));
        table.getColumns().add(column("Pulse", 75,
                row -> valueOrDash(row.pulse())));
        table.getColumns().add(column("BP", 90,
                row -> valueOrDash(row.bloodPressure())));
        table.getColumns().add(column("Resp.", 80,
                row -> valueOrDash(row.respiration())));
        table.getColumns().add(column("SpO₂ %", 80,
                row -> valueOrDash(row.oxygenSaturation())));
        table.getColumns().add(column("Notes", 220,
                row -> valueOrDash(row.notes())));
        table.getColumns().add(column("Recorded by", 110,
                ObservationRow::recordedBy));
        table.setPlaceholder(new Label(
                "Observation records are not configured or no records exist."));
        return table;
    }

    private void refreshPatients(TableView<AdmittedPatient> table,
                                 Label feedback) {
        try {
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadAdmittedPatients()));
            if (table.getItems().isEmpty()) {
                table.setPlaceholder(new Label(
                        "No patients are currently admitted."));
            }
        } catch (SQLException | IllegalStateException exception) {
            table.getItems().clear();
            table.setPlaceholder(new Label(
                    "Admission and patient tables are unavailable."));
            feedback.setText(exception.getMessage());
        }
    }

    private void refreshPatients(ComboBox<AdmittedPatient> selector,
                                 Label feedback) {
        try {
            selector.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadAdmittedPatients()));
        } catch (SQLException | IllegalStateException exception) {
            selector.getItems().clear();
            feedback.setText("Admitted patient list unavailable: "
                    + exception.getMessage());
        }
    }

    private void refreshObservations(TableView<ObservationRow> table,
                                     Label feedback) {
        try {
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadObservations()));
            if (table.getItems().isEmpty()) {
                table.setPlaceholder(new Label(
                        "No nursing observations have been recorded."));
            }
        } catch (SQLException | IllegalStateException exception) {
            table.getItems().clear();
            table.setPlaceholder(new Label(
                    "Nursing observation records are not configured."));
            feedback.setText(exception.getMessage());
        }
    }

    private void refreshMedicationOptions(
            ComboBox<MedicationOption> selector, Label feedback) {
        try {
            List<MedicationOption> options =
                    dashboardDao.loadMedicationOptions();
            selector.setItems(FXCollections.observableArrayList(options));
            if (options.isEmpty()) {
                selector.setPromptText(
                        "No dispensed prescriptions for admitted patients");
            }
        } catch (SQLException | IllegalStateException exception) {
            selector.getItems().clear();
            selector.setPromptText("Prescription or dispensing data unavailable");
            feedback.setText("Medication selection unavailable: "
                    + exception.getMessage());
        }
    }

    private void refreshMedicationAdministrations(
            TableView<MedicationAdministrationRow> table, Label feedback) {
        try {
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadMedicationAdministrations()));
            if (table.getItems().isEmpty()) {
                table.setPlaceholder(new Label(
                        "No administrations have been recorded."));
            }
        } catch (SQLException | IllegalStateException exception) {
            table.getItems().clear();
            table.setPlaceholder(new Label(
                    "Medication administration records are not configured."));
            feedback.setText(exception.getMessage());
        }
    }

    private VBox createPage(String title, String subtitle) {
        VBox page = new VBox(14);
        page.getStyleClass().addAll("dashboard-content", "nursing-content");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("page-subtitle");
        subtitleLabel.setWrapText(true);
        page.getChildren().addAll(titleLabel, subtitleLabel);
        return page;
    }

    private VBox createPanel(String title, String hint) {
        VBox panel = new VBox(9);
        panel.getStyleClass().addAll("panel", "nursing-panel");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("panel-hint");
        hintLabel.setWrapText(true);
        panel.getChildren().addAll(titleLabel, hintLabel);
        return panel;
    }

    private void showPage(VBox page, Button activeButton, Runnable refresh) {
        contentScroll.setContent(page);
        setActiveNavigation(activeButton);
        activeRefresh = refresh;
    }

    private void setActiveNavigation(Button selected) {
        if (activeNavigation != null) {
            activeNavigation.getStyleClass().remove("nav-button-active");
        }
        activeNavigation = selected;
        if (!selected.getStyleClass().contains("nav-button-active")) {
            selected.getStyleClass().add("nav-button-active");
        }
    }

    private <T> TableColumn<T, String> column(String title, double width,
                                               Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private TextField textField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        return field;
    }

    private Button button(String text, String style) {
        Button button = new Button(text);
        button.getStyleClass().add(style);
        return button;
    }

    private Label feedbackLabel() {
        Label label = new Label();
        label.getStyleClass().add("panel-hint");
        label.setWrapText(true);
        return label;
    }

    private void runAction(Label feedback, SqlAction action) {
        try {
            action.run();
            setStatus("Nursing workspace connected", "status-connected");
        } catch (IllegalArgumentException | SecurityException
                 | IllegalStateException exception) {
            feedback.setText(exception.getMessage());
            setStatus("Action needs attention", "status-warning");
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Nursing action failed", exception);
            feedback.setText("The change could not be saved: "
                    + exception.getMessage());
            setStatus("Nursing data is unavailable", "status-warning");
        }
    }

    private boolean requireNurse() {
        if (!isNursingUser()) {
            setStatus("Nursing access requires a Nurse or Senior Nurse session",
                    "status-warning");
            return false;
        }
        return true;
    }

    private long currentUserId() {
        if (!UserSession.isAuthenticated()) {
            throw new SecurityException("Sign in before saving changes.");
        }
        return UserSession.getCurrentUser().id();
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    @FXML
    private void handleRefresh() {
        if (isNursingUser()) {
            activeRefresh.run();
        }
    }

    @FXML
    private void handleOpenInpatient() throws Exception {
        if (!UserSession.hasRole("Senior Nurse")) {
            setStatus("Inpatient access is restricted to Ward Managers "
                    + "and Senior Nurses", "status-warning");
            return;
        }
        Parent root = FXMLLoader.load(
                MainApp.class.getResource(
                        "/ug/sjch/views/inpatient-dashboard.fxml"));
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource("/ug/sjch/styles/application.css")
                        .toExternalForm());
        stage.setScene(scene);
        stage.setTitle("SJCH Inpatient & Bed Management");
    }

    @FXML
    private void handleLogout() throws Exception {
        UserSession.clear();
        Parent root = FXMLLoader.load(
                MainApp.class.getResource("/ug/sjch/views/login.fxml"));
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource("/ug/sjch/styles/application.css")
                        .toExternalForm());
        stage.setScene(scene);
        stage.setTitle("St Jude Central Hospital - SJCH");
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
