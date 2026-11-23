package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LaboratoryDashboardDaoTest {
    private final LaboratoryDashboardDao dao = new LaboratoryDashboardDao();

    @Test
    void resultEntryRejectsBlankAndOversizedTextBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.enterResult(1, "   ", 1));
        assertThrows(IllegalArgumentException.class,
                () -> dao.enterResult(1, "x".repeat(20_001), 1));
        assertThrows(IllegalArgumentException.class,
                () -> dao.correctResult(1, "   ", 1));
        assertThrows(IllegalArgumentException.class,
                () -> dao.correctResult(1, "x".repeat(20_001), 1));
    }

    @Test
    void onlyUncollectedRequestsCanBeCancelled() {
        assertTrue(LaboratoryDashboardDao.isCancellableRequest(
                "REQUESTED", false, false));
        assertFalse(LaboratoryDashboardDao.isCancellableRequest(
                "SAMPLE_COLLECTED", true, false));
        assertFalse(LaboratoryDashboardDao.isCancellableRequest(
                "REQUESTED", true, false));
        assertFalse(LaboratoryDashboardDao.isCancellableRequest(
                "REQUESTED", false, true));
    }

    @Test
    void resultCorrectionIsBlockedAfterClinicianReview() {
        Timestamp reviewedAt = Timestamp.from(Instant.now());
        assertTrue(LaboratoryDashboardDao.isCorrectableResult(
                "RESULT_ENTERED", null, null));
        assertFalse(LaboratoryDashboardDao.isCorrectableResult(
                "REVIEWED", null, null));
        assertFalse(LaboratoryDashboardDao.isCorrectableResult(
                "RESULT_ENTERED", reviewedAt, null));
        assertFalse(LaboratoryDashboardDao.isCorrectableResult(
                "RESULT_ENTERED", null, reviewedAt));
    }
}
