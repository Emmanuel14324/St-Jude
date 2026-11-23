package ug.sjch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventType;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.junit.jupiter.api.Test;

import ug.sjch.model.User;
import ug.sjch.security.UserSession;

class HospitalDashboardViewTest {
    @Test
    void departmentDashboardsLoad() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable loadView = () -> {
            try {
                UserSession.clear();
                Parent loginRoot = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/login.fxml"
                        )
                );
                assertNotNull(loginRoot);
                Parent genericRoot = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/role-dashboard.fxml"
                        )
                );
                assertNotNull(genericRoot);
                Parent root = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/outpatient-dashboard.fxml"
                        )
                );
                assertNotNull(root);
                for (String buttonId : new String[] {
                        "dashboardButton", "patientQueueButton",
                        "patientRecordsButton", "startConsultationButton",
                        "diagnosisButton", "completeConsultationButton",
                        "prescriptionsButton",
                        "laboratoryRequestsButton",
                        "consultationHistoryButton",
                        "followUpManagementButton"
                }) {
                    Button button = (Button) root.lookup("#" + buttonId);
                    assertNotNull(button, "Missing outpatient action: "
                            + buttonId);
                    assertTrue(button.isDisabled(),
                            "Outpatient actions should require a doctor "
                                    + "session: " + buttonId);
                    assertNotNull(button.getOnAction(),
                            "Outpatient action is not wired: " + buttonId);
                }
                Parent adminRoot = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/admin-dashboard.fxml"
                        ));
                assertNotNull(adminRoot);
                for (String buttonId : new String[] {
                        "overviewButton", "patientsButton",
                        "appointmentsButton", "staffButton", "usersButton",
                        "departmentsButton", "reportsButton", "auditButton"
                }) {
                    Button button = (Button) adminRoot.lookup("#" + buttonId);
                    assertNotNull(button, "Missing admin action: " + buttonId);
                    assertTrue(button.isDisabled(),
                            "Admin action should require an administrator session: "
                                    + buttonId);
                }
                UserSession.setCurrentUser(new User(
                        1, "admin-test", "Administrator", true, null));
                Parent authorizedAdminRoot = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/admin-dashboard.fxml"
                        ));
                for (String buttonId : new String[] {
                        "overviewButton", "patientsButton",
                        "appointmentsButton", "staffButton", "usersButton",
                        "departmentsButton", "reportsButton", "auditButton"
                }) {
                    Button button = (Button) authorizedAdminRoot.lookup(
                            "#" + buttonId);
                    assertNotNull(button, "Missing admin action: " + buttonId);
                    assertTrue(!button.isDisabled(),
                            "Admin action should be enabled for an "
                                    + "administrator: " + buttonId);
                    button.fire();
                }
                FXMLLoader receptionLoader = new FXMLLoader(
                        getClass().getResource(
                                "/ug/sjch/views/reception-dashboard.fxml"
                        ));
                Parent receptionRoot = receptionLoader.load();
                assertNotNull(receptionRoot);
                for (String buttonId : new String[] {
                        "dashboardButton", "registerPatientButton",
                        "patientDirectoryButton", "bookAppointmentButton",
                        "appointmentScheduleButton", "patientCheckInButton",
                        "patientSearchButton", "notificationsButton"
                }) {
                    Button button = (Button) receptionRoot.lookup(
                            "#" + buttonId);
                    assertNotNull(button, "Missing reception action: "
                            + buttonId);
                    assertTrue(!button.isDisabled(),
                            "Reception action should be enabled: "
                                    + buttonId);
                }
                UserSession.clear();
                Parent nursingRoot = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/nursing-dashboard.fxml"
                        )
                );
                assertNotNull(nursingRoot);
                Parent laboratoryRoot = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/laboratory-dashboard.fxml"
                        )
                );
                assertNotNull(laboratoryRoot);
                for (String buttonId : new String[] {
                        "dashboardButton", "testRequestsButton",
                        "sampleCollectionButton", "testsInProgressButton",
                        "enterResultsButton", "verifyResultsButton",
                        "historyButton", "printResultsButton",
                        "reportsButton", "pendingRequestsAction",
                        "awaitingVerificationAction", "refreshButton",
                        "topEnterResultsButton", "topReportsButton"
                }) {
                    Button button = (Button) laboratoryRoot.lookup(
                            "#" + buttonId);
                    assertNotNull(button, "Missing laboratory action: "
                            + buttonId);
                    assertTrue(button.isDisabled(),
                            "Laboratory action should require a technician "
                                    + "session: " + buttonId);
                    assertNotNull(button.getOnAction(),
                            "Laboratory action is not wired: " + buttonId);
                }
                UserSession.setCurrentUser(new User(
                        3, "lab-test", "Lab Technician", true, 3L));
                FXMLLoader laboratoryLoader = new FXMLLoader(
                        getClass().getResource(
                                "/ug/sjch/views/laboratory-dashboard.fxml"
                        ));
                Parent authorizedLaboratoryRoot = laboratoryLoader.load();
                Scene laboratoryScene = new Scene(
                        authorizedLaboratoryRoot);
                laboratoryScene.getStylesheets().add(getClass().getResource(
                        "/ug/sjch/styles/application.css").toExternalForm());
                authorizedLaboratoryRoot.applyCss();
                authorizedLaboratoryRoot.resize(1280, 800);
                authorizedLaboratoryRoot.layout();
                VBox laboratorySidebar = (VBox) authorizedLaboratoryRoot
                        .lookup("#sidebar");
                Region resizeHandle = (Region) authorizedLaboratoryRoot
                        .lookup("#sidebarResizeHandle");
                assertNotNull(laboratorySidebar);
                assertNotNull(resizeHandle);
                fireResizeEvent(resizeHandle, MouseEvent.MOUSE_PRESSED,
                        200, 1, true);
                fireResizeEvent(resizeHandle, MouseEvent.MOUSE_DRAGGED,
                        300, 1, true);
                assertEquals(320, laboratorySidebar.getPrefWidth(), 0.001);
                authorizedLaboratoryRoot.layout();
                assertEquals(320, laboratorySidebar.getWidth(), 0.001);
                fireResizeEvent(resizeHandle, MouseEvent.MOUSE_RELEASED,
                        300, 1, false);
                fireResizeEvent(resizeHandle, MouseEvent.MOUSE_PRESSED,
                        300, 1, true);
                fireResizeEvent(resizeHandle, MouseEvent.MOUSE_DRAGGED,
                        150, 1, true);
                assertEquals(190, laboratorySidebar.getPrefWidth(), 0.001);
                authorizedLaboratoryRoot.layout();
                assertEquals(190, laboratorySidebar.getWidth(), 0.001);
                fireResizeEvent(resizeHandle, MouseEvent.MOUSE_RELEASED,
                        150, 1, false);
                fireResizeEvent(resizeHandle, MouseEvent.MOUSE_PRESSED,
                        300, 2, true);
                assertEquals(238, laboratorySidebar.getPrefWidth(), 0.001);
                for (String buttonId : new String[] {
                        "dashboardButton", "testRequestsButton",
                        "sampleCollectionButton", "testsInProgressButton",
                        "enterResultsButton", "verifyResultsButton",
                        "historyButton", "printResultsButton", "reportsButton",
                        "pendingRequestsAction", "awaitingVerificationAction",
                        "refreshButton", "topEnterResultsButton",
                        "topReportsButton", "logoutButton"
                }) {
                    Button button = (Button) authorizedLaboratoryRoot.lookup(
                            "#" + buttonId);
                    assertNotNull(button, "Missing laboratory action: "
                            + buttonId);
                    assertTrue(!button.isDisabled(),
                            "Laboratory action should be enabled for an "
                                    + "authorized technician: " + buttonId);
                    assertNotNull(button.getOnAction(),
                            "Laboratory action is not wired: " + buttonId);
                    if (!"logoutButton".equals(buttonId)) {
                        button.fire();
                    }
                }
                UserSession.clear();
                Parent pharmacyRoot = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/pharmacy-dashboard.fxml"
                        )
                );
                assertNotNull(pharmacyRoot);
                for (String buttonId : new String[] {
                        "dashboardButton", "inventoryButton",
                        "addMedicineButton", "stockManagementButton",
                        "suppliersButton", "prescriptionsButton",
                        "dispenseButton", "expiryAlertsButton",
                        "stockReportsButton", "topAddMedicineButton"
                }) {
                    Button button = (Button) pharmacyRoot.lookup(
                            "#" + buttonId);
                    assertNotNull(button, "Missing pharmacy action: "
                            + buttonId);
                    assertTrue(button.isDisabled(),
                            "Pharmacy action should require a pharmacy "
                                    + "session: " + buttonId);
                    assertNotNull(button.getOnAction(),
                            "Pharmacy action is not wired: " + buttonId);
                }
                UserSession.setCurrentUser(new User(
                        2, "pharmacy-test", "Pharmacist", true, null));
                FXMLLoader authorizedPharmacyLoader = new FXMLLoader(
                        getClass().getResource(
                                "/ug/sjch/views/pharmacy-dashboard.fxml"
                        ));
                Parent authorizedPharmacyRoot =
                        authorizedPharmacyLoader.load();
                assertNotNull(authorizedPharmacyLoader.getNamespace()
                        .get("categoryCards"));
                for (String buttonId : new String[] {
                        "dashboardButton", "inventoryButton",
                        "addMedicineButton", "stockManagementButton",
                        "suppliersButton", "prescriptionsButton",
                        "dispenseButton", "expiryAlertsButton", "logoutButton",
                        "stockReportsButton", "topAddMedicineButton",
                        "allFilterButton", "lowStockFilterButton",
                        "expiringFilterButton", "refreshButton"
                }) {
                    Button button = (Button) authorizedPharmacyRoot.lookup(
                            "#" + buttonId);
                    assertNotNull(button, "Missing pharmacy action: "
                            + buttonId);
                    assertTrue(!button.isDisabled(),
                            "Pharmacy action should be enabled for an "
                                    + "authorized pharmacist: " + buttonId);
                    assertNotNull(button.getOnAction(),
                            "Pharmacy action is not wired: " + buttonId);
                    if (!"addMedicineButton".equals(buttonId)
                            && !"topAddMedicineButton".equals(buttonId)
                            && !"logoutButton".equals(buttonId)) {
                        button.fire();
                    }
                }
                TextField pharmacySearch = (TextField) authorizedPharmacyRoot
                        .lookup("#medicineSearchField");
                assertNotNull(pharmacySearch);
                pharmacySearch.setText("test");
                pharmacySearch.fireEvent(new ActionEvent());
                UserSession.clear();
                Parent inpatientRoot = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/inpatient-dashboard.fxml"
                        )
                );
                assertNotNull(inpatientRoot);
                Parent financeRoot = FXMLLoader.load(
                        getClass().getResource(
                                "/ug/sjch/views/finance-dashboard.fxml"
                        )
                );
                assertNotNull(financeRoot);
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                UserSession.clear();
                loaded.countDown();
            }
        };

        try {
            Platform.startup(loadView);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(loadView);
        }

        try {
            assertTrue(loaded.await(15, TimeUnit.SECONDS),
                    "The JavaFX view did not finish loading");
            assertNull(failure.get(), () -> "Unable to load dashboard: "
                    + failure.get());
        } finally {
            Platform.exit();
        }
    }

    private static void fireResizeEvent(Region handle,
                                        EventType<MouseEvent> eventType,
                                        double sceneX, int clickCount,
                                        boolean primaryButtonDown) {
        MouseEvent event = new MouseEvent(
                eventType, sceneX, 0, sceneX, 0,
                primaryButtonDown ? MouseButton.PRIMARY : MouseButton.NONE,
                clickCount, false, false, false, false,
                primaryButtonDown, false, false,
                false, false, true,
                new PickResult(handle, sceneX, 0)
        );
        handle.fireEvent(event);
    }
}
