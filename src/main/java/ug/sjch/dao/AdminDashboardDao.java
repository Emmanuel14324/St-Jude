package ug.sjch.dao;

import java.sql.Connection;
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

import ug.sjch.security.PasswordHasher;

public class AdminDashboardDao {
    public record OverviewMetrics(
            int patients,
            int activeStaff,
            int appointmentsToday,
            Integer currentAdmissions,
            int activeUsers,
            int departments
    ) {
    }

    public record DepartmentPerformance(
            String departmentName,
            int appointmentsToday,
            int activeStaff
    ) {
    }

    public record PatientSummary(
            long patientId,
            String patientNumber,
            String patientName,
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

    public record AppointmentSummary(
            long appointmentId,
            String appointmentTime,
            LocalDateTime appointmentDateTime,
            String patientNumber,
            String patientName,
            String departmentName,
            String doctorName,
            Long doctorId,
            Long departmentId,
            String reason,
            String status
    ) {
    }

    public record DepartmentRow(
            long departmentId,
            String name,
            String location,
            String phone,
            String status
    ) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record StaffRow(
            long staffId,
            String staffNumber,
            String name,
            String jobTitle,
            String department,
            Long departmentId,
            String firstName,
            String lastName,
            String gender,
            String phone,
            String email,
            String status
    ) {
    }

    public record UserRow(
            long userId,
            String username,
            String role,
            String staffName,
            String email,
            boolean active
    ) {
    }

    public record StaffOption(long staffId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record RoleOption(long roleId, String roleName) {
        @Override
        public String toString() {
            return roleName;
        }
    }

    public record AuditRow(
            String actionTime,
            String username,
            String action,
            String description
    ) {
    }

    public record WorkflowEventRow(
            String occurredAt,
            String patientNumber,
            String department,
            String event
    ) {
    }

    public record ClinicianOption(long staffId, long departmentId,
                                  String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public OverviewMetrics loadOverviewMetrics() throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            int patients = count(connection,
                    "SELECT COUNT(*) FROM patients");
            int activeStaff = count(connection,
                    "SELECT COUNT(*) FROM staff WHERE status = 'ACTIVE'");
            int appointmentsToday = count(connection, """
                    SELECT COUNT(*)
                    FROM appointments
                    WHERE TRUNC(appointment_date) = TRUNC(SYSDATE)
                      AND status <> 'CANCELLED'
                    """);
            int activeUsers = count(connection,
                    "SELECT COUNT(*) FROM users WHERE is_active = 'Y'");
            int departments = count(connection,
                    "SELECT COUNT(*) FROM departments WHERE status = 'ACTIVE'");
            Integer currentAdmissions = null;
            if (OracleSchemaInspector.tableExists(connection, "ADMISSIONS")
                    && OracleSchemaInspector.columnExists(
                            connection, "ADMISSIONS", "DISCHARGE_DATE")) {
                currentAdmissions = count(connection, """
                        SELECT COUNT(*)
                        FROM admissions
                        WHERE discharge_date IS NULL
                          AND UPPER(status) = 'ADMITTED'
                        """);
            }
            return new OverviewMetrics(patients, activeStaff,
                    appointmentsToday, currentAdmissions, activeUsers,
                    departments);
        }
    }

    public List<DepartmentPerformance> loadDepartmentPerformance()
            throws SQLException {
        String sql = """
                SELECT d.department_name,
                       COUNT(DISTINCT CASE
                           WHEN TRUNC(a.appointment_date) = TRUNC(SYSDATE)
                            AND a.status <> 'CANCELLED'
                           THEN a.appointment_id
                       END) AS appointments_today,
                       COUNT(DISTINCT CASE
                           WHEN s.status = 'ACTIVE' THEN s.staff_id
                       END) AS active_staff
                FROM departments d
                LEFT JOIN appointments a
                  ON a.department_id = d.department_id
                LEFT JOIN staff s
                  ON s.department_id = d.department_id
                WHERE d.status = 'ACTIVE'
                GROUP BY d.department_name
                ORDER BY appointments_today DESC, d.department_name
                """;
        List<DepartmentPerformance> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new DepartmentPerformance(
                        resultSet.getString("department_name"),
                        resultSet.getInt("appointments_today"),
                        resultSet.getInt("active_staff")
                ));
            }
        }
        return rows;
    }

    public List<PatientSummary> loadRecentPatients() throws SQLException {
        String sql = """
                SELECT patient_id, patient_number, first_name, last_name,
                       TRIM(first_name || ' ' || last_name) AS patient_name,
                       date_of_birth, gender, phone, email, address,
                       emergency_contact, emergency_phone, status,
                       TO_CHAR(registered_at, 'DD Mon YYYY HH:MI AM')
                           AS registered_at
                FROM patients
                ORDER BY registered_at DESC NULLS LAST, patient_id DESC
                FETCH FIRST 8 ROWS ONLY
                """;
        List<PatientSummary> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new PatientSummary(
                        resultSet.getLong("patient_id"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name"),
                        resultSet.getDate("date_of_birth") == null ? null
                                : resultSet.getDate("date_of_birth").toLocalDate(),
                        resultSet.getString("gender"),
                        resultSet.getString("phone"),
                        resultSet.getString("email"),
                        resultSet.getString("address"),
                        resultSet.getString("emergency_contact"),
                        resultSet.getString("emergency_phone"),
                        resultSet.getString("status"),
                        resultSet.getString("registered_at")
                ));
            }
        }
        return rows;
    }

    public List<PatientSummary> searchPatients(String searchText)
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
                FETCH FIRST 200 ROWS ONLY
                """.formatted(query == null ? "" : """
                WHERE LOWER(patient_number) LIKE LOWER(?)
                   OR LOWER(first_name || ' ' || last_name) LIKE LOWER(?)
                   OR LOWER(phone) LIKE LOWER(?)
                   OR LOWER(email) LIKE LOWER(?)
                """);
        List<PatientSummary> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (query != null) {
                String pattern = "%" + query + "%";
                for (int index = 1; index <= 4; index++) {
                    statement.setString(index, pattern);
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new PatientSummary(
                            resultSet.getLong("patient_id"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getDate("date_of_birth") == null ? null
                                    : resultSet.getDate("date_of_birth").toLocalDate(),
                            resultSet.getString("gender"),
                            resultSet.getString("phone"),
                            resultSet.getString("email"),
                            resultSet.getString("address"),
                            resultSet.getString("emergency_contact"),
                            resultSet.getString("emergency_phone"),
                            resultSet.getString("status"),
                            resultSet.getString("registered_at")
                    ));
                }
            }
        }
        return rows;
    }

    public void updatePatientDetails(long patientId, String firstName,
                                     String lastName, LocalDate dateOfBirth,
                                     String gender, String phone, String email,
                                     String address, String emergencyContact,
                                     String emergencyPhone, long administratorId)
            throws SQLException {
        String first = required(firstName, "First name", 60);
        String last = required(lastName, "Last name", 60);
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Date of birth cannot be in the future.");
        }
        String normalizedGender = optional(gender);
        if (normalizedGender != null
                && !List.of("Male", "Female", "Other")
                .contains(normalizedGender)) {
            throw new IllegalArgumentException("Select a valid gender.");
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
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE patients
                        SET first_name = ?, last_name = ?, date_of_birth = ?,
                            gender = ?, phone = ?, email = ?, address = ?,
                            emergency_contact = ?, emergency_phone = ?
                        WHERE patient_id = ? AND status = 'ACTIVE'
                        """)) {
                    statement.setString(1, first);
                    statement.setString(2, last);
                    if (dateOfBirth == null) {
                        statement.setNull(3, java.sql.Types.DATE);
                    } else {
                        statement.setDate(3,
                                java.sql.Date.valueOf(dateOfBirth));
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
                writeAudit(connection, administratorId,
                        "UPDATE_PATIENT_DETAILS",
                        "Updated demographics and contact details for patient "
                                + patientId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void archivePatient(long patientId, long administratorId)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
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
                if (count(connection, """
                        SELECT COUNT(*)
                        FROM appointments
                        WHERE patient_id = ?
                          AND (status IN (
                              'CHECKED_IN', 'WAITING', 'IN_PROGRESS'
                          ) OR (status = 'SCHEDULED'
                                AND appointment_date > SYSDATE))
                        """, patientId) > 0) {
                    throw new IllegalArgumentException(
                            "A patient with an active visit or future "
                                    + "appointment cannot be archived.");
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
                writeAudit(connection, administratorId, "ARCHIVE_PATIENT",
                        "Archived patient " + patientId
                                + "; linked history retained");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public List<AppointmentSummary> loadRecentAppointments()
            throws SQLException {
        String sql = """
                SELECT a.appointment_id, a.appointment_date, a.doctor_id,
                       a.department_id, a.reason,
                       TO_CHAR(a.appointment_date, 'DD Mon YYYY HH:MI AM')
                           AS appointment_time,
                       p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name)
                           AS patient_name,
                       d.department_name,
                       TRIM(s.first_name || ' ' || s.last_name) AS doctor_name,
                       a.status
                FROM appointments a
                JOIN patients p ON p.patient_id = a.patient_id
                LEFT JOIN departments d ON d.department_id = a.department_id
                LEFT JOIN staff s ON s.staff_id = a.doctor_id
                ORDER BY a.appointment_date DESC
                FETCH FIRST 8 ROWS ONLY
                """;
        List<AppointmentSummary> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new AppointmentSummary(
                        resultSet.getLong("appointment_id"),
                        resultSet.getString("appointment_time"),
                        resultSet.getTimestamp("appointment_date")
                                .toLocalDateTime(),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("department_name"),
                        resultSet.getString("doctor_name"),
                        nullableLong(resultSet, "doctor_id"),
                        nullableLong(resultSet, "department_id"),
                        resultSet.getString("reason"),
                        resultSet.getString("status")
                ));
            }
        }
        return rows;
    }

    public List<AppointmentSummary> loadAppointments(LocalDate fromDate,
                                                     LocalDate toDate)
            throws SQLException {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException(
                    "Choose a valid appointment date range.");
        }
        String sql = """
                SELECT a.appointment_id, a.appointment_date, a.doctor_id,
                       a.department_id, a.reason,
                       TO_CHAR(a.appointment_date, 'DD Mon YYYY HH:MI AM')
                           AS appointment_time,
                       p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name)
                           AS patient_name,
                       d.department_name,
                       TRIM(s.first_name || ' ' || s.last_name) AS doctor_name,
                       a.status
                FROM appointments a
                JOIN patients p ON p.patient_id = a.patient_id
                LEFT JOIN departments d ON d.department_id = a.department_id
                LEFT JOIN staff s ON s.staff_id = a.doctor_id
                WHERE a.appointment_date >= ?
                  AND a.appointment_date < ?
                ORDER BY a.appointment_date DESC
                FETCH FIRST 500 ROWS ONLY
                """;
        List<AppointmentSummary> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1,
                    Timestamp.valueOf(fromDate.atStartOfDay()));
            statement.setTimestamp(2,
                    Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new AppointmentSummary(
                            resultSet.getLong("appointment_id"),
                            resultSet.getString("appointment_time"),
                            resultSet.getTimestamp("appointment_date")
                                    .toLocalDateTime(),
                            resultSet.getString("patient_number"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("department_name"),
                            resultSet.getString("doctor_name"),
                            nullableLong(resultSet, "doctor_id"),
                            nullableLong(resultSet, "department_id"),
                            resultSet.getString("reason"),
                            resultSet.getString("status")
                    ));
                }
            }
        }
        return rows;
    }

    public List<DepartmentRow> loadDepartments() throws SQLException {
        String sql = """
                SELECT department_id, department_name, location, phone, status
                FROM departments
                ORDER BY department_name
                """;
        List<DepartmentRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new DepartmentRow(
                        resultSet.getLong("department_id"),
                        resultSet.getString("department_name"),
                        resultSet.getString("location"),
                        resultSet.getString("phone"),
                        resultSet.getString("status")
                ));
            }
        }
        return rows;
    }

    public List<StaffRow> searchStaff(String searchText) throws SQLException {
        String query = optional(searchText);
        String sql = """
                SELECT s.staff_id, s.staff_number,
                       s.first_name, s.last_name, s.gender, s.department_id,
                       TRIM(s.first_name || ' ' || s.last_name) AS staff_name,
                       s.job_title, d.department_name, s.phone, s.email,
                       s.status
                FROM staff s
                LEFT JOIN departments d ON d.department_id = s.department_id
                %s
                ORDER BY s.last_name, s.first_name
                FETCH FIRST 200 ROWS ONLY
                """.formatted(query == null ? "" : """
                WHERE LOWER(s.staff_number) LIKE LOWER(?)
                   OR LOWER(s.first_name || ' ' || s.last_name) LIKE LOWER(?)
                   OR LOWER(s.job_title) LIKE LOWER(?)
                   OR LOWER(d.department_name) LIKE LOWER(?)
                """);
        List<StaffRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (query != null) {
                String pattern = "%" + query + "%";
                for (int index = 1; index <= 4; index++) {
                    statement.setString(index, pattern);
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new StaffRow(
                            resultSet.getLong("staff_id"),
                            resultSet.getString("staff_number"),
                            resultSet.getString("staff_name"),
                            resultSet.getString("job_title"),
                            resultSet.getString("department_name"),
                            nullableLong(resultSet, "department_id"),
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getString("gender"),
                            resultSet.getString("phone"),
                            resultSet.getString("email"),
                            resultSet.getString("status")
                    ));
                }
            }
        }
        return rows;
    }

    public List<UserRow> loadUsers(String searchText) throws SQLException {
        String query = optional(searchText);
        String sql = """
                SELECT u.user_id, u.username, r.role_name,
                       TRIM(s.first_name || ' ' || s.last_name) AS staff_name,
                       u.email, u.is_active
                FROM users u
                JOIN roles r ON r.role_id = u.role_id
                LEFT JOIN staff s ON s.staff_id = u.staff_id
                %s
                ORDER BY u.username
                FETCH FIRST 200 ROWS ONLY
                """.formatted(query == null ? "" : """
                WHERE LOWER(u.username) LIKE LOWER(?)
                   OR LOWER(r.role_name) LIKE LOWER(?)
                   OR LOWER(s.first_name || ' ' || s.last_name) LIKE LOWER(?)
                """);
        List<UserRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (query != null) {
                String pattern = "%" + query + "%";
                for (int index = 1; index <= 3; index++) {
                    statement.setString(index, pattern);
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new UserRow(
                            resultSet.getLong("user_id"),
                            resultSet.getString("username"),
                            resultSet.getString("role_name"),
                            resultSet.getString("staff_name"),
                            resultSet.getString("email"),
                            "Y".equalsIgnoreCase(
                                    resultSet.getString("is_active"))
                    ));
                }
            }
        }
        return rows;
    }

    public List<StaffOption> loadStaffWithoutAccounts() throws SQLException {
        String sql = """
                SELECT s.staff_id,
                       s.staff_number || ' - ' ||
                       TRIM(s.first_name || ' ' || s.last_name) AS staff_label
                FROM staff s
                LEFT JOIN users u ON u.staff_id = s.staff_id
                WHERE s.status = 'ACTIVE'
                  AND u.user_id IS NULL
                ORDER BY s.last_name, s.first_name
                """;
        List<StaffOption> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new StaffOption(
                        resultSet.getLong("staff_id"),
                        resultSet.getString("staff_label")
                ));
            }
        }
        return rows;
    }

    public List<RoleOption> loadRoles() throws SQLException {
        String sql = "SELECT role_id, role_name FROM roles ORDER BY role_name";
        List<RoleOption> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new RoleOption(
                        resultSet.getLong("role_id"),
                        resultSet.getString("role_name")
                ));
            }
        }
        return rows;
    }

    public List<ClinicianOption> loadActiveClinicians() throws SQLException {
        String sql = """
                SELECT s.staff_id, s.department_id,
                       TRIM(s.first_name || ' ' || s.last_name)
                       || ' - ' || d.department_name AS clinician_label
                FROM staff s
                JOIN departments d ON d.department_id = s.department_id
                WHERE s.status = 'ACTIVE'
                  AND d.status = 'ACTIVE'
                  AND (UPPER(s.job_title) LIKE '%DOCTOR%'
                       OR UPPER(s.job_title) LIKE '%PHYSICIAN%'
                       OR UPPER(s.job_title) LIKE '%MEDICAL OFFICER%')
                ORDER BY clinician_label
                """;
        List<ClinicianOption> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new ClinicianOption(
                        resultSet.getLong("staff_id"),
                        resultSet.getLong("department_id"),
                        resultSet.getString("clinician_label")
                ));
            }
        }
        return rows;
    }

    public void updateStaffDetails(long staffId, String firstName,
                                   String lastName, String jobTitle,
                                   long departmentId, String gender,
                                   String phone, String email,
                                   long administratorId) throws SQLException {
        String first = required(firstName, "First name", 60);
        String last = required(lastName, "Last name", 60);
        String title = required(jobTitle, "Job title", 100);
        String normalizedGender = optional(gender);
        if (normalizedGender != null
                && !List.of("Male", "Female", "Other")
                .contains(normalizedGender)) {
            throw new IllegalArgumentException("Select a valid gender.");
        }
        String normalizedPhone = optional(phone);
        String normalizedEmail = optional(email);
        checkLength(normalizedPhone, "Phone", 20);
        checkLength(normalizedEmail, "Email", 120);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE staff s
                        SET first_name = ?, last_name = ?, job_title = ?,
                            department_id = ?, gender = ?, phone = ?, email = ?
                        WHERE s.staff_id = ?
                          AND EXISTS (
                              SELECT 1
                              FROM departments d
                              WHERE d.department_id = ?
                                AND (d.status = 'ACTIVE'
                                     OR d.department_id = s.department_id)
                          )
                        """)) {
                    statement.setString(1, first);
                    statement.setString(2, last);
                    statement.setString(3, title);
                    statement.setLong(4, departmentId);
                    statement.setString(5, normalizedGender);
                    statement.setString(6, normalizedPhone);
                    statement.setString(7, normalizedEmail);
                    statement.setLong(8, staffId);
                    statement.setLong(9, departmentId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "Select an existing department, or an active "
                                        + "department when reassigning staff.");
                    }
                }
                writeAudit(connection, administratorId, "UPDATE_STAFF_DETAILS",
                        "Updated staff record " + staffId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateDepartmentDetails(long departmentId, String name,
                                        String location, String phone,
                                        long administratorId)
            throws SQLException {
        String normalizedName = required(name, "Department name", 100);
        String normalizedLocation = optional(location);
        String normalizedPhone = optional(phone);
        checkLength(normalizedLocation, "Location", 200);
        checkLength(normalizedPhone, "Phone", 30);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE departments
                        SET department_name = ?, location = ?, phone = ?
                        WHERE department_id = ?
                        """)) {
                    statement.setString(1, normalizedName);
                    statement.setString(2, normalizedLocation);
                    statement.setString(3, normalizedPhone);
                    statement.setLong(4, departmentId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "The selected department no longer exists.");
                    }
                }
                writeAudit(connection, administratorId,
                        "UPDATE_DEPARTMENT_DETAILS",
                        "Updated department " + departmentId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateDepartmentStatus(long departmentId, boolean active,
                                       long administratorId)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                if (!active && (count(connection, """
                        SELECT COUNT(*) FROM staff
                        WHERE department_id = ? AND status = 'ACTIVE'
                        """, departmentId) > 0
                        || count(connection, """
                        SELECT COUNT(*) FROM appointments
                        WHERE department_id = ?
                          AND status = 'SCHEDULED'
                          AND appointment_date > SYSDATE
                        """, departmentId) > 0
                        || hasActiveWards(connection, departmentId))) {
                    throw new IllegalArgumentException(
                            "Move active staff and resolve future scheduled "
                                    + "appointments before deactivating this department.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE departments
                        SET status = ?
                        WHERE department_id = ?
                        """)) {
                    statement.setString(1, active ? "ACTIVE" : "INACTIVE");
                    statement.setLong(2, departmentId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "The selected department no longer exists.");
                    }
                }
                writeAudit(connection, administratorId,
                        active ? "ACTIVATE_DEPARTMENT"
                                : "DEACTIVATE_DEPARTMENT",
                        (active ? "Activated" : "Deactivated")
                                + " department " + departmentId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateAppointmentDetails(long appointmentId, long doctorId,
                                        LocalDateTime appointmentDateTime,
                                        String reason, long administratorId)
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
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT status, appointment_date
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
                             JOIN departments d
                               ON d.department_id = s.department_id
                             WHERE s.staff_id = ?
                               AND s.status = 'ACTIVE'
                               AND d.status = 'ACTIVE'
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
                                "Select an active clinician in an active "
                                        + "department; refresh if the appointment changed.");
                    }
                }
                writeAudit(connection, administratorId,
                        "UPDATE_APPOINTMENT_DETAILS",
                        "Updated scheduled appointment " + appointmentId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void cancelAppointment(long appointmentId, long administratorId)
            throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT status, appointment_date
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
                writeAudit(connection, administratorId, "CANCEL_APPOINTMENT",
                        "Cancelled scheduled appointment " + appointmentId);
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

    private boolean hasActiveWards(Connection connection, long departmentId)
            throws SQLException {
        if (!OracleSchemaInspector.tableExists(connection, "WARDS")
                || !OracleSchemaInspector.columnExists(
                        connection, "WARDS", "DEPARTMENT_ID")
                || !OracleSchemaInspector.columnExists(
                        connection, "WARDS", "IS_ACTIVE")) {
            return false;
        }
        return count(connection, """
                SELECT COUNT(*) FROM wards
                WHERE department_id = ? AND is_active = 'Y'
                """, departmentId) > 0;
    }

    public List<AuditRow> loadRecentAuditLogs() throws SQLException {
        String sql = """
                SELECT TO_CHAR(a.action_time, 'DD Mon YYYY HH:MI AM')
                           AS action_time,
                       u.username, a.action, a.description
                FROM audit_logs a
                LEFT JOIN users u ON u.user_id = a.user_id
                ORDER BY a.action_time DESC NULLS LAST, a.log_id DESC
                FETCH FIRST 200 ROWS ONLY
                """;
        List<AuditRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new AuditRow(
                        resultSet.getString("action_time"),
                        resultSet.getString("username"),
                        resultSet.getString("action"),
                        resultSet.getString("description")
                ));
            }
        }
        return rows;
    }

    public List<WorkflowEventRow> loadRecentWorkflowEvents()
            throws SQLException {
        String sql = """
                SELECT TO_CHAR(e.occurred_at, 'DD Mon YYYY HH:MI AM')
                           AS occurred_at,
                       p.patient_number, e.department_name, e.event_type
                FROM workflow_events e
                JOIN patients p ON p.patient_id = e.patient_id
                ORDER BY e.occurred_at DESC
                FETCH FIRST 12 ROWS ONLY
                """;
        List<WorkflowEventRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            if (!OracleSchemaInspector.tableExists(connection,
                    "WORKFLOW_EVENTS")) {
                throw new SQLException(
                        "Workflow event monitoring is not configured.");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new WorkflowEventRow(
                            resultSet.getString("occurred_at"),
                            resultSet.getString("patient_number"),
                            resultSet.getString("department_name"),
                            resultSet.getString("event_type")
                    ));
                }
            }
        }
        return rows;
    }

    public String createStaff(String firstName, String lastName,
                              String jobTitle, long departmentId,
                              String gender, String phone, String email,
                              long administratorId) throws SQLException {
        String first = required(firstName, "First name", 60);
        String last = required(lastName, "Last name", 60);
        String title = required(jobTitle, "Job title", 100);
        String normalizedGender = optional(gender);
        if (normalizedGender != null
                && !List.of("Male", "Female", "Other")
                .contains(normalizedGender)) {
            throw new IllegalArgumentException("Select a valid gender.");
        }
        String normalizedPhone = optional(phone);
        String normalizedEmail = optional(email);
        checkLength(normalizedPhone, "Phone", 20);
        checkLength(normalizedEmail, "Email", 120);
        String staffNumber = "SJCH-STF-" + UUID.randomUUID()
                .toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO staff (
                            staff_number, first_name, last_name, gender,
                            job_title, phone, email, department_id, status
                        )
                        SELECT ?, ?, ?, ?, ?, ?, ?, department_id, 'ACTIVE'
                        FROM departments
                        WHERE department_id = ? AND status = 'ACTIVE'
                        """)) {
                    statement.setString(1, staffNumber);
                    statement.setString(2, first);
                    statement.setString(3, last);
                    statement.setString(4, normalizedGender);
                    statement.setString(5, title);
                    statement.setString(6, normalizedPhone);
                    statement.setString(7, normalizedEmail);
                    statement.setLong(8, departmentId);
                    inserted = statement.executeUpdate();
                }
                if (inserted != 1) {
                    throw new IllegalArgumentException(
                            "Select an active department.");
                }
                writeAudit(connection, administratorId, "CREATE_STAFF",
                        "Created staff record " + staffNumber);
                connection.commit();
                return staffNumber;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateStaffStatus(long staffId, String newStatus,
                                  long administratorId) throws SQLException {
        if (!List.of("ACTIVE", "INACTIVE", "ON_LEAVE").contains(newStatus)) {
            throw new IllegalArgumentException("Select a valid staff status.");
        }
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                Long linkedUserId = findLinkedUser(connection, staffId);
                if (linkedUserId != null && !"ACTIVE".equals(newStatus)) {
                    protectAdministratorDeactivation(connection,
                            linkedUserId, administratorId);
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE staff SET status = ? WHERE staff_id = ?
                        """)) {
                    statement.setString(1, newStatus);
                    statement.setLong(2, staffId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "The selected staff record no longer exists.");
                    }
                }
                if (!"ACTIVE".equals(newStatus)) {
                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                                         UPDATE users SET is_active = 'N'
                                         WHERE staff_id = ?
                                         """)) {
                        statement.setLong(1, staffId);
                        statement.executeUpdate();
                    }
                }
                writeAudit(connection, administratorId, "UPDATE_STAFF_STATUS",
                        "Changed staff " + staffId + " status to " + newStatus);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void createUserAccount(String username, String password,
                                  String email, long staffId, long roleId,
                                  long administratorId) throws SQLException {
        String normalizedUsername = validateAccountUsername(username);
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException(
                    "Initial password must be at least 12 characters.");
        }
        String normalizedEmail = optional(email);
        checkLength(normalizedEmail, "Email", 120);
        String passwordHash = PasswordHasher.hash(password);
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                if (count(connection, """
                        SELECT COUNT(*)
                        FROM users
                        WHERE LOWER(username) = LOWER(?)
                        """, normalizedUsername) > 0) {
                    throw new IllegalArgumentException(
                            "That username is already in use.");
                }
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO users (
                            username, password_hash, role_id, staff_id,
                            email, is_active
                        )
                        SELECT ?, ?, r.role_id, s.staff_id, ?, 'Y'
                        FROM roles r
                        JOIN staff s ON s.staff_id = ?
                        WHERE r.role_id = ?
                          AND s.status = 'ACTIVE'
                          AND NOT EXISTS (
                              SELECT 1 FROM users existing
                              WHERE LOWER(existing.username) =
                                    LOWER(?)
                          )
                          AND NOT EXISTS (
                              SELECT 1 FROM users existing
                              WHERE existing.staff_id = s.staff_id
                          )
                        """)) {
                    statement.setString(1, normalizedUsername);
                    statement.setString(2, passwordHash);
                    statement.setString(3, normalizedEmail);
                    statement.setLong(4, staffId);
                    statement.setLong(5, roleId);
                    statement.setString(6, normalizedUsername);
                    inserted = statement.executeUpdate();
                }
                if (inserted != 1) {
                    throw new IllegalArgumentException(
                            "Select an active staff member without an account "
                                    + "and a valid role.");
                }
                writeAudit(connection, administratorId, "CREATE_USER",
                        "Created account for " + normalizedUsername);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void updateUserAccount(long userId, String username, String email,
                                      long roleId, String newPassword,
                                      long administratorId) throws SQLException {
            String normalizedUsername = validateAccountUsername(username);
            String normalizedEmail = optional(email);
            checkLength(normalizedEmail, "Email", 120);
            String normalizedPassword = validatePasswordReset(newPassword);
            String passwordHash = normalizedPassword == null ? null
                    : PasswordHasher.hash(normalizedPassword);
            try (Connection connection = DatabaseConnectionFactory.openConnection()) {
                connection.setAutoCommit(false);
                try {
                    requireAdministrator(connection, administratorId);
                    long currentRoleId;
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT role_id
                            FROM users
                            WHERE user_id = ?
                            FOR UPDATE
                            """)) {
                        statement.setLong(1, userId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                throw new IllegalArgumentException(
                                        "The selected account no longer exists.");
                            }
                            currentRoleId = resultSet.getLong("role_id");
                        }
                    }
                    if (count(connection, """
                            SELECT COUNT(*) FROM roles WHERE role_id = ?
                            """, roleId) != 1) {
                        throw new IllegalArgumentException("Select a valid role.");
                    }
                    if (currentRoleId != roleId) {
                        if (userId == administratorId) {
                            throw new IllegalArgumentException(
                                    "You cannot change your own account role.");
                        }
                        protectAdministratorRoleChange(
                                connection, userId, roleId);
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT COUNT(*)
                            FROM users
                            WHERE LOWER(username) = LOWER(?)
                              AND user_id <> ?
                            """)) {
                        statement.setString(1, normalizedUsername);
                        statement.setLong(2, userId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            if (resultSet.getInt(1) > 0) {
                                throw new IllegalArgumentException(
                                        "That username is already in use.");
                            }
                        }
                    }
                    String updateSql = passwordHash == null
                            ? """
                              UPDATE users
                              SET username = ?, email = ?, role_id = ?
                              WHERE user_id = ?
                              """
                            : """
                              UPDATE users
                              SET username = ?, email = ?, role_id = ?,
                                  password_hash = ?
                              WHERE user_id = ?
                              """;
                    try (PreparedStatement statement =
                                 connection.prepareStatement(updateSql)) {
                        statement.setString(1, normalizedUsername);
                        statement.setString(2, normalizedEmail);
                        statement.setLong(3, roleId);
                        int userIdIndex;
                        if (passwordHash == null) {
                            userIdIndex = 4;
                        } else {
                            statement.setString(4, passwordHash);
                            userIdIndex = 5;
                        }
                        statement.setLong(userIdIndex, userId);
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalArgumentException(
                                    "The selected account could not be updated.");
                        }
                    }
                    String description = "Updated account " + userId;
                    if (currentRoleId != roleId) {
                        description += "; role assignment changed";
                    }
                    if (passwordHash != null) {
                        description += "; password reset";
                    }
                    writeAudit(connection, administratorId,
                            "UPDATE_USER_ACCOUNT", description);
                    connection.commit();
                } catch (SQLException | RuntimeException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            }
        }

    static String validateAccountUsername(String username) {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Username is required.");
            }
            String normalizedUsername = username.trim();
            if (normalizedUsername.length() > 60) {
                throw new IllegalArgumentException(
                        "Username must be no more than 60 characters.");
            }
            if (normalizedUsername.length() < 3
                    || !normalizedUsername.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException(
                        "Username must be at least 3 characters and use only "
                                + "letters, numbers, dots, underscores, or hyphens.");
            }
            return normalizedUsername;
        }

    static String validatePasswordReset(String newPassword) {
            if (newPassword == null || newPassword.isBlank()) {
                return null;
            }
            if (newPassword.length() < 12) {
                throw new IllegalArgumentException(
                        "New password must be at least 12 characters.");
            }
            return newPassword;
    }

    public void updateUserActive(long userId, boolean active,
                                 long administratorId) throws SQLException {
        try (Connection connection = DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdministrator(connection, administratorId);
                if (!active) {
                    protectAdministratorDeactivation(connection, userId,
                            administratorId);
                } else if (!hasActiveStaff(connection, userId)) {
                    throw new IllegalArgumentException(
                            "Activate the linked staff record before enabling "
                                    + "this account.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE users SET is_active = ?
                        WHERE user_id = ?
                        """)) {
                    statement.setString(1, active ? "Y" : "N");
                    statement.setLong(2, userId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "The selected account no longer exists.");
                    }
                }
                writeAudit(connection, administratorId,
                        active ? "ACTIVATE_USER" : "DEACTIVATE_USER",
                        (active ? "Activated" : "Deactivated")
                                + " account " + userId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private int count(Connection connection, String sql, String value)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int count(Connection connection, String sql, long value)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int count(Connection connection, String sql, long firstValue,
                      long secondValue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, firstValue);
            statement.setLong(2, secondValue);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private Long nullableLong(ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private void requireAdministrator(Connection connection, long userId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM users u
                JOIN roles r ON r.role_id = u.role_id
                WHERE u.user_id = ?
                  AND u.is_active = 'Y'
                  AND UPPER(r.role_name) = 'ADMINISTRATOR'
                """)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) != 1) {
                    throw new SecurityException(
                            "Administrator access is required.");
                }
            }
        }
    }

    private Long findLinkedUser(Connection connection, long staffId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT u.user_id
                FROM users u
                WHERE u.staff_id = ?
                """)) {
            statement.setLong(1, staffId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }

    private void protectAdministratorDeactivation(Connection connection,
                                                  long userId,
                                                  long administratorId)
            throws SQLException {
        if (userId == administratorId) {
            throw new IllegalArgumentException(
                    "You cannot deactivate your own administrator account.");
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT role_id
                FROM roles
                WHERE UPPER(role_name) = 'ADMINISTRATOR'
                FOR UPDATE
                """);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
        }
        boolean targetIsAdministrator;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT r.role_name
                FROM users u
                JOIN roles r ON r.role_id = u.role_id
                WHERE u.user_id = ? AND u.is_active = 'Y'
                """)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                targetIsAdministrator = resultSet.next()
                        && "ADMINISTRATOR".equalsIgnoreCase(
                                resultSet.getString("role_name"));
            }
        }

        if (targetIsAdministrator && count(connection, """
                SELECT COUNT(*)
                FROM users u
                JOIN roles r ON r.role_id = u.role_id
                WHERE u.is_active = 'Y'
                  AND UPPER(r.role_name) = 'ADMINISTRATOR'
                """) <= 1) {
            throw new IllegalArgumentException(
                    "The last active administrator cannot be deactivated.");
        }
    }

    private void protectAdministratorRoleChange(Connection connection,
                                                long userId,
                                                long targetRoleId)
            throws SQLException {
        long administratorRoleId;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT role_id
                FROM roles
                WHERE UPPER(role_name) = 'ADMINISTRATOR'
                FOR UPDATE
                """);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Administrator role is not configured.");
            }
            administratorRoleId = resultSet.getLong("role_id");
        }
        if (targetRoleId == administratorRoleId
                || count(connection, """
                SELECT COUNT(*)
                FROM users
                WHERE user_id = ? AND is_active = 'Y' AND role_id = ?
                """, userId, administratorRoleId) == 0) {
            return;
        }
        if (count(connection, """
                SELECT COUNT(*)
                FROM users
                WHERE is_active = 'Y'
                  AND role_id = ?
                  AND user_id <> ?
                """, administratorRoleId, userId) == 0) {
            throw new IllegalArgumentException(
                    "The last active administrator cannot be assigned another role.");
        }
    }

    private boolean hasActiveStaff(Connection connection, long userId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM users u
                LEFT JOIN staff s ON s.staff_id = u.staff_id
                WHERE u.user_id = ?
                  AND (u.staff_id IS NULL OR s.status = 'ACTIVE')
                """)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }

    private void writeAudit(Connection connection, long administratorId,
                            String action, String description)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_logs (user_id, action, description)
                VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, administratorId);
            statement.setString(2, action);
            statement.setString(3, description);
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
        return value == null || value.isBlank() ? null : value.trim();
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
}
