package ug.sjch.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FinanceDashboardDao {
    public static final String NOT_CONFIGURED =
            "Finance tables are not configured.";

    public record FinanceMetrics(
            BigDecimal todaysRevenue,
            BigDecimal totalPayments,
            int outstandingInvoices,
            int pendingPayments,
            BigDecimal monthlyRevenue,
            int paidInvoices,
            int partiallyPaidInvoices,
            int voidedInvoices
    ) {}

    public record InvoiceRow(
            long invoiceId,
            long patientId,
            String invoiceNumber,
            String patientNumber,
            String patientName,
            String status,
            String currency,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal balance,
            String createdAt,
            LocalDate dueDate
    ) {
        @Override
        public String toString() {
            return invoiceNumber + " - " + patientName
                    + " (balance " + balance.toPlainString() + " "
                    + currency + ")";
        }
    }

    public record InvoiceItemRow(
            long invoiceItemId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    public record InvoiceItemUpdate(
            long invoiceItemId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice
    ) {}

    public record PaymentRow(
            long paymentId,
            long invoiceId,
            String invoiceNumber,
            String patientName,
            BigDecimal amount,
            String method,
            String status,
            String reference,
            String receivedBy,
            String receivedAt,
            String notes
    ) {}

    public record MethodSummary(String method, BigDecimal amount, int count) {}
    public record DailyRevenue(String day, BigDecimal amount) {}

    public boolean isConfigured() throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            return OracleSchemaInspector.tableExists(connection, "INVOICES")
                    && OracleSchemaInspector.tableExists(connection, "INVOICE_ITEMS")
                    && OracleSchemaInspector.tableExists(connection, "PAYMENTS");
        }
    }

    public FinanceMetrics loadMetrics() throws SQLException {
        requireConfigured();
        String sql = """
                SELECT
                    NVL((SELECT SUM(p.amount)
                         FROM payments p
                         WHERE p.payment_status = 'COMPLETED'
                           AND TRUNC(p.received_at) = TRUNC(SYSDATE)), 0)
                        AS todays_revenue,
                    NVL((SELECT SUM(p.amount)
                         FROM payments p
                         WHERE p.payment_status = 'COMPLETED'), 0)
                        AS total_payments,
                    (SELECT COUNT(*)
                     FROM invoices i
                     WHERE i.invoice_status IN ('ISSUED', 'PARTIALLY_PAID')) 
                        AS outstanding_invoices,
                    (SELECT COUNT(*)
                     FROM payments p
                     WHERE p.payment_status = 'PENDING')
                        AS pending_payments,
                    NVL((SELECT SUM(p.amount)
                         FROM payments p
                         WHERE p.payment_status = 'COMPLETED'
                           AND p.received_at >= TRUNC(SYSDATE, 'MM')), 0)
                        AS monthly_revenue,
                    (SELECT COUNT(*)
                     FROM invoices i
                     WHERE i.invoice_status = 'PAID')
                        AS paid_invoices,
                    (SELECT COUNT(*)
                     FROM invoices i
                     WHERE i.invoice_status = 'PARTIALLY_PAID')
                        AS partially_paid_invoices,
                    (SELECT COUNT(*)
                     FROM invoices i
                     WHERE i.invoice_status = 'VOID')
                        AS voided_invoices
                FROM dual
                """;
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return new FinanceMetrics(
                    resultSet.getBigDecimal("todays_revenue"),
                    resultSet.getBigDecimal("total_payments"),
                    resultSet.getInt("outstanding_invoices"),
                    resultSet.getInt("pending_payments"),
                    resultSet.getBigDecimal("monthly_revenue"),
                    resultSet.getInt("paid_invoices"),
                    resultSet.getInt("partially_paid_invoices"),
                    resultSet.getInt("voided_invoices"));
        }
    }

    public List<InvoiceRow> searchInvoices(String search) throws SQLException {
        requireConfigured();
        String patientNameExpression = patientNameExpression();
        String query = optional(search);
        StringBuilder sql = new StringBuilder("""
                SELECT i.invoice_id, i.patient_id, i.invoice_number,
                       p.patient_number, %s AS patient_name,
                       i.invoice_status, i.currency_code, i.total_amount,
                       NVL((SELECT SUM(pay.amount)
                            FROM payments pay
                            WHERE pay.invoice_id = i.invoice_id
                              AND pay.payment_status = 'COMPLETED'), 0)
                           AS paid_amount,
                       TO_CHAR(i.created_at, 'DD Mon YYYY HH:MI AM') AS created_at,
                       i.due_at
                FROM invoices i
                JOIN patients p ON p.patient_id = i.patient_id
                """.formatted(patientNameExpression));
        if (query != null) {
            sql.append("""
                    WHERE LOWER(i.invoice_number) LIKE LOWER(?)
                       OR LOWER(p.patient_number) LIKE LOWER(?)
                       OR LOWER(%s) LIKE LOWER(?)
                       OR LOWER(i.invoice_status) LIKE LOWER(?)
                    """.formatted(patientNameExpression));
        }
        sql.append(" ORDER BY i.created_at DESC NULLS LAST, i.invoice_id DESC FETCH FIRST 200 ROWS ONLY");
        List<InvoiceRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            if (query != null) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
                for (int index = 1; index <= 4; index++) {
                    statement.setString(index, pattern);
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    BigDecimal total = nz(resultSet.getBigDecimal("total_amount"));
                    BigDecimal paid = nz(resultSet.getBigDecimal("paid_amount"));
                    rows.add(new InvoiceRow(
                            resultSet.getLong("invoice_id"),
                            resultSet.getLong("patient_id"),
                            resultSet.getString("invoice_number"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("invoice_status"),
                            resultSet.getString("currency_code"),
                            total, paid, total.subtract(paid),
                            resultSet.getString("created_at"),
                            resultSet.getTimestamp("due_at") == null ? null
                                    : resultSet.getTimestamp("due_at")
                                            .toLocalDateTime().toLocalDate()));
                }
            }
        }
        return rows;
    }

    public FinanceMetrics loadOverviewMetrics() throws SQLException {
        return loadMetrics();
    }

    public List<InvoiceRow> loadInvoices(String search) throws SQLException {
        return searchInvoices(search);
    }

    public List<PaymentRow> searchPayments(String search) throws SQLException {
        return loadPaymentHistory(search);
    }

    public List<InvoiceRow> loadOutstandingInvoices() throws SQLException {
        return searchInvoices(null).stream()
                .filter(row -> "ISSUED".equalsIgnoreCase(row.status())
                        || "PARTIALLY_PAID".equalsIgnoreCase(row.status()))
                .filter(row -> row.balance().signum() > 0)
                .toList();
    }

    public List<PaymentRow> loadPaymentHistory(String search) throws SQLException {
        requireConfigured();
        String patientNameExpression = patientNameExpression();
        String query = optional(search);
        StringBuilder sql = new StringBuilder("""
                SELECT pay.payment_id, pay.invoice_id, i.invoice_number,
                       %s AS patient_name, pay.amount, pay.payment_method,
                       pay.payment_status, pay.payment_reference,
                       TRIM(u.username) AS received_by,
                       TO_CHAR(pay.received_at, 'DD Mon YYYY HH:MI AM') AS received_at,
                       pay.notes
                FROM payments pay
                JOIN invoices i ON i.invoice_id = pay.invoice_id
                JOIN patients p ON p.patient_id = i.patient_id
                LEFT JOIN users u ON u.user_id = pay.received_by_user_id
                """.formatted(patientNameExpression));
        if (query != null) {
            sql.append("""
                    WHERE LOWER(i.invoice_number) LIKE LOWER(?)
                       OR LOWER(p.patient_number) LIKE LOWER(?)
                       OR LOWER(%s) LIKE LOWER(?)
                       OR LOWER(pay.payment_method) LIKE LOWER(?)
                       OR LOWER(pay.payment_status) LIKE LOWER(?)
                       OR LOWER(NVL(pay.payment_reference, '')) LIKE LOWER(?)
                    """.formatted(patientNameExpression));
        }
        sql.append(" ORDER BY pay.received_at DESC NULLS LAST, pay.payment_id DESC FETCH FIRST 300 ROWS ONLY");
        List<PaymentRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            if (query != null) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
                for (int index = 1; index <= 6; index++) statement.setString(index, pattern);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new PaymentRow(
                            resultSet.getLong("payment_id"),
                            resultSet.getLong("invoice_id"),
                            resultSet.getString("invoice_number"),
                            resultSet.getString("patient_name"),
                            nz(resultSet.getBigDecimal("amount")),
                            resultSet.getString("payment_method"),
                            resultSet.getString("payment_status"),
                            resultSet.getString("payment_reference"),
                            resultSet.getString("received_by"),
                            resultSet.getString("received_at"),
                            resultSet.getString("notes")));
                }
            }
        }
        return rows;
    }

    public List<InvoiceItemRow> loadInvoiceItems(long invoiceId) throws SQLException {
        requireConfigured();
        String sql = """
                SELECT invoice_item_id, description, quantity, unit_price, line_total
                FROM invoice_items
                WHERE invoice_id = ?
                ORDER BY invoice_item_id
                """;
        List<InvoiceItemRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, invoiceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new InvoiceItemRow(
                            resultSet.getLong("invoice_item_id"),
                            resultSet.getString("description"),
                            nz(resultSet.getBigDecimal("quantity")),
                            nz(resultSet.getBigDecimal("unit_price")),
                            nz(resultSet.getBigDecimal("line_total"))));
                }
            }
        }
        return rows;
    }

    public void updateDraftInvoice(long invoiceId,
                                   List<InvoiceItemUpdate> itemUpdates,
                                   LocalDate dueDate, long userId)
            throws SQLException {
        if (itemUpdates == null) {
            throw new IllegalArgumentException("Invoice items are required.");
        }
        Map<Long, InvoiceItemUpdate> updatesById = new HashMap<>();
        BigDecimal updatedTotal = BigDecimal.ZERO.setScale(2);
        for (InvoiceItemUpdate update : itemUpdates) {
            if (update == null || updatesById.putIfAbsent(
                    update.invoiceItemId(), update) != null) {
                throw new IllegalArgumentException(
                        "Each invoice item must be selected only once.");
            }
            String description = required(
                    update.description(), "Description", 300);
            BigDecimal quantity = normalizeAmount(
                    update.quantity(), "Quantity", 8, true);
            BigDecimal unitPrice = normalizeAmount(
                    update.unitPrice(), "Unit price", 10, false);
            BigDecimal lineTotal = calculateInvoiceLineTotal(
                    quantity, unitPrice);
            updatedTotal = updatedTotal.add(lineTotal);
            updatesById.put(update.invoiceItemId(), new InvoiceItemUpdate(
                    update.invoiceItemId(), description, quantity, unitPrice));
        }
        updatedTotal = normalizeAmount(
                updatedTotal, "Invoice total", 10, false);

        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "FINANCE_INVOICE_CREATE");
                requireConfigured(connection);
                InvoiceSnapshot invoice = lockInvoice(connection, invoiceId);
                requireDraftInvoiceEditable(invoice.status,
                        paymentCount(connection, invoiceId));
                List<Long> lockedItemIds = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT invoice_item_id
                        FROM invoice_items
                        WHERE invoice_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, invoiceId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            lockedItemIds.add(
                                    resultSet.getLong("invoice_item_id"));
                        }
                    }
                }
                for (long itemId : lockedItemIds) {
                    InvoiceItemUpdate update = updatesById.remove(itemId);
                    if (update == null) {
                        throw new IllegalArgumentException(
                                "Invoice items changed. Refresh and retry.");
                    }
                    BigDecimal lineTotal = calculateInvoiceLineTotal(
                            update.quantity(), update.unitPrice());
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE invoice_items
                            SET description = ?, quantity = ?,
                                unit_price = ?, line_total = ?
                            WHERE invoice_item_id = ? AND invoice_id = ?
                            """)) {
                        statement.setString(1, update.description());
                        statement.setBigDecimal(2, update.quantity());
                        statement.setBigDecimal(3, update.unitPrice());
                        statement.setBigDecimal(4, lineTotal);
                        statement.setLong(5, itemId);
                        statement.setLong(6, invoiceId);
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalStateException(
                                    "An invoice item changed before it "
                                            + "could be updated.");
                        }
                    }
                }
                if (!updatesById.isEmpty()
                        || lockedItemIds.size() != itemUpdates.size()) {
                    throw new IllegalArgumentException(
                            "Invoice items changed. Refresh and retry.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE invoices
                        SET total_amount = ?, due_at = ?
                        WHERE invoice_id = ? AND invoice_status = 'DRAFT'
                        """)) {
                    statement.setBigDecimal(1, updatedTotal);
                    if (dueDate == null) {
                        statement.setNull(2, Types.TIMESTAMP);
                    } else {
                        statement.setTimestamp(2, Timestamp.valueOf(
                                dueDate.atTime(23, 59, 59)));
                    }
                    statement.setLong(3, invoiceId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Invoice state changed; refresh and try again.");
                    }
                }
                insertWorkflowEvent(connection, invoice.patientId,
                        invoiceAppointmentId(connection, invoiceId), invoiceId,
                        "DRAFT_INVOICE_UPDATED", userId,
                        "Draft invoice items and due date updated.");
                writeAudit(connection, userId, "FINANCE_DRAFT_INVOICE_UPDATED",
                        "Updated draft invoice " + invoiceId
                                + "; total recalculated from line items.");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public List<MethodSummary> loadPaymentMethods() throws SQLException {
        requireConfigured();
        String sql = """
                SELECT payment_method, SUM(amount) AS amount_total, COUNT(*) AS payment_count
                FROM payments
                WHERE payment_status = 'COMPLETED'
                GROUP BY payment_method
                ORDER BY amount_total DESC
                """;
        List<MethodSummary> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new MethodSummary(
                        resultSet.getString("payment_method"),
                        nz(resultSet.getBigDecimal("amount_total")),
                        resultSet.getInt("payment_count")));
            }
        }
        return rows;
    }

    public List<DailyRevenue> loadMonthlyRevenue() throws SQLException {
        requireConfigured();
        String sql = """
                SELECT TO_CHAR(received_at, 'DD Mon') AS revenue_day,
                       SUM(amount) AS amount_total
                FROM payments
                WHERE payment_status = 'COMPLETED'
                  AND received_at >= TRUNC(SYSDATE, 'MM')
                GROUP BY TO_CHAR(received_at, 'DD Mon'), TRUNC(received_at)
                ORDER BY TRUNC(received_at)
                """;
        List<DailyRevenue> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new DailyRevenue(
                        resultSet.getString("revenue_day"),
                        nz(resultSet.getBigDecimal("amount_total"))));
            }
        }
        return rows;
    }

    public String recordPayment(long invoiceId, BigDecimal amount,
                                String paymentMethod, String paymentReference,
                                String notes, long userId) throws SQLException {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        String method = required(paymentMethod, "Payment method", 24)
                .toUpperCase(Locale.ROOT);
        if (!List.of("CASH", "MOBILE_MONEY", "CARD", "BANK_TRANSFER").contains(method)) {
            throw new IllegalArgumentException("Unsupported payment method.");
        }
        String reference = optional(paymentReference);
        String normalizedNotes = optional(notes);
        checkLength(reference, "Payment reference", 120);
        checkLength(normalizedNotes, "Notes", 1000);

        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "FINANCE_PAYMENT_RECORD");
                requireConfigured(connection);
                InvoiceSnapshot invoice = lockInvoice(connection, invoiceId);
                BigDecimal total = invoice.total;
                BigDecimal paid = invoice.paid;
                String status = invoice.status;
                if ("VOID".equals(status) || "PAID".equals(status)) {
                    throw new IllegalArgumentException(
                            "This invoice cannot accept another payment.");
                }
                BigDecimal balance = total.subtract(paid);
                if (amount.compareTo(balance) > 0) {
                    throw new IllegalArgumentException(
                            "Payment exceeds the outstanding balance.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO payments (
                            invoice_id, amount, payment_method, payment_status,
                            payment_reference, received_by_user_id, notes
                        ) VALUES (?, ?, ?, 'COMPLETED', ?, ?, ?)
                        """)) {
                    statement.setLong(1, invoiceId);
                    statement.setBigDecimal(2, amount);
                    statement.setString(3, method);
                    if (reference == null) statement.setNull(4, Types.VARCHAR);
                    else statement.setString(4, reference);
                    statement.setLong(5, userId);
                    if (normalizedNotes == null) statement.setNull(6, Types.VARCHAR);
                    else statement.setString(6, normalizedNotes);
                    statement.executeUpdate();
                }
                BigDecimal newPaid = paid.add(amount);
                String newStatus = newPaid.compareTo(total) >= 0
                        ? "PAID" : "PARTIALLY_PAID";
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE invoices
                        SET invoice_status = ?
                        WHERE invoice_id = ?
                        """)) {
                    statement.setString(1, newStatus);
                    statement.setLong(2, invoiceId);
                    statement.executeUpdate();
                }
                long patientId = invoice.patientId;
                Long appointmentId = invoiceAppointmentId(connection, invoiceId);
                insertWorkflowEvent(connection, patientId, appointmentId,
                        invoiceId, "INVOICE_PAYMENT_RECORDED", userId,
                        "Payment " + amount.toPlainString() + " " + method
                                + " recorded for invoice " + invoiceId);
                writeAudit(connection, userId, "FINANCE_PAYMENT_RECORDED",
                        "Recorded " + amount.toPlainString() + " payment for "
                                + "invoice " + invoiceId);
                connection.commit();
                return newStatus;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void voidInvoice(long invoiceId, long userId) throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "FINANCE_INVOICE_CREATE");
                requireConfigured(connection);
                InvoiceSnapshot invoice = lockInvoice(connection, invoiceId);
                int paymentCount = paymentCount(connection, invoiceId);
                requireInvoiceVoidAllowed(invoice.status, paymentCount);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE invoices
                        SET invoice_status = 'VOID'
                        WHERE invoice_id = ?
                          AND invoice_status IN ('DRAFT', 'ISSUED')
                        """)) {
                    statement.setLong(1, invoiceId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Invoice state changed; refresh and try again.");
                    }
                }
                Long appointmentId = invoiceAppointmentId(connection, invoiceId);
                insertWorkflowEvent(connection, invoice.patientId, appointmentId,
                        invoiceId, "INVOICE_VOIDED", userId,
                        "Invoice " + invoiceId + " voided; record retained.");
                writeAudit(connection, userId, "FINANCE_INVOICE_VOIDED",
                        "Voided invoice " + invoiceId
                                + "; invoice retained for audit.");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void reversePayment(long paymentId, long userId) throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "FINANCE_PAYMENT_RECORD");
                requireConfigured(connection);
                long invoiceId = paymentInvoiceId(connection, paymentId);
                InvoiceSnapshot invoice = lockInvoice(connection, invoiceId);
                PaymentSnapshot payment = lockPayment(connection, paymentId);
                requirePaymentReversalAllowed(payment.status);
                if (payment.invoiceId != invoiceId) {
                    throw new IllegalStateException(
                            "Payment association changed; refresh and try again.");
                }
                BigDecimal paidAfterReversal = invoice.paid.subtract(payment.amount);
                if (paidAfterReversal.signum() < 0) {
                    throw new IllegalStateException(
                            "Payment totals are inconsistent; reversal was not applied.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE payments
                        SET payment_status = 'REVERSED'
                        WHERE payment_id = ? AND payment_status = 'COMPLETED'
                        """)) {
                    statement.setLong(1, paymentId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Payment state changed; refresh and try again.");
                    }
                }
                if (!"VOID".equalsIgnoreCase(invoice.status)) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE invoices
                            SET invoice_status = ?
                            WHERE invoice_id = ?
                            """)) {
                        statement.setString(1, invoiceStatusAfterReversal(
                                invoice.status, invoice.total, paidAfterReversal));
                        statement.setLong(2, invoiceId);
                        statement.executeUpdate();
                    }
                }
                Long appointmentId = invoiceAppointmentId(connection, invoiceId);
                insertPaymentWorkflowEvent(connection, invoice.patientId,
                        appointmentId, paymentId, userId,
                        "Payment " + paymentId + " reversed for invoice "
                                + invoiceId + "; amount and reference retained.");
                writeAudit(connection, userId, "FINANCE_PAYMENT_REVERSED",
                        "Reversed payment " + paymentId + " for invoice "
                                + invoiceId + "; amount and reference retained.");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private record InvoiceSnapshot(
            BigDecimal total, BigDecimal paid, String status, long patientId) {}

    private record PaymentSnapshot(
            long invoiceId, BigDecimal amount, String status) {}

    static void requireInvoiceVoidAllowed(String status, int paymentCount) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "ISSUED").contains(normalized)) {
            throw new IllegalArgumentException(
                    "Only draft or unpaid issued invoices can be voided.");
        }
        if (paymentCount != 0) {
            throw new IllegalArgumentException(
                    "Invoices with payment records cannot be voided.");
        }
    }

    static void requireDraftInvoiceEditable(String status, int paymentCount) {
        if (!"DRAFT".equalsIgnoreCase(
                status == null ? "" : status.trim())) {
            throw new IllegalArgumentException(
                    "Only draft invoices can be edited.");
        }
        if (paymentCount != 0) {
            throw new IllegalArgumentException(
                    "An invoice with payment history cannot be edited.");
        }
    }

    static BigDecimal calculateInvoiceLineTotal(BigDecimal quantity,
                                               BigDecimal unitPrice) {
        BigDecimal normalizedQuantity =
                normalizeAmount(quantity, "Quantity", 8, true);
        BigDecimal normalizedUnitPrice =
                normalizeAmount(unitPrice, "Unit price", 10, false);
        return normalizeAmount(normalizedQuantity.multiply(normalizedUnitPrice),
                "Line total", 10, false);
    }

    static void requirePaymentReversalAllowed(String status) {
        if (!"COMPLETED".equalsIgnoreCase(status == null ? "" : status.trim())) {
            throw new IllegalArgumentException(
                    "Only completed payments can be reversed.");
        }
    }

    static String invoiceStatusAfterReversal(String currentStatus,
                                            BigDecimal total,
                                            BigDecimal paidAfterReversal) {
        if ("VOID".equalsIgnoreCase(currentStatus)) return "VOID";
        if (paidAfterReversal.signum() > 0) {
            return paidAfterReversal.compareTo(total) >= 0
                    ? "PAID" : "PARTIALLY_PAID";
        }
        return "ISSUED";
    }

    private InvoiceSnapshot lockInvoice(Connection connection, long invoiceId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT i.total_amount, i.invoice_status, i.patient_id
                FROM invoices i
                WHERE i.invoice_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, invoiceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Invoice was not found.");
                }
                BigDecimal paid = BigDecimal.ZERO;
                try (PreparedStatement paidStatement = connection.prepareStatement("""
                        SELECT NVL(SUM(amount), 0) AS paid_amount
                        FROM payments
                        WHERE invoice_id = ? AND payment_status = 'COMPLETED'
                        """)) {
                    paidStatement.setLong(1, invoiceId);
                    try (ResultSet paidResult = paidStatement.executeQuery()) {
                        if (paidResult.next()) {
                            paid = nz(paidResult.getBigDecimal("paid_amount"));
                        }
                    }
                }
                return new InvoiceSnapshot(
                        nz(resultSet.getBigDecimal("total_amount")),
                        paid,
                        resultSet.getString("invoice_status"),
                        resultSet.getLong("patient_id"));
            }
        }
    }

    private int paymentCount(Connection connection, long invoiceId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM payments WHERE invoice_id = ?
                """)) {
            statement.setLong(1, invoiceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private long paymentInvoiceId(Connection connection, long paymentId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT invoice_id FROM payments WHERE payment_id = ?
                """)) {
            statement.setLong(1, paymentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Payment was not found.");
                }
                return resultSet.getLong("invoice_id");
            }
        }
    }

    private PaymentSnapshot lockPayment(Connection connection, long paymentId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT invoice_id, amount, payment_status
                FROM payments WHERE payment_id = ? FOR UPDATE
                """)) {
            statement.setLong(1, paymentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Payment was not found.");
                }
                return new PaymentSnapshot(resultSet.getLong("invoice_id"),
                        nz(resultSet.getBigDecimal("amount")),
                        resultSet.getString("payment_status"));
            }
        }
    }

    private Long invoiceAppointmentId(Connection connection, long invoiceId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT appointment_id FROM invoices WHERE invoice_id = ?
                """)) {
            statement.setLong(1, invoiceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                long value = resultSet.getLong("appointment_id");
                return resultSet.wasNull() ? null : value;
            }
        }
    }

    private void insertWorkflowEvent(Connection connection, long patientId,
                                     Long appointmentId, long invoiceId,
                                     String eventType, long userId,
                                     String details) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workflow_events (
                    patient_id, appointment_id, entity_type, entity_id,
                    event_type, department_name, performed_by_user_id, details
                ) VALUES (?, ?, 'INVOICE', ?, ?, 'Finance', ?, ?)
                """)) {
            statement.setLong(1, patientId);
            if (appointmentId == null) statement.setNull(2, Types.NUMERIC);
            else statement.setLong(2, appointmentId);
            statement.setLong(3, invoiceId);
            statement.setString(4, eventType);
            statement.setLong(5, userId);
            statement.setString(6, details);
            statement.executeUpdate();
        }
    }

    private void insertPaymentWorkflowEvent(Connection connection,
                                            long patientId,
                                            Long appointmentId,
                                            long paymentId,
                                            long userId,
                                            String details) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workflow_events (
                    patient_id, appointment_id, entity_type, entity_id,
                    event_type, department_name, performed_by_user_id, details
                ) VALUES (?, ?, 'PAYMENT', ?, 'PAYMENT_REVERSED',
                          'Finance', ?, ?)
                """)) {
            statement.setLong(1, patientId);
            if (appointmentId == null) statement.setNull(2, Types.NUMERIC);
            else statement.setLong(2, appointmentId);
            statement.setLong(3, paymentId);
            statement.setLong(4, userId);
            statement.setString(5, details);
            statement.executeUpdate();
        }
    }

    private void writeAudit(Connection connection, long userId,
                            String action, String details) throws SQLException {
        if (OracleSchemaInspector.columnExists(connection, "AUDIT_LOGS", "DESCRIPTION")) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO audit_logs (user_id, action, description)
                    VALUES (?, ?, ?)
                    """)) {
                statement.setLong(1, userId);
                statement.setString(2, action);
                statement.setString(3, details);
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO audit_logs (user_id, action, details)
                    VALUES (?, ?, ?)
                    """)) {
                statement.setLong(1, userId);
                statement.setString(2, action);
                statement.setString(3, details);
                statement.executeUpdate();
            }
        }
    }

    private void requirePermission(Connection connection, long userId,
                                   String permission) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM users u
                JOIN role_permissions rp ON rp.role_id = u.role_id
                WHERE u.user_id = ?
                  AND u.is_active = 'Y'
                  AND rp.permission_code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, permission);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) != 1) {
                    throw new SecurityException(
                            "You do not have permission for this finance action.");
                }
            }
        }
    }

    private void requireConfigured() throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireConfigured(connection);
        }
    }

    private void requireConfigured(Connection connection) throws SQLException {
        if (!OracleSchemaInspector.tableExists(connection, "INVOICES")
                || !OracleSchemaInspector.tableExists(connection, "INVOICE_ITEMS")
                || !OracleSchemaInspector.tableExists(connection, "PAYMENTS")) {
            throw new IllegalStateException(NOT_CONFIGURED);
        }
    }

    private String patientNameExpression() throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            if (OracleSchemaInspector.columnExists(connection, "PATIENTS", "FULL_NAME")) {
                return "p.full_name";
            }
            if (OracleSchemaInspector.columnExists(connection, "PATIENTS", "FIRST_NAME")
                    && OracleSchemaInspector.columnExists(connection, "PATIENTS", "LAST_NAME")) {
                return "TRIM(p.first_name || ' ' || p.last_name)";
            }
            throw new IllegalStateException("Patient name fields are not configured.");
        }
    }

    private static String optional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = optional(value);
        if (normalized == null) throw new IllegalArgumentException(field + " is required.");
        checkLength(normalized, field, maxLength);
        return normalized;
    }

    private static void checkLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be " + maxLength
                    + " characters or fewer.");
        }
    }

    private static BigDecimal normalizeAmount(BigDecimal value, String field,
                                              int maxIntegerDigits,
                                              boolean strictlyPositive) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (strictlyPositive ? value.signum() <= 0 : value.signum() < 0) {
            throw new IllegalArgumentException(strictlyPositive
                    ? field + " must be greater than zero."
                    : field + " cannot be negative.");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    field + " can have no more than 2 decimal places.",
                    exception);
        }
        if (normalized.precision() - normalized.scale() > maxIntegerDigits) {
            throw new IllegalArgumentException(
                    field + " is too large.");
        }
        return normalized;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String toLower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static void rollback(Connection connection, Exception exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }
}
