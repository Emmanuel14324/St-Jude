package ug.sjch.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ReceptionDashboardDao {
    public record Metrics(
            int registeredToday,
            int appointmentsToday,
            int waitingPatients,
            int upcomingAppointments
    ) {
    }

    public record WorkflowCounts(
            int queued,
            int waiting,
            int engaged,
            int completed
    ) {
        public int total() {
            return queued + waiting + engaged + completed;
        }
    }

    public record PatientRow(
            String patientNumber,
            String name,
            String phone,
            String registeredAt
    ) {
    }

    public record AppointmentRow(
            long appointmentId,
            String appointmentTime,
            String patientNumber,
            String patientName,
            String doctorName,
            String status
    ) {
    }

    public Metrics loadMetrics() throws SQLException {
        String sql = """
                SELECT
                    (SELECT COUNT(*)
                     FROM patients p
                     WHERE TRUNC(p.registered_at) = TRUNC(SYSDATE))
                        AS registered_today,
                    (SELECT COUNT(*)
                     FROM appointments a
                     WHERE TRUNC(a.appointment_date) = TRUNC(SYSDATE)
                       AND a.status <> 'CANCELLED')
                        AS appointments_today,
                    (SELECT COUNT(*)
                     FROM appointments a
                     WHERE TRUNC(a.appointment_date) = TRUNC(SYSDATE)
                       AND a.status IN ('CHECKED_IN', 'WAITING'))
                        AS waiting_patients,
                    (SELECT COUNT(*)
                     FROM appointments a
                     WHERE a.appointment_date > SYSDATE
                       AND a.status = 'SCHEDULED')
                        AS upcoming_appointments
                FROM dual
                """;
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return new Metrics(
                    resultSet.getInt("registered_today"),
                    resultSet.getInt("appointments_today"),
                    resultSet.getInt("waiting_patients"),
                    resultSet.getInt("upcoming_appointments")
            );
        }
    }

    public WorkflowCounts loadTodayWorkflow() throws SQLException {
        String sql = """
                SELECT
                    COUNT(CASE WHEN status = 'SCHEDULED' THEN 1 END) AS queued,
                    COUNT(CASE WHEN status IN ('CHECKED_IN', 'WAITING')
                               THEN 1 END) AS waiting,
                    COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS engaged,
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed
                FROM appointments
                WHERE TRUNC(appointment_date) = TRUNC(SYSDATE)
                  AND status <> 'CANCELLED'
                """;
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return new WorkflowCounts(
                    resultSet.getInt("queued"),
                    resultSet.getInt("waiting"),
                    resultSet.getInt("engaged"),
                    resultSet.getInt("completed")
            );
        }
    }

    public List<PatientRow> loadRecentPatients() throws SQLException {
        String sql = """
                SELECT patient_number,
                       TRIM(first_name || ' ' || last_name) AS patient_name,
                       phone,
                       TO_CHAR(registered_at, 'DD Mon YYYY HH:MI AM')
                           AS registered_at
                FROM patients
                ORDER BY registered_at DESC
                FETCH FIRST 6 ROWS ONLY
                """;
        List<PatientRow> patients = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                patients.add(new PatientRow(
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("phone"),
                        resultSet.getString("registered_at")
                ));
            }
        }
        return patients;
    }

    public List<AppointmentRow> loadUpcomingAppointments()
            throws SQLException {
        String sql = """
                SELECT a.appointment_id,
                       TO_CHAR(a.appointment_date, 'DD Mon HH:MI AM')
                           AS appointment_time,
                       p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name)
                           AS patient_name,
                       TRIM(s.first_name || ' ' || s.last_name)
                           AS doctor_name,
                       a.status
                FROM appointments a
                JOIN patients p ON p.patient_id = a.patient_id
                LEFT JOIN staff s ON s.staff_id = a.doctor_id
                WHERE a.appointment_date >= SYSDATE
                  AND a.status = 'SCHEDULED'
                ORDER BY a.appointment_date
                FETCH FIRST 8 ROWS ONLY
                """;
        List<AppointmentRow> appointments = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                appointments.add(new AppointmentRow(
                        resultSet.getLong("appointment_id"),
                        resultSet.getString("appointment_time"),
                        resultSet.getString("patient_number"),
                        resultSet.getString("patient_name"),
                        resultSet.getString("doctor_name"),
                        resultSet.getString("status")
                ));
            }
        }
        return appointments;
    }
}
