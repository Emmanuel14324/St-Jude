-- One-time additive Oracle migration for the shared patient-care workflow.
-- Run after the core ROLES, USERS, STAFF, PATIENTS, and APPOINTMENTS tables
-- exist. This script is not executed by the application.
--
-- Workflow: appointment -> consultation -> lab request/result review ->
-- prescription -> dispensing -> service invoice -> one or more payments.
-- Every clinical and financial record remains linked to the central PATIENTS
-- table. WORKFLOW_EVENTS supports an administration-wide patient timeline.
--
-- Invoices are per service. Payments may be partial and repeated. A lab result
-- is entered by laboratory staff and reviewed by the requesting clinician.

CREATE TABLE WARDS (
    ward_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    department_id NUMBER,
    ward_name VARCHAR2(100) NOT NULL,
    ward_type VARCHAR2(40),
    is_active CHAR(1) DEFAULT 'Y' NOT NULL
        CONSTRAINT ck_wards_active CHECK (is_active IN ('Y', 'N')),
    CONSTRAINT uq_wards_name UNIQUE (ward_name),
    CONSTRAINT fk_wards_department FOREIGN KEY (department_id)
        REFERENCES DEPARTMENTS(department_id)
);

CREATE TABLE BEDS (
    bed_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ward_id NUMBER NOT NULL,
    bed_number VARCHAR2(30) NOT NULL,
    bed_status VARCHAR2(24) DEFAULT 'AVAILABLE' NOT NULL,
    CONSTRAINT uq_beds_ward_number UNIQUE (ward_id, bed_number),
    CONSTRAINT ck_beds_status CHECK (
        bed_status IN ('AVAILABLE', 'OCCUPIED', 'CLEANING', 'OUT_OF_SERVICE')
    ),
    CONSTRAINT fk_beds_ward FOREIGN KEY (ward_id)
        REFERENCES WARDS(ward_id)
);

-- ADMISSIONS exists in the starter schema but may not yet exist in the live
-- database. Create it when absent; otherwise add only the bed relationship.
DECLARE
    table_count NUMBER;
    column_count NUMBER;
    constraint_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO table_count
    FROM USER_TABLES
    WHERE TABLE_NAME = 'ADMISSIONS';

    IF table_count = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE ADMISSIONS (
                admission_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                patient_id NUMBER NOT NULL,
                bed_id NUMBER,
                admitted_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
                discharge_date TIMESTAMP,
                status VARCHAR2(30) DEFAULT 'ADMITTED' NOT NULL,
                CONSTRAINT fk_admissions_patient FOREIGN KEY (patient_id)
                    REFERENCES PATIENTS(patient_id),
                CONSTRAINT fk_admissions_bed FOREIGN KEY (bed_id)
                    REFERENCES BEDS(bed_id)
            )
        ]';
    ELSE
        SELECT COUNT(*) INTO column_count
        FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'ADMISSIONS'
          AND COLUMN_NAME = 'BED_ID';

        IF column_count = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE ADMISSIONS ADD (BED_ID NUMBER)';
        END IF;

        SELECT COUNT(*) INTO constraint_count
        FROM USER_CONSTRAINTS
        WHERE CONSTRAINT_NAME = 'FK_ADMISSIONS_BED';

        IF constraint_count = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE ADMISSIONS ADD CONSTRAINT fk_admissions_bed '
                || 'FOREIGN KEY (bed_id) REFERENCES BEDS(bed_id)';
        END IF;
    END IF;
END;
/

-- Reception check-in and outpatient queue states must be accepted by the
-- appointment status constraint already present in the core schema.
DECLARE
    constraint_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO constraint_count
    FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'APPOINTMENTS'
      AND CONSTRAINT_NAME = 'CK_APPT_STATUS';

    IF constraint_count > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE APPOINTMENTS DROP CONSTRAINT CK_APPT_STATUS';
    END IF;

    EXECUTE IMMEDIATE q'[
        ALTER TABLE APPOINTMENTS
        ADD CONSTRAINT CK_APPT_STATUS CHECK (
            STATUS IN (
                'SCHEDULED', 'CHECKED_IN', 'WAITING', 'IN_PROGRESS',
                'COMPLETED', 'CANCELLED', 'MISSED'
            )
        )
    ]';
END;
/

CREATE TABLE PATIENT_TRANSFERS (
    transfer_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admission_id NUMBER NOT NULL,
    from_bed_id NUMBER,
    to_bed_id NUMBER NOT NULL,
    requested_by_user_id NUMBER NOT NULL,
    completed_by_user_id NUMBER,
    transfer_status VARCHAR2(20) DEFAULT 'REQUESTED' NOT NULL,
    reason VARCHAR2(1000),
    requested_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT ck_patient_transfers_status CHECK (
        transfer_status IN ('REQUESTED', 'APPROVED', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT fk_transfer_admission FOREIGN KEY (admission_id)
        REFERENCES ADMISSIONS(admission_id),
    CONSTRAINT fk_transfer_from_bed FOREIGN KEY (from_bed_id)
        REFERENCES BEDS(bed_id),
    CONSTRAINT fk_transfer_to_bed FOREIGN KEY (to_bed_id)
        REFERENCES BEDS(bed_id),
    CONSTRAINT fk_transfer_requested_by FOREIGN KEY (requested_by_user_id)
        REFERENCES USERS(user_id),
    CONSTRAINT fk_transfer_completed_by FOREIGN KEY (completed_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE TABLE DISCHARGE_REQUESTS (
    discharge_request_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admission_id NUMBER NOT NULL,
    requested_by_user_id NUMBER NOT NULL,
    processed_by_user_id NUMBER,
    request_status VARCHAR2(20) DEFAULT 'REQUESTED' NOT NULL,
    requested_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    notes CLOB,
    CONSTRAINT ck_discharge_request_status CHECK (
        request_status IN ('REQUESTED', 'APPROVED', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT fk_discharge_admission FOREIGN KEY (admission_id)
        REFERENCES ADMISSIONS(admission_id),
    CONSTRAINT fk_discharge_requested_by FOREIGN KEY (requested_by_user_id)
        REFERENCES USERS(user_id),
    CONSTRAINT fk_discharge_processed_by FOREIGN KEY (processed_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE TABLE NURSING_OBSERVATIONS (
    observation_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admission_id NUMBER NOT NULL,
    patient_id NUMBER NOT NULL,
    recorded_by_user_id NUMBER NOT NULL,
    recorded_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    temperature_c NUMBER(5, 2),
    pulse_rate NUMBER(5, 2),
    systolic_bp NUMBER(5, 2),
    diastolic_bp NUMBER(5, 2),
    respiratory_rate NUMBER(5, 2),
    oxygen_saturation NUMBER(5, 2),
    notes VARCHAR2(2000),
    CONSTRAINT ck_nursing_temperature CHECK (
        temperature_c IS NULL OR temperature_c BETWEEN 25 AND 45
    ),
    CONSTRAINT ck_nursing_pulse CHECK (
        pulse_rate IS NULL OR pulse_rate BETWEEN 20 AND 250
    ),
    CONSTRAINT ck_nursing_blood_pressure CHECK (
        (systolic_bp IS NULL AND diastolic_bp IS NULL)
        OR (systolic_bp IS NOT NULL AND diastolic_bp IS NOT NULL
            AND systolic_bp BETWEEN 40 AND 300
            AND diastolic_bp BETWEEN 20 AND 200
            AND systolic_bp > diastolic_bp)
    ),
    CONSTRAINT ck_nursing_respiration CHECK (
        respiratory_rate IS NULL OR respiratory_rate BETWEEN 4 AND 80
    ),
    CONSTRAINT ck_nursing_oxygen CHECK (
        oxygen_saturation IS NULL OR oxygen_saturation BETWEEN 50 AND 100
    ),
    CONSTRAINT ck_nursing_observation_content CHECK (
        temperature_c IS NOT NULL OR pulse_rate IS NOT NULL
        OR systolic_bp IS NOT NULL OR diastolic_bp IS NOT NULL
        OR respiratory_rate IS NOT NULL OR oxygen_saturation IS NOT NULL
        OR notes IS NOT NULL
    ),
    CONSTRAINT fk_nursing_observation_admission FOREIGN KEY (admission_id)
        REFERENCES ADMISSIONS(admission_id),
    CONSTRAINT fk_nursing_observation_patient FOREIGN KEY (patient_id)
        REFERENCES PATIENTS(patient_id),
    CONSTRAINT fk_nursing_observation_user FOREIGN KEY (recorded_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE TABLE CONSULTATIONS (
    consultation_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    appointment_id NUMBER NOT NULL,
    patient_id NUMBER NOT NULL,
    doctor_id NUMBER NOT NULL,
    consultation_date TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    diagnosis CLOB,
    clinical_notes CLOB,
    consultation_status VARCHAR2(20) DEFAULT 'IN_PROGRESS' NOT NULL,
    created_by_user_id NUMBER NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT uq_consultation_appointment UNIQUE (appointment_id),
    CONSTRAINT ck_consultation_status CHECK (
        consultation_status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT fk_consultation_appointment FOREIGN KEY (appointment_id)
        REFERENCES APPOINTMENTS(appointment_id),
    CONSTRAINT fk_consultation_patient FOREIGN KEY (patient_id)
        REFERENCES PATIENTS(patient_id),
    CONSTRAINT fk_consultation_doctor FOREIGN KEY (doctor_id)
        REFERENCES STAFF(staff_id),
    CONSTRAINT fk_consultation_created_by FOREIGN KEY (created_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE TABLE LAB_REQUESTS (
    lab_request_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultation_id NUMBER NOT NULL,
    patient_id NUMBER NOT NULL,
    requested_by_staff_id NUMBER NOT NULL,
    test_name VARCHAR2(160) NOT NULL,
    instructions CLOB,
    request_status VARCHAR2(24) DEFAULT 'REQUESTED' NOT NULL,
    requested_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    sample_collected_at TIMESTAMP,
    processing_started_at TIMESTAMP,
    result_entered_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    CONSTRAINT ck_lab_request_status CHECK (
        request_status IN (
            'REQUESTED', 'SAMPLE_COLLECTED', 'IN_PROGRESS',
            'RESULT_ENTERED', 'REVIEWED', 'CANCELLED'
        )
    ),
    CONSTRAINT fk_lab_request_consultation FOREIGN KEY (consultation_id)
        REFERENCES CONSULTATIONS(consultation_id),
    CONSTRAINT fk_lab_request_patient FOREIGN KEY (patient_id)
        REFERENCES PATIENTS(patient_id),
    CONSTRAINT fk_lab_request_staff FOREIGN KEY (requested_by_staff_id)
        REFERENCES STAFF(staff_id)
);

CREATE TABLE LAB_RESULTS (
    lab_result_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_request_id NUMBER NOT NULL,
    result_text CLOB NOT NULL,
    entered_by_staff_id NUMBER NOT NULL,
    entered_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    reviewed_by_staff_id NUMBER,
    reviewed_at TIMESTAMP,
    CONSTRAINT uq_lab_result_request UNIQUE (lab_request_id),
    CONSTRAINT ck_lab_result_review_pair CHECK (
        (reviewed_by_staff_id IS NULL AND reviewed_at IS NULL)
        OR (reviewed_by_staff_id IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT fk_lab_result_request FOREIGN KEY (lab_request_id)
        REFERENCES LAB_REQUESTS(lab_request_id),
    CONSTRAINT fk_lab_result_entered_by FOREIGN KEY (entered_by_staff_id)
        REFERENCES STAFF(staff_id),
    CONSTRAINT fk_lab_result_reviewed_by FOREIGN KEY (reviewed_by_staff_id)
        REFERENCES STAFF(staff_id)
);

CREATE TABLE SUPPLIERS (
    supplier_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_name VARCHAR2(160) NOT NULL,
    contact_name VARCHAR2(120),
    phone VARCHAR2(40),
    email VARCHAR2(200),
    address VARCHAR2(500),
    is_active CHAR(1) DEFAULT 'Y' NOT NULL
        CONSTRAINT ck_suppliers_active CHECK (is_active IN ('Y', 'N')),
    CONSTRAINT uq_suppliers_name UNIQUE (supplier_name)
);

CREATE TABLE MEDICINES (
    medicine_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    medicine_name VARCHAR2(160) NOT NULL,
    generic_name VARCHAR2(160),
    strength VARCHAR2(80),
    dosage_form VARCHAR2(80),
    category VARCHAR2(100),
    reorder_level NUMBER(10, 2) DEFAULT 0 NOT NULL,
    is_active CHAR(1) DEFAULT 'Y' NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_medicines_reorder CHECK (reorder_level >= 0),
    CONSTRAINT ck_medicines_active CHECK (is_active IN ('Y', 'N'))
);

CREATE TABLE MEDICINE_BATCHES (
    batch_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    medicine_id NUMBER NOT NULL,
    supplier_id NUMBER,
    batch_number VARCHAR2(80) NOT NULL,
    quantity_received NUMBER(10, 2) NOT NULL,
    unit_cost NUMBER(12, 2) NOT NULL,
    selling_price NUMBER(12, 2) NOT NULL,
    received_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    expiry_date DATE,
    CONSTRAINT uq_medicine_batch UNIQUE (medicine_id, batch_number),
    CONSTRAINT ck_batch_quantity CHECK (quantity_received > 0),
    CONSTRAINT ck_batch_cost CHECK (unit_cost >= 0),
    CONSTRAINT ck_batch_price CHECK (selling_price >= 0),
    CONSTRAINT fk_batch_medicine FOREIGN KEY (medicine_id)
        REFERENCES MEDICINES(medicine_id),
    CONSTRAINT fk_batch_supplier FOREIGN KEY (supplier_id)
        REFERENCES SUPPLIERS(supplier_id)
);

CREATE TABLE PRESCRIPTIONS (
    prescription_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultation_id NUMBER NOT NULL,
    patient_id NUMBER NOT NULL,
    prescribed_by_staff_id NUMBER NOT NULL,
    prescription_status VARCHAR2(24) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    notes CLOB,
    CONSTRAINT ck_prescription_status CHECK (
        prescription_status IN (
            'PENDING', 'PARTIALLY_DISPENSED', 'DISPENSED', 'CANCELLED'
        )
    ),
    CONSTRAINT fk_prescription_consultation FOREIGN KEY (consultation_id)
        REFERENCES CONSULTATIONS(consultation_id),
    CONSTRAINT fk_prescription_patient FOREIGN KEY (patient_id)
        REFERENCES PATIENTS(patient_id),
    CONSTRAINT fk_prescription_staff FOREIGN KEY (prescribed_by_staff_id)
        REFERENCES STAFF(staff_id)
);

CREATE TABLE PRESCRIPTION_ITEMS (
    prescription_item_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prescription_id NUMBER NOT NULL,
    medicine_id NUMBER NOT NULL,
    dosage VARCHAR2(100),
    route VARCHAR2(60),
    frequency VARCHAR2(100),
    duration_days NUMBER(5),
    quantity_prescribed NUMBER(10, 2) NOT NULL,
    quantity_dispensed NUMBER(10, 2) DEFAULT 0 NOT NULL,
    instructions VARCHAR2(1000),
    CONSTRAINT ck_prescribed_quantity CHECK (quantity_prescribed > 0),
    CONSTRAINT ck_dispensed_quantity CHECK (
        quantity_dispensed >= 0
        AND quantity_dispensed <= quantity_prescribed
    ),
    CONSTRAINT fk_prescription_item_prescription FOREIGN KEY (prescription_id)
        REFERENCES PRESCRIPTIONS(prescription_id),
    CONSTRAINT fk_prescription_item_medicine FOREIGN KEY (medicine_id)
        REFERENCES MEDICINES(medicine_id)
);

CREATE TABLE NURSING_MEDICATION_ADMINISTRATIONS (
    administration_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admission_id NUMBER NOT NULL,
    patient_id NUMBER NOT NULL,
    prescription_item_id NUMBER NOT NULL,
    administered_by_user_id NUMBER NOT NULL,
    administered_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    dose_given VARCHAR2(100) NOT NULL,
    route_given VARCHAR2(60) NOT NULL,
    notes VARCHAR2(1000),
    CONSTRAINT fk_nursing_admin_admission FOREIGN KEY (admission_id)
        REFERENCES ADMISSIONS(admission_id),
    CONSTRAINT fk_nursing_admin_patient FOREIGN KEY (patient_id)
        REFERENCES PATIENTS(patient_id),
    CONSTRAINT fk_nursing_admin_prescription_item FOREIGN KEY (
        prescription_item_id
    ) REFERENCES PRESCRIPTION_ITEMS(prescription_item_id),
    CONSTRAINT fk_nursing_admin_user FOREIGN KEY (administered_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE TABLE DISPENSINGS (
    dispensing_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prescription_id NUMBER NOT NULL,
    dispensed_by_user_id NUMBER NOT NULL,
    dispensed_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    notes VARCHAR2(1000),
    CONSTRAINT fk_dispensing_prescription FOREIGN KEY (prescription_id)
        REFERENCES PRESCRIPTIONS(prescription_id),
    CONSTRAINT fk_dispensing_user FOREIGN KEY (dispensed_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE TABLE DISPENSING_ITEMS (
    dispensing_item_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispensing_id NUMBER NOT NULL,
    prescription_item_id NUMBER NOT NULL,
    batch_id NUMBER NOT NULL,
    quantity NUMBER(10, 2) NOT NULL,
    CONSTRAINT ck_dispensing_item_quantity CHECK (quantity > 0),
    CONSTRAINT fk_dispensing_item_header FOREIGN KEY (dispensing_id)
        REFERENCES DISPENSINGS(dispensing_id),
    CONSTRAINT fk_dispensing_item_prescription FOREIGN KEY (
        prescription_item_id
    ) REFERENCES PRESCRIPTION_ITEMS(prescription_item_id),
    CONSTRAINT fk_dispensing_item_batch FOREIGN KEY (batch_id)
        REFERENCES MEDICINE_BATCHES(batch_id)
);

CREATE TABLE INVENTORY_TRANSACTIONS (
    inventory_transaction_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id NUMBER NOT NULL,
    transaction_type VARCHAR2(20) NOT NULL,
    quantity_delta NUMBER(10, 2) NOT NULL,
    related_dispensing_item_id NUMBER,
    recorded_by_user_id NUMBER NOT NULL,
    recorded_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    notes VARCHAR2(1000),
    CONSTRAINT ck_inventory_transaction_type CHECK (
        transaction_type IN (
            'RECEIPT', 'DISPENSE', 'ADJUSTMENT', 'RETURN', 'EXPIRED'
        )
    ),
    CONSTRAINT ck_inventory_quantity_delta CHECK (quantity_delta <> 0),
    CONSTRAINT fk_inventory_batch FOREIGN KEY (batch_id)
        REFERENCES MEDICINE_BATCHES(batch_id),
    CONSTRAINT fk_inventory_dispense_item FOREIGN KEY (
        related_dispensing_item_id
    ) REFERENCES DISPENSING_ITEMS(dispensing_item_id),
    CONSTRAINT fk_inventory_user FOREIGN KEY (recorded_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE TABLE INVOICES (
    invoice_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_number VARCHAR2(40) NOT NULL,
    patient_id NUMBER NOT NULL,
    appointment_id NUMBER,
    service_type VARCHAR2(24) NOT NULL,
    service_reference_id NUMBER NOT NULL,
    invoice_status VARCHAR2(20) DEFAULT 'DRAFT' NOT NULL,
    currency_code CHAR(3) DEFAULT 'UGX' NOT NULL,
    total_amount NUMBER(12, 2) DEFAULT 0 NOT NULL,
    created_by_user_id NUMBER NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    due_at TIMESTAMP,
    CONSTRAINT uq_invoice_number UNIQUE (invoice_number),
    CONSTRAINT uq_invoice_service UNIQUE (
        service_type, service_reference_id
    ),
    CONSTRAINT ck_invoice_service_type CHECK (
        service_type IN (
            'CONSULTATION', 'LAB_REQUEST', 'PRESCRIPTION',
            'DISPENSING', 'ADMISSION', 'OTHER'
        )
    ),
    CONSTRAINT ck_invoice_status CHECK (
        invoice_status IN (
            'DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID'
        )
    ),
    CONSTRAINT ck_invoice_total CHECK (total_amount >= 0),
    CONSTRAINT fk_invoice_patient FOREIGN KEY (patient_id)
        REFERENCES PATIENTS(patient_id),
    CONSTRAINT fk_invoice_appointment FOREIGN KEY (appointment_id)
        REFERENCES APPOINTMENTS(appointment_id),
    CONSTRAINT fk_invoice_user FOREIGN KEY (created_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE TABLE INVOICE_ITEMS (
    invoice_item_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id NUMBER NOT NULL,
    description VARCHAR2(300) NOT NULL,
    quantity NUMBER(10, 2) NOT NULL,
    unit_price NUMBER(12, 2) NOT NULL,
    line_total NUMBER(12, 2) NOT NULL,
    CONSTRAINT ck_invoice_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_invoice_item_price CHECK (
        unit_price >= 0 AND line_total >= 0
    ),
    CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id)
        REFERENCES INVOICES(invoice_id)
);

CREATE TABLE PAYMENTS (
    payment_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id NUMBER NOT NULL,
    amount NUMBER(12, 2) NOT NULL,
    payment_method VARCHAR2(24) NOT NULL,
    payment_status VARCHAR2(16) DEFAULT 'COMPLETED' NOT NULL,
    payment_reference VARCHAR2(120),
    received_by_user_id NUMBER NOT NULL,
    received_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    notes VARCHAR2(1000),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (
        payment_method IN (
            'CASH', 'MOBILE_MONEY', 'CARD', 'BANK_TRANSFER'
        )
    ),
    CONSTRAINT ck_payment_status CHECK (
        payment_status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')
    ),
    CONSTRAINT fk_payment_invoice FOREIGN KEY (invoice_id)
        REFERENCES INVOICES(invoice_id),
    CONSTRAINT fk_payment_user FOREIGN KEY (received_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE TABLE WORKFLOW_EVENTS (
    workflow_event_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id NUMBER NOT NULL,
    appointment_id NUMBER,
    entity_type VARCHAR2(24) NOT NULL,
    entity_id NUMBER NOT NULL,
    event_type VARCHAR2(60) NOT NULL,
    department_name VARCHAR2(80) NOT NULL,
    performed_by_user_id NUMBER NOT NULL,
    occurred_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    details CLOB,
    CONSTRAINT fk_workflow_event_patient FOREIGN KEY (patient_id)
        REFERENCES PATIENTS(patient_id),
    CONSTRAINT fk_workflow_event_appointment FOREIGN KEY (appointment_id)
        REFERENCES APPOINTMENTS(appointment_id),
    CONSTRAINT fk_workflow_event_user FOREIGN KEY (performed_by_user_id)
        REFERENCES USERS(user_id)
);

CREATE INDEX ix_consultations_patient_date
    ON CONSULTATIONS(patient_id, consultation_date);
CREATE INDEX ix_lab_requests_status_date
    ON LAB_REQUESTS(request_status, requested_at);
CREATE INDEX ix_lab_requests_consultation
    ON LAB_REQUESTS(consultation_id);
CREATE INDEX ix_prescriptions_patient_status
    ON PRESCRIPTIONS(patient_id, prescription_status);
CREATE INDEX ix_medicine_batches_expiry
    ON MEDICINE_BATCHES(expiry_date);
CREATE INDEX ix_inventory_batch_date
    ON INVENTORY_TRANSACTIONS(batch_id, recorded_at);
CREATE INDEX ix_invoices_patient_status
    ON INVOICES(patient_id, invoice_status);
CREATE INDEX ix_payments_invoice_status
    ON PAYMENTS(invoice_id, payment_status);
CREATE INDEX ix_nursing_observations_admission
    ON NURSING_OBSERVATIONS(admission_id, recorded_at);
CREATE INDEX ix_nursing_med_admin_admission
    ON NURSING_MEDICATION_ADMINISTRATIONS(admission_id, administered_at);
CREATE INDEX ix_workflow_patient_time
    ON WORKFLOW_EVENTS(patient_id, occurred_at);

CREATE TABLE PERMISSIONS (
    permission_code VARCHAR2(80) PRIMARY KEY,
    description VARCHAR2(240) NOT NULL
);

CREATE TABLE ROLE_PERMISSIONS (
    role_id NUMBER NOT NULL,
    permission_code VARCHAR2(80) NOT NULL,
    granted_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id)
        REFERENCES ROLES(role_id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code)
        REFERENCES PERMISSIONS(permission_code)
);

MERGE INTO PERMISSIONS target
USING (
    SELECT 'ADMIN_WORKFLOW_MONITOR' permission_code,
           'Monitor cross-department patient workflow' description FROM DUAL
    UNION ALL SELECT 'RECEPTION_PATIENT_REGISTER',
           'Register patients' FROM DUAL
    UNION ALL SELECT 'RECEPTION_APPOINTMENT_BOOK',
           'Book patient appointments' FROM DUAL
    UNION ALL SELECT 'RECEPTION_PATIENT_CHECKIN',
           'Check patients in' FROM DUAL
    UNION ALL SELECT 'OPD_CONSULT',
           'Conduct outpatient consultations' FROM DUAL
    UNION ALL SELECT 'OPD_LAB_REQUEST',
           'Request laboratory investigations' FROM DUAL
    UNION ALL SELECT 'OPD_LAB_RESULT_REVIEW',
           'Review laboratory results' FROM DUAL
    UNION ALL SELECT 'OPD_PRESCRIBE',
           'Create patient prescriptions' FROM DUAL
    UNION ALL SELECT 'NURSING_CARE_RECORD',
           'Record nursing care' FROM DUAL
    UNION ALL SELECT 'NURSING_VITALS_RECORD',
           'Record patient vital signs' FROM DUAL
    UNION ALL SELECT 'LAB_REQUEST_PROCESS',
           'Process laboratory requests' FROM DUAL
    UNION ALL SELECT 'LAB_RESULT_ENTER',
           'Enter laboratory results' FROM DUAL
    UNION ALL SELECT 'PHARMACY_INVENTORY_MANAGE',
           'Manage medicine inventory' FROM DUAL
    UNION ALL SELECT 'PHARMACY_DISPENSE',
           'Dispense prescribed medicines' FROM DUAL
    UNION ALL SELECT 'FINANCE_INVOICE_CREATE',
           'Create service invoices' FROM DUAL
    UNION ALL SELECT 'FINANCE_PAYMENT_RECORD',
           'Record patient payments' FROM DUAL
    UNION ALL SELECT 'FINANCE_RECEIPT_ISSUE',
           'Issue payment receipts' FROM DUAL
    UNION ALL SELECT 'FINANCE_REPORT_VIEW',
           'View finance reports' FROM DUAL
    UNION ALL SELECT 'INPATIENT_WARD_MANAGE',
           'Manage wards and bed capacity' FROM DUAL
    UNION ALL SELECT 'INPATIENT_ADMISSION_MANAGE',
           'Manage patient admissions' FROM DUAL
    UNION ALL SELECT 'INPATIENT_BED_ALLOCATE',
           'Allocate and transfer beds' FROM DUAL
    UNION ALL SELECT 'INPATIENT_DISCHARGE_PROCESS',
           'Process patient discharges' FROM DUAL
) source
ON (target.permission_code = source.permission_code)
WHEN NOT MATCHED THEN
    INSERT (permission_code, description)
    VALUES (source.permission_code, source.description);

MERGE INTO ROLE_PERMISSIONS target
USING (
    SELECT role_row.role_id, permission_row.permission_code
    FROM ROLES role_row
    CROSS JOIN PERMISSIONS permission_row
    WHERE UPPER(role_row.role_name) = 'ADMINISTRATOR'
    UNION ALL
    SELECT role_row.role_id, 'RECEPTION_PATIENT_REGISTER'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) = 'RECEPTIONIST'
    UNION ALL
    SELECT role_row.role_id, 'RECEPTION_APPOINTMENT_BOOK'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) = 'RECEPTIONIST'
    UNION ALL
    SELECT role_row.role_id, 'RECEPTION_PATIENT_CHECKIN'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) = 'RECEPTIONIST'
    UNION ALL
    SELECT role_row.role_id, 'OPD_CONSULT'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('DOCTOR', 'MEDICAL OFFICER')
    UNION ALL
    SELECT role_row.role_id, 'OPD_LAB_REQUEST'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('DOCTOR', 'MEDICAL OFFICER')
    UNION ALL
    SELECT role_row.role_id, 'OPD_LAB_RESULT_REVIEW'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('DOCTOR', 'MEDICAL OFFICER')
    UNION ALL
    SELECT role_row.role_id, 'OPD_PRESCRIBE'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('DOCTOR', 'MEDICAL OFFICER')
    UNION ALL
    SELECT role_row.role_id, 'NURSING_CARE_RECORD'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('NURSE', 'SENIOR NURSE')
    UNION ALL
    SELECT role_row.role_id, 'NURSING_VITALS_RECORD'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('NURSE', 'SENIOR NURSE')
    UNION ALL
    SELECT role_row.role_id, 'LAB_REQUEST_PROCESS'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN (
        'LAB TECHNICIAN', 'LABORATORY TECHNICIAN'
    )
    UNION ALL
    SELECT role_row.role_id, 'LAB_RESULT_ENTER'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN (
        'LAB TECHNICIAN', 'LABORATORY TECHNICIAN'
    )
    UNION ALL
    SELECT role_row.role_id, 'PHARMACY_INVENTORY_MANAGE'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN (
        'PHARMACIST', 'PHARMACY ASSISTANT'
    )
    UNION ALL
    SELECT role_row.role_id, 'PHARMACY_DISPENSE'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN (
        'PHARMACIST', 'PHARMACY ASSISTANT'
    )
    UNION ALL
    SELECT role_row.role_id, 'FINANCE_INVOICE_CREATE'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('ACCOUNTANT', 'FINANCE OFFICER')
    UNION ALL
    SELECT role_row.role_id, 'FINANCE_PAYMENT_RECORD'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('ACCOUNTANT', 'FINANCE OFFICER')
    UNION ALL
    SELECT role_row.role_id, 'FINANCE_RECEIPT_ISSUE'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('ACCOUNTANT', 'FINANCE OFFICER')
    UNION ALL
    SELECT role_row.role_id, 'FINANCE_REPORT_VIEW'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) IN ('ACCOUNTANT', 'FINANCE OFFICER')
    UNION ALL
    SELECT role_row.role_id, 'INPATIENT_WARD_MANAGE'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) = 'WARD MANAGER'
    UNION ALL
    SELECT role_row.role_id, 'INPATIENT_ADMISSION_MANAGE'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) = 'WARD MANAGER'
    UNION ALL
    SELECT role_row.role_id, 'INPATIENT_BED_ALLOCATE'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) = 'WARD MANAGER'
    UNION ALL
    SELECT role_row.role_id, 'INPATIENT_DISCHARGE_PROCESS'
    FROM ROLES role_row
    WHERE UPPER(role_row.role_name) = 'WARD MANAGER'
) source
ON (target.role_id = source.role_id
    AND target.permission_code = source.permission_code)
WHEN NOT MATCHED THEN
    INSERT (role_id, permission_code)
    VALUES (source.role_id, source.permission_code);

COMMIT;
