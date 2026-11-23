package ug.sjch.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

import ug.sjch.MainApp;
import ug.sjch.dao.PharmacyDashboardDao;
import ug.sjch.dao.PharmacyDashboardDao.InventoryFilter;
import ug.sjch.dao.PharmacyDashboardDao.InventoryTransactionRow;
import ug.sjch.dao.PharmacyDashboardDao.MedicineBatchRow;
import ug.sjch.dao.PharmacyDashboardDao.MedicineCatalogRow;
import ug.sjch.dao.PharmacyDashboardDao.MedicineOption;
import ug.sjch.dao.PharmacyDashboardDao.CategorySummary;
import ug.sjch.dao.PharmacyDashboardDao.PharmacyMetrics;
import ug.sjch.dao.PharmacyDashboardDao.PrescriptionItemRow;
import ug.sjch.dao.PharmacyDashboardDao.PrescriptionRow;
import ug.sjch.dao.PharmacyDashboardDao.StockReportRow;
import ug.sjch.dao.PharmacyDashboardDao.SupplierRow;
import ug.sjch.security.UserSession;

public class PharmacyDashboardController {
    private static final Logger LOGGER =
            Logger.getLogger(PharmacyDashboardController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private final PharmacyDashboardDao dashboardDao =
            new PharmacyDashboardDao();
    private InventoryFilter inventoryFilter = InventoryFilter.ALL;
    private Runnable activeRefresh = () -> { };

    @FXML private ScrollPane contentScroll;
    @FXML private VBox dashboardHome;
    @FXML private VBox sidebar;
    @FXML private Region sidebarResizeHandle;
    @FXML private Label sidebarUserLabel;
    @FXML private Label sidebarRoleLabel;
    @FXML private Label topUserLabel;
    @FXML private Label topRoleLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private Label categoryCountLabel;
    @FXML private FlowPane categoryCards;
    @FXML private Label totalMedicinesValue;
    @FXML private Label lowStockMedicinesValue;
    @FXML private Label outOfStockMedicinesValue;
    @FXML private Label expiringMedicinesValue;
    @FXML private Label pendingPrescriptionsValue;
    @FXML private Label todaysDispensingsValue;
    @FXML private Label stockAlertLabel;
    @FXML private Label expiryAlertLabel;
    @FXML private Label inventoryCountLabel;
    @FXML private Label dashboardInventoryActionFeedback;
    @FXML private TableView<MedicineBatchRow> medicineTable;
    @FXML private TableColumn<MedicineBatchRow, String> medicineColumn;
    @FXML private TableColumn<MedicineBatchRow, String> categoryColumn;
    @FXML private TableColumn<MedicineBatchRow, String> stockColumn;
    @FXML private TableColumn<MedicineBatchRow, String> batchColumn;
    @FXML private TableColumn<MedicineBatchRow, String> expiryColumn;
    @FXML private TableColumn<MedicineBatchRow, String> stockStatusColumn;
    @FXML private TextField medicineSearchField;
    @FXML private Button allFilterButton;
    @FXML private Button lowStockFilterButton;
    @FXML private Button expiringFilterButton;
    @FXML private Button refreshButton;
    @FXML private Button editDashboardMedicineButton;
    @FXML private Button archiveDashboardMedicineButton;
    @FXML private Button editDashboardBatchButton;
    @FXML private Button dashboardButton;
    @FXML private Button inventoryButton;
    @FXML private Button addMedicineButton;
    @FXML private Button stockManagementButton;
    @FXML private Button suppliersButton;
    @FXML private Button prescriptionsButton;
    @FXML private Button dispenseButton;
    @FXML private Button expiryAlertsButton;
    @FXML private Button stockReportsButton;
    @FXML private Button topAddMedicineButton;

    @FXML
    private void initialize() {
        SidebarResizer.install(sidebar, sidebarResizeHandle);
        medicineTable.setPlaceholder(new Label(
                "No medicine batches found."));
        configureInventoryColumns();
        medicineTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    boolean noSelection = selected == null;
                    editDashboardMedicineButton.setDisable(noSelection);
                    archiveDashboardMedicineButton.setDisable(noSelection);
                    editDashboardBatchButton.setDisable(noSelection);
                });
        dateLabel.setText(LocalDate.now().format(DATE_FORMAT));

        if (!isPharmacyUser()) {
            setStatus("Pharmacy staff session is not available",
                    "status-warning");
            sidebarUserLabel.setText("Not signed in");
            sidebarRoleLabel.setText("Pharmacy");
            topUserLabel.setText("Not signed in");
            topRoleLabel.setText("Pharmacy");
            setUnavailableMetrics();
            setUnavailableAlerts();
            setPharmacyActionsDisabled(true);
            return;
        }

        String username = UserSession.getCurrentUser().username();
        String roleName = UserSession.getCurrentUser().roleName();
        sidebarUserLabel.setText(username);
        sidebarRoleLabel.setText(roleName);
        topUserLabel.setText(username);
        topRoleLabel.setText(roleName);
        setPharmacyActionsDisabled(false);
        showContent(dashboardHome, dashboardButton, this::refreshDashboard);
        refreshDashboard();
    }

    private boolean isPharmacyUser() {
        return UserSession.hasRole("Pharmacist")
                || UserSession.hasRole("Pharmacy Assistant");
    }

    private void configureInventoryColumns() {
        medicineColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().medicineName()));
        categoryColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().category()));
        stockColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().quantityOnHand()));
        batchColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().batchNumber()));
        expiryColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().expiryDate()));
        stockStatusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().status()));
    }

    private void setPharmacyActionsDisabled(boolean disabled) {
        medicineSearchField.setDisable(disabled);
        allFilterButton.setDisable(disabled);
        lowStockFilterButton.setDisable(disabled);
        expiringFilterButton.setDisable(disabled);
        refreshButton.setDisable(disabled);
        boolean noSelection =
                medicineTable.getSelectionModel().getSelectedItem() == null;
        editDashboardMedicineButton.setDisable(disabled || noSelection);
        archiveDashboardMedicineButton.setDisable(disabled || noSelection);
        editDashboardBatchButton.setDisable(disabled || noSelection);
        topAddMedicineButton.setDisable(disabled);
        for (Button button : navigationButtons()) {
            button.setDisable(disabled);
        }
    }

    private List<Button> navigationButtons() {
        return List.of(dashboardButton, inventoryButton, addMedicineButton,
                stockManagementButton, suppliersButton, prescriptionsButton,
                dispenseButton, expiryAlertsButton, stockReportsButton);
    }

    private void refreshDashboard() {
        boolean metricsLoaded = loadMetrics();
        boolean categoriesLoaded = loadCategoryCards();
        boolean inventoryLoaded = loadInventoryRows();
        if (metricsLoaded && categoriesLoaded && inventoryLoaded) {
            setStatus("Live pharmacy data connected", "status-connected");
        } else {
            setStatus("Some pharmacy data is unavailable",
                    "status-warning");
        }
    }

    private boolean loadMetrics() {
        try {
            PharmacyMetrics metrics = dashboardDao.loadMetrics();
            totalMedicinesValue.setText(
                    Integer.toString(metrics.totalMedicines()));
            lowStockMedicinesValue.setText(
                    Integer.toString(metrics.lowStockMedicines()));
            outOfStockMedicinesValue.setText(
                    Integer.toString(metrics.outOfStockMedicines()));
            expiringMedicinesValue.setText(
                    Integer.toString(metrics.expiringMedicines()));
            pendingPrescriptionsValue.setText(
                    Integer.toString(metrics.pendingPrescriptions()));
            todaysDispensingsValue.setText(
                    Integer.toString(metrics.todaysDispensings()));
            stockAlertLabel.setText(String.format(
                    "%d medicines are low on stock and %d are out of stock.",
                    metrics.lowStockMedicines(),
                    metrics.outOfStockMedicines()));
            expiryAlertLabel.setText(metrics.expiringMedicines() == 0
                    ? "No medicines with stock expire in the next 30 days."
                    : metrics.expiringMedicines()
                            + " medicines with stock are expired or expire "
                            + "within 30 days.");
            return true;
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load pharmacy dashboard metrics", exception);
            setUnavailableMetrics();
            setUnavailableAlerts();
            return false;
        }
    }

    private boolean loadCategoryCards() {
        try {
            List<CategorySummary> categories =
                    dashboardDao.loadCategorySummary();
            categoryCards.getChildren().clear();
            categoryCountLabel.setText(categories.size()
                    + (categories.size() == 1
                            ? " category" : " categories"));
            if (categories.isEmpty()) {
                categoryCards.getChildren().add(createUnavailableLabel(
                        "No active medicine categories are recorded."));
                return true;
            }
            for (CategorySummary category : categories) {
                categoryCards.getChildren().add(
                        createCategoryCard(category));
            }
            return true;
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load pharmacy category summaries", exception);
            categoryCards.getChildren().setAll(createUnavailableLabel(
                    "Category summaries are unavailable."));
            categoryCountLabel.setText("Unavailable");
            return false;
        }
    }

    private VBox createCategoryCard(CategorySummary category) {
        VBox card = new VBox(6);
        card.getStyleClass().add("pharmacy-category-card");
        Label name = new Label(category.category());
        name.getStyleClass().add("pharmacy-category-name");
        Label count = new Label(Integer.toString(category.medicineCount()));
        count.getStyleClass().add("pharmacy-category-count");
        Label products = new Label("active products");
        products.getStyleClass().add("pharmacy-category-detail");
        Label stock = new Label(category.totalStock() + " units available");
        stock.getStyleClass().add("pharmacy-category-detail");
        Label lowStock = new Label(
                category.lowStockCount() + " low-stock products");
        lowStock.getStyleClass().add("pharmacy-category-detail");
        card.getChildren().addAll(name, count, products, stock, lowStock);
        return card;
    }

    private boolean loadInventoryRows() {
        try {
            List<MedicineBatchRow> rows = dashboardDao.loadInventory(
                    medicineSearchField.getText(), inventoryFilter);
            medicineTable.setItems(
                    FXCollections.observableArrayList(rows));
            inventoryCountLabel.setText(rows.size() == 100
                    ? "Showing first 100 batches"
                    : rows.size() + (rows.size() == 1
                            ? " batch" : " batches"));
            return true;
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load pharmacy medicine inventory", exception);
            medicineTable.setItems(FXCollections.observableArrayList());
            inventoryCountLabel.setText("Unavailable");
            return false;
        }
    }

    @FXML
    private void searchInventory() {
        showInventoryPage(inventoryFilter, inventoryPageTitle(),
                inventoryFilter == InventoryFilter.EXPIRING
                        ? expiryAlertsButton : inventoryButton);
    }

    @FXML
    private void showAllInventory() {
        showInventoryPage(InventoryFilter.ALL, "Medicine inventory",
                inventoryButton);
    }

    @FXML
    private void showLowStockInventory() {
        showInventoryPage(InventoryFilter.LOW_STOCK, "Low-stock medicines",
                inventoryButton);
    }

    @FXML
    private void showExpiringInventory() {
        showInventoryPage(InventoryFilter.EXPIRING, "Expiry alerts",
                expiryAlertsButton);
    }

    @FXML
    private void refreshDashboardAction() {
        activeRefresh.run();
    }

    @FXML
    private void editSelectedDashboardMedicine() {
        MedicineBatchRow selected =
                medicineTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            MedicineCatalogRow medicine = selectedMedicine(selected);
            showEditMedicineDialog(medicine, this::refreshDashboard,
                    dashboardInventoryActionFeedback);
        } catch (SQLException | IllegalArgumentException
                 | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load selected pharmacy medicine", exception);
            showFeedback(dashboardInventoryActionFeedback,
                    "Selected medicine details are unavailable.", false);
        }
    }

    @FXML
    private void archiveSelectedDashboardMedicine() {
        MedicineBatchRow selected =
                medicineTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            archiveMedicine(selectedMedicine(selected),
                    this::refreshDashboard,
                    dashboardInventoryActionFeedback);
        } catch (SQLException | IllegalArgumentException
                 | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load selected pharmacy medicine", exception);
            showFeedback(dashboardInventoryActionFeedback,
                    "Selected medicine details are unavailable.", false);
        }
    }

    @FXML
    private void editSelectedDashboardBatch() {
        MedicineBatchRow selected =
                medicineTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showEditBatchDialog(selected, this::refreshDashboard,
                    dashboardInventoryActionFeedback);
        }
    }

    private MedicineCatalogRow selectedMedicine(MedicineBatchRow batch)
            throws SQLException {
        return dashboardDao.loadMedicineCatalog().stream()
                .filter(row -> row.medicineId() == batch.medicineId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Medicine record was not found."));
    }

    @FXML
    private void showDashboard() {
        inventoryFilter = InventoryFilter.ALL;
        updateFilterButtons(inventoryFilter);
        showContent(dashboardHome, dashboardButton, this::refreshDashboard);
        refreshDashboard();
    }

    @FXML
    private void showMedicineInventory() {
        showInventoryPage(InventoryFilter.ALL, "Medicine inventory",
                inventoryButton);
    }

    @FXML
    private void showExpiryAlerts() {
        showInventoryPage(InventoryFilter.EXPIRING, "Expiry alerts",
                expiryAlertsButton);
    }

    @FXML
    private void showAddMedicine() {
        setActiveNavigation(addMedicineButton);
        showAddMedicineDialog();
    }

    @FXML
    private void showStockManagement() {
        VBox page = createPage("Stock management",
                "Receive batches and record auditable stock adjustments.");
        VBox inventoryPanel = createPanel("Current batch balances",
                "Select a batch to adjust its quantity. Negative adjustments "
                        + "cannot reduce stock below zero.");
        TableView<MedicineBatchRow> table =
                createInventoryTable("No medicine batches found.");
        Label count = createCountLabel();
        Label feedback = createFeedbackLabel();
        Button receiveButton = createPrimaryButton("Receive stock");
        Button adjustButton = createSecondaryButton("Adjust selected batch");
        adjustButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) ->
                        adjustButton.setDisable(selected == null));
        VBox historyPanel = createPanel("Recent stock transactions",
                "Receipts, adjustments, returns, and dispensing history.");
        TableView<InventoryTransactionRow> history =
                createTable("No inventory transactions recorded.");
        history.getColumns().add(column("Date", 145,
                InventoryTransactionRow::recordedAt));
        history.getColumns().add(column("Medicine", 160,
                InventoryTransactionRow::medicineName));
        history.getColumns().add(column("Batch", 100,
                InventoryTransactionRow::batchNumber));
        history.getColumns().add(column("Type", 110,
                InventoryTransactionRow::transactionType));
        history.getColumns().add(column("Change", 90,
                InventoryTransactionRow::quantity));
        history.getColumns().add(column("Recorded by", 130,
                InventoryTransactionRow::recordedBy));
        history.getColumns().add(column("Notes", 190,
                InventoryTransactionRow::notes));
        Label historyFeedback = createFeedbackLabel();
        Runnable reload = () -> {
            loadInventoryTable(table, count, "", InventoryFilter.ALL, feedback);
            try {
                history.setItems(FXCollections.observableArrayList(
                        dashboardDao.loadInventoryTransactions()));
                showFeedback(historyFeedback, "", true);
            } catch (SQLException | IllegalStateException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to load pharmacy stock transactions",
                        exception);
                history.setItems(FXCollections.observableArrayList());
                showFeedback(historyFeedback,
                        "Stock transaction history is unavailable.", false);
            }
        };
        receiveButton.setOnAction(event ->
                showReceiveStockDialog(reload, feedback));
        adjustButton.setOnAction(event -> {
            MedicineBatchRow selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showAdjustStockDialog(selected, reload, feedback);
            }
        });
        inventoryPanel.getChildren().addAll(
                new HBox(10, receiveButton, adjustButton, count),
                table, feedback);
        historyPanel.getChildren().addAll(history, historyFeedback);
        page.getChildren().addAll(inventoryPanel, historyPanel);
        showContent(page, stockManagementButton, reload);
        reload.run();
    }

    @FXML
    private void showSuppliers() {
        VBox page = createPage("Suppliers",
                "View medicine suppliers and register supplier contacts.");
        VBox panel = createPanel("Supplier directory",
                "Search active and inactive supplier records.");
        TextField search = createField("Supplier name, contact, phone, email");
        Button searchButton = createSecondaryButton("Search");
        Button addButton = createPrimaryButton("Add supplier");
        Button editButton = createSecondaryButton("Edit selected supplier");
        Button archiveButton = createSecondaryButton("Archive selected");
        Label count = createCountLabel();
        Label feedback = createFeedbackLabel();
        TableView<SupplierRow> table =
                createTable("No supplier records found.");
        editButton.setDisable(true);
        archiveButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    editButton.setDisable(selected == null);
                    archiveButton.setDisable(selected == null
                            || !"Active".equals(selected.status()));
                });
        table.getColumns().add(column("Supplier ID", 95,
                row -> Long.toString(row.supplierId())));
        table.getColumns().add(column("Supplier", 170, SupplierRow::name));
        table.getColumns().add(column("Contact", 140,
                row -> displayValue(row.contactName())));
        table.getColumns().add(column("Phone", 120,
                row -> displayValue(row.phone())));
        table.getColumns().add(column("Email", 180,
                row -> displayValue(row.email())));
        table.getColumns().add(column("Address", 200,
                row -> displayValue(row.address())));
        table.getColumns().add(column("Status", 90, SupplierRow::status));
        Runnable reload = () -> loadSupplierRows(
                search.getText(), table, count, feedback);
        searchButton.setOnAction(event -> reload.run());
        search.setOnAction(event -> reload.run());
        addButton.setOnAction(event ->
                showCreateSupplierDialog(reload, feedback));
        editButton.setOnAction(event -> {
            SupplierRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditSupplierDialog(selected, reload, feedback);
            }
        });
        archiveButton.setOnAction(event -> {
            SupplierRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                archiveSupplier(selected, reload, feedback);
            }
        });
        panel.getChildren().addAll(
                new HBox(10, search, searchButton, addButton, editButton,
                        archiveButton, count),
                table, feedback);
        HBox.setHgrow(search, Priority.ALWAYS);
        page.getChildren().add(panel);
        showContent(page, suppliersButton, reload);
        reload.run();
    }

    @FXML
    private void showPrescriptions() {
        showPrescriptionPage(false);
    }

    @FXML
    private void showDispensePage() {
        showPrescriptionPage(true);
    }

    @FXML
    private void showStockReports() {
        VBox page = createPage("Stock reports",
                "Review active medicine stock against reorder levels.");
        VBox panel = createPanel("Medicine stock summary",
                "Expired batches are excluded from available stock.");
        Label count = createCountLabel();
        Label feedback = createFeedbackLabel();
        TableView<StockReportRow> table =
                createTable("No active medicine records found.");
        table.getColumns().add(column("Medicine", 240,
                StockReportRow::medicineName));
        table.getColumns().add(column("Available stock", 150,
                StockReportRow::stockOnHand));
        table.getColumns().add(column("Reorder level", 140,
                StockReportRow::reorderLevel));
        table.getColumns().add(column("Status", 130,
                StockReportRow::status));
        Runnable reload = () -> {
            try {
                List<StockReportRow> rows = dashboardDao.loadStockReport();
                table.setItems(FXCollections.observableArrayList(rows));
                count.setText(rows.size() + " medicines");
                showFeedback(feedback, "", true);
            } catch (SQLException | IllegalStateException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to load pharmacy stock report", exception);
                table.setItems(FXCollections.observableArrayList());
                count.setText("Unavailable");
                showFeedback(feedback,
                        "Stock report data is unavailable.", false);
            }
        };
        panel.getChildren().addAll(table, count, feedback);
        page.getChildren().add(panel);
        showContent(page, stockReportsButton, reload);
        reload.run();
    }

    private void showPrescriptionPage(boolean dispensingPage) {
        VBox page = createPage(
                dispensingPage ? "Dispense medicines" : "Prescriptions",
                "Review waiting prescriptions. Enter only the quantities "
                        + "actually dispensed.");
        VBox panel = createPanel("Pending prescriptions",
                "Partial dispensing is supported; each quantity is checked "
                        + "against the remaining prescription and unexpired stock.");
        Label count = createCountLabel();
        Label feedback = createFeedbackLabel();
        TableView<PrescriptionRow> table =
                createTable("No pending prescriptions found.");
        table.getColumns().add(column("Prescription", 100,
                row -> Long.toString(row.prescriptionId())));
        table.getColumns().add(column("Patient No.", 120,
                PrescriptionRow::patientNumber));
        table.getColumns().add(column("Patient", 175,
                PrescriptionRow::patientName));
        table.getColumns().add(column("Prescribed by", 165,
                PrescriptionRow::prescribedBy));
        table.getColumns().add(column("Created", 150,
                PrescriptionRow::createdAt));
        table.getColumns().add(column("Status", 150,
                PrescriptionRow::status));
        Button dispenseSelected = createPrimaryButton(
                "Dispense selected prescription");
        dispenseSelected.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) ->
                        dispenseSelected.setDisable(selected == null));
        Runnable reload = () -> {
            try {
                List<PrescriptionRow> rows =
                        dashboardDao.loadPendingPrescriptions();
                table.setItems(FXCollections.observableArrayList(rows));
                count.setText(rows.size() + (rows.size() == 1
                        ? " prescription" : " prescriptions"));
                showFeedback(feedback, "", true);
            } catch (SQLException | IllegalStateException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to load pharmacy prescriptions", exception);
                table.setItems(FXCollections.observableArrayList());
                count.setText("Unavailable");
                showFeedback(feedback,
                        "Prescription data is unavailable.", false);
            }
        };
        dispenseSelected.setOnAction(event -> {
            PrescriptionRow selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showDispenseDialog(selected, reload, feedback);
            }
        });
        panel.getChildren().addAll(table,
                new HBox(10, dispenseSelected, count), feedback);
        page.getChildren().add(panel);
        showContent(page,
                dispensingPage ? dispenseButton : prescriptionsButton, reload);
        reload.run();
    }

    private void showInventoryPage(InventoryFilter filter, String title,
                                   Button navigationButton) {
        inventoryFilter = filter;
        updateFilterButtons(filter);
        VBox page = createPage(title,
                "Manage catalog details and batch metadata. Stock quantities "
                        + "remain controlled by receipt and adjustment records.");
        VBox catalogPanel = createPanel("Medicine catalog",
                "Edit product details or archive medicines without removing "
                        + "historical records.");
        TableView<MedicineCatalogRow> catalog =
                createTable("No medicine catalog records found.");
        catalog.getColumns().add(column("Medicine ID", 95,
                row -> Long.toString(row.medicineId())));
        catalog.getColumns().add(column("Medicine", 180,
                MedicineCatalogRow::name));
        catalog.getColumns().add(column("Generic name", 150,
                row -> displayValue(row.genericName())));
        catalog.getColumns().add(column("Strength / form", 150,
                row -> displayValue(row.strength()) + " / "
                        + displayValue(row.dosageForm())));
        catalog.getColumns().add(column("Category", 120,
                row -> displayValue(row.category())));
        catalog.getColumns().add(column("Reorder level", 110,
                row -> decimalText(row.reorderLevel())));
        catalog.getColumns().add(column("Status", 90,
                MedicineCatalogRow::status));
        Button editMedicine = createSecondaryButton("Edit selected medicine");
        Button archiveMedicine = createSecondaryButton("Archive selected");
        editMedicine.setDisable(true);
        archiveMedicine.setDisable(true);
        Label catalogCount = createCountLabel();
        Label catalogFeedback = createFeedbackLabel();
        catalog.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> {
                    editMedicine.setDisable(selected == null);
                    archiveMedicine.setDisable(selected == null
                            || !"Active".equals(selected.status()));
                });
        editMedicine.setOnAction(event -> {
            MedicineCatalogRow selected =
                    catalog.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditMedicineDialog(selected, () ->
                        loadMedicineCatalog(catalog, catalogCount,
                                catalogFeedback), catalogFeedback);
            }
        });
        archiveMedicine.setOnAction(event -> {
            MedicineCatalogRow selected =
                    catalog.getSelectionModel().getSelectedItem();
            if (selected != null) {
                archiveMedicine(selected, () -> {
                    loadMedicineCatalog(catalog, catalogCount,
                            catalogFeedback);
                    refreshDashboard();
                }, catalogFeedback);
            }
        });
        catalogPanel.getChildren().addAll(
                new HBox(10, editMedicine, archiveMedicine, catalogCount),
                catalog, catalogFeedback);

        VBox panel = createPanel("Medicine batches",
                "Available stock is calculated from received quantities, "
                        + "dispensing records, adjustments, and expiry. "
                        + "Only batch metadata is editable here.");
        TextField search = createField("Search medicine or batch");
        search.setText(medicineSearchField.getText());
        Button searchButton = createSecondaryButton("Search");
        Button editBatch = createSecondaryButton("Edit batch metadata");
        Label count = createCountLabel();
        Label feedback = createFeedbackLabel();
        TableView<MedicineBatchRow> table =
                createInventoryTable("No matching medicine batches found.");
        editBatch.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) ->
                        editBatch.setDisable(selected == null));
        Runnable reload = () -> loadInventoryTable(
                table, count, search.getText(), filter, feedback);
        search.setOnAction(event -> reload.run());
        searchButton.setOnAction(event -> reload.run());
        editBatch.setOnAction(event -> {
            MedicineBatchRow selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditBatchDialog(selected, reload, feedback);
            }
        });
        panel.getChildren().addAll(
                new HBox(10, search, searchButton, editBatch, count),
                table, feedback);
        HBox.setHgrow(search, Priority.ALWAYS);
        page.getChildren().addAll(catalogPanel, panel);
        Runnable refresh = () -> {
            loadMedicineCatalog(catalog, catalogCount, catalogFeedback);
            reload.run();
        };
        showContent(page, navigationButton, refresh);
        refresh.run();
    }

    private void loadMedicineCatalog(TableView<MedicineCatalogRow> table,
                                     Label count, Label feedback) {
        try {
            List<MedicineCatalogRow> rows =
                    dashboardDao.loadMedicineCatalog();
            table.setItems(FXCollections.observableArrayList(rows));
            count.setText(rows.size() + " medicines");
            showFeedback(feedback, "", true);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load pharmacy medicine catalog", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback, "Medicine catalog is unavailable.", false);
        }
    }

    private boolean loadInventoryTable(TableView<MedicineBatchRow> table,
                                       Label count, String query,
                                       InventoryFilter filter,
                                       Label feedback) {
        try {
            List<MedicineBatchRow> rows =
                    dashboardDao.loadInventory(query, filter);
            table.setItems(FXCollections.observableArrayList(rows));
            count.setText(rows.size() == 100
                    ? "Showing first 100 batches"
                    : rows.size() + (rows.size() == 1
                            ? " batch" : " batches"));
            showFeedback(feedback, "", true);
            return true;
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load pharmacy inventory view", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback,
                    "Inventory is unavailable. Check the database "
                            + "connection and schema.", false);
            setStatus("Medicine inventory is unavailable", "status-warning");
            return false;
        }
    }

    private void loadSupplierRows(String query, TableView<SupplierRow> table,
                                  Label count, Label feedback) {
        try {
            List<SupplierRow> rows = dashboardDao.loadSuppliers(query);
            table.setItems(FXCollections.observableArrayList(rows));
            count.setText(rows.size() + (rows.size() == 1
                    ? " supplier" : " suppliers"));
            showFeedback(feedback, "", true);
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load pharmacy suppliers", exception);
            table.setItems(FXCollections.observableArrayList());
            count.setText("Unavailable");
            showFeedback(feedback, "Supplier data is unavailable.", false);
        }
    }

    private TableView<MedicineBatchRow> createInventoryTable(
            String placeholder) {
        TableView<MedicineBatchRow> table = createTable(placeholder);
        table.getColumns().add(column("Medicine", 180,
                MedicineBatchRow::medicineName));
        table.getColumns().add(column("Category", 120,
                row -> displayValue(row.category())));
        table.getColumns().add(column("Stock", 90,
                MedicineBatchRow::quantityOnHand));
        table.getColumns().add(column("Batch", 100,
                MedicineBatchRow::batchNumber));
        table.getColumns().add(column("Expiry", 110,
                MedicineBatchRow::expiryDate));
        table.getColumns().add(column("Status", 110,
                MedicineBatchRow::status));
        return table;
    }

    private <T> TableView<T> createTable(String placeholder) {
        TableView<T> table = new TableView<>();
        table.getStyleClass().addAll("dashboard-table", "pharmacy-table");
        table.setPrefHeight(360);
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

    private VBox createPage(String title, String subtitle) {
        VBox page = new VBox(14);
        page.getStyleClass().addAll("dashboard-content", "pharmacy-content");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("page-subtitle");
        page.getChildren().addAll(titleLabel, subtitleLabel);
        return page;
    }

    private VBox createPanel(String title, String description) {
        VBox panel = new VBox(10);
        panel.getStyleClass().addAll("panel", "pharmacy-inventory-panel");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        Label hint = new Label(description);
        hint.getStyleClass().add("panel-hint");
        hint.setWrapText(true);
        panel.getChildren().addAll(titleLabel, hint);
        return panel;
    }

    private GridPane createFormGrid() {
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        return form;
    }

    private void addFormField(GridPane form, int row, String name, Node input) {
        Label label = new Label(name);
        label.getStyleClass().add("field-label");
        form.add(label, 0, row);
        form.add(input, 1, row);
        GridPane.setHgrow(input, Priority.ALWAYS);
        if (input instanceof TextField field) {
            field.setMaxWidth(Double.MAX_VALUE);
        } else if (input instanceof ComboBox<?> comboBox) {
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

    private void showContent(Node content, Button navigationButton,
                             Runnable reload) {
        contentScroll.setContent(content);
        contentScroll.setVvalue(0);
        setActiveNavigation(navigationButton);
        activeRefresh = reload;
    }

    private void setActiveNavigation(Button selected) {
        for (Button button : navigationButtons()) {
            button.getStyleClass().remove("nav-button-active");
        }
        if (selected != null) {
            selected.getStyleClass().add("nav-button-active");
        }
    }

    private void updateFilterButtons(InventoryFilter filter) {
        Button selected = switch (filter) {
            case ALL -> allFilterButton;
            case LOW_STOCK -> lowStockFilterButton;
            case EXPIRING -> expiringFilterButton;
        };
        for (Button button : new Button[] {
                allFilterButton, lowStockFilterButton, expiringFilterButton
        }) {
            button.getStyleClass().remove("pharmacy-filter-selected");
            if (!button.getStyleClass().contains("pharmacy-filter-button")) {
                button.getStyleClass().add("pharmacy-filter-button");
            }
        }
        selected.getStyleClass().remove("pharmacy-filter-button");
        if (!selected.getStyleClass().contains("pharmacy-filter-selected")) {
            selected.getStyleClass().add("pharmacy-filter-selected");
        }
    }

    private String inventoryPageTitle() {
        return switch (inventoryFilter) {
            case ALL -> "Medicine inventory";
            case LOW_STOCK -> "Low-stock medicines";
            case EXPIRING -> "Expiry alerts";
        };
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String displayValueForInput(String value) {
        return value == null ? "" : value;
    }

    private String decimalText(BigDecimal value) {
        return value == null ? "0"
                : value.stripTrailingZeros().toPlainString();
    }

    private BigDecimal parseDecimal(String text, String field) {
        try {
            return new BigDecimal(text == null ? "" : text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be a valid number.");
        }
    }

    private String dispenseLabel(PrescriptionItemRow item) {
        StringBuilder label = new StringBuilder(item.medicineName());
        if (item.dosage() != null && !item.dosage().isBlank()) {
            label.append(" — ").append(item.dosage());
        }
        if (item.route() != null && !item.route().isBlank()) {
            label.append(", ").append(item.route());
        }
        if (item.frequency() != null && !item.frequency().isBlank()) {
            label.append(", ").append(item.frequency());
        }
        label.append(" | Remaining: ")
                .append(decimalText(item.remainingQuantity()))
                .append(" | Unexpired stock: ")
                .append(decimalText(item.availableQuantity()));
        if (item.instructions() != null && !item.instructions().isBlank()) {
            label.append(" | ").append(item.instructions());
        }
        return label.toString();
    }

    private long currentUserId() {
        return UserSession.getCurrentUser().id();
    }

    private void showAddMedicineDialog() {
        Dialog<ButtonType> dialog = createDialog(
                "Add medicine", "Add a medicine to the active inventory catalog.");
        GridPane form = createFormGrid();
        TextField name = createField("Medicine name");
        TextField genericName = createField("Generic name");
        TextField strength = createField("Strength");
        TextField dosageForm = createField("Dosage form");
        TextField category = createField("Category");
        TextField reorderLevel = createField("0");
        reorderLevel.setText("0");
        Label feedback = createFeedbackLabel();
        addFormField(form, 0, "Medicine name", name);
        addFormField(form, 1, "Generic name", genericName);
        addFormField(form, 2, "Strength", strength);
        addFormField(form, 3, "Dosage form", dosageForm);
        addFormField(form, 4, "Category", category);
        addFormField(form, 5, "Reorder level", reorderLevel);
        form.add(feedback, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(form);
        boolean[] saved = new boolean[1];
        Button confirm = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                dashboardDao.createMedicine(name.getText(),
                        genericName.getText(), strength.getText(),
                        dosageForm.getText(), category.getText(),
                        parseDecimal(reorderLevel.getText(), "Reorder level"),
                        currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to create pharmacy medicine", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && saved[0]) {
            setStatus("Medicine added. Receive a batch to add stock.",
                    "status-connected");
            refreshDashboard();
            activeRefresh.run();
        }
    }

    private void showEditMedicineDialog(MedicineCatalogRow medicine,
                                       Runnable reload,
                                       Label pageFeedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Edit medicine", "Update editable medicine catalog details.");
        GridPane form = createFormGrid();
        Label id = new Label(Long.toString(medicine.medicineId()));
        TextField name = createField("Medicine name");
        name.setText(medicine.name());
        TextField genericName = createField("Generic name");
        genericName.setText(displayValueForInput(medicine.genericName()));
        TextField strength = createField("Strength");
        strength.setText(displayValueForInput(medicine.strength()));
        TextField dosageForm = createField("Dosage form");
        dosageForm.setText(displayValueForInput(medicine.dosageForm()));
        TextField category = createField("Category");
        category.setText(displayValueForInput(medicine.category()));
        TextField reorderLevel = createField("Reorder level");
        reorderLevel.setText(decimalText(medicine.reorderLevel()));
        Label feedback = createFeedbackLabel();
        addFormField(form, 0, "Medicine ID (read-only)", id);
        addFormField(form, 1, "Medicine name", name);
        addFormField(form, 2, "Generic name", genericName);
        addFormField(form, 3, "Strength", strength);
        addFormField(form, 4, "Dosage form", dosageForm);
        addFormField(form, 5, "Category", category);
        addFormField(form, 6, "Reorder level", reorderLevel);
        form.add(feedback, 0, 7, 2, 1);
        dialog.getDialogPane().setContent(form);
        boolean[] saved = new boolean[1];
        Button confirm = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirm.setText("Save changes");
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                dashboardDao.updateMedicine(medicine.medicineId(),
                        name.getText(), genericName.getText(),
                        strength.getText(), dosageForm.getText(),
                        category.getText(),
                        parseDecimal(reorderLevel.getText(), "Reorder level"),
                        currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update pharmacy medicine", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && saved[0]) {
            showFeedback(pageFeedback, "Medicine details updated.", true);
            setStatus("Medicine details updated", "status-connected");
            refreshDashboard();
            reload.run();
        }
    }

    private void archiveMedicine(MedicineCatalogRow medicine,
                                 Runnable reload, Label pageFeedback) {
        if (!confirmAction("Archive medicine",
                "Archive " + medicine.name() + "? It will no longer be "
                        + "available for new stock or dispensing, and its "
                        + "historical records will be retained.")) {
            return;
        }
        try {
            dashboardDao.archiveMedicine(medicine.medicineId(),
                    currentUserId());
            showFeedback(pageFeedback,
                    "Medicine archived; historical records were retained.",
                    true);
            setStatus("Medicine archived", "status-connected");
            reload.run();
        } catch (SQLException | IllegalArgumentException
                 | IllegalStateException | SecurityException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to archive pharmacy medicine", exception);
            showFeedback(pageFeedback, exception.getMessage(), false);
        }
    }

    private void showCreateSupplierDialog(Runnable reload, Label pageFeedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Add supplier", "Register a medicine supplier.");
        GridPane form = createFormGrid();
        TextField name = createField("Supplier name");
        TextField contact = createField("Contact person");
        TextField phone = createField("Phone");
        TextField email = createField("Email");
        TextField address = createField("Address");
        Label feedback = createFeedbackLabel();
        addFormField(form, 0, "Supplier name", name);
        addFormField(form, 1, "Contact person", contact);
        addFormField(form, 2, "Phone", phone);
        addFormField(form, 3, "Email", email);
        addFormField(form, 4, "Address", address);
        form.add(feedback, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(form);
        boolean[] saved = new boolean[1];
        Button confirm = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                dashboardDao.createSupplier(name.getText(), contact.getText(),
                        phone.getText(), email.getText(), address.getText(),
                        currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to create pharmacy supplier", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && saved[0]) {
            showFeedback(pageFeedback, "Supplier record created.", true);
            setStatus("Supplier record created", "status-connected");
            reload.run();
        }
    }

    private void showEditSupplierDialog(SupplierRow supplier,
                                        Runnable reload,
                                        Label pageFeedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Edit supplier", "Update supplier contact details.");
        GridPane form = createFormGrid();
        Label id = new Label(Long.toString(supplier.supplierId()));
        TextField name = createField("Supplier name");
        name.setText(supplier.name());
        TextField contact = createField("Contact person");
        contact.setText(displayValueForInput(supplier.contactName()));
        TextField phone = createField("Phone");
        phone.setText(displayValueForInput(supplier.phone()));
        TextField email = createField("Email");
        email.setText(displayValueForInput(supplier.email()));
        TextField address = createField("Address");
        address.setText(displayValueForInput(supplier.address()));
        Label status = new Label(supplier.status()
                + " (managed by archive action)");
        Label feedback = createFeedbackLabel();
        addFormField(form, 0, "Supplier ID (read-only)", id);
        addFormField(form, 1, "Supplier name", name);
        addFormField(form, 2, "Contact person", contact);
        addFormField(form, 3, "Phone", phone);
        addFormField(form, 4, "Email", email);
        addFormField(form, 5, "Address", address);
        addFormField(form, 6, "Status", status);
        form.add(feedback, 0, 7, 2, 1);
        dialog.getDialogPane().setContent(form);
        boolean[] saved = new boolean[1];
        Button confirm = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirm.setText("Save changes");
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                dashboardDao.updateSupplier(supplier.supplierId(),
                        name.getText(), contact.getText(), phone.getText(),
                        email.getText(), address.getText(), currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update pharmacy supplier", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && saved[0]) {
            showFeedback(pageFeedback, "Supplier details updated.", true);
            setStatus("Supplier details updated", "status-connected");
            reload.run();
        }
    }

    private void archiveSupplier(SupplierRow supplier, Runnable reload,
                                 Label pageFeedback) {
        if (!confirmAction("Archive supplier",
                "Archive " + supplier.name() + "? Existing batches and "
                        + "supplier history will be retained. The supplier "
                        + "will no longer be selectable for new receipts.")) {
            return;
        }
        try {
            dashboardDao.archiveSupplier(supplier.supplierId(),
                    currentUserId());
            showFeedback(pageFeedback,
                    "Supplier archived; historical records were retained.",
                    true);
            setStatus("Supplier archived", "status-connected");
            reload.run();
        } catch (SQLException | IllegalArgumentException
                 | IllegalStateException | SecurityException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to archive pharmacy supplier", exception);
            showFeedback(pageFeedback, exception.getMessage(), false);
        }
    }

    private void showEditBatchDialog(MedicineBatchRow batch, Runnable reload,
                                     Label pageFeedback) {
        List<SupplierRow> suppliers;
        try {
            suppliers = dashboardDao.loadSuppliers("");
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load supplier options for batch edit", exception);
            showFeedback(pageFeedback, "Supplier options are unavailable.",
                    false);
            return;
        }
        Dialog<ButtonType> dialog = createDialog(
                "Edit batch metadata",
                "Change batch details only; stock and received timestamps "
                        + "remain unchanged.");
        GridPane form = createFormGrid();
        Label id = new Label(Long.toString(batch.batchId()));
        ComboBox<SupplierRow> supplier = new ComboBox<>(
                FXCollections.observableArrayList(suppliers.stream()
                        .filter(row -> "Active".equals(row.status())
                                || (batch.supplierId() != null
                                && row.supplierId() == batch.supplierId()))
                        .toList()));
        supplier.setPromptText("No supplier");
        if (batch.supplierId() != null) {
            suppliers.stream()
                    .filter(row -> row.supplierId() == batch.supplierId())
                    .findFirst().ifPresent(supplier::setValue);
        }
        TextField batchNumber = createField("Batch number");
        batchNumber.setText(batch.batchNumber());
        TextField unitCost = createField("Unit cost");
        unitCost.setText(decimalText(batch.unitCost()));
        TextField sellingPrice = createField("Selling price");
        sellingPrice.setText(decimalText(batch.sellingPrice()));
        DatePicker expiry = new DatePicker(batch.expiryDateValue());
        expiry.setPromptText("Optional expiry date");
        Label note = new Label("Quantity, batch ID, and received timestamp "
                + "are read-only.");
        Label feedback = createFeedbackLabel();
        addFormField(form, 0, "Batch ID (read-only)", id);
        addFormField(form, 1, "Medicine (read-only)",
                new Label(batch.medicineName()));
        addFormField(form, 2, "Supplier", supplier);
        addFormField(form, 3, "Batch number", batchNumber);
        addFormField(form, 4, "Unit cost", unitCost);
        addFormField(form, 5, "Selling price", sellingPrice);
        addFormField(form, 6, "Expiry date", expiry);
        form.add(note, 0, 7, 2, 1);
        form.add(feedback, 0, 8, 2, 1);
        dialog.getDialogPane().setContent(form);
        boolean[] saved = new boolean[1];
        Button confirm = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirm.setText("Save changes");
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                dashboardDao.updateBatchMetadata(batch.batchId(),
                        supplier.getValue() == null ? null
                                : supplier.getValue().supplierId(),
                        batchNumber.getText(),
                        parseDecimal(unitCost.getText(), "Unit cost"),
                        parseDecimal(sellingPrice.getText(), "Selling price"),
                        expiry.getValue(), currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update pharmacy batch metadata", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && saved[0]) {
            showFeedback(pageFeedback, "Batch metadata updated.", true);
            setStatus("Batch metadata updated", "status-connected");
            refreshDashboard();
            reload.run();
        }
    }

    private void showReceiveStockDialog(Runnable reload,
                                       Label pageFeedback) {
        List<MedicineOption> medicines;
        List<SupplierRow> suppliers;
        try {
            medicines = dashboardDao.loadMedicines();
            suppliers = dashboardDao.loadSuppliers("");
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load receive-stock form options", exception);
            showFeedback(pageFeedback,
                    "Medicine or supplier options are unavailable.", false);
            return;
        }
        Dialog<ButtonType> dialog = createDialog(
                "Receive medicine stock",
                "Register a new batch and receipt transaction.");
        GridPane form = createFormGrid();
        ComboBox<MedicineOption> medicine = new ComboBox<>(
                FXCollections.observableArrayList(medicines));
        medicine.setPromptText("Select medicine");
        ComboBox<SupplierRow> supplier = new ComboBox<>(
                FXCollections.observableArrayList(suppliers.stream()
                        .filter(row -> "Active".equals(row.status())).toList()));
        supplier.setPromptText("Optional supplier");
        TextField batch = createField("Batch number");
        TextField quantity = createField("Quantity received");
        TextField cost = createField("Unit cost");
        TextField price = createField("Selling price");
        DatePicker expiry = new DatePicker();
        expiry.setPromptText("Optional expiry date");
        TextArea notes = new TextArea();
        notes.setPromptText("Receipt notes");
        notes.setPrefRowCount(2);
        Label feedback = createFeedbackLabel();
        addFormField(form, 0, "Medicine", medicine);
        addFormField(form, 1, "Supplier", supplier);
        addFormField(form, 2, "Batch number", batch);
        addFormField(form, 3, "Quantity received", quantity);
        addFormField(form, 4, "Unit cost", cost);
        addFormField(form, 5, "Selling price", price);
        addFormField(form, 6, "Expiry date", expiry);
        addFormField(form, 7, "Notes", notes);
        form.add(feedback, 0, 8, 2, 1);
        dialog.getDialogPane().setContent(form);
        boolean[] saved = new boolean[1];
        Button confirm = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (medicine.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select a medicine.");
                }
                dashboardDao.receiveStock(
                        medicine.getValue().medicineId(),
                        supplier.getValue() == null ? null
                                : supplier.getValue().supplierId(),
                        batch.getText(),
                        parseDecimal(quantity.getText(), "Quantity received"),
                        parseDecimal(cost.getText(), "Unit cost"),
                        parseDecimal(price.getText(), "Selling price"),
                        expiry.getValue(), notes.getText(), currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to receive pharmacy stock", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && saved[0]) {
            showFeedback(pageFeedback, "Batch received and recorded.", true);
            setStatus("Medicine stock received", "status-connected");
            refreshDashboard();
            reload.run();
        }
    }

    private void showAdjustStockDialog(MedicineBatchRow batch,
                                       Runnable reload, Label pageFeedback) {
        Dialog<ButtonType> dialog = createDialog(
                "Adjust stock",
                "Record a positive or negative stock correction.");
        GridPane form = createFormGrid();
        Label medicine = new Label(batch.medicineName()
                + " • Batch " + batch.batchNumber()
                + " • Current stock " + batch.quantityOnHand());
        TextField delta = createField("Quantity change (e.g. -2 or 5)");
        TextField reason = createField("Reason for adjustment");
        Label feedback = createFeedbackLabel();
        addFormField(form, 0, "Selected batch", medicine);
        addFormField(form, 1, "Quantity change", delta);
        addFormField(form, 2, "Reason", reason);
        form.add(feedback, 0, 3, 2, 1);
        dialog.getDialogPane().setContent(form);
        boolean[] saved = new boolean[1];
        Button confirm = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                BigDecimal quantityDelta =
                        parseDecimal(delta.getText(), "Quantity change");
                if (quantityDelta.signum() == 0) {
                    throw new IllegalArgumentException(
                            "Quantity change cannot be zero.");
                }
                if (!confirmAction("Confirm stock adjustment",
                        "Record " + quantityDelta.toPlainString()
                                + " units for batch " + batch.batchNumber()
                                + "?")) {
                    event.consume();
                    return;
                }
                dashboardDao.adjustStock(batch.batchId(), quantityDelta,
                        reason.getText(), currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to adjust pharmacy stock", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && saved[0]) {
            showFeedback(pageFeedback, "Stock adjustment recorded.", true);
            setStatus("Stock adjustment recorded", "status-connected");
            refreshDashboard();
            reload.run();
        }
    }

    private void showDispenseDialog(PrescriptionRow prescription,
                                   Runnable reload, Label pageFeedback) {
        List<PrescriptionItemRow> items;
        try {
            items = dashboardDao.loadPrescriptionItems(
                    prescription.prescriptionId());
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load prescription items", exception);
            showFeedback(pageFeedback,
                    "Prescription items are unavailable.", false);
            return;
        }
        if (items.isEmpty()) {
            showFeedback(pageFeedback,
                    "This prescription has no remaining items.", false);
            return;
        }
        Dialog<ButtonType> dialog = createDialog(
                "Dispense prescription",
                "Enter the amount dispensed for each item. Leave an item "
                        + "blank to dispense none of it.");
        VBox form = new VBox(10);
        Label patient = new Label(prescription.patientName() + " • "
                + prescription.patientNumber());
        patient.getStyleClass().add("panel-hint");
        form.getChildren().add(patient);
        Map<Long, TextField> quantityFields = new LinkedHashMap<>();
        for (PrescriptionItemRow item : items) {
            VBox itemEntry = new VBox(4);
            Label itemDescription = new Label(dispenseLabel(item));
            itemDescription.setWrapText(true);
            itemDescription.getStyleClass().add("field-label");
            TextField quantity = createField("Quantity to dispense");
            quantityFields.put(item.prescriptionItemId(), quantity);
            itemEntry.getChildren().addAll(itemDescription, quantity);
            form.getChildren().add(itemEntry);
        }
        Label feedback = createFeedbackLabel();
        form.getChildren().add(feedback);
        dialog.getDialogPane().setContent(form);
        boolean[] saved = new boolean[1];
        String[] resultingStatus = new String[1];
        Button confirm = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirm.setText("Save dispensing");
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
                for (PrescriptionItemRow item : items) {
                    String text = quantityFields.get(
                            item.prescriptionItemId()).getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    BigDecimal quantity =
                            parseDecimal(text, item.medicineName());
                    if (quantity.signum() < 0) {
                        throw new IllegalArgumentException(
                                "Dispensed quantity cannot be negative.");
                    }
                    if (quantity.compareTo(item.remainingQuantity()) > 0) {
                        throw new IllegalArgumentException(
                                "Dispensed quantity exceeds the remaining "
                                        + "prescription for "
                                        + item.medicineName() + ".");
                    }
                    if (quantity.compareTo(item.availableQuantity()) > 0) {
                        throw new IllegalArgumentException(
                                "Requested quantity exceeds unexpired stock for "
                                        + item.medicineName() + ".");
                    }
                    if (quantity.signum() > 0) {
                        quantities.put(item.prescriptionItemId(), quantity);
                    }
                }
                resultingStatus[0] = dashboardDao.dispensePrescription(
                        prescription.prescriptionId(), quantities,
                        currentUserId());
                saved[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to dispense pharmacy prescription", exception);
                showFeedback(feedback, exception.getMessage(), false);
                event.consume();
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK && saved[0]) {
            showFeedback(pageFeedback,
                    "Dispensing saved: " + displayValue(resultingStatus[0])
                            + ".", true);
            setStatus("Prescription dispensing recorded",
                    "status-connected");
            refreshDashboard();
            reload.run();
        }
    }

    private void setUnavailableAlerts() {
        stockAlertLabel.setText(
                "Stock-level alerts are unavailable until the database "
                        + "connection is restored.");
        expiryAlertLabel.setText(
                "Expiry monitoring is unavailable until medicine batches "
                        + "can be read.");
    }

    private void setUnavailableMetrics() {
        for (Label value : new Label[] {
                totalMedicinesValue, lowStockMedicinesValue,
                outOfStockMedicinesValue, expiringMedicinesValue,
                pendingPrescriptionsValue, todaysDispensingsValue
        }) {
            value.setText("Unavailable");
        }
        categoryCards.getChildren().clear();
        categoryCards.getChildren().add(createUnavailableLabel(
                "Category summaries are unavailable."));
        categoryCountLabel.setText("Unavailable");
    }

    private void setStatus(String text, String stateClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(
                "status-warning", "status-connected"
        );
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
}
