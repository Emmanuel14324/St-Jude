package ug.sjch.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.print.PrinterJob;
import javafx.stage.Stage;

import ug.sjch.MainApp;
import ug.sjch.dao.LaboratoryDashboardDao;
import ug.sjch.dao.LaboratoryDashboardDao.Metrics;
import ug.sjch.dao.LaboratoryDashboardDao.ReportRow;
import ug.sjch.dao.LaboratoryDashboardDao.RequestFilter;
import ug.sjch.dao.LaboratoryDashboardDao.RequestRow;
import ug.sjch.security.UserSession;

public class LaboratoryDashboardController {
    private static final Logger LOGGER =
            Logger.getLogger(LaboratoryDashboardController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private final LaboratoryDashboardDao dashboardDao =
            new LaboratoryDashboardDao();
    private Runnable activeRefresh = () -> { };

    @FXML private Label sidebarUserLabel;
    @FXML private Label sidebarRoleLabel;
    @FXML private Label topUserLabel;
    @FXML private Label topRoleLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private Label pendingRequestsValue;
    @FXML private Label samplesCollectedValue;
    @FXML private Label testsInProgressValue;
    @FXML private Label completedTestsValue;
    @FXML private Label awaitingVerificationValue;
    @FXML private Label requestCountLabel;
    @FXML private Label requestFeedbackLabel;
    @FXML private VBox sidebar;
    @FXML private Region sidebarResizeHandle;
    @FXML private TextField searchField;
    @FXML private VBox dashboardContent;
    @FXML private ScrollPane contentScroll;
    @FXML private TableView<RequestRow> requestTable;
    @FXML private TableColumn<RequestRow, String> requestIdColumn;
    @FXML private TableColumn<RequestRow, String> patientColumn;
    @FXML private TableColumn<RequestRow, String> testColumn;
    @FXML private TableColumn<RequestRow, String> requestedColumn;
    @FXML private TableColumn<RequestRow, String> statusColumn;
    @FXML private Button dashboardButton;
    @FXML private Button testRequestsButton;
    @FXML private Button sampleCollectionButton;
    @FXML private Button testsInProgressButton;
    @FXML private Button enterResultsButton;
    @FXML private Button verifyResultsButton;
    @FXML private Button historyButton;
    @FXML private Button printResultsButton;
    @FXML private Button reportsButton;
    @FXML private Button pendingRequestsAction;
    @FXML private Button awaitingVerificationAction;
    @FXML private Button refreshButton;
    @FXML private Button topEnterResultsButton;
    @FXML private Button topReportsButton;

    @FXML
    private void initialize() {
        SidebarResizer.install(sidebar, sidebarResizeHandle);
        configureRequestTable(requestTable);
        requestTable.setPlaceholder(new Label(
                "No laboratory requests are available."));
        dateLabel.setText(LocalDate.now().format(DATE_FORMAT));

        if (!isLaboratoryUser()) {
            setStatus("Laboratory Technician session is not available",
                    "status-warning");
            sidebarUserLabel.setText("Not signed in");
            sidebarRoleLabel.setText("Laboratory");
            topUserLabel.setText("Not signed in");
            topRoleLabel.setText("Laboratory");
            setLaboratoryActionsDisabled(true);
            setUnavailableMetrics();
            return;
        }

        String username = UserSession.getCurrentUser().username();
        String roleName = UserSession.getCurrentUser().roleName();
        sidebarUserLabel.setText(username);
        sidebarRoleLabel.setText(roleName);
        topUserLabel.setText(username);
        topRoleLabel.setText(roleName);
        setLaboratoryActionsDisabled(false);
        showDashboard();
    }

    private boolean isLaboratoryUser() {
        return UserSession.hasRole("Lab Technician")
                || UserSession.hasRole("Laboratory Technician");
    }

    private void setLaboratoryActionsDisabled(boolean disabled) {
        for (Button button : navigationButtons()) {
            button.setDisable(disabled);
        }
        pendingRequestsAction.setDisable(disabled);
        awaitingVerificationAction.setDisable(disabled);
        refreshButton.setDisable(disabled);
        topEnterResultsButton.setDisable(disabled);
        topReportsButton.setDisable(disabled);
        searchField.setDisable(disabled);
    }

    private List<Button> navigationButtons() {
        return List.of(dashboardButton, testRequestsButton,
                sampleCollectionButton, testsInProgressButton,
                enterResultsButton, verifyResultsButton, historyButton,
                printResultsButton, reportsButton);
    }

    @FXML
    private void showDashboard() {
        showContent(dashboardContent, dashboardButton,
                this::refreshDashboard);
        refreshDashboard();
    }

    @FXML
    private void showTestRequests() {
        showRequestPage("Test requests",
                "New investigations requested by outpatient clinicians.",
                RequestFilter.REQUESTED, testRequestsButton,
                RowAction.COLLECT_SAMPLE);
    }

    @FXML
    private void showSampleCollection() {
        showRequestPage("Sample collection",
                "Requests with samples collected and ready for processing.",
                RequestFilter.SAMPLE_COLLECTED, sampleCollectionButton,
                RowAction.START_PROCESSING);
    }

    @FXML
    private void showTestsInProgress() {
        showRequestPage("Tests in progress",
                "Enter results when laboratory processing is complete.",
                RequestFilter.IN_PROGRESS, testsInProgressButton,
                RowAction.ENTER_RESULT);
    }

    @FXML
    private void showEnterResults() {
        showRequestPage("Enter results",
                "Select a test in progress to record its laboratory result.",
                RequestFilter.IN_PROGRESS, enterResultsButton,
                RowAction.ENTER_RESULT);
    }

    @FXML
    private void showVerifyResults() {
        showRequestPage("Results awaiting verification",
                "Submitted results are reviewed by the requesting clinician.",
                RequestFilter.AWAITING_VERIFICATION, verifyResultsButton,
                RowAction.VIEW_RESULT);
    }

    @FXML
    private void showHistory() {
        showRequestPage("Laboratory history",
                "Search all laboratory requests and their latest status.",
                RequestFilter.ALL, historyButton, RowAction.NONE);
    }

    @FXML
    private void showPrintResults() {
        showRequestPage("Print verified results",
                "Only results reviewed by the requesting clinician are listed.",
                RequestFilter.REVIEWED, printResultsButton,
                RowAction.PRINT_RESULT);
    }

    @FXML
    private void showReports() {
        VBox page = createPage("Laboratory reports",
                "Request totals grouped by their current workflow status.");
        VBox panel = createPanel("Request status summary",
                "Counts are calculated from the shared laboratory workflow.");
        TableView<ReportRow> table = new TableView<>();
        table.getStyleClass().addAll("dashboard-table", "laboratory-table");
        table.setPlaceholder(new Label("No laboratory requests found."));
        table.getColumns().add(reportColumn("Status", 260,
                ReportRow::status));
        table.getColumns().add(reportColumn("Requests", 140,
                row -> Integer.toString(row.requestCount())));
        Label feedback = createFeedbackLabel();
        panel.getChildren().addAll(table, feedback);
        page.getChildren().add(panel);
        Runnable reload = () -> loadReportRows(table, feedback);
        showContent(page, reportsButton, reload);
        reload.run();
    }

    @FXML
    private void refreshDashboardAction() {
        activeRefresh.run();
        loadMetrics();
    }

    @FXML
    private void searchRequests() {
        showRequestPage("Laboratory search results",
                "Matching requests by patient number, patient name, or test.",
                RequestFilter.ALL, historyButton, RowAction.NONE);
    }

    private void refreshDashboard() {
        boolean metricsLoaded = loadMetrics();
        boolean requestsLoaded = loadRequestRows(
                requestTable, requestCountLabel, requestFeedbackLabel,
                RequestFilter.ALL);
        if (metricsLoaded && requestsLoaded) {
            setStatus("Laboratory data connected", "status-connected");
        } else {
            setStatus("Laboratory data is unavailable", "status-warning");
        }
    }

    private boolean loadMetrics() {
        try {
            Metrics metrics = dashboardDao.loadMetrics(currentUserId());
            pendingRequestsValue.setText(
                    Integer.toString(metrics.pendingRequests()));
            samplesCollectedValue.setText(
                    Integer.toString(metrics.samplesCollected()));
            testsInProgressValue.setText(
                    Integer.toString(metrics.testsInProgress()));
            completedTestsValue.setText(
                    Integer.toString(metrics.completedTests()));
            awaitingVerificationValue.setText(
                    Integer.toString(metrics.awaitingVerification()));
            return true;
        } catch (SQLException | IllegalStateException
                 | SecurityException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load laboratory dashboard metrics", exception);
            setUnavailableMetrics();
            setStatus("Laboratory metrics are unavailable",
                    "status-warning");
            return false;
        }
    }

    private boolean loadRequestRows(TableView<RequestRow> table, Label count,
                                    Label feedback, RequestFilter filter) {
        try {
            List<RequestRow> rows = dashboardDao.loadRequests(
                    filter, searchField.getText(), currentUserId());
            table.setItems(FXCollections.observableArrayList(rows));
            count.setText(rows.size() + (rows.size() == 1
                    ? " request" : " requests"));
            showFeedback(feedback, "", true);
            setStatus("Laboratory requests connected", "status-connected");
            return true;
        } catch (SQLException | IllegalStateException
                 | SecurityException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load laboratory requests", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback,
                    exception.getMessage() == null
                            ? "Laboratory requests are unavailable."
                            : exception.getMessage(),
                    false);
            return false;
        }
    }

    private void loadReportRows(TableView<ReportRow> table, Label feedback) {
        try {
            table.setItems(FXCollections.observableArrayList(
                    dashboardDao.loadReport(currentUserId())));
            showFeedback(feedback, "", true);
            setStatus("Laboratory reports loaded", "status-connected");
        } catch (SQLException | IllegalStateException
                 | SecurityException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load laboratory reports", exception);
            table.setItems(FXCollections.observableArrayList());
            showFeedback(feedback,
                    exception.getMessage() == null
                            ? "Laboratory reports are unavailable."
                            : exception.getMessage(),
                    false);
            setStatus("Laboratory reports are unavailable",
                    "status-warning");
        }
    }

    private void showRequestPage(String title, String subtitle,
                                 RequestFilter filter, Button navigationButton,
                                 RowAction rowAction) {
        VBox page = createPage(title, subtitle);
        VBox panel = createPanel(title, subtitle);
        TableView<RequestRow> table = createRequestTable();
        Label count = createCountLabel();
        Label feedback = createFeedbackLabel();
        Button actionButton = createRowActionButton(rowAction);
        RowAction secondaryAction = switch (rowAction) {
            case COLLECT_SAMPLE -> RowAction.CANCEL_REQUEST;
            case VIEW_RESULT -> RowAction.CORRECT_RESULT;
            default -> RowAction.NONE;
        };
        Button secondaryActionButton =
                createRowActionButton(secondaryAction);
        actionButton.setDisable(true);
        secondaryActionButton.setDisable(true);
        HBox actionBar = new HBox(
                10, actionButton, secondaryActionButton, count);
        actionBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        panel.getChildren().addAll(actionBar, table, feedback);
        VBox.setVgrow(table, Priority.ALWAYS);
        page.getChildren().add(panel);

        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                        actionButton.setDisable(
                                !canPerform(rowAction, selected));
                        secondaryActionButton.setDisable(
                                !canPerform(secondaryAction, selected));
                });
        actionButton.setOnAction(event -> {
            RequestRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                performRowAction(rowAction, selected, feedback);
            }
        });
        secondaryActionButton.setOnAction(event -> {
            RequestRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                performRowAction(secondaryAction, selected, feedback);
            }
        });

        Runnable reload = () -> loadRequestRows(table, count, feedback, filter);
        showContent(page, navigationButton, reload);
        reload.run();
        loadMetrics();
    }

    private TableView<RequestRow> createRequestTable() {
        TableView<RequestRow> table = new TableView<>();
        table.getStyleClass().addAll("dashboard-table", "laboratory-table");
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No laboratory requests found."));
        table.getColumns().add(requestColumn("Request", 90,
                row -> Long.toString(row.requestId())));
        table.getColumns().add(requestColumn("Patient", 190,
                row -> row.patientNumber() + " · " + row.patientName()));
        table.getColumns().add(requestColumn("Test", 180,
                RequestRow::testName));
        table.getColumns().add(requestColumn("Requested", 140,
                RequestRow::requestedAt));
        table.getColumns().add(requestColumn("Status", 120,
                row -> row.status().replace('_', ' ')));
        return table;
    }

    private void configureRequestTable(TableView<RequestRow> table) {
        requestIdColumn.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(
                        Long.toString(cell.getValue().requestId())));
        patientColumn.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(
                        cell.getValue().patientNumber() + " · "
                                + cell.getValue().patientName()));
        testColumn.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(
                        cell.getValue().testName()));
        requestedColumn.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(
                        cell.getValue().requestedAt()));
        statusColumn.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(
                        cell.getValue().status().replace('_', ' ')));
    }

    private TableColumn<RequestRow, String> requestColumn(
            String title, double width, Function<RequestRow, String> value) {
        TableColumn<RequestRow, String> column =
                new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(
                        value.apply(cell.getValue())));
        return column;
    }

    private TableColumn<ReportRow, String> reportColumn(
            String title, double width, Function<ReportRow, String> value) {
        TableColumn<ReportRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(
                        value.apply(cell.getValue())));
        return column;
    }

    private Button createRowActionButton(RowAction action) {
        String text = switch (action) {
            case COLLECT_SAMPLE -> "Record sample collection";
            case START_PROCESSING -> "Start processing";
            case ENTER_RESULT -> "Enter result";
            case CANCEL_REQUEST -> "Cancel request";
            case VIEW_RESULT -> "View result";
            case CORRECT_RESULT -> "Correct result";
            case PRINT_RESULT -> "Print result";
            case NONE -> "";
        };
        Button button = new Button(text);
        button.getStyleClass().add(action == RowAction.COLLECT_SAMPLE
                || action == RowAction.START_PROCESSING
                || action == RowAction.ENTER_RESULT
                ? "laboratory-primary-button"
                : "laboratory-secondary-button");
        return button;
    }

    private boolean canPerform(RowAction action, RequestRow row) {
        if (row == null) {
            return false;
        }
        return switch (action) {
            case COLLECT_SAMPLE -> "REQUESTED".equals(row.status());
            case START_PROCESSING -> "SAMPLE_COLLECTED".equals(row.status());
            case ENTER_RESULT -> "IN_PROGRESS".equals(row.status());
            case CANCEL_REQUEST -> "REQUESTED".equals(row.status());
            case VIEW_RESULT -> row.resultText() != null;
            case CORRECT_RESULT -> "RESULT_ENTERED".equals(row.status())
                    && !row.reviewed() && row.resultText() != null;
            case PRINT_RESULT -> row.reviewed()
                    && row.resultText() != null;
            case NONE -> false;
        };
    }

    private void performRowAction(RowAction action, RequestRow row,
                                  Label feedback) {
        switch (action) {
            case COLLECT_SAMPLE -> performRequestUpdate(row, feedback,
                    () -> dashboardDao.collectSample(
                            row.requestId(), currentUserId()),
                    "Sample collection recorded.");
            case START_PROCESSING -> performRequestUpdate(row, feedback,
                    () -> dashboardDao.startProcessing(
                            row.requestId(), currentUserId()),
                    "Test moved to in progress.");
            case ENTER_RESULT -> showEnterResultDialog(row, feedback);
            case CANCEL_REQUEST -> {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        "Cancel the request for " + row.testName()
                                + " for " + row.patientName()
                                + "? It will remain in laboratory history.",
                        ButtonType.YES, ButtonType.NO);
                confirmation.setHeaderText("Cancel laboratory request");
                if (confirmation.showAndWait().orElse(ButtonType.NO)
                        == ButtonType.YES) {
                    performRequestUpdate(row, feedback,
                            () -> dashboardDao.cancelRequest(
                                    row.requestId(), currentUserId()),
                            "Request cancelled before sample collection.");
                }
            }
            case VIEW_RESULT -> showResult(row);
            case CORRECT_RESULT -> showCorrectResultDialog(row, feedback);
            case PRINT_RESULT -> printResult(row, feedback);
            case NONE -> {
            }
        }
    }

    private void performRequestUpdate(RequestRow row, Label feedback,
                                     SqlAction action, String successMessage) {
        try {
            action.run();
            activeRefresh.run();
            loadMetrics();
            showFeedback(feedback, successMessage, true);
        } catch (SQLException | IllegalArgumentException
                 | IllegalStateException | SecurityException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to update laboratory request "
                            + row.requestId(), exception);
            showFeedback(feedback, exception.getMessage(), false);
            setStatus("Laboratory request was not updated",
                    "status-warning");
        }
    }

    private void showEnterResultDialog(RequestRow row, Label feedback) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Enter laboratory result");
        dialog.setHeaderText(row.testName() + " — "
                + row.patientName() + " (" + row.patientNumber() + ")");
        if (feedback.getScene() != null) {
            dialog.initOwner(feedback.getScene().getWindow());
        }
        TextArea resultInput = new TextArea();
        resultInput.setPromptText("Enter the result and relevant observations");
        resultInput.setWrapText(true);
        resultInput.setPrefRowCount(8);
        VBox content = new VBox(8,
                new Label("Result details"), resultInput);
        content.setPrefWidth(520);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);
        Button saveButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        saveButton.setText("Save result");
        boolean[] saved = new boolean[1];
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                dashboardDao.enterResult(row.requestId(),
                        resultInput.getText(), currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to enter laboratory result for request "
                                + row.requestId(), exception);
                showFeedback(feedback, exception.getMessage(), false);
                setStatus("Laboratory result was not saved",
                        "status-warning");
                event.consume();
            }
        });
        dialog.showAndWait();
        if (saved[0]) {
            activeRefresh.run();
            loadMetrics();
            showFeedback(feedback,
                    "Result saved and sent to the requesting clinician.",
                    true);
        }
    }

    private void showCorrectResultDialog(RequestRow row, Label feedback) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Correct laboratory result");
        dialog.setHeaderText(row.testName() + " — "
                + row.patientName() + " (" + row.patientNumber() + ")");
        if (feedback.getScene() != null) {
            dialog.initOwner(feedback.getScene().getWindow());
        }
        TextArea resultInput = new TextArea(row.resultText());
        resultInput.setWrapText(true);
        resultInput.setPrefRowCount(8);
        VBox content = new VBox(8,
                new Label("Result details (editable until clinician review)"),
                resultInput);
        content.setPrefWidth(520);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);
        Button saveButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        saveButton.setText("Save correction");
        boolean[] saved = new boolean[1];
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                dashboardDao.correctResult(row.requestId(),
                        resultInput.getText(), currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to correct result for request "
                                + row.requestId(), exception);
                showFeedback(feedback, exception.getMessage(), false);
                setStatus("Laboratory result was not corrected",
                        "status-warning");
                event.consume();
            }
        });
        dialog.showAndWait();
        if (saved[0]) {
            activeRefresh.run();
            loadMetrics();
            showFeedback(feedback,
                    "Result corrected and correction recorded in the audit log.",
                    true);
        }
    }

    private void showResult(RequestRow row) {
        TextArea resultText = new TextArea(row.resultText());
        resultText.setEditable(false);
        resultText.setWrapText(true);
        resultText.setPrefRowCount(12);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Laboratory result");
        alert.setHeaderText(row.testName() + " — " + row.patientName());
        alert.getDialogPane().setContent(resultText);
        if (statusLabel.getScene() != null) {
            alert.initOwner(statusLabel.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private void printResult(RequestRow row, Label feedback) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showFeedback(feedback, "No printer is available.", false);
            return;
        }
        Stage owner = (Stage) statusLabel.getScene().getWindow();
        if (!job.showPrintDialog(owner)) {
            job.cancelJob();
            return;
        }
        Label heading = new Label(
                "Laboratory result — " + row.testName());
        heading.getStyleClass().add("page-title");
        Label patient = new Label("Patient: " + row.patientName()
                + " (" + row.patientNumber() + ")");
        Label requested = new Label("Requested: " + row.requestedAt());
        TextArea result = new TextArea(row.resultText());
        result.setEditable(false);
        result.setWrapText(true);
        VBox printContent = new VBox(12, heading, patient, requested, result);
        printContent.setPadding(new Insets(28));
        printContent.setPrefWidth(560);
        if (job.printPage(printContent) && job.endJob()) {
            showFeedback(feedback, "Result sent to the printer.", true);
        } else {
            job.cancelJob();
            showFeedback(feedback, "The result could not be printed.", false);
        }
    }

    private VBox createPage(String title, String subtitle) {
        VBox page = new VBox(14);
        page.getStyleClass().addAll("dashboard-content",
                "laboratory-content");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("page-subtitle");
        page.getChildren().addAll(titleLabel, subtitleLabel);
        return page;
    }

    private VBox createPanel(String title, String subtitle) {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("panel");
        Label panelTitle = new Label(title);
        panelTitle.getStyleClass().add("panel-title");
        Label panelSubtitle = new Label(subtitle);
        panelSubtitle.getStyleClass().add("panel-hint");
        panel.getChildren().addAll(panelTitle, panelSubtitle);
        return panel;
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

    private void showFeedback(Label label, String message, boolean success) {
        label.setText(message == null ? "" : message);
        label.getStyleClass().removeAll(
                "admin-feedback-success", "admin-feedback-error");
        label.getStyleClass().add(success
                ? "admin-feedback-success" : "admin-feedback-error");
    }

    private void showContent(Node content, Button selected,
                             Runnable reload) {
        contentScroll.setContent(content);
        contentScroll.setVvalue(0);
        for (Button button : navigationButtons()) {
            button.getStyleClass().remove("nav-button-active");
        }
        selected.getStyleClass().add("nav-button-active");
        activeRefresh = reload;
    }

    private void setUnavailableMetrics() {
        for (Label value : new Label[] {
                pendingRequestsValue, samplesCollectedValue,
                testsInProgressValue, completedTestsValue,
                awaitingVerificationValue
        }) {
            value.setText("Unavailable");
        }
    }

    private long currentUserId() {
        if (!isLaboratoryUser()) {
            throw new SecurityException(
                    "A laboratory technician session is required.");
        }
        return UserSession.getCurrentUser().id();
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

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private enum RowAction {
        NONE,
        COLLECT_SAMPLE,
        START_PROCESSING,
        ENTER_RESULT,
        CANCEL_REQUEST,
        VIEW_RESULT,
        CORRECT_RESULT,
        PRINT_RESULT
    }
}
