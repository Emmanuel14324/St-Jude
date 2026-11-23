package ug.sjch.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InpatientDashboardDao {
    public record Metrics(
            Integer totalBeds,
            Integer occupiedBeds,
            Integer availableBeds,
            Integer currentAdmissions,
            Integer patientsAwaitingDischarge,
            Integer occupancyRate
    ) {
    }

    public record WardOption(long id, String name, String type, boolean active) {
        @Override
        public String toString() {
            return name + (type == null ? "" : " - " + type)
                    + (active ? "" : " (archived)");
        }
    }

    public record BedOption(long id, long wardId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record BedRow(
            long bedId,
            String ward,
            String bedNumber,
            String status,
            String patientNumber,
            String patientName,
            String admittedAt
    ) {
    }

    public record AdmissionRow(
            long admissionId,
            String patientNumber,
            String patientName,
            String ward,
            String bedNumber,
            String admittedAt,
            String status,
            String dischargeStatus
    ) {
        @Override
        public String toString() {
            return patientNumber + " - " + patientName;
        }
    }

    public record TransferRow(
            String requestedAt,
            String patientNumber,
            String patientName,
            String fromBed,
            String toBed,
            String status,
            String reason
    ) {
    }

    public record DischargeRow(
            long admissionId,
            long dischargeRequestId,
            String patientNumber,
            String patientName,
            String ward,
            String bedNumber,
            String admittedAt,
            String requestStatus,
            String notes
    ) {
    }

    public Metrics loadMetrics() throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            Integer admissions = null;
            Integer totalBeds = null;
            Integer occupiedBeds = null;
            Integer availableBeds = null;
            Integer pendingDischarges = null;

            if (tableExists(connection, "ADMISSIONS")
                    && OracleSchemaInspector.columnExists(
                            connection, "ADMISSIONS", "STATUS")) {
                admissions = count(connection, """
                        SELECT COUNT(*) FROM admissions
                        WHERE UPPER(status) = 'ADMITTED'
                        """);
            }
            if (tableExists(connection, "BEDS")) {
                totalBeds = count(connection, "SELECT COUNT(*) FROM beds");
                occupiedBeds = count(connection, """
                        SELECT COUNT(*) FROM beds
                        WHERE UPPER(bed_status) = 'OCCUPIED'
                        """);
                availableBeds = count(connection, """
                        SELECT COUNT(*) FROM beds
                        WHERE UPPER(bed_status) = 'AVAILABLE'
                        """);
            }
            if (tableExists(connection, "DISCHARGE_REQUESTS")) {
                pendingDischarges = count(connection, """
                        SELECT COUNT(*) FROM discharge_requests
                        WHERE request_status IN ('REQUESTED', 'APPROVED')
                        """);
            }
            Integer occupancy = totalBeds == null || totalBeds == 0
                    || occupiedBeds == null
                    ? (totalBeds == null ? null : 0)
                    : Math.round(occupiedBeds * 100f / totalBeds);
            return new Metrics(totalBeds, occupiedBeds, availableBeds,
                    admissions, pendingDischarges, occupancy);
        }
    }

    public List<WardOption> loadWards() throws SQLException {
        return loadWards(false);
    }

    public List<WardOption> loadActiveWards() throws SQLException {
        return loadWards(true);
    }

    private List<WardOption> loadWards(boolean activeOnly) throws SQLException {
        requireTables("WARDS");
        List<WardOption> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT ward_id, ward_name, ward_type, is_active
                     FROM wards
                     %s
                     ORDER BY ward_name
                     """.formatted(activeOnly ? "WHERE is_active = 'Y'" : ""));
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new WardOption(resultSet.getLong("ward_id"),
                        resultSet.getString("ward_name"),
                        resultSet.getString("ward_type"),
                        "Y".equalsIgnoreCase(
                                resultSet.getString("is_active"))));
            }
        }
        return rows;
    }

    public void createWard(String name, String type, long userId)
            throws SQLException {
        String normalizedName = normalizeWardName(name);
        String normalizedType = normalizeWardType(type);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_WARD_MANAGE");
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO wards (ward_name, ward_type, is_active)
                        VALUES (?, ?, 'Y')
                        """)) {
                    statement.setString(1, normalizedName);
                    statement.setString(2, normalizedType);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateWardMetadata(long wardId, String name, String type,
                                   long userId) throws SQLException {
        String normalizedName = normalizeWardName(name);
        String normalizedType = normalizeWardType(type);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_WARD_MANAGE");
                lockWardForMaintenance(connection, wardId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE wards
                        SET ward_name = ?, ward_type = ?
                        WHERE ward_id = ?
                        """)) {
                    statement.setString(1, normalizedName);
                    statement.setString(2, normalizedType);
                    statement.setLong(3, wardId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Ward was not found.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void archiveWard(long wardId, long userId) throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_WARD_MANAGE");
                lockWardForMaintenance(connection, wardId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE wards SET is_active = 'N'
                        WHERE ward_id = ? AND is_active = 'Y'
                        """)) {
                    statement.setLong(1, wardId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "Ward is already archived or was not found.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void deleteUnreferencedWard(long wardId, long userId)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_WARD_MANAGE");
                lockWardForMaintenance(connection, wardId);
                requireUnreferenced(connection, "WARDS", "WARD_ID", wardId);
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM wards WHERE ward_id = ?")) {
                    statement.setLong(1, wardId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Ward was not found.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public List<BedRow> loadBeds() throws SQLException {
        requireTables("WARDS", "BEDS");
        String patientName = patientNameExpression();
        boolean hasBedId = columnExists("ADMISSIONS", "BED_ID");
        String patientColumns = hasBedId && tableExists("ADMISSIONS")
                ? """
                  LEFT JOIN admissions a
                    ON a.bed_id = b.bed_id AND a.status = 'ADMITTED'
                  LEFT JOIN patients p ON p.patient_id = a.patient_id
                  """
                : "";
        String patientNumber = patientColumns.isEmpty()
                ? "CAST(NULL AS VARCHAR2(1))" : "p.patient_number";
        String admittedAt = patientColumns.isEmpty()
                ? "CAST(NULL AS VARCHAR2(1))"
                : "TO_CHAR(a.admitted_at, 'DD Mon YYYY HH:MI AM')";
        String sql = """
                SELECT b.bed_id, w.ward_name, b.bed_number, b.bed_status,
                       %s AS patient_number,
                       %s AS patient_name,
                       %s AS admitted_at
                FROM beds b
                JOIN wards w ON w.ward_id = b.ward_id
                %s
                ORDER BY w.ward_name, b.bed_number
                FETCH FIRST 500 ROWS ONLY
                """.formatted(patientNumber,
                patientColumns.isEmpty() ? "CAST(NULL AS VARCHAR2(1))"
                        : patientName,
                admittedAt, patientColumns);
        List<BedRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new BedRow(resultSet.getLong("bed_id"),
                        resultSet.getString("ward_name"),
                        resultSet.getString("bed_number"),
                        resultSet.getString("bed_status"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("admitted_at")));
            }
        }
        return rows;
    }

    public List<BedOption> loadAvailableBeds() throws SQLException {
        requireTables("WARDS", "BEDS");
        List<BedOption> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT b.bed_id, b.ward_id, w.ward_name, b.bed_number
                     FROM beds b
                     JOIN wards w ON w.ward_id = b.ward_id
                     WHERE b.bed_status = 'AVAILABLE'
                       AND w.is_active = 'Y'
                     ORDER BY w.ward_name, b.bed_number
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new BedOption(resultSet.getLong("bed_id"),
                        resultSet.getLong("ward_id"),
                        resultSet.getString("ward_name") + " / "
                                + resultSet.getString("bed_number")));
            }
        }
        return rows;
    }

    public void createBed(long wardId, String bedNumber, long userId)
            throws SQLException {
        String normalizedNumber = normalizeBedNumber(bedNumber);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_WARD_MANAGE");
                lockWard(connection, wardId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO beds (ward_id, bed_number, bed_status)
                        VALUES (?, ?, 'AVAILABLE')
                        """)) {
                    statement.setLong(1, wardId);
                    statement.setString(2, normalizedNumber);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateBedMetadata(long bedId, String bedNumber, long userId)
            throws SQLException {
        String normalizedNumber = normalizeBedNumber(bedNumber);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_WARD_MANAGE");
                lockBedForMaintenance(connection, bedId);
                requireUnreferenced(connection, "BEDS", "BED_ID", bedId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE beds SET bed_number = ? WHERE bed_id = ?
                        """)) {
                    statement.setString(1, normalizedNumber);
                    statement.setLong(2, bedId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Bed was not found.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void deleteUnreferencedBed(long bedId, long userId)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_WARD_MANAGE");
                lockBedForMaintenance(connection, bedId);
                requireUnreferenced(connection, "BEDS", "BED_ID", bedId);
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM beds WHERE bed_id = ?")) {
                    statement.setLong(1, bedId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Bed was not found.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateBedStatus(long bedId, String status, long userId)
            throws SQLException {
        String normalizedStatus = normalizeBedStatus(status);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_WARD_MANAGE");
                if (!tableExists(connection, "ADMISSIONS")
                        || !OracleSchemaInspector.columnExists(
                        connection, "ADMISSIONS", "BED_ID")) {
                    throw new IllegalStateException(
                            "Admission-to-bed tracking is not configured.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE beds b
                        SET bed_status = ?
                        WHERE bed_id = ?
                          AND bed_status <> 'OCCUPIED'
                          AND NOT EXISTS (
                              SELECT 1 FROM admissions a
                              WHERE a.bed_id = b.bed_id
                                AND a.status = 'ADMITTED'
                          )
                        """)) {
                    statement.setString(1, normalizedStatus);
                    statement.setLong(2, bedId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "Bed was not found or is occupied.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    static String normalizeBedStatus(String status) {
        String normalizedStatus = required(status, "Bed status", 24)
                .toUpperCase(java.util.Locale.ROOT);
        if (!List.of("AVAILABLE", "CLEANING", "OUT_OF_SERVICE")
                .contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Choose Available, Cleaning, or Out of service.");
        }
        return normalizedStatus;
    }

    public List<AdmissionRow> loadAdmissions() throws SQLException {
        requireTables("ADMISSIONS", "PATIENTS");
        String bedJoin = tableExists("BEDS")
                && columnExists("ADMISSIONS", "BED_ID")
                ? """
                  LEFT JOIN beds b ON b.bed_id = a.bed_id
                  LEFT JOIN wards w ON w.ward_id = b.ward_id
                  """
                : "";
        String nameExpression = patientNameExpression();
        String sql = """
                SELECT a.admission_id, p.patient_number, %s AS patient_name,
                       w.ward_name, b.bed_number,
                       TO_CHAR(a.admitted_at, 'DD Mon YYYY HH:MI AM')
                           AS admitted_at,
                       a.status,
                       (SELECT MAX(dr.request_status)
                        FROM discharge_requests dr
                        WHERE dr.admission_id = a.admission_id
                          AND dr.request_status IN ('REQUESTED', 'APPROVED'))
                           AS discharge_status
                FROM admissions a
                JOIN patients p ON p.patient_id = a.patient_id
                %s
                WHERE a.status = 'ADMITTED'
                ORDER BY a.admitted_at DESC
                FETCH FIRST 500 ROWS ONLY
                """.formatted(nameExpression,
                bedJoin.isEmpty() ? """
                        LEFT JOIN (SELECT CAST(NULL AS VARCHAR2(1))
                                         AS ward_name,
                                         CAST(NULL AS VARCHAR2(1))
                                         AS bed_number
                                   FROM dual) w ON 1 = 1
                        LEFT JOIN (SELECT CAST(NULL AS NUMBER) AS bed_id
                                   FROM dual) b ON 1 = 1
                        """ : bedJoin);
        List<AdmissionRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new AdmissionRow(resultSet.getLong("admission_id"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("ward_name"),
                        resultSet.getString("bed_number"),
                        resultSet.getString("admitted_at"),
                        resultSet.getString("status"),
                        resultSet.getString("discharge_status")));
            }
        }
        return rows;
    }

    public void createAdmission(String patientNumber, long bedId, long userId)
            throws SQLException {
        String normalizedPatientNumber =
                required(patientNumber, "Patient number", 30);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "INPATIENT_ADMISSION_MANAGE");
                long patientId = lockPatient(connection, normalizedPatientNumber);
                ensureNoActiveAdmission(connection, patientId);
                lockAndOccupyBed(connection, bedId);
                long admissionId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO admissions (
                            patient_id, bed_id, admitted_at, status
                        ) VALUES (?, ?, SYSTIMESTAMP, 'ADMITTED')
                        """, new String[] {"ADMISSION_ID"})) {
                    statement.setLong(1, patientId);
                    statement.setLong(2, bedId);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "Admission identifier was not generated.");
                        }
                        admissionId = keys.getLong(1);
                    }
                }
                writeWorkflowEvent(connection, patientId, admissionId,
                        "ADMISSION", "PATIENT_ADMITTED", userId,
                        "Patient admitted and assigned to bed " + bedId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void allocateBed(long admissionId, long bedId, long userId)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_BED_ALLOCATE");
                long patientId = lockActiveAdmission(connection, admissionId);
                Long oldBedId = readAdmissionBed(connection, admissionId);
                if (oldBedId != null && oldBedId == bedId) {
                    throw new IllegalArgumentException(
                            "The patient is already assigned to this bed.");
                }
                lockAndOccupyBed(connection, bedId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE admissions SET bed_id = ?
                        WHERE admission_id = ? AND status = 'ADMITTED'
                        """)) {
                    statement.setLong(1, bedId);
                    statement.setLong(2, admissionId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "Admission is no longer active.");
                    }
                }
                releaseBed(connection, oldBedId);
                writeWorkflowEvent(connection, patientId, admissionId,
                        "ADMISSION", "BED_ALLOCATED", userId,
                        "Bed assigned: " + bedId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void transferPatient(long admissionId, long targetBedId,
                                String reason, long userId)
            throws SQLException {
        String normalizedReason = required(reason, "Transfer reason", 1000);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "INPATIENT_BED_ALLOCATE");
                long patientId = lockActiveAdmission(connection, admissionId);
                Long sourceBedId = readAdmissionBed(connection, admissionId);
                if (sourceBedId == null) {
                    throw new IllegalArgumentException(
                            "Allocate a bed before requesting a transfer.");
                }
                if (sourceBedId == targetBedId) {
                    throw new IllegalArgumentException(
                            "Choose a different destination bed.");
                }
                lockAndOccupyBed(connection, targetBedId);
                if (tableExists(connection, "PATIENT_TRANSFERS")) {
                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                            INSERT INTO patient_transfers (
                                admission_id, from_bed_id, to_bed_id,
                                requested_by_user_id, completed_by_user_id,
                                transfer_status, reason, completed_at
                            ) VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?,
                                      SYSTIMESTAMP)
                            """)) {
                        statement.setLong(1, admissionId);
                        statement.setLong(2, sourceBedId);
                        statement.setLong(3, targetBedId);
                        statement.setLong(4, userId);
                        statement.setLong(5, userId);
                        statement.setString(6, normalizedReason);
                        statement.executeUpdate();
                    }
                } else {
                    throw new IllegalStateException(
                            "Patient transfer records are not configured.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE admissions SET bed_id = ?
                        WHERE admission_id = ? AND status = 'ADMITTED'
                        """)) {
                    statement.setLong(1, targetBedId);
                    statement.setLong(2, admissionId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "Admission is no longer active.");
                    }
                }
                releaseBed(connection, sourceBedId);
                writeWorkflowEvent(connection, patientId, admissionId,
                        "ADMISSION", "PATIENT_TRANSFERRED", userId,
                        normalizedReason);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public List<TransferRow> loadTransfers() throws SQLException {
        requireTables("PATIENT_TRANSFERS", "ADMISSIONS", "PATIENTS");
        String sql = """
                SELECT TO_CHAR(t.requested_at, 'DD Mon YYYY HH:MI AM')
                           AS requested_at,
                       p.patient_number, %s AS patient_name,
                       fw.ward_name || ' / ' || fb.bed_number AS from_bed,
                       tw.ward_name || ' / ' || tb.bed_number AS to_bed,
                       t.transfer_status, t.reason
                FROM patient_transfers t
                JOIN admissions a ON a.admission_id = t.admission_id
                JOIN patients p ON p.patient_id = a.patient_id
                LEFT JOIN beds fb ON fb.bed_id = t.from_bed_id
                LEFT JOIN wards fw ON fw.ward_id = fb.ward_id
                JOIN beds tb ON tb.bed_id = t.to_bed_id
                JOIN wards tw ON tw.ward_id = tb.ward_id
                ORDER BY t.requested_at DESC
                FETCH FIRST 200 ROWS ONLY
                """.formatted(patientNameExpression());
        List<TransferRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new TransferRow(
                        resultSet.getString("requested_at"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("from_bed"),
                        resultSet.getString("to_bed"),
                        resultSet.getString("transfer_status"),
                        resultSet.getString("reason")));
            }
        }
        return rows;
    }

    public long requestDischarge(long admissionId, String notes, long userId)
            throws SQLException {
        String normalizedNotes = optional(notes);
        checkLength(normalizedNotes, "Discharge notes", 2000);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "INPATIENT_DISCHARGE_PROCESS");
                lockActiveAdmission(connection, admissionId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT COUNT(*) FROM discharge_requests
                        WHERE admission_id = ?
                          AND request_status IN ('REQUESTED', 'APPROVED')
                        """)) {
                    statement.setLong(1, admissionId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        if (resultSet.getInt(1) > 0) {
                            throw new IllegalArgumentException(
                                    "An open discharge request already exists.");
                        }
                    }
                }
                long requestId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO discharge_requests (
                            admission_id, requested_by_user_id,
                            request_status, notes
                        ) VALUES (?, ?, 'REQUESTED', ?)
                        """, new String[] {"DISCHARGE_REQUEST_ID"})) {
                    statement.setLong(1, admissionId);
                    statement.setLong(2, userId);
                    statement.setString(3, normalizedNotes);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "Discharge request identifier was not generated.");
                        }
                        requestId = keys.getLong(1);
                    }
                }
                connection.commit();
                return requestId;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public List<DischargeRow> loadDischargeRequests() throws SQLException {
        requireTables("DISCHARGE_REQUESTS", "ADMISSIONS", "PATIENTS");
        String bedJoin = tableExists("BEDS")
                && columnExists("ADMISSIONS", "BED_ID")
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
                SELECT a.admission_id, dr.discharge_request_id,
                       p.patient_number, %s AS patient_name, w.ward_name,
                       b.bed_number,
                       TO_CHAR(a.admitted_at, 'DD Mon YYYY HH:MI AM')
                           AS admitted_at,
                       dr.request_status, dr.notes
                FROM discharge_requests dr
                JOIN admissions a ON a.admission_id = dr.admission_id
                JOIN patients p ON p.patient_id = a.patient_id
                %s
                WHERE dr.request_status IN (
                    'REQUESTED', 'APPROVED', 'COMPLETED'
                )
                ORDER BY dr.requested_at DESC
                FETCH FIRST 200 ROWS ONLY
                """.formatted(patientNameExpression(), bedJoin);
        List<DischargeRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new DischargeRow(resultSet.getLong("admission_id"),
                        resultSet.getLong("discharge_request_id"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("ward_name"),
                        resultSet.getString("bed_number"),
                        resultSet.getString("admitted_at"),
                        resultSet.getString("request_status"),
                        resultSet.getString("notes")));
            }
        }
        return rows;
    }

    public void completeDischarge(long requestId, long userId)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId,
                        "INPATIENT_DISCHARGE_PROCESS");
                long admissionId;
                long patientId;
                Long bedId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT a.admission_id, a.patient_id, a.bed_id
                        FROM discharge_requests dr
                        JOIN admissions a ON a.admission_id = dr.admission_id
                        WHERE dr.discharge_request_id = ?
                          AND dr.request_status IN ('REQUESTED', 'APPROVED')
                          AND a.status = 'ADMITTED'
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, requestId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalArgumentException(
                                    "The discharge request is no longer active.");
                        }
                        admissionId = resultSet.getLong("admission_id");
                        patientId = resultSet.getLong("patient_id");
                        long resultBedId = resultSet.getLong("bed_id");
                        bedId = resultSet.wasNull() ? null : resultBedId;
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE admissions
                        SET status = 'DISCHARGED', discharge_date = SYSTIMESTAMP
                        WHERE admission_id = ? AND status = 'ADMITTED'
                        """)) {
                    statement.setLong(1, admissionId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "Admission is no longer active.");
                    }
                }
                releaseBed(connection, bedId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE discharge_requests
                        SET request_status = 'COMPLETED',
                            processed_by_user_id = ?, processed_at = SYSTIMESTAMP
                        WHERE discharge_request_id = ?
                          AND request_status IN ('REQUESTED', 'APPROVED')
                        """)) {
                    statement.setLong(1, userId);
                    statement.setLong(2, requestId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "Discharge request was already processed.");
                    }
                }
                writeWorkflowEvent(connection, patientId, admissionId,
                        "ADMISSION", "PATIENT_DISCHARGED", userId,
                        "Discharge request " + requestId + " completed");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private long lockPatient(Connection connection, String patientNumber)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT patient_id FROM patients
                WHERE UPPER(patient_number) = UPPER(?)
                FOR UPDATE
                """)) {
            statement.setString(1, patientNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Patient number was not found.");
                }
                return resultSet.getLong(1);
            }
        }
    }

    private void ensureNoActiveAdmission(Connection connection, long patientId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM admissions
                WHERE patient_id = ? AND status = 'ADMITTED'
                """)) {
            statement.setLong(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) > 0) {
                    throw new IllegalArgumentException(
                            "Patient already has an active admission.");
                }
            }
        }
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
                            "Active admission was not found.");
                }
                return resultSet.getLong(1);
            }
        }
    }

    private Long readAdmissionBed(Connection connection, long admissionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bed_id FROM admissions WHERE admission_id = ?
                """)) {
            statement.setLong(1, admissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Admission was not found.");
                }
                long bedId = resultSet.getLong(1);
                return resultSet.wasNull() ? null : bedId;
            }
        }
    }

    private void lockAndOccupyBed(Connection connection, long bedId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE beds SET bed_status = 'OCCUPIED'
                WHERE bed_id = ? AND bed_status = 'AVAILABLE'
                """)) {
            statement.setLong(1, bedId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException(
                        "Selected bed is no longer available.");
            }
        }
    }

    private void releaseBed(Connection connection, Long bedId)
            throws SQLException {
        if (bedId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE beds SET bed_status = 'AVAILABLE'
                WHERE bed_id = ? AND bed_status = 'OCCUPIED'
                """)) {
            statement.setLong(1, bedId);
            statement.executeUpdate();
        }
    }

    private void lockWard(Connection connection, long wardId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT ward_id FROM wards
                WHERE ward_id = ? AND is_active = 'Y'
                FOR UPDATE
                """)) {
            statement.setLong(1, wardId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Active ward was not found.");
                }
            }
        }
    }

    private void lockWardForMaintenance(Connection connection, long wardId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT ward_id FROM wards WHERE ward_id = ? FOR UPDATE
                """)) {
            statement.setLong(1, wardId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Ward was not found.");
                }
            }
        }
    }

    private void lockBedForMaintenance(Connection connection, long bedId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bed_status FROM beds WHERE bed_id = ? FOR UPDATE
                """)) {
            statement.setLong(1, bedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Bed was not found.");
                }
                if ("OCCUPIED".equalsIgnoreCase(
                        resultSet.getString("bed_status"))) {
                    throw new IllegalStateException(
                            "An occupied bed cannot be renamed or deleted.");
                }
            }
        }
    }

    private void requireUnreferenced(Connection connection, String tableName,
                                     String idColumn, long id)
            throws SQLException {
        Map<String, List<ForeignKeyColumn>> references = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT fk.owner AS child_owner, fk.constraint_name,
                       fk.table_name, fkc.column_name,
                       pkc.column_name AS parent_column
                FROM all_constraints fk
                JOIN all_cons_columns fkc
                  ON fkc.owner = fk.owner
                 AND fkc.constraint_name = fk.constraint_name
                JOIN all_constraints pk
                  ON pk.owner = fk.r_owner
                 AND pk.constraint_name = fk.r_constraint_name
                JOIN all_cons_columns pkc
                  ON pkc.owner = pk.owner
                 AND pkc.constraint_name = pk.constraint_name
                 AND pkc.position = fkc.position
                WHERE fk.constraint_type = 'R'
                  AND pk.owner = USER
                  AND pk.table_name = ?
                ORDER BY fk.owner, fk.constraint_name, fkc.position
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String constraintName = resultSet.getString("child_owner")
                            + "." + resultSet.getString("constraint_name");
                    references.computeIfAbsent(constraintName,
                            ignored -> new ArrayList<>()).add(
                            new ForeignKeyColumn(
                                    resultSet.getString("child_owner"),
                                    resultSet.getString("table_name"),
                                    resultSet.getString("column_name"),
                                    resultSet.getString("parent_column")));
                }
            }
        }
        for (List<ForeignKeyColumn> columns : references.values()) {
            ForeignKeyColumn first = columns.get(0);
            StringBuilder predicate = new StringBuilder();
            for (ForeignKeyColumn column : columns) {
                if (!first.owner().equals(column.owner())
                        || !first.tableName().equals(column.tableName())) {
                    throw new SQLException(
                            "Invalid Oracle foreign-key metadata.");
                }
                if (predicate.length() > 0) {
                    predicate.append(" AND ");
                }
                predicate.append("c.").append(sqlIdentifier(column.columnName()))
                        .append(" = (SELECT p.")
                        .append(sqlIdentifier(column.parentColumn()))
                        .append(" FROM ")
                        .append(sqlIdentifier(tableName))
                        .append(" p WHERE p.")
                        .append(sqlIdentifier(idColumn)).append(" = ?)");
            }
            String sql = "SELECT COUNT(*) FROM "
                    + sqlIdentifier(first.owner()) + "."
                    + sqlIdentifier(first.tableName()) + " c WHERE "
                    + predicate;
            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {
                for (int index = 0; index < columns.size(); index++) {
                    statement.setLong(index + 1, id);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    requireNoReferences(
                            tableName.substring(0, 1)
                                    + tableName.substring(1).toLowerCase(
                                            java.util.Locale.ROOT)
                                    + " is referenced by "
                                    + first.tableName().toLowerCase(
                                            java.util.Locale.ROOT)
                                    + " records",
                            resultSet.getLong(1));
                }
            }
        }
        requireKnownReferences(connection, tableName, id);
    }

    private void requireKnownReferences(Connection connection,
                                        String tableName, long id)
            throws SQLException {
        if ("WARDS".equals(tableName)
                && tableExists(connection, "BEDS")
                && OracleSchemaInspector.columnExists(
                        connection, "BEDS", "WARD_ID")) {
            requireNoReferences("Ward still contains beds",
                    count(connection,
                            "SELECT COUNT(*) FROM beds WHERE ward_id = ?", id));
        }
        if ("BEDS".equals(tableName)) {
            if (tableExists(connection, "ADMISSIONS")
                    && OracleSchemaInspector.columnExists(
                            connection, "ADMISSIONS", "BED_ID")) {
                requireNoReferences("Bed is referenced by admission history",
                        count(connection,
                                "SELECT COUNT(*) FROM admissions WHERE bed_id = ?",
                                id));
            }
            if (tableExists(connection, "PATIENT_TRANSFERS")) {
                for (String column : List.of("FROM_BED_ID", "TO_BED_ID")) {
                    if (OracleSchemaInspector.columnExists(
                            connection, "PATIENT_TRANSFERS", column)) {
                        requireNoReferences(
                                "Bed is referenced by transfer history",
                                count(connection, """
                                    SELECT COUNT(*) FROM patient_transfers
                                    WHERE %s = ?
                                    """.formatted(column.toLowerCase(
                                        java.util.Locale.ROOT)), id));
                    }
                }
            }
        }
    }

    private String sqlIdentifier(String identifier) throws SQLException {
        if (identifier == null
                || !identifier.matches("[A-Z][A-Z0-9_$#]*")) {
            throw new SQLException(
                    "Unrecognized Oracle identifier in reference metadata.");
        }
        return "\"" + identifier + "\"";
    }

    static void requireNoReferences(String description, long referenceCount) {
        if (referenceCount > 0) {
            throw new IllegalStateException(
                    description + " and cannot be changed or deleted.");
        }
    }

    private record ForeignKeyColumn(String owner, String tableName,
                                    String columnName, String parentColumn) {
    }

    private void requirePermission(Connection connection, long userId,
                                   String permissionCode) throws SQLException {
        if (!tableExists(connection, "ROLE_PERMISSIONS")
                || !tableExists(connection, "PERMISSIONS")) {
            throw new IllegalStateException(
                    "Workflow permissions are not configured in Oracle.");
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
                            "Your account cannot perform this inpatient action.");
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
                ) VALUES (?, ?, ?, ?, 'Inpatient', ?, ?)
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

    private int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private int count(Connection connection, String sql, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
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

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            return tableExists(connection, tableName);
        }
    }

    private boolean tableExists(Connection connection, String tableName)
            throws SQLException {
        return OracleSchemaInspector.tableExists(connection, tableName);
    }

    private boolean columnExists(String tableName, String columnName)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            return OracleSchemaInspector.columnExists(
                    connection, tableName, columnName);
        }
    }

    private String patientNameExpression() throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            return patientNameExpression(connection);
        }
    }

    private String patientNameExpression(Connection connection)
            throws SQLException {
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

    private static String required(String value, String field, int maxLength) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        checkLength(normalized, field, maxLength);
        return normalized;
    }

    static String normalizeWardName(String name) {
        return required(name, "Ward name", 100);
    }

    static String normalizeWardType(String type) {
        String normalized = optional(type);
        checkLength(normalized, "Ward type", 40);
        return normalized;
    }

    static String normalizeBedNumber(String bedNumber) {
        return required(bedNumber, "Bed number", 30);
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
