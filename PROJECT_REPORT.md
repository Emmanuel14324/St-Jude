# St Jude Central Hospital Management System

## Project Report

**Project:** St Jude Central Hospital (SJCH)  
**Technology:** Java 21, JavaFX 21.0.6, FXML, CSS, Oracle Database 21c, Maven  
**Report date:** 23 November 2026

## 1. Executive Summary

The project is a desktop hospital management system implemented with JavaFX and an Oracle database. It provides role-based login and separate workspaces for administration, reception, outpatient care, nursing, ward management, laboratory, pharmacy, and finance.

The system is organized in a layered MVC-style structure:

- JavaFX FXML and CSS provide the user interface.
- Controller classes manage navigation and user actions.
- DAO classes execute Oracle SQL and enforce workflow rules.
- Model and session classes hold authenticated-user state.
- SQL scripts define the core schema, cross-department workflow, permissions, indexes, and demo data.

## 2. Overall UI Design

### 2.1 Visual Style

The interface uses a consistent desktop design system:

- Font: Segoe UI / Arial
- Primary hospital blue: `#3154c5`
- Teal action color: `#1d7a78`
- Dark branded text and backgrounds: `#102a43`
- Light application canvas: `#f3f6f8`
- Rounded cards, panels, tables, buttons, and status labels
- Side navigation for major modules
- A resizable sidebar in the dashboard workspaces
- Top bars showing the current user, date, and database status
- Tables and dialog forms for operational records

The visual assets are centralized in [application.css](src/main/resources/ug/sjch/styles/application.css).

### 2.2 Common Dashboard Structure

All role dashboards follow this layout:

```text
+------------------------------------------------------------------+
| SJCH / department brand |  Page title | date | status | refresh |
+--------------------------+---------------------------------------+
|                          |                                       |
|  Resizable sidebar       |  Metric cards                         |
|  - Overview              |  Tables / charts / forms              |
|  - Department actions    |  Action buttons and feedback          |
|  - Active session        |                                       |
|  - Sign out              |                                       |
+--------------------------+---------------------------------------+
```

The initial application window is 1180 x 720 pixels, with a minimum size of 900 x 650 pixels.

## 3. Implemented Screens

### 3.1 Login Screen

**Source:** [login.fxml](src/main/resources/ug/sjch/views/login.fxml)

UI design:

- Split layout with a dark branded hospital panel
- Hospital name and “Hospital Management System” subtitle
- Welcome title and sign-in instructions
- Username field
- Password field with Show/Hide control
- Remember username option
- Sign-in button
- Inline error messages
- “Authorized personnel only” footer

Implemented behavior:

- Username and password validation
- PBKDF2 password verification
- Active-account checking
- Remembered username using Java preferences
- Role-specific dashboard navigation
- Database connection error handling

### 3.2 Generic Role Dashboard

**Source:** [role-dashboard.fxml](src/main/resources/ug/sjch/views/role-dashboard.fxml)

This fallback workspace is used for roles that do not have a dedicated dashboard implementation. It provides user identity, overview metrics, admission-status charting, staff distribution charting, recent-patient lists, recent-staff lists, and sign-out.

### 3.3 Administrator Dashboard

**Source:** [admin-dashboard.fxml](src/main/resources/ug/sjch/views/admin-dashboard.fxml)

Main navigation:

- Overview
- Patients
- Appointments
- Staff
- User Accounts
- Departments
- Reports
- Audit Logs

Implemented modules:

- Hospital overview metrics and department performance
- Patient search and detail editing
- Patient archiving
- Appointment search, reassignment, editing, and cancellation
- Staff creation and status/department management
- User-account creation and editing
- Role and staff-account linking
- Department viewing and editing
- Audit-log viewing
- Workflow-event monitoring
- Reports for operational review

### 3.4 Reception Dashboard

**Source:** [reception-dashboard.fxml](src/main/resources/ug/sjch/views/reception-dashboard.fxml)

Main navigation:

- Dashboard
- Register Patient
- Patient Directory
- Book Appointment
- Appointment Schedule
- Patient Check-in
- Patient Search
- Notifications

Implemented modules:

- Reception metrics
- Patient registration
- Patient contact and detail searching
- Appointment booking
- Appointment schedule filtering
- Appointment editing
- Patient check-in
- Today’s workflow counts
- Recent patients and upcoming appointments
- Cross-department workflow notifications

### 3.5 Outpatient / Doctor Dashboard

**Source:** [outpatient-dashboard.fxml](src/main/resources/ug/sjch/views/outpatient-dashboard.fxml)

Main navigation:

- Dashboard
- Patient Queue
- Patient Records
- Start Consultation
- Diagnosis
- Complete Consultation
- Prescriptions
- Laboratory Requests
- Consultation History
- Follow-up Management

Implemented modules:

- Waiting-queue metrics
- Consultation records
- Patient medical history
- Consultation start and completion
- Diagnosis capture
- Prescription creation and item editing
- Prescription cancellation
- Laboratory investigation requests
- Laboratory request editing and cancellation
- Follow-up scheduling and cancellation
- Pending laboratory-result tracking

### 3.6 Nursing Dashboard

**Source:** [nursing-dashboard.fxml](src/main/resources/ug/sjch/views/nursing-dashboard.fxml)

Main navigation:

- Dashboard
- Assigned Patients
- Patient Check-up
- Record Vital Signs
- Nursing Notes
- Medication Administration
- Patient Monitoring
- Ward Reports

Implemented modules:

- Admitted-patient list
- Vital-sign recording
- Temperature, blood pressure, pulse, and respiratory observations
- Nursing observations and notes
- Medication administration recording
- Medication and admission lists
- Patient-monitoring views
- Ward reporting
- Nursing-to-inpatient dashboard navigation

### 3.7 Inpatient / Ward Dashboard

**Source:** [inpatient-dashboard.fxml](src/main/resources/ug/sjch/views/inpatient-dashboard.fxml)

Main navigation:

- Dashboard
- Ward Management
- Bed Management
- Patient Admissions
- Bed Allocation
- Transfers
- Discharge
- Records
- Reports

Implemented modules:

- Ward metrics and occupancy
- Ward creation, editing, and archiving
- Bed creation, editing, status updates, and deletion
- Available-bed listing
- Patient admission
- Initial bed allocation
- Transfer to another bed
- Discharge-request creation
- Discharge completion
- Admission, transfer, and discharge records
- Ward reports
- Navigation to the nursing workspace

### 3.8 Laboratory Dashboard

**Source:** [laboratory-dashboard.fxml](src/main/resources/ug/sjch/views/laboratory-dashboard.fxml)

Main navigation:

- Dashboard
- Test Requests
- Sample Collection
- Tests in Progress
- Enter Results
- Verify Results
- History
- Print Results
- Reports

Implemented workflow:

- Request filtering and searching
- Pending, collected, in-progress, completed, and cancelled states
- Sample collection
- Test processing
- Test-result entry
- Result correction
- Result viewing
- Result printing
- Laboratory status reports
- Workflow and audit-event recording
- Technician permission enforcement

### 3.9 Pharmacy Dashboard

**Source:** [pharmacy-dashboard.fxml](src/main/resources/ug/sjch/views/pharmacy-dashboard.fxml)

Main navigation:

- Dashboard
- Medicine Inventory
- Add Medicine
- Stock Management
- Suppliers
- Prescriptions
- Dispensing
- Stock Reports
- Expiry Alerts

Implemented modules:

- Medicine catalog
- Medicine categories and stock summaries
- Inventory search and filters
- Low-stock and expiry views
- Medicine creation and editing
- Medicine archiving
- Batch metadata
- Supplier creation and editing
- Supplier archiving
- Stock receipt
- Stock adjustment
- Pending prescriptions
- Prescription dispensing
- Inventory transaction history
- Stock reporting
- Workflow and audit-event recording

### 3.10 Finance Dashboard

**Source:** [finance-dashboard.fxml](src/main/resources/ug/sjch/views/finance-dashboard.fxml)

Main navigation:

- Dashboard
- Patient Billing
- Record Payments
- Payment History
- Print Receipts
- Reports

Implemented modules:

- Revenue and finance overview
- Invoice search
- Outstanding-invoice view
- Draft invoice item editing
- Invoice updates
- Invoice voiding
- Payment recording
- Payment reversal
- Payment history
- Payment-method summaries
- Monthly revenue view
- Receipt and finance reporting
- Optional database-module handling when finance tables are unavailable

## 4. Department Operating Guide

This section explains what each department currently sees, what information it handles, and how the information moves between departments.

### 4.1 Administration Department

**Purpose:** Central hospital configuration, staff governance, account security, and operational oversight.

**Current UI design**

- Left sidebar with Administration navigation.
- A resizable blue sidebar with the Administrator user and Sign out action.
- A white workspace with a top refresh button, connection status, and date.
- Dynamic pages are loaded into the main scrollable content area.
- Overview pages use metric cards and summary tables.
- Patient, staff, appointment, and account pages use search fields, tables, and edit dialogs.
- Department and audit pages use record tables and status/feedback labels.

**Information handled**

| Page | Data handled | Available actions |
|---|---|---|
| Overview | Total patients, appointments, staff, departments, and department performance | Review hospital status and refresh information |
| Patients | Patient number, name, date of birth, sex, phone, address, and activity state | Search, edit details, and archive patients |
| Appointments | Appointment date, patient, doctor, department, and status | Search, edit, reassign, and cancel |
| Staff | Staff number, full name, job title, department, email, and active status | Create staff, edit details, change status, and link staff to accounts |
| User Accounts | Username, linked staff, role, email, and active state | Create, edit, activate/deactivate, and change account details |
| Departments | Department name and active state | Search, edit, and activate/deactivate |
| Reports | Recent operational data and department summaries | Review department information |
| Audit Logs | Acting user, action, details, and timestamp | Review administrative changes |

**Database and handoff**

Main tables: `roles`, `users`, `staff`, `departments`, `patients`, `appointments`, and `audit_logs`.

The administration department manages the master identity and configuration data used by every other department. It does not replace the clinical or financial workflows; it controls who can use them and maintains the shared reference data.

### 4.2 Reception Department

**Purpose:** Patient entry, appointment scheduling, arrival processing, and the first step of the outpatient workflow.

**Current UI design**

- A blue resizable sidebar with reception-specific navigation.
- A top search field for patients and appointments.
- A refresh control, notification indicator, receptionist profile, and status badge.
- A metric grid for reception totals.
- A four-stage appointment-flow panel showing Queue, Waiting, Engaged, and Completed progress.
- Separate tables for recent patients and upcoming appointments.
- Dedicated pages for registration, directory, scheduling, check-in, search, and notifications.

**Information handled**

| Page | Data handled | Available actions |
|---|---|---|
| Dashboard | Reception totals, today's appointment flow, recent patients, and upcoming visits | Monitor queue progress and refresh data |
| Register New Patient | Patient number, full name, date of birth, sex, phone, and address | Create and validate a patient record |
| Patient Directory | Registered patient details and registration date | Find and inspect patients |
| Patient Search | Search text, name, number, phone, and matching records | Search and open contact-editing options |
| Book Appointment | Patient, doctor, department, date/time, and appointment status | Create an appointment |
| Appointment Schedule | Date range, patient, clinician, department, and status | Filter and edit appointments |
| Patient Check-in | Scheduled appointments, patient identity, arrival/check-in state | Mark a patient as checked in |
| Notifications | Workflow events, patient, department, event type, actor, and timestamp | Review cross-department activity |

**Database and handoff**

Main tables: `patients`, `appointments`, `departments`, `staff`, and `workflow_events`.

Reception creates the patient and appointment records used by outpatient care. Check-in changes the appointment state so the doctor can see the patient in the clinical queue. Reception also provides workflow notifications to the rest of the hospital.

### 4.3 Outpatient / Doctor Department

**Purpose:** Clinical assessment and treatment of registered outpatient patients.

**Current UI design**

- A clinical blue sidebar with patient and consultation actions.
- A top search field for today's clinical lists.
- Metric cards for queue, today's consultations, follow-ups, and laboratory requests.
- A two-column dashboard containing waiting patients and today's consultations.
- A laboratory-request panel and follow-up or record tables.
- Dialogs and result panels for starting consultations, diagnosis, prescriptions, laboratory requests, and follow-ups.

**Information handled**

| Page | Data handled | Available actions |
|---|---|---|
| Patient Queue | Patient number, patient name, arrival/visit time, and check-in status | Select a patient and start consultation |
| Start Consultation | Appointment, doctor, patient, encounter time, and status | Open a clinical encounter |
| Diagnosis | Consultation ID, diagnosis, clinical findings, and notes | Save or update diagnosis |
| Complete Consultation | Diagnosis/completion state and encounter status | Finish the outpatient encounter |
| Patient Medical Records | Medical history and previous consultation information | Review patient records |
| Prescriptions | Medicine, dosage, instructions, quantity, and prescription status | Create, edit items, or cancel a prescription |
| Laboratory Requests | Test, request reason, status, requested time, and result availability | Create, edit, or cancel a test request |
| Consultation History | Previous consultations and completion details | Review the patient's care history |
| Follow-up Management | Follow-up date, clinician, appointment state, and patient | Schedule or cancel a follow-up |

**Database and handoff**

Main tables: `appointments`, `consultations`, `lab_requests`, `prescriptions`, `prescription_items`, and `workflow_events`.

The doctor sends prescriptions to the pharmacy and laboratory requests to the laboratory. Consultation, diagnosis, and follow-up records remain available for later clinical review and administration reporting.

### 4.4 Nursing Department

**Purpose:** Care monitoring, observations, nursing notes, and medication administration for admitted patients.

**Current UI design**

- A blue Nursing Care Command Center layout.
- A resizable sidebar with admitted-patient and care actions.
- Metric cards for assigned patients, attention, and recorded vital signs.
- Home panels for admitted patients and patients requiring attention.
- Separate pages for patient check-up, vital signs, notes, medication, monitoring, and ward reports.
- Empty-state panels clearly indicate when assignment or attention data is not configured.
- A link to the inpatient/bed-management dashboard is shown for appropriate users.

**Information handled**

| Page | Data handled | Available actions |
|---|---|---|
| Admitted Patients | Admission ID, patient, bed/ward, admission state, and patient details | Select admitted patients for care |
| Patient Check-up | Admission, assessment state, observations, and recorded time | Record a check-up or observation |
| Vital Signs | Temperature, systolic/diastolic blood pressure, pulse, respiratory rate, and SpO2 | Validate and record vital signs |
| Nursing Notes | Observation text, nurse/actor, admission, and timestamp | Save and review nursing notes |
| Medication Administration | Medicine, dose, administration time, admission, nurse/actor, and status | Record medication given |
| Patient Monitoring | Admitted patient observations and trend/status information | Monitor current care state |
| Ward Reports | Admission and nursing activity summaries | Review ward-level activity |

**Database and handoff**

Main tables: `admissions`, `nursing_observations`, `nursing_medication_administrations`, and `workflow_events`.

Nursing consumes admitted-patient and bed records created by the ward department and returns care observations and medication records for the patient record. The nursing view is not a separate patient-registration system.

### 4.5 Inpatient / Ward Department

**Purpose:** Ward and bed resources, admissions, bed allocation, patient transfers, and discharge.

**Current UI design**

- A top search area for wards/beds, with new-admission, allocate-bed, and refresh actions.
- A four-card capacity overview.
- A ward-availability table showing bed, patient, status, admitted time, and ward.
- An occupancy overview panel.
- Pages for ward management, bed management, admissions, allocation, transfers, discharge, records, and reports.
- Ward and bed actions use forms and dialogs to reduce errors.

**Information handled**

| Page | Data handled | Available actions |
|---|---|---|
| Ward Management | Ward name, ward type, active state | Create, edit, or archive wards |
| Bed Management | Bed number, ward, status, active/referenced state | Create, edit, update status, or remove unused beds |
| Patient Admissions | Patient number, selected bed, admission time, and admission state | Admit a patient and allocate an initial bed |
| Bed Allocation | Admission and available beds | Allocate or reassign a bed |
| Patient Transfers | Admission, source bed/ward, target bed/ward, reason, and transfer time | Transfer a patient |
| Discharge | Discharge request, notes, state, and completion/acting user | Request and complete discharge |
| Inpatient Records | Admissions, transfers, and discharge history | Review inpatient movements |
| Ward Reports | Occupancy, ward, bed, admission, transfer, and discharge summaries | Review ward activity |

**Database and handoff**

Main tables: `wards`, `beds`, `admissions`, `patient_transfers`, `discharge_requests`, and `workflow_events`.

This department creates the admitted-patient records used by nursing. It is responsible for physical bed capacity and patient movement while the doctor remains responsible for clinical care.

### 4.6 Laboratory Department

**Purpose:** Process laboratory requests from sample collection through final result status.

**Current UI design**

- A laboratory search field and quick filters for pending requests and awaiting review.
- Metric cards for pending requests, collected samples, tests in progress, completed tests, and awaiting verification.
- A resizable blue sidebar for all laboratory stages.
- Request pages use tables with patient, test, order details, and status.
- Dialogs support result entry and correction.
- Dedicated actions exist for viewing and printing result information.

**Information handled**

| Page | Data handled | Available actions |
|---|---|---|
| Test Requests | Patient, test, requesting doctor, reason, status, and requested time | Review and search requests |
| Sample Collection | Request, patient/test, sample state, collection time | Collect a sample and update status |
| Tests in Progress | Current request, processing state, and technician | Mark a test as in progress |
| Enter Results | Result text, result time, technician/acting user | Enter or correct result data |
| Verify Results | Result content, verification state, and request | Review and verify completed results |
| Laboratory History | Completed/corrected/cancelled requests | Review past test activity |
| Print Results | Patient, test, result, and status | Display or print result information |
| Laboratory Reports | Pending, collected, in-progress, completed, and cancelled totals | Review laboratory workload |

**Database and handoff**

Main tables: `lab_requests`, `lab_results`, `consultations`, `patients`, and `workflow_events`.

Laboratory results return to the outpatient consultation workflow. The laboratory changes only the status and result of a linked request; it does not create a separate treatment record.

### 4.7 Pharmacy Department

**Purpose:** Medicine inventory, stock control, supplier management, prescription fulfilment, and expiry/stock alerts.

**Current UI design**

- Top search for medicines plus All, Low Stock, and Expiring filters.
- Metric cards for total medicines, low/out-of-stock medicines, expiry risk, pending prescriptions, and today's dispensing.
- Inventory-by-category cards.
- Stock and expiry warning banners.
- A medicine inventory table for products, stock, batches, expiry, and status.
- Dialogs for medicines, suppliers, batches, stock receipt, adjustments, and dispensing.

**Information handled**

| Page | Data handled | Available actions |
|---|---|---|
| Medicine Inventory | Medicine name/generic name, category, strength, reorder level, stock, status | Search, edit, and archive medicines |
| Add Medicine | Name, generic name, category, strength, reorder level, supplier-related metadata | Create medicine |
| Stock Management | Medicine batch, supplier, quantity, expiry, received/adjusted quantity | Receive or adjust stock |
| Suppliers | Supplier name, contact name, phone, email/address, active state | Create, edit, or archive suppliers |
| Batches | Batch number, expiry, supplier, opening/current quantity | Edit batch metadata and track availability |
| Prescriptions | Patient, doctor, prescription items, quantity, status | Review prescriptions awaiting dispensing |
| Dispensing | Prescription, medicine item, dispensed quantity, batch, pharmacist/actor | Dispense and update inventory |
| Stock Reports | Transactions, receipt/adjustment/dispensing activity, balances | Review stock movement |
| Expiry Alerts | Batch expiry date and quantity; medicines expiring within the current reporting period | Identify medicines requiring action |

**Database and handoff**

Main tables: `suppliers`, `medicines`, `medicine_batches`, `prescriptions`, `prescription_items`, `dispensings`, `dispensing_items`, `inventory_transactions`, and `workflow_events`.

The pharmacy receives prescriptions created by outpatient care, selects batches, decrements/adjusts inventory, and records each dispensing transaction for finance and audit use.

### 4.8 Finance Department

**Purpose:** Patient billing, payment collection, outstanding balances, receipts, reversals, and financial reporting.

**Current UI design**

- Top search for invoices/payments, month/filter/export actions, and a finance profile.
- Eight metric cards for daily/monthly revenue, total payments, outstanding, pending, paid, partially paid, and voided invoices.
- Revenue-trend and payment-method panels.
- Invoice and payment tables loaded by date, status, patient, and search criteria.
- Empty-state messages are shown until the finance database module is configured.

**Information handled**

| Page | Data handled | Available actions |
|---|---|---|
| Dashboard | Today's revenue, monthly revenue, payment totals, invoice states | Review financial position |
| Patient Billing | Patient, invoice number, status, item, quantity, unit price, total, and created date | Search and edit draft invoice items |
| Record Payments | Invoice, amount, method, payment reference, payer, and date | Record a payment |
| Payment History | Payment ID/reference, invoice, amount, method, status, and timestamp | Search and review payments |
| Outstanding Bills | Outstanding invoice, patient, total, balance, and status | Identify invoices requiring collection |
| Print Receipts | Patient, payment, invoice, amount, method, and receipt details | Display or print receipt information |
| Revenue Reports | Daily/monthly revenue, paid/partially paid/voided totals, and payment methods | Review financial trends |

**Database and handoff**

Main tables: `invoices`, `invoice_items`, `payments`, and `workflow_events`.

Finance consumes patient and service records created by reception and clinical/pharmacy workflows. It should reflect the cost of services and dispensed items while maintaining invoice and payment status. Some module actions remain disabled in the UI until the configured finance tables are available.

## 5. Cross-Department Data Flow

The main end-to-end information flow is:

```text
Reception
  creates patient identity and appointment
         |
         v
Outpatient / Doctor
  starts consultation, records diagnosis
  creates laboratory request and/or prescription
         |                         |
         v                         v
Laboratory                  Pharmacy
  result record              stock and dispensing record
         |                         |
         +-----------+-------------+
                     v
                  Finance
            invoice and payment record

Nursing / Ward can also admit the patient:
  Ward creates admission, ward, and bed records
  -> Nursing records observations and medication
  -> Ward manages transfer and discharge
```

The database uses `workflow_events` and audit records to identify the patient, the entity changed, the department, the acting user, event type, and time.

## 6. Authentication and Security

### 6.1 Authentication

Authentication is handled through [AuthenticationService.java](src/main/java/ug/sjch/service/AuthenticationService.java) and [UserDao.java](src/main/java/ug/sjch/dao/UserDao.java).

Checks include:

- Username and password presence
- Active user accounts
- Password hash verification
- Role lookup
- Staff-account linkage
- Role-specific dashboard selection

### 6.2 Password Storage

[PasswordHasher.java](src/main/java/ug/sjch/security/PasswordHasher.java) uses:

- PBKDF2 with HMAC-SHA256
- 210,000 iterations
- 256-bit derived keys
- 16-byte cryptographically secure salts
- Encoded salt and digest in the database

### 6.3 Authorization

The UI disables actions until an appropriate session is present. DAO methods also perform permission checks, which prevents authorization from depending only on button visibility.

The database has `PERMISSIONS` and `ROLE_PERMISSIONS` tables for permission mappings. Workflow operations also record the acting user.

## 7. Database Design

The project uses 32 SQL-defined tables across the two main schema scripts:

- 8 tables in [milestone3-schema.sql](db/milestone3-schema.sql)
- 24 tables in [cross-department-workflow.sql](db/cross-department-workflow.sql)

Core groups:

### Identity and Administration

- `roles`
- `users`
- `staff`
- `departments`
- `audit_logs`
- `permissions`
- `role_permissions`

### Patient and Appointments

- `patients`
- `appointments`
- `admissions`

### Clinical Workflow

- `consultations`
- `lab_requests`
- `lab_results`
- `prescriptions`
- `prescription_items`
- `nursing_observations`
- `nursing_medication_administrations`

### Inpatient Workflow

- `wards`
- `beds`
- `admissions`
- `patient_transfers`
- `discharge_requests`

### Pharmacy

- `suppliers`
- `medicines`
- `medicine_batches`
- `prescriptions`
- `dispensings`
- `dispensing_items`
- `inventory_transactions`

### Finance

- `invoices`
- `invoice_items`
- `payments`

### Workflow Traceability

- `workflow_events`

The workflow script also creates indexes on frequently queried status, patient, date, invoice, batch, and workflow columns.

## 8. Cross-Department Workflow

The implemented workflow is traceable across roles:

```text
Reception
  -> registers patients
  -> schedules and checks in appointments
  -> opens the clinical workflow

Outpatient / Doctor
  -> starts consultations
  -> records diagnosis
  -> creates prescriptions
  -> creates laboratory requests

Laboratory
  -> collects samples
  -> processes tests
  -> enters or corrects results

Nursing / Ward
  -> records observations and medications
  -> admits and transfers patients
  -> requests and completes discharge

Pharmacy
  -> receives and manages stock
  -> dispenses prescriptions
  -> records inventory transactions

Finance
  -> prepares invoices
  -> records and reverses payments
  -> provides revenue reporting

Administration
  -> manages users, staff, departments, and records
  -> reviews audit and workflow events
```

Workflow operations are recorded in audit logs and/or `workflow_events`, where applicable.

## 9. Project Structure

```text
src/main/java/ug/sjch/
  MainApp.java
  config/
  controller/
  dao/
  model/
  security/
  service/

src/main/resources/ug/sjch/
  styles/application.css
  views/*.fxml

src/test/java/ug/sjch/
  controller/
  dao/
  security/

db/
  milestone3-schema.sql
  cross-department-workflow.sql
  hospital-demo-seed.sql
  pharmacy-demo-seed.sql
```

## 10. Test and Build Verification

A Maven test run was executed on 23 November 2026.

Result:

- Tests run: 35
- Failures: 1
- Errors: 0
- Skipped: 0
- Build result: Failed

The failing test is [HospitalDashboardViewTest.java](src/test/java/ug/sjch/controller/HospitalDashboardViewTest.java), which reported that a JavaFX department view did not finish loading within the test condition.

Other test classes completed without failures, including:

- Login and role routing
- Password hashing
- Database connection
- Administration DAO
- Finance DAO
- Outpatient DAO
- Laboratory DAO
- Nursing workflow validation
- Inpatient workflow validation
- Pharmacy DAO
- Reception DAO

The remaining work should determine whether the JavaFX view failure is timing-related or indicates an initialization issue in the laboratory or department view. The database-backed test logs also show a laboratory authorization warning when no laboratory session is active, which is consistent with the implemented permission design.

## 11. Current Implementation Status

| Area | Status |
|---|---|
| JavaFX application shell | Implemented |
| Login and role routing | Implemented |
| Password security | Implemented |
| Session and role handling | Implemented |
| Administrator dashboard | Implemented |
| Reception dashboard | Implemented |
| Outpatient/doctor dashboard | Implemented |
| Nursing dashboard | Implemented |
| Ward/inpatient dashboard | Implemented |
| Laboratory dashboard | Implemented |
| Pharmacy dashboard | Implemented |
| Finance dashboard | Implemented |
| Oracle schema and demo scripts | Implemented |
| Role and permission tables | Implemented |
| Workflow/audit event recording | Implemented |
| Automated unit/DAO tests | Implemented, with one JavaFX view failure |

## 12. Notable Limitations

- Some optional modules depend on Oracle tables being loaded by the full SQL scripts.
- Several dashboards display explicit unavailable/configuration states if a database module is missing.
- Generic role navigation is present for roles without dedicated dashboards.
- Some dashboard views are large and may require further UX refinement on smaller screens.
- The JavaFX loading test needs investigation before a fully green build can be reported.

## 13. Conclusion

The project is a substantial role-based hospital information system rather than a basic UI prototype. It contains implemented desktop screens, database-backed workflows, authentication, authorization, audit trails, cross-department records, and operational transactions across administration, reception, clinical care, nursing, wards, laboratory, pharmacy, and finance.
