package ug.sjch.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
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
import javafx.stage.Stage;

import ug.sjch.MainApp;
import ug.sjch.dao.OutpatientDashboardDao;
import ug.sjch.dao.OutpatientDashboardDao.AppointmentRow;
import ug.sjch.dao.OutpatientDashboardDao.ConsultationRow;
import ug.sjch.dao.OutpatientDashboardDao.FollowUpRow;
import ug.sjch.dao.OutpatientDashboardDao.LaboratoryRequestRow;
import ug.sjch.dao.OutpatientDashboardDao.MedicalRecordRow;
import ug.sjch.dao.OutpatientDashboardDao.MedicineOption;
import ug.sjch.dao.OutpatientDashboardDao.Metrics;
import ug.sjch.dao.OutpatientDashboardDao.OptionalRows;
import ug.sjch.dao.OutpatientDashboardDao.PrescriptionRow;
import ug.sjch.dao.OutpatientDashboardDao.PrescriptionItemRow;
import ug.sjch.dao.OutpatientDashboardDao.LaboratoryRequestDetails;
import ug.sjch.model.User;
import ug.sjch.security.UserSession;

public class OutpatientDashboardController {
    private static final Logger LOGGER =
            Logger.getLogger(OutpatientDashboardController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final String[] CARD_STYLES = {
            "outpatient-card-blue", "outpatient-card-teal",
            "outpatient-card-amber", "outpatient-card-purple",
            "outpatient-card-red"
    };

    @FXML private Label sidebarUserLabel;
    @FXML private Label sidebarRoleLabel;
    @FXML private Label topUserLabel;
    @FXML private Label topRoleLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private Label queueCountLabel;
    @FXML private Label consultationCountLabel;
    @FXML private Label followUpCountLabel;
    @FXML private Label laboratoryCountLabel;
    @FXML private TextField searchField;
    @FXML private VBox sidebar;
    @FXML private Region sidebarResizeHandle;
    @FXML private GridPane metricGrid;

    @FXML private TableView<AppointmentRow> queueTable;
    @FXML private TableColumn<AppointmentRow, String> queueNumberColumn;
    @FXML private TableColumn<AppointmentRow, String> queuePatientColumn;
    @FXML private TableColumn<AppointmentRow, String> queueTimeColumn;
    @FXML private TableColumn<AppointmentRow, String> queueStatusColumn;

    @FXML private TableView<ConsultationRow> consultationTable;
    @FXML private TableColumn<ConsultationRow, String> consultationNumberColumn;
    @FXML private TableColumn<ConsultationRow, String> consultationPatientColumn;
    @FXML private TableColumn<ConsultationRow, String> consultationTimeColumn;
    @FXML private TableColumn<ConsultationRow, String> consultationStatusColumn;

    @FXML private TableView<FollowUpRow> followUpTable;
    @FXML private TableColumn<FollowUpRow, String> followUpNumberColumn;
    @FXML private TableColumn<FollowUpRow, String> followUpPatientColumn;
    @FXML private TableColumn<FollowUpRow, String> followUpDateColumn;
    @FXML private Button cancelFollowUpButton;

    @FXML private TableView<LaboratoryRequestRow> laboratoryTable;
    @FXML private TableColumn<LaboratoryRequestRow, String> laboratoryNumberColumn;
    @FXML private TableColumn<LaboratoryRequestRow, String> laboratoryPatientColumn;
    @FXML private TableColumn<LaboratoryRequestRow, String> laboratoryTestColumn;
    @FXML private TableColumn<LaboratoryRequestRow, String> laboratoryStatusColumn;
    @FXML private Button editLaboratoryRequestButton;
    @FXML private Button cancelLaboratoryRequestButton;

    private final OutpatientDashboardDao dashboardDao =
            new OutpatientDashboardDao();
    private FilteredList<AppointmentRow> filteredQueue;
    private FilteredList<ConsultationRow> filteredConsultations;
    private FilteredList<FollowUpRow> filteredFollowUps;
    private FilteredList<LaboratoryRequestRow> filteredLaboratoryRequests;

    @FXML
    private void initialize() {
        SidebarResizer.install(sidebar, sidebarResizeHandle);
        boolean canUseClinicalActions = isClinicalUser()
                && UserSession.getCurrentUser().staffId() != null;
        sidebar.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getStyleClass().contains("nav-button"))
                .forEach(button -> button.setDisable(!canUseClinicalActions));
        configureTables();
        editLaboratoryRequestButton.setDisable(true);
        cancelLaboratoryRequestButton.setDisable(true);
        cancelFollowUpButton.setDisable(true);
        laboratoryTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, selected) -> {
                    boolean editable = selected != null
                            && "REQUESTED".equals(selected.status());
                    editLaboratoryRequestButton.setDisable(!editable);
                    cancelLaboratoryRequestButton.setDisable(!editable);
                });
        followUpTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, selected) ->
                        cancelFollowUpButton.setDisable(
                        selected == null
                                || !"SCHEDULED".equals(
                                        selected.status())));
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> filterTables(newValue)
        );

        if (!isClinicalUser()) {
            setMetricPlaceholders();
            setStatus("Doctor session is not available", "status-warning");
            return;
        }

        User user = UserSession.getCurrentUser();
        sidebarUserLabel.setText(user.username());
        sidebarRoleLabel.setText(user.roleName());
        topUserLabel.setText(user.username());
        topRoleLabel.setText(user.roleName());
        dateLabel.setText(LocalDate.now().format(DATE_FORMAT));

        if (user.staffId() == null) {
            setMetricPlaceholders();
            setStatus("No staff record is linked to this account",
                    "status-warning");
            setModulePlaceholders("Link a staff record to view assigned patients");
            return;
        }
        loadDashboard(user.staffId());
    }

    private boolean isClinicalUser() {
        if (!UserSession.isAuthenticated()) {
            return false;
        }
        String role = UserSession.getCurrentUser().roleName();
        return "Doctor".equalsIgnoreCase(role)
                || "Medical Officer".equalsIgnoreCase(role);
    }

    private void configureTables() {
        setAppointmentColumns(queueNumberColumn, queuePatientColumn,
                queueTimeColumn, queueStatusColumn);
        consultationNumberColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().patientNumber()));
        consultationPatientColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().patientName()));
        consultationTimeColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().consultationTime()));
        consultationStatusColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        formatStatus(cell.getValue().status())));

        followUpNumberColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().patientNumber()));
        followUpPatientColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().patientName()));
        followUpDateColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().followUpDate()));

        laboratoryNumberColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().patientNumber()));
        laboratoryPatientColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().patientName()));
        laboratoryTestColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().testName()));
        laboratoryStatusColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        formatStatus(cell.getValue().status())));

        queueTable.setPlaceholder(new Label("No patients are waiting"));
        consultationTable.setPlaceholder(
                new Label("No consultations are recorded for today")
        );
        followUpTable.setPlaceholder(
                new Label("No follow-up appointments scheduled")
        );
        laboratoryTable.setPlaceholder(
                new Label("No pending laboratory requests")
        );
    }

    private void setAppointmentColumns(
            TableColumn<AppointmentRow, String> numberColumn,
            TableColumn<AppointmentRow, String> patientColumn,
            TableColumn<AppointmentRow, String> timeColumn,
            TableColumn<AppointmentRow, String> statusColumn) {
        numberColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().patientNumber()));
        patientColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().patientName()));
        timeColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        cell.getValue().appointmentTime()));
        statusColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        formatStatus(cell.getValue().status())));
    }

    private void loadDashboard(long doctorId) {
        boolean partialFailure = false;
        try {
            Metrics metrics = dashboardDao.loadMetrics(doctorId);
            metricGrid.getChildren().clear();
            addMetric("Patients waiting", metrics.patientsWaiting(),
                    "◷", 0);
            addMetric("Today's consultations", metrics.consultationsToday(),
                    "⚕", 1);
            addMetric("Completed consultations", metrics.completedConsultations(),
                    "✓", 2);
            addMetric("Follow-up appointments", metrics.followUpAppointments(),
                    "▣", 3);
            addMetric("Pending lab requests",
                    metrics.pendingLaboratoryRequests(), "⚗", 4);
            partialFailure = metrics.followUpAppointments() == null
                    || metrics.pendingLaboratoryRequests() == null
                    || metrics.consultationsToday() == null
                    || metrics.completedConsultations() == null;
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load outpatient dashboard metrics", exception);
            setMetricPlaceholders();
            setStatus("Outpatient data is unavailable", "status-warning");
            return;
        }

        try {
            setAppointments(queueTable, dashboardDao.loadWaitingQueue(doctorId));
            queueCountLabel.setText(countText(filteredQueue.size(), "patient"));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING, "Unable to load the patient queue", exception);
            queueTable.setPlaceholder(new Label("Patient queue is unavailable"));
            queueCountLabel.setText("Unavailable");
            partialFailure = true;
        }

        try {
            OptionalRows<ConsultationRow> consultations =
                    dashboardDao.loadTodayConsultations(doctorId);
            if (consultations.configured()) {
                filteredConsultations = new FilteredList<>(
                        FXCollections.observableArrayList(consultations.rows()),
                        row -> true
                );
                consultationTable.setItems(filteredConsultations);
                consultationCountLabel.setText(
                        countText(consultations.rows().size(), "consultation")
                );
            } else {
                consultationTable.setPlaceholder(
                        new Label("Consultation module is not configured in Oracle")
                );
                consultationCountLabel.setText("Not configured");
                partialFailure = true;
            }
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load today's consultations", exception);
            consultationTable.setPlaceholder(
                    new Label("Consultation data is unavailable")
            );
            consultationCountLabel.setText("Unavailable");
            partialFailure = true;
        }

        try {
            OptionalRows<FollowUpRow> followUps =
                    dashboardDao.loadFollowUps(doctorId);
            if (followUps.configured()) {
                filteredFollowUps = new FilteredList<>(
                        FXCollections.observableArrayList(followUps.rows()),
                        row -> true
                );
                followUpTable.setItems(filteredFollowUps);
                followUpCountLabel.setText(
                        countText(followUps.rows().size(), "appointment")
                );
            } else {
                followUpTable.setPlaceholder(
                        new Label("Follow-up module is not configured in Oracle")
                );
                followUpCountLabel.setText("Not configured");
                partialFailure = true;
            }
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load follow-up appointments", exception);
            followUpTable.setPlaceholder(
                    new Label("Follow-up data is unavailable")
            );
            followUpCountLabel.setText("Unavailable");
            partialFailure = true;
        }

        try {
            OptionalRows<LaboratoryRequestRow> requests =
                    dashboardDao.loadPendingLaboratoryRequests(doctorId);
            if (requests.configured()) {
                filteredLaboratoryRequests = new FilteredList<>(
                        FXCollections.observableArrayList(requests.rows()),
                        row -> true
                );
                laboratoryTable.setItems(filteredLaboratoryRequests);
                laboratoryCountLabel.setText(
                        countText(requests.rows().size(), "request")
                );
            } else {
                laboratoryTable.setPlaceholder(
                        new Label("Laboratory request module is not configured")
                );
                laboratoryCountLabel.setText("Not configured");
                partialFailure = true;
            }
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load pending laboratory requests", exception);
            laboratoryTable.setPlaceholder(
                    new Label("Laboratory request data is unavailable")
            );
            laboratoryCountLabel.setText("Unavailable");
            partialFailure = true;
        }

        setStatus(
                partialFailure
                        ? "Appointments connected; some clinical modules are unavailable"
                        : "Clinical workspace connected",
                partialFailure ? "status-warning" : "status-connected"
        );
    }

    private void setAppointments(TableView<AppointmentRow> table,
                                 List<AppointmentRow> rows) {
        FilteredList<AppointmentRow> filtered =
                new FilteredList<>(FXCollections.observableArrayList(rows),
                        row -> true);
        table.setItems(filtered);
        if (table == queueTable) {
            filteredQueue = filtered;
        }
    }

    private void filterTables(String searchText) {
        String query = searchText == null
                ? ""
                : searchText.trim().toLowerCase(Locale.ROOT);
        if (filteredQueue != null) {
            filteredQueue.setPredicate(row -> matches(row, query));
            queueCountLabel.setText(countText(filteredQueue.size(), "patient"));
        }
        if (filteredConsultations != null) {
            filteredConsultations.setPredicate(row ->
                    query.isEmpty()
                            || contains(row.patientNumber(), query)
                            || contains(row.patientName(), query)
                            || contains(formatStatus(row.status()), query));
            consultationCountLabel.setText(
                    countText(filteredConsultations.size(), "consultation")
            );
        }
        if (filteredFollowUps != null) {
            filteredFollowUps.setPredicate(row ->
                    query.isEmpty()
                            || contains(row.patientNumber(), query)
                            || contains(row.patientName(), query));
            followUpCountLabel.setText(
                    countText(filteredFollowUps.size(), "appointment")
            );
        }
        if (filteredLaboratoryRequests != null) {
            filteredLaboratoryRequests.setPredicate(row ->
                    query.isEmpty()
                            || contains(row.patientNumber(), query)
                            || contains(row.patientName(), query)
                            || contains(row.testName(), query));
            laboratoryCountLabel.setText(
                    countText(filteredLaboratoryRequests.size(), "request")
            );
        }
    }

    private boolean matches(AppointmentRow row, String query) {
        return query.isEmpty()
                || contains(row.patientNumber(), query)
                || contains(row.patientName(), query)
                || contains(row.status(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String countText(int count, String item) {
        return count + " " + item + (count == 1 ? "" : "s");
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
        addMetric("Patients waiting", "—", "◷", 0);
        addMetric("Today's consultations", "—", "⚕", 1);
        addMetric("Completed consultations", "—", "✓", 2);
        addMetric("Follow-up appointments", "—", "▣", 3);
        addMetric("Pending lab requests", "—", "⚗", 4);
    }

    private void setModulePlaceholders(String message) {
        queueTable.setPlaceholder(new Label(message));
        consultationTable.setPlaceholder(new Label(message));
        followUpTable.setPlaceholder(new Label(message));
        laboratoryTable.setPlaceholder(new Label(message));
    }

    private void addMetric(String title, Integer value, String icon, int column) {
        addMetric(title, value == null ? "—" : String.valueOf(value),
                icon, column);
    }

    private void addMetric(String title, String value, String icon, int column) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-label");
        titleLabel.setWrapText(true);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-value");
        valueLabel.setWrapText(true);
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("metric-icon");
        VBox card = new VBox(iconLabel, valueLabel, titleLabel);
        card.getStyleClass().addAll(
                "metric-card", "outpatient-metric-card",
                CARD_STYLES[column]
        );
        GridPane.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        metricGrid.add(card, column % 4, column / 4);
    }

    private void setStatus(String text, String stateClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(
                "status-warning", "status-connected"
        );
        statusLabel.getStyleClass().add(stateClass);
    }

    @FXML
    private void handleDashboard() {
        handleRefresh();
    }

    @FXML
    private void handlePatientQueue() {
        queueTable.requestFocus();
        queueTable.scrollTo(0);
        queueTable.getSelectionModel().selectFirst();
    }

    @FXML
    private void handlePatientRecords() {
        User user = currentClinicalUser();
        if (user == null || user.staffId() == null) {
            return;
        }
        try {
            OptionalRows<MedicalRecordRow> records =
                    dashboardDao.loadMedicalRecords(user.staffId());
            if (!records.configured()) {
                showInformation("The consultation module is not configured in Oracle.");
                return;
            }
            TableView<MedicalRecordRow> table = new TableView<>(
                    FXCollections.observableArrayList(records.rows()));
            table.getColumns().setAll(
                    column("Patient No.", MedicalRecordRow::patientNumber),
                    column("Patient", MedicalRecordRow::patientName),
                    column("Date", MedicalRecordRow::consultationDate),
                    column("Diagnosis", MedicalRecordRow::diagnosis),
                    column("Notes", MedicalRecordRow::clinicalNotes),
                    column("Status", row -> formatStatus(row.status())));
            showTableDialog("Patient medical records", table);
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to load patient records", exception);
        }
    }

    @FXML
    private void handleStartConsultation() {
        User user = currentClinicalUser();
        AppointmentRow selected = queueTable.getSelectionModel()
                .getSelectedItem();
        if (user == null || user.staffId() == null) {
            return;
        }
        if (selected == null) {
            showInformation("Select a checked-in patient from the queue first.");
            return;
        }
        try {
            long consultationId = dashboardDao.startConsultation(
                    selected.appointmentId(), user.staffId(), user.id());
            loadDashboard(user.staffId());
            showInformation("Consultation " + consultationId
                    + " is ready for diagnosis and clinical actions.");
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to start consultation", exception);
        }
    }

    @FXML
    private void handleDiagnosis() {
        ConsultationRow consultation = selectedActiveConsultation();
        User user = currentClinicalUser();
        if (consultation == null || user == null || user.staffId() == null) {
            return;
        }

        TextArea diagnosis = new TextArea();
        diagnosis.setPromptText("Required diagnosis");
        diagnosis.setPrefRowCount(3);
        TextArea notes = new TextArea();
        notes.setPromptText("Clinical notes (optional)");
        notes.setPrefRowCount(5);
        try {
            OptionalRows<MedicalRecordRow> records =
                    dashboardDao.loadMedicalRecords(user.staffId());
            if (!records.configured()) {
                showInformation("The consultation module is not configured in Oracle.");
                return;
            }
            records.rows().stream()
                    .filter(row -> row.consultationId()
                            == consultation.consultationId())
                    .findFirst()
                    .ifPresent(row -> {
                        diagnosis.setText(row.diagnosis() == null
                                ? "" : row.diagnosis());
                        notes.setText(row.clinicalNotes() == null
                                ? "" : row.clinicalNotes());
                    });
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to load consultation details", exception);
            return;
        }
        GridPane form = formGrid();
        form.addRow(0, new Label("Patient"),
                new Label(consultation.patientNumber() + " — "
                        + consultation.patientName()));
        form.addRow(1, new Label("Diagnosis"), diagnosis);
        form.addRow(2, new Label("Clinical notes"), notes);

        Dialog<ButtonType> dialog =
                createFormDialog("Record diagnosis", form);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            dashboardDao.saveDiagnosis(consultation.consultationId(),
                    user.staffId(), user.id(), diagnosis.getText(),
                    notes.getText());
            loadDashboard(user.staffId());
            showInformation("Diagnosis saved.");
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to save diagnosis", exception);
        }
    }

    @FXML
    private void handleCompleteConsultation() {
        ConsultationRow consultation = selectedActiveConsultation();
        User user = currentClinicalUser();
        if (consultation == null || user == null || user.staffId() == null) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Complete the consultation for "
                        + consultation.patientName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirmation.setHeaderText("Complete consultation");
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }
        try {
            dashboardDao.completeConsultation(consultation.consultationId(),
                    user.staffId(), user.id());
            loadDashboard(user.staffId());
            showInformation("Consultation completed.");
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to complete consultation", exception);
        }
    }

    @FXML
    private void handlePrescriptions() {
        User user = currentClinicalUser();
        if (user == null || user.staffId() == null) {
            return;
        }
        try {
            OptionalRows<PrescriptionRow> prescriptions =
                    dashboardDao.loadPrescriptions(user.staffId());
            if (!prescriptions.configured()) {
                showInformation("The prescription module is not configured in Oracle.");
                return;
            }
            TableView<PrescriptionRow> table = new TableView<>(
                    FXCollections.observableArrayList(prescriptions.rows()));
            table.getColumns().setAll(
                    column("Patient No.", PrescriptionRow::patientNumber),
                    column("Patient", PrescriptionRow::patientName),
                    column("Created", PrescriptionRow::createdAt),
                    column("Status", row -> formatStatus(row.status())));
            table.setPrefSize(700, 360);
            table.setColumnResizePolicy(
                    TableView.CONSTRAINED_RESIZE_POLICY);
            ButtonType createPrescription = new ButtonType(
                    "New prescription", ButtonBar.ButtonData.OK_DONE);
            ButtonType editPrescription = new ButtonType(
                    "Edit selected", ButtonBar.ButtonData.OTHER);
            ButtonType cancelPrescription = new ButtonType(
                    "Cancel selected", ButtonBar.ButtonData.OTHER);
            Dialog<ButtonType> historyDialog = new Dialog<>();
            historyDialog.setTitle("Prescription history");
            historyDialog.getDialogPane().setContent(table);
            historyDialog.getDialogPane().getButtonTypes().setAll(
                    createPrescription, editPrescription, cancelPrescription,
                    ButtonType.CLOSE);
            Button editButton = (Button) historyDialog.getDialogPane()
                    .lookupButton(editPrescription);
            Button cancelButton = (Button) historyDialog.getDialogPane()
                    .lookupButton(cancelPrescription);
            editButton.setDisable(true);
            cancelButton.setDisable(true);
            table.getSelectionModel().selectedItemProperty().addListener(
                    (observable, oldValue, selected) -> {
                        boolean pending = selected != null
                                && "PENDING".equals(selected.status());
                        editButton.setDisable(!pending);
                        cancelButton.setDisable(!pending);
                    });
            PrescriptionRow selectedPrescription = null;
            ButtonType selectedAction = historyDialog.showAndWait()
                    .orElse(ButtonType.CLOSE);
            if (selectedAction == editPrescription
                    || selectedAction == cancelPrescription) {
                selectedPrescription = table.getSelectionModel()
                        .getSelectedItem();
            }
            if (selectedAction == editPrescription
                    && selectedPrescription != null) {
                showPrescriptionEditDialog(user, selectedPrescription);
                return;
            }
            if (selectedAction == cancelPrescription
                    && selectedPrescription != null) {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        "Cancel prescription "
                                + selectedPrescription.prescriptionId()
                                + "? It will remain in the clinical history.",
                        ButtonType.YES, ButtonType.NO);
                confirmation.setHeaderText("Cancel prescription");
                if (confirmation.showAndWait().orElse(ButtonType.NO)
                        == ButtonType.YES) {
                    dashboardDao.cancelPrescription(
                            selectedPrescription.prescriptionId(),
                            user.staffId(), user.id());
                    loadDashboard(user.staffId());
                    showInformation("Prescription cancelled.");
                }
                return;
            }
            if (selectedAction != createPrescription) {
                return;
            }

            OptionalRows<MedicineOption> medicines =
                    dashboardDao.loadActiveMedicines();
            if (!medicines.configured()) {
                showInformation("The medicines module is not configured in Oracle.");
                return;
            }
            if (medicines.rows().isEmpty()) {
                showInformation("No active medicines are available to prescribe.");
                return;
            }
            ConsultationRow consultation = selectedActiveConsultation();
            if (consultation == null) {
                return;
            }
            showPrescriptionDialog(user, consultation, medicines.rows());
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to manage prescriptions", exception);
        }
    }

    private void showPrescriptionEditDialog(User user,
                                            PrescriptionRow prescription)
            throws SQLException {
        List<PrescriptionItemRow> items =
                dashboardDao.loadPrescriptionItems(
                        prescription.prescriptionId(), user.staffId(), user.id());
        if (items.isEmpty()) {
            showInformation("This prescription has no editable items.");
            return;
        }
        OptionalRows<MedicineOption> activeMedicines =
                dashboardDao.loadActiveMedicines();
        if (!activeMedicines.configured()) {
            showInformation("The medicines module is not configured in Oracle.");
            return;
        }
        List<MedicineOption> medicineOptions =
                new java.util.ArrayList<>(activeMedicines.rows());
        items.stream().map(PrescriptionItemRow::medicine)
                .filter(option -> !medicineOptions.contains(option))
                .forEach(medicineOptions::add);

        ComboBox<PrescriptionItemRow> item = new ComboBox<>(
                FXCollections.observableArrayList(items));
        item.getSelectionModel().selectFirst();
        ComboBox<MedicineOption> medicine = new ComboBox<>(
                FXCollections.observableArrayList(medicineOptions));
        TextField dosage = new TextField();
        TextField route = new TextField();
        TextField frequency = new TextField();
        TextField duration = new TextField();
        TextField quantity = new TextField();
        TextArea instructions = new TextArea();
        instructions.setPrefRowCount(3);
        TextArea notes = new TextArea();
        notes.setPrefRowCount(3);
        item.valueProperty().addListener((observable, oldItem, selected) -> {
            if (selected != null) {
                populatePrescriptionItem(selected, medicine, dosage, route,
                        frequency, duration, quantity, instructions, notes);
            }
        });
        populatePrescriptionItem(items.get(0), medicine, dosage, route,
                frequency, duration, quantity, instructions, notes);

        GridPane form = formGrid();
        form.addRow(0, new Label("Prescription item"), item);
        form.addRow(1, new Label("Medicine"), medicine);
        form.addRow(2, new Label("Dosage"), dosage);
        form.addRow(3, new Label("Route"), route);
        form.addRow(4, new Label("Frequency"), frequency);
        form.addRow(5, new Label("Duration (days)"), duration);
        form.addRow(6, new Label("Quantity"), quantity);
        form.addRow(7, new Label("Item instructions"), instructions);
        form.addRow(8, new Label("Prescription notes"), notes);
        Dialog<ButtonType> dialog =
                createFormDialog("Edit pending prescription", form);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            PrescriptionItemRow selectedItem = item.getValue();
            MedicineOption selectedMedicine = medicine.getValue();
            if (selectedItem == null || selectedMedicine == null) {
                throw new IllegalArgumentException(
                        "Select a prescription item and medicine.");
            }
            Integer durationDays = duration.getText().isBlank()
                    ? null : Integer.valueOf(duration.getText().trim());
            BigDecimal prescribedQuantity =
                    new BigDecimal(quantity.getText().trim());
            dashboardDao.updatePrescriptionItem(
                    prescription.prescriptionId(),
                    selectedItem.prescriptionItemId(), user.staffId(),
                    user.id(), selectedMedicine.medicineId(),
                    dosage.getText(), route.getText(), frequency.getText(),
                    durationDays, prescribedQuantity, instructions.getText(),
                    notes.getText());
            loadDashboard(user.staffId());
            showInformation("Prescription item updated.");
        } catch (NumberFormatException exception) {
            showActionFailure(
                    "Enter a whole number for duration and a numeric quantity.",
                    exception);
        }
    }

    private void populatePrescriptionItem(
            PrescriptionItemRow item, ComboBox<MedicineOption> medicine,
            TextField dosage, TextField route, TextField frequency,
            TextField duration, TextField quantity, TextArea instructions,
            TextArea notes) {
        medicine.getSelectionModel().select(item.medicine());
        dosage.setText(item.dosage() == null ? "" : item.dosage());
        route.setText(item.route() == null ? "" : item.route());
        frequency.setText(item.frequency() == null ? "" : item.frequency());
        duration.setText(item.durationDays() == null
                ? "" : item.durationDays().toString());
        quantity.setText(item.quantityPrescribed() == null
                ? "" : item.quantityPrescribed().toPlainString());
        instructions.setText(item.instructions() == null
                ? "" : item.instructions());
        notes.setText(item.prescriptionNotes() == null
                ? "" : item.prescriptionNotes());
    }

    private void showPrescriptionDialog(User user,
                                        ConsultationRow consultation,
                                        List<MedicineOption> medicines) {
        ComboBox<MedicineOption> medicine = new ComboBox<>(
                FXCollections.observableArrayList(medicines));
        medicine.getSelectionModel().selectFirst();
        TextField dosage = new TextField();
        dosage.setPromptText("e.g. 500 mg");
        TextField route = new TextField();
        route.setPromptText("e.g. Oral");
        TextField frequency = new TextField();
        frequency.setPromptText("e.g. Twice daily");
        TextField duration = new TextField();
        duration.setPromptText("Days (optional)");
        TextField quantity = new TextField();
        quantity.setPromptText("Quantity");
        TextArea instructions = new TextArea();
        instructions.setPromptText("Additional instructions (optional)");
        instructions.setPrefRowCount(3);

        GridPane form = formGrid();
        form.addRow(0, new Label("Patient"),
                new Label(consultation.patientNumber() + " — "
                        + consultation.patientName()));
        form.addRow(1, new Label("Medicine"), medicine);
        form.addRow(2, new Label("Dosage"), dosage);
        form.addRow(3, new Label("Route"), route);
        form.addRow(4, new Label("Frequency"), frequency);
        form.addRow(5, new Label("Duration"), duration);
        form.addRow(6, new Label("Quantity"), quantity);
        form.addRow(7, new Label("Instructions"), instructions);

        Dialog<ButtonType> dialog =
                createFormDialog("Create prescription", form);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            Integer durationDays = duration.getText().isBlank()
                    ? null : Integer.valueOf(duration.getText().trim());
            BigDecimal prescribedQuantity =
                    new BigDecimal(quantity.getText().trim());
            MedicineOption selectedMedicine = medicine.getValue();
            if (selectedMedicine == null) {
                throw new IllegalArgumentException("Select a medicine.");
            }
            long prescriptionId = dashboardDao.createPrescription(
                    consultation.consultationId(), user.staffId(), user.id(),
                    selectedMedicine.medicineId(), dosage.getText(),
                    route.getText(), frequency.getText(), durationDays,
                    prescribedQuantity, instructions.getText());
            loadDashboard(user.staffId());
            showInformation("Prescription " + prescriptionId + " created.");
        } catch (NumberFormatException exception) {
            showActionFailure(
                    "Enter a valid whole number for duration and a numeric "
                            + "quantity.", exception);
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to create prescription", exception);
        }
    }

    @FXML
    private void handleLaboratoryRequests() {
        ConsultationRow consultation = selectedActiveConsultation();
        User user = currentClinicalUser();
        if (consultation == null || user == null || user.staffId() == null) {
            return;
        }
        TextField testName = new TextField();
        testName.setPromptText("Investigation requested");
        TextArea instructions = new TextArea();
        instructions.setPromptText("Instructions for the laboratory");
        instructions.setPrefRowCount(4);
        GridPane form = formGrid();
        form.addRow(0, new Label("Patient"),
                new Label(consultation.patientNumber() + " — "
                        + consultation.patientName()));
        form.addRow(1, new Label("Investigation"), testName);
        form.addRow(2, new Label("Instructions"), instructions);
        Dialog<ButtonType> dialog =
                createFormDialog("Request laboratory test", form);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            long requestId = dashboardDao.createLaboratoryRequest(
                    consultation.consultationId(), user.staffId(), user.id(),
                    testName.getText(), instructions.getText());
            loadDashboard(user.staffId());
            showInformation("Laboratory request " + requestId + " created.");
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to create laboratory request", exception);
        }
    }

    @FXML
    private void handleEditLaboratoryRequest() {
        User user = currentClinicalUser();
        LaboratoryRequestRow request =
                laboratoryTable.getSelectionModel().getSelectedItem();
        if (user == null || user.staffId() == null || request == null) {
            return;
        }
        try {
            LaboratoryRequestDetails details =
                    dashboardDao.loadLaboratoryRequestDetails(
                            request.requestId(), user.staffId(), user.id());
            TextField testName = new TextField(details.testName());
            TextArea instructions = new TextArea(
                    details.instructions() == null
                            ? "" : details.instructions());
            instructions.setPromptText("Instructions for the laboratory");
            instructions.setPrefRowCount(4);
            GridPane form = formGrid();
            form.addRow(0, new Label("Test"), testName);
            form.addRow(1, new Label("Instructions"), instructions);
            Dialog<ButtonType> dialog =
                    createFormDialog("Edit laboratory request", form);
            if (dialog.showAndWait().orElse(ButtonType.CANCEL)
                    != ButtonType.OK) {
                return;
            }
            dashboardDao.updateLaboratoryRequest(request.requestId(),
                    user.staffId(), user.id(), testName.getText(),
                    instructions.getText());
            loadDashboard(user.staffId());
            showInformation("Laboratory request updated.");
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to edit laboratory request", exception);
        }
    }

    @FXML
    private void handleCancelLaboratoryRequest() {
        User user = currentClinicalUser();
        LaboratoryRequestRow request =
                laboratoryTable.getSelectionModel().getSelectedItem();
        if (user == null || user.staffId() == null || request == null) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Cancel the request for " + request.testName()
                        + " for " + request.patientName()
                        + "? The request will remain in the clinical history.",
                ButtonType.YES, ButtonType.NO);
        confirmation.setHeaderText("Cancel laboratory request");
        if (confirmation.showAndWait().orElse(ButtonType.NO)
                != ButtonType.YES) {
            return;
        }
        try {
            dashboardDao.cancelLaboratoryRequest(request.requestId(),
                    user.staffId(), user.id());
            loadDashboard(user.staffId());
            showInformation("Laboratory request cancelled.");
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to cancel laboratory request", exception);
        }
    }

    @FXML
    private void handleConsultationHistory() {
        User user = currentClinicalUser();
        if (user == null || user.staffId() == null) {
            return;
        }
        try {
            OptionalRows<ConsultationRow> history =
                    dashboardDao.loadConsultationHistory(user.staffId());
            if (!history.configured()) {
                showInformation("The consultation module is not configured in Oracle.");
                return;
            }
            TableView<ConsultationRow> table = new TableView<>(
                    FXCollections.observableArrayList(history.rows()));
            table.getColumns().setAll(
                    column("Patient No.", ConsultationRow::patientNumber),
                    column("Patient", ConsultationRow::patientName),
                    column("Date / time", ConsultationRow::consultationTime),
                    column("Status", row -> formatStatus(row.status())));
            showTableDialog("Consultation history", table);
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to load consultation history", exception);
        }
    }

    @FXML
    private void handleFollowUpManagement() {
        User user = currentClinicalUser();
        if (user == null || user.staffId() == null) {
            return;
        }
        TextField patientNumber = new TextField();
        patientNumber.setPromptText("Registered patient number");
        DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(7));
        TextField time = new TextField("09:00");
        time.setPromptText("24-hour time, e.g. 09:00");
        TextField reason = new TextField();
        reason.setPromptText("Reason for follow-up (optional)");
        GridPane form = formGrid();
        form.addRow(0, new Label("Patient number"), patientNumber);
        form.addRow(1, new Label("Date"), datePicker);
        form.addRow(2, new Label("Time"), time);
        form.addRow(3, new Label("Reason"), reason);

        Dialog<ButtonType> dialog =
                createFormDialog("Schedule follow-up", form);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            LocalTime appointmentTime = LocalTime.parse(time.getText().trim());
            LocalDateTime appointmentDateTime = LocalDateTime.of(
                    datePicker.getValue(), appointmentTime);
            long appointmentId = dashboardDao.scheduleFollowUp(
                    patientNumber.getText(), user.staffId(), user.id(),
                    appointmentDateTime, reason.getText());
            loadDashboard(user.staffId());
            showInformation("Follow-up appointment " + appointmentId
                    + " scheduled.");
        } catch (DateTimeParseException | NullPointerException exception) {
            showActionFailure(
                    "Choose a date and enter the time as HH:mm.", exception);
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to schedule follow-up", exception);
        }
    }

    @FXML
    private void handleCancelFollowUp() {
        User user = currentClinicalUser();
        FollowUpRow followUp =
                followUpTable.getSelectionModel().getSelectedItem();
        if (user == null || user.staffId() == null || followUp == null) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Cancel the scheduled follow-up for "
                        + followUp.patientName()
                        + "? The appointment record will be retained.",
                ButtonType.YES, ButtonType.NO);
        confirmation.setHeaderText("Cancel follow-up appointment");
        if (confirmation.showAndWait().orElse(ButtonType.NO)
                != ButtonType.YES) {
            return;
        }
        try {
            dashboardDao.cancelFollowUp(followUp.followUpId(),
                    user.staffId(), user.id());
            loadDashboard(user.staffId());
            showInformation("Follow-up appointment cancelled.");
        } catch (SQLException | RuntimeException exception) {
            showActionFailure("Unable to cancel follow-up", exception);
        }
    }

    private ConsultationRow selectedActiveConsultation() {
        ConsultationRow selected =
                consultationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInformation("Select a consultation from today's list first.");
            return null;
        }
        if (!"IN_PROGRESS".equalsIgnoreCase(selected.status())) {
            showInformation("The selected consultation is not in progress.");
            return null;
        }
        return selected;
    }

    private User currentClinicalUser() {
        if (!isClinicalUser()) {
            showInformation("Sign in with a doctor or medical officer account.");
            return null;
        }
        User user = UserSession.getCurrentUser();
        if (user.staffId() == null) {
            showInformation("This account has no linked staff record.");
            return null;
        }
        return user;
    }

    private GridPane formGrid() {
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(12));
        form.setPrefWidth(520);
        return form;
    }

    private Dialog<ButtonType> createFormDialog(String title, GridPane form) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().setAll(
                ButtonType.OK, ButtonType.CANCEL);
        return dialog;
    }

    private <T> TableColumn<T, String> column(
            String title, Function<T, String> valueFactory) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(valueFactory.apply(cell.getValue())));
        return column;
    }

    private void showTableDialog(String title, TableView<?> table) {
        table.setPrefSize(760, 420);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(table);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showInformation(String message) {
        new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait();
    }

    private void showActionFailure(String message, Exception exception) {
        LOGGER.log(Level.WARNING, message, exception);
        String details = exception.getMessage() == null
                ? "See the application log for details."
                : exception.getMessage();
        new Alert(Alert.AlertType.ERROR,
                message + ": " + details, ButtonType.OK)
                .showAndWait();
    }

    @FXML
    private void handleRefresh() {
        if (isClinicalUser() && UserSession.getCurrentUser().staffId() != null) {
            loadDashboard(UserSession.getCurrentUser().staffId());
        }
    }

    @FXML
    private void handleLogout() throws Exception {
        UserSession.clear();
        Parent root = FXMLLoader.load(
                MainApp.class.getResource("/ug/sjch/views/login.fxml")
        );
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource("/ug/sjch/styles/application.css")
                        .toExternalForm()
        );
        stage.setScene(scene);
        stage.setTitle("St Jude Central Hospital - SJCH");
    }
}
