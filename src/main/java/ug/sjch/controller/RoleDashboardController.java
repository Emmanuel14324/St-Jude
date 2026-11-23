package ug.sjch.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import ug.sjch.MainApp;
import ug.sjch.dao.DashboardDao;
import ug.sjch.dao.DashboardDao.CategoryCount;
import ug.sjch.dao.DashboardDao.PatientSummary;
import ug.sjch.dao.DashboardDao.StaffSummary;
import ug.sjch.security.UserSession;

public class RoleDashboardController {
    private static final Logger LOGGER =
            Logger.getLogger(RoleDashboardController.class.getName());

    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private Label topUserLabel;
    @FXML private Label topRoleLabel;
    @FXML private Label pageTitle;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private VBox sidebar;
    @FXML private Region sidebarResizeHandle;
    @FXML private GridPane metricGrid;
    @FXML private PieChart admissionChart;
    @FXML private Label admissionTotal;
    @FXML private VBox admissionLegend;
    @FXML private BarChart<String, Number> staffChart;
    @FXML private VBox patientPanel;
    @FXML private TableView<PatientSummary> patientTable;
    @FXML private TableColumn<PatientSummary, String> patientNumberColumn;
    @FXML private TableColumn<PatientSummary, String> patientNameColumn;
    @FXML private TableColumn<PatientSummary, String> patientDateColumn;
    @FXML private TableView<StaffSummary> staffTable;
    @FXML private TableColumn<StaffSummary, String> staffNameColumn;
    @FXML private TableColumn<StaffSummary, String> staffRoleColumn;
    @FXML private TableColumn<StaffSummary, String> staffStatusColumn;

    private final DashboardDao dashboardDao = new DashboardDao();

    @FXML
    private void initialize() {
        SidebarResizer.install(sidebar, sidebarResizeHandle);
        if (!UserSession.isAuthenticated()) {
            return;
        }
        String role = UserSession.getCurrentUser().roleName();
        userLabel.setText(UserSession.getCurrentUser().username());
        roleLabel.setText(role);
        topUserLabel.setText(UserSession.getCurrentUser().username());
        topRoleLabel.setText(role);
        pageTitle.setText("Dashboard Overview");
        dateLabel.setText(LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
        ));
        configureTables();
        patientPanel.setVisible(canViewPatientNames(role));
        patientPanel.setManaged(canViewPatientNames(role));
        loadMetrics();
    }

    private boolean canViewPatientNames(String role) {
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "administrator", "receptionist", "doctor", "nurse",
                    "laboratory technician", "pharmacist", "accountant",
                    "ward manager" -> true;
            default -> false;
        };
    }

    private void configureTables() {
        patientNumberColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().number()));
        patientNameColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        patientDateColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().registeredAt()));
        staffNameColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        staffRoleColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().jobTitle()));
        staffStatusColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().status()));
        patientTable.setPlaceholder(new Label("No patient registrations yet"));
        staffTable.setPlaceholder(new Label("No staff records available"));
    }

    private void loadMetrics() {
        metricGrid.getChildren().clear();
        try {
            Map<String, Integer> counts = dashboardDao.loadCounts();
            addMetric("Total patients", counts.get("patients"), "♙", 0);
            addMetric("Total staff", counts.get("staff"), "⚕", 1);
            addMetric("Today's appointments",
                    counts.get("appointmentsToday"), "▣", 2);
            addMetric("Current admissions",
                    counts.get("currentAdmissions"), "⌂", 3);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING, "Unable to load dashboard metrics", exception);
            setDashboardUnavailable();
            return;
        }

        boolean partialFailure = false;
        try {
            loadAdmissionChart(dashboardDao.loadAdmissionStatusCounts());
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Unable to load admission chart", exception);
            admissionLegend.getChildren().setAll(
                    new Label("Admission data is unavailable")
            );
            partialFailure = true;
        }
        try {
            loadStaffChart(dashboardDao.loadStaffCountsByJobTitle());
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Unable to load staffing chart", exception);
            partialFailure = true;
        }
        try {
            staffTable.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadRecentStaff()
            ));
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Unable to load recent staff", exception);
            staffTable.setPlaceholder(new Label("Staff data is unavailable"));
            partialFailure = true;
        }
        if (patientPanel.isVisible()) {
            try {
                patientTable.setItems(FXCollections.observableArrayList(
                        dashboardDao.loadRecentPatients()
                ));
            } catch (SQLException exception) {
                LOGGER.log(Level.WARNING, "Unable to load recent patients", exception);
                patientTable.setPlaceholder(
                        new Label("Patient data is unavailable")
                );
                partialFailure = true;
            }
        }
        statusLabel.setText(partialFailure
                ? "Some dashboard data is unavailable"
                : "Database connected");
    }

    private void setDashboardUnavailable() {
        addMetric("Total patients", 0, "♙", 0);
        addMetric("Total staff", 0, "⚕", 1);
        addMetric("Today's appointments", 0, "▣", 2);
        addMetric("Current admissions", 0, "⌂", 3);
        statusLabel.setText("Database unavailable");
        admissionLegend.getChildren().setAll(
                new Label("Connect to the database to view admission data")
        );
        staffTable.setPlaceholder(
                new Label("Connect to the database to view staff")
        );
        patientTable.setPlaceholder(
                new Label("Connect to the database to view patients")
        );
    }

    private void loadAdmissionChart(List<CategoryCount> values) {
        admissionChart.getData().clear();
        admissionLegend.getChildren().clear();
        int total = values.stream().mapToInt(CategoryCount::count).sum();
        admissionTotal.setText(String.valueOf(total));
        if (values.isEmpty()) {
            admissionLegend.getChildren().add(new Label("No admission records yet"));
            return;
        }
        String[] colors = {"#3154C5", "#7183C5", "#9AA1AF", "#4D7BDE"};
        for (int i = 0; i < values.size(); i++) {
            CategoryCount item = values.get(i);
            PieChart.Data slice = new PieChart.Data(item.name(), item.count());
            admissionChart.getData().add(slice);
            Label swatch = new Label("●");
            swatch.setStyle("-fx-text-fill: " + colors[i % colors.length] + ";");
            Label name = new Label(item.name());
            Label count = new Label(String.valueOf(item.count()));
            count.getStyleClass().add("legend-count");
            javafx.scene.layout.HBox row =
                    new javafx.scene.layout.HBox(7, swatch, name, count);
            row.getStyleClass().add("legend-row");
            admissionLegend.getChildren().add(row);
        }
    }

    private void loadStaffChart(List<CategoryCount> values) {
        staffChart.getData().clear();
        if (values.isEmpty()) {
            return;
        }
        javafx.scene.chart.XYChart.Series<String, Number> series =
                new javafx.scene.chart.XYChart.Series<>();
        for (CategoryCount item : values) {
            series.getData().add(new javafx.scene.chart.XYChart.Data<>(
                    item.name(), item.count()
            ));
        }
        staffChart.getData().add(series);
    }

    private void addMetric(String label, int value, String icon, int column) {
        Label title = new Label(label);
        title.getStyleClass().add("metric-label");
        title.setWrapText(true);
        Label number = new Label(String.valueOf(value));
        number.getStyleClass().add("metric-value");
        number.setWrapText(true);
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("metric-icon");
        javafx.scene.layout.VBox card =
                new javafx.scene.layout.VBox(iconLabel, number, title);
        card.getStyleClass().add("metric-card");
        GridPane.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        metricGrid.add(card, column, 0);
    }

    @FXML
    private void handleLogout() throws Exception {
        UserSession.clear();
        Parent root = FXMLLoader.load(
                MainApp.class.getResource("/ug/sjch/views/login.fxml")
        );
        Stage stage = (Stage) pageTitle.getScene().getWindow();
        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource("/ug/sjch/styles/application.css")
                        .toExternalForm()
        );
        stage.setScene(scene);
        stage.setTitle("St Jude Central Hospital - SJCH");
    }
}
