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
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
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
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

import java.util.Optional;

import ug.sjch.MainApp;
import ug.sjch.dao.InpatientDashboardDao;
import ug.sjch.dao.InpatientDashboardDao.AdmissionRow;
import ug.sjch.dao.InpatientDashboardDao.BedOption;
import ug.sjch.dao.InpatientDashboardDao.BedRow;
import ug.sjch.dao.InpatientDashboardDao.DischargeRow;
import ug.sjch.dao.InpatientDashboardDao.Metrics;
import ug.sjch.dao.InpatientDashboardDao.TransferRow;
import ug.sjch.dao.InpatientDashboardDao.WardOption;
import ug.sjch.model.User;
import ug.sjch.security.UserSession;

public class InpatientDashboardController {
    private static final Logger LOGGER =
            Logger.getLogger(InpatientDashboardController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final String[] CARD_STYLES = {
            "inpatient-card-blue", "inpatient-card-red",
            "inpatient-card-green", "inpatient-card-purple",
            "inpatient-card-amber", "inpatient-card-teal"
    };

    @FXML private Label sidebarUserLabel;
    @FXML private Label sidebarRoleLabel;
    @FXML private Label topUserLabel;
    @FXML private Label topRoleLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private Label occupancyStateLabel;
    @FXML private Label homeBedCountLabel;
    @FXML private Label homeDischargeCountLabel;
    @FXML private VBox sidebar;
    @FXML private Region sidebarResizeHandle;
    @FXML private Button nursingDashboardButton;
    @FXML private Button dashboardButton;
    @FXML private Button wardManagementButton;
    @FXML private Button bedManagementButton;
    @FXML private Button admissionsButton;
    @FXML private Button bedAllocationButton;
    @FXML private Button transfersButton;
    @FXML private Button dischargeButton;
    @FXML private Button recordsButton;
    @FXML private Button reportsButton;
    @FXML private Button topNewAdmissionButton;
    @FXML private Button topAllocateBedButton;
    @FXML private GridPane metricGrid;
    @FXML private TableView<BedRow> bedTable;
    @FXML private TableColumn<BedRow, String> homeBedIdColumn;
    @FXML private TableColumn<BedRow, String> homeBedPatientColumn;
    @FXML private TableColumn<BedRow, String> homeBedStatusColumn;
    @FXML private TableColumn<BedRow, String> homeBedStayColumn;
    @FXML private TableColumn<BedRow, String> homeBedWardColumn;
    @FXML private TableView<DischargeRow> dischargeTable;
    @FXML private TableColumn<DischargeRow, String> homeDischargePatientNumberColumn;
    @FXML private TableColumn<DischargeRow, String> homeDischargePatientColumn;
    @FXML private TableColumn<DischargeRow, String> homeDischargeBedColumn;
    @FXML private TableColumn<DischargeRow, String> homeDischargeAdmittedColumn;
    @FXML private TableColumn<DischargeRow, String> homeDischargeStatusColumn;
    @FXML private ScrollPane contentScroll;
    @FXML private VBox dashboardContent;

    private final InpatientDashboardDao dashboardDao =
            new InpatientDashboardDao();
    private Runnable activeRefresh = this::loadDashboard;
    private Button activeNavigation = dashboardButton;
    private Label pageMessage;

    @FXML
    private void initialize() {
        SidebarResizer.install(sidebar, sidebarResizeHandle);
        configureHomeTables();
        if (!isAuthorized()) {
            setMetricPlaceholders();
            setUnavailablePlaceholders(
                    "Sign in as a Ward Manager or Senior Nurse to view this dashboard.");
            setStatus("Inpatient staff session is not available",
                    "status-warning");
            setActionsDisabled(true);
            nursingDashboardButton.setVisible(false);
            nursingDashboardButton.setManaged(false);
            return;
        }

        User user = UserSession.getCurrentUser();
        sidebarUserLabel.setText(user.username());
        sidebarRoleLabel.setText(user.roleName());
        topUserLabel.setText(user.username());
        topRoleLabel.setText(user.roleName());
        dateLabel.setText(LocalDate.now().format(DATE_FORMAT));
        boolean seniorNurse = UserSession.hasRole("Senior Nurse");
        nursingDashboardButton.setVisible(seniorNurse);
        nursingDashboardButton.setManaged(seniorNurse);
        setActionsDisabled(!isWardManager());
        loadDashboard();
    }

    private boolean isAuthorized() {
        return UserSession.hasRole("Ward Manager")
                || UserSession.hasRole("Senior Nurse");
    }

    private boolean isWardManager() {
        return UserSession.hasRole("Ward Manager");
    }

    private void setActionsDisabled(boolean disabled) {
        dashboardButton.setDisable(!isAuthorized());
        for (Button button : new Button[] {
                wardManagementButton, bedManagementButton, admissionsButton,
                bedAllocationButton, transfersButton, dischargeButton,
                topNewAdmissionButton,
                topAllocateBedButton
        }) {
            button.setDisable(disabled);
        }
        recordsButton.setDisable(!isAuthorized());
        reportsButton.setDisable(!isAuthorized());
    }

    private void loadDashboard() {
        try {
            Metrics metrics = dashboardDao.loadMetrics();
            metricGrid.getChildren().clear();
            addMetric("Total beds", metrics.totalBeds(), "▤", 0);
            addMetric("Occupied beds", metrics.occupiedBeds(), "♙", 1);
            addMetric("Available beds", metrics.availableBeds(), "✓", 2);
            addMetric("Current admissions", metrics.currentAdmissions(), "⌂", 3);
            addMetric("Patients awaiting discharge",
                    metrics.patientsAwaitingDischarge(), "⇥", 4);
            addMetric("Ward occupancy rate",
                    metrics.occupancyRate(), "%", 5, true);

            loadHomeTables();
            boolean modulesMissing = metrics.totalBeds() == null
                    || metrics.occupiedBeds() == null
                    || metrics.availableBeds() == null
                    || metrics.currentAdmissions() == null
                    || metrics.patientsAwaitingDischarge() == null;
            setStatus(modulesMissing
                            ? "Ward management modules need setup"
                            : "Inpatient workspace connected",
                    modulesMissing ? "status-warning" : "status-connected");
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load inpatient dashboard data", exception);
            setMetricPlaceholders();
            setUnavailablePlaceholders(
                    "Inpatient and bed management data is unavailable.");
            setStatus("Inpatient data is unavailable", "status-warning");
        }
    }

    private void loadHomeTables() {
        bedTable.getItems().clear();
        dischargeTable.getItems().clear();
        try {
            List<BedRow> beds = dashboardDao.loadBeds();
            bedTable.setItems(FXCollections.observableArrayList(beds));
            homeBedCountLabel.setText(beds.size() + " beds");
            bedTable.setPlaceholder(new Label(
                    beds.isEmpty() ? "No ward beds are recorded."
                            : "No bed data to display."));
            occupancyStateLabel.setText(beds.isEmpty()
                    ? "No ward beds are currently recorded."
                    : "Capacity and occupancy are read from the configured "
                            + "ward and bed tables.");
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.INFO, "Bed overview is not available", exception);
            bedTable.setPlaceholder(new Label(
                    "Ward and bed tables are not configured."));
            homeBedCountLabel.setText("Not configured");
            occupancyStateLabel.setText(
                    "Ward and bed tables are not configured.");
        }
        try {
            List<DischargeRow> requests = dashboardDao.loadDischargeRequests();
            List<DischargeRow> openRequests = new java.util.ArrayList<>();
            for (DischargeRow row : requests) {
                if (!"COMPLETED".equalsIgnoreCase(row.requestStatus())) {
                    openRequests.add(row);
                }
            }
            dischargeTable.setItems(
                    FXCollections.observableArrayList(openRequests));
            homeDischargeCountLabel.setText(openRequests.size() + " open");
            dischargeTable.setPlaceholder(new Label(
                    "No discharge requests are awaiting processing."));
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.INFO,
                    "Discharge overview is not available", exception);
            dischargeTable.setPlaceholder(new Label(
                    "Discharge request data is not configured."));
            homeDischargeCountLabel.setText("Not configured");
        }
    }

    private void configureHomeTables() {
        homeBedIdColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().bedNumber()));
        homeBedPatientColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(valueOrDash(
                        cell.getValue().patientName())));
        homeBedStatusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().status()));
        homeBedStayColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(valueOrDash(
                        cell.getValue().admittedAt())));
        homeBedWardColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().ward()));
        homeDischargePatientNumberColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(
                        cell.getValue().patientNumber()));
        homeDischargePatientColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().patientName()));
        homeDischargeBedColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(valueOrDash(
                        cell.getValue().ward()) + " / "
                        + valueOrDash(cell.getValue().bedNumber())));
        homeDischargeAdmittedColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().admittedAt()));
        homeDischargeStatusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().requestStatus()));
        bedTable.setPlaceholder(new Label(
                "Ward and bed tables are not configured."));
        dischargeTable.setPlaceholder(new Label(
                "Discharge request data is not configured."));
    }

    private void setMetricPlaceholders() {
        metricGrid.getChildren().clear();
        addMetric("Total beds", null, "▤", 0);
        addMetric("Occupied beds", null, "♙", 1);
        addMetric("Available beds", null, "✓", 2);
        addMetric("Current admissions", null, "⌂", 3);
        addMetric("Patients awaiting discharge", null, "⇥", 4);
        addMetric("Ward occupancy rate", null, "%", 5, true);
    }

    private void addMetric(String title, Integer value, String icon, int column) {
        addMetric(title, value, icon, column, false);
    }

    private void addMetric(String title, Integer value, String icon, int column,
                           boolean percentage) {
        String displayValue = value == null
                ? "Not configured" : value + (percentage ? "%" : "");
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
                "metric-card", "inpatient-metric-card", CARD_STYLES[column]);
        GridPane.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        metricGrid.add(card, column % 4, column / 4);
    }

    private void setUnavailablePlaceholders(String message) {
        bedTable.setPlaceholder(new Label(message));
        dischargeTable.setPlaceholder(new Label(message));
        occupancyStateLabel.setText(message);
    }

    private void setStatus(String text, String stateClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(
                "status-warning", "status-connected");
        statusLabel.getStyleClass().add(stateClass);
    }

    @FXML
    private void handleDashboard() {
        if (!requireAuthorized()) {
            return;
        }
        contentScroll.setContent(dashboardContent);
        setActiveNavigation(dashboardButton);
        activeRefresh = this::loadDashboard;
        loadDashboard();
    }

    @FXML
    private void handleWardManagement() {
        if (requireManager()) {
            showWardManagementPage();
        }
    }

    @FXML
    private void handleBedManagement() {
        if (requireManager()) {
            showBedManagementPage();
        }
    }

    @FXML
    private void handleAdmissions() {
        if (requireManager()) {
            showAdmissionsPage();
        }
    }

    @FXML
    private void handleBedAllocation() {
        if (requireManager()) {
            showBedAllocationPage();
        }
    }

    @FXML
    private void handleTransfers() {
        if (requireManager()) {
            showTransfersPage();
        }
    }

    @FXML
    private void handleDischarge() {
        if (requireManager()) {
            showDischargePage();
        }
    }

    @FXML
    private void handleRecords() {
        if (requireAuthorized()) {
            showRecordsPage();
        }
    }

    @FXML
    private void handleReports() {
        if (requireAuthorized()) {
            showReportsPage();
        }
    }

    private void showWardManagementPage() {
        VBox page = createPage("Ward Management",
                "Edit ward metadata, archive wards, or remove only unreferenced wards.");
        VBox panel = createPanel("Ward directory",
                "Ward names are unique. Archived wards remain in historical records.");
        TableView<WardOption> table = new TableView<>();
        table.getColumns().add(column("Ward", 220, WardOption::name));
        table.getColumns().add(column("Type", 180,
                row -> valueOrDash(row.type())));
        table.getColumns().add(column("Status", 110,
                row -> row.active() ? "Active" : "Archived"));
        table.setPlaceholder(new Label("No wards are configured."));
        Button edit = button("Edit metadata", "secondary-button");
        Button archive = button("Archive ward", "secondary-button");
        Button delete = button("Delete if unreferenced", "secondary-button");
        Label feedback = feedbackLabel();
        edit.setOnAction(event -> editSelectedWard(table, feedback));
        archive.setOnAction(event -> {
            WardOption selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                feedback.setText("Select a ward first.");
            } else if (selected.active()
                    && confirmAction("Archive ward",
                    "Archive " + selected.name()
                            + "? It will no longer be available for new beds, "
                            + "but existing records will be preserved.")) {
                runAction(feedback, () -> {
                    dashboardDao.archiveWard(selected.id(), currentUserId());
                    refreshWardTable(table, feedback);
                    feedback.setText("Ward archived; linked records preserved.");
                });
            }
        });
        delete.setOnAction(event -> {
            WardOption selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                feedback.setText("Select a ward first.");
            } else if (confirmAction("Permanently delete unreferenced ward",
                    "Delete " + selected.name()
                            + " only if Oracle confirms that no beds or other "
                            + "records reference it? This cannot be undone.")) {
                runAction(feedback, () -> {
                    dashboardDao.deleteUnreferencedWard(
                            selected.id(), currentUserId());
                    refreshWardTable(table, feedback);
                    feedback.setText("Unreferenced ward deleted.");
                });
            }
        });
        VBox formPanel = createPanel("Add ward", "Ward details");
        TextField name = textField("Ward name");
        TextField type = textField("Ward type (optional)");
        Button save = button("Create ward", "primary-button");
        save.setOnAction(event -> runAction(feedback, () -> {
            dashboardDao.createWard(name.getText(), type.getText(),
                    currentUserId());
            name.clear();
            type.clear();
            refreshWardTable(table, feedback);
            feedback.setText("Ward created.");
        }));
        HBox actions = new HBox(8, edit, archive, delete);
        panel.getChildren().addAll(table, actions, feedback);
        formPanel.getChildren().addAll(name, type, save);
        page.getChildren().addAll(panel, formPanel);
        refreshWardTable(table, feedback);
        showPage(page, wardManagementButton, () -> {
            refreshWardTable(table, feedback);
        });
    }

    private void refreshWardTable(TableView<WardOption> table, Label feedback) {
        try {
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadWards()));
            if (table.getItems().isEmpty()) {
                table.setPlaceholder(new Label("No wards are configured."));
            }
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING, "Unable to load wards", exception);
            table.getItems().clear();
            table.setPlaceholder(new Label(
                    "Ward data is unavailable or not configured."));
            feedback.setText(exception.getMessage());
        }
    }

    private void showBedManagementPage() {
        VBox page = createPage("Bed Management",
                "Review bed occupancy, add beds, or update non-occupied beds.");
        VBox panel = createPanel("Ward beds",
                "Occupied beds can only be released by discharge or transfer.");
        TableView<BedRow> table = new TableView<>();
        table.getColumns().add(column("Ward", 140, BedRow::ward));
        table.getColumns().add(column("Bed", 90, BedRow::bedNumber));
        table.getColumns().add(column("Status", 120, BedRow::status));
        table.getColumns().add(column("Patient No.", 120,
                row -> valueOrDash(row.patientNumber())));
        table.getColumns().add(column("Patient", 150,
                row -> valueOrDash(row.patientName())));
        table.getColumns().add(column("Admitted", 150,
                row -> valueOrDash(row.admittedAt())));
        table.setPlaceholder(new Label("No beds are configured."));
        Label feedback = feedbackLabel();
        HBox actions = new HBox(8);
        Button editMetadata = button("Edit bed number", "secondary-button");
        Button delete = button("Delete if unreferenced", "secondary-button");
        Button available = button("Set available", "secondary-button");
        Button cleaning = button("Mark cleaning", "secondary-button");
        Button offline = button("Take out of service", "secondary-button");
        editMetadata.setOnAction(event ->
                editSelectedBedMetadata(table, feedback));
        delete.setOnAction(event -> deleteSelectedBed(table, feedback));
        available.setOnAction(event -> updateSelectedBed(
                table, "AVAILABLE", pageMessage));
        cleaning.setOnAction(event -> updateSelectedBed(
                table, "CLEANING", pageMessage));
        offline.setOnAction(event -> updateSelectedBed(
                table, "OUT_OF_SERVICE", pageMessage));
        actions.getChildren().addAll(editMetadata, delete,
                available, cleaning, offline);
        panel.getChildren().addAll(table, actions, feedback);

        VBox addPanel = createPanel("Add bed", "New beds start as available.");
        ComboBox<WardOption> ward = new ComboBox<>();
        ward.setPromptText("Select ward");
        ward.setMaxWidth(Double.MAX_VALUE);
        TextField bedNumber = textField("Bed number");
        Button add = button("Add bed", "primary-button");
        add.setOnAction(event -> runAction(feedback, () -> {
            WardOption selected = ward.getValue();
            if (selected == null) {
                throw new IllegalArgumentException("Select a ward.");
            }
            dashboardDao.createBed(selected.id(), bedNumber.getText(),
                    currentUserId());
            bedNumber.clear();
            refreshBeds(table, feedback);
            feedback.setText("Bed added.");
        }));
        addPanel.getChildren().addAll(ward, bedNumber, add);
        page.getChildren().addAll(panel, addPanel);
        pageMessage = feedback;
        refreshWards(ward, feedback);
        refreshBeds(table, feedback);
        showPage(page, bedManagementButton, () -> {
            refreshWards(ward, feedback);
            refreshBeds(table, feedback);
        });
    }

    private void updateSelectedBed(TableView<BedRow> table, String status,
                                   Label feedback) {
        runAction(feedback, () -> {
            BedRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException("Select a bed first.");
            }
            dashboardDao.updateBedStatus(selected.bedId(), status,
                    currentUserId());
            refreshBeds(table, feedback);
            feedback.setText("Bed status updated.");
        });
    }

    private void editSelectedWard(TableView<WardOption> table,
                                  Label feedback) {
        WardOption selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select a ward first.");
            return;
        }
        TextField name = textField("Ward name");
        name.setText(selected.name());
        TextField type = textField("Ward type (optional)");
        type.setText(selected.type() == null ? "" : selected.type());
        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(8);
        fields.addRow(0, new Label("Ward ID (read-only)"),
                new Label(Long.toString(selected.id())));
        fields.addRow(1, new Label("Name"), name);
        fields.addRow(2, new Label("Type"), type);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit ward metadata");
        dialog.setHeaderText("Ward status and linked history are unchanged.");
        dialog.getDialogPane().setContent(fields);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            runAction(feedback, () -> {
                dashboardDao.updateWardMetadata(selected.id(), name.getText(),
                        type.getText(), currentUserId());
                refreshWardTable(table, feedback);
                feedback.setText("Ward metadata updated.");
            });
        }
    }

    private void editSelectedBedMetadata(TableView<BedRow> table,
                                         Label feedback) {
        BedRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select a bed first.");
            return;
        }
        TextField bedNumber = textField("Bed number");
        bedNumber.setText(selected.bedNumber());
        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(8);
        fields.addRow(0, new Label("Bed ID (read-only)"),
                new Label(Long.toString(selected.bedId())));
        fields.addRow(1, new Label("Ward (read-only)"),
                new Label(selected.ward()));
        fields.addRow(2, new Label("Bed number"), bedNumber);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit bed metadata");
        dialog.setHeaderText(
                "Only unoccupied, unreferenced beds can be renamed.");
        dialog.getDialogPane().setContent(fields);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            runAction(feedback, () -> {
                dashboardDao.updateBedMetadata(selected.bedId(),
                        bedNumber.getText(), currentUserId());
                refreshBeds(table, feedback);
                feedback.setText("Bed metadata updated.");
            });
        }
    }

    private void deleteSelectedBed(TableView<BedRow> table, Label feedback) {
        BedRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select a bed first.");
        } else if (confirmAction("Permanently delete unreferenced bed",
                "Delete " + selected.ward() + " / " + selected.bedNumber()
                        + " only if Oracle confirms that no admissions, "
                        + "transfers, or other records reference it? "
                        + "This cannot be undone.")) {
            runAction(feedback, () -> {
                dashboardDao.deleteUnreferencedBed(
                        selected.bedId(), currentUserId());
                refreshBeds(table, feedback);
                feedback.setText("Unreferenced bed deleted.");
            });
        }
    }

    private boolean confirmAction(String title, String message) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                message, ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle(title);
        confirmation.setHeaderText(null);
        return confirmation.showAndWait().orElse(ButtonType.CANCEL)
                == ButtonType.OK;
    }

    private void refreshWards(ComboBox<WardOption> selector, Label feedback) {
        try {
            selector.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadActiveWards()));
        } catch (SQLException | IllegalStateException exception) {
            selector.getItems().clear();
            feedback.setText("Ward list unavailable: " + exception.getMessage());
        }
    }

    private void refreshBeds(TableView<BedRow> table, Label feedback) {
        try {
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadBeds()));
        } catch (SQLException | IllegalStateException exception) {
            table.getItems().clear();
            table.setPlaceholder(new Label(
                    "Bed tables are not configured or unavailable."));
            feedback.setText(exception.getMessage());
        }
    }

    private void showAdmissionsPage() {
        VBox page = createPage("Patient Admissions",
                "Admit a registered patient and atomically allocate an available bed.");
        VBox form = createPanel("New admission",
                "Patient must already exist in the central PATIENTS table.");
        TextField patientNumber = textField("Patient number");
        ComboBox<BedOption> bed = new ComboBox<>();
        bed.setPromptText("Select available bed");
        bed.setMaxWidth(Double.MAX_VALUE);
        Label feedback = feedbackLabel();
        Button save = button("Admit and allocate bed", "primary-button");
        save.setOnAction(event -> runAction(feedback, () -> {
            if (bed.getValue() == null) {
                throw new IllegalArgumentException(
                        "Select an available bed.");
            }
            dashboardDao.createAdmission(patientNumber.getText(),
                    bed.getValue().id(), currentUserId());
            patientNumber.clear();
            refreshBedOptions(bed, feedback);
            feedback.setText("Admission saved and bed allocated.");
        }));
        form.getChildren().addAll(patientNumber, bed, save, feedback);
        VBox records = createPanel("Current admissions",
                "Active admitted patients linked to their central patient record.");
        TableView<AdmissionRow> table = admissionTable();
        records.getChildren().add(table);
        page.getChildren().addAll(form, records);
        refreshBedOptions(bed, feedback);
        refreshAdmissions(table, feedback);
        showPage(page, admissionsButton, () -> {
            refreshBedOptions(bed, feedback);
            refreshAdmissions(table, feedback);
        });
    }

    private void showBedAllocationPage() {
        VBox page = createPage("Bed Allocation",
                "Assign or reassign a bed for an active admission.");
        VBox panel = createPanel("Active admissions",
                "Bed state and admission assignment update in one transaction.");
        TableView<AdmissionRow> table = admissionTable();
        ComboBox<BedOption> bed = new ComboBox<>();
        bed.setPromptText("Select available bed");
        bed.setMaxWidth(Double.MAX_VALUE);
        Label feedback = feedbackLabel();
        Button allocate = button("Allocate selected bed", "primary-button");
        allocate.setOnAction(event -> runAction(feedback, () -> {
            AdmissionRow admission = table.getSelectionModel()
                    .getSelectedItem();
            if (admission == null || bed.getValue() == null) {
                throw new IllegalArgumentException(
                        "Select an admission and an available bed.");
            }
            dashboardDao.allocateBed(admission.admissionId(),
                    bed.getValue().id(), currentUserId());
            refreshAdmissions(table, feedback);
            refreshBedOptions(bed, feedback);
            feedback.setText("Bed allocated.");
        }));
        panel.getChildren().addAll(table, bed, allocate, feedback);
        pageMessage = feedback;
        page.getChildren().add(panel);
        refreshAdmissions(table, feedback);
        refreshBedOptions(bed, feedback);
        showPage(page, bedAllocationButton, () -> {
            refreshAdmissions(table, feedback);
            refreshBedOptions(bed, feedback);
        });
    }

    private void showTransfersPage() {
        VBox page = createPage("Patient Transfers",
                "Move an active inpatient to a different available bed.");
        VBox form = createPanel("Complete transfer",
                "Transfer record, destination allocation, and source release commit together.");
        ComboBox<AdmissionRow> admission = new ComboBox<>();
        admission.setPromptText("Select admitted patient");
        admission.setMaxWidth(Double.MAX_VALUE);
        ComboBox<BedOption> bed = new ComboBox<>();
        bed.setPromptText("Select destination bed");
        bed.setMaxWidth(Double.MAX_VALUE);
        TextArea reason = new TextArea();
        reason.setPromptText("Transfer reason (required)");
        reason.setPrefRowCount(3);
        reason.setWrapText(true);
        Label feedback = feedbackLabel();
        VBox historyPanel = createPanel("Transfer history",
                "Most recent completed inpatient transfers.");
        TableView<TransferRow> table = new TableView<>();
        table.getColumns().add(column("Requested", 150, TransferRow::requestedAt));
        table.getColumns().add(column("Patient No.", 110,
                TransferRow::patientNumber));
        table.getColumns().add(column("Patient", 140, TransferRow::patientName));
        table.getColumns().add(column("From", 140,
                row -> valueOrDash(row.fromBed())));
        table.getColumns().add(column("To", 140,
                row -> valueOrDash(row.toBed())));
        table.getColumns().add(column("Reason", 180,
                row -> valueOrDash(row.reason())));
        table.setPlaceholder(new Label("No transfers recorded."));
        historyPanel.getChildren().add(table);
        Button transfer = button("Complete transfer", "primary-button");
        transfer.setOnAction(event -> runAction(feedback, () -> {
            if (admission.getValue() == null || bed.getValue() == null) {
                throw new IllegalArgumentException(
                        "Select an active admission and destination bed.");
            }
            dashboardDao.transferPatient(admission.getValue().admissionId(),
                    bed.getValue().id(), reason.getText(), currentUserId());
            reason.clear();
            refreshAdmissions(admission, feedback);
            refreshBedOptions(bed, feedback);
            refreshTransfers(table, feedback);
            feedback.setText("Patient transfer completed.");
        }));
        form.getChildren().addAll(admission, bed, reason, transfer, feedback);
        page.getChildren().addAll(form, historyPanel);
        refreshAdmissions(admission, feedback);
        refreshBedOptions(bed, feedback);
        refreshTransfers(table, feedback);
        showPage(page, transfersButton, () -> {
            refreshAdmissions(admission, feedback);
            refreshBedOptions(bed, feedback);
            refreshTransfers(table, feedback);
        });
    }

    private void showDischargePage() {
        VBox page = createPage("Discharge Management",
                "Request discharge and release the bed only when processing is completed.");
        VBox requestPanel = createPanel("Request discharge",
                "A patient must have an active inpatient admission.");
        ComboBox<AdmissionRow> admission = new ComboBox<>();
        admission.setPromptText("Select admitted patient");
        admission.setMaxWidth(Double.MAX_VALUE);
        TextArea notes = new TextArea();
        notes.setPromptText("Discharge notes (optional)");
        notes.setPrefRowCount(3);
        notes.setWrapText(true);
        Label feedback = feedbackLabel();
        VBox requestsPanel = createPanel("Open and recent requests",
                "Completing a request marks the admission discharged and bed available.");
        TableView<DischargeRow> requestsTable = new TableView<>();
        requestsTable.getColumns().add(column("Patient No.", 110,
                DischargeRow::patientNumber));
        requestsTable.getColumns().add(column("Patient", 150,
                DischargeRow::patientName));
        requestsTable.getColumns().add(column("Ward / bed", 150,
                row -> valueOrDash(row.ward()) + " / "
                        + valueOrDash(row.bedNumber())));
        requestsTable.getColumns().add(column("Admitted", 145,
                DischargeRow::admittedAt));
        requestsTable.getColumns().add(column("Status", 110,
                DischargeRow::requestStatus));
        requestsTable.getColumns().add(column("Notes", 180,
                row -> valueOrDash(row.notes())));
        requestsTable.setPlaceholder(new Label(
                "No discharge requests have been recorded."));
        Button complete = button("Complete selected discharge",
                "secondary-button");
        complete.setOnAction(event -> runAction(feedback, () -> {
            DischargeRow selected = requestsTable.getSelectionModel()
                    .getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException(
                        "Select a discharge request.");
            }
            if ("COMPLETED".equalsIgnoreCase(selected.requestStatus())) {
                throw new IllegalArgumentException(
                        "This discharge request is already completed.");
            }
            dashboardDao.completeDischarge(
                    selected.dischargeRequestId(), currentUserId());
            refreshDischarges(requestsTable, feedback);
            refreshAdmissions(admission, feedback);
            feedback.setText("Discharge completed; bed released.");
        }));
        Button request = button("Create discharge request", "primary-button");
        request.setOnAction(event -> runAction(feedback, () -> {
            if (admission.getValue() == null) {
                throw new IllegalArgumentException(
                        "Select an active admission.");
            }
            dashboardDao.requestDischarge(admission.getValue().admissionId(),
                    notes.getText(), currentUserId());
            notes.clear();
            refreshAdmissions(admission, feedback);
            refreshDischarges(requestsTable, feedback);
            feedback.setText("Discharge request created.");
        }));
        requestPanel.getChildren().addAll(admission, notes, request, feedback);

        requestsPanel.getChildren().addAll(requestsTable, complete);
        page.getChildren().addAll(requestPanel, requestsPanel);
        refreshAdmissions(admission, feedback);
        refreshDischarges(requestsTable, feedback);
        showPage(page, dischargeButton, () -> {
            refreshAdmissions(admission, feedback);
            refreshDischarges(requestsTable, feedback);
        });
    }

    private void showRecordsPage() {
        VBox page = createPage("Inpatient Records",
                "Current active admissions linked to the central patient register.");
        VBox panel = createPanel("Active inpatient census",
                "Patient, bed, ward, admission date, and discharge request status.");
        TableView<AdmissionRow> table = admissionTable();
        panel.getChildren().add(table);
        page.getChildren().add(panel);
        Label feedback = feedbackLabel();
        page.getChildren().add(feedback);
        refreshAdmissions(table, feedback);
        showPage(page, recordsButton, () -> refreshAdmissions(table, feedback));
    }

    private void showReportsPage() {
        VBox page = createPage("Ward Reports",
                "Live capacity and occupancy data from configured ward and bed records.");
        VBox panel = createPanel("Bed capacity report",
                "Patient identifiers remain linked to PATIENTS.");
        TableView<BedRow> table = new TableView<>();
        table.getColumns().add(column("Ward", 140, BedRow::ward));
        table.getColumns().add(column("Bed", 90, BedRow::bedNumber));
        table.getColumns().add(column("Status", 120, BedRow::status));
        table.getColumns().add(column("Patient No.", 130,
                row -> valueOrDash(row.patientNumber())));
        table.getColumns().add(column("Patient", 160,
                row -> valueOrDash(row.patientName())));
        table.getColumns().add(column("Admitted", 150,
                row -> valueOrDash(row.admittedAt())));
        table.setPlaceholder(new Label("Ward or bed data is not configured."));
        panel.getChildren().add(table);
        Label feedback = feedbackLabel();
        page.getChildren().addAll(panel, feedback);
        refreshBeds(table, feedback);
        showPage(page, reportsButton, () -> refreshBeds(table, feedback));
    }

    private VBox createPage(String title, String subtitle) {
        VBox page = new VBox(14);
        page.getStyleClass().addAll("dashboard-content", "inpatient-content");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("page-subtitle");
        pageMessage = feedbackLabel();
        page.getChildren().addAll(titleLabel, subtitleLabel, pageMessage);
        return page;
    }

    private VBox createPanel(String title, String hint) {
        VBox panel = new VBox(9);
        panel.getStyleClass().addAll("panel", "inpatient-panel");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("panel-hint");
        hintLabel.setWrapText(true);
        panel.getChildren().addAll(titleLabel, hintLabel);
        return panel;
    }

    private TableView<AdmissionRow> admissionTable() {
        TableView<AdmissionRow> table = new TableView<>();
        table.getStyleClass().addAll("dashboard-table", "inpatient-table");
        table.getColumns().add(column("Patient No.", 120,
                AdmissionRow::patientNumber));
        table.getColumns().add(column("Patient", 150,
                AdmissionRow::patientName));
        table.getColumns().add(column("Ward", 110,
                row -> valueOrDash(row.ward())));
        table.getColumns().add(column("Bed", 90,
                row -> valueOrDash(row.bedNumber())));
        table.getColumns().add(column("Admitted", 150,
                AdmissionRow::admittedAt));
        table.getColumns().add(column("Discharge", 120,
                row -> valueOrDash(row.dischargeStatus())));
        table.setPlaceholder(new Label("No active admissions."));
        return table;
    }

    private void refreshAdmissions(TableView<AdmissionRow> table,
                                   Label feedback) {
        try {
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadAdmissions()));
            if (table.getItems().isEmpty()) {
                table.setPlaceholder(new Label("No active admissions."));
            }
        } catch (SQLException | IllegalStateException exception) {
            table.getItems().clear();
            table.setPlaceholder(new Label(
                    "Admissions or patient details are not configured."));
            feedback.setText(exception.getMessage());
        }
    }

    private void refreshAdmissions(ComboBox<AdmissionRow> selector,
                                   Label feedback) {
        try {
            selector.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadAdmissions()));
        } catch (SQLException | IllegalStateException exception) {
            selector.getItems().clear();
            feedback.setText("Admissions unavailable: " + exception.getMessage());
        }
    }

    private void refreshBedOptions(ComboBox<BedOption> selector,
                                   Label feedback) {
        try {
            selector.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadAvailableBeds()));
            if (selector.getItems().isEmpty()) {
                selector.setPromptText("No available beds");
            }
        } catch (SQLException | IllegalStateException exception) {
            selector.getItems().clear();
            selector.setPromptText("Bed data not configured");
            feedback.setText("Bed list unavailable: " + exception.getMessage());
        }
    }

    private void refreshTransfers(TableView<TransferRow> table,
                                  Label feedback) {
        try {
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadTransfers()));
        } catch (SQLException | IllegalStateException exception) {
            table.getItems().clear();
            table.setPlaceholder(new Label(
                    "Transfer records are not configured."));
            feedback.setText(exception.getMessage());
        }
    }

    private void refreshDischarges(TableView<DischargeRow> table,
                                   Label feedback) {
        try {
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadDischargeRequests()));
        } catch (SQLException | IllegalStateException exception) {
            table.getItems().clear();
            table.setPlaceholder(new Label(
                    "Discharge request records are not configured."));
            feedback.setText(exception.getMessage());
        }
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
            setStatus("Inpatient workspace connected", "status-connected");
        } catch (IllegalArgumentException | SecurityException
                 | IllegalStateException exception) {
            feedback.setText(exception.getMessage());
            setStatus("Action needs attention", "status-warning");
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Inpatient action failed", exception);
            feedback.setText("The change could not be saved: "
                    + exception.getMessage());
            setStatus("Inpatient data is unavailable", "status-warning");
        }
    }

    private boolean requireAuthorized() {
        if (!isAuthorized()) {
            setStatus("Inpatient access requires a Ward Manager or Senior Nurse",
                    "status-warning");
            return false;
        }
        return true;
    }

    private boolean requireManager() {
        if (!isWardManager()) {
            setStatus("Ward Manager authorization is required for changes",
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
        if (isAuthorized()) {
            activeRefresh.run();
        }
    }

    @FXML
    private void handleOpenNursing() throws Exception {
        if (!UserSession.hasRole("Senior Nurse")) {
            setStatus("Nursing dashboard access is restricted to Senior Nurses",
                    "status-warning");
            return;
        }
        openDashboard("/ug/sjch/views/nursing-dashboard.fxml",
                "SJCH Nursing Care Dashboard");
    }

    @FXML
    private void handleLogout() throws Exception {
        UserSession.clear();
        openDashboard("/ug/sjch/views/login.fxml",
                "St Jude Central Hospital - SJCH");
    }

    private void openDashboard(String resource, String title) throws Exception {
        Parent root = FXMLLoader.load(MainApp.class.getResource(resource));
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource("/ug/sjch/styles/application.css")
                        .toExternalForm());
        stage.setScene(scene);
        stage.setTitle(title);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
