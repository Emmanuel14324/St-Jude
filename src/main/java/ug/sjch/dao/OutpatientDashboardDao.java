package ug.sjch.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import ug.sjch.model.User;
import ug.sjch.security.UserSession;

public class OutpatientDashboardDao {
    public record Metrics(
            int patientsWaiting,
            Integer consultationsToday,
            Integer completedConsultations,
            Integer followUpAppointments,
            Integer pendingLaboratoryRequests
    ) {
    }

    private record ConsultationCounts(Integer today, Integer completed) {
    }

    private record ConsultationContext(long patientId, long appointmentId) {
    }

    private record PrescriptionContext(
            long patientId, long appointmentId, String status) {
    }

    private record RequestContext(long patientId, long appointmentId,
                                  String status, boolean sampleCollected,
                                  boolean hasResult) {
    }

    public record AppointmentRow(
            long appointmentId,
            String patientNumber,
            String patientName,
            String appointmentTime,
            String status
    ) {
    }

    public record ConsultationRow(
            long consultationId,
            String patientNumber,
            String patientName,
            String consultationTime,
            String status
    ) {
    }

    public record MedicalRecordRow(
            long consultationId,
            String patientNumber,
            String patientName,
            String consultationDate,
            String diagnosis,
            String clinicalNotes,
            String status
    ) {
    }

    public record PrescriptionRow(
            long prescriptionId,
            String patientNumber,
            String patientName,
            String createdAt,
            String status
    ) {
    }

    public record PrescriptionItemRow(
            long prescriptionItemId,
            long medicineId,
            MedicineOption medicine,
            String dosage,
            String route,
            String frequency,
            Integer durationDays,
            BigDecimal quantityPrescribed,
            BigDecimal quantityDispensed,
            String instructions,
            String prescriptionNotes
    ) {
        @Override
        public String toString() {
            return medicine + " — " + dosage + " — " + frequency;
        }
    }

    public record LaboratoryRequestDetails(
            long requestId,
            String testName,
            String instructions
    ) {
    }

    public record MedicineOption(long medicineId, String name, String strength) {
        @Override
        public String toString() {
            return strength == null || strength.isBlank()
                    ? name : name + " (" + strength + ")";
        }
    }

    public record FollowUpRow(
            long followUpId,
            String patientNumber,
            String patientName,
            String followUpDate,
            String status
    ) {
    }

    public record LaboratoryRequestRow(
            long requestId,
            String patientNumber,
            String patientName,
            String testName,
            String requestedAt,
            String status
    ) {
    }

    public record OptionalRows<T>(boolean configured, List<T> rows) {
    }

    public OptionalRows<MedicalRecordRow> loadMedicalRecords(long doctorId)
            throws SQLException {
        if (!tableExists("CONSULTATIONS")) {
            return new OptionalRows<>(false, List.of());
        }
        String sql = """
                SELECT c.consultation_id, p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name) AS patient_name,
                       TO_CHAR(c.consultation_date, 'DD Mon YYYY HH:MI AM')
                           AS consultation_date,
                       c.diagnosis, c.clinical_notes,
                       c.consultation_status AS status
                FROM consultations c
                JOIN patients p ON p.patient_id = c.patient_id
                WHERE c.doctor_id = ?
                ORDER BY c.consultation_date DESC
                FETCH FIRST 100 ROWS ONLY
                """;
        List<MedicalRecordRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new MedicalRecordRow(
                            resultSet.getLong("consultation_id"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("consultation_date"),
                            resultSet.getString("diagnosis"),
                            resultSet.getString("clinical_notes"),
                            resultSet.getString("status")
                    ));
                }
            }
        }
        return new OptionalRows<>(true, rows);
    }

    public OptionalRows<ConsultationRow> loadConsultationHistory(long doctorId)
            throws SQLException {
        if (!tableExists("CONSULTATIONS")) {
            return new OptionalRows<>(false, List.of());
        }
        String sql = """
                SELECT c.consultation_id, p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name) AS patient_name,
                       TO_CHAR(c.consultation_date, 'DD Mon YYYY HH:MI AM')
                           AS consultation_time,
                       c.consultation_status AS status
                FROM consultations c
                JOIN patients p ON p.patient_id = c.patient_id
                WHERE c.doctor_id = ?
                ORDER BY c.consultation_date DESC
                FETCH FIRST 100 ROWS ONLY
                """;
        return loadConsultations(doctorId, sql);
    }

    public OptionalRows<PrescriptionRow> loadPrescriptions(long doctorId)
            throws SQLException {
        if (!tableExists("PRESCRIPTIONS")
                || !tableExists("CONSULTATIONS")) {
            return new OptionalRows<>(false, List.of());
        }
        String sql = """
                SELECT r.prescription_id, p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name) AS patient_name,
                       TO_CHAR(r.created_at, 'DD Mon YYYY HH:MI AM')
                           AS created_at,
                       r.prescription_status AS status
                FROM prescriptions r
                JOIN consultations c
                  ON c.consultation_id = r.consultation_id
                JOIN patients p ON p.patient_id = r.patient_id
                WHERE c.doctor_id = ?
                ORDER BY r.created_at DESC
                FETCH FIRST 100 ROWS ONLY
                """;
        List<PrescriptionRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new PrescriptionRow(
                            resultSet.getLong("prescription_id"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("created_at"),
                            resultSet.getString("status")
                    ));
                }
            }
        }
        return new OptionalRows<>(true, rows);
    }

    public List<PrescriptionItemRow> loadPrescriptionItems(
            long prescriptionId, long doctorId, long userId) throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_PRESCRIBE");
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "PRESCRIPTIONS", "PRESCRIPTION_ITEMS",
                    "MEDICINES", "CONSULTATIONS", "ROLE_PERMISSIONS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_PRESCRIBE");
            String sql = """
                    SELECT pi.prescription_item_id, pi.medicine_id,
                           m.medicine_name, m.strength, pi.dosage, pi.route,
                           pi.frequency, pi.duration_days,
                           pi.quantity_prescribed, pi.quantity_dispensed,
                           pi.instructions, p.notes
                    FROM prescriptions p
                    JOIN consultations c
                      ON c.consultation_id = p.consultation_id
                    JOIN prescription_items pi
                      ON pi.prescription_id = p.prescription_id
                    JOIN medicines m ON m.medicine_id = pi.medicine_id
                    WHERE p.prescription_id = ?
                      AND c.doctor_id = ?
                    ORDER BY pi.prescription_item_id
                    """;
            List<PrescriptionItemRow> rows = new ArrayList<>();
            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {
                statement.setLong(1, prescriptionId);
                statement.setLong(2, doctorId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        int duration = resultSet.getInt("duration_days");
                        boolean durationIsNull = resultSet.wasNull();
                        rows.add(new PrescriptionItemRow(
                                resultSet.getLong("prescription_item_id"),
                                resultSet.getLong("medicine_id"),
                                new MedicineOption(
                                        resultSet.getLong("medicine_id"),
                                        resultSet.getString("medicine_name"),
                                        resultSet.getString("strength")),
                                resultSet.getString("dosage"),
                                resultSet.getString("route"),
                                resultSet.getString("frequency"),
                                durationIsNull ? null : duration,
                                resultSet.getBigDecimal(
                                        "quantity_prescribed"),
                                resultSet.getBigDecimal("quantity_dispensed"),
                                resultSet.getString("instructions"),
                                resultSet.getString("notes")
                        ));
                    }
                }
            }
            return rows;
        }
    }

    public void updatePrescriptionItem(
            long prescriptionId, long prescriptionItemId, long doctorId,
            long userId, long medicineId, String dosage, String route,
            String frequency, Integer durationDays, BigDecimal quantity,
            String instructions, String prescriptionNotes) throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_PRESCRIBE");
        String normalizedDosage = required(dosage, "Dosage", 100);
        String normalizedRoute = required(route, "Route", 60);
        String normalizedFrequency = required(frequency, "Frequency", 100);
        BigDecimal normalizedQuantity =
                positive(quantity, "Prescribed quantity");
        String normalizedInstructions =
                optional(instructions, "Instructions", 1000);
        String normalizedNotes =
                optional(prescriptionNotes, "Prescription notes", 4000);
        validateDuration(durationDays);

        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "PRESCRIPTIONS", "PRESCRIPTION_ITEMS",
                    "DISPENSINGS", "MEDICINES", "CONSULTATIONS",
                    "ROLE_PERMISSIONS", "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_PRESCRIBE");
            connection.setAutoCommit(false);
            try {
                PrescriptionContext context = lockPrescription(
                        connection, prescriptionId, doctorId);
                requireMutablePrescription(connection, prescriptionId,
                        context.status());
                long originalMedicineId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT medicine_id, quantity_dispensed
                        FROM prescription_items
                        WHERE prescription_item_id = ?
                          AND prescription_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, prescriptionItemId);
                    statement.setLong(2, prescriptionId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalArgumentException(
                                    "Select an item belonging to this "
                                            + "prescription.");
                        }
                        originalMedicineId =
                                resultSet.getLong("medicine_id");
                        BigDecimal dispensed =
                                resultSet.getBigDecimal("quantity_dispensed");
                        if (dispensed == null || dispensed.signum() != 0) {
                            throw new IllegalStateException(
                                    "A prescription item cannot be edited "
                                            + "after dispensing.");
                        }
                    }
                }
                if (medicineId != originalMedicineId) {
                    requireActiveMedicine(connection, medicineId);
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE prescription_items
                        SET medicine_id = ?, dosage = ?, route = ?,
                            frequency = ?, duration_days = ?,
                            quantity_prescribed = ?, instructions = ?
                        WHERE prescription_item_id = ?
                          AND prescription_id = ?
                          AND quantity_dispensed = 0
                        """)) {
                    statement.setLong(1, medicineId);
                    statement.setString(2, normalizedDosage);
                    statement.setString(3, normalizedRoute);
                    statement.setString(4, normalizedFrequency);
                    if (durationDays == null) {
                        statement.setNull(5, java.sql.Types.NUMERIC);
                    } else {
                        statement.setInt(5, durationDays);
                    }
                    statement.setBigDecimal(6, normalizedQuantity);
                    statement.setString(7, normalizedInstructions);
                    statement.setLong(8, prescriptionItemId);
                    statement.setLong(9, prescriptionId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "The prescription item changed before it "
                                        + "could be updated.");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE prescriptions
                        SET notes = ?
                        WHERE prescription_id = ?
                          AND prescription_status = 'PENDING'
                        """)) {
                    statement.setString(1, normalizedNotes);
                    statement.setLong(2, prescriptionId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "This prescription is no longer editable.");
                    }
                }
                writeWorkflowEvent(connection, context.patientId(),
                        context.appointmentId(), "PRESCRIPTION",
                        prescriptionId, "PRESCRIPTION_UPDATED", userId,
                        "Prescription item details corrected before "
                                + "dispensing.");
                writeAudit(connection, userId, "OPD_PRESCRIPTION_UPDATED",
                        "Updated an item on prescription " + prescriptionId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void cancelPrescription(long prescriptionId, long doctorId,
                                   long userId) throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_PRESCRIBE");
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "PRESCRIPTIONS", "PRESCRIPTION_ITEMS",
                    "DISPENSINGS", "CONSULTATIONS", "ROLE_PERMISSIONS",
                    "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_PRESCRIBE");
            connection.setAutoCommit(false);
            try {
                PrescriptionContext context = lockPrescription(
                        connection, prescriptionId, doctorId);
                requireMutablePrescription(connection, prescriptionId,
                        context.status());
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE prescriptions
                        SET prescription_status = 'CANCELLED'
                        WHERE prescription_id = ?
                          AND prescription_status = 'PENDING'
                        """)) {
                    statement.setLong(1, prescriptionId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "This prescription is no longer cancellable.");
                    }
                }
                writeWorkflowEvent(connection, context.patientId(),
                        context.appointmentId(), "PRESCRIPTION",
                        prescriptionId, "PRESCRIPTION_CANCELLED", userId,
                        "Prescription cancelled before dispensing.");
                writeAudit(connection, userId, "OPD_PRESCRIPTION_CANCELLED",
                        "Cancelled prescription " + prescriptionId
                                + " before dispensing");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public OptionalRows<MedicineOption> loadActiveMedicines()
            throws SQLException {
        if (!tableExists("MEDICINES")) {
            return new OptionalRows<>(false, List.of());
        }
        String sql = """
                SELECT medicine_id, medicine_name, strength
                FROM medicines
                WHERE is_active = 'Y'
                ORDER BY medicine_name
                """;
        List<MedicineOption> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new MedicineOption(
                        resultSet.getLong("medicine_id"),
                        resultSet.getString("medicine_name"),
                        resultSet.getString("strength")
                ));
            }
        }
        return new OptionalRows<>(true, rows);
    }

    public List<AppointmentRow> loadFollowUpAppointments(long doctorId)
            throws SQLException {
        String sql = """
                SELECT a.appointment_id, p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name) AS patient_name,
                       TO_CHAR(a.appointment_date, 'DD Mon YYYY HH:MI AM')
                           AS appointment_time,
                       a.status
                FROM appointments a
                JOIN patients p ON p.patient_id = a.patient_id
                WHERE a.doctor_id = ?
                  AND a.appointment_date >= SYSTIMESTAMP
                  AND a.status = 'SCHEDULED'
                  AND UPPER(a.reason) LIKE 'FOLLOW-UP:%'
                ORDER BY a.appointment_date
                FETCH FIRST 100 ROWS ONLY
                """;
        List<AppointmentRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new AppointmentRow(
                            resultSet.getLong("appointment_id"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("appointment_time"),
                            resultSet.getString("status")
                    ));
                }
            }
        }
        return rows;
    }

    public long startConsultation(long appointmentId, long doctorId,
                                  long userId) throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_CONSULT");
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "CONSULTATIONS", "ROLE_PERMISSIONS",
                    "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_CONSULT");
            connection.setAutoCommit(false);
            try {
                long patientId;
                String appointmentStatus;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT patient_id, status
                        FROM appointments
                        WHERE appointment_id = ? AND doctor_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, appointmentId);
                    statement.setLong(2, doctorId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalArgumentException(
                                    "The selected appointment is not assigned "
                                            + "to this doctor.");
                        }
                        patientId = resultSet.getLong("patient_id");
                        appointmentStatus = resultSet.getString("status");
                    }
                }

                if ("IN_PROGRESS".equalsIgnoreCase(appointmentStatus)) {
                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                                         SELECT consultation_id
                                         FROM consultations
                                         WHERE appointment_id = ?
                                         """)) {
                        statement.setLong(1, appointmentId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                long existingId =
                                        resultSet.getLong("consultation_id");
                                connection.commit();
                                return existingId;
                            }
                        }
                    }
                }
                if (!"CHECKED_IN".equalsIgnoreCase(appointmentStatus)
                        && !"WAITING".equalsIgnoreCase(appointmentStatus)) {
                    throw new IllegalArgumentException(
                            "Only checked-in or waiting appointments can "
                                    + "start a consultation.");
                }

                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     INSERT INTO consultations (
                                         appointment_id, patient_id, doctor_id,
                                         consultation_date,
                                         consultation_status,
                                         created_by_user_id
                                     ) VALUES (?, ?, ?, SYSTIMESTAMP,
                                               'IN_PROGRESS', ?)
                                     """)) {
                    statement.setLong(1, appointmentId);
                    statement.setLong(2, patientId);
                    statement.setLong(3, doctorId);
                    statement.setLong(4, userId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "The consultation record could not be created.");
                    }
                }

                long consultationId;
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     SELECT consultation_id
                                     FROM consultations
                                     WHERE appointment_id = ?
                                     """)) {
                    statement.setLong(1, appointmentId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SQLException(
                                    "The consultation record could not be "
                                            + "retrieved after creation.");
                        }
                        consultationId =
                                resultSet.getLong("consultation_id");
                    }
                }
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     UPDATE appointments
                                     SET status = 'IN_PROGRESS'
                                     WHERE appointment_id = ?
                                       AND doctor_id = ?
                                     """)) {
                    statement.setLong(1, appointmentId);
                    statement.setLong(2, doctorId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "The appointment status could not be updated.");
                    }
                }
                writeWorkflowEvent(connection, patientId, appointmentId,
                        "CONSULTATION", consultationId,
                        "CONSULTATION_STARTED", userId);
                writeAudit(connection, userId, "OPD_CONSULTATION_STARTED",
                        "Consultation " + consultationId + " started");
                connection.commit();
                return consultationId;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void saveDiagnosis(long consultationId, long doctorId, long userId,
                              String diagnosis, String clinicalNotes)
            throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_CONSULT");
        String normalizedDiagnosis =
                required(diagnosis, "Diagnosis", 4000);
        String normalizedNotes = optional(clinicalNotes, "Clinical notes", 4000);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "CONSULTATIONS", "ROLE_PERMISSIONS",
                    "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_CONSULT");
            connection.setAutoCommit(false);
            try {
                long patientId;
                long appointmentId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT patient_id, appointment_id,
                               consultation_status
                        FROM consultations
                        WHERE consultation_id = ?
                          AND doctor_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, consultationId);
                    statement.setLong(2, doctorId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalArgumentException(
                                    "Select an active consultation assigned "
                                            + "to this doctor.");
                        }
                        patientId = resultSet.getLong("patient_id");
                        appointmentId = resultSet.getLong("appointment_id");
                        if (!isEditableConsultation(
                                resultSet.getString("consultation_status"))) {
                            throw new IllegalStateException(
                                    "Diagnosis and notes can only be edited "
                                            + "while the consultation is in "
                                            + "progress.");
                        }
                    }
                }
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     UPDATE consultations
                                     SET diagnosis = ?, clinical_notes = ?,
                                         updated_at = SYSTIMESTAMP
                                     WHERE consultation_id = ?
                                       AND doctor_id = ?
                                       AND consultation_status = 'IN_PROGRESS'
                                     """)) {
                    statement.setString(1, normalizedDiagnosis);
                    statement.setString(2, normalizedNotes);
                    statement.setLong(3, consultationId);
                    statement.setLong(4, doctorId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "The diagnosis could not be saved.");
                    }
                }
                writeWorkflowEvent(connection, patientId, appointmentId,
                        "CONSULTATION", consultationId, "DIAGNOSIS_RECORDED",
                        userId);
                writeAudit(connection, userId, "OPD_DIAGNOSIS_RECORDED",
                        "Diagnosis recorded for consultation "
                                + consultationId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void completeConsultation(long consultationId, long doctorId,
                                     long userId) throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_CONSULT");
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "CONSULTATIONS", "ROLE_PERMISSIONS",
                    "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_CONSULT");
            connection.setAutoCommit(false);
            try {
                long patientId;
                long appointmentId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT patient_id, appointment_id, diagnosis
                        FROM consultations
                        WHERE consultation_id = ?
                          AND doctor_id = ?
                          AND consultation_status = 'IN_PROGRESS'
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, consultationId);
                    statement.setLong(2, doctorId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalArgumentException(
                                    "Select an active consultation assigned "
                                            + "to this doctor.");
                        }
                        patientId = resultSet.getLong("patient_id");
                        appointmentId = resultSet.getLong("appointment_id");
                        String diagnosis = resultSet.getString("diagnosis");
                        if (diagnosis == null || diagnosis.isBlank()) {
                            throw new IllegalArgumentException(
                                    "Record a diagnosis before completing the "
                                            + "consultation.");
                        }
                    }
                }
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     UPDATE consultations
                                     SET consultation_status = 'COMPLETED',
                                         updated_at = SYSTIMESTAMP
                                     WHERE consultation_id = ?
                                       AND doctor_id = ?
                                     """)) {
                    statement.setLong(1, consultationId);
                    statement.setLong(2, doctorId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "The consultation status could not be updated.");
                    }
                }
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     UPDATE appointments
                                     SET status = 'COMPLETED'
                                     WHERE appointment_id = ?
                                       AND doctor_id = ?
                                     """)) {
                    statement.setLong(1, appointmentId);
                    statement.setLong(2, doctorId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "The appointment status could not be updated.");
                    }
                }
                writeWorkflowEvent(connection, patientId, appointmentId,
                        "CONSULTATION", consultationId,
                        "CONSULTATION_COMPLETED", userId);
                writeAudit(connection, userId, "OPD_CONSULTATION_COMPLETED",
                        "Consultation " + consultationId + " completed");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public long createLaboratoryRequest(long consultationId, long doctorId,
                                        long userId, String testName,
                                        String instructions)
            throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_LAB_REQUEST");
        String normalizedTestName = required(testName, "Test name", 160);
        String normalizedInstructions =
                optional(instructions, "Instructions", 4000);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "CONSULTATIONS", "LAB_REQUESTS",
                    "ROLE_PERMISSIONS", "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_LAB_REQUEST");
            connection.setAutoCommit(false);
            try {
                ConsultationContext context =
                        consultationContext(connection, consultationId, doctorId);
                long requestId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO lab_requests (
                            consultation_id, patient_id, requested_by_staff_id,
                            test_name, instructions, request_status
                        ) VALUES (?, ?, ?, ?, ?, 'REQUESTED')
                        """, new String[] {"LAB_REQUEST_ID"})) {
                    statement.setLong(1, consultationId);
                    statement.setLong(2, context.patientId());
                    statement.setLong(3, doctorId);
                    statement.setString(4, normalizedTestName);
                    statement.setString(5, normalizedInstructions);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "The laboratory request ID was not "
                                            + "returned.");
                        }
                        requestId = keys.getLong(1);
                    }
                }
                writeWorkflowEvent(connection, context.patientId(),
                        context.appointmentId(), "LAB_REQUEST", requestId,
                        "LAB_REQUESTED", userId);
                writeAudit(connection, userId, "OPD_LAB_REQUESTED",
                        "Laboratory request " + requestId + " created");
                connection.commit();
                return requestId;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public long createPrescription(long consultationId, long doctorId,
                                   long userId, long medicineId,
                                   String dosage, String route,
                                   String frequency, Integer durationDays,
                                   BigDecimal quantity, String instructions)
            throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_PRESCRIBE");
        BigDecimal normalizedQuantity =
                positive(quantity, "Prescribed quantity");
        String normalizedDosage = required(dosage, "Dosage", 100);
        String normalizedRoute = required(route, "Route", 60);
        String normalizedFrequency = required(frequency, "Frequency", 100);
        String normalizedInstructions =
                optional(instructions, "Instructions", 1000);
        if (durationDays != null && durationDays <= 0) {
            throw new IllegalArgumentException(
                    "Duration must be a positive number of days.");
        }
        if (durationDays != null && durationDays > 99999) {
            throw new IllegalArgumentException(
                    "Duration exceeds the supported number of days.");
        }
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "CONSULTATIONS", "PRESCRIPTIONS",
                    "PRESCRIPTION_ITEMS", "MEDICINES", "ROLE_PERMISSIONS",
                    "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_PRESCRIBE");
            connection.setAutoCommit(false);
            try {
                ConsultationContext context =
                        consultationContext(connection, consultationId, doctorId);
                requireActiveMedicine(connection, medicineId);
                long prescriptionId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO prescriptions (
                            consultation_id, patient_id, prescribed_by_staff_id,
                            prescription_status, notes
                        ) VALUES (?, ?, ?, 'PENDING', ?)
                        """, new String[] {"PRESCRIPTION_ID"})) {
                    statement.setLong(1, consultationId);
                    statement.setLong(2, context.patientId());
                    statement.setLong(3, doctorId);
                    statement.setString(4, normalizedInstructions);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "The prescription ID was not returned.");
                        }
                        prescriptionId = keys.getLong(1);
                    }
                }
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     INSERT INTO prescription_items (
                                         prescription_id, medicine_id, dosage,
                                         route, frequency, duration_days,
                                         quantity_prescribed,
                                         quantity_dispensed, instructions
                                     ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
                                     """)) {
                    statement.setLong(1, prescriptionId);
                    statement.setLong(2, medicineId);
                    statement.setString(3, normalizedDosage);
                    statement.setString(4, normalizedRoute);
                    statement.setString(5, normalizedFrequency);
                    if (durationDays == null) {
                        statement.setNull(6, java.sql.Types.NUMERIC);
                    } else {
                        statement.setInt(6, durationDays);
                    }
                    statement.setBigDecimal(7, normalizedQuantity);
                    statement.setString(8, normalizedInstructions);
                    statement.executeUpdate();
                }
                writeWorkflowEvent(connection, context.patientId(),
                        context.appointmentId(), "PRESCRIPTION",
                        prescriptionId, "PRESCRIPTION_CREATED", userId);
                writeAudit(connection, userId, "OPD_PRESCRIPTION_CREATED",
                        "Prescription " + prescriptionId + " created");
                connection.commit();
                return prescriptionId;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public long scheduleFollowUp(String patientNumber, long doctorId,
                                 long userId, LocalDateTime appointmentTime,
                                 String reason) throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_CONSULT");
        if (appointmentTime == null) {
            throw new IllegalArgumentException(
                    "Choose a future follow-up date and time.");
        }
        LocalDateTime normalizedAppointmentTime =
                appointmentTime.withSecond(0).withNano(0);
        if (normalizedAppointmentTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Choose a future follow-up date and time.");
        }
        String normalizedPatientNumber =
                required(patientNumber, "Patient number", 30);
        String normalizedReason = optional(reason, "Reason", 489);
        String appointmentReason = normalizedReason == null
                ? "FOLLOW-UP:" : "FOLLOW-UP: " + normalizedReason;
        Timestamp appointmentTimestamp =
                Timestamp.valueOf(normalizedAppointmentTime);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "APPOINTMENTS", "PATIENTS", "STAFF",
                    "ROLE_PERMISSIONS", "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_CONSULT");
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT COUNT(*)
                        FROM appointments
                        WHERE doctor_id = ?
                          AND appointment_date = ?
                          AND status IN (
                              'SCHEDULED', 'CHECKED_IN', 'WAITING',
                              'IN_PROGRESS'
                          )
                        """)) {
                    statement.setLong(1, doctorId);
                    statement.setTimestamp(2, appointmentTimestamp);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        if (resultSet.getInt(1) > 0) {
                            throw new IllegalArgumentException(
                                    "The clinician already has an appointment "
                                            + "at this time.");
                        }
                    }
                }

                int inserted;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO appointments (
                            patient_id, doctor_id, department_id,
                            appointment_date, reason, status
                        )
                        SELECT p.patient_id, s.staff_id, s.department_id,
                               ?, ?, 'SCHEDULED'
                        FROM patients p
                        JOIN staff s ON s.staff_id = ?
                        WHERE UPPER(p.patient_number) = UPPER(?)
                          AND p.status = 'ACTIVE'
                          AND s.status = 'ACTIVE'
                          AND (UPPER(s.job_title) LIKE '%DOCTOR%'
                               OR UPPER(s.job_title) LIKE '%PHYSICIAN%'
                               OR UPPER(s.job_title) LIKE '%MEDICAL OFFICER%')
                        """)) {
                    statement.setTimestamp(1, appointmentTimestamp);
                    statement.setString(2, appointmentReason);
                    statement.setLong(3, doctorId);
                    statement.setString(4, normalizedPatientNumber);
                    inserted = statement.executeUpdate();
                }
                if (inserted != 1) {
                    throw new IllegalArgumentException(
                            "Patient number or active doctor was not found.");
                }

                long appointmentId;
                long patientId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT a.appointment_id, a.patient_id
                        FROM appointments a
                        JOIN patients p ON p.patient_id = a.patient_id
                        WHERE a.doctor_id = ? AND a.appointment_date = ?
                          AND a.reason = ?
                          AND UPPER(p.patient_number) = UPPER(?)
                        ORDER BY a.appointment_id DESC
                        FETCH FIRST 1 ROWS ONLY
                        """)) {
                    statement.setLong(1, doctorId);
                    statement.setTimestamp(2, appointmentTimestamp);
                    statement.setString(3, appointmentReason);
                    statement.setString(4, normalizedPatientNumber);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SQLException(
                                    "The follow-up appointment could not be "
                                            + "retrieved after creation.");
                        }
                        appointmentId =
                                resultSet.getLong("appointment_id");
                        patientId = resultSet.getLong("patient_id");
                    }
                }
                writeWorkflowEvent(connection, patientId, appointmentId,
                        "APPOINTMENT", appointmentId,
                        "FOLLOW_UP_SCHEDULED", userId);
                writeAudit(connection, userId, "OPD_FOLLOW_UP_SCHEDULED",
                        "Follow-up appointment " + appointmentId
                                + " scheduled");
                connection.commit();
                return appointmentId;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void cancelFollowUp(long appointmentId, long doctorId, long userId)
            throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_CONSULT");
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "APPOINTMENTS", "ROLE_PERMISSIONS",
                    "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_CONSULT");
            connection.setAutoCommit(false);
            try {
                long patientId;
                String reason;
                String status;
                LocalDateTime appointmentTime;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT patient_id, reason, status, appointment_date
                        FROM appointments
                        WHERE appointment_id = ?
                          AND doctor_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, appointmentId);
                    statement.setLong(2, doctorId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalArgumentException(
                                    "The selected follow-up is not assigned "
                                            + "to this doctor.");
                        }
                        patientId = resultSet.getLong("patient_id");
                        reason = resultSet.getString("reason");
                        status = resultSet.getString("status");
                        Timestamp timestamp =
                                resultSet.getTimestamp("appointment_date");
                        appointmentTime = timestamp == null ? null
                                : timestamp.toLocalDateTime();
                    }
                }
                if (!isCancellableFollowUp(status, reason, appointmentTime,
                        LocalDateTime.now())) {
                    throw new IllegalStateException(
                            "Only future scheduled follow-up appointments "
                                    + "can be cancelled.");
                }
                if (hasAppointmentHistory(connection, appointmentId)) {
                    throw new IllegalStateException(
                            "A follow-up linked to a consultation or invoice "
                                    + "cannot be cancelled.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE appointments
                        SET status = 'CANCELLED'
                        WHERE appointment_id = ?
                          AND doctor_id = ?
                          AND status = 'SCHEDULED'
                        """)) {
                    statement.setLong(1, appointmentId);
                    statement.setLong(2, doctorId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "The follow-up changed before it could be "
                                        + "cancelled.");
                    }
                }
                writeWorkflowEvent(connection, patientId, appointmentId,
                        "APPOINTMENT", appointmentId,
                        "FOLLOW_UP_CANCELLED", userId,
                        "Future follow-up cancelled; appointment history "
                                + "retained.");
                writeAudit(connection, userId, "OPD_FOLLOW_UP_CANCELLED",
                        "Cancelled follow-up appointment " + appointmentId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    static boolean isCancellableFollowUp(String status, String reason,
                                         LocalDateTime appointmentTime,
                                         LocalDateTime now) {
        return "SCHEDULED".equals(status)
                && reason != null
                && reason.trim().toUpperCase(java.util.Locale.ROOT)
                        .startsWith("FOLLOW-UP:")
                && appointmentTime != null && now != null
                && appointmentTime.isAfter(now);
    }

    private boolean hasAppointmentHistory(Connection connection,
                                          long appointmentId)
            throws SQLException {
        for (String table : List.of("CONSULTATIONS", "INVOICES")) {
            if (OracleSchemaInspector.tableExists(connection, table)
                    && OracleSchemaInspector.columnExists(
                            connection, table, "APPOINTMENT_ID")) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM " + table
                                + " WHERE appointment_id = ?")) {
                    statement.setLong(1, appointmentId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        if (resultSet.getInt(1) > 0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public Metrics loadMetrics(long doctorId) throws SQLException {
        int waitingPatients;
        Integer consultationsToday;
        Integer completedConsultations;
        Integer followUpAppointments;
        Integer pendingLaboratoryRequests;

        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            String appointmentSql = """
                SELECT COUNT(CASE
                           WHEN status IN ('CHECKED_IN', 'WAITING') THEN 1
                       END) AS patients_waiting
                FROM appointments
                WHERE doctor_id = ?
                  AND TRUNC(appointment_date) = TRUNC(SYSDATE)
                """;
            try (PreparedStatement statement =
                         connection.prepareStatement(appointmentSql)) {
                statement.setLong(1, doctorId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    waitingPatients = resultSet.getInt("patients_waiting");
                }
            }

            ConsultationCounts consultationCounts =
                    countConsultations(connection, doctorId);
            consultationsToday = consultationCounts.today();
            completedConsultations = consultationCounts.completed();
            String followUpSql = """
                    SELECT COUNT(*)
                    FROM appointments
                    WHERE doctor_id = ?
                      AND appointment_date >= SYSTIMESTAMP
                      AND status = 'SCHEDULED'
                      AND UPPER(reason) LIKE 'FOLLOW-UP:%'
                    """;
            try (PreparedStatement statement =
                         connection.prepareStatement(followUpSql)) {
                statement.setLong(1, doctorId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    followUpAppointments = resultSet.getInt(1);
                }
            }
            pendingLaboratoryRequests =
                    !tableExists(connection, "CONSULTATIONS")
                            ? null : countOptionalModule(connection,
                    "LAB_REQUESTS", """
                    SELECT COUNT(*)
                    FROM lab_requests l
                    JOIN consultations c
                      ON c.consultation_id = l.consultation_id
                    WHERE c.doctor_id = ?
                      AND l.request_status IN (
                          'REQUESTED', 'SAMPLE_COLLECTED',
                          'IN_PROGRESS', 'RESULT_ENTERED'
                      )
                    """,
                    doctorId);
        }
        return new Metrics(
                waitingPatients,
                consultationsToday,
                completedConsultations,
                followUpAppointments,
                pendingLaboratoryRequests
        );
    }

    public List<AppointmentRow> loadWaitingQueue(long doctorId)
            throws SQLException {
        return loadAppointments(doctorId,
                "a.status IN ('CHECKED_IN', 'WAITING')");
    }

    public OptionalRows<ConsultationRow> loadTodayConsultations(long doctorId)
            throws SQLException {
        if (!tableExists("CONSULTATIONS")) {
            return new OptionalRows<>(false, List.of());
        }
        String sql = """
                SELECT c.consultation_id, p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name) AS patient_name,
                       TO_CHAR(c.consultation_date, 'HH:MI AM')
                           AS consultation_time,
                       c.consultation_status AS status
                FROM consultations c
                JOIN patients p ON p.patient_id = c.patient_id
                WHERE c.doctor_id = ?
                  AND TRUNC(c.consultation_date) = TRUNC(SYSDATE)
                ORDER BY c.consultation_date
                FETCH FIRST 12 ROWS ONLY
                """;
        return loadConsultations(doctorId, sql);
    }

    private OptionalRows<ConsultationRow> loadConsultations(
            long doctorId, String sql) throws SQLException {
        List<ConsultationRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new ConsultationRow(
                            resultSet.getLong("consultation_id"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("consultation_time"),
                            resultSet.getString("status")
                    ));
                }
            }
        }
        return new OptionalRows<>(true, rows);
    }

    private ConsultationContext consultationContext(
            Connection connection, long consultationId, long doctorId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT patient_id, appointment_id
                FROM consultations
                WHERE consultation_id = ?
                  AND doctor_id = ?
                  AND consultation_status = 'IN_PROGRESS'
                FOR UPDATE
                """)) {
            statement.setLong(1, consultationId);
            statement.setLong(2, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Select an active consultation assigned to this "
                                    + "doctor.");
                }
                return new ConsultationContext(
                        resultSet.getLong("patient_id"),
                        resultSet.getLong("appointment_id"));
            }
        }
    }

    private PrescriptionContext lockPrescription(
            Connection connection, long prescriptionId, long doctorId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.patient_id, p.prescription_status, c.appointment_id
                FROM prescriptions p
                JOIN consultations c
                  ON c.consultation_id = p.consultation_id
                WHERE p.prescription_id = ?
                  AND c.doctor_id = ?
                FOR UPDATE OF p.prescription_status
                """)) {
            statement.setLong(1, prescriptionId);
            statement.setLong(2, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "The selected prescription is not assigned to "
                                    + "this doctor.");
                }
                return new PrescriptionContext(
                        resultSet.getLong("patient_id"),
                        resultSet.getLong("appointment_id"),
                        resultSet.getString("prescription_status"));
            }
        }
    }

    private void requireMutablePrescription(Connection connection,
                                            long prescriptionId,
                                            String status) throws SQLException {
        int dispensingCount;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM dispensings
                WHERE prescription_id = ?
                """)) {
            statement.setLong(1, prescriptionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                dispensingCount = resultSet.getInt(1);
            }
        }
        int dispensedItemCount;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM prescription_items
                WHERE prescription_id = ?
                  AND quantity_dispensed > 0
                """)) {
            statement.setLong(1, prescriptionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                dispensedItemCount = resultSet.getInt(1);
            }
        }
        if (!isMutablePrescription(
                status, dispensingCount, dispensedItemCount)) {
            throw new IllegalStateException(
                    "Only pending prescriptions with no dispensing history "
                            + "can be edited or cancelled.");
        }
    }

    private void requireCurrentDoctor(long doctorId, long userId,
                                     String permissionCode) {
        User user = UserSession.getCurrentUser();
        if (!UserSession.isAuthenticated() || user.id() != userId
                || user.staffId() == null || user.staffId() != doctorId) {
            throw new SecurityException(
                    "Sign in with the assigned doctor account to perform "
                            + permissionCode + ".");
        }
    }

    private void requireDoctorPermission(Connection connection, long doctorId,
                                         long userId, String permissionCode)
            throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM users u
                JOIN role_permissions rp ON rp.role_id = u.role_id
                JOIN staff s ON s.staff_id = u.staff_id
                WHERE u.user_id = ?
                  AND u.is_active = 'Y'
                  AND s.staff_id = ?
                  AND s.status = 'ACTIVE'
                  AND rp.permission_code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, doctorId);
            statement.setString(3, permissionCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) != 1) {
                    throw new SecurityException(
                            "Your account cannot perform this outpatient "
                                    + "action.");
                }
            }
        }
    }

    private void requireActiveMedicine(Connection connection, long medicineId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT medicine_id
                FROM medicines
                WHERE medicine_id = ? AND is_active = 'Y'
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

    private static void validateDuration(Integer durationDays) {
        if (durationDays != null && durationDays <= 0) {
            throw new IllegalArgumentException(
                    "Duration must be a positive number of days.");
        }
        if (durationDays != null && durationDays > 99999) {
            throw new IllegalArgumentException(
                    "Duration exceeds the supported number of days.");
        }
    }

    private void writeWorkflowEvent(Connection connection, long patientId,
                                    long appointmentId, String entityType,
                                    long entityId, String eventType,
                                    long userId) throws SQLException {
        writeWorkflowEvent(connection, patientId, appointmentId, entityType,
                entityId, eventType, userId, null);
    }

    private void writeWorkflowEvent(Connection connection, long patientId,
                                    long appointmentId, String entityType,
                                    long entityId, String eventType,
                                    long userId, String details)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workflow_events (
                    patient_id, appointment_id, entity_type, entity_id,
                    event_type, department_name, performed_by_user_id, details
                ) VALUES (?, ?, ?, ?, ?, 'Outpatient', ?, ?)
                """)) {
            statement.setLong(1, patientId);
            statement.setLong(2, appointmentId);
            statement.setString(3, entityType);
            statement.setLong(4, entityId);
            statement.setString(5, eventType);
            statement.setLong(6, userId);
            statement.setString(7, details);
            statement.executeUpdate();
        }
    }

    private void writeAudit(Connection connection, long userId, String action,
                            String description) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_logs (user_id, action, description)
                VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, action);
            statement.setString(3, description);
            statement.executeUpdate();
        }
    }

    private void requireTables(Connection connection, String... tableNames)
            throws SQLException {
        for (String tableName : tableNames) {
            if (!tableExists(connection, tableName)) {
                throw new IllegalStateException(
                        tableName + " is not configured in Oracle.");
            }
        }
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 10) {
                throw new IllegalArgumentException(
                        field + " exceeds the supported quantity.");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    field + " supports at most two decimal places.");
        }
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = optional(value, field, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return normalized;
    }

    private static String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be no more than " + maxLength
                            + " characters.");
        }
        return normalized;
    }

    private static void rollback(Connection connection, Exception exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    private List<AppointmentRow> loadAppointments(long doctorId,
                                                   String statusPredicate)
            throws SQLException {
        String sql = """
                SELECT a.appointment_id, p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name) AS patient_name,
                       TO_CHAR(a.appointment_date, 'HH:MI AM')
                           AS appointment_time,
                       a.status
                FROM appointments a
                JOIN patients p ON p.patient_id = a.patient_id
                WHERE a.doctor_id = ?
                  AND TRUNC(a.appointment_date) = TRUNC(SYSDATE)
                  AND %s
                ORDER BY a.appointment_date
                FETCH FIRST 12 ROWS ONLY
                """.formatted(statusPredicate);
        List<AppointmentRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new AppointmentRow(
                            resultSet.getLong("appointment_id"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("appointment_time"),
                            resultSet.getString("status")
                    ));
                }
            }
        }
        return rows;
    }

    public OptionalRows<FollowUpRow> loadFollowUps(long doctorId)
            throws SQLException {
        List<FollowUpRow> rows = loadFollowUpAppointments(doctorId).stream()
                .map(appointment -> new FollowUpRow(
                        appointment.appointmentId(),
                        appointment.patientNumber(),
                        appointment.patientName(),
                        appointment.appointmentTime(),
                        appointment.status()
                ))
                .toList();
        return new OptionalRows<>(true, rows);
    }

    public OptionalRows<LaboratoryRequestRow> loadPendingLaboratoryRequests(
            long doctorId) throws SQLException {
        String sql = """
                SELECT l.lab_request_id, p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name) AS patient_name,
                       l.test_name,
                       TO_CHAR(l.requested_at, 'DD Mon YYYY HH:MI AM')
                           AS requested_at,
                       l.request_status AS status
                FROM lab_requests l
                JOIN consultations c
                  ON c.consultation_id = l.consultation_id
                JOIN patients p ON p.patient_id = l.patient_id
                WHERE c.doctor_id = ?
                  AND l.request_status IN (
                      'REQUESTED', 'SAMPLE_COLLECTED',
                      'IN_PROGRESS', 'RESULT_ENTERED'
                  )
                ORDER BY l.requested_at
                FETCH FIRST 6 ROWS ONLY
                """;
        if (!tableExists("LAB_REQUESTS")
                || !tableExists("CONSULTATIONS")) {
            return new OptionalRows<>(false, List.of());
        }

        List<LaboratoryRequestRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new LaboratoryRequestRow(
                            resultSet.getLong("lab_request_id"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("test_name"),
                            resultSet.getString("requested_at"),
                            resultSet.getString("status")
                    ));
                }
            }
        }
        return new OptionalRows<>(true, rows);
    }

    public LaboratoryRequestDetails loadLaboratoryRequestDetails(
            long requestId, long doctorId, long userId) throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_LAB_REQUEST");
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "LAB_REQUESTS", "LAB_RESULTS",
                    "CONSULTATIONS", "ROLE_PERMISSIONS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_LAB_REQUEST");
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT l.lab_request_id, l.test_name, l.instructions,
                           l.request_status, l.sample_collected_at,
                           (SELECT COUNT(*) FROM lab_results r
                            WHERE r.lab_request_id = l.lab_request_id)
                               AS result_count
                    FROM lab_requests l
                    JOIN consultations c
                      ON c.consultation_id = l.consultation_id
                    WHERE l.lab_request_id = ?
                      AND c.doctor_id = ?
                    """)) {
                statement.setLong(1, requestId);
                statement.setLong(2, doctorId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalArgumentException(
                                "The selected laboratory request is not "
                                        + "assigned to this doctor.");
                    }
                    if (!isMutableLaboratoryRequest(
                            resultSet.getString("request_status"),
                            resultSet.getTimestamp(
                                    "sample_collected_at") != null,
                            resultSet.getInt("result_count") > 0)) {
                        throw new IllegalStateException(
                                "Only requests awaiting sample collection "
                                        + "can be edited.");
                    }
                    return new LaboratoryRequestDetails(
                            resultSet.getLong("lab_request_id"),
                            resultSet.getString("test_name"),
                            resultSet.getString("instructions"));
                }
            }
        }
    }

    public void updateLaboratoryRequest(long requestId, long doctorId,
                                        long userId, String testName,
                                        String instructions)
            throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_LAB_REQUEST");
        String normalizedTestName = required(testName, "Test name", 160);
        String normalizedInstructions =
                optional(instructions, "Instructions", 4000);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "LAB_REQUESTS", "LAB_RESULTS",
                    "CONSULTATIONS",
                    "ROLE_PERMISSIONS", "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_LAB_REQUEST");
            connection.setAutoCommit(false);
            try {
                RequestContext context = lockLaboratoryRequest(
                        connection, requestId, doctorId);
                if (!isMutableLaboratoryRequest(
                        context.status(), context.sampleCollected(),
                        context.hasResult())) {
                    throw new IllegalStateException(
                            "Only requests awaiting sample collection can "
                                    + "be edited.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE lab_requests
                        SET test_name = ?, instructions = ?
                        WHERE lab_request_id = ?
                          AND request_status = 'REQUESTED'
                          AND sample_collected_at IS NULL
                        """)) {
                    statement.setString(1, normalizedTestName);
                    statement.setString(2, normalizedInstructions);
                    statement.setLong(3, requestId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "The laboratory request changed before it "
                                        + "could be updated.");
                    }
                }
                writeWorkflowEvent(connection, context.patientId(),
                        context.appointmentId(), "LAB_REQUEST", requestId,
                        "LAB_REQUEST_UPDATED", userId,
                        "Request details corrected before sample collection.");
                writeAudit(connection, userId, "OPD_LAB_REQUEST_UPDATED",
                        "Updated laboratory request " + requestId
                                + " before sample collection");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void cancelLaboratoryRequest(long requestId, long doctorId,
                                        long userId) throws SQLException {
        requireCurrentDoctor(doctorId, userId, "OPD_LAB_REQUEST");
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            requireTables(connection, "LAB_REQUESTS", "LAB_RESULTS",
                    "CONSULTATIONS",
                    "ROLE_PERMISSIONS", "AUDIT_LOGS", "WORKFLOW_EVENTS");
            requireDoctorPermission(connection, doctorId, userId,
                    "OPD_LAB_REQUEST");
            connection.setAutoCommit(false);
            try {
                RequestContext context = lockLaboratoryRequest(
                        connection, requestId, doctorId);
                if (!isMutableLaboratoryRequest(
                        context.status(), context.sampleCollected(),
                        context.hasResult())) {
                    throw new IllegalStateException(
                            "Only requests awaiting sample collection can "
                                    + "be cancelled.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE lab_requests
                        SET request_status = 'CANCELLED'
                        WHERE lab_request_id = ?
                          AND request_status = 'REQUESTED'
                          AND sample_collected_at IS NULL
                        """)) {
                    statement.setLong(1, requestId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "The laboratory request is no longer "
                                        + "cancellable.");
                    }
                }
                writeWorkflowEvent(connection, context.patientId(),
                        context.appointmentId(), "LAB_REQUEST", requestId,
                        "LAB_REQUEST_CANCELLED", userId,
                        "Request cancelled before sample collection.");
                writeAudit(connection, userId, "OPD_LAB_REQUEST_CANCELLED",
                        "Cancelled laboratory request " + requestId
                                + " before sample collection");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    static boolean isEditableConsultation(String status) {
        return "IN_PROGRESS".equals(status);
    }

    static boolean isMutableLaboratoryRequest(String status,
                                              boolean sampleCollected,
                                              boolean hasResult) {
        return "REQUESTED".equals(status) && !sampleCollected && !hasResult;
    }

    static boolean isMutablePrescription(String status, int dispensingCount,
                                         int dispensedItemCount) {
        return "PENDING".equals(status)
                && dispensingCount == 0 && dispensedItemCount == 0;
    }

    private RequestContext lockLaboratoryRequest(
            Connection connection, long requestId, long doctorId)
            throws SQLException {
        long patientId;
        long appointmentId;
        String status;
        boolean sampleCollected;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT l.patient_id, c.appointment_id, l.request_status,
                       l.sample_collected_at
                FROM lab_requests l
                JOIN consultations c
                  ON c.consultation_id = l.consultation_id
                WHERE l.lab_request_id = ?
                  AND c.doctor_id = ?
                FOR UPDATE OF l.request_status
                """)) {
            statement.setLong(1, requestId);
            statement.setLong(2, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "The selected laboratory request is not "
                                    + "assigned to this doctor.");
                }
                patientId = resultSet.getLong("patient_id");
                appointmentId = resultSet.getLong("appointment_id");
                status = resultSet.getString("request_status");
                sampleCollected =
                        resultSet.getTimestamp("sample_collected_at") != null;
            }
        }
        return new RequestContext(patientId, appointmentId, status,
                sampleCollected, hasLaboratoryResult(connection, requestId));
    }

    private boolean hasLaboratoryResult(Connection connection, long requestId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM lab_results
                WHERE lab_request_id = ?
                """)) {
            statement.setLong(1, requestId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private ConsultationCounts countConsultations(
            Connection connection,
            long doctorId
    )
            throws SQLException {
        if (!tableExists(connection, "CONSULTATIONS")) {
            return new ConsultationCounts(null, null);
        }
        String sql = """
                SELECT COUNT(*) AS consultations_today,
                       COUNT(CASE
                                 WHEN consultation_status = 'COMPLETED'
                                 THEN 1
                             END)
                           AS completed_consultations
                FROM consultations
                WHERE doctor_id = ?
                  AND TRUNC(consultation_date) = TRUNC(SYSDATE)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new ConsultationCounts(
                        resultSet.getInt("consultations_today"),
                        resultSet.getInt("completed_consultations")
                );
            }
        }
    }

    private Integer countOptionalModule(Connection connection, String tableName,
                                        String sql, long doctorId)
            throws SQLException {
        if (!tableExists(connection, tableName)) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
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
        String sql = "SELECT COUNT(*) FROM user_tables WHERE table_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }
}
