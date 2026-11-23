package ug.sjch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class LoginControllerTest {
    @Test
    void routesAllCanonicalRolesToTheirDepartmentDashboards() {
        Map<String, String> routes = Map.of(
                "Administrator", "/ug/sjch/views/admin-dashboard.fxml",
                "Receptionist", "/ug/sjch/views/reception-dashboard.fxml",
                "Doctor", "/ug/sjch/views/outpatient-dashboard.fxml",
                "Nurse", "/ug/sjch/views/nursing-dashboard.fxml",
                "Lab Technician", "/ug/sjch/views/laboratory-dashboard.fxml",
                "Pharmacist", "/ug/sjch/views/pharmacy-dashboard.fxml",
                "Accountant", "/ug/sjch/views/finance-dashboard.fxml",
                "Ward Manager", "/ug/sjch/views/inpatient-dashboard.fxml"
        );

        routes.forEach((role, expectedPath) ->
                assertEquals(expectedPath,
                        LoginController.dashboardPathForRole(role), role));
    }

    @Test
    void preservesLegacyRoleAliases() {
        assertEquals("/ug/sjch/views/outpatient-dashboard.fxml",
                LoginController.dashboardPathForRole("Medical Officer"));
        assertEquals("/ug/sjch/views/laboratory-dashboard.fxml",
                LoginController.dashboardPathForRole("Laboratory Technician"));
        assertEquals("/ug/sjch/views/role-dashboard.fxml",
                LoginController.dashboardPathForRole("Unknown"));
    }
}
