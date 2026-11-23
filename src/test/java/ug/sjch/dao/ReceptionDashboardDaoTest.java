package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ug.sjch.config.DatabaseConfig;

class ReceptionDashboardDaoTest {
    @Test
    void onlyFutureScheduledAppointmentsAreEditableOrCancellable() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 21, 12, 0);

        assertTrue(ReceptionWorkflowDao.canEditAppointment(
                "SCHEDULED", now.plusHours(1), now));
        assertFalse(ReceptionWorkflowDao.canEditAppointment(
                "CHECKED_IN", now.plusHours(1), now));
        assertFalse(ReceptionWorkflowDao.canEditAppointment(
                "COMPLETED", now.plusHours(1), now));
        assertFalse(ReceptionWorkflowDao.canEditAppointment(
                "SCHEDULED", now, now));
        assertFalse(ReceptionWorkflowDao.canEditAppointment(
                "SCHEDULED", now.minusMinutes(1), now));
    }

    @Test
    void receptionQueriesRunAgainstOracle() throws Exception {
        assumeTrue(
                DatabaseConfig.isConfigured(),
                "Set SJCH_DB_URL, SJCH_DB_USER and SJCH_DB_PASSWORD to test Oracle queries"
        );

        ReceptionDashboardDao dao = new ReceptionDashboardDao();
        ReceptionWorkflowDao workflowDao = new ReceptionWorkflowDao();
        ReceptionDashboardDao.Metrics metrics = dao.loadMetrics();
        ReceptionDashboardDao.WorkflowCounts workflow =
                dao.loadTodayWorkflow();

        assertTrue(metrics.registeredToday() >= 0);
        assertTrue(metrics.appointmentsToday() >= 0);
        assertTrue(metrics.waitingPatients() >= 0);
        assertTrue(metrics.upcomingAppointments() >= 0);
        assertTrue(workflow.total() >= 0);
        assertTrue(dao.loadRecentPatients().size() <= 6);
        assertTrue(dao.loadUpcomingAppointments().size() <= 8);
        assertNotNull(workflow);
        assertNotNull(workflowDao.searchPatients(""));
        assertNotNull(workflowDao.loadDoctors());
        assertNotNull(workflowDao.loadAppointments(
                LocalDate.now(), LocalDate.now().plusDays(7)));
        assertNotNull(workflowDao.loadTodayCheckIns());
        assertNotNull(workflowDao.loadRecentWorkflowEvents());
    }
}
