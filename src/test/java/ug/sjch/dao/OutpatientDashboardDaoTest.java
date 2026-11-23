package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import ug.sjch.config.DatabaseConfig;

class OutpatientDashboardDaoTest {
    @Test
    void diagnosisAndNotesAreEditableOnlyDuringAnActiveConsultation() {
        assertTrue(OutpatientDashboardDao.isEditableConsultation(
                "IN_PROGRESS"));
        assertFalse(OutpatientDashboardDao.isEditableConsultation(
                "COMPLETED"));
        assertFalse(OutpatientDashboardDao.isEditableConsultation(
                "CANCELLED"));
    }

    @Test
    void onlyUncollectedRequestsCanBeEditedOrCancelled() {
        assertTrue(OutpatientDashboardDao.isMutableLaboratoryRequest(
                "REQUESTED", false, false));
        assertFalse(OutpatientDashboardDao.isMutableLaboratoryRequest(
                "SAMPLE_COLLECTED", true, false));
        assertFalse(OutpatientDashboardDao.isMutableLaboratoryRequest(
                "REQUESTED", true, false));
        assertFalse(OutpatientDashboardDao.isMutableLaboratoryRequest(
                "REQUESTED", false, true));
    }

    @Test
    void onlyFutureScheduledFollowUpsCanBeCancelled() {
        LocalDateTime now = LocalDateTime.now();
        assertTrue(OutpatientDashboardDao.isCancellableFollowUp(
                "SCHEDULED", "FOLLOW-UP: review", now.plusDays(1), now));
        assertFalse(OutpatientDashboardDao.isCancellableFollowUp(
                "COMPLETED", "FOLLOW-UP: review", now.plusDays(1), now));
        assertFalse(OutpatientDashboardDao.isCancellableFollowUp(
                "SCHEDULED", "Routine appointment", now.plusDays(1), now));
        assertFalse(OutpatientDashboardDao.isCancellableFollowUp(
                "SCHEDULED", "FOLLOW-UP: review", now.minusMinutes(1), now));
    }

    @Test
    void prescriptionsAreMutableOnlyBeforeAnyDispensing() {
        assertTrue(OutpatientDashboardDao.isMutablePrescription(
                "PENDING", 0, 0));
        assertFalse(OutpatientDashboardDao.isMutablePrescription(
                "PARTIALLY_DISPENSED", 1, 1));
        assertFalse(OutpatientDashboardDao.isMutablePrescription(
                "DISPENSED", 1, 1));
        assertFalse(OutpatientDashboardDao.isMutablePrescription(
                "PENDING", 1, 0));
        assertFalse(OutpatientDashboardDao.isMutablePrescription(
                "PENDING", 0, 1));
    }

    @Test
    void outpatientQueriesRunAgainstOracle() throws Exception {
        assumeTrue(
                DatabaseConfig.isConfigured(),
                "Set SJCH_DB_URL, SJCH_DB_USER and SJCH_DB_PASSWORD to test Oracle queries"
        );

        OutpatientDashboardDao dao = new OutpatientDashboardDao();

        assertNotNull(dao.loadMetrics(0));
        assertNotNull(dao.loadWaitingQueue(0));
        assertNotNull(dao.loadTodayConsultations(0));
        assertNotNull(dao.loadConsultationHistory(0));
        assertNotNull(dao.loadMedicalRecords(0));
        assertNotNull(dao.loadPrescriptions(0));
        assertNotNull(dao.loadActiveMedicines());
        assertNotNull(dao.loadFollowUps(0));
        assertNotNull(dao.loadPendingLaboratoryRequests(0));
    }
}
