package ug.sjch.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

import ug.sjch.MainApp;
import ug.sjch.dao.ReceptionDashboardDao;
import ug.sjch.dao.ReceptionWorkflowDao;
import ug.sjch.dao.ReceptionDashboardDao.AppointmentRow;
import ug.sjch.dao.ReceptionDashboardDao.Metrics;
import ug.sjch.dao.ReceptionDashboardDao.PatientRow;
import ug.sjch.dao.ReceptionDashboardDao.WorkflowCounts;
import ug.sjch.dao.ReceptionWorkflowDao.AppointmentRecord;
import ug.sjch.dao.ReceptionWorkflowDao.DoctorOption;
import ug.sjch.dao.ReceptionWorkflowDao.PatientRecord;
import ug.sjch.dao.ReceptionWorkflowDao.WorkflowEventRecord;
import ug.sjch.model.User;
import ug.sjch.security.UserSession;

public class ReceptionDashboardController {
    private static final Logger LOGGER =
            Logger.getLogger(ReceptionDashboardController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");

    @FXML private Label sidebarUserLabel;
    @FXML private Label topUserLabel;
    @FXML private Label welcomeLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private Label workflowTotalLabel;
    @FXML private Label queuedCountLabel;
    @FXML private Label waitingCountLabel;
    @FXML private Label engagedCountLabel;
    @FXML private Label completedCountLabel;
    @FXML private Label queuedPercentLabel;
    @FXML private Label waitingPercentLabel;
    @FXML private Label engagedPercentLabel;
    @FXML private Label completedPercentLabel;
    @FXML private Label recentPatientCountLabel;
    @FXML private Label upcomingCountLabel;
    @FXML private VBox sidebar;
    @FXML private Region sidebarResizeHandle;
    @FXML private ScrollPane contentScroll;
    @FXML private VBox dashboardContent;
    @FXML private Button dashboardButton;
    @FXML private Button registerPatientButton;
    @FXML private Button patientDirectoryButton;
    @FXML private Button bookAppointmentButton;
    @FXML private Button appointmentScheduleButton;
    @FXML private Button patientCheckInButton;
    @FXML private Button patientSearchButton;
    @FXML private Button notificationsButton;
    @FXML private TextField searchField;
    @FXML private GridPane metricGrid;
    @FXML private ProgressBar queuedProgress;
    @FXML private ProgressBar waitingProgress;
    @FXML private ProgressBar engagedProgress;
    @FXML private ProgressBar completedProgress;
    @FXML private TableView<PatientRow> patientTable;
    @FXML private TableColumn<PatientRow, String> patientNumberColumn;
    @FXML private TableColumn<PatientRow, String> patientNameColumn;
    @FXML private TableColumn<PatientRow, String> patientPhoneColumn;
    @FXML private TableColumn<PatientRow, String> patientRegisteredColumn;
    @FXML private TableView<AppointmentRow> appointmentTable;
    @FXML private TableColumn<AppointmentRow, String> appointmentTimeColumn;
    @FXML private TableColumn<AppointmentRow, String> appointmentPatientColumn;
    @FXML private TableColumn<AppointmentRow, String> appointmentDoctorColumn;
    @FXML private TableColumn<AppointmentRow, String> appointmentStatusColumn;

    private final ReceptionDashboardDao dashboardDao =
            new ReceptionDashboardDao();
    private final ReceptionWorkflowDao workflowDao =
            new ReceptionWorkflowDao();
    private FilteredList<PatientRow> filteredPatients;
    private FilteredList<AppointmentRow> filteredAppointments;
    private Runnable activeRefresh = this::loadDashboard;

    @FXML
    private void initialize() {
        SidebarResizer.install(sidebar, sidebarResizeHandle);
        configureTables();
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> filterTables(newValue)
        );

        if (!UserSession.isAuthenticated()
                || !UserSession.hasRole("Receptionist")) {
            setStatus("Receptionist session is not available", "status-warning");
            setMetricPlaceholders();
            return;
        }

        User user = UserSession.getCurrentUser();
        sidebarUserLabel.setText(user.username());
        topUserLabel.setText(user.username());
        welcomeLabel.setText("Reception & Patient Registration");
        dateLabel.setText(LocalDate.now().format(DATE_FORMAT));
        loadDashboard();
    }

    private void configureTables() {
        patientNumberColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().patientNumber()
                ));
        patientNameColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        patientPhoneColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().phone() == null
                                ? "—" : cell.getValue().phone()
                ));
        patientRegisteredColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().registeredAt()
                ));

        appointmentTimeColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().appointmentTime()
                ));
        appointmentPatientColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().patientName()
                ));
        appointmentDoctorColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().doctorName() == null
                                ? "Unassigned" : cell.getValue().doctorName()
                ));
        appointmentStatusColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        formatStatus(cell.getValue().status())
                ));

        patientTable.setPlaceholder(
                new Label("No patient registrations found")
        );
        appointmentTable.setPlaceholder(
                new Label("No upcoming appointments scheduled")
        );
    }

    private void loadDashboard() {
        boolean partialFailure = false;
        try {
            Metrics metrics = dashboardDao.loadMetrics();
            metricGrid.getChildren().clear();
            addMetric("Today's registered patients",
                    metrics.registeredToday(), "♙", "reception-metric-pink", 0);
            addMetric("Today's appointments",
                    metrics.appointmentsToday(), "▣", "reception-metric-amber", 1);
            addMetric("Waiting patients",
                    metrics.waitingPatients(), "◷", "reception-metric-green", 2);
            addMetric("Upcoming appointments",
                    metrics.upcomingAppointments(), "▦", "reception-metric-purple", 3);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load reception dashboard metrics",
                    exception);
            setMetricPlaceholders();
            setStatus("Reception data is unavailable", "status-warning");
            return;
        }

        try {
            loadWorkflow(dashboardDao.loadTodayWorkflow());
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load today's reception workflow",
                    exception);
            workflowTotalLabel.setText("Unavailable");
            partialFailure = true;
        }

        try {
            List<PatientRow> patients = dashboardDao.loadRecentPatients();
            filteredPatients = new FilteredList<>(
                    FXCollections.observableArrayList(patients),
                    patient -> true
            );
            patientTable.setItems(filteredPatients);
            recentPatientCountLabel.setText(patients.size()
                    + (patients.size() == 1 ? " record" : " records"));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load recently registered patients",
                    exception);
            patientTable.setPlaceholder(
                    new Label("Patient registration data is unavailable")
            );
            recentPatientCountLabel.setText("Unavailable");
            partialFailure = true;
        }

        try {
            List<AppointmentRow> appointments =
                    dashboardDao.loadUpcomingAppointments();
            filteredAppointments = new FilteredList<>(
                    FXCollections.observableArrayList(appointments),
                    appointment -> true
            );
            appointmentTable.setItems(filteredAppointments);
            upcomingCountLabel.setText(appointments.size()
                    + (appointments.size() == 1
                    ? " appointment" : " appointments"));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load upcoming appointments",
                    exception);
            appointmentTable.setPlaceholder(
                    new Label("Upcoming appointment data is unavailable")
            );
            upcomingCountLabel.setText("Unavailable");
            partialFailure = true;
        }

        setStatus(
                partialFailure ? "Some reception data is unavailable"
                        : "Database connected",
                partialFailure ? "status-warning" : "status-connected"
        );
    }

    private void loadWorkflow(WorkflowCounts workflow) {
        int total = workflow.total();
        workflowTotalLabel.setText(total
                + (total == 1 ? " appointment" : " appointments"));
        setWorkflowStep(workflow.queued(), total,
                queuedCountLabel, queuedPercentLabel, queuedProgress);
        setWorkflowStep(workflow.waiting(), total,
                waitingCountLabel, waitingPercentLabel, waitingProgress);
        setWorkflowStep(workflow.engaged(), total,
                engagedCountLabel, engagedPercentLabel, engagedProgress);
        setWorkflowStep(workflow.completed(), total,
                completedCountLabel, completedPercentLabel, completedProgress);
    }

    private void setWorkflowStep(int count, int total, Label countLabel,
                                 Label percentLabel, ProgressBar progressBar) {
        int percentage = total == 0 ? 0
                : Math.round((count * 100f) / total);
        countLabel.setText(String.valueOf(count));
        percentLabel.setText(percentage + "%");
        progressBar.setProgress(total == 0 ? 0 : count / (double) total);
    }

    private void filterTables(String searchText) {
        String query = searchText == null
                ? ""
                : searchText.trim().toLowerCase(Locale.ROOT);
        if (filteredPatients != null) {
            filteredPatients.setPredicate(patient ->
                    query.isEmpty()
                            || contains(patient.patientNumber(), query)
                            || contains(patient.name(), query)
                            || contains(patient.phone(), query));
            recentPatientCountLabel.setText(filteredPatients.size()
                    + (filteredPatients.size() == 1
                    ? " record" : " records"));
        }
        if (filteredAppointments != null) {
            filteredAppointments.setPredicate(appointment ->
                    query.isEmpty()
                            || contains(appointment.patientNumber(), query)
                            || contains(appointment.patientName(), query)
                            || contains(appointment.doctorName(), query)
                            || contains(formatStatus(appointment.status()), query));
            upcomingCountLabel.setText(filteredAppointments.size()
                    + (filteredAppointments.size() == 1
                    ? " appointment" : " appointments"));
        }
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String formatStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        String normalized = status.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0))
                + normalized.substring(1);
    }

    private void setMetricPlaceholders() {
        metricGrid.getChildren().clear();
        addMetric("Today's registered patients", "—", "♙",
                "reception-metric-pink", 0);
        addMetric("Today's appointments", "—", "▣",
                "reception-metric-amber", 1);
        addMetric("Waiting patients", "—", "◷",
                "reception-metric-green", 2);
        addMetric("Upcoming appointments", "—", "▦",
                "reception-metric-purple", 3);
    }

    private void addMetric(String title, int value, String icon,
                           String cardStyle, int column) {
        addMetric(title, String.valueOf(value), icon, cardStyle, column);
    }

    private void addMetric(String title, String value, String icon,
                           String cardStyle, int column) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-label");
        titleLabel.setWrapText(true);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-value");
        valueLabel.setWrapText(true);
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("metric-icon");
        VBox card = new VBox(iconLabel, valueLabel, titleLabel);
        card.getStyleClass().addAll("metric-card", cardStyle);
        GridPane.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        metricGrid.add(card, column, 0);
    }

    private void setStatus(String text, String stateClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(
                "status-warning", "status-connected"
        );
        statusLabel.getStyleClass().add(stateClass);
    }

    @FXML
    private void handleRefresh() {
        if (activeRefresh != null) {
            activeRefresh.run();
        } else {
            loadDashboard();
        }
    }

    @FXML
    private void handleDashboard() {
        if (!requireReceptionist()) {
            return;
        }
        contentScroll.setContent(dashboardContent);
        setActiveNavigation(dashboardButton);
        activeRefresh = this::loadDashboard;
        loadDashboard();
    }

    @FXML
    private void handleRegisterPatient() {
        if (requireReceptionist()) {
            showRegisterPatientPage();
        }
    }

    @FXML
    private void handlePatientDirectory() {
        if (requireReceptionist()) {
            showPatientSearchPage("Patient Directory",
                    "Browse registered patients and update their contact details",
                    true, patientDirectoryButton);
        }
    }

    @FXML
    private void handleBookAppointment() {
        if (requireReceptionist()) {
            showBookAppointmentPage();
        }
    }

    @FXML
    private void handleAppointmentSchedule() {
        if (requireReceptionist()) {
            showAppointmentSchedulePage();
        }
    }

    @FXML
    private void handlePatientCheckIn() {
        if (requireReceptionist()) {
            showPatientCheckInPage();
        }
    }

    @FXML
    private void handlePatientSearch() {
        if (requireReceptionist()) {
            showPatientSearchPage("Patient Search",
                    "Search by patient number, name, phone, or email",
                    false, patientSearchButton);
        }
    }

    @FXML
    private void handleNotifications() {
        if (requireReceptionist()) {
            showNotificationsPage();
        }
    }

    private void showRegisterPatientPage() {
        VBox page = createWorkflowPage("Register New Patient",
                "Add a patient to the shared hospital record");
        VBox panel = createPanel("Patient details",
                "Patient number is generated when the record is saved.");
        GridPane form = createFormGrid();
        TextField firstName = createTextField("First name");
        TextField lastName = createTextField("Last name");
        DatePicker dateOfBirth = new DatePicker();
        dateOfBirth.setPromptText("Optional");
        dateOfBirth.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> gender = new ComboBox<>(
                FXCollections.observableArrayList("Female", "Male", "Other"));
        gender.setPromptText("Select gender");
        gender.setMaxWidth(Double.MAX_VALUE);
        TextField phone = createTextField("Phone");
        TextField email = createTextField("Email");
        TextArea address = createTextArea("Address");
        TextField emergencyContact = createTextField("Emergency contact");
        TextField emergencyPhone = createTextField("Emergency phone");
        addFormField(form, 0, "First name", firstName);
        addFormField(form, 1, "Last name", lastName);
        addFormField(form, 2, "Date of birth", dateOfBirth);
        addFormField(form, 3, "Gender", gender);
        addFormField(form, 4, "Phone", phone);
        addFormField(form, 5, "Email", email);
        addFormField(form, 6, "Address", address);
        addFormField(form, 7, "Emergency contact", emergencyContact);
        addFormField(form, 8, "Emergency phone", emergencyPhone);

        Label feedback = createFeedbackLabel();
        Button saveButton = createPrimaryButton("Register patient");
        Button clearButton = createSecondaryButton("Clear form");
        saveButton.setOnAction(event -> {
            try {
                validateEmail(email.getText());
                PatientRecord patient = workflowDao.registerPatient(
                        firstName.getText(),
                        lastName.getText(),
                        dateOfBirth.getValue(),
                        gender.getValue(),
                        phone.getText(),
                        email.getText(),
                        address.getText(),
                        emergencyContact.getText(),
                        emergencyPhone.getText(),
                        currentUserId()
                );
                showFeedback(feedback, "Patient registered: "
                        + patient.patientNumber(), true);
                loadDashboard();
            } catch (SQLException | IllegalArgumentException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to register reception patient", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        });
        clearButton.setOnAction(event -> {
            firstName.clear();
            lastName.clear();
            dateOfBirth.setValue(null);
            gender.setValue(null);
            phone.clear();
            email.clear();
            address.clear();
            emergencyContact.clear();
            emergencyPhone.clear();
            feedback.setText("");
        });
        panel.getChildren().addAll(form,
                new HBox(10, saveButton, clearButton), feedback);
        page.getChildren().add(panel);
        showWorkflowPage(page, registerPatientButton, this::loadDashboard);
    }

    private void showPatientSearchPage(String title, String subtitle,
                                       boolean allowContactEdit,
                                       Button navigationButton) {
        VBox page = createWorkflowPage(title, subtitle);
        VBox panel = createPanel("Patient records",
                "Showing the latest matching records, up to 100 patients.");
        TextField query = createTextField(
                "Patient number, name, phone, or email");
        Button searchButton = createPrimaryButton("Search");
        Button editButton = createSecondaryButton("Edit patient details");
        Button archiveButton = createSecondaryButton("Archive patient");
        Label count = new Label("Loading patient records...");
        count.getStyleClass().add("table-count-badge");
        Label feedback = createFeedbackLabel();
        TableView<PatientRecord> table = new TableView<>();
        table.getStyleClass().addAll("dashboard-table", "reception-table");
        table.setPrefHeight(450);
        table.setPlaceholder(new Label("No patient records found"));
        table.getColumns().add(patientColumn("Patient No.", 120,
                PatientRecord::patientNumber));
        table.getColumns().add(patientColumn("Patient name", 180,
                PatientRecord::name));
        table.getColumns().add(patientColumn("Date of birth", 120,
                patient -> patient.dateOfBirth() == null
                        ? "—" : patient.dateOfBirth().toString()));
        table.getColumns().add(patientColumn("Gender", 90,
                patient -> displayValue(patient.gender())));
        table.getColumns().add(patientColumn("Phone", 120,
                patient -> displayValue(patient.phone())));
        table.getColumns().add(patientColumn("Email", 180,
                patient -> displayValue(patient.email())));
        table.getColumns().add(patientColumn("Status", 90,
                patient -> formatStatus(patient.status())));
        table.getColumns().add(patientColumn("Registered", 150,
                patient -> displayValue(patient.registeredAt())));

        Runnable reload = () -> loadPatientSearchResults(
                query.getText(), table, count, feedback);
        searchButton.setOnAction(event -> reload.run());
        query.setOnAction(event -> reload.run());
        if (allowContactEdit) {
            table.getSelectionModel().selectedItemProperty().addListener(
                    (observable, oldValue, selected) -> {
                        editButton.setDisable(selected == null
                                || !"ACTIVE".equalsIgnoreCase(selected.status()));
                        archiveButton.setDisable(selected == null
                                || !"ACTIVE".equalsIgnoreCase(selected.status()));
                    });
            editButton.setDisable(true);
            archiveButton.setDisable(true);
            editButton.setOnAction(event -> {
                PatientRecord selected =
                        table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showContactEditor(selected, reload, feedback);
                }
            });
            archiveButton.setOnAction(event -> {
                PatientRecord selected =
                        table.getSelectionModel().getSelectedItem();
                if (selected == null || !"ACTIVE".equalsIgnoreCase(
                        selected.status())) {
                    return;
                }
                if (!confirmAction("Archive patient",
                        "This patient record will be marked inactive. "
                                + "Existing clinical and appointment history "
                                + "will be retained. Continue?")) {
                    return;
                }
                try {
                    workflowDao.archivePatient(
                            selected.patientId(), currentUserId());
                    showFeedback(feedback,
                            "Patient archived; history was retained.", true);
                    reload.run();
                    loadDashboard();
                } catch (SQLException | IllegalArgumentException exception) {
                    LOGGER.log(Level.WARNING,
                            "Unable to archive reception patient", exception);
                    showFeedback(feedback, exception.getMessage(), false);
                }
            });
        }
        HBox searchRow = new HBox(10, query, searchButton);
        HBox.setHgrow(query, Priority.ALWAYS);
        HBox actions = new HBox(10, count, new Region());
        HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);
        if (allowContactEdit) {
            actions.getChildren().addAll(editButton, archiveButton);
        }
        panel.getChildren().addAll(searchRow, actions, table, feedback);
        page.getChildren().add(panel);
        showWorkflowPage(page, navigationButton, reload);
        reload.run();
    }

    private void loadPatientSearchResults(String query,
                                          TableView<PatientRecord> table,
                                          Label count, Label feedback) {
        try {
            List<PatientRecord> patients =
                    workflowDao.searchPatients(query);
            table.setItems(FXCollections.observableArrayList(patients));
            count.setText(patients.size()
                    + (patients.size() == 1 ? " patient" : " patients"));
            showFeedback(feedback, "", true);
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to search reception patient records", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback,
                    "Patient records are unavailable. Check the database connection.",
                    false);
        }
    }

    private void showContactEditor(PatientRecord patient, Runnable reload,
                                  Label feedback) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Update patient contact details");
        dialog.setHeaderText(patient.patientNumber() + " - " + patient.name());
        if (contentScroll.getScene() != null) {
            dialog.initOwner(contentScroll.getScene().getWindow());
        }
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);
        GridPane form = createFormGrid();
        TextField firstName = createTextField("First name");
        firstName.setText(patient.firstName());
        TextField lastName = createTextField("Last name");
        lastName.setText(patient.lastName());
        DatePicker dateOfBirth = new DatePicker(patient.dateOfBirth());
        dateOfBirth.setPromptText("Optional");
        ComboBox<String> gender = new ComboBox<>(
                FXCollections.observableArrayList("Female", "Male", "Other"));
        gender.setPromptText("Select gender");
        gender.setValue(patient.gender());
        TextField phone = createTextField("Phone");
        phone.setText(patient.phone());
        TextField email = createTextField("Email");
        email.setText(patient.email());
        TextArea address = createTextArea("Address");
        address.setText(patient.address());
        TextField emergencyContact =
                createTextField("Emergency contact");
        emergencyContact.setText(patient.emergencyContact());
        TextField emergencyPhone = createTextField("Emergency phone");
        emergencyPhone.setText(patient.emergencyPhone());
        addFormField(form, 0, "First name", firstName);
        addFormField(form, 1, "Last name", lastName);
        addFormField(form, 2, "Date of birth", dateOfBirth);
        addFormField(form, 3, "Gender", gender);
        addFormField(form, 4, "Phone", phone);
        addFormField(form, 5, "Email", email);
        addFormField(form, 6, "Address", address);
        addFormField(form, 7, "Emergency contact", emergencyContact);
        addFormField(form, 8, "Emergency phone", emergencyPhone);
        dialog.getDialogPane().setContent(form);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                validateEmail(email.getText());
                workflowDao.updatePatientDetails(
                        patient.patientId(), firstName.getText(),
                        lastName.getText(), dateOfBirth.getValue(),
                        gender.getValue(), phone.getText(), email.getText(),
                        address.getText(), emergencyContact.getText(),
                        emergencyPhone.getText(), currentUserId());
                showFeedback(feedback, "Patient details updated.", true);
                reload.run();
                loadDashboard();
            } catch (SQLException | IllegalArgumentException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update reception patient contact details",
                        exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        }
    }

    private void showBookAppointmentPage() {
        VBox page = createWorkflowPage("Book Appointment",
                "Schedule a patient with an active clinician");
        VBox panel = createPanel("Appointment details",
                "Appointments are saved to the shared hospital schedule.");
        GridPane form = createFormGrid();
        TextField patientNumber = createTextField("Patient number");
        Button findPatientButton = createSecondaryButton("Find patient");
        Label patientMatch = new Label("No patient selected");
        patientMatch.getStyleClass().add("panel-hint");
        PatientRecord[] selectedPatient = new PatientRecord[1];
        patientNumber.textProperty().addListener(
                (observable, oldValue, newValue) -> {
                    selectedPatient[0] = null;
                    patientMatch.setText("Find the patient before booking.");
                });
        findPatientButton.setOnAction(event -> {
            try {
                selectedPatient[0] = workflowDao.findPatientByNumber(
                        patientNumber.getText());
                patientMatch.setText(selectedPatient[0].name() + " - "
                        + selectedPatient[0].patientNumber());
            } catch (IllegalArgumentException exception) {
                selectedPatient[0] = null;
                patientMatch.setText(exception.getMessage());
            } catch (SQLException exception) {
                LOGGER.log(Level.WARNING,
                        "Reception patient lookup is unavailable",
                        exception);
                selectedPatient[0] = null;
                patientMatch.setText(
                        "Patient lookup is unavailable. Try again.");
            }
        });
        HBox patientLookup = new HBox(8, patientNumber, findPatientButton);
        HBox.setHgrow(patientNumber, Priority.ALWAYS);
        ComboBox<DoctorOption> doctor = new ComboBox<>();
        doctor.setPromptText("Select clinician");
        doctor.setMaxWidth(Double.MAX_VALUE);
        DatePicker appointmentDate = new DatePicker(LocalDate.now());
        appointmentDate.setMaxWidth(Double.MAX_VALUE);
        TextField appointmentTime = createTextField("HH:mm");
        TextField reason = createTextField("Reason for visit (optional)");
        addFormField(form, 0, "Patient", patientLookup);
        addFormField(form, 1, "Selected patient", patientMatch);
        addFormField(form, 2, "Clinician", doctor);
        addFormField(form, 3, "Appointment date", appointmentDate);
        addFormField(form, 4, "Appointment time", appointmentTime);
        addFormField(form, 5, "Reason", reason);

        Label feedback = createFeedbackLabel();
        try {
            doctor.setItems(FXCollections.observableArrayList(
                    workflowDao.loadDoctors()));
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load clinicians for reception booking",
                    exception);
            showFeedback(feedback,
                    "Clinician list is unavailable. Check the database connection.",
                    false);
        }
        Button bookButton = createPrimaryButton("Book appointment");
        bookButton.setOnAction(event -> {
            try {
                if (selectedPatient[0] == null) {
                    throw new IllegalArgumentException(
                            "Find and select a patient before booking.");
                }
                if (doctor.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select a clinician for the appointment.");
                }
                if (appointmentDate.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select an appointment date.");
                }
                LocalTime time = LocalTime.parse(
                        appointmentTime.getText().trim());
                LocalDateTime dateTime = LocalDateTime.of(
                        appointmentDate.getValue(), time);
                workflowDao.bookAppointment(
                        selectedPatient[0].patientNumber(),
                        doctor.getValue().staffId(), dateTime,
                        reason.getText(), currentUserId());
                showFeedback(feedback, "Appointment booked for "
                        + selectedPatient[0].name() + ".", true);
                loadDashboard();
            } catch (DateTimeParseException exception) {
                showFeedback(feedback,
                        "Enter appointment time in 24-hour HH:mm format.",
                        false);
            } catch (SQLException | IllegalArgumentException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to book reception appointment", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        });
        panel.getChildren().addAll(form, bookButton, feedback);
        page.getChildren().add(panel);
        showWorkflowPage(page, bookAppointmentButton, () -> {
            try {
                doctor.setItems(FXCollections.observableArrayList(
                        workflowDao.loadDoctors()));
            } catch (SQLException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to refresh available clinicians", exception);
                showFeedback(feedback, "Clinician list is unavailable.", false);
            }
        });
    }

    private void showAppointmentSchedulePage() {
        VBox page = createWorkflowPage("Appointment Schedule",
                "View and refresh scheduled patient visits");
        VBox panel = createPanel("Appointments",
                "Search a date range; up to 200 appointments are shown.");
        HBox filters = new HBox(10);
        DatePicker fromDate = new DatePicker(LocalDate.now());
        DatePicker toDate = new DatePicker(LocalDate.now().plusDays(14));
        Button search = createPrimaryButton("Load schedule");
        Button editButton = createSecondaryButton("Edit selected");
        Button cancelButton = createSecondaryButton("Cancel selected");
        editButton.setDisable(true);
        cancelButton.setDisable(true);
        Label count = new Label();
        count.getStyleClass().add("table-count-badge");
        filters.getChildren().addAll(
                new Label("From"), fromDate,
                new Label("To"), toDate, search, count);
        TableView<AppointmentRecord> table = createAppointmentTable();
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    boolean editable = selected != null
                            && ReceptionWorkflowDao.canEditAppointment(
                                    selected.status(),
                                    selected.appointmentDateTime(),
                                    LocalDateTime.now());
                    editButton.setDisable(!editable);
                    cancelButton.setDisable(!editable);
                });
        Label feedback = createFeedbackLabel();
        Runnable reload = () -> loadScheduleRows(
                fromDate.getValue(), toDate.getValue(), table, count, feedback);
        search.setOnAction(event -> reload.run());
        editButton.setOnAction(event -> {
            AppointmentRecord selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected != null && ReceptionWorkflowDao.canEditAppointment(
                    selected.status(), selected.appointmentDateTime(),
                    LocalDateTime.now())) {
                showAppointmentEditor(selected, reload, feedback);
            }
        });
        cancelButton.setOnAction(event -> {
            AppointmentRecord selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected == null || !ReceptionWorkflowDao.canEditAppointment(
                    selected.status(), selected.appointmentDateTime(),
                    LocalDateTime.now())) {
                return;
            }
            if (!confirmAction("Cancel appointment",
                    "Cancel the scheduled visit for " + selected.patientName()
                            + "? The appointment record and history will be retained.")) {
                return;
            }
            try {
                workflowDao.cancelAppointment(
                        selected.appointmentId(), currentUserId());
                showFeedback(feedback, "Appointment cancelled.", true);
                loadDashboard();
                reload.run();
            } catch (SQLException | IllegalArgumentException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to cancel reception appointment", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        });
        panel.getChildren().addAll(filters, table,
                new HBox(10, editButton, cancelButton), feedback);
        page.getChildren().add(panel);
        showWorkflowPage(page, appointmentScheduleButton, reload);
        reload.run();
    }

    private void showAppointmentEditor(AppointmentRecord appointment,
                                       Runnable reload, Label feedback) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit scheduled appointment");
        dialog.setHeaderText(appointment.patientName() + " • "
                + appointment.patientNumber());
        if (contentScroll.getScene() != null) {
            dialog.initOwner(contentScroll.getScene().getWindow());
        }
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);
        GridPane form = createFormGrid();
        ComboBox<DoctorOption> doctor = new ComboBox<>();
        try {
            doctor.setItems(FXCollections.observableArrayList(
                    workflowDao.loadDoctors()));
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load clinicians for appointment edit", exception);
            showFeedback(feedback, "Clinician list is unavailable.", false);
            return;
        }
        doctor.setValue(doctor.getItems().stream()
                .filter(option -> appointment.doctorId() != null
                        && option.staffId() == appointment.doctorId())
                .findFirst().orElse(null));
        DatePicker date = new DatePicker(
                appointment.appointmentDateTime().toLocalDate());
        TextField time = createTextField("HH:mm");
        time.setText(appointment.appointmentDateTime()
                .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        TextField reason = createTextField("Reason for visit");
        reason.setText(appointment.reason());
        addFormField(form, 0, "Clinician", doctor);
        addFormField(form, 1, "Date", date);
        addFormField(form, 2, "Time", time);
        addFormField(form, 3, "Reason", reason);
        dialog.getDialogPane().setContent(form);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                if (doctor.getValue() == null || date.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select a clinician and appointment date.");
                }
                LocalTime selectedTime = LocalTime.parse(time.getText().trim());
                workflowDao.updateAppointmentDetails(
                        appointment.appointmentId(), doctor.getValue().staffId(),
                        LocalDateTime.of(date.getValue(), selectedTime),
                        reason.getText(), currentUserId());
                showFeedback(feedback, "Appointment details updated.", true);
                loadDashboard();
                reload.run();
            } catch (DateTimeParseException exception) {
                showFeedback(feedback,
                        "Enter appointment time in 24-hour HH:mm format.", false);
            } catch (SQLException | IllegalArgumentException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update reception appointment", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        }
    }

    private boolean confirmAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (contentScroll.getScene() != null) {
            alert.initOwner(contentScroll.getScene().getWindow());
        }
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void loadScheduleRows(LocalDate fromDate, LocalDate toDate,
                                  TableView<AppointmentRecord> table,
                                  Label count, Label feedback) {
        try {
            List<AppointmentRecord> appointments =
                    workflowDao.loadAppointments(fromDate, toDate);
            table.setItems(FXCollections.observableArrayList(appointments));
            count.setText(appointments.size()
                    + (appointments.size() == 1
                    ? " appointment" : " appointments"));
            showFeedback(feedback, "", true);
        } catch (SQLException | IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load reception appointment schedule", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback, exception.getMessage(), false);
        }
    }

    private void showPatientCheckInPage() {
        VBox page = createWorkflowPage("Patient Check-in",
                "Mark today's scheduled visits as arrived");
        VBox panel = createPanel("Today's arrivals",
                "Only today's scheduled appointments can be checked in.");
        TableView<AppointmentRecord> table = createAppointmentTable();
        Label count = new Label();
        count.getStyleClass().add("table-count-badge");
        Label feedback = createFeedbackLabel();
        Button checkInButton = createPrimaryButton("Check in selected patient");
        Button refreshButton = createSecondaryButton("Refresh");
        checkInButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) ->
                        checkInButton.setDisable(selected == null
                                || !"SCHEDULED".equals(selected.status())));
        Runnable reload = () -> loadCheckInRows(table, count, feedback);
        refreshButton.setOnAction(event -> reload.run());
        checkInButton.setOnAction(event -> {
            AppointmentRecord selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            try {
                workflowDao.checkInAppointment(
                        selected.appointmentId(), currentUserId());
                showFeedback(feedback,
                        selected.patientName() + " checked in successfully.",
                        true);
                loadDashboard();
                reload.run();
            } catch (SQLException | IllegalArgumentException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to check in reception appointment", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        });
        panel.getChildren().addAll(new HBox(10, count, refreshButton),
                table, checkInButton, feedback);
        page.getChildren().add(panel);
        showWorkflowPage(page, patientCheckInButton, reload);
        reload.run();
    }

    private void loadCheckInRows(TableView<AppointmentRecord> table,
                                Label count, Label feedback) {
        try {
            List<AppointmentRecord> appointments =
                    workflowDao.loadTodayCheckIns();
            table.setItems(FXCollections.observableArrayList(appointments));
            count.setText(appointments.size()
                    + (appointments.size() == 1
                    ? " appointment" : " appointments"));
            showFeedback(feedback, "", true);
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load today's reception check-ins", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback,
                    "Today's check-in list is unavailable.", false);
        }
    }

    private void showNotificationsPage() {
        VBox page = createWorkflowPage("Notifications",
                "Recent events recorded across the shared patient workflow");
        VBox panel = createPanel("Recent workflow activity",
                "Patient registration, appointment, and department events "
                        + "from the last 30 days.");
        TableView<WorkflowEventRecord> table = new TableView<>();
        table.getStyleClass().addAll("dashboard-table", "reception-table");
        table.setPrefHeight(470);
        table.setPlaceholder(new Label("No recent workflow events"));
        table.getColumns().add(eventColumn("Time", 180,
                WorkflowEventRecord::occurredAt));
        table.getColumns().add(eventColumn("Patient No.", 140,
                WorkflowEventRecord::patientNumber));
        table.getColumns().add(eventColumn("Department", 180,
                WorkflowEventRecord::departmentName));
        table.getColumns().add(eventColumn("Event", 220,
                row -> formatStatus(row.eventType())));
        Label count = new Label();
        count.getStyleClass().add("table-count-badge");
        Label feedback = createFeedbackLabel();
        Runnable reload = () -> loadWorkflowEvents(table, count, feedback);
        panel.getChildren().addAll(new HBox(10, count), table, feedback);
        page.getChildren().add(panel);
        showWorkflowPage(page, notificationsButton, reload);
        reload.run();
    }

    private void loadWorkflowEvents(TableView<WorkflowEventRecord> table,
                                    Label count, Label feedback) {
        try {
            List<WorkflowEventRecord> events =
                    workflowDao.loadRecentWorkflowEvents();
            table.setItems(FXCollections.observableArrayList(events));
            count.setText(events.size()
                    + (events.size() == 1 ? " event" : " events"));
            showFeedback(feedback, "", true);
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load reception workflow notifications",
                    exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback,
                    "Workflow notifications are not configured or unavailable.",
                    false);
        }
    }

    private TableView<AppointmentRecord> createAppointmentTable() {
        TableView<AppointmentRecord> table = new TableView<>();
        table.getStyleClass().addAll("dashboard-table", "reception-table");
        table.setPrefHeight(440);
        table.setPlaceholder(new Label("No appointments found"));
        table.getColumns().add(appointmentColumn("Date & time", 170,
                AppointmentRecord::appointmentTime));
        table.getColumns().add(appointmentColumn("Patient No.", 120,
                AppointmentRecord::patientNumber));
        table.getColumns().add(appointmentColumn("Patient", 170,
                AppointmentRecord::patientName));
        table.getColumns().add(appointmentColumn("Clinician", 160,
                row -> displayValue(row.doctorName())));
        table.getColumns().add(appointmentColumn("Department", 150,
                row -> displayValue(row.departmentName())));
        table.getColumns().add(appointmentColumn("Reason", 200,
                row -> displayValue(row.reason())));
        table.getColumns().add(appointmentColumn("Status", 110,
                row -> formatStatus(row.status())));
        return table;
    }

    private <T> TableColumn<T, String> patientColumn(
            String title, double width, Function<T, String> value) {
        return createColumn(title, width, value);
    }

    private <T> TableColumn<T, String> appointmentColumn(
            String title, double width, Function<T, String> value) {
        return createColumn(title, width, value);
    }

    private <T> TableColumn<T, String> eventColumn(
            String title, double width, Function<T, String> value) {
        return createColumn(title, width, value);
    }

    private <T> TableColumn<T, String> createColumn(
            String title, double width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private VBox createWorkflowPage(String title, String subtitle) {
        VBox page = new VBox(14);
        page.getStyleClass().addAll("dashboard-content", "reception-content");
        VBox heading = new VBox(3);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("page-subtitle");
        heading.getChildren().addAll(titleLabel, subtitleLabel);
        page.getChildren().add(heading);
        return page;
    }

    private VBox createPanel(String title, String hint) {
        VBox panel = new VBox(12);
        panel.getStyleClass().addAll("panel", "reception-workflow-panel");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("panel-hint");
        panel.getChildren().addAll(titleLabel, hintLabel);
        return panel;
    }

    private GridPane createFormGrid() {
        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(12);
        return form;
    }

    private void addFormField(GridPane form, int row, String labelText,
                              Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        form.add(label, 0, row);
        form.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        if (control instanceof TextField textField) {
            textField.setMaxWidth(Double.MAX_VALUE);
        } else if (control instanceof TextArea textArea) {
            textArea.setMaxWidth(Double.MAX_VALUE);
        } else if (control instanceof ComboBox<?> comboBox) {
            comboBox.setMaxWidth(Double.MAX_VALUE);
        } else if (control instanceof DatePicker datePicker) {
            datePicker.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private TextField createTextField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("reception-form-field");
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private TextArea createTextArea(String prompt) {
        TextArea field = new TextArea();
        field.setPromptText(prompt);
        field.setWrapText(true);
        field.setPrefRowCount(2);
        field.getStyleClass().add("reception-form-field");
        return field;
    }

    private Label createFeedbackLabel() {
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("reception-feedback");
        return feedback;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        return button;
    }

    private void showFeedback(Label label, String message, boolean success) {
        label.setText(message == null ? "" : message);
        label.getStyleClass().removeAll(
                "reception-feedback-success", "reception-feedback-error");
        label.getStyleClass().add(success
                ? "reception-feedback-success" : "reception-feedback-error");
    }

    private void showWorkflowPage(VBox page, Button activeButton,
                                  Runnable refresh) {
        contentScroll.setContent(page);
        contentScroll.setVvalue(0);
        setActiveNavigation(activeButton);
        activeRefresh = refresh;
    }

    private void setActiveNavigation(Button activeButton) {
        List.of(dashboardButton, registerPatientButton,
                patientDirectoryButton, bookAppointmentButton,
                appointmentScheduleButton, patientCheckInButton,
                patientSearchButton, notificationsButton)
                .forEach(button -> button.getStyleClass()
                        .remove("nav-button-active"));
        activeButton.getStyleClass().add("nav-button-active");
    }

    private boolean requireReceptionist() {
        if (UserSession.hasRole("Receptionist")) {
            return true;
        }
        setStatus("Receptionist access is required", "status-warning");
        return false;
    }

    private long currentUserId() {
        if (!requireReceptionist()) {
            throw new IllegalArgumentException(
                    "Receptionist session is no longer available.");
        }
        return UserSession.getCurrentUser().id();
    }

    private void validateEmail(String email) {
        if (email != null && !email.isBlank()
                && !email.trim().matches(
                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "Enter a valid email address.");
        }
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    @FXML
    private void handleLogout() throws Exception {
        UserSession.clear();
        Parent root = FXMLLoader.load(
                MainApp.class.getResource("/ug/sjch/views/login.fxml")
        );
        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource("/ug/sjch/styles/application.css")
                        .toExternalForm()
        );
        stage.setScene(scene);
        stage.setTitle("St Jude Central Hospital - SJCH");
    }
}
