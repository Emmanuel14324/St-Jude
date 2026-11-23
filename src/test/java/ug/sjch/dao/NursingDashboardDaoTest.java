package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import ug.sjch.config.DatabaseConfig;

class NursingDashboardDaoTest {
    @Test
    void nursingMetricsQueryRunsAgainstOracle() throws Exception {
        assumeTrue(
                DatabaseConfig.isConfigured(),
                "Set SJCH_DB_URL, SJCH_DB_USER and SJCH_DB_PASSWORD to test Oracle queries"
        );

        assertNotNull(new NursingDashboardDao().loadMetrics());
    }
}
