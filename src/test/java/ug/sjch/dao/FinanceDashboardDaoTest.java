package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import ug.sjch.config.DatabaseConfig;

class FinanceDashboardDaoTest {
    @Test
    void onlyUnpaidDraftOrIssuedInvoicesCanBeVoided() {
        FinanceDashboardDao.requireInvoiceVoidAllowed("DRAFT", 0);
        FinanceDashboardDao.requireInvoiceVoidAllowed("ISSUED", 0);

        for (String status : new String[] {
                "PARTIALLY_PAID", "PAID", "VOID", "UNKNOWN"}) {
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> FinanceDashboardDao.requireInvoiceVoidAllowed(
                            status, 0));
        }
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> FinanceDashboardDao.requireInvoiceVoidAllowed(
                        "ISSUED", 1));
    }

    @Test
    void onlyUnpaidDraftInvoicesCanBeEdited() {
        FinanceDashboardDao.requireDraftInvoiceEditable("DRAFT", 0);

        assertThrows(IllegalArgumentException.class,
                () -> FinanceDashboardDao.requireDraftInvoiceEditable(
                        "ISSUED", 0));
        assertThrows(IllegalArgumentException.class,
                () -> FinanceDashboardDao.requireDraftInvoiceEditable(
                        "DRAFT", 1));
    }

    @Test
    void invoiceLineTotalsUseValidatedTwoDecimalAmounts() {
        assertEquals(new BigDecimal("37.50"),
                FinanceDashboardDao.calculateInvoiceLineTotal(
                        new BigDecimal("2.50"), new BigDecimal("15.00")));
        assertThrows(IllegalArgumentException.class,
                () -> FinanceDashboardDao.calculateInvoiceLineTotal(
                        new BigDecimal("0"), new BigDecimal("15.00")));
        assertThrows(IllegalArgumentException.class,
                () -> FinanceDashboardDao.calculateInvoiceLineTotal(
                        new BigDecimal("1.001"), new BigDecimal("15.00")));
    }

    @Test
    void paymentReversalOnlyAllowsCompletedAndRecalculatesInvoiceState() {
        FinanceDashboardDao.requirePaymentReversalAllowed("COMPLETED");
        for (String status : new String[] {
                "PENDING", "FAILED", "REVERSED"}) {
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> FinanceDashboardDao.requirePaymentReversalAllowed(
                            status));
        }

        assertEquals("ISSUED",
                FinanceDashboardDao.invoiceStatusAfterReversal(
                        "PAID", new BigDecimal("100"),
                        BigDecimal.ZERO));
        assertEquals("PARTIALLY_PAID",
                FinanceDashboardDao.invoiceStatusAfterReversal(
                        "PAID", new BigDecimal("100"),
                        new BigDecimal("40")));
        assertEquals("VOID",
                FinanceDashboardDao.invoiceStatusAfterReversal(
                        "VOID", new BigDecimal("100"),
                        BigDecimal.ZERO));
    }

    @Test
    void financeReadQueriesAreSafeWhenTablesAreMissing() throws Exception {
        assumeTrue(DatabaseConfig.isConfigured(),
                "Set SJCH_DB_URL, SJCH_DB_USER and SJCH_DB_PASSWORD to test Oracle queries");

        FinanceDashboardDao dao = new FinanceDashboardDao();
        if (!dao.isConfigured()) {
            return;
        }
        assertNotNull(dao.loadMetrics());
        assertNotNull(dao.searchInvoices(""));
        assertNotNull(dao.loadOutstandingInvoices());
        assertNotNull(dao.loadPaymentHistory(""));
        assertNotNull(dao.loadPaymentMethods());
        assertNotNull(dao.loadMonthlyRevenue());
    }

    @Test
    void paymentValidationRejectsNonPositiveAmountsBeforeDatabaseAccess() {
        FinanceDashboardDao dao = new FinanceDashboardDao();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> dao.recordPayment(1, BigDecimal.ZERO,
                        "CASH", null, null, 1));
    }
}
