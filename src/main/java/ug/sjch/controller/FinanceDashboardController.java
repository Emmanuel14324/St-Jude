package ug.sjch.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import ug.sjch.MainApp;
import ug.sjch.dao.FinanceDashboardDao;
import ug.sjch.dao.FinanceDashboardDao.InvoiceItemRow;
import ug.sjch.dao.FinanceDashboardDao.InvoiceItemUpdate;
import ug.sjch.dao.FinanceDashboardDao.InvoiceRow;
import ug.sjch.dao.FinanceDashboardDao.PaymentRow;
import ug.sjch.security.UserSession;

public class FinanceDashboardController {
    private static final Logger LOGGER =
            Logger.getLogger(FinanceDashboardController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private final FinanceDashboardDao dashboardDao = new FinanceDashboardDao();

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
    @FXML private Label revenueTrendStateLabel;
    @FXML private Label paymentMethodStateLabel;
    @FXML private Label todaysRevenueValue;
    @FXML private Label totalPaymentsValue;
    @FXML private Label outstandingInvoicesValue;
    @FXML private Label pendingPaymentsValue;
    @FXML private Label monthlyRevenueValue;
    @FXML private Label paidInvoicesValue;
    @FXML private Label partiallyPaidInvoicesValue;
    @FXML private Label voidedInvoicesValue;
    @FXML private Label invoiceCountLabel;
    @FXML private TableView<InvoiceRow> invoiceTable;
    @FXML private TextField topSearchField;
    @FXML private Button dashboardButton;
    @FXML private Button patientBillingButton;
    @FXML private Button recordPaymentButton;
    @FXML private Button paymentHistoryButton;
    @FXML private Button printReceiptButton;
    @FXML private Button outstandingBillsButton;
    @FXML private Button revenueReportsButton;
    @FXML private Button thisMonthButton;
    @FXML private Button filterButton;
    @FXML private Button exportButton;

    @FXML
    private void initialize() {
        SidebarResizer.install(sidebar, sidebarResizeHandle);
        configureInvoiceTable(invoiceTable);
        dateLabel.setText(LocalDate.now().format(DATE_FORMAT));

        if (!isFinanceUser()) {
            setStatus("Finance staff session is not available", "status-warning");
            sidebarUserLabel.setText("Not signed in");
            sidebarRoleLabel.setText("Finance");
            topUserLabel.setText("Not signed in");
            topRoleLabel.setText("Finance");
            setUnavailable("A finance session is required.");
            setActionsDisabled(true);
            return;
        }

        sidebarUserLabel.setText(UserSession.getCurrentUser().username());
        sidebarRoleLabel.setText(UserSession.getCurrentUser().roleName());
        topUserLabel.setText(UserSession.getCurrentUser().username());
        topRoleLabel.setText(UserSession.getCurrentUser().roleName());
        setActionsDisabled(false);
        showDashboard();
    }

    private boolean isFinanceUser() {
        return UserSession.hasRole("Accountant")
                || UserSession.hasRole("Finance Officer");
    }

    private void setActionsDisabled(boolean disabled) {
        topSearchField.setDisable(disabled);
        thisMonthButton.setDisable(disabled);
        filterButton.setDisable(disabled);
        exportButton.setDisable(disabled);
        for (Button button : List.of(dashboardButton, patientBillingButton,
                recordPaymentButton, paymentHistoryButton, printReceiptButton,
                outstandingBillsButton, revenueReportsButton)) {
            button.setDisable(disabled);
        }
    }

    @FXML
    private void showDashboard() {
        setActive(dashboardButton);
        contentScroll.setContent(dashboardHome);
        refreshDashboard();
    }

    private void refreshDashboard() {
        if (!isFinanceUser()) {
            setUnavailable("A finance session is required.");
            return;
        }
        try {
            if (!dashboardDao.isConfigured()) {
                setUnavailable(FinanceDashboardDao.NOT_CONFIGURED);
                setStatus("not configured", "status-warning");
                return;
            }
            var metrics = dashboardDao.loadMetrics();
            todaysRevenueValue.setText(formatMoney(metrics.todaysRevenue()));
            totalPaymentsValue.setText(formatMoney(metrics.totalPayments()));
            outstandingInvoicesValue.setText(Integer.toString(
                    metrics.outstandingInvoices()));
            pendingPaymentsValue.setText(Integer.toString(
                    metrics.pendingPayments()));
            monthlyRevenueValue.setText(formatMoney(metrics.monthlyRevenue()));
            paidInvoicesValue.setText(Integer.toString(
                    metrics.paidInvoices()));
            partiallyPaidInvoicesValue.setText(Integer.toString(
                    metrics.partiallyPaidInvoices()));
            voidedInvoicesValue.setText(Integer.toString(
                    metrics.voidedInvoices()));
            revenueTrendStateLabel.setText("Daily revenue and payment totals");
            paymentMethodStateLabel.setText("Payment method statistics");

            List<InvoiceRow> rows = dashboardDao.searchInvoices("");
            invoiceTable.setItems(FXCollections.observableArrayList(rows));
            invoiceCountLabel.setText(rows.size()
                    + (rows.size() == 1 ? " invoice" : " invoices"));

            try {
                var daily = dashboardDao.loadMonthlyRevenue();
                revenueTrendStateLabel.setText(daily.isEmpty()
                        ? "No completed payments this month."
                        : formatDailyRevenue(daily));
                var methods = dashboardDao.loadPaymentMethods();
                paymentMethodStateLabel.setText(methods.isEmpty()
                        ? "No completed payments are recorded."
                        : formatMethods(methods));
            } catch (SQLException exception) {
                LOGGER.log(Level.WARNING, "Unable to load finance summaries",
                        exception);
            }
            setStatus("Live finance data connected", "status-connected");
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING, "Unable to load finance dashboard",
                    exception);
            setUnavailable(exception.getMessage());
            setStatus("not configured", "status-warning");
        }
    }

    private void setUnavailable(String message) {
        String text = FinanceDashboardDao.NOT_CONFIGURED.equals(message)
                || (message != null && message.toLowerCase(Locale.ROOT)
                        .contains("not configured"))
                ? "not configured" : "Not available";
        todaysRevenueValue.setText(text);
        totalPaymentsValue.setText(text);
        outstandingInvoicesValue.setText(text);
        pendingPaymentsValue.setText(text);
        monthlyRevenueValue.setText(text);
        paidInvoicesValue.setText(text);
        partiallyPaidInvoicesValue.setText(text);
        voidedInvoicesValue.setText(text);
        revenueTrendStateLabel.setText(message == null
                ? "not configured" : message);
        paymentMethodStateLabel.setText(message == null
                ? "not configured" : message);
        invoiceTable.setItems(FXCollections.observableArrayList());
        invoiceTable.setPlaceholder(new Label(message == null
                ? "not configured" : message));
        invoiceCountLabel.setText("not configured");
    }

    @FXML
    private void showInvoices() {
        setActive(patientBillingButton);
        VBox page = createPage("Patient billing",
                "Search, inspect, and select patient invoices.");
        TextField search = new TextField();
        search.setPromptText("Invoice number, patient, or status");
        Button searchButton = new Button("Search");
        Button editDraftButton = new Button("Edit selected draft");
        Button voidInvoiceButton = new Button("Void selected invoice");
        editDraftButton.setDisable(true);
        Label count = new Label();
        Label feedback = new Label();
        TableView<InvoiceRow> table = new TableView<>();
        configureInvoiceTable(table);
        Label integrityHint = new Label(
                "Only unpaid draft invoices can be edited; totals are "
                        + "recalculated from their line items. Issued invoices "
                        + "and payment history are retained and not edited.");
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) ->
                        editDraftButton.setDisable(selected == null
                                || !"DRAFT".equalsIgnoreCase(selected.status())
                                || selected.paidAmount().signum() != 0));
        HBox filters = new HBox(10, search, searchButton, count);
        HBox.setHgrow(search, Priority.ALWAYS);
        page.getChildren().addAll(filters, integrityHint, table,
                new HBox(10, editDraftButton, voidInvoiceButton), feedback);
        Runnable reload = () -> loadInvoices(search.getText(), table, count,
                feedback);
        searchButton.setOnAction(event -> reload.run());
        search.setOnAction(event -> reload.run());
        editDraftButton.setOnAction(event -> {
            InvoiceRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null || !"DRAFT".equalsIgnoreCase(selected.status())
                    || selected.paidAmount().signum() != 0) {
                feedback.setText("Select an unpaid draft invoice to edit.");
                return;
            }
            showDraftInvoiceEditor(selected, reload, feedback);
        });
        voidInvoiceButton.setOnAction(event -> {
            InvoiceRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                feedback.setText("Select an invoice first.");
                return;
            }
            if (!isFinanceUser()) {
                feedback.setText(
                        "Only an Accountant or Finance Officer can void invoices.");
                return;
            }
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Void invoice");
            confirmation.setHeaderText("Retain and mark this invoice VOID?");
            confirmation.setContentText("Invoice " + selected.invoiceNumber()
                    + " will not be deleted. Invoices with payments or a paid "
                    + "state cannot be voided.");
            if (confirmation.showAndWait().filter(
                    ButtonType.OK::equals).isEmpty()) return;
            try {
                dashboardDao.voidInvoice(selected.invoiceId(), currentUserId());
                feedback.setText("Invoice voided and retained for audit.");
                reload.run();
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                feedback.setText(exception.getMessage());
            }
        });
        reload.run();
        showPage(page);
    }

    private void showDraftInvoiceEditor(InvoiceRow invoice, Runnable reload,
                                        Label feedback) {
        List<InvoiceItemRow> items;
        try {
            items = dashboardDao.loadInvoiceItems(invoice.invoiceId());
        } catch (SQLException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING,
                    "Unable to load draft invoice items", exception);
            feedback.setText(exception.getMessage());
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit draft invoice");
        dialog.setHeaderText(invoice.invoiceNumber() + " - "
                + invoice.patientNumber() + " - "
                + invoice.patientName());
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(760, 560);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.setPadding(new Insets(12));
        DatePicker dueDate = new DatePicker(invoice.dueDate());
        form.addRow(0, new Label("Due date"), dueDate);
        form.addRow(1, new Label("Description"),
                new Label("Quantity"), new Label("Unit price"));

        List<TextField> descriptions = new java.util.ArrayList<>();
        List<TextField> quantities = new java.util.ArrayList<>();
        List<TextField> unitPrices = new java.util.ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            InvoiceItemRow item = items.get(index);
            TextField description = new TextField(item.description());
            TextField quantity = new TextField(
                    item.quantity().toPlainString());
            TextField unitPrice = new TextField(
                    item.unitPrice().toPlainString());
            description.setPrefColumnCount(28);
            quantity.setPrefColumnCount(10);
            unitPrice.setPrefColumnCount(12);
            descriptions.add(description);
            quantities.add(quantity);
            unitPrices.add(unitPrice);
            form.addRow(index + 2, description, quantity, unitPrice);
        }
        ScrollPane scrollPane = new ScrollPane(form);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        dialog.getDialogPane().setContent(scrollPane);

        boolean[] updated = new boolean[1];
        Button confirmButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        confirmButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                List<InvoiceItemUpdate> updates = new java.util.ArrayList<>();
                for (int index = 0; index < items.size(); index++) {
                    updates.add(new InvoiceItemUpdate(
                            items.get(index).invoiceItemId(),
                            descriptions.get(index).getText(),
                            new BigDecimal(quantities.get(index).getText().trim()),
                            new BigDecimal(unitPrices.get(index).getText().trim())));
                }
                dashboardDao.updateDraftInvoice(invoice.invoiceId(), updates,
                        dueDate.getValue(), currentUserId());
                updated[0] = true;
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING,
                        "Unable to update draft invoice", exception);
                showAlert(Alert.AlertType.WARNING, "Invoice not updated",
                        exception.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
        if (updated[0]) {
            feedback.setText("Draft invoice updated and total recalculated.");
            reload.run();
        }
    }

    private void loadInvoices(String query, TableView<InvoiceRow> table,
                              Label count, Label feedback) {
        try {
            if (!dashboardDao.isConfigured()) {
                table.setItems(FXCollections.observableArrayList());
                table.setPlaceholder(new Label("not configured"));
                count.setText("not configured");
                feedback.setText("Finance tables are not configured.");
                return;
            }
            List<InvoiceRow> rows = dashboardDao.searchInvoices(query);
            table.setItems(FXCollections.observableArrayList(rows));
            count.setText(rows.size() + (rows.size() == 1
                    ? " invoice" : " invoices"));
            feedback.setText("");
        } catch (SQLException | IllegalStateException exception) {
            table.setItems(FXCollections.observableArrayList());
            table.setPlaceholder(new Label(exception.getMessage()));
            count.setText("Unavailable");
            feedback.setText(exception.getMessage());
        }
    }

    @FXML
    private void showOutstanding() {
        setActive(outstandingBillsButton);
        VBox page = createPage("Outstanding invoices",
                "Invoices with an unpaid or partially paid balance.");
        TableView<InvoiceRow> table = new TableView<>();
        configureInvoiceTable(table);
        Label count = new Label();
        Label feedback = new Label();
        page.getChildren().addAll(table, count, feedback);
        try {
            if (!dashboardDao.isConfigured()) {
                table.setPlaceholder(new Label("not configured"));
                count.setText("not configured");
            } else {
                List<InvoiceRow> rows =
                        dashboardDao.loadOutstandingInvoices();
                table.setItems(FXCollections.observableArrayList(rows));
                count.setText(rows.size()
                        + (rows.size() == 1 ? " outstanding invoice"
                        : " outstanding invoices"));
            }
        } catch (SQLException | IllegalStateException exception) {
            table.setPlaceholder(new Label(exception.getMessage()));
            feedback.setText(exception.getMessage());
        }
        showPage(page);
    }

    @FXML
    private void showPaymentHistory() {
        setActive(paymentHistoryButton);
        VBox page = createPage("Payment history",
                "Completed, pending, failed, and reversed payments.");
        TextField search = new TextField();
        search.setPromptText("Invoice, patient, method, status, or reference");
        Button searchButton = new Button("Search");
        Button reversePaymentButton = new Button("Reverse selected payment");
        Label count = new Label();
        TableView<PaymentRow> table = createPaymentTable();
        Label feedback = new Label();
        HBox filters = new HBox(10, search, searchButton, count);
        HBox.setHgrow(search, Priority.ALWAYS);
        Label integrityHint = new Label(
                "Recorded payment amount, reference, ID, and timestamp are "
                        + "immutable. Reversal retains the payment record.");
        page.getChildren().addAll(filters, integrityHint, table,
                reversePaymentButton, feedback);
        Runnable reload = () -> {
            try {
                if (!dashboardDao.isConfigured()) {
                    table.setPlaceholder(new Label("not configured"));
                    count.setText("not configured");
                    feedback.setText("Finance tables are not configured.");
                    return;
                }
                List<PaymentRow> rows =
                        dashboardDao.loadPaymentHistory(search.getText());
                table.setItems(FXCollections.observableArrayList(rows));
                count.setText(rows.size()
                        + (rows.size() == 1 ? " payment" : " payments"));
                feedback.setText("");
            } catch (SQLException | IllegalStateException exception) {
                table.setItems(FXCollections.observableArrayList());
                table.setPlaceholder(new Label(exception.getMessage()));
                count.setText("Unavailable");
                feedback.setText(exception.getMessage());
            }
        };
        searchButton.setOnAction(event -> reload.run());
        search.setOnAction(event -> reload.run());
        reversePaymentButton.setOnAction(event -> {
            PaymentRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                feedback.setText("Select a payment first.");
                return;
            }
            if (!isFinanceUser()) {
                feedback.setText(
                        "Only an Accountant or Finance Officer can reverse payments.");
                return;
            }
            if (!"COMPLETED".equalsIgnoreCase(selected.status())) {
                feedback.setText("Only completed payments can be reversed.");
                return;
            }
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Reverse payment");
            confirmation.setHeaderText("Reverse this completed payment?");
            confirmation.setContentText("Payment " + selected.paymentId()
                    + " for " + formatMoney(selected.amount())
                    + " will be marked REVERSED. Its amount and reference "
                    + "will remain unchanged.");
            if (confirmation.showAndWait().filter(
                    ButtonType.OK::equals).isEmpty()) return;
            try {
                dashboardDao.reversePayment(
                        selected.paymentId(), currentUserId());
                feedback.setText("Payment reversed and retained for audit.");
                reload.run();
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                feedback.setText(exception.getMessage());
            }
        });
        reload.run();
        showPage(page);
    }

    @FXML
    private void showRecordPayment() {
        setActive(recordPaymentButton);
        if (!isFinanceUser()) {
            showAlert(Alert.AlertType.WARNING, "Permission required",
                    "Only an Accountant or Finance Officer can record payments.");
            return;
        }
        openRecordPaymentDialog();
    }

    private void openRecordPaymentDialog() {
        List<InvoiceRow> outstanding;
        try {
            if (!dashboardDao.isConfigured()) {
                showAlert(Alert.AlertType.WARNING, "not configured",
                        "Finance tables are not configured.");
                return;
            }
            outstanding = dashboardDao.loadOutstandingInvoices();
        } catch (SQLException | IllegalStateException exception) {
            showAlert(Alert.AlertType.WARNING, "Unavailable",
                    exception.getMessage());
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record payment");
        dialog.setHeaderText("Record a partial or full payment");
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.CANCEL, ButtonType.OK);
        ComboBox<InvoiceRow> invoice = new ComboBox<>(
                FXCollections.observableArrayList(outstanding));
        invoice.setMaxWidth(Double.MAX_VALUE);
        invoice.setPromptText("Select outstanding invoice");
        TextField amount = new TextField();
        amount.setPromptText("Amount");
        ComboBox<String> method = new ComboBox<>(FXCollections.observableArrayList(
                "CASH", "MOBILE_MONEY", "CARD", "BANK_TRANSFER"));
        method.setMaxWidth(Double.MAX_VALUE);
        method.setValue("CASH");
        TextField reference = new TextField();
        reference.setPromptText("Optional reference");
        TextArea notes = new TextArea();
        notes.setPromptText("Optional notes");
        notes.setPrefRowCount(3);
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(12));
        form.addRow(0, new Label("Invoice"), invoice);
        form.addRow(1, new Label("Amount"), amount);
        form.addRow(2, new Label("Method"), method);
        form.addRow(3, new Label("Reference"), reference);
        form.addRow(4, new Label("Notes"), notes);
        dialog.getDialogPane().setContent(form);
        dialog.setResultConverter(button -> button);
        dialog.showAndWait().ifPresent(button -> {
            if (button != ButtonType.OK) return;
            if (invoice.getValue() == null) {
                showAlert(Alert.AlertType.WARNING, "Invoice required",
                        "Select an outstanding invoice.");
                return;
            }
            try {
                BigDecimal value = new BigDecimal(amount.getText().trim());
                String status = dashboardDao.recordPayment(
                        invoice.getValue().invoiceId(), value,
                        method.getValue(), reference.getText(), notes.getText(),
                        currentUserId());
                showAlert(Alert.AlertType.INFORMATION, "Payment recorded",
                        "Invoice status: " + status);
                showOutstanding();
            } catch (NumberFormatException exception) {
                showAlert(Alert.AlertType.WARNING, "Invalid amount",
                        "Enter a valid payment amount.");
            } catch (SQLException | IllegalArgumentException
                     | IllegalStateException | SecurityException exception) {
                LOGGER.log(Level.WARNING, "Unable to record payment",
                        exception);
                showAlert(Alert.AlertType.WARNING, "Payment not recorded",
                        exception.getMessage());
            }
        });
    }

    @FXML
    private void showReceipts() {
        setActive(printReceiptButton);
        VBox page = createPage("Receipts",
                "Select a completed payment and view its printable receipt.");
        TableView<PaymentRow> table = createPaymentTable();
        TextArea receipt = new TextArea();
        receipt.setEditable(false);
        receipt.setPromptText("Select a completed payment.");
        Button viewReceipt = new Button("View receipt");
        page.getChildren().addAll(table, viewReceipt, receipt);
        try {
            if (!dashboardDao.isConfigured()) {
                table.setPlaceholder(new Label("not configured"));
            } else {
                table.setItems(FXCollections.observableArrayList(
                        dashboardDao.loadPaymentHistory("")
                                .stream()
                                .filter(row -> "COMPLETED".equalsIgnoreCase(
                                        row.status()))
                                .toList()));
            }
        } catch (SQLException | IllegalStateException exception) {
            table.setPlaceholder(new Label(exception.getMessage()));
        }
        viewReceipt.setOnAction(event -> {
            PaymentRow selected =
                    table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                receipt.setText("Select a completed payment first.");
                return;
            }
            receipt.setText(buildReceipt(selected));
        });
        showPage(page);
    }

    private String buildReceipt(PaymentRow payment) {
        return "ST JUDE CENTRAL HOSPITAL\n"
                + "FINANCE RECEIPT\n\n"
                + "Receipt / Payment: " + payment.paymentId() + "\n"
                + "Invoice: " + payment.invoiceNumber() + "\n"
                + "Patient: " + payment.patientName() + "\n"
                + "Amount: " + formatMoney(payment.amount()) + "\n"
                + "Method: " + payment.method() + "\n"
                + "Reference: " + (payment.reference() == null
                        ? "-" : payment.reference()) + "\n"
                + "Received: " + payment.receivedAt() + "\n"
                + "Received by: " + payment.receivedBy() + "\n";
    }

    @FXML
    private void showReports() {
        setActive(revenueReportsButton);
        VBox page = createPage("Revenue reports",
                "Monthly collections and payment-method summary.");
        TextArea report = new TextArea();
        report.setEditable(false);
        report.setPrefRowCount(14);
        Button refresh = new Button("Refresh report");
        page.getChildren().addAll(refresh, report);
        Runnable reload = () -> {
            try {
                if (!dashboardDao.isConfigured()) {
                    report.setText("not configured");
                    return;
                }
                var metrics = dashboardDao.loadMetrics();
                var revenue = dashboardDao.loadMonthlyRevenue();
                var methods = dashboardDao.loadPaymentMethods();
                String text = "Monthly revenue: "
                        + formatMoney(metrics.monthlyRevenue()) + "\n"
                        + "Today's revenue: "
                        + formatMoney(metrics.todaysRevenue()) + "\n"
                        + "Total payments: "
                        + formatMoney(metrics.totalPayments()) + "\n\n"
                        + "DAILY COLLECTIONS\n"
                        + (revenue.isEmpty()
                        ? "No completed payments this month.\n"
                        : formatDailyRevenue(revenue) + "\n")
                        + "\nPAYMENT METHODS\n"
                        + (methods.isEmpty()
                        ? "No completed payments are recorded."
                        : formatMethods(methods));
                report.setText(text);
            } catch (SQLException | IllegalStateException exception) {
                report.setText(exception.getMessage());
            }
        };
        refresh.setOnAction(event -> reload.run());
        reload.run();
        showPage(page);
    }

    @FXML
    private void handleTopSearch() {
        String query = topSearchField.getText();
        showInvoices();
        if (!(contentScroll.getContent() instanceof VBox page)) return;
        for (javafx.scene.Node node : page.getChildren()) {
            if (node instanceof HBox hbox) {
                for (javafx.scene.Node child : hbox.getChildren()) {
                    if (child instanceof TextField field) {
                        field.setText(query);
                        field.fireEvent(
                                new javafx.event.ActionEvent());
                        return;
                    }
                }
            }
        }
    }

    private void configureInvoiceTable(TableView<InvoiceRow> table) {
        table.getColumns().clear();
        table.setEditable(false);
        table.getColumns().add(column("Invoice", 150,
                row -> row.invoiceNumber()));
        table.getColumns().add(column("Patient", 180,
                row -> row.patientNumber() + " - " + row.patientName()));
        table.getColumns().add(column("Status", 120,
                row -> row.status()));
        table.getColumns().add(column("Total", 130,
                row -> formatMoney(row.totalAmount())));
        table.getColumns().add(column("Paid", 130,
                row -> formatMoney(row.paidAmount())));
        table.getColumns().add(column("Balance", 130,
                row -> formatMoney(row.balance())));
        table.getColumns().add(column("Created", 145,
                row -> row.createdAt()));
    }

    private TableView<PaymentRow> createPaymentTable() {
        TableView<PaymentRow> table = new TableView<>();
        table.setEditable(false);
        table.getColumns().add(column("Payment", 100,
                row -> Long.toString(row.paymentId())));
        table.getColumns().add(column("Invoice", 150,
                row -> row.invoiceNumber()));
        table.getColumns().add(column("Patient", 180,
                row -> row.patientName()));
        table.getColumns().add(column("Amount", 130,
                row -> formatMoney(row.amount())));
        table.getColumns().add(column("Method", 130,
                row -> row.method()));
        table.getColumns().add(column("Status", 100,
                row -> row.status()));
        table.getColumns().add(column("Reference", 130,
                row -> row.reference() == null ? "-" : row.reference()));
        table.getColumns().add(column("Received", 145,
                row -> row.receivedAt()));
        table.setPlaceholder(new Label("No payment records found."));
        return table;
    }

    private <T> TableColumn<T, String> column(String title, int width,
                                               java.util.function.Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private VBox createPage(String title, String subtitle) {
        VBox page = new VBox(12);
        page.getStyleClass().addAll("dashboard-content", "finance-content");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("page-subtitle");
        page.getChildren().addAll(titleLabel, subtitleLabel);
        return page;
    }

    private void showPage(VBox page) {
        contentScroll.setContent(page);
    }

    private void setActive(Button active) {
        for (Button button : List.of(dashboardButton, patientBillingButton,
                recordPaymentButton, paymentHistoryButton, printReceiptButton,
                outstandingBillsButton, revenueReportsButton)) {
            button.getStyleClass().remove("nav-button-active");
        }
        if (active != null && !active.getStyleClass().contains(
                "nav-button-active")) {
            active.getStyleClass().add("nav-button-active");
        }
    }

    private long currentUserId() {
        return UserSession.getCurrentUser().id();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "UGX 0";
        return "UGX " + amount.toPlainString();
    }

    private String formatDailyRevenue(
            List<FinanceDashboardDao.DailyRevenue> rows) {
        StringBuilder text = new StringBuilder();
        for (var row : rows) {
            text.append(row.day()).append(": ")
                    .append(formatMoney(row.amount())).append("\n");
        }
        return text.toString().trim();
    }

    private String formatMethods(
            List<FinanceDashboardDao.MethodSummary> rows) {
        StringBuilder text = new StringBuilder();
        for (var row : rows) {
            text.append(row.method()).append(": ")
                    .append(formatMoney(row.amount()))
                    .append(" (").append(row.count()).append(")\n");
        }
        return text.toString().trim();
    }

    private void setStatus(String text, String stateClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(
                "status-warning", "status-connected");
        statusLabel.getStyleClass().add(stateClass);
    }

    private void showAlert(Alert.AlertType type, String title,
                           String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleLogout() throws Exception {
        UserSession.clear();
        Parent root = FXMLLoader.load(
                MainApp.class.getResource("/ug/sjch/views/login.fxml"));
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource(
                        "/ug/sjch/styles/application.css")
                        .toExternalForm());
        stage.setScene(scene);
        stage.setTitle("St Jude Central Hospital - SJCH");
    }
}
