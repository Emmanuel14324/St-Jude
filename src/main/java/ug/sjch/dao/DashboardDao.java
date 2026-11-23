package ug.sjch.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardDao {
    public record CategoryCount(String name, int count) {
    }

    public record PatientSummary(String number, String name, String registeredAt) {
    }

    public record StaffSummary(String name, String jobTitle, String status) {
    }

    public Map<String, Integer> loadCounts() throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("patients", 0);
        counts.put("staff", 0);
        counts.put("appointmentsToday", 0);
        counts.put("currentAdmissions", null);

        try (Connection connection = DatabaseConnectionFactory.openConnection();
             Statement statement = connection.createStatement()) {
            countTable(statement, "PATIENTS", "patient_id", "patients", counts);
            countTable(statement, "STAFF", "staff_id", "staff", counts);
            countTable(statement, "APPOINTMENTS", "appointment_id",
                    "appointmentsToday", counts,
                    "WHERE TRUNC(appointment_date) = TRUNC(SYSDATE)");
            if (OracleSchemaInspector.tableExists(connection, "ADMISSIONS")
                    && OracleSchemaInspector.columnExists(
                            connection, "ADMISSIONS", "ADMISSION_ID")
                    && OracleSchemaInspector.columnExists(
                            connection, "ADMISSIONS", "DISCHARGE_DATE")) {
                countTable(statement, "ADMISSIONS", "admission_id",
                        "currentAdmissions", counts,
                        "WHERE discharge_date IS NULL");
            }
        }
        return counts;
    }

    public List<CategoryCount> loadAdmissionStatusCounts() throws SQLException {
        String sql = """
                SELECT status, COUNT(*) AS total
                FROM admissions
                GROUP BY status
                ORDER BY status
                """;
        List<CategoryCount> results = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                results.add(new CategoryCount(
                        resultSet.getString("status"),
                        resultSet.getInt("total")
                ));
            }
        }
        return results;
    }

    public List<CategoryCount> loadStaffCountsByJobTitle() throws SQLException {
        String sql = """
                SELECT job_title, COUNT(*) AS total
                FROM staff
                GROUP BY job_title
                ORDER BY total DESC, job_title
                """;
        List<CategoryCount> results = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                results.add(new CategoryCount(
                        resultSet.getString("job_title"),
                        resultSet.getInt("total")
                ));
            }
        }
        return results;
    }

    public List<PatientSummary> loadRecentPatients() throws SQLException {
        String sql = """
                SELECT patient_number, full_name,
                       TO_CHAR(created_at, 'DD Mon YYYY') AS registered_at
                FROM patients
                ORDER BY created_at DESC
                FETCH FIRST 5 ROWS ONLY
                """;
        List<PatientSummary> results = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                results.add(new PatientSummary(
                        resultSet.getString("patient_number"),
                        resultSet.getString("full_name"),
                        resultSet.getString("registered_at")
                ));
            }
        }
        return results;
    }

    public List<StaffSummary> loadRecentStaff() throws SQLException {
        String sql = """
                SELECT full_name, job_title, is_active
                FROM staff
                ORDER BY staff_id DESC
                FETCH FIRST 5 ROWS ONLY
                """;
        List<StaffSummary> results = new ArrayList<>();
        try (Connection connection = DatabaseConnectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                results.add(new StaffSummary(
                        resultSet.getString("full_name"),
                        resultSet.getString("job_title"),
                        "Y".equalsIgnoreCase(resultSet.getString("is_active"))
                                ? "Active" : "Inactive"
                ));
            }
        }
        return results;
    }

    private void countTable(Statement statement, String table, String column,
                           String key, Map<String, Integer> counts)
            throws SQLException {
        countTable(statement, table, column, key, counts, "");
    }

    private void countTable(Statement statement, String table, String column,
                           String key, Map<String, Integer> counts, String condition)
            throws SQLException {
        String sql = "SELECT COUNT(" + column + ") AS total FROM "
                + table + " " + condition;
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                counts.put(key, resultSet.getInt("total"));
            }
        }
    }
}
