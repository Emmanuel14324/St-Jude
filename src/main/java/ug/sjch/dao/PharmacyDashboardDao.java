package ug.sjch.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PharmacyDashboardDao {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy");

    private static final String STOCK_CTES = """
            WITH dispensed AS (
                SELECT batch_id, SUM(quantity) AS dispensed_quantity
                FROM dispensing_items
                GROUP BY batch_id
            ),
            adjustments AS (
                SELECT batch_id,
                       SUM(CASE
                           WHEN transaction_type IN ('ADJUSTMENT', 'RETURN')
                               THEN quantity_delta
                           WHEN transaction_type = 'EXPIRED'
                               THEN -ABS(quantity_delta)
                           ELSE 0
                       END) AS adjustment_quantity
                FROM inventory_transactions
                GROUP BY batch_id
            ),
            batch_stock AS (
                SELECT mb.batch_id,
                       mb.medicine_id,
                       mb.batch_number,
                       mb.expiry_date,
                       GREATEST(
                           mb.quantity_received
                           - NVL(d.dispensed_quantity, 0)
                           + NVL(a.adjustment_quantity, 0),
                           0
                       ) AS quantity_on_hand
                FROM medicine_batches mb
                LEFT JOIN dispensed d ON d.batch_id = mb.batch_id
                LEFT JOIN adjustments a ON a.batch_id = mb.batch_id
            ),
            medicine_stock AS (
                SELECT medicine_id,
                       SUM(CASE
                           WHEN expiry_date IS NULL
                             OR expiry_date >= TRUNC(SYSDATE)
                               THEN quantity_on_hand
                           ELSE 0
                       END) AS total_on_hand
                FROM batch_stock
                GROUP BY medicine_id
            )
            """;

    public enum InventoryFilter {
        ALL,
        LOW_STOCK,
        EXPIRING
    }

    public record PharmacyMetrics(
            int totalMedicines,
            int lowStockMedicines,
            int outOfStockMedicines,
            int expiringMedicines,
            int pendingPrescriptions,
            int todaysDispensings
    ) {
    }

    public record CategorySummary(
            String category,
            int medicineCount,
            String totalStock,
            int lowStockCount
    ) {
    }

    public record MedicineBatchRow(
            long batchId,
            long medicineId,
            String medicineName,
            String category,
            String quantityOnHand,
            String batchNumber,
            String expiryDate,
            String status,
            Long supplierId,
            String supplierName,
            BigDecimal unitCost,
            BigDecimal sellingPrice,
            LocalDate expiryDateValue
    ) {
    }

    public record MedicineCatalogRow(
            long medicineId,
            String name,
            String genericName,
            String strength,
            String dosageForm,
            String category,
            BigDecimal reorderLevel,
            String status
    ) {
    }

    public record MedicineOption(long medicineId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record SupplierRow(
            long supplierId,
            String name,
            String contactName,
            String phone,
            String email,
            String address,
            String status
    ) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record PrescriptionRow(
            long prescriptionId,
            long patientId,
            String patientNumber,
            String patientName,
            String prescribedBy,
            String createdAt,
            String status
    ) {
    }

    public record PrescriptionItemRow(
            long prescriptionItemId,
            long medicineId,
            String medicineName,
            String dosage,
            String route,
            String frequency,
            String instructions,
            BigDecimal remainingQuantity,
            BigDecimal availableQuantity
    ) {
    }

    public record InventoryTransactionRow(
            String recordedAt,
            String medicineName,
            String batchNumber,
            String transactionType,
            String quantity,
            String recordedBy,
            String notes
    ) {
    }

    public record StockReportRow(
            String medicineName,
            String stockOnHand,
            String reorderLevel,
            String status
    ) {
    }

    public PharmacyMetrics loadMetrics() throws SQLException {
        String sql = STOCK_CTES + """
                SELECT
                    (SELECT COUNT(*)
                     FROM medicines
                     WHERE is_active = 'Y') AS total_medicines,
                    (SELECT COUNT(*)
                     FROM medicines m
                     LEFT JOIN medicine_stock ms
                       ON ms.medicine_id = m.medicine_id
                     WHERE m.is_active = 'Y'
                       AND NVL(ms.total_on_hand, 0) > 0
                       AND NVL(ms.total_on_hand, 0) <= m.reorder_level
                    ) AS low_stock_medicines,
                    (SELECT COUNT(*)
                     FROM medicines m
                     LEFT JOIN medicine_stock ms
                       ON ms.medicine_id = m.medicine_id
                     WHERE m.is_active = 'Y'
                       AND NVL(ms.total_on_hand, 0) <= 0
                    ) AS out_of_stock_medicines,
                    (SELECT COUNT(DISTINCT bs.medicine_id)
                     FROM batch_stock bs
                     JOIN medicines m ON m.medicine_id = bs.medicine_id
                     WHERE bs.quantity_on_hand > 0
                       AND m.is_active = 'Y'
                       AND bs.expiry_date <= TRUNC(SYSDATE) + 30
                    ) AS expiring_medicines,
                    (SELECT COUNT(*)
                     FROM prescriptions
                     WHERE prescription_status IN (
                         'PENDING', 'PARTIALLY_DISPENSED'
                     )
                    ) AS pending_prescriptions,
                    (SELECT COUNT(*)
                     FROM dispensings
                     WHERE TRUNC(dispensed_at) = TRUNC(SYSDATE)
                    ) AS todays_dispensings
                FROM dual
                """;

        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException(
                        "Pharmacy dashboard metrics returned no row.");
            }
            return new PharmacyMetrics(
                    resultSet.getInt("total_medicines"),
                    resultSet.getInt("low_stock_medicines"),
                    resultSet.getInt("out_of_stock_medicines"),
                    resultSet.getInt("expiring_medicines"),
                    resultSet.getInt("pending_prescriptions"),
                    resultSet.getInt("todays_dispensings"));
        }
    }

    public List<CategorySummary> loadCategorySummary() throws SQLException {
        String sql = STOCK_CTES + """
                SELECT NVL(TRIM(m.category), 'Uncategorized') AS category,
                       COUNT(*) AS medicine_count,
                       NVL(SUM(NVL(ms.total_on_hand, 0)), 0) AS total_stock,
                       SUM(CASE
                           WHEN NVL(ms.total_on_hand, 0) > 0
                            AND NVL(ms.total_on_hand, 0) <= m.reorder_level
                               THEN 1
                           ELSE 0
                       END) AS low_stock_count
                FROM medicines m
                LEFT JOIN medicine_stock ms
                  ON ms.medicine_id = m.medicine_id
                WHERE m.is_active = 'Y'
                GROUP BY NVL(TRIM(m.category), 'Uncategorized')
                ORDER BY category
                """;
        List<CategorySummary> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new CategorySummary(
                        resultSet.getString("category"),
                        resultSet.getInt("medicine_count"),
                        formatQuantity(resultSet.getBigDecimal("total_stock")),
                        resultSet.getInt("low_stock_count")));
            }
        }
        return rows;
    }

    public List<MedicineBatchRow> loadInventory(
            String search, InventoryFilter filter) throws SQLException {
        String normalizedSearch = optional(search);
        String filterSql = switch (filter) {
            case ALL -> "";
            case LOW_STOCK -> """
                    AND NVL(ms.total_on_hand, 0) > 0
                    AND NVL(ms.total_on_hand, 0) <= m.reorder_level
                    """;
            case EXPIRING -> """
                    AND bs.quantity_on_hand > 0
                    AND bs.expiry_date <= TRUNC(SYSDATE) + 30
                    """;
        };
        String searchSql = normalizedSearch == null ? "" : """
                  AND (
                      UPPER(m.medicine_name) LIKE UPPER(?)
                      OR UPPER(NVL(m.generic_name, ' ')) LIKE UPPER(?)
                      OR UPPER(NVL(bs.batch_number, ' ')) LIKE UPPER(?)
                  )
                """;
        String sql = STOCK_CTES + """
                SELECT bs.batch_id,
                       m.medicine_id,
                       m.medicine_name,
                       m.category,
                       bs.quantity_on_hand,
                       bs.batch_number,
                       bs.expiry_date,
                       mb.supplier_id,
                       s.supplier_name,
                       mb.unit_cost,
                       mb.selling_price,
                       CASE
                           WHEN bs.quantity_on_hand <= 0 THEN 'Out of stock'
                           WHEN bs.expiry_date < TRUNC(SYSDATE)
                               THEN 'Expired'
                           WHEN bs.expiry_date <= TRUNC(SYSDATE) + 30
                               THEN 'Expiring'
                           WHEN NVL(ms.total_on_hand, 0) <= m.reorder_level
                               THEN 'Low stock'
                           ELSE 'In stock'
                       END AS stock_status
                FROM batch_stock bs
                JOIN medicines m ON m.medicine_id = bs.medicine_id
                JOIN medicine_batches mb ON mb.batch_id = bs.batch_id
                LEFT JOIN suppliers s ON s.supplier_id = mb.supplier_id
                LEFT JOIN medicine_stock ms
                  ON ms.medicine_id = m.medicine_id
                WHERE m.is_active = 'Y'
                %s
                %s
                ORDER BY m.medicine_name, bs.expiry_date NULLS LAST,
                         bs.batch_number
                FETCH FIRST 100 ROWS ONLY
                """.formatted(searchSql, filterSql);
        List<MedicineBatchRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (normalizedSearch != null) {
                String searchPattern = "%" + normalizedSearch + "%";
                statement.setString(1, searchPattern);
                statement.setString(2, searchPattern);
                statement.setString(3, searchPattern);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    BigDecimal quantity =
                            resultSet.getBigDecimal("quantity_on_hand");
                    java.sql.Date expiry =
                            resultSet.getDate("expiry_date");
                    long rawSupplierId = resultSet.getLong("supplier_id");
                    Long supplierId = resultSet.wasNull()
                            ? null : rawSupplierId;
                    rows.add(new MedicineBatchRow(
                            resultSet.getLong("batch_id"),
                            resultSet.getLong("medicine_id"),
                            resultSet.getString("medicine_name"),
                            resultSet.getString("category"),
                            formatQuantity(quantity),
                            resultSet.getString("batch_number"),
                            expiry == null ? "Not recorded"
                                    : expiry.toLocalDate().format(DATE_FORMAT),
                            resultSet.getString("stock_status"),
                            supplierId,
                            resultSet.getString("supplier_name"),
                            resultSet.getBigDecimal("unit_cost"),
                            resultSet.getBigDecimal("selling_price"),
                            expiry == null ? null : expiry.toLocalDate()));
                }
            }
        }
        return rows;
    }

    public List<MedicineCatalogRow> loadMedicineCatalog()
            throws SQLException {
        String sql = """
                SELECT medicine_id, medicine_name, generic_name, strength,
                       dosage_form, category, reorder_level, is_active
                FROM medicines
                ORDER BY CASE WHEN is_active = 'Y' THEN 0 ELSE 1 END,
                         medicine_name
                FETCH FIRST 500 ROWS ONLY
                """;
        List<MedicineCatalogRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new MedicineCatalogRow(
                        resultSet.getLong("medicine_id"),
                        resultSet.getString("medicine_name"),
                        resultSet.getString("generic_name"),
                        resultSet.getString("strength"),
                        resultSet.getString("dosage_form"),
                        resultSet.getString("category"),
                        resultSet.getBigDecimal("reorder_level"),
                        "Y".equalsIgnoreCase(
                                resultSet.getString("is_active"))
                                ? "Active" : "Archived"));
            }
        }
        return rows;
    }

    public List<MedicineOption> loadMedicines() throws SQLException {
        String sql = """
                SELECT medicine_id, medicine_name, generic_name, strength
                FROM medicines
                WHERE is_active = 'Y'
                ORDER BY medicine_name, strength
                """;
        List<MedicineOption> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String label = resultSet.getString("medicine_name");
                String strength = resultSet.getString("strength");
                if (strength != null && !strength.isBlank()) {
                    label += " " + strength;
                }
                String generic = resultSet.getString("generic_name");
                if (generic != null && !generic.isBlank()) {
                    label += " (" + generic + ")";
                }
                rows.add(new MedicineOption(
                        resultSet.getLong("medicine_id"), label));
            }
        }
        return rows;
    }

    public List<SupplierRow> loadSuppliers(String searchText)
            throws SQLException {
        String search = optional(searchText);
        String sql = """
                SELECT supplier_id, supplier_name, contact_name, phone,
                       email, address, is_active
                FROM suppliers
                %s
                ORDER BY supplier_name
                FETCH FIRST 200 ROWS ONLY
                """.formatted(search == null ? "" : """
                WHERE LOWER(supplier_name) LIKE LOWER(?)
                   OR LOWER(NVL(contact_name, ' ')) LIKE LOWER(?)
                   OR LOWER(NVL(phone, ' ')) LIKE LOWER(?)
                   OR LOWER(NVL(email, ' ')) LIKE LOWER(?)
                """);
        List<SupplierRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (search != null) {
                String pattern = "%" + search + "%";
                for (int index = 1; index <= 4; index++) {
                    statement.setString(index, pattern);
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SupplierRow(
                            resultSet.getLong("supplier_id"),
                            resultSet.getString("supplier_name"),
                            resultSet.getString("contact_name"),
                            resultSet.getString("phone"),
                            resultSet.getString("email"),
                            resultSet.getString("address"),
                            "Y".equalsIgnoreCase(
                                    resultSet.getString("is_active"))
                                    ? "Active" : "Inactive"));
                }
            }
        }
        return rows;
    }

    public List<PrescriptionRow> loadPendingPrescriptions()
            throws SQLException {
        String sql = """
                SELECT p.prescription_id, p.patient_id, pt.patient_number,
                       TRIM(pt.first_name || ' ' || pt.last_name)
                           AS patient_name,
                       TRIM(s.first_name || ' ' || s.last_name)
                           AS prescribed_by,
                       TO_CHAR(p.created_at, 'DD Mon YYYY HH:MI AM')
                           AS created_at,
                       p.prescription_status
                FROM prescriptions p
                JOIN patients pt ON pt.patient_id = p.patient_id
                JOIN staff s ON s.staff_id = p.prescribed_by_staff_id
                WHERE p.prescription_status IN (
                    'PENDING', 'PARTIALLY_DISPENSED'
                )
                ORDER BY p.created_at, p.prescription_id
                FETCH FIRST 200 ROWS ONLY
                """;
        List<PrescriptionRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new PrescriptionRow(
                        resultSet.getLong("prescription_id"),
                        resultSet.getLong("patient_id"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("prescribed_by"),
                        resultSet.getString("created_at"),
                        resultSet.getString("prescription_status")));
            }
        }
        return rows;
    }

    public List<PrescriptionItemRow> loadPrescriptionItems(
            long prescriptionId) throws SQLException {
        String sql = STOCK_CTES + """
                SELECT pi.prescription_item_id, pi.medicine_id,
                       m.medicine_name, pi.dosage, pi.route, pi.frequency,
                       pi.instructions,
                       pi.quantity_prescribed - pi.quantity_dispensed
                           AS remaining_quantity,
                       NVL(ms.total_on_hand, 0) AS available_quantity
                FROM prescription_items pi
                JOIN medicines m ON m.medicine_id = pi.medicine_id
                LEFT JOIN medicine_stock ms
                  ON ms.medicine_id = pi.medicine_id
                WHERE pi.prescription_id = ?
                  AND pi.quantity_dispensed < pi.quantity_prescribed
                ORDER BY pi.prescription_item_id
                """;
        List<PrescriptionItemRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, prescriptionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new PrescriptionItemRow(
                            resultSet.getLong("prescription_item_id"),
                            resultSet.getLong("medicine_id"),
                            resultSet.getString("medicine_name"),
                            resultSet.getString("dosage"),
                            resultSet.getString("route"),
                            resultSet.getString("frequency"),
                            resultSet.getString("instructions"),
                            resultSet.getBigDecimal("remaining_quantity"),
                            resultSet.getBigDecimal("available_quantity")));
                }
            }
        }
        return rows;
    }

    public List<InventoryTransactionRow> loadInventoryTransactions()
            throws SQLException {
        String sql = """
                SELECT TO_CHAR(t.recorded_at, 'DD Mon YYYY HH:MI AM')
                           AS recorded_at,
                       m.medicine_name, b.batch_number, t.transaction_type,
                       t.quantity_delta, u.username, t.notes
                FROM inventory_transactions t
                JOIN medicine_batches b ON b.batch_id = t.batch_id
                JOIN medicines m ON m.medicine_id = b.medicine_id
                JOIN users u ON u.user_id = t.recorded_by_user_id
                ORDER BY t.recorded_at DESC, t.inventory_transaction_id DESC
                FETCH FIRST 200 ROWS ONLY
                """;
        List<InventoryTransactionRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new InventoryTransactionRow(
                        resultSet.getString("recorded_at"),
                        resultSet.getString("medicine_name"),
                        resultSet.getString("batch_number"),
                        resultSet.getString("transaction_type"),
                        formatQuantity(
                                resultSet.getBigDecimal("quantity_delta")),
                        resultSet.getString("username"),
                        resultSet.getString("notes")));
            }
        }
        return rows;
    }

    public List<StockReportRow> loadStockReport() throws SQLException {
        String sql = STOCK_CTES + """
                SELECT m.medicine_name,
                       NVL(ms.total_on_hand, 0) AS stock_on_hand,
                       m.reorder_level,
                       CASE
                           WHEN NVL(ms.total_on_hand, 0) <= 0
                               THEN 'Out of stock'
                           WHEN NVL(ms.total_on_hand, 0) <= m.reorder_level
                               THEN 'Low stock'
                           ELSE 'In stock'
                       END AS stock_status
                FROM medicines m
                LEFT JOIN medicine_stock ms
                  ON ms.medicine_id = m.medicine_id
                WHERE m.is_active = 'Y'
                ORDER BY CASE
                             WHEN NVL(ms.total_on_hand, 0) <= 0 THEN 1
                             WHEN NVL(ms.total_on_hand, 0) <= m.reorder_level
                                 THEN 2
                             ELSE 3
                         END,
                         m.medicine_name
                FETCH FIRST 500 ROWS ONLY
                """;
        List<StockReportRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new StockReportRow(
                        resultSet.getString("medicine_name"),
                        formatQuantity(
                                resultSet.getBigDecimal("stock_on_hand")),
                        formatQuantity(
                                resultSet.getBigDecimal("reorder_level")),
                        resultSet.getString("stock_status")));
            }
        }
        return rows;
    }

    public void createMedicine(String name, String genericName,
                               String strength, String dosageForm,
                               String category, BigDecimal reorderLevel,
                               long userId) throws SQLException {
        String medicineName = required(name, "Medicine name", 160);
        String generic = optional(genericName);
        String normalizedStrength = optional(strength);
        String normalizedForm = optional(dosageForm);
        String normalizedCategory = optional(category);
        checkLength(generic, "Generic name", 160);
        checkLength(normalizedStrength, "Strength", 80);
        checkLength(normalizedForm, "Dosage form", 80);
        checkLength(normalizedCategory, "Category", 100);
        if (reorderLevel == null || reorderLevel.signum() < 0) {
            throw new IllegalArgumentException(
                    "Reorder level must be zero or greater.");
        }
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "PHARMACY_INVENTORY_MANAGE");
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO medicines (
                            medicine_name, generic_name, strength, dosage_form,
                            category, reorder_level, is_active
                        ) VALUES (?, ?, ?, ?, ?, ?, 'Y')
                        """)) {
                    statement.setString(1, medicineName);
                    statement.setString(2, generic);
                    statement.setString(3, normalizedStrength);
                    statement.setString(4, normalizedForm);
                    statement.setString(5, normalizedCategory);
                    statement.setBigDecimal(6, reorderLevel);
                    statement.executeUpdate();
                }
                writeAudit(connection, userId, "CREATE_MEDICINE",
                        "Created medicine record: " + medicineName);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void createSupplier(String name, String contactName, String phone,
                               String email, String address, long userId)
            throws SQLException {
        String supplierName = required(name, "Supplier name", 160);
        String contact = optional(contactName);
        String supplierPhone = optional(phone);
        String supplierEmail = optional(email);
        String supplierAddress = optional(address);
        checkLength(contact, "Contact name", 120);
        checkLength(supplierPhone, "Phone", 40);
        checkLength(supplierEmail, "Email", 200);
        checkLength(supplierAddress, "Address", 500);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "PHARMACY_INVENTORY_MANAGE");
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO suppliers (
                            supplier_name, contact_name, phone, email, address,
                            is_active
                        ) VALUES (?, ?, ?, ?, ?, 'Y')
                        """)) {
                    statement.setString(1, supplierName);
                    statement.setString(2, contact);
                    statement.setString(3, supplierPhone);
                    statement.setString(4, supplierEmail);
                    statement.setString(5, supplierAddress);
                    statement.executeUpdate();
                }
                writeAudit(connection, userId, "CREATE_SUPPLIER",
                        "Created supplier: " + supplierName);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateMedicine(long medicineId, String name,
                              String genericName, String strength,
                              String dosageForm, String category,
                              BigDecimal reorderLevel, long userId)
            throws SQLException {
        String medicineName = required(name, "Medicine name", 160);
        String generic = optional(genericName);
        String normalizedStrength = optional(strength);
        String normalizedForm = optional(dosageForm);
        String normalizedCategory = optional(category);
        checkLength(generic, "Generic name", 160);
        checkLength(normalizedStrength, "Strength", 80);
        checkLength(normalizedForm, "Dosage form", 80);
        checkLength(normalizedCategory, "Category", 100);
        validateReorderLevel(reorderLevel);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "PHARMACY_INVENTORY_MANAGE");
                lockMedicine(connection, medicineId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE medicines
                        SET medicine_name = ?, generic_name = ?, strength = ?,
                            dosage_form = ?, category = ?, reorder_level = ?
                        WHERE medicine_id = ?
                        """)) {
                    statement.setString(1, medicineName);
                    statement.setString(2, generic);
                    statement.setString(3, normalizedStrength);
                    statement.setString(4, normalizedForm);
                    statement.setString(5, normalizedCategory);
                    statement.setBigDecimal(6, reorderLevel);
                    statement.setLong(7, medicineId);
                    requireUpdated(statement.executeUpdate(), "Medicine");
                }
                writeAudit(connection, userId, "UPDATE_MEDICINE",
                        "Updated medicine record " + medicineId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void archiveMedicine(long medicineId, long userId)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "PHARMACY_INVENTORY_MANAGE");
                lockMedicine(connection, medicineId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE medicines
                        SET is_active = 'N'
                        WHERE medicine_id = ? AND is_active = 'Y'
                        """)) {
                    statement.setLong(1, medicineId);
                    requireUpdated(statement.executeUpdate(), "Active medicine");
                }
                writeAudit(connection, userId, "ARCHIVE_MEDICINE",
                        "Archived medicine record " + medicineId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateSupplier(long supplierId, String name,
                              String contactName, String phone, String email,
                              String address, long userId) throws SQLException {
        String supplierName = required(name, "Supplier name", 160);
        String contact = optional(contactName);
        String supplierPhone = optional(phone);
        String supplierEmail = optional(email);
        String supplierAddress = optional(address);
        checkLength(contact, "Contact name", 120);
        checkLength(supplierPhone, "Phone", 40);
        checkLength(supplierEmail, "Email", 200);
        checkLength(supplierAddress, "Address", 500);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "PHARMACY_INVENTORY_MANAGE");
                lockSupplier(connection, supplierId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE suppliers
                        SET supplier_name = ?, contact_name = ?, phone = ?,
                            email = ?, address = ?
                        WHERE supplier_id = ?
                        """)) {
                    statement.setString(1, supplierName);
                    statement.setString(2, contact);
                    statement.setString(3, supplierPhone);
                    statement.setString(4, supplierEmail);
                    statement.setString(5, supplierAddress);
                    statement.setLong(6, supplierId);
                    requireUpdated(statement.executeUpdate(), "Supplier");
                }
                writeAudit(connection, userId, "UPDATE_SUPPLIER",
                        "Updated supplier record " + supplierId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void archiveSupplier(long supplierId, long userId)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "PHARMACY_INVENTORY_MANAGE");
                lockSupplier(connection, supplierId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE suppliers
                        SET is_active = 'N'
                        WHERE supplier_id = ? AND is_active = 'Y'
                        """)) {
                    statement.setLong(1, supplierId);
                    requireUpdated(statement.executeUpdate(), "Active supplier");
                }
                writeAudit(connection, userId, "ARCHIVE_SUPPLIER",
                        "Archived supplier record " + supplierId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateBatchMetadata(long batchId, Long supplierId,
                                    String batchNumber, BigDecimal unitCost,
                                    BigDecimal sellingPrice, LocalDate expiryDate,
                                    long userId) throws SQLException {
        String normalizedBatchNumber =
                required(batchNumber, "Batch number", 80);
        if (unitCost == null || unitCost.signum() < 0
                || sellingPrice == null || sellingPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Unit cost and selling price must be zero or greater.");
        }
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "PHARMACY_INVENTORY_MANAGE");
                Long currentSupplierId =
                        lockBatchSupplier(connection, batchId);
                if (supplierId != null
                        && !supplierId.equals(currentSupplierId)) {
                    requireActiveSupplier(connection, supplierId);
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE medicine_batches
                        SET supplier_id = ?, batch_number = ?, unit_cost = ?,
                            selling_price = ?, expiry_date = ?
                        WHERE batch_id = ?
                        """)) {
                    if (supplierId == null) {
                        statement.setNull(1, Types.NUMERIC);
                    } else {
                        statement.setLong(1, supplierId);
                    }
                    statement.setString(2, normalizedBatchNumber);
                    statement.setBigDecimal(3, unitCost);
                    statement.setBigDecimal(4, sellingPrice);
                    if (expiryDate == null) {
                        statement.setNull(5, Types.DATE);
                    } else {
                        statement.setDate(5, Date.valueOf(expiryDate));
                    }
                    statement.setLong(6, batchId);
                    requireUpdated(statement.executeUpdate(), "Medicine batch");
                }
                writeAudit(connection, userId, "UPDATE_MEDICINE_BATCH",
                        "Updated metadata for medicine batch " + batchId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void receiveStock(long medicineId, Long supplierId,
                             String batchNumber, BigDecimal quantity,
                             BigDecimal unitCost, BigDecimal sellingPrice,
                             LocalDate expiryDate, String notes, long userId)
            throws SQLException {
        String normalizedBatchNumber =
                required(batchNumber, "Batch number", 80);
        String normalizedNotes = optional(notes);
        checkLength(normalizedNotes, "Notes", 1000);
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Received quantity must be greater than zero.");
        }
        if (unitCost == null || unitCost.signum() < 0
                || sellingPrice == null || sellingPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Unit cost and selling price must be zero or greater.");
        }
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Received medicine cannot already be expired.");
        }
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "PHARMACY_INVENTORY_MANAGE");
                lockActiveMedicine(connection, medicineId);
                if (supplierId != null) {
                    requireActiveSupplier(connection, supplierId);
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO medicine_batches (
                            medicine_id, supplier_id, batch_number,
                            quantity_received, unit_cost, selling_price,
                            expiry_date
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, medicineId);
                    if (supplierId == null) {
                        statement.setNull(2, Types.NUMERIC);
                    } else {
                        statement.setLong(2, supplierId);
                    }
                    statement.setString(3, normalizedBatchNumber);
                    statement.setBigDecimal(4, quantity);
                    statement.setBigDecimal(5, unitCost);
                    statement.setBigDecimal(6, sellingPrice);
                    if (expiryDate == null) {
                        statement.setNull(7, Types.DATE);
                    } else {
                        statement.setDate(7, Date.valueOf(expiryDate));
                    }
                    statement.executeUpdate();
                }
                long batchId = findBatchId(connection, medicineId,
                        normalizedBatchNumber);
                insertInventoryTransaction(connection, batchId, "RECEIPT",
                        quantity, null, userId, normalizedNotes);
                writeAudit(connection, userId, "RECEIVE_MEDICINE_STOCK",
                        "Received batch " + normalizedBatchNumber);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void adjustStock(long batchId, BigDecimal quantityDelta,
                            String notes, long userId) throws SQLException {
        String normalizedNotes = required(notes, "Adjustment reason", 1000);
        if (quantityDelta == null || quantityDelta.signum() == 0) {
            throw new IllegalArgumentException(
                    "Stock adjustment cannot be zero.");
        }
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "PHARMACY_INVENTORY_MANAGE");
                BigDecimal receivedQuantity = lockBatch(connection, batchId);
                BigDecimal currentQuantity =
                        getBatchQuantityOnHand(connection, batchId,
                                receivedQuantity);
                if (currentQuantity.add(quantityDelta).signum() < 0) {
                    throw new IllegalArgumentException(
                            "Adjustment would make stock negative.");
                }
                insertInventoryTransaction(connection, batchId, "ADJUSTMENT",
                        quantityDelta, null, userId, normalizedNotes);
                writeAudit(connection, userId, "ADJUST_MEDICINE_STOCK",
                        "Adjusted batch " + batchId + " by "
                                + quantityDelta.toPlainString());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public String dispensePrescription(long prescriptionId,
                                       Map<Long, BigDecimal> quantities,
                                       long userId)
            throws SQLException {
        if (quantities == null || quantities.isEmpty()) {
            throw new IllegalArgumentException(
                    "Enter a quantity for at least one prescription item.");
        }
        Map<Long, BigDecimal> requestedQuantities =
                new TreeMap<>(quantities);
        for (BigDecimal quantity : requestedQuantities.values()) {
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Dispensed quantities must be greater than zero.");
            }
        }
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "PHARMACY_DISPENSE");
                long patientId = lockPendingPrescription(
                        connection, prescriptionId);
                long dispensingId = insertDispensing(
                        connection, prescriptionId, userId);
                for (Map.Entry<Long, BigDecimal> entry
                        : requestedQuantities.entrySet()) {
                    dispensePrescriptionItem(connection, prescriptionId,
                            entry.getKey(), entry.getValue(), dispensingId,
                            userId);
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE prescriptions
                        SET prescription_status = CASE
                            WHEN EXISTS (
                                SELECT 1
                                FROM prescription_items pi
                                WHERE pi.prescription_id = prescriptions.prescription_id
                                  AND pi.quantity_dispensed
                                      < pi.quantity_prescribed
                            ) THEN 'PARTIALLY_DISPENSED'
                            ELSE 'DISPENSED'
                        END
                        WHERE prescription_id = ?
                        """)) {
                    statement.setLong(1, prescriptionId);
                    statement.executeUpdate();
                }
                String status;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT prescription_status
                        FROM prescriptions
                        WHERE prescription_id = ?
                        """)) {
                    statement.setLong(1, prescriptionId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SQLException(
                                    "Updated prescription could not be loaded.");
                        }
                        status = resultSet.getString("prescription_status");
                    }
                }
                insertWorkflowEvent(connection, patientId,
                        "PRESCRIPTION", prescriptionId,
                        "PRESCRIPTION_"
                                + ("DISPENSED".equals(status)
                                ? "DISPENSED" : "PARTIALLY_DISPENSED"),
                        userId, "Pharmacy",
                        "Prescription updated by pharmacy");
                writeAudit(connection, userId, "DISPENSE_PRESCRIPTION",
                        "Dispensed prescription " + prescriptionId);
                connection.commit();
                return status;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private void dispensePrescriptionItem(Connection connection,
                                          long prescriptionId,
                                          long prescriptionItemId,
                                          BigDecimal quantity,
                                          long dispensingId, long userId)
            throws SQLException {
        long medicineId;
        BigDecimal remaining;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT medicine_id, quantity_prescribed - quantity_dispensed
                           AS remaining
                FROM prescription_items
                WHERE prescription_id = ?
                  AND prescription_item_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, prescriptionId);
            statement.setLong(2, prescriptionItemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Prescription item does not belong to this prescription.");
                }
                medicineId = resultSet.getLong("medicine_id");
                remaining = resultSet.getBigDecimal("remaining");
            }
        }
        lockActiveMedicine(connection, medicineId);
        if (quantity.compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                    "Dispensed quantity cannot exceed the prescribed remainder.");
        }
        String batchesSql = """
                SELECT batch_id, quantity_received
                FROM medicine_batches
                WHERE medicine_id = ?
                  AND (expiry_date IS NULL OR expiry_date >= TRUNC(SYSDATE))
                ORDER BY expiry_date NULLS LAST, received_at, batch_id
                FOR UPDATE
                """;
        List<Long> batchIds = new ArrayList<>();
        List<BigDecimal> batchReceivedQuantities = new ArrayList<>();
        try (PreparedStatement statement =
                     connection.prepareStatement(batchesSql)) {
            statement.setLong(1, medicineId);
            try (ResultSet batches = statement.executeQuery()) {
                while (batches.next()) {
                    batchIds.add(batches.getLong("batch_id"));
                    batchReceivedQuantities.add(
                            batches.getBigDecimal("quantity_received"));
                }
            }
        }
        BigDecimal quantityToAllocate = quantity;
        for (int batchIndex = 0;
             batchIndex < batchIds.size()
                     && quantityToAllocate.signum() > 0;
             batchIndex++) {
            long batchId = batchIds.get(batchIndex);
            BigDecimal available = getBatchQuantityOnHand(
                    connection, batchId,
                    batchReceivedQuantities.get(batchIndex));
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal allocated = quantityToAllocate.min(available);
            long dispensingItemId = insertDispensingItem(
                    connection, dispensingId, prescriptionItemId, batchId,
                    allocated);
            insertInventoryTransaction(connection, batchId, "DISPENSE",
                    allocated.negate(), dispensingItemId, userId,
                    "Prescription " + prescriptionId);
            quantityToAllocate = quantityToAllocate.subtract(allocated);
        }
        if (quantityToAllocate.signum() > 0) {
            throw new IllegalArgumentException(
                    "Requested quantity exceeds unexpired stock. "
                            + "No changes were saved.");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE prescription_items
                SET quantity_dispensed = quantity_dispensed + ?
                WHERE prescription_item_id = ?
                  AND quantity_dispensed + ? <= quantity_prescribed
                """)) {
            statement.setBigDecimal(1, quantity);
            statement.setLong(2, prescriptionItemId);
            statement.setBigDecimal(3, quantity);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException(
                        "Prescription quantities changed. Refresh and retry.");
            }
        }
    }

    private long lockPendingPrescription(Connection connection,
                                         long prescriptionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT patient_id, prescription_status
                FROM prescriptions
                WHERE prescription_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, prescriptionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()
                        || !List.of("PENDING", "PARTIALLY_DISPENSED")
                        .contains(resultSet.getString("prescription_status"))) {
                    throw new IllegalArgumentException(
                            "This prescription is no longer available to dispense.");
                }
                return resultSet.getLong("patient_id");
            }
        }
    }

    private long insertDispensing(Connection connection, long prescriptionId,
                                  long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO dispensings (
                    prescription_id, dispensed_by_user_id
                ) VALUES (?, ?)
                """, new String[] {"DISPENSING_ID"})) {
            statement.setLong(1, prescriptionId);
            statement.setLong(2, userId);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
        }
        throw new SQLException("Unable to retrieve the dispensing record ID.");
    }

    private long insertDispensingItem(Connection connection, long dispensingId,
                                      long prescriptionItemId, long batchId,
                                      BigDecimal quantity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO dispensing_items (
                    dispensing_id, prescription_item_id, batch_id, quantity
                ) VALUES (?, ?, ?, ?)
                """, new String[] {"DISPENSING_ITEM_ID"})) {
            statement.setLong(1, dispensingId);
            statement.setLong(2, prescriptionItemId);
            statement.setLong(3, batchId);
            statement.setBigDecimal(4, quantity);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
        }
        throw new SQLException(
                "Unable to retrieve the dispensing item record ID.");
    }

    private BigDecimal lockBatch(Connection connection, long batchId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT quantity_received
                FROM medicine_batches
                WHERE batch_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, batchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Medicine batch was not found.");
                }
                return resultSet.getBigDecimal("quantity_received");
            }
        }
    }

    private Long lockBatchSupplier(Connection connection, long batchId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT supplier_id
                FROM medicine_batches
                WHERE batch_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, batchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Medicine batch was not found.");
                }
                long supplierId = resultSet.getLong("supplier_id");
                return resultSet.wasNull() ? null : supplierId;
            }
        }
    }

    private void lockMedicine(Connection connection, long medicineId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT medicine_id
                FROM medicines
                WHERE medicine_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, medicineId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Medicine record was not found.");
                }
            }
        }
    }

    private void lockSupplier(Connection connection, long supplierId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT supplier_id
                FROM suppliers
                WHERE supplier_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, supplierId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Supplier record was not found.");
                }
            }
        }
    }

    private BigDecimal getBatchQuantityOnHand(Connection connection,
                                              long batchId,
                                              BigDecimal receivedQuantity)
            throws SQLException {
        String sql = """
                SELECT GREATEST(
                    ? - NVL((
                        SELECT SUM(quantity)
                        FROM dispensing_items
                        WHERE batch_id = ?
                    ), 0) + NVL((
                        SELECT SUM(CASE
                            WHEN transaction_type IN ('ADJUSTMENT', 'RETURN')
                                THEN quantity_delta
                            WHEN transaction_type = 'EXPIRED'
                                THEN -ABS(quantity_delta)
                            ELSE 0
                        END)
                        FROM inventory_transactions
                        WHERE batch_id = ?
                    ), 0),
                    0
                ) AS quantity_on_hand
                FROM dual
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, receivedQuantity);
            statement.setLong(2, batchId);
            statement.setLong(3, batchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBigDecimal("quantity_on_hand");
            }
        }
    }

    private void insertInventoryTransaction(
            Connection connection, long batchId, String type,
            BigDecimal quantityDelta, Long dispensingItemId,
            long userId, String notes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO inventory_transactions (
                    batch_id, transaction_type, quantity_delta,
                    related_dispensing_item_id, recorded_by_user_id, notes
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, batchId);
            statement.setString(2, type);
            statement.setBigDecimal(3, quantityDelta);
            if (dispensingItemId == null) {
                statement.setNull(4, Types.NUMERIC);
            } else {
                statement.setLong(4, dispensingItemId);
            }
            statement.setLong(5, userId);
            statement.setString(6, notes);
            statement.executeUpdate();
        }
    }

    private long findBatchId(Connection connection, long medicineId,
                             String batchNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT batch_id
                FROM medicine_batches
                WHERE medicine_id = ? AND batch_number = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, medicineId);
            statement.setString(2, batchNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "The received medicine batch could not be loaded.");
                }
                return resultSet.getLong("batch_id");
            }
        }
    }

    private void lockActiveMedicine(Connection connection, long medicineId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT medicine_id
                FROM medicines
                WHERE medicine_id = ? AND is_active = 'Y'
                FOR UPDATE
                """)) {
            statement.setLong(1, medicineId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Select an active medicine.");
                }
            }
        }
    }

    private void requireActiveSupplier(Connection connection, long supplierId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT supplier_id
                FROM suppliers
                WHERE supplier_id = ? AND is_active = 'Y'
                """)) {
            statement.setLong(1, supplierId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Select an active supplier.");
                }
            }
        }
    }

    private void requirePermission(Connection connection, long userId,
                                   String permissionCode)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM users u
                JOIN role_permissions rp ON rp.role_id = u.role_id
                WHERE u.user_id = ?
                  AND u.is_active = 'Y'
                  AND (u.staff_id IS NULL OR EXISTS (
                      SELECT 1
                      FROM staff s
                      WHERE s.staff_id = u.staff_id
                        AND s.status = 'ACTIVE'
                  ))
                  AND rp.permission_code = ?
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, permissionCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) != 1) {
                    throw new SecurityException(
                            "You do not have permission for this pharmacy action.");
                }
            }
        }
    }

    private void insertWorkflowEvent(Connection connection, long patientId,
                                     String entityType, long eventId,
                                     String eventType,
                                     long userId, String department,
                                     String details) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workflow_events (
                    patient_id, entity_type, entity_id, event_type,
                    department_name, performed_by_user_id, details
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, patientId);
            statement.setString(2, entityType);
            statement.setLong(3, eventId);
            statement.setString(4, eventType);
            statement.setString(5, department);
            statement.setLong(6, userId);
            statement.setString(7, details);
            statement.executeUpdate();
        }
    }

    private void writeAudit(Connection connection, long userId,
                            String action, String description)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_logs (user_id, action, details)
                VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, action);
            statement.setString(3, description);
            statement.executeUpdate();
        }
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        checkLength(normalized, field, maxLength);
        return normalized;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void checkLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be no more than " + maxLength + " characters.");
        }
    }

    private static void validateReorderLevel(BigDecimal reorderLevel) {
        if (reorderLevel == null || reorderLevel.signum() < 0) {
            throw new IllegalArgumentException(
                    "Reorder level must be zero or greater.");
        }
    }

    private static void requireUpdated(int updatedRows, String entity)
            throws SQLException {
        if (updatedRows != 1) {
            throw new SQLException(entity + " record could not be updated.");
        }
    }

    private static void rollback(Connection connection, Exception exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    private static String formatQuantity(BigDecimal quantity) {
        return quantity == null
                ? "0"
                : quantity.stripTrailingZeros().toPlainString();
    }
}
