package ug.sjch.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NursingDashboardDao {
    public record Metrics(
            Integer assignedPatients,
            Integer patientsRequiringAttention,
            Integer vitalsAwaitingRecording,
            Integer currentAdmissions
    ) {
    }

    public record AdmittedPatient(
            long admissionId,
            long patientId,
            String patientNumber,
            String patientName,
            String ward,
            String bedNumber,
            String admittedAt
    ) {
        @Override
        public String toString() {
            return patientNumber + " - " + patientName
                    + (ward == null ? "" : " - " + ward
                    + (bedNumber == null ? "" : " / " + bedNumber));
        }
    }

    public record ObservationRow(
            String recordedAt,
            String patientNumber,
            String patientName,
            String temperature,
            String pulse,
            String bloodPressure,
            String respiration,
            String oxygenSaturation,
            String notes,
            String recordedBy
    ) {
    }

    public record MedicationOption(
            long admissionId,
            long patientId,
            long prescriptionItemId,
            String patient,
            String medicine,
            String dosage,
            String route,
            String frequency,
            String instructions
    ) {
        @Override
        public String toString() {
            return patient + " — " + medicine + " — "
                    + (dosage == null ? "dose not specified" : dosage)
                    + (route == null ? "" : " (" + route + ")");
        }
    }

    public record MedicationAdministrationRow(
            String administeredAt,
            String patientNumber,
            String patientName,
            String medicine,
            String dose,
            String route,
            String administeredBy,
            String notes
    ) {
    }

    record ObservationInput(
            BigDecimal temperature,
            BigDecimal pulse,
            BigDecimal systolic,
            BigDecimal diastolic,
            BigDecimal respiration,
            BigDecimal oxygenSaturation,
            String notes
    ) {
    }

    public Metrics loadMetrics() throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            Integer currentAdmissions = null;
            Integer vitalsAwaiting = null;
            if (tableExists(connection, "ADMISSIONS")
                    && OracleSchemaInspector.columnExists(
                            connection, "ADMISSIONS", "STATUS")) {
                currentAdmissions = count(connection, """
                        SELECT COUNT(*) FROM admissions
                        WHERE status = 'ADMITTED'
                        """);
                if (tableExists(connection, "NURSING_OBSERVATIONS")) {
                    vitalsAwaiting = count(connection, """
                            SELECT COUNT(*)
                            FROM admissions a
                            WHERE a.status = 'ADMITTED'
                              AND NOT EXISTS (
                                  SELECT 1 FROM nursing_observations n
                                  WHERE n.admission_id = a.admission_id
                                    AND TRUNC(n.recorded_at) = TRUNC(SYSDATE)
                                    AND (n.temperature_c IS NOT NULL
                                      OR n.pulse_rate IS NOT NULL
                                      OR n.systolic_bp IS NOT NULL
                                      OR n.diastolic_bp IS NOT NULL
                                      OR n.respiratory_rate IS NOT NULL
                                      OR n.oxygen_saturation IS NOT NULL)
                              )
                            """);
                }

            }
            return new Metrics(currentAdmissions, null, vitalsAwaiting,
                    currentAdmissions);
        }
    }

    public boolean isObservationConfigured() throws SQLException {
        return tableExists("NURSING_OBSERVATIONS");
    }

    public boolean isMedicationAdministrationConfigured()
            throws SQLException {
        return tableExists("NURSING_MEDICATION_ADMINISTRATIONS");
    }

    public List<AdmittedPatient> loadAdmittedPatients() throws SQLException {
        requireTables("ADMISSIONS", "PATIENTS");
        boolean hasBedId = columnExists("ADMISSIONS", "BED_ID")
                && tableExists("BEDS");
        String bedJoin = hasBedId
                ? """
                  LEFT JOIN beds b ON b.bed_id = a.bed_id
                  LEFT JOIN wards w ON w.ward_id = b.ward_id
                  """
                : """
                  LEFT JOIN (SELECT CAST(NULL AS VARCHAR2(1))
                                  AS ward_name FROM dual) w ON 1 = 1
                  LEFT JOIN (SELECT CAST(NULL AS VARCHAR2(1))
                                  AS bed_number FROM dual) b ON 1 = 1
                  """;
        String sql = """
                SELECT a.admission_id, p.patient_id, p.patient_number,
                       %s AS patient_name, w.ward_name, b.bed_number,
                       TO_CHAR(a.admitted_at, 'DD Mon YYYY HH:MI AM')
                           AS admitted_at
                FROM admissions a
                JOIN patients p ON p.patient_id = a.patient_id
                %s
                WHERE a.status = 'ADMITTED'
                ORDER BY w.ward_name NULLS LAST, b.bed_number NULLS LAST,
                         p.patient_number
                FETCH FIRST 500 ROWS ONLY
                """.formatted(patientNameExpression(), bedJoin);
        List<AdmittedPatient> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new AdmittedPatient(
                        resultSet.getLong("admission_id"),
                        resultSet.getLong("patient_id"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("ward_name"),
                        resultSet.getString("bed_number"),
                        resultSet.getString("admitted_at")));
            }
        }
        return rows;
    }

    public List<ObservationRow> loadObservations() throws SQLException {
        requireTables("NURSING_OBSERVATIONS", "PATIENTS", "USERS");
        String sql = """
                SELECT TO_CHAR(n.recorded_at, 'DD Mon YYYY HH:MI AM')
                           AS recorded_at,
                       p.patient_number, %s AS patient_name,
                       n.temperature_c, n.pulse_rate, n.systolic_bp,
                       n.diastolic_bp, n.respiratory_rate,
                       n.oxygen_saturation, n.notes, u.username
                FROM nursing_observations n
                JOIN patients p ON p.patient_id = n.patient_id
                JOIN users u ON u.user_id = n.recorded_by_user_id
                ORDER BY n.recorded_at DESC, n.observation_id DESC
                FETCH FIRST 200 ROWS ONLY
                """.formatted(patientNameExpression());
        List<ObservationRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new ObservationRow(
                        resultSet.getString("recorded_at"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        decimal(resultSet.getBigDecimal("temperature_c")),
                        decimal(resultSet.getBigDecimal("pulse_rate")),
                        bloodPressure(resultSet.getBigDecimal("systolic_bp"),
                                resultSet.getBigDecimal("diastolic_bp")),
                        decimal(resultSet.getBigDecimal("respiratory_rate")),
                        decimal(resultSet.getBigDecimal("oxygen_saturation")),
                        resultSet.getString("notes"),
                        resultSet.getString("username")));
            }
        }
        return rows;
    }

    public List<MedicationOption> loadMedicationOptions() throws SQLException {
        requireTables("ADMISSIONS", "PATIENTS", "PRESCRIPTIONS",
                "PRESCRIPTION_ITEMS", "MEDICINES", "DISPENSINGS",
                "DISPENSING_ITEMS");
        String sql = """
                SELECT a.admission_id, p.patient_id, p.patient_number,
                       %s AS patient_name, pi.prescription_item_id,
                       m.medicine_name, m.strength, pi.dosage, pi.route,
                       pi.frequency, pi.instructions
                FROM admissions a
                JOIN patients p ON p.patient_id = a.patient_id
                JOIN prescriptions pr ON pr.patient_id = p.patient_id
                JOIN prescription_items pi
                  ON pi.prescription_id = pr.prescription_id
                JOIN medicines m ON m.medicine_id = pi.medicine_id
                WHERE a.status = 'ADMITTED'
                  AND pr.prescription_status IN (
                      'PARTIALLY_DISPENSED', 'DISPENSED'
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM dispensing_items di
                      JOIN dispensings d ON d.dispensing_id = di.dispensing_id
                      WHERE di.prescription_item_id =
                            pi.prescription_item_id
                  )
                ORDER BY p.patient_number, m.medicine_name
                FETCH FIRST 500 ROWS ONLY
                """.formatted(patientNameExpression());
        List<MedicationOption> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String patient = resultSet.getString("patient_number") + " - "
                        + resultSet.getString("patient_name");
                String strength = resultSet.getString("strength");
                String medicine = resultSet.getString("medicine_name");
                if (strength != null && !strength.isBlank()) {
                    medicine += " " + strength;
                }
                rows.add(new MedicationOption(
                        resultSet.getLong("admission_id"),
                        resultSet.getLong("patient_id"),
                        resultSet.getLong("prescription_item_id"),
                        patient,
                        medicine,
                        resultSet.getString("dosage"),
                        resultSet.getString("route"),
                        resultSet.getString("frequency"),
                        resultSet.getString("instructions")));
            }
        }
        return rows;
    }

    public List<MedicationAdministrationRow> loadMedicationAdministrations()
            throws SQLException {
        requireTables("NURSING_MEDICATION_ADMINISTRATIONS", "PATIENTS",
                "MEDICINES", "PRESCRIPTION_ITEMS", "USERS");
        String sql = """
                SELECT TO_CHAR(ma.administered_at, 'DD Mon YYYY HH:MI AM')
                           AS administered_at,
                       p.patient_number, %s AS patient_name,
                       m.medicine_name, ma.dose_given, ma.route_given,
                       u.username, ma.notes
                FROM nursing_medication_administrations ma
                JOIN patients p ON p.patient_id = ma.patient_id
                JOIN prescription_items pi
                  ON pi.prescription_item_id = ma.prescription_item_id
                JOIN medicines m ON m.medicine_id = pi.medicine_id
                JOIN users u ON u.user_id = ma.administered_by_user_id
                ORDER BY ma.administered_at DESC,
                         ma.administration_id DESC
                FETCH FIRST 200 ROWS ONLY
                """.formatted(patientNameExpression());
        List<MedicationAdministrationRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new MedicationAdministrationRow(
                        resultSet.getString("administered_at"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("medicine_name"),
                        resultSet.getString("dose_given"),
                        resultSet.getString("route_given"),
                        resultSet.getString("username"),
                        resultSet.getString("notes")));
            }
        }
        return rows;
    }

    public void recordObservation(long admissionId, String temperature,
                                 String pulse, String systolic,
                                 String diastolic, String respiration,
                                 String oxygenSaturation, String notes,
                                 long userId) throws SQLException {
        ObservationInput input = normalizeObservation(temperature, pulse,
                systolic, diastolic, respiration, oxygenSaturation, notes);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean hasVitalValues = input.temperature() != null
                        || input.pulse() != null || input.systolic() != null
                        || input.respiration() != null
                        || input.oxygenSaturation() != null;
                requirePermission(connection, userId, hasVitalValues
                        ? "NURSING_VITALS_RECORD" : "NURSING_CARE_RECORD");
                long patientId = lockActiveAdmission(connection, admissionId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO nursing_observations (
                            admission_id, patient_id, recorded_by_user_id,
                            temperature_c, pulse_rate, systolic_bp,
                            diastolic_bp, respiratory_rate,
                            oxygen_saturation, notes
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, admissionId);
                    statement.setLong(2, patientId);
                    statement.setLong(3, userId);
                    setDecimal(statement, 4, input.temperature());
                    setDecimal(statement, 5, input.pulse());
                    setDecimal(statement, 6, input.systolic());
                    setDecimal(statement, 7, input.diastolic());
                    setDecimal(statement, 8, input.respiration());
                    setDecimal(statement, 9, input.oxygenSaturation());
                    statement.setString(10, input.notes());
                    statement.executeUpdate();
                }
                writeWorkflowEvent(connection, patientId, admissionId,
                        "NURSING_OBSERVATION", "NURSING_OBSERVATION_RECORDED",
                        userId, "Vital observation or nursing note recorded");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void recordMedicationAdministration(long admissionId,
                                               long prescriptionItemId,
                                               String dose, String route,
                                               String notes, long userId)
            throws SQLException {
        String normalizedDose = required(dose, "Dose given", 100);
        String normalizedRoute = required(route, "Route given", 60);
        String normalizedNotes = optional(notes);
        checkLength(normalizedNotes, "Administration notes", 1000);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "NURSING_CARE_RECORD");
                long patientId = lockActiveAdmission(connection, admissionId);
                long prescriptionId;
                String expectedRoute;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT prescription_id, route
                        FROM prescription_items
                        WHERE prescription_item_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, prescriptionItemId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalArgumentException(
                                    "Select a prescribed medication.");
                        }
                        prescriptionId = resultSet.getLong("prescription_id");
                        expectedRoute = resultSet.getString("route");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT COUNT(*)
                        FROM prescriptions pr
                        WHERE pr.prescription_id = ?
                          AND pr.patient_id = ?
                          AND pr.prescription_status IN (
                              'PARTIALLY_DISPENSED', 'DISPENSED'
                          )
                          AND EXISTS (
                              SELECT 1
                              FROM dispensing_items di
                              JOIN dispensings d
                                ON d.dispensing_id = di.dispensing_id
                              WHERE di.prescription_item_id = ?
                          )
                        """)) {
                    statement.setLong(1, prescriptionId);
                    statement.setLong(2, patientId);
                    statement.setLong(3, prescriptionItemId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        if (resultSet.getInt(1) != 1) {
                            throw new IllegalArgumentException(
                                    "Select a dispensed medication prescribed "
                                            + "for this admitted patient.");
                        }
                    }
                }
                if (expectedRoute != null
                        && !expectedRoute.equalsIgnoreCase(normalizedRoute)) {
                    throw new IllegalArgumentException(
                            "Route must match the prescribed route: "
                                    + expectedRoute + ".");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT COUNT(*)
                        FROM nursing_medication_administrations
                        WHERE admission_id = ?
                          AND prescription_item_id = ?
                          AND administered_at >=
                              SYSTIMESTAMP - INTERVAL '1' MINUTE
                        """)) {
                    statement.setLong(1, admissionId);
                    statement.setLong(2, prescriptionItemId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        if (resultSet.getInt(1) > 0) {
                            throw new IllegalArgumentException(
                                    "A recent administration for this "
                                            + "prescription item is already "
                                            + "recorded.");
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO nursing_medication_administrations (
                            admission_id, patient_id, prescription_item_id,
                            administered_by_user_id, dose_given, route_given,
                            notes
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, admissionId);
                    statement.setLong(2, patientId);
                    statement.setLong(3, prescriptionItemId);
                    statement.setLong(4, userId);
                    statement.setString(5, normalizedDose);
                    statement.setString(6, normalizedRoute);
                    statement.setString(7, normalizedNotes);
                    statement.executeUpdate();
                }
                writeWorkflowEvent(connection, patientId, admissionId,
                        "MEDICATION_ADMINISTRATION",
                        "MEDICATION_ADMINISTERED", userId,
                        "Medication administration recorded");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    static ObservationInput normalizeObservation(
            String temperature, String pulse, String systolic,
            String diastolic, String respiration, String oxygenSaturation,
            String notes) {
        BigDecimal normalizedTemperature = decimalValue(
                temperature, "Temperature", new BigDecimal("25"),
                new BigDecimal("45"));
        BigDecimal normalizedPulse = decimalValue(
                pulse, "Pulse rate", new BigDecimal("20"),
                new BigDecimal("250"));
        BigDecimal normalizedSystolic = decimalValue(
                systolic, "Systolic blood pressure", new BigDecimal("40"),
                new BigDecimal("300"));
        BigDecimal normalizedDiastolic = decimalValue(
                diastolic, "Diastolic blood pressure", new BigDecimal("20"),
                new BigDecimal("200"));
        if ((normalizedSystolic == null) != (normalizedDiastolic == null)) {
            throw new IllegalArgumentException(
                    "Enter both systolic and diastolic blood pressure.");
        }
        if (normalizedSystolic != null
                && normalizedSystolic.compareTo(normalizedDiastolic) <= 0) {
            throw new IllegalArgumentException(
                    "Systolic pressure must be greater than diastolic pressure.");
        }
        BigDecimal normalizedRespiration = decimalValue(
                respiration, "Respiratory rate", new BigDecimal("4"),
                new BigDecimal("80"));
        BigDecimal normalizedOxygen = decimalValue(
                oxygenSaturation, "Oxygen saturation",
                new BigDecimal("50"), new BigDecimal("100"));
        String normalizedNotes = optional(notes);
        checkLength(normalizedNotes, "Nursing notes", 2000);
        if (normalizedTemperature == null && normalizedPulse == null
                && normalizedSystolic == null
                && normalizedRespiration == null && normalizedOxygen == null
                && normalizedNotes == null) {
            throw new IllegalArgumentException(
                    "Enter at least one observation or nursing note.");
        }
        return new ObservationInput(normalizedTemperature, normalizedPulse,
                normalizedSystolic, normalizedDiastolic,
                normalizedRespiration, normalizedOxygen, normalizedNotes);
    }

    private long lockActiveAdmission(Connection connection, long admissionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT patient_id FROM admissions
                WHERE admission_id = ? AND status = 'ADMITTED'
                FOR UPDATE
                """)) {
            statement.setLong(1, admissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Select a patient with an active admission.");
                }
                return resultSet.getLong(1);
            }
        }
    }

    private void requirePermission(Connection connection, long userId,
                                   String permissionCode) throws SQLException {
        if (!tableExists(connection, "ROLE_PERMISSIONS")
                || !tableExists(connection, "PERMISSIONS")) {
            throw new IllegalStateException(
                    "Nursing workflow permissions are not configured.");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM users u
                JOIN role_permissions rp ON rp.role_id = u.role_id
                WHERE u.user_id = ? AND u.is_active = 'Y'
                  AND rp.permission_code = ?
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, permissionCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) != 1) {
                    throw new SecurityException(
                            "Your account cannot perform this nursing action.");
                }
            }
        }
    }

    private void writeWorkflowEvent(Connection connection, long patientId,
                                    long admissionId, String entityType,
                                    String eventType, long userId,
                                    String details) throws SQLException {
        if (!tableExists(connection, "WORKFLOW_EVENTS")) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workflow_events (
                    patient_id, entity_type, entity_id, event_type,
                    department_name, performed_by_user_id, details
                ) VALUES (?, ?, ?, ?, 'Nursing', ?, ?)
                """)) {
            statement.setLong(1, patientId);
            statement.setString(2, entityType);
            statement.setLong(3, admissionId);
            statement.setString(4, eventType);
            statement.setLong(5, userId);
            statement.setString(6, details);
            statement.executeUpdate();
        }
    }

    private String patientNameExpression() throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            if (OracleSchemaInspector.columnExists(
                    connection, "PATIENTS", "FIRST_NAME")
                    && OracleSchemaInspector.columnExists(
                    connection, "PATIENTS", "LAST_NAME")) {
                return "TRIM(p.first_name || ' ' || p.last_name)";
            }
            if (OracleSchemaInspector.columnExists(
                    connection, "PATIENTS", "FULL_NAME")) {
                return "TRIM(p.full_name)";
            }
            throw new IllegalStateException(
                    "Patient name fields are not configured in Oracle.");
        }
    }

    private void requireTables(String... tables) throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            for (String table : tables) {
                if (!tableExists(connection, table)) {
                    throw new IllegalStateException(
                            table + " is not configured in Oracle.");
                }
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName)
            throws SQLException {
        return OracleSchemaInspector.tableExists(connection, tableName);
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            return tableExists(connection, tableName);
        }
    }

    private boolean columnExists(String tableName, String columnName)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            return OracleSchemaInspector.columnExists(
                    connection, tableName, columnName);
        }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static BigDecimal decimalValue(String raw, String field,
                                           BigDecimal minimum,
                                           BigDecimal maximum) {
        String value = optional(raw);
        if (value == null) {
            return null;
        }
        final BigDecimal decimal;
        try {
            decimal = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be numeric.");
        }
        if (decimal.compareTo(minimum) < 0 || decimal.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " must be between "
                    + minimum.stripTrailingZeros().toPlainString() + " and "
                    + maximum.stripTrailingZeros().toPlainString() + ".");
        }
        return decimal;
    }

    private static void setDecimal(PreparedStatement statement, int index,
                                   BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.NUMERIC);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String bloodPressure(BigDecimal systolic,
                                        BigDecimal diastolic) {
        if (systolic == null || diastolic == null) {
            return null;
        }
        return decimal(systolic) + "/" + decimal(diastolic);
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

    private static void rollback(Connection connection, Exception exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }
}
