package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InpatientWorkflowValidationTest {
    @Test
    void acceptsOnlyNonOccupiedBedMaintenanceStates() {
        assertEquals("AVAILABLE",
                InpatientDashboardDao.normalizeBedStatus(" available "));
        assertEquals("CLEANING",
                InpatientDashboardDao.normalizeBedStatus("Cleaning"));
        assertEquals("OUT_OF_SERVICE",
                InpatientDashboardDao.normalizeBedStatus("out_of_service"));
    }

    @Test
    void preventsManualOccupancyAndUnknownBedStates() {
        assertThrows(IllegalArgumentException.class,
                () -> InpatientDashboardDao.normalizeBedStatus("OCCUPIED"));
        assertThrows(IllegalArgumentException.class,
                () -> InpatientDashboardDao.normalizeBedStatus("BROKEN"));
        assertThrows(IllegalArgumentException.class,
                () -> InpatientDashboardDao.normalizeBedStatus(" "));
    }

    @Test
    void normalizesEditableMetadataAndRejectsMissingRequiredNames() {
        assertEquals("Ward B",
                InpatientDashboardDao.normalizeWardName(" Ward B "));
        assertEquals("Medical",
                InpatientDashboardDao.normalizeWardType(" Medical "));
        assertEquals("Bed 12",
                InpatientDashboardDao.normalizeBedNumber(" Bed 12 "));
        assertThrows(IllegalArgumentException.class,
                () -> InpatientDashboardDao.normalizeWardName(" "));
        assertThrows(IllegalArgumentException.class,
                () -> InpatientDashboardDao.normalizeBedNumber(" "));
    }

    @Test
    void refusesToChangeOrDeleteAnyReferencedWardOrBed() {
        InpatientDashboardDao.requireNoReferences("Bed is referenced", 0);
        assertThrows(IllegalStateException.class,
                () -> InpatientDashboardDao.requireNoReferences(
                        "Bed is referenced by admissions", 1));
    }
}
