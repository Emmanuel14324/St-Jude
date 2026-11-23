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
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import ug.sjch.MainApp;
import ug.sjch.dao.AdminDashboardDao;
import ug.sjch.dao.AdminDashboardDao.AppointmentSummary;
import ug.sjch.dao.AdminDashboardDao.AuditRow;
import ug.sjch.dao.AdminDashboardDao.DepartmentPerformance;
import ug.sjch.dao.AdminDashboardDao.DepartmentRow;
import ug.sjch.dao.AdminDashboardDao.ClinicianOption;
import ug.sjch.dao.AdminDashboardDao.OverviewMetrics;
import ug.sjch.dao.AdminDashboardDao.PatientSummary;
import ug.sjch.dao.AdminDashboardDao.RoleOption;
import ug.sjch.dao.AdminDashboardDao.StaffOption;
import ug.sjch.dao.AdminDashboardDao.StaffRow;
import ug.sjch.dao.AdminDashboardDao.UserRow;
import ug.sjch.dao.AdminDashboardDao.WorkflowEventRow;
import ug.sjch.security.UserSession;

public class AdminDashboardController {
    private static final Logger LOGGER =
            Logger.getLogger(AdminDashboardController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");

    @FXML private Label userLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private ScrollPane contentScroll;
    @FXML private VBox dashboardContent;
    @FXML private VBox sidebar;
    @FXML private Region sidebarResizeHandle;
    @FXML private Button overviewButton;
    @FXML private Button patientsButton;
    @FXML private Button appointmentsButton;
    @FXML private Button staffButton;
    @FXML private Button usersButton;
    @FXML private Button departmentsButton;
    @FXML private Button reportsButton;
    @FXML private Button auditButton;

    private final AdminDashboardDao dashboardDao = new AdminDashboardDao();
    private Runnable activeRefresh = this::renderOverview;

    @FXML
    private void initialize() {
        SidebarResizer.install(sidebar, sidebarResizeHandle);
        dateLabel.setText(LocalDate.now().format(DATE_FORMAT));
        if (!UserSession.hasRole("Administrator")) {
            userLabel.setText("Not signed in");
            setStatus("Administrator access is required", "status-warning");
            setNavigationEnabled(false);
            dashboardContent.getChildren().setAll(
                    new Label("Sign in with an administrator account "
                            + "to view this workspace."));
            return;
        }
        userLabel.setText(UserSession.getCurrentUser().username());
        renderOverview();
    }

    @FXML
    private void handleOverview() {
        if (requireAdministrator()) {
            contentScroll.setContent(dashboardContent);
            setActiveNavigation(overviewButton);
            activeRefresh = this::renderOverview;
            renderOverview();
        }
    }

    @FXML
    private void handlePatients() {
        if (requireAdministrator()) {
            showPatientPage();
        }
    }

    @FXML
    private void handleAppointments() {
        if (requireAdministrator()) {
            showAppointmentsPage();
        }
    }

    @FXML
    private void handleStaff() {
        if (requireAdministrator()) {
            showStaffPage();
        }
    }

    @FXML
    private void handleUsers() {
        if (requireAdministrator()) {
            showUsersPage();
        }
    }

    @FXML
    private void handleDepartments() {
        if (requireAdministrator()) {
            showDepartmentsPage();
        }
    }

    @FXML
    private void handleReports() {
        if (requireAdministrator()) {
            showReportsPage();
        }
    }

    @FXML
    private void handleAuditLogs() {
        if (requireAdministrator()) {
            showAuditPage();
        }
    }

    @FXML
    private void handleRefresh() {
        if (requireAdministrator()) {
            activeRefresh.run();
        }
    }

    private void renderOverview() {
        VBox page = createPage("Hospital overview",
                "Live activity from the shared SJCH database");
        try {
            OverviewMetrics metrics = dashboardDao.loadOverviewMetrics();
            GridPane cards = new GridPane();
            cards.setHgap(16);
            cards.setVgap(16);
            for (int columnIndex = 0; columnIndex < 4; columnIndex++) {
                ColumnConstraints column = new ColumnConstraints();
                column.setPercentWidth(25);
                cards.getColumnConstraints().add(column);
            }
            addMetric(cards, "Registered patients", metrics.patients(), 0);
            addMetric(cards, "Active staff", metrics.activeStaff(), 1);
            addMetric(cards, "Appointments today",
                    metrics.appointmentsToday(), 2);
            addMetric(cards, "Current admissions",
                    metrics.currentAdmissions() == null
                            ? "Not configured"
                            : metrics.currentAdmissions().toString(), 3);
            addMetric(cards, "Active user accounts", metrics.activeUsers(), 4);
            addMetric(cards, "Departments", metrics.departments(), 5);
            page.getChildren().add(cards);
            setStatus("Database connection active", "status-connected");
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load administrator overview metrics", exception);
            page.getChildren().add(createMessagePanel(
                    "Dashboard data unavailable",
                    "Could not read hospital statistics. Check database "
                            + "connectivity and schema access."));
            setStatus("Database unavailable", "status-warning");
        }

        HBox dataRow = new HBox(12);
        VBox departmentPanel = createPanel("Department performance",
                "Active staff and non-cancelled appointments today");
        VBox recentEventsPanel = createPanel("Recent patient workflow",
                "Latest events from departments across the hospital");
        try {
            List<DepartmentPerformance> departments =
                    dashboardDao.loadDepartmentPerformance();
            departmentPanel.getChildren().addAll(
                    createDepartmentChart(departments),
                    createDepartmentPerformanceTable(departments));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load department performance", exception);
            departmentPanel.getChildren().add(
                    createUnavailableLabel("Department data is unavailable."));
        }
        try {
            TableView<WorkflowEventRow> events = createTable(
                    "No workflow events recorded");
            events.getColumns().add(column("Time", 145,
                    WorkflowEventRow::occurredAt));
            events.getColumns().add(column("Patient", 100,
                    WorkflowEventRow::patientNumber));
            events.getColumns().add(column("Department", 115,
                    WorkflowEventRow::department));
            events.getColumns().add(column("Event", 145,
                    row -> formatStatus(row.event())));
            events.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadRecentWorkflowEvents()));
            recentEventsPanel.getChildren().add(events);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load administrator workflow events", exception);
            recentEventsPanel.getChildren().add(
                    createUnavailableLabel("Workflow events are unavailable."));
        }
        HBox.setHgrow(departmentPanel, Priority.ALWAYS);
        HBox.setHgrow(recentEventsPanel, Priority.ALWAYS);
        dataRow.getChildren().addAll(departmentPanel, recentEventsPanel);
        page.getChildren().add(dataRow);

        HBox recentRow = new HBox(12);
        VBox patients = createPanel("Recent registrations",
                "Latest patient records");
        VBox appointments = createPanel("Recent appointments",
                "Most recently scheduled visits");
        try {
            TableView<PatientSummary> table =
                    createTable("No patient registrations found");
            table.getColumns().add(column("Patient No.", 110,
                    PatientSummary::patientNumber));
            table.getColumns().add(column("Patient", 150,
                    PatientSummary::patientName));
            table.getColumns().add(column("Registered", 145,
                    PatientSummary::registeredAt));
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadRecentPatients()));
            patients.getChildren().add(table);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load recent patient registrations", exception);
            patients.getChildren().add(
                    createUnavailableLabel("Patient data is unavailable."));
        }
        try {
            TableView<AppointmentSummary> table =
                    createTable("No appointments found");
            table.getColumns().add(column("Date & time", 145,
                    AppointmentSummary::appointmentTime));
            table.getColumns().add(column("Patient", 130,
                    AppointmentSummary::patientName));
            table.getColumns().add(column("Department", 125,
                    row -> displayValue(row.departmentName())));
            table.getColumns().add(column("Status", 95,
                    row -> formatStatus(row.status())));
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadRecentAppointments()));
            appointments.getChildren().add(table);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load recent appointments", exception);
            appointments.getChildren().add(
                    createUnavailableLabel("Appointment data is unavailable."));
        }
        HBox.setHgrow(patients, Priority.ALWAYS);
        HBox.setHgrow(appointments, Priority.ALWAYS);
        recentRow.getChildren().addAll(patients, appointments);
        page.getChildren().add(recentRow);
        showContent(page, overviewButton, this::renderOverview);
    }

    private void showPatientPage() {
        VBox page = createPage("Patient records",
                "Search hospital patient registrations");
        VBox panel = createPanel("Patient directory",
                "Search by patient number, name, or phone. Up to 200 rows "
                        + "are shown.");
        TextField search = createField("Patient number, name, phone, or email");
        Button searchButton = createPrimaryButton("Search");
        Label count = createCountLabel();
        TableView<PatientSummary> table = createTable("No patients found");
        table.getColumns().add(column("Patient No.", 130,
                PatientSummary::patientNumber));
        table.getColumns().add(column("Patient name", 170,
                PatientSummary::patientName));
        table.getColumns().add(column("DOB", 110,
                row -> row.dateOfBirth() == null
                        ? "—" : row.dateOfBirth().toString()));
        table.getColumns().add(column("Phone", 125,
                row -> displayValue(row.phone())));
        table.getColumns().add(column("Email", 170,
                row -> displayValue(row.email())));
        table.getColumns().add(column("Status", 95,
                row -> formatStatus(row.status())));
        table.getColumns().add(column("Registered", 170,
                PatientSummary::registeredAt));
        Button editButton = createSecondaryButton("Edit selected details");
        Button archiveButton = createSecondaryButton("Archive selected");
        editButton.setDisable(true);
        archiveButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    boolean active = selected != null
                            && "ACTIVE".equalsIgnoreCase(selected.status());
                    editButton.setDisable(!active);
                    archiveButton.setDisable(!active);
                });
        Label feedback = createFeedbackLabel();
        Runnable reload = () -> loadPatientRows(
                search.getText(), table, count, feedback);
        searchButton.setOnAction(event -> reload.run());
        search.setOnAction(event -> reload.run());
        editButton.setOnAction(event -> {
            PatientSummary selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected != null && "ACTIVE".equalsIgnoreCase(
                    selected.status())) {
                showEditPatientDialog(selected, reload, feedback);
            }
        });
        archiveButton.setOnAction(event -> {
            PatientSummary selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected == null || !"ACTIVE".equalsIgnoreCase(
                    selected.status())) {
                return;
            }
            if (!confirmAction("Archive patient",
                    "Mark " + selected.patientNumber()
                            + " inactive? Existing clinical and appointment "
                            + "history will be retained.")) {
                return;
            }
            try {
                dashboardDao.archivePatient(
                        selected.patientId(), currentUserId());
                showFeedback(feedback,
                        "Patient archived; history was retained.", true);
                reload.run();
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to archive administrator patient", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        });
        HBox searchRow = new HBox(10, search, searchButton, count);
        HBox.setHgrow(search, Priority.ALWAYS);
        panel.getChildren().addAll(searchRow, table,
                new HBox(10, editButton, archiveButton), feedback);
        page.getChildren().add(panel);
        showContent(page, patientsButton, reload);
        reload.run();
    }

    private void showEditPatientDialog(PatientSummary patient,
                                       Runnable reload, Label feedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Edit patient details",
                patient.patientNumber()
                        + " - generated patient number is read-only");
        GridPane form = createFormGrid();
        TextField firstName = createField("First name");
        firstName.setText(patient.firstName());
        TextField lastName = createField("Last name");
        lastName.setText(patient.lastName());
        DatePicker dateOfBirth = new DatePicker(patient.dateOfBirth());
        ComboBox<String> gender = new ComboBox<>(
                FXCollections.observableArrayList("Female", "Male", "Other"));
        gender.setValue(patient.gender());
        TextField phone = createField("Phone");
        phone.setText(patient.phone());
        TextField email = createField("Email");
        email.setText(patient.email());
        TextField address = createField("Address");
        address.setText(patient.address());
        TextField emergencyContact = createField("Emergency contact");
        emergencyContact.setText(patient.emergencyContact());
        TextField emergencyPhone = createField("Emergency phone");
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
        boolean[] updated = new boolean[1];
        Button confirmButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                validateEmail(email.getText());
                dashboardDao.updatePatientDetails(
                        patient.patientId(), firstName.getText(),
                        lastName.getText(), dateOfBirth.getValue(),
                        gender.getValue(), phone.getText(), email.getText(),
                        address.getText(), emergencyContact.getText(),
                        emergencyPhone.getText(), currentUserId());
                updated[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update administrator patient details",
                        exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && updated[0]) {
            showFeedback(feedback, "Patient details updated.", true);
            reload.run();
        }
    }

    private void loadPatientRows(String query, TableView<PatientSummary> table,
                                 Label count, Label feedback) {
        try {
            List<PatientSummary> rows = dashboardDao.searchPatients(query);
            table.setItems(FXCollections.observableArrayList(rows));
            count.setText(rows.size() + (rows.size() == 1
                    ? " patient" : " patients"));
            showFeedback(feedback, "", true);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load administrator patient directory", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback, "Patient directory is unavailable.", false);
        }
    }

    private void showAppointmentsPage() {
        VBox page = createPage("Appointments",
                "Review appointments across all hospital departments");
        VBox panel = createPanel("Appointment schedule",
                "Choose a date range to view appointments.");
        DatePicker fromDate = new DatePicker(LocalDate.now().minusDays(7));
        DatePicker toDate = new DatePicker(LocalDate.now().plusDays(30));
        Button searchButton = createPrimaryButton("Load appointments");
        Label count = createCountLabel();
        TableView<AppointmentSummary> table =
                createTable("No appointments found");
        table.getColumns().add(column("Date & time", 160,
                AppointmentSummary::appointmentTime));
        table.getColumns().add(column("Patient No.", 120,
                AppointmentSummary::patientNumber));
        table.getColumns().add(column("Patient", 160,
                AppointmentSummary::patientName));
        table.getColumns().add(column("Clinician", 145,
                row -> displayValue(row.doctorName())));
        table.getColumns().add(column("Department", 150,
                row -> displayValue(row.departmentName())));
        table.getColumns().add(column("Reason", 200,
                row -> displayValue(row.reason())));
        table.getColumns().add(column("Status", 110,
                row -> formatStatus(row.status())));
        Button editButton = createSecondaryButton("Edit selected");
        Button cancelButton = createSecondaryButton("Cancel selected");
        editButton.setDisable(true);
        cancelButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    boolean editable = selected != null
                            && AdminDashboardDao.canEditAppointment(
                                    selected.status(),
                                    selected.appointmentDateTime(),
                                    LocalDateTime.now());
                    editButton.setDisable(!editable);
                    cancelButton.setDisable(!editable);
                });
        Label feedback = createFeedbackLabel();
        Runnable reload = () -> loadAppointmentRows(fromDate.getValue(),
                toDate.getValue(), table, count, feedback);
        searchButton.setOnAction(event -> reload.run());
        editButton.setOnAction(event -> {
            AppointmentSummary selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected != null && AdminDashboardDao.canEditAppointment(
                    selected.status(), selected.appointmentDateTime(),
                    LocalDateTime.now())) {
                showAppointmentEditor(selected, reload, feedback);
            }
        });
        cancelButton.setOnAction(event -> {
            AppointmentSummary selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected == null || !AdminDashboardDao.canEditAppointment(
                    selected.status(), selected.appointmentDateTime(),
                    LocalDateTime.now())) {
                return;
            }
            if (!confirmAction("Cancel appointment",
                    "Cancel the scheduled visit for " + selected.patientName()
                            + "? The appointment and its history will be retained.")) {
                return;
            }
            try {
                dashboardDao.cancelAppointment(
                        selected.appointmentId(), currentUserId());
                showFeedback(feedback, "Appointment cancelled.", true);
                reload.run();
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to cancel administrator appointment", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        });
        HBox filters = new HBox(10,
                new Label("From"), fromDate,
                new Label("To"), toDate, searchButton, count);
        panel.getChildren().addAll(filters, table,
                new HBox(10, editButton, cancelButton), feedback);
        page.getChildren().add(panel);
        showContent(page, appointmentsButton, reload);
        reload.run();
    }

    private void showAppointmentEditor(AppointmentSummary appointment,
                                       Runnable reload, Label feedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Edit scheduled appointment",
                appointment.patientNumber() + " - " + appointment.patientName());
        GridPane form = createFormGrid();
        ComboBox<ClinicianOption> clinician = new ComboBox<>();
        clinician.setMaxWidth(Double.MAX_VALUE);
        DatePicker date = new DatePicker(
                appointment.appointmentDateTime().toLocalDate());
        TextField time = createField("HH:mm");
        time.setText(appointment.appointmentDateTime().toLocalTime()
                .format(DateTimeFormatter.ofPattern("HH:mm")));
        TextField reason = createField("Reason for visit");
        reason.setText(appointment.reason());
        try {
            clinician.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadActiveClinicians()));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load clinicians for appointment edit", exception);
            showFeedback(feedback, "Clinician list is unavailable.", false);
            return;
        }
        clinician.setValue(clinician.getItems().stream()
                .filter(option -> appointment.doctorId() != null
                        && option.staffId() == appointment.doctorId())
                .findFirst().orElse(null));
        addFormField(form, 0, "Clinician", clinician);
        addFormField(form, 1, "Date", date);
        addFormField(form, 2, "Time", time);
        addFormField(form, 3, "Reason", reason);
        dialog.getDialogPane().setContent(form);
        boolean[] updated = new boolean[1];
        Button confirmButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (clinician.getValue() == null || date.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select a clinician and appointment date.");
                }
                LocalTime selectedTime = LocalTime.parse(time.getText().trim());
                dashboardDao.updateAppointmentDetails(
                        appointment.appointmentId(),
                        clinician.getValue().staffId(),
                        LocalDateTime.of(date.getValue(), selectedTime),
                        reason.getText(), currentUserId());
                updated[0] = true;
            } catch (DateTimeParseException exception) {
                showFeedback(feedback,
                        "Enter appointment time in 24-hour HH:mm format.", false);
                event.consume();
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update administrator appointment", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && updated[0]) {
            showFeedback(feedback, "Appointment details updated.", true);
            reload.run();
        }
    }

    private void loadAppointmentRows(LocalDate fromDate, LocalDate toDate,
                                     TableView<AppointmentSummary> table,
                                     Label count, Label feedback) {
        try {
            List<AppointmentSummary> rows =
                    dashboardDao.loadAppointments(fromDate, toDate);
            table.setItems(FXCollections.observableArrayList(rows));
            count.setText(rows.size() + (rows.size() == 1
                    ? " appointment" : " appointments"));
            showFeedback(feedback, "", true);
        } catch (SQLException | IllegalArgumentException
                 | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load administrator appointment schedule",
                    exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback, exception.getMessage(), false);
        }
    }

    private void showStaffPage() {
        VBox page = createPage("Staff management",
                "Maintain staff records and access status");
        VBox panel = createPanel("Hospital staff",
                "Staff records are deactivated rather than deleted to preserve "
                        + "clinical history.");
        TextField search = createField("Search staff, role, or department");
        Button searchButton = createSecondaryButton("Search");
        Button addButton = createPrimaryButton("Add staff");
        Button editButton = createSecondaryButton("Edit selected details");
        editButton.setDisable(true);
        ComboBox<String> statusChoice = new ComboBox<>(
                FXCollections.observableArrayList(
                        "ACTIVE", "INACTIVE", "ON_LEAVE"));
        statusChoice.setPromptText("Select status");
        Button updateButton = createSecondaryButton("Update status");
        updateButton.setDisable(true);
        Label count = createCountLabel();
        Label feedback = createFeedbackLabel();
        TableView<StaffRow> table = createTable("No staff records found");
        table.getColumns().add(column("Staff No.", 130, StaffRow::staffNumber));
        table.getColumns().add(column("Name", 160, StaffRow::name));
        table.getColumns().add(column("Job title", 140, StaffRow::jobTitle));
        table.getColumns().add(column("Department", 150, StaffRow::department));
        table.getColumns().add(column("Phone", 120,
                row -> displayValue(row.phone())));
        table.getColumns().add(column("Status", 100, StaffRow::status));
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    updateButton.setDisable(selected == null);
                    editButton.setDisable(selected == null);
                    if (selected != null) {
                        statusChoice.setValue(selected.status());
                    }
                });
        Runnable reload = () -> loadStaffRows(search.getText(),
                table, count, feedback);
        searchButton.setOnAction(event -> reload.run());
        search.setOnAction(event -> reload.run());
        addButton.setOnAction(event -> showAddStaffDialog(reload, feedback));
        editButton.setOnAction(event -> {
            StaffRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditStaffDialog(selected, reload, feedback);
            }
        });
        updateButton.setOnAction(event -> {
            StaffRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null || statusChoice.getValue() == null) {
                showFeedback(feedback,
                        "Select a staff member and a status.", false);
                return;
            }
            if (selected.status().equals(statusChoice.getValue())) {
                showFeedback(feedback, "The selected status is unchanged.",
                        false);
                return;
            }
            if (!confirmAction("Change staff status",
                    "Change " + selected.name() + " from " + selected.status()
                            + " to " + statusChoice.getValue()
                            + "? Linked access may be deactivated.")) {
                return;
            }
            try {
                dashboardDao.updateStaffStatus(selected.staffId(),
                        statusChoice.getValue(), currentUserId());
                showFeedback(feedback, "Staff status updated.", true);
                reload.run();
                loadOverviewMetricsForStatus();
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update staff status", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        });
        HBox filters = new HBox(10, search, searchButton, count);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox actions = new HBox(10, addButton, editButton,
                statusChoice, updateButton);
        panel.getChildren().addAll(filters, actions, table, feedback);
        page.getChildren().add(panel);
        showContent(page, staffButton, reload);
        reload.run();
    }

    private void loadStaffRows(String query, TableView<StaffRow> table,
                               Label count, Label feedback) {
        try {
            List<StaffRow> rows = dashboardDao.searchStaff(query);
            table.setItems(FXCollections.observableArrayList(rows));
            count.setText(rows.size() + (rows.size() == 1
                    ? " staff member" : " staff members"));
            showFeedback(feedback, "", true);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load staff directory", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback, "Staff records are unavailable.", false);
        }
    }

    private void showAddStaffDialog(Runnable reload, Label feedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Add staff member", "Create a staff record in the hospital directory");
        GridPane form = createFormGrid();
        TextField firstName = createField("First name");
        TextField lastName = createField("Last name");
        TextField jobTitle = createField("Job title");
        TextField phone = createField("Phone");
        TextField email = createField("Email");
        ComboBox<String> gender = new ComboBox<>(
                FXCollections.observableArrayList("Female", "Male", "Other"));
        gender.setPromptText("Optional");
        ComboBox<DepartmentRow> department = new ComboBox<>();
        department.setMaxWidth(Double.MAX_VALUE);
        department.setPromptText("Select department");
        try {
            department.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadDepartments()));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load departments for staff form", exception);
            showFeedback(feedback, "Department list is unavailable.", false);
            return;
        }

        addFormField(form, 0, "First name", firstName);
        addFormField(form, 1, "Last name", lastName);
        addFormField(form, 2, "Job title", jobTitle);
        addFormField(form, 3, "Department", department);
        addFormField(form, 4, "Gender", gender);
        addFormField(form, 5, "Phone", phone);
        addFormField(form, 6, "Email", email);
        Label dialogFeedback = createFeedbackLabel();
        form.add(dialogFeedback, 0, 7, 2, 1);
        dialog.getDialogPane().setContent(form);
        String[] createdStaffNumber = new String[1];
        Button confirmButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                validateEmail(email.getText());
                if (department.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select a department.");
                }
                createdStaffNumber[0] = dashboardDao.createStaff(
                        firstName.getText(), lastName.getText(),
                        jobTitle.getText(),
                        department.getValue().departmentId(),
                        gender.getValue(), phone.getText(), email.getText(),
                        currentUserId());
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to create staff record", exception);
                showFeedback(dialogFeedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK
                && createdStaffNumber[0] != null) {
            showFeedback(feedback,
                    "Staff record created: " + createdStaffNumber[0], true);
            reload.run();
            loadOverviewMetricsForStatus();
        }
    }

    private void showEditStaffDialog(StaffRow staff, Runnable reload,
                                     Label feedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Edit staff details",
                staff.staffNumber() + " - generated staff number is read-only");
        GridPane form = createFormGrid();
        TextField firstName = createField("First name");
        firstName.setText(staff.firstName());
        TextField lastName = createField("Last name");
        lastName.setText(staff.lastName());
        TextField jobTitle = createField("Job title");
        jobTitle.setText(staff.jobTitle());
        ComboBox<DepartmentRow> department = new ComboBox<>();
        department.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> gender = new ComboBox<>(
                FXCollections.observableArrayList("Female", "Male", "Other"));
        gender.setValue(staff.gender());
        TextField phone = createField("Phone");
        phone.setText(staff.phone());
        TextField email = createField("Email");
        email.setText(staff.email());
        try {
            department.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadDepartments()));
            department.setValue(department.getItems().stream()
                    .filter(option -> staff.departmentId() != null
                            && option.departmentId() == staff.departmentId())
                    .findFirst().orElse(null));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load departments for staff edit", exception);
            showFeedback(feedback, "Department list is unavailable.", false);
            return;
        }
        addFormField(form, 0, "First name", firstName);
        addFormField(form, 1, "Last name", lastName);
        addFormField(form, 2, "Job title", jobTitle);
        addFormField(form, 3, "Department", department);
        addFormField(form, 4, "Gender", gender);
        addFormField(form, 5, "Phone", phone);
        addFormField(form, 6, "Email", email);
        dialog.getDialogPane().setContent(form);
        boolean[] updated = new boolean[1];
        Button confirmButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                validateEmail(email.getText());
                if (department.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select a department.");
                }
                dashboardDao.updateStaffDetails(
                        staff.staffId(), firstName.getText(), lastName.getText(),
                        jobTitle.getText(),
                        department.getValue().departmentId(),
                        gender.getValue(), phone.getText(), email.getText(),
                        currentUserId());
                updated[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update staff details", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && updated[0]) {
            showFeedback(feedback, "Staff details updated.", true);
            reload.run();
        }
    }

    private void showUsersPage() {
        VBox page = createPage("User account management",
                "Create and manage system access for active staff");
        VBox panel = createPanel("Accounts",
                "Passwords are stored as PBKDF2 hashes. Accounts are "
                        + "deactivated rather than deleted.");
        TextField search = createField("Search username, role, or staff");
        Button searchButton = createSecondaryButton("Search");
        Button createButton = createPrimaryButton("Create account");
        Button editButton = createSecondaryButton("Edit selected");
        Button toggleButton = createSecondaryButton("Activate / deactivate");
        editButton.setDisable(true);
        toggleButton.setDisable(true);
        Label count = createCountLabel();
        Label feedback = createFeedbackLabel();
        TableView<UserRow> table = createTable("No user accounts found");
        table.getColumns().add(column("Username", 150, UserRow::username));
        table.getColumns().add(column("Role", 145, UserRow::role));
        table.getColumns().add(column("Staff member", 180,
                row -> displayValue(row.staffName())));
        table.getColumns().add(column("Email", 180,
                row -> displayValue(row.email())));
        table.getColumns().add(column("Status", 100,
                row -> row.active() ? "Active" : "Inactive"));
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    editButton.setDisable(selected == null);
                    toggleButton.setDisable(selected == null);
                });
        Runnable reload = () -> loadUserRows(search.getText(),
                table, count, feedback);
        searchButton.setOnAction(event -> reload.run());
        search.setOnAction(event -> reload.run());
        createButton.setOnAction(event ->
                showCreateUserDialog(reload, feedback));
        editButton.setOnAction(event -> {
            UserRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditUserDialog(selected, reload, feedback);
            }
        });
        toggleButton.setOnAction(event -> {
            UserRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            if (!confirmAction(
                    selected.active() ? "Deactivate user account"
                            : "Activate user account",
                    selected.active()
                            ? "This account will no longer be able to sign in. Continue?"
                            : "This account will be able to sign in again. Continue?")) {
                return;
            }
            try {
                dashboardDao.updateUserActive(selected.userId(),
                        !selected.active(), currentUserId());
                showFeedback(feedback,
                        selected.active()
                                ? "User account deactivated."
                                : "User account activated.",
                        true);
                reload.run();
                loadOverviewMetricsForStatus();
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update user account status", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        });
        HBox filters = new HBox(10, search, searchButton, count);
        HBox.setHgrow(search, Priority.ALWAYS);
        panel.getChildren().addAll(filters,
                new HBox(10, createButton, editButton, toggleButton),
                table, feedback);
        page.getChildren().add(panel);
        showContent(page, usersButton, reload);
        reload.run();
    }

    private void loadUserRows(String query, TableView<UserRow> table,
                              Label count, Label feedback) {
        try {
            List<UserRow> rows = dashboardDao.loadUsers(query);
            table.setItems(FXCollections.observableArrayList(rows));
            count.setText(rows.size() + (rows.size() == 1
                    ? " account" : " accounts"));
            showFeedback(feedback, "", true);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load user accounts", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback, "User account data is unavailable.", false);
        }
    }

    private void showCreateUserDialog(Runnable reload, Label feedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Create user account",
                "The account is linked to one active staff member.");
        GridPane form = createFormGrid();
        TextField username = createField("Username");
        TextField email = createField("Email (optional)");
        PasswordField password = new PasswordField();
        password.setPromptText("At least 12 characters");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Repeat initial password");
        ComboBox<StaffOption> staff = new ComboBox<>();
        staff.setMaxWidth(Double.MAX_VALUE);
        staff.setPromptText("Select active staff");
        ComboBox<RoleOption> role = new ComboBox<>();
        role.setMaxWidth(Double.MAX_VALUE);
        role.setPromptText("Select role");
        try {
            staff.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadStaffWithoutAccounts()));
            role.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadRoles()));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load account form options", exception);
            showFeedback(feedback,
                    "Staff or role list is unavailable.", false);
            return;
        }
        addFormField(form, 0, "Username", username);
        addFormField(form, 1, "Email", email);
        addFormField(form, 2, "Staff member", staff);
        addFormField(form, 3, "Role", role);
        addFormField(form, 4, "Initial password", password);
        addFormField(form, 5, "Confirm password", confirmPassword);
        Label dialogFeedback = createFeedbackLabel();
        form.add(dialogFeedback, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(form);
        boolean[] created = new boolean[1];
        Button confirmButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                validateEmail(email.getText());
                if (staff.getValue() == null || role.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select a staff member and role.");
                }
                if (!password.getText().equals(confirmPassword.getText())) {
                    throw new IllegalArgumentException(
                            "The passwords do not match.");
                }
                dashboardDao.createUserAccount(
                        username.getText(), password.getText(), email.getText(),
                        staff.getValue().staffId(), role.getValue().roleId(),
                        currentUserId());
                created[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to create user account", exception);
                showFeedback(dialogFeedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK
                && created[0]) {
            showFeedback(feedback,
                    "User account created and linked to staff.", true);
            reload.run();
            loadOverviewMetricsForStatus();
        }
    }

    private void showEditUserDialog(UserRow account, Runnable reload,
                                    Label feedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Edit user account",
                "User ID and linked staff are read-only; no stored password is displayed.");
        GridPane form = createFormGrid();
        Label idValue = new Label(String.valueOf(account.userId()));
        Label staffValue = new Label(displayValue(account.staffName()));
        TextField username = createField("Username");
        username.setText(account.username());
        TextField email = createField("Email (optional)");
        email.setText(account.email());
        ComboBox<RoleOption> role = new ComboBox<>();
        role.setMaxWidth(Double.MAX_VALUE);
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("Optional; at least 12 characters");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Repeat new password");
        try {
            role.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadRoles()));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load roles for user edit", exception);
            showFeedback(feedback, "Role list is unavailable.", false);
            return;
        }
        role.setValue(role.getItems().stream()
                .filter(option -> option.roleName().equalsIgnoreCase(
                        account.role()))
                .findFirst().orElse(null));
        boolean editingCurrentAccount = UserSession.getCurrentUser() != null
                && UserSession.getCurrentUser().id() == account.userId();
        if (editingCurrentAccount) {
            role.setDisable(true);
        }
        addFormField(form, 0, "User ID (read-only)", idValue);
        addFormField(form, 1, "Linked staff (read-only)", staffValue);
        addFormField(form, 2, "Username", username);
        addFormField(form, 3, "Email", email);
        addFormField(form, 4, "Role", role);
        addFormField(form, 5, "Reset password (optional)", newPassword);
        addFormField(form, 6, "Confirm new password", confirmPassword);
        dialog.getDialogPane().setContent(form);
        boolean[] updated = new boolean[1];
        Button confirmButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                validateEmail(email.getText());
                if (role.getValue() == null) {
                    throw new IllegalArgumentException("Select a role.");
                }
                boolean resettingPassword = !newPassword.getText().isBlank()
                        || !confirmPassword.getText().isBlank();
                if (!newPassword.getText().equals(confirmPassword.getText())) {
                    throw new IllegalArgumentException(
                            "The new passwords do not match.");
                }
                boolean changingRole = !account.role().equalsIgnoreCase(
                        role.getValue().roleName());
                if ((changingRole || resettingPassword)
                        && !confirmAction("Confirm account security change",
                        (changingRole ? "Change this account's role? " : "")
                                + (resettingPassword
                                ? "Reset its password as well?" : ""))) {
                    event.consume();
                    return;
                }
                dashboardDao.updateUserAccount(
                        account.userId(), username.getText(), email.getText(),
                        role.getValue().roleId(),
                        resettingPassword ? newPassword.getText() : null,
                        currentUserId());
                updated[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update user account", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && updated[0]) {
            showFeedback(feedback, "User account updated.", true);
            reload.run();
        }
    }

    private void showDepartmentsPage() {
        VBox page = createPage("Departments",
                "View active and inactive hospital departments");
        VBox panel = createPanel("Department directory",
                "Department statistics are available from the dashboard "
                        + "and reports.");
        TableView<DepartmentRow> table =
                createTable("No department records found");
        Button editButton = createSecondaryButton("Edit selected details");
        Button statusButton = createSecondaryButton("Deactivate selected");
        editButton.setDisable(true);
        statusButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    editButton.setDisable(selected == null);
                    statusButton.setDisable(selected == null);
                    if (selected != null) {
                        statusButton.setText(
                                "ACTIVE".equalsIgnoreCase(selected.status())
                                        ? "Deactivate selected"
                                        : "Activate selected");
                    }
                });
        table.getColumns().add(column("Department", 220,
                DepartmentRow::name));
        table.getColumns().add(column("Location", 180,
                row -> displayValue(row.location())));
        table.getColumns().add(column("Phone", 150,
                row -> displayValue(row.phone())));
        table.getColumns().add(column("Status", 110, DepartmentRow::status));
        Label feedback = createFeedbackLabel();
        Runnable reload = () -> {
            try {
                table.setItems(FXCollections.observableArrayList(
                        dashboardDao.loadDepartments()));
                showFeedback(feedback, "", true);
            } catch (SQLException | IllegalStateException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to load departments", exception);
                table.setItems(FXCollections.observableArrayList());
                showFeedback(feedback,
                        "Department data is unavailable.", false);
            }
        };
        editButton.setOnAction(event -> {
            DepartmentRow selected = table.getSelectionModel()
                    .getSelectedItem();
            if (selected != null) {
                showEditDepartmentDialog(selected, reload, feedback);
            }
        });
        statusButton.setOnAction(event -> {
            DepartmentRow selected = table.getSelectionModel()
                    .getSelectedItem();
            if (selected == null) {
                return;
            }
            boolean activate = !"ACTIVE".equalsIgnoreCase(selected.status());
            if (!confirmAction(
                    activate ? "Activate department" : "Deactivate department",
                    activate
                            ? "Reactivate " + selected.name() + "?"
                            : "Deactivate " + selected.name()
                                    + "? Departments with active staff or future "
                                    + "scheduled appointments cannot be deactivated.")) {
                return;
            }
            try {
                dashboardDao.updateDepartmentStatus(selected.departmentId(),
                        activate, currentUserId());
                showFeedback(feedback,
                        activate ? "Department activated."
                                : "Department deactivated.", true);
                reload.run();
                loadOverviewMetricsForStatus();
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update department status", exception);
                showFeedback(feedback, exception.getMessage(), false);
            }
        });
        panel.getChildren().addAll(
                new HBox(10, editButton, statusButton), table, feedback);
        page.getChildren().add(panel);
        showContent(page, departmentsButton, reload);
        reload.run();
    }

    private void showEditDepartmentDialog(DepartmentRow department,
                                         Runnable reload, Label feedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Edit department details",
                "Department ID " + department.departmentId()
                        + " is read-only. Linked history is retained.");
        GridPane form = createFormGrid();
        TextField name = createField("Department name");
        name.setText(department.name());
        TextField location = createField("Location");
        location.setText(department.location());
        TextField phone = createField("Phone");
        phone.setText(department.phone());
        addFormField(form, 0, "Department", name);
        addFormField(form, 1, "Location", location);
        addFormField(form, 2, "Phone", phone);
        dialog.getDialogPane().setContent(form);
        boolean[] updated = new boolean[1];
        Button confirmButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                dashboardDao.updateDepartmentDetails(
                        department.departmentId(), name.getText(),
                        location.getText(), phone.getText(), currentUserId());
                updated[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update department details", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && updated[0]) {
            showFeedback(feedback, "Department details updated.", true);
            reload.run();
        }
    }

    private void showReportsPage() {
        VBox page = createPage("Department reports",
                "Compare staffing and appointment activity");
        VBox panel = createPanel("Today's department activity",
                "Appointment counts exclude cancelled visits.");
        Label feedback = createFeedbackLabel();
        try {
            List<DepartmentPerformance> departments =
                    dashboardDao.loadDepartmentPerformance();
            panel.getChildren().addAll(
                    createDepartmentChart(departments),
                    createDepartmentPerformanceTable(departments));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load department report", exception);
            showFeedback(feedback,
                    "Department report data is unavailable.", false);
            panel.getChildren().add(feedback);
        }
        page.getChildren().add(panel);
        showContent(page, reportsButton, () -> showReportsPage());
    }

    private void showAuditPage() {
        VBox page = createPage("Audit logs",
                "Review recorded administrative actions");
        VBox panel = createPanel("Recent audit activity",
                "Logs are read-only and exclude passwords and other secrets.");
        TableView<AuditRow> table = createTable("No audit events found");
        table.getColumns().add(column("Time", 170, AuditRow::actionTime));
        table.getColumns().add(column("User", 140,
                row -> displayValue(row.username())));
        table.getColumns().add(column("Action", 170, AuditRow::action));
        table.getColumns().add(column("Details", 360,
                row -> displayValue(row.description())));
        Label feedback = createFeedbackLabel();
        Runnable reload = () -> {
            try {
                table.setItems(FXCollections.observableArrayList(
                        dashboardDao.loadRecentAuditLogs()));
                showFeedback(feedback, "", true);
            } catch (SQLException | IllegalStateException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to load audit log records", exception);
                table.setItems(FXCollections.observableArrayList());
                showFeedback(feedback,
                        "Audit log data is unavailable.", false);
            }
        };
        panel.getChildren().addAll(table, feedback);
        page.getChildren().add(panel);
        showContent(page, auditButton, reload);
        reload.run();
    }

    private void loadOverviewMetricsForStatus() {
        try {
            OverviewMetrics metrics = dashboardDao.loadOverviewMetrics();
            setStatus("Database connected • " + metrics.activeUsers()
                    + " active accounts", "status-connected");
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to refresh administrator system status", exception);
            setStatus("Database unavailable", "status-warning");
        }
    }

    private BarChart<String, Number> createDepartmentChart(
            List<DepartmentPerformance> rows) {
        CategoryAxis departments = new CategoryAxis();
        departments.setLabel("Department");
        NumberAxis appointmentCounts = new NumberAxis();
        appointmentCounts.setLabel("Appointments");
        BarChart<String, Number> chart =
                new BarChart<>(departments, appointmentCounts);
        chart.setTitle("Appointments by department");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(280);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (DepartmentPerformance row : rows) {
            series.getData().add(new XYChart.Data<>(
                    row.departmentName(), row.appointmentsToday()));
        }
        chart.getData().add(series);
        return chart;
    }

    private TableView<DepartmentPerformance>
    createDepartmentPerformanceTable(List<DepartmentPerformance> rows) {
        TableView<DepartmentPerformance> table =
                createTable("No department performance data");
        table.setPrefHeight(220);
        table.getColumns().add(column("Department", 190,
                DepartmentPerformance::departmentName));
        table.getColumns().add(column("Appointments today", 150,
                row -> String.valueOf(row.appointmentsToday())));
        table.getColumns().add(column("Active staff", 130,
                row -> String.valueOf(row.activeStaff())));
        table.setItems(FXCollections.observableArrayList(rows));
        return table;
    }

    private VBox createPage(String title, String subtitle) {
        VBox page = new VBox(14);
        page.getStyleClass().addAll("dashboard-content", "admin-content");
        VBox heading = new VBox(3);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("page-subtitle");
        heading.getChildren().addAll(titleLabel, subtitleLabel);
        page.getChildren().add(heading);
        return page;
    }

    private VBox createPanel(String title, String description) {
        VBox panel = new VBox(10);
        panel.getStyleClass().addAll("panel", "admin-panel");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("panel-hint");
        descriptionLabel.setWrapText(true);
        panel.getChildren().addAll(titleLabel, descriptionLabel);
        return panel;
    }

    private VBox createMessagePanel(String title, String message) {
        VBox panel = createPanel(title, message);
        return panel;
    }

    private <T> TableView<T> createTable(String placeholder) {
        TableView<T> table = new TableView<>();
        table.getStyleClass().addAll("dashboard-table", "admin-table");
        table.setPrefHeight(300);
        table.setPlaceholder(new Label(placeholder));
        return table;
    }

    private <T> TableColumn<T, String> column(
            String title, double width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private void addMetric(GridPane grid, String title, int value, int index) {
        addMetric(grid, title, String.valueOf(value), index);
    }

    private void addMetric(GridPane grid, String title, String value,
                           int index) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("metric-card", "admin-metric-card");
        card.setMaxWidth(Double.MAX_VALUE);
        Label name = new Label(title);
        name.getStyleClass().add("metric-label");
        name.setWrapText(true);
        Label count = new Label(value);
        count.getStyleClass().add("metric-value");
        count.setWrapText(true);
        Label icon = new Label("▤");
        icon.getStyleClass().add("metric-icon");
        card.getChildren().addAll(icon, count, name);
        GridPane.setHgrow(card, Priority.ALWAYS);
        grid.add(card, index % 4, index / 4);
    }

    private GridPane createFormGrid() {
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        return form;
    }

    private void addFormField(GridPane form, int row, String name,
                              Node control) {
        Label label = new Label(name);
        label.getStyleClass().add("field-label");
        form.add(label, 0, row);
        form.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        if (control instanceof TextField field) {
            field.setMaxWidth(Double.MAX_VALUE);
        } else if (control instanceof ComboBox<?> comboBox) {
            comboBox.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private Dialog<ButtonType> createDialog(String title, String heading) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(heading);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);
        if (contentScroll.getScene() != null) {
            dialog.initOwner(contentScroll.getScene().getWindow());
        }
        return dialog;
    }

    private boolean confirmAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (contentScroll.getScene() != null) {
            alert.initOwner(contentScroll.getScene().getWindow());
        }
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private TextField createField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("admin-form-field");
        return field;
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

    private Label createCountLabel() {
        Label label = new Label();
        label.getStyleClass().add("table-count-badge");
        return label;
    }

    private Label createFeedbackLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.getStyleClass().add("admin-feedback");
        return label;
    }

    private Label createUnavailableLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        label.setWrapText(true);
        return label;
    }

    private void showFeedback(Label label, String message, boolean success) {
        label.setText(message == null ? "" : message);
        label.getStyleClass().removeAll(
                "admin-feedback-success", "admin-feedback-error");
        label.getStyleClass().add(success
                ? "admin-feedback-success" : "admin-feedback-error");
    }

    private void showContent(VBox page, Button activeButton, Runnable refresh) {
        contentScroll.setContent(page);
        contentScroll.setVvalue(0);
        setActiveNavigation(activeButton);
        activeRefresh = refresh;
    }

    private void setActiveNavigation(Button activeButton) {
        List.of(overviewButton, patientsButton, appointmentsButton,
                staffButton, usersButton, departmentsButton,
                reportsButton, auditButton)
                .forEach(button -> button.getStyleClass()
                        .remove("nav-button-active"));
        activeButton.getStyleClass().add("nav-button-active");
    }

    private void setNavigationEnabled(boolean enabled) {
        List.of(overviewButton, patientsButton, appointmentsButton,
                staffButton, usersButton, departmentsButton,
                reportsButton, auditButton)
                .forEach(button -> button.setDisable(!enabled));
    }

    private boolean requireAdministrator() {
        if (UserSession.hasRole("Administrator")) {
            return true;
        }
        setStatus("Administrator access is required", "status-warning");
        return false;
    }

    private long currentUserId() {
        if (!requireAdministrator()) {
            throw new SecurityException(
                    "Administrator session is no longer available.");
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

    private String formatStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        String normalized = status.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0))
                + normalized.substring(1);
    }

    private void setStatus(String text, String stateClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(
                "status-warning", "status-connected");
        statusLabel.getStyleClass().add(stateClass);
    }

    @FXML
    private void handleLogout() throws Exception {
        UserSession.clear();
        Parent root = FXMLLoader.load(
                MainApp.class.getResource("/ug/sjch/views/login.fxml"));
        Stage stage = (Stage) dateLabel.getScene().getWindow();
        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource("/ug/sjch/styles/application.css")
                        .toExternalForm());
        stage.setScene(scene);
        stage.setTitle("St Jude Central Hospital - SJCH");
    }
}
