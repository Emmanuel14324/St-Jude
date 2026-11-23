package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ug.sjch.config.DatabaseConfig;

class AdminDashboardDaoTest {
    @Test
    void userAccountEditsValidateUsernameAndOptionalPasswordReset() {
        assertEquals("admin.user",
                AdminDashboardDao.validateAccountUsername(" admin.user "));
        assertThrows(IllegalArgumentException.class,
                () -> AdminDashboardDao.validateAccountUsername("ab"));
        assertThrows(IllegalArgumentException.class,
                () -> AdminDashboardDao.validateAccountUsername("two words"));
        assertThrows(IllegalArgumentException.class,
                () -> AdminDashboardDao.validateAccountUsername("x".repeat(61)));

        assertNull(AdminDashboardDao.validatePasswordReset(null));
        assertNull(AdminDashboardDao.validatePasswordReset("  "));
        assertEquals("LongPassword123",
                AdminDashboardDao.validatePasswordReset("LongPassword123"));
        assertThrows(IllegalArgumentException.class,
                () -> AdminDashboardDao.validatePasswordReset("short"));
    }

    @Test
    void appointmentMaintenanceIsLimitedToFutureScheduledVisits() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 21, 12, 0);

        assertTrue(AdminDashboardDao.canEditAppointment(
                "SCHEDULED", now.plusHours(1), now));
        assertFalse(AdminDashboardDao.canEditAppointment(
                "CHECKED_IN", now.plusHours(1), now));
        assertFalse(AdminDashboardDao.canEditAppointment(
                "IN_PROGRESS", now.plusHours(1), now));
        assertFalse(AdminDashboardDao.canEditAppointment(
                "CANCELLED", now.plusHours(1), now));
        assertFalse(AdminDashboardDao.canEditAppointment(
                "SCHEDULED", now, now));
    }

    @Test
    void administratorQueriesRunAgainstOracle() throws Exception {
        assumeTrue(
                DatabaseConfig.isConfigured(),
                "Set SJCH_DB_URL, SJCH_DB_USER and SJCH_DB_PASSWORD to test Oracle queries"
        );

        AdminDashboardDao dao = new AdminDashboardDao();
        assertNotNull(dao.loadOverviewMetrics());
        assertNotNull(dao.loadDepartmentPerformance());
        assertTrue(dao.loadRecentPatients().size() <= 8);
        assertTrue(dao.loadRecentAppointments().size() <= 8);
        assertNotNull(dao.searchPatients(""));
        assertNotNull(dao.loadAppointments(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(7)));
        assertNotNull(dao.loadDepartments());
        assertNotNull(dao.searchStaff(""));
        assertNotNull(dao.loadUsers(""));
        assertNotNull(dao.loadStaffWithoutAccounts());
        assertNotNull(dao.loadRoles());
        assertNotNull(dao.loadRecentAuditLogs());
        assertNotNull(dao.loadRecentWorkflowEvents());
    }
}
