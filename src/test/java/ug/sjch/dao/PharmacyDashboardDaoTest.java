package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import ug.sjch.config.DatabaseConfig;
import ug.sjch.dao.PharmacyDashboardDao.InventoryFilter;

class PharmacyDashboardDaoTest {
    @Test
    void catalogEditsRejectInvalidValuesBeforeDatabaseAccess() {
        PharmacyDashboardDao dao = new PharmacyDashboardDao();

        assertThrows(IllegalArgumentException.class,
                () -> dao.updateMedicine(1, " ", null, null, null, null,
                        BigDecimal.ZERO, 1));
        assertThrows(IllegalArgumentException.class,
                () -> dao.updateMedicine(1, "Medicine", null, null, null,
                        null, BigDecimal.valueOf(-1), 1));
        assertThrows(IllegalArgumentException.class,
                () -> dao.updateSupplier(1, "Supplier", "x".repeat(121),
                        null, null, null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> dao.updateBatchMetadata(1, null, "Batch",
                        BigDecimal.valueOf(-1), BigDecimal.ZERO, null, 1));
    }

    @Test
    void pharmacyReadQueriesRunAgainstOracle() throws Exception {
        assumeTrue(
                DatabaseConfig.isConfigured(),
                "Set SJCH_DB_URL, SJCH_DB_USER and SJCH_DB_PASSWORD to test Oracle queries"
        );

        PharmacyDashboardDao dao = new PharmacyDashboardDao();
        PharmacyDashboardDao.PharmacyMetrics metrics = dao.loadMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.totalMedicines() >= 0);
        assertTrue(metrics.pendingPrescriptions() >= 0);
        assertNotNull(dao.loadCategorySummary());
        assertTrue(dao.loadInventory("", InventoryFilter.ALL).size() <= 100);
        assertNotNull(dao.loadInventory("", InventoryFilter.LOW_STOCK));
        assertNotNull(dao.loadInventory("", InventoryFilter.EXPIRING));
        assertNotNull(dao.loadInventory("paracetamol", InventoryFilter.ALL));
        assertNotNull(dao.loadMedicineCatalog());
        assertNotNull(dao.loadMedicines());
        assertNotNull(dao.loadSuppliers(""));
        assertNotNull(dao.loadPendingPrescriptions());
        assertNotNull(dao.loadPrescriptionItems(-1));
        assertNotNull(dao.loadInventoryTransactions());
        assertNotNull(dao.loadStockReport());
    }
}
