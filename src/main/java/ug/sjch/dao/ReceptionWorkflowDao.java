package ug.sjch.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ReceptionWorkflowDao {
    public record PatientRecord(
            long patientId,
            String patientNumber,
            String name,
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            String phone,
            String email,
            String address,
            String emergencyContact,
            String emergencyPhone,
            String status,
            String registeredAt
    ) {
    }

    public record DoctorOption(
            long staffId,
            long departmentId,
            String name,
            String departmentName
    ) {
        @Override
        public String toString() {
            return name + " - " + departmentName;
        }
    }

    public record AppointmentRecord(
            long appointmentId,
            String appointmentTime,
            String patientNumber,
            String patientName,
            String doctorName,
            String departmentName,
            String status,
            LocalDateTime appointmentDateTime,
            Long doctorId,
            Long departmentId,
            String reason
    ) {
    }

    public record WorkflowEventRecord(
            String occurredAt,
            String patientNumber,
            String departmentName,
            String eventType
    ) {
    }

    public PatientRecord registerPatient(
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            String phone,
            String email,
            String address,
            String emergencyContact,
            String emergencyPhone,
            long userId
    ) throws SQLException {
        String normalizedFirstName = required(firstName, "First name", 60);
        String normalizedLastName = required(lastName, "Last name", 60);
        String normalizedGender = optional(gender);
        if (normalizedGender != null
                && !List.of("Male", "Female", "Other")
                .contains(normalizedGender)) {
            throw new IllegalArgumentException("Select a valid gender.");
        }
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Date of birth cannot be in the future.");
        }
        String normalizedPhone = optional(phone);
        String normalizedEmail = optional(email);
        String normalizedAddress = optional(address);
        String normalizedEmergencyContact = optional(emergencyContact);
        String normalizedEmergencyPhone = optional(emergencyPhone);
        checkLength(normalizedPhone, "Phone", 20);
        checkLength(normalizedEmail, "Email", 120);
        checkLength(normalizedAddress, "Address", 250);
        checkLength(normalizedEmergencyContact, "Emergency contact", 120);
        checkLength(normalizedEmergencyPhone, "Emergency phone", 20);

        String patientNumber = "SJCH-" + UUID.randomUUID()
                .toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        String sql = """
                INSERT INTO patients (
                    patient_number, first_name, last_name, date_of_birth,
                    gender, phone, email, address, emergency_contact,
                    emergency_phone, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """;
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {
                    statement.setString(1, patientNumber);
                    statement.setString(2, normalizedFirstName);
                    statement.setString(3, normalizedLastName);
                    if (dateOfBirth == null) {
                        statement.setNull(4, java.sql.Types.DATE);
                    } else {
                        statement.setDate(4, Date.valueOf(dateOfBirth));
                    }
                    statement.setString(5, normalizedGender);
                    statement.setString(6, normalizedPhone);
                    statement.setString(7, normalizedEmail);
                    statement.setString(8, normalizedAddress);
                    statement.setString(9, normalizedEmergencyContact);
                    statement.setString(10, normalizedEmergencyPhone);
                    statement.executeUpdate();
                }

                PatientRecord patient = findPatient(connection, patientNumber);
                insertWorkflowEvent(connection, patient.patientId(), null,
                        "PATIENT", patient.patientId(), "PATIENT_REGISTERED",
                        "Reception", userId, "Patient registered");
                connection.commit();
                return patient;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public List<PatientRecord> searchPatients(String searchText)
            throws SQLException {
        String query = optional(searchText);
        String sql = """
                SELECT patient_id, patient_number, first_name, last_name,
                       TRIM(first_name || ' ' || last_name) AS patient_name,
                       date_of_birth, gender, phone, email, address,
                       emergency_contact, emergency_phone, status,
                       TO_CHAR(registered_at, 'DD Mon YYYY HH:MI AM')
                           AS registered_at
                FROM patients
                %s
                ORDER BY registered_at DESC NULLS LAST, patient_id DESC
                FETCH FIRST 100 ROWS ONLY
                """.formatted(query == null ? "" : """
                WHERE LOWER(patient_number) LIKE LOWER(?)
                   OR LOWER(first_name || ' ' || last_name) LIKE LOWER(?)
                   OR LOWER(phone) LIKE LOWER(?)
                   OR LOWER(email) LIKE LOWER(?)
                """);
        List<PatientRecord> patients = new ArrayList<>();
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (query != null) {
                String pattern = "%" + query + "%";
                for (int index = 1; index <= 4; index++) {
                    statement.setString(index, pattern);
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    patients.add(mapPatient(resultSet));
                }
            }
        }
        return patients;
    }

    public PatientRecord findPatientByNumber(String patientNumber)
            throws SQLException {
        String normalizedPatientNumber =
                required(patientNumber, "Patient number", 30);
        String sql = """
                SELECT patient_id, patient_number, first_name, last_name,
                       TRIM(first_name || ' ' || last_name) AS patient_name,
                       date_of_birth, gender, phone, email, address,
                       emergency_contact, emergency_phone, status,
                       TO_CHAR(registered_at, 'DD Mon YYYY HH:MI AM')
                           AS registered_at
                FROM patients
                WHERE UPPER(patient_number) = UPPER(?)
                """;
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedPatientNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Patient number was not found.");
                }
                return mapPatient(resultSet);
            }
        }
    }

    public List<DoctorOption> loadDoctors() throws SQLException {
        String sql = """
                SELECT s.staff_id, s.department_id,
                       TRIM(s.first_name || ' ' || s.last_name) AS doctor_name,
                       d.department_name
                FROM staff s
                JOIN departments d ON d.department_id = s.department_id
                WHERE s.status = 'ACTIVE'
                  AND (UPPER(s.job_title) LIKE '%DOCTOR%'
                       OR UPPER(s.job_title) LIKE '%PHYSICIAN%'
                       OR UPPER(s.job_title) LIKE '%MEDICAL OFFICER%')
                ORDER BY doctor_name
                """;
        List<DoctorOption> doctors = new ArrayList<>();
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                doctors.add(new DoctorOption(
                        resultSet.getLong("staff_id"),
                        resultSet.getLong("department_id"),
                        resultSet.getString("doctor_name"),
                        resultSet.getString("department_name")
                ));
            }
        }
        return doctors;
    }

    public void bookAppointment(String patientNumber, long doctorId,
                                LocalDateTime appointmentDate,
                                String reason, long userId)
            throws SQLException {
        String normalizedPatientNumber =
                required(patientNumber, "Patient number", 30);
        if (appointmentDate == null
                || appointmentDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Choose a future appointment date and time.");
        }
        String normalizedReason = optional(reason);
        checkLength(normalizedReason, "Reason", 500);
        Timestamp appointmentTimestamp =
                Timestamp.valueOf(appointmentDate.withSecond(0).withNano(0));
        String conflictSql = """
                SELECT COUNT(*)
                FROM appointments
                WHERE doctor_id = ?
                  AND appointment_date = ?
                  AND status IN (
                      'SCHEDULED', 'CHECKED_IN', 'WAITING', 'IN_PROGRESS'
                  )
                """;
        String insertSql = """
                INSERT INTO appointments (
                    patient_id, doctor_id, department_id,
                    appointment_date, reason, status
                )
                SELECT p.patient_id, s.staff_id, s.department_id,
                       ?, ?, 'SCHEDULED'
                FROM patients p
                JOIN staff s ON s.staff_id = ?
                WHERE p.patient_number = ?
                  AND p.status = 'ACTIVE'
                  AND s.status = 'ACTIVE'
                  AND (UPPER(s.job_title) LIKE '%DOCTOR%'
                       OR UPPER(s.job_title) LIKE '%PHYSICIAN%'
                       OR UPPER(s.job_title) LIKE '%MEDICAL OFFICER%')
                """;
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement =
                             connection.prepareStatement(conflictSql)) {
                    statement.setLong(1, doctorId);
                    statement.setTimestamp(2, appointmentTimestamp);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        if (resultSet.getInt(1) > 0) {
                            throw new IllegalArgumentException(
                                    "That clinician already has an appointment "
                                            + "at this time.");
                        }
                    }
                }

                int inserted;
                try (PreparedStatement statement =
                             connection.prepareStatement(insertSql)) {
                    statement.setTimestamp(1, appointmentTimestamp);
                    statement.setString(2, normalizedReason);
                    statement.setLong(3, doctorId);
                    statement.setString(4, normalizedPatientNumber);
                    inserted = statement.executeUpdate();
                }
                if (inserted != 1) {
                    throw new IllegalArgumentException(
                            "Patient number or active clinician was not found.");
                }

                long appointmentId;
                long patientId;
                String idSql = """
                        SELECT a.appointment_id, a.patient_id
                        FROM appointments a
                        JOIN patients p ON p.patient_id = a.patient_id
                        WHERE p.patient_number = ?
                          AND a.doctor_id = ?
                          AND a.appointment_date = ?
                        ORDER BY a.appointment_id DESC
                        FETCH FIRST 1 ROWS ONLY
                        """;
                try (PreparedStatement statement =
                             connection.prepareStatement(idSql)) {
                    statement.setString(1, normalizedPatientNumber);
                    statement.setLong(2, doctorId);
                    statement.setTimestamp(3, appointmentTimestamp);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SQLException(
                                    "Booked appointment could not be retrieved.");
                        }
                        appointmentId = resultSet.getLong("appointment_id");
                        patientId = resultSet.getLong("patient_id");
                    }
                }
                insertWorkflowEvent(connection, patientId, appointmentId,
                        "APPOINTMENT", appointmentId, "APPOINTMENT_BOOKED",
                        "Reception", userId, "Appointment booked");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public List<AppointmentRecord> loadAppointments(LocalDate fromDate,
                                                    LocalDate toDate)
            throws SQLException {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException(
                    "Choose a valid appointment date range.");
        }
        String sql = """
                SELECT a.appointment_id, a.appointment_date,
                       a.doctor_id, a.department_id, a.reason,
                       TO_CHAR(a.appointment_date, 'DD Mon YYYY HH:MI AM')
                           AS appointment_time,
                       p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name)
                           AS patient_name,
                       TRIM(s.first_name || ' ' || s.last_name) AS doctor_name,
                       d.department_name, a.status
                FROM appointments a
                JOIN patients p ON p.patient_id = a.patient_id
                LEFT JOIN staff s ON s.staff_id = a.doctor_id
                LEFT JOIN departments d ON d.department_id = a.department_id
                WHERE a.appointment_date >= ?
                  AND a.appointment_date < ?
                ORDER BY a.appointment_date
                FETCH FIRST 200 ROWS ONLY
                """;
        return loadAppointments(sql, statement -> {
            statement.setTimestamp(1,
                    Timestamp.valueOf(fromDate.atStartOfDay()));
            statement.setTimestamp(2,
                    Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        });
    }

    public List<AppointmentRecord> loadTodayCheckIns() throws SQLException {
        String sql = """
                SELECT a.appointment_id, a.appointment_date,
                       a.doctor_id, a.department_id, a.reason,
                       TO_CHAR(a.appointment_date, 'DD Mon YYYY HH:MI AM')
                           AS appointment_time,
                       p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name)
                           AS patient_name,
                       TRIM(s.first_name || ' ' || s.last_name) AS doctor_name,
                       d.department_name, a.status
                FROM appointments a
                JOIN patients p ON p.patient_id = a.patient_id
                LEFT JOIN staff s ON s.staff_id = a.doctor_id
                LEFT JOIN departments d ON d.department_id = a.department_id
                WHERE TRUNC(a.appointment_date) = TRUNC(SYSDATE)
                  AND a.status IN ('SCHEDULED', 'CHECKED_IN')
                ORDER BY a.appointment_date
                FETCH FIRST 200 ROWS ONLY
                """;
        return loadAppointments(sql, statement -> { });
    }

    public void checkInAppointment(long appointmentId, long userId)
            throws SQLException {
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                long patientId;
                String findSql = """
                        SELECT patient_id
                        FROM appointments
                        WHERE appointment_id = ?
                          AND TRUNC(appointment_date) = TRUNC(SYSDATE)
                          AND status = 'SCHEDULED'
                        FOR UPDATE
                        """;
                try (PreparedStatement statement =
                             connection.prepareStatement(findSql)) {
                    statement.setLong(1, appointmentId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalArgumentException(
                                    "Only today's scheduled appointments can "
                                            + "be checked in.");
                        }
                        patientId = resultSet.getLong("patient_id");
                    }
                }

                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     UPDATE appointments
                                     SET status = 'CHECKED_IN'
                                     WHERE appointment_id = ?
                                       AND status = 'SCHEDULED'
                                     """)) {
                    statement.setLong(1, appointmentId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "The appointment status changed. Refresh and "
                                        + "try again.");
                    }
                }
                insertWorkflowEvent(connection, patientId, appointmentId,
                        "APPOINTMENT", appointmentId, "PATIENT_CHECKED_IN",
                        "Reception", userId, "Patient checked in");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public List<WorkflowEventRecord> loadRecentWorkflowEvents()
            throws SQLException {
        String sql = """
                SELECT TO_CHAR(e.occurred_at, 'DD Mon YYYY HH:MI AM')
                           AS occurred_at,
                       p.patient_number, e.department_name, e.event_type
                FROM workflow_events e
                JOIN patients p ON p.patient_id = e.patient_id
                WHERE e.occurred_at >= SYSTIMESTAMP - INTERVAL '30' DAY
                ORDER BY e.occurred_at DESC
                FETCH FIRST 100 ROWS ONLY
                """;
        List<WorkflowEventRecord> events = new ArrayList<>();
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            if (!OracleSchemaInspector.tableExists(connection,
                    "WORKFLOW_EVENTS")) {
                throw new SQLException(
                        "Workflow notifications are not configured.");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(new WorkflowEventRecord(
                            resultSet.getString("occurred_at"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("department_name"),
                            resultSet.getString("event_type")
                    ));
                }
            }
        }
        return events;
    }

    public void updatePatientDetails(long patientId, String firstName,
                                     String lastName, LocalDate dateOfBirth,
                                     String gender, String phone, String email,
                                     String address, String emergencyContact,
                                     String emergencyPhone, long userId)
            throws SQLException {
        String normalizedFirstName = required(firstName, "First name", 60);
        String normalizedLastName = required(lastName, "Last name", 60);
        String normalizedGender = optional(gender);
        if (normalizedGender != null
                && !List.of("Male", "Female", "Other")
                .contains(normalizedGender)) {
            throw new IllegalArgumentException("Select a valid gender.");
        }
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Date of birth cannot be in the future.");
        }
        String normalizedPhone = optional(phone);
        String normalizedEmail = optional(email);
        String normalizedAddress = optional(address);
        String normalizedEmergencyContact = optional(emergencyContact);
        String normalizedEmergencyPhone = optional(emergencyPhone);
        checkLength(normalizedPhone, "Phone", 20);
        checkLength(normalizedEmail, "Email", 120);
        checkLength(normalizedAddress, "Address", 250);
        checkLength(normalizedEmergencyContact, "Emergency contact", 120);
        checkLength(normalizedEmergencyPhone, "Emergency phone", 20);
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     UPDATE patients
                                     SET first_name = ?, last_name = ?,
                                         date_of_birth = ?, gender = ?,
                                         phone = ?, email = ?, address = ?,
                                         emergency_contact = ?,
                                         emergency_phone = ?
                                     WHERE patient_id = ?
                                       AND status = 'ACTIVE'
                                     """)) {
                    statement.setString(1, normalizedFirstName);
                    statement.setString(2, normalizedLastName);
                    if (dateOfBirth == null) {
                        statement.setNull(3, java.sql.Types.DATE);
                    } else {
                        statement.setDate(3, Date.valueOf(dateOfBirth));
                    }
                    statement.setString(4, normalizedGender);
                    statement.setString(5, normalizedPhone);
                    statement.setString(6, normalizedEmail);
                    statement.setString(7, normalizedAddress);
                    statement.setString(8, normalizedEmergencyContact);
                    statement.setString(9, normalizedEmergencyPhone);
                    statement.setLong(10, patientId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "The selected patient is no longer active.");
                    }
                }
                insertWorkflowEvent(connection, patientId, null, "PATIENT",
                        patientId, "PATIENT_DETAILS_UPDATED", "Reception",
                        userId, "Patient demographic and contact details updated");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void archivePatient(long patientId, long userId)
                throws SQLException {
            try (Connection connection =
                         DatabaseConnectionFactory.openConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT status
                            FROM patients
                            WHERE patient_id = ?
                            FOR UPDATE
                            """)) {
                        statement.setLong(1, patientId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()
                                    || !"ACTIVE".equalsIgnoreCase(
                                            resultSet.getString("status"))) {
                                throw new IllegalArgumentException(
                                        "The selected patient is not active.");
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT COUNT(*)
                            FROM appointments
                            WHERE patient_id = ?
                              AND (status IN (
                                  'CHECKED_IN', 'WAITING', 'IN_PROGRESS'
                              ) OR (status = 'SCHEDULED'
                                    AND appointment_date > SYSDATE))
                            """)) {
                        statement.setLong(1, patientId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            if (resultSet.getInt(1) > 0) {
                                throw new IllegalArgumentException(
                                        "A patient with an active visit cannot be "
                                                + "archived.");
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE patients
                            SET status = 'INACTIVE'
                            WHERE patient_id = ? AND status = 'ACTIVE'
                            """)) {
                        statement.setLong(1, patientId);
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalArgumentException(
                                    "The patient status changed. Refresh and retry.");
                        }
                    }
                    insertWorkflowEvent(connection, patientId, null, "PATIENT",
                            patientId, "PATIENT_ARCHIVED", "Reception", userId,
                            "Patient archived; history retained");
                    connection.commit();
                } catch (SQLException | RuntimeException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            }
        }

        public void updateAppointmentDetails(long appointmentId, long doctorId,
                                             LocalDateTime appointmentDateTime,
                                             String reason, long userId)
                throws SQLException {
            if (appointmentDateTime == null
                    || !appointmentDateTime.isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException(
                        "Choose a future appointment date and time.");
            }
            String normalizedReason = optional(reason);
            checkLength(normalizedReason, "Reason", 500);
            Timestamp appointmentTimestamp =
                    Timestamp.valueOf(appointmentDateTime.withSecond(0).withNano(0));
            try (Connection connection =
                         DatabaseConnectionFactory.openConnection()) {
                connection.setAutoCommit(false);
                try {
                    long patientId;
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT patient_id, status, appointment_date
                            FROM appointments
                            WHERE appointment_id = ?
                            FOR UPDATE
                            """)) {
                        statement.setLong(1, appointmentId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                throw new IllegalArgumentException(
                                        "The selected appointment no longer exists.");
                            }
                            Timestamp currentTime =
                                    resultSet.getTimestamp("appointment_date");
                            if (!canEditAppointment(
                                    resultSet.getString("status"),
                                    currentTime == null ? null
                                            : currentTime.toLocalDateTime(),
                                    LocalDateTime.now())) {
                                throw new IllegalArgumentException(
                                        "Only future scheduled appointments can be edited.");
                            }
                            patientId = resultSet.getLong("patient_id");
                        }
                    }
                    if (hasAppointmentHistory(connection, appointmentId)) {
                        throw new IllegalArgumentException(
                                "An appointment linked to a consultation or invoice "
                                        + "cannot be edited.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT COUNT(*)
                            FROM appointments
                            WHERE doctor_id = ?
                              AND appointment_date = ?
                              AND appointment_id <> ?
                              AND status IN (
                                  'SCHEDULED', 'CHECKED_IN', 'WAITING', 'IN_PROGRESS'
                              )
                            """)) {
                        statement.setLong(1, doctorId);
                        statement.setTimestamp(2, appointmentTimestamp);
                        statement.setLong(3, appointmentId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            if (resultSet.getInt(1) > 0) {
                                throw new IllegalArgumentException(
                                        "That clinician already has an appointment "
                                                + "at this time.");
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE appointments a
                            SET (doctor_id, department_id, appointment_date, reason) =
                                (SELECT s.staff_id, s.department_id, ?, ?
                                 FROM staff s
                                 WHERE s.staff_id = ?
                                   AND s.status = 'ACTIVE'
                                   AND (UPPER(s.job_title) LIKE '%DOCTOR%'
                                        OR UPPER(s.job_title) LIKE '%PHYSICIAN%'
                                        OR UPPER(s.job_title) LIKE '%MEDICAL OFFICER%'))
                            WHERE a.appointment_id = ?
                              AND a.status = 'SCHEDULED'
                            """)) {
                        statement.setTimestamp(1, appointmentTimestamp);
                        statement.setString(2, normalizedReason);
                        statement.setLong(3, doctorId);
                        statement.setLong(4, appointmentId);
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalArgumentException(
                                    "Select an active clinician. The appointment "
                                            + "may have changed; refresh and retry.");
                        }
                    }
                    insertWorkflowEvent(connection, patientId, appointmentId,
                            "APPOINTMENT", appointmentId,
                            "APPOINTMENT_DETAILS_UPDATED", "Reception", userId,
                            "Scheduled appointment details updated");
                    connection.commit();
                } catch (SQLException | RuntimeException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            }
        }

        public void cancelAppointment(long appointmentId, long userId)
                throws SQLException {
            try (Connection connection =
                         DatabaseConnectionFactory.openConnection()) {
                connection.setAutoCommit(false);
                try {
                    long patientId;
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT patient_id, status, appointment_date
                            FROM appointments
                            WHERE appointment_id = ?
                            FOR UPDATE
                            """)) {
                        statement.setLong(1, appointmentId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                throw new IllegalArgumentException(
                                        "The selected appointment no longer exists.");
                            }
                            Timestamp appointmentTime =
                                    resultSet.getTimestamp("appointment_date");
                            if (!canEditAppointment(
                                    resultSet.getString("status"),
                                    appointmentTime == null ? null
                                            : appointmentTime.toLocalDateTime(),
                                    LocalDateTime.now())) {
                                throw new IllegalArgumentException(
                                        "Only future scheduled appointments can be cancelled.");
                            }
                            patientId = resultSet.getLong("patient_id");
                        }
                    }
                    if (hasAppointmentHistory(connection, appointmentId)) {
                        throw new IllegalArgumentException(
                                "An appointment linked to a consultation or invoice "
                                        + "cannot be cancelled.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE appointments
                            SET status = 'CANCELLED'
                            WHERE appointment_id = ? AND status = 'SCHEDULED'
                            """)) {
                        statement.setLong(1, appointmentId);
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalArgumentException(
                                    "The appointment status changed. Refresh and retry.");
                        }
                    }
                    insertWorkflowEvent(connection, patientId, appointmentId,
                            "APPOINTMENT", appointmentId,
                            "APPOINTMENT_CANCELLED", "Reception", userId,
                            "Scheduled appointment cancelled; history retained");
                    connection.commit();
                } catch (SQLException | RuntimeException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            }
        }

        public static boolean canEditAppointment(String status,
                                                 LocalDateTime appointmentTime,
                                                 LocalDateTime now) {
            return "SCHEDULED".equalsIgnoreCase(status)
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

    private List<AppointmentRecord> loadAppointments(
            String sql, StatementBinder binder) throws SQLException {
        List<AppointmentRecord> appointments = new ArrayList<>();
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appointments.add(new AppointmentRecord(
                            resultSet.getLong("appointment_id"),
                            resultSet.getString("appointment_time"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("doctor_name"),
                            resultSet.getString("department_name"),
                            resultSet.getString("status"),
                            resultSet.getTimestamp("appointment_date")
                                    .toLocalDateTime(),
                            nullableLong(resultSet, "doctor_id"),
                            nullableLong(resultSet, "department_id"),
                            resultSet.getString("reason")
                    ));
                }
            }
        }
        return appointments;
    }

    private PatientRecord findPatient(Connection connection,
                                      String patientNumber)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT patient_id, patient_number, first_name, last_name,
                       TRIM(first_name || ' ' || last_name) AS patient_name,
                       date_of_birth, gender, phone, email, address,
                       emergency_contact, emergency_phone, status,
                       TO_CHAR(registered_at, 'DD Mon YYYY HH:MI AM')
                           AS registered_at
                FROM patients
                WHERE patient_number = ?
                """)) {
            statement.setString(1, patientNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "Registered patient could not be retrieved.");
                }
                return mapPatient(resultSet);
            }
        }
    }

    private PatientRecord mapPatient(ResultSet resultSet)
            throws SQLException {
        Date dateOfBirth = resultSet.getDate("date_of_birth");
        return new PatientRecord(
                resultSet.getLong("patient_id"),
                resultSet.getString("patient_number"),
                resultSet.getString("patient_name"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                dateOfBirth == null ? null : dateOfBirth.toLocalDate(),
                resultSet.getString("gender"),
                resultSet.getString("phone"),
                resultSet.getString("email"),
                resultSet.getString("address"),
                resultSet.getString("emergency_contact"),
                resultSet.getString("emergency_phone"),
                resultSet.getString("status"),
                resultSet.getString("registered_at")
        );
    }

    private Long nullableLong(ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private void insertWorkflowEvent(Connection connection, long patientId,
                                     Long appointmentId, String entityType,
                                     long entityId, String eventType,
                                     String departmentName, long userId,
                                     String details) throws SQLException {
        String sql = """
                INSERT INTO workflow_events (
                    patient_id, appointment_id, entity_type, entity_id,
                    event_type, department_name, performed_by_user_id, details
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, patientId);
            if (appointmentId == null) {
                statement.setNull(2, java.sql.Types.NUMERIC);
            } else {
                statement.setLong(2, appointmentId);
            }
            statement.setString(3, entityType);
            statement.setLong(4, entityId);
            statement.setString(5, eventType);
            statement.setString(6, departmentName);
            statement.setLong(7, userId);
            statement.setString(8, details);
            statement.executeUpdate();
        }
    }

    private String required(String value, String field, int maxLength) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        checkLength(normalized, field, maxLength);
        return normalized;
    }

    private String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void checkLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be no more than " + maxLength + " characters.");
        }
    }

    private void rollback(Connection connection, Exception exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
