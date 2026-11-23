package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class NursingWorkflowValidationTest {
    @Test
    void acceptsValidVitalsAndOptionalNursingNotes() {
        NursingDashboardDao.ObservationInput input =
                NursingDashboardDao.normalizeObservation(
                        "37.2", "72", "120", "80", "16", "98", "Patient stable");

        assertEquals(new BigDecimal("37.2"), input.temperature());
        assertEquals(new BigDecimal("72"), input.pulse());
        assertEquals(new BigDecimal("120"), input.systolic());
        assertEquals(new BigDecimal("80"), input.diastolic());
        assertEquals(new BigDecimal("16"), input.respiration());
        assertEquals(new BigDecimal("98"), input.oxygenSaturation());
        assertEquals("Patient stable", input.notes());
    }

    @Test
    void permitsNotesWithoutVitalsButNotAnEmptyObservation() {
        NursingDashboardDao.ObservationInput input =
                NursingDashboardDao.normalizeObservation(
                        "", "", "", "", "", "", "Repositioned patient");

        assertNull(input.temperature());
        assertEquals("Repositioned patient", input.notes());
        assertThrows(IllegalArgumentException.class,
                () -> NursingDashboardDao.normalizeObservation(
                        "", "", "", "", "", "", " "));
    }

    @Test
    void rejectsOutOfRangeValuesAndIncompleteBloodPressure() {
        assertThrows(IllegalArgumentException.class,
                () -> NursingDashboardDao.normalizeObservation(
                        "37", "251", "", "", "", "", null));
        assertThrows(IllegalArgumentException.class,
                () -> NursingDashboardDao.normalizeObservation(
                        "", "", "120", "", "", "", null));
        assertThrows(IllegalArgumentException.class,
                () -> NursingDashboardDao.normalizeObservation(
                        "", "", "70", "90", "", "", null));
        assertThrows(IllegalArgumentException.class,
                () -> NursingDashboardDao.normalizeObservation(
                        "not-a-number", "", "", "", "", "", null));
    }
}
