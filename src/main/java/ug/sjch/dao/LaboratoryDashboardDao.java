package ug.sjch.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class LaboratoryDashboardDao {
    public enum RequestFilter {
        ALL,
        REQUESTED,
        SAMPLE_COLLECTED,
        IN_PROGRESS,
        AWAITING_VERIFICATION,
        REVIEWED
    }

    public record Metrics(
            int pendingRequests,
            int samplesCollected,
            int testsInProgress,
            int completedTests,
            int awaitingVerification
    ) {
    }

    public record RequestRow(
            long requestId,
            String patientNumber,
            String patientName,
            String testName,
            String requestedAt,
            String status,
            String resultText,
            boolean reviewed
    ) {
    }

    public record ReportRow(String status, int requestCount) {
    }

    public Metrics loadMetrics(long userId) throws SQLException {
        String sql = """
                SELECT COUNT(CASE WHEN request_status = 'REQUESTED'
                                  THEN 1 END) AS pending_requests,
                       COUNT(CASE WHEN request_status = 'SAMPLE_COLLECTED'
                                  THEN 1 END) AS samples_collected,
                       COUNT(CASE WHEN request_status = 'IN_PROGRESS'
                                  THEN 1 END) AS tests_in_progress,
                       COUNT(CASE WHEN request_status IN (
                                      'RESULT_ENTERED', 'REVIEWED')
                                  THEN 1 END) AS completed_tests,
                       COUNT(CASE WHEN request_status = 'RESULT_ENTERED'
                                  THEN 1 END) AS awaiting_verification
                FROM lab_requests
                """;
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            requireConfigured(connection);
            requirePermission(connection, userId, "LAB_REQUEST_PROCESS");
            try (PreparedStatement statement =
                         connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new Metrics(
                        resultSet.getInt("pending_requests"),
                        resultSet.getInt("samples_collected"),
                        resultSet.getInt("tests_in_progress"),
                        resultSet.getInt("completed_tests"),
                        resultSet.getInt("awaiting_verification")
                );
            }
        }
    }

    public List<RequestRow> loadRequests(RequestFilter filter, String query,
                                         long userId) throws SQLException {
        RequestFilter selectedFilter = filter == null
                ? RequestFilter.ALL : filter;
        String search = normalize(query);
        StringBuilder sql = new StringBuilder("""
                SELECT l.lab_request_id, p.patient_number,
                       TRIM(p.first_name || ' ' || p.last_name)
                           AS patient_name,
                       l.test_name,
                       TO_CHAR(l.requested_at, 'DD Mon YYYY HH:MI AM')
                           AS requested_at,
                       l.request_status,
                       r.result_text,
                       CASE WHEN r.reviewed_at IS NULL
                                  AND l.reviewed_at IS NULL
                            THEN 0 ELSE 1 END
                           AS reviewed
                FROM lab_requests l
                JOIN consultations c
                  ON c.consultation_id = l.consultation_id
                JOIN patients p ON p.patient_id = l.patient_id
                LEFT JOIN lab_results r
                  ON r.lab_request_id = l.lab_request_id
                WHERE 1 = 1
                """);
        appendFilter(sql, selectedFilter);
        if (search != null) {
            sql.append("""
                      AND (LOWER(p.patient_number) LIKE ?
                           OR LOWER(p.first_name || ' ' || p.last_name)
                               LIKE ?
                           OR LOWER(l.test_name) LIKE ?)
                    """);
        }
        sql.append(" ORDER BY l.requested_at DESC");

        List<RequestRow> rows = new ArrayList<>();
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            requireConfigured(connection);
            requirePermission(connection, userId, "LAB_REQUEST_PROCESS");
            try (PreparedStatement statement =
                         connection.prepareStatement(sql.toString())) {
                int parameterIndex = 1;
                if (search != null) {
                    String pattern = "%" + search.toLowerCase() + "%";
                    statement.setString(parameterIndex++, pattern);
                    statement.setString(parameterIndex++, pattern);
                    statement.setString(parameterIndex, pattern);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        rows.add(new RequestRow(
                                resultSet.getLong("lab_request_id"),
                                resultSet.getString("patient_number"),
                                resultSet.getString("patient_name"),
                                resultSet.getString("test_name"),
                                resultSet.getString("requested_at"),
                                resultSet.getString("request_status"),
                                resultSet.getString("result_text"),
                                resultSet.getInt("reviewed") == 1
                        ));
                    }
                }
            }
        }
        return rows;
    }

    public List<ReportRow> loadReport(long userId) throws SQLException {
        String sql = """
                SELECT request_status, COUNT(*) AS request_count
                FROM lab_requests
                GROUP BY request_status
                ORDER BY request_status
                """;
        List<ReportRow> rows = new ArrayList<>();
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            requireConfigured(connection);
            requirePermission(connection, userId, "LAB_REQUEST_PROCESS");
            try (PreparedStatement statement =
                         connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new ReportRow(
                            resultSet.getString("request_status"),
                            resultSet.getInt("request_count")
                    ));
                }
            }
        }
        return rows;
    }

    public void collectSample(long requestId, long userId)
            throws SQLException {
        transitionRequest(requestId, userId, "REQUESTED", "SAMPLE_COLLECTED",
                "sample_collected_at", "LAB_SAMPLE_COLLECTED");
    }

    public void startProcessing(long requestId, long userId)
            throws SQLException {
        transitionRequest(requestId, userId, "SAMPLE_COLLECTED", "IN_PROGRESS",
                "processing_started_at", "LAB_PROCESSING_STARTED");
    }

    public void enterResult(long requestId, String resultText, long userId)
            throws SQLException {
        String normalizedResult = normalize(resultText);
        if (normalizedResult == null) {
            throw new IllegalArgumentException("Result details are required.");
        }
        if (normalizedResult.length() > 20000) {
            throw new IllegalArgumentException(
                    "Result details must be 20,000 characters or fewer.");
        }

        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            requireConfigured(connection);
            connection.setAutoCommit(false);
            try {
                long staffId = requirePermission(connection, userId,
                        "LAB_RESULT_ENTER");
                RequestContext request = lockRequest(connection, requestId);
                if (!"IN_PROGRESS".equals(request.status())) {
                    throw new IllegalStateException(
                            "Only tests in progress can receive results.");
                }
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     INSERT INTO lab_results (
                                         lab_request_id, result_text,
                                         entered_by_staff_id
                                     ) VALUES (?, ?, ?)
                                     """)) {
                    statement.setLong(1, requestId);
                    statement.setString(2, normalizedResult);
                    statement.setLong(3, staffId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     UPDATE lab_requests
                                     SET request_status = 'RESULT_ENTERED',
                                         result_entered_at = SYSTIMESTAMP
                                     WHERE lab_request_id = ?
                                       AND request_status = 'IN_PROGRESS'
                                     """)) {
                    statement.setLong(1, requestId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "The test request changed before the result "
                                        + "could be saved.");
                    }
                }
                writeWorkflowEvent(connection, request, requestId, userId,
                        "LAB_RESULT_ENTERED",
                        "Laboratory result entered and sent to the requesting "
                                + "clinician for review.");
                writeAudit(connection, userId, "LAB_RESULT_ENTERED",
                        "Entered result for laboratory request " + requestId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void correctResult(long requestId, String resultText, long userId)
            throws SQLException {
        String normalizedResult = normalize(resultText);
        if (normalizedResult == null) {
            throw new IllegalArgumentException("Result details are required.");
        }
        if (normalizedResult.length() > 20000) {
            throw new IllegalArgumentException(
                    "Result details must be 20,000 characters or fewer.");
        }

        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            requireConfigured(connection);
            connection.setAutoCommit(false);
            try {
                long staffId = requirePermission(connection, userId,
                        "LAB_RESULT_ENTER");
                RequestContext request = lockRequest(connection, requestId);
                Timestamp resultReviewedAt;
                long resultId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT lab_result_id, reviewed_at
                        FROM lab_results
                        WHERE lab_request_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, requestId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalStateException(
                                    "No result exists to correct.");
                        }
                        resultId = resultSet.getLong("lab_result_id");
                        resultReviewedAt =
                                resultSet.getTimestamp("reviewed_at");
                    }
                }
                if (!isCorrectableResult(request.status(), resultReviewedAt,
                        request.reviewedAt())) {
                    throw new IllegalStateException(
                            "A result can only be corrected before clinician "
                                    + "review.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE lab_results
                        SET result_text = ?
                        WHERE lab_result_id = ?
                          AND reviewed_at IS NULL
                        """)) {
                    statement.setString(1, normalizedResult);
                    statement.setLong(2, resultId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "The result changed before it could be "
                                        + "corrected.");
                    }
                }
                writeWorkflowEvent(connection, request, requestId, userId,
                        "LAB_RESULT_CORRECTED",
                        "Laboratory result corrected before clinician review.");
                writeAudit(connection, userId, "LAB_RESULT_CORRECTED",
                        "Corrected result for laboratory request " + requestId);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public void cancelRequest(long requestId, long userId) throws SQLException {
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            requireConfigured(connection);
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "LAB_REQUEST_PROCESS");
                RequestContext request = lockRequest(connection, requestId);
                boolean hasResult = hasResult(connection, requestId);
                if (!isCancellableRequest(request.status(),
                        request.sampleCollectedAt() != null, hasResult)) {
                    throw new IllegalStateException(
                            "Only requests awaiting sample collection can be "
                                    + "cancelled.");
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
                                "The request changed before it could be "
                                        + "cancelled.");
                    }
                }
                writeWorkflowEvent(connection, request, requestId, userId,
                        "LAB_REQUEST_CANCELLED",
                        "Request cancelled before sample collection.");
                writeAudit(connection, userId, "LAB_REQUEST_CANCELLED",
                        "Cancelled laboratory request " + requestId
                                + " before sample collection");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    static boolean isCancellableRequest(String status, boolean sampleCollected,
                                        boolean hasResult) {
        return "REQUESTED".equals(status) && !sampleCollected && !hasResult;
    }

    static boolean isCorrectableResult(String status,
                                       Timestamp resultReviewedAt,
                                       Timestamp requestReviewedAt) {
        return "RESULT_ENTERED".equals(status)
                && resultReviewedAt == null && requestReviewedAt == null;
    }

    private void transitionRequest(long requestId, long userId,
                                   String currentStatus, String nextStatus,
                                   String timestampColumn, String eventType)
            throws SQLException {
        if (!"sample_collected_at".equals(timestampColumn)
                && !"processing_started_at".equals(timestampColumn)) {
            throw new IllegalArgumentException("Unsupported lab transition.");
        }
        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePermission(connection, userId, "LAB_REQUEST_PROCESS");
                RequestContext request = lockRequest(connection, requestId);
                if (!currentStatus.equals(request.status())) {
                    throw new IllegalStateException(
                            "This test request is no longer in the expected "
                                    + "workflow state.");
                }
                String sql = """
                        UPDATE lab_requests
                        SET request_status = ?, %s = SYSTIMESTAMP
                        WHERE lab_request_id = ?
                          AND request_status = ?
                        """.formatted(timestampColumn);
                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {
                    statement.setString(1, nextStatus);
                    statement.setLong(2, requestId);
                    statement.setString(3, currentStatus);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "The test request changed before it could be "
                                        + "updated.");
                    }
                }
                writeWorkflowEvent(connection, request, requestId, userId,
                        eventType, "Laboratory request moved to "
                                + nextStatus.replace('_', ' ') + ".");
                writeAudit(connection, userId, eventType,
                        "Updated laboratory request " + requestId + " to "
                                + nextStatus);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private RequestContext lockRequest(Connection connection, long requestId)
            throws SQLException {
        long patientId;
        long consultationId;
        String status;
        Timestamp sampleCollectedAt;
        Timestamp reviewedAt;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT lab_request_id, patient_id, consultation_id,
                       request_status, sample_collected_at, reviewed_at
                FROM lab_requests
                WHERE lab_request_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, requestId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "The selected laboratory request no longer exists.");
                }
                patientId = resultSet.getLong("patient_id");
                consultationId = resultSet.getLong("consultation_id");
                status = resultSet.getString("request_status");
                sampleCollectedAt =
                        resultSet.getTimestamp("sample_collected_at");
                reviewedAt = resultSet.getTimestamp("reviewed_at");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT appointment_id
                FROM consultations
                WHERE consultation_id = ?
                """)) {
            statement.setLong(1, consultationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException(
                            "The consultation linked to this request "
                                    + "no longer exists.");
                }
                return new RequestContext(patientId,
                        resultSet.getLong("appointment_id"), status,
                        sampleCollectedAt, reviewedAt);
            }
        }
    }

    private boolean hasResult(Connection connection, long requestId)
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

    private long requirePermission(Connection connection, long userId,
                                   String permissionCode) throws SQLException {
        String sql = """
                SELECT s.staff_id
                FROM users u
                JOIN role_permissions rp ON rp.role_id = u.role_id
                JOIN staff s ON s.staff_id = u.staff_id
                WHERE u.user_id = ?
                  AND u.is_active = 'Y'
                  AND s.status = 'ACTIVE'
                  AND rp.permission_code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, permissionCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SecurityException(
                            "You do not have permission for this laboratory "
                                    + "action.");
                }
                return resultSet.getLong("staff_id");
            }
        }
    }

    private void writeWorkflowEvent(Connection connection,
                                    RequestContext request, long requestId,
                                    long userId, String eventType,
                                    String details) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workflow_events (
                    patient_id, appointment_id, entity_type, entity_id,
                    event_type, department_name, performed_by_user_id, details
                ) VALUES (?, ?, 'LAB_REQUEST', ?, ?, 'Laboratory', ?, ?)
                """)) {
            statement.setLong(1, request.patientId());
            statement.setLong(2, request.appointmentId());
            statement.setLong(3, requestId);
            statement.setString(4, eventType);
            statement.setLong(5, userId);
            statement.setString(6, details);
            statement.executeUpdate();
        }
    }

    private void writeAudit(Connection connection, long userId, String action,
                            String details) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_logs (user_id, action, description)
                VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, action);
            statement.setString(3, details);
            statement.executeUpdate();
        }
    }

    private void requireConfigured(Connection connection) throws SQLException {
        if (!OracleSchemaInspector.tableExists(connection, "LAB_REQUESTS")
                || !OracleSchemaInspector.tableExists(connection, "LAB_RESULTS")
                || !OracleSchemaInspector.tableExists(connection, "CONSULTATIONS")
                || !OracleSchemaInspector.tableExists(connection, "PATIENTS")) {
            throw new SQLException(
                    "Laboratory workflow tables are not configured. Run "
                            + "db/cross-department-workflow.sql first.");
        }
    }

    private void appendFilter(StringBuilder sql, RequestFilter filter) {
        switch (filter) {
            case ALL -> {
            }
            case REQUESTED -> sql.append(
                    " AND l.request_status = 'REQUESTED'");
            case SAMPLE_COLLECTED -> sql.append(
                    " AND l.request_status = 'SAMPLE_COLLECTED'");
            case IN_PROGRESS -> sql.append(
                    " AND l.request_status = 'IN_PROGRESS'");
            case AWAITING_VERIFICATION -> sql.append(
                    " AND l.request_status = 'RESULT_ENTERED'");
            case REVIEWED -> sql.append(
                    " AND l.request_status = 'REVIEWED'");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void rollback(Connection connection, Exception exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    private record RequestContext(long patientId, long appointmentId,
                                  String status, Timestamp sampleCollectedAt,
                                  Timestamp reviewedAt) {
    }
}
