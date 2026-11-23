-- Optional synthetic data for local/demo databases only.
-- Do not run this script against production.
--
-- Run this file with SQL Developer's Run Script (F5). It expects the current
-- application schema and db/cross-department-workflow.sql to be installed.
-- The pharmacy catalog and stock seed is included below, so this file can run
-- without resolving another script path.
--
-- The script reuses active role accounts; it does not create login accounts,
-- passwords, or real patient information. DEMO-SEED markers distinguish
-- generated rows from operational data.

SET DEFINE OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DECLARE
    v_object_count NUMBER;

    PROCEDURE require_table(p_table_name VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_object_count
        FROM user_tables
        WHERE table_name = UPPER(p_table_name);

        IF v_object_count = 0 THEN
            raise_application_error(
                -20001,
                'Required table ' || UPPER(p_table_name)
                    || ' is missing. Install the current workflow schema first.'
            );
        END IF;
    END;

    PROCEDURE require_column(
        p_table_name VARCHAR2, p_column_name VARCHAR2
    ) IS
    BEGIN
        SELECT COUNT(*) INTO v_object_count
        FROM user_tab_columns
        WHERE table_name = UPPER(p_table_name)
          AND column_name = UPPER(p_column_name);

        IF v_object_count = 0 THEN
            raise_application_error(
                -20002,
                'Required column ' || UPPER(p_table_name) || '.'
                    || UPPER(p_column_name)
                    || ' is missing. This seed targets the current app schema, '
                    || 'not the older milestone3 schema.'
            );
        END IF;
    END;

    PROCEDURE require_active_role(
        p_role_name_one VARCHAR2,
        p_role_name_two VARCHAR2,
        p_needs_active_staff CHAR
    ) IS
    BEGIN
        SELECT COUNT(*) INTO v_object_count
        FROM users u
        JOIN roles r ON r.role_id = u.role_id
        LEFT JOIN staff s ON s.staff_id = u.staff_id
        WHERE u.is_active = 'Y'
          AND (
              UPPER(r.role_name) = UPPER(p_role_name_one)
              OR (p_role_name_two IS NOT NULL
                  AND UPPER(r.role_name) = UPPER(p_role_name_two))
          )
          AND (
              p_needs_active_staff = 'N'
              OR (s.staff_id IS NOT NULL AND s.status = 'ACTIVE')
          );

        IF v_object_count = 0 THEN
            raise_application_error(
                -20003,
                'An active ' || p_role_name_one
                    || ' account is required to create complete demo data.'
            );
        END IF;
    END;
BEGIN
    require_table('PATIENTS');
    require_table('STAFF');
    require_table('USERS');
    require_table('ROLES');
    require_table('DEPARTMENTS');
    require_table('APPOINTMENTS');
    require_table('WARDS');
    require_table('BEDS');
    require_table('ADMISSIONS');
    require_table('PATIENT_TRANSFERS');
    require_table('DISCHARGE_REQUESTS');
    require_table('NURSING_OBSERVATIONS');
    require_table('CONSULTATIONS');
    require_table('LAB_REQUESTS');
    require_table('LAB_RESULTS');
    require_table('SUPPLIERS');
    require_table('MEDICINES');
    require_table('MEDICINE_BATCHES');
    require_table('PRESCRIPTIONS');
    require_table('PRESCRIPTION_ITEMS');
    require_table('NURSING_MEDICATION_ADMINISTRATIONS');
    require_table('DISPENSINGS');
    require_table('DISPENSING_ITEMS');
    require_table('INVENTORY_TRANSACTIONS');
    require_table('INVOICES');
    require_table('INVOICE_ITEMS');
    require_table('PAYMENTS');
    require_table('WORKFLOW_EVENTS');
    require_table('ROLE_PERMISSIONS');

    require_column('PATIENTS', 'FIRST_NAME');
    require_column('PATIENTS', 'LAST_NAME');
    require_column('PATIENTS', 'STATUS');
    require_column('STAFF', 'FIRST_NAME');
    require_column('STAFF', 'LAST_NAME');
    require_column('STAFF', 'STATUS');
    require_column('DEPARTMENTS', 'STATUS');
    require_column('APPOINTMENTS', 'DOCTOR_ID');
    require_column('APPOINTMENTS', 'REASON');
    require_column('ADMISSIONS', 'BED_ID');

    require_active_role('DOCTOR', 'MEDICAL OFFICER', 'Y');
    require_active_role('RECEPTIONIST', NULL, 'N');
    require_active_role('LAB TECHNICIAN', 'LABORATORY TECHNICIAN', 'Y');
    require_active_role('NURSE', 'SENIOR NURSE', 'Y');
    require_active_role('PHARMACIST', 'PHARMACY ASSISTANT', 'N');
    require_active_role('ACCOUNTANT', 'FINANCE OFFICER', 'N');
    require_active_role('WARD MANAGER', NULL, 'N');
END;
/

-- Seed the pharmacy catalog and stock used by the demo prescription workflow.
MERGE INTO suppliers target
USING (
    SELECT 'SJCH DEMO SUPPLIER' supplier_name FROM dual
) source
ON (UPPER(target.supplier_name) = UPPER(source.supplier_name))
WHEN NOT MATCHED THEN
    INSERT (supplier_name, contact_name, phone, email, address, is_active)
    VALUES (
        source.supplier_name, 'Demo Contact', '0000000000',
        'demo@example.invalid', 'Demonstration data only', 'Y'
    );

MERGE INTO medicines target
USING (
    SELECT 'Paracetamol' medicine_name, 'Paracetamol' generic_name,
           '500 mg' strength, 'Tablet' dosage_form, 'Analgesics' category,
           10 reorder_level FROM dual
    UNION ALL SELECT 'Ibuprofen', 'Ibuprofen', '400 mg', 'Tablet',
           'Analgesics', 18 FROM dual
    UNION ALL SELECT 'Diclofenac', 'Diclofenac', '50 mg', 'Tablet',
           'Analgesics', 10 FROM dual
    UNION ALL SELECT 'Amoxicillin', 'Amoxicillin', '500 mg', 'Capsule',
           'Antibiotics', 10 FROM dual
    UNION ALL SELECT 'Azithromycin', 'Azithromycin', '250 mg', 'Tablet',
           'Antibiotics', 18 FROM dual
    UNION ALL SELECT 'Metronidazole', 'Metronidazole', '400 mg', 'Tablet',
           'Antibiotics', 10 FROM dual
    UNION ALL SELECT 'Vitamin C', 'Ascorbic acid', '500 mg', 'Tablet',
           'Vitamins', 10 FROM dual
    UNION ALL SELECT 'Folic Acid', 'Folic acid', '5 mg', 'Tablet',
           'Vitamins', 18 FROM dual
    UNION ALL SELECT 'Multivitamin', 'Multivitamin', 'Standard', 'Tablet',
           'Vitamins', 10 FROM dual
) source
ON (
    UPPER(target.medicine_name) = UPPER(source.medicine_name)
    AND UPPER(target.strength) = UPPER(source.strength)
    AND UPPER(target.category) = UPPER(source.category)
)
WHEN NOT MATCHED THEN
    INSERT (
        medicine_name, generic_name, strength, dosage_form, category,
        reorder_level, is_active
    )
    VALUES (
        source.medicine_name, source.generic_name, source.strength,
        source.dosage_form, source.category, source.reorder_level, 'Y'
    );

MERGE INTO medicine_batches target
USING (
    SELECT m.medicine_id, s.supplier_id, demo_batch.batch_number,
           demo_batch.quantity_received, demo_batch.unit_cost,
           demo_batch.selling_price, demo_batch.expiry_date
    FROM (
        SELECT 'Paracetamol' medicine_name, '500 mg' strength,
               'Analgesics' category, 'DEMO-AN-001' batch_number,
               20 quantity_received, 500 unit_cost, 800 selling_price,
               TRUNC(SYSDATE) + 180 expiry_date FROM dual
        UNION ALL SELECT 'Ibuprofen', '400 mg', 'Analgesics',
               'DEMO-AN-002', 15, 700, 1000, TRUNC(SYSDATE) + 20 FROM dual
        UNION ALL SELECT 'Diclofenac', '50 mg', 'Analgesics',
               'DEMO-AN-003', 15, 600, 900, TRUNC(SYSDATE) + 180 FROM dual
        UNION ALL SELECT 'Amoxicillin', '500 mg', 'Antibiotics',
               'DEMO-AB-001', 20, 1200, 1700, TRUNC(SYSDATE) + 180 FROM dual
        UNION ALL SELECT 'Azithromycin', '250 mg', 'Antibiotics',
               'DEMO-AB-002', 15, 1500, 2100, TRUNC(SYSDATE) + 20 FROM dual
        UNION ALL SELECT 'Metronidazole', '400 mg', 'Antibiotics',
               'DEMO-AB-003', 15, 800, 1200, TRUNC(SYSDATE) + 180 FROM dual
        UNION ALL SELECT 'Vitamin C', '500 mg', 'Vitamins',
               'DEMO-VT-001', 20, 400, 700, TRUNC(SYSDATE) + 180 FROM dual
        UNION ALL SELECT 'Folic Acid', '5 mg', 'Vitamins',
               'DEMO-VT-002', 15, 300, 500, TRUNC(SYSDATE) + 20 FROM dual
        UNION ALL SELECT 'Multivitamin', 'Standard', 'Vitamins',
               'DEMO-VT-003', 15, 900, 1300, TRUNC(SYSDATE) + 180 FROM dual
    ) demo_batch
    JOIN medicines m
      ON UPPER(m.medicine_name) = UPPER(demo_batch.medicine_name)
     AND UPPER(m.strength) = UPPER(demo_batch.strength)
     AND UPPER(m.category) = UPPER(demo_batch.category)
    JOIN suppliers s
      ON UPPER(s.supplier_name) = 'SJCH DEMO SUPPLIER'
) source
ON (
    target.medicine_id = source.medicine_id
    AND target.batch_number = source.batch_number
)
WHEN NOT MATCHED THEN
    INSERT (
        medicine_id, supplier_id, batch_number, quantity_received, unit_cost,
        selling_price, expiry_date
    )
    VALUES (
        source.medicine_id, source.supplier_id, source.batch_number,
        source.quantity_received, source.unit_cost, source.selling_price,
        source.expiry_date
    );

INSERT INTO inventory_transactions (
    batch_id, transaction_type, quantity_delta, recorded_by_user_id, notes
)
SELECT b.batch_id, 'RECEIPT', b.quantity_received,
       (
           SELECT u.user_id
           FROM users u
           JOIN roles r ON r.role_id = u.role_id
           JOIN role_permissions rp ON rp.role_id = r.role_id
           WHERE u.is_active = 'Y'
             AND UPPER(r.role_name) IN ('PHARMACIST', 'PHARMACY ASSISTANT')
             AND rp.permission_code = 'PHARMACY_INVENTORY_MANAGE'
           ORDER BY u.user_id
           FETCH FIRST 1 ROW ONLY
       ),
       'DEMO DATA SEED'
FROM medicine_batches b
WHERE b.batch_number LIKE 'DEMO-%'
  AND EXISTS (
      SELECT 1
      FROM suppliers s
      WHERE s.supplier_id = b.supplier_id
        AND UPPER(s.supplier_name) = 'SJCH DEMO SUPPLIER'
  )
  AND EXISTS (
      SELECT 1
      FROM users u
      JOIN roles r ON r.role_id = u.role_id
      JOIN role_permissions rp ON rp.role_id = r.role_id
      WHERE u.is_active = 'Y'
        AND UPPER(r.role_name) IN ('PHARMACIST', 'PHARMACY ASSISTANT')
        AND rp.permission_code = 'PHARMACY_INVENTORY_MANAGE'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_transactions t
      WHERE t.batch_id = b.batch_id
        AND t.transaction_type = 'RECEIPT'
        AND t.notes = 'DEMO DATA SEED'
  );

DECLARE
    v_doctor_user_id users.user_id%TYPE;
    v_doctor_staff_id staff.staff_id%TYPE;
    v_doctor_department_id staff.department_id%TYPE;
    v_receptionist_user_id users.user_id%TYPE;
    v_laboratory_user_id users.user_id%TYPE;
    v_laboratory_staff_id staff.staff_id%TYPE;
    v_nurse_user_id users.user_id%TYPE;
    v_nurse_staff_id staff.staff_id%TYPE;
    v_pharmacist_user_id users.user_id%TYPE;
    v_finance_user_id users.user_id%TYPE;
    v_ward_manager_user_id users.user_id%TYPE;
    v_ward_department_id staff.department_id%TYPE;

    v_waiting_patient_id patients.patient_id%TYPE;
    v_active_patient_id patients.patient_id%TYPE;
    v_completed_patient_id patients.patient_id%TYPE;
    v_follow_up_patient_id patients.patient_id%TYPE;

    v_waiting_appointment_id appointments.appointment_id%TYPE;
    v_active_appointment_id appointments.appointment_id%TYPE;
    v_completed_appointment_id appointments.appointment_id%TYPE;
    v_follow_up_appointment_id appointments.appointment_id%TYPE;

    v_active_consultation_id consultations.consultation_id%TYPE;
    v_completed_consultation_id consultations.consultation_id%TYPE;
    v_pending_lab_request_id lab_requests.lab_request_id%TYPE;
    v_result_lab_request_id lab_requests.lab_request_id%TYPE;
    v_medicine_id medicines.medicine_id%TYPE;
    v_prescription_id prescriptions.prescription_id%TYPE;
    v_prescription_item_id prescription_items.prescription_item_id%TYPE;
    v_demo_ward_id wards.ward_id%TYPE;
    v_occupied_bed_id beds.bed_id%TYPE;
    v_previous_bed_id beds.bed_id%TYPE;
    v_admission_id admissions.admission_id%TYPE;
    v_batch_id medicine_batches.batch_id%TYPE;
    v_dispensing_id dispensings.dispensing_id%TYPE;
    v_dispensing_item_id dispensing_items.dispensing_item_id%TYPE;
    v_invoice_paid_id invoices.invoice_id%TYPE;
    v_invoice_outstanding_id invoices.invoice_id%TYPE;
    v_invoice_partial_id invoices.invoice_id%TYPE;

    PROCEDURE upsert_demo_event(
        patient_id_value NUMBER,
        appointment_id_value NUMBER,
        entity_type_value VARCHAR2,
        entity_id_value NUMBER,
        event_type_value VARCHAR2,
        department_name_value VARCHAR2,
        user_id_value NUMBER,
        details_value VARCHAR2
    ) IS
    BEGIN
        MERGE INTO workflow_events target
        USING (
            SELECT patient_id_value patient_id,
                   appointment_id_value appointment_id,
                   entity_type_value entity_type,
                   entity_id_value entity_id,
                   event_type_value event_type,
                   department_name_value department_name,
                   user_id_value performed_by_user_id,
                   details_value details
            FROM dual
        ) source
        ON (
            target.patient_id = source.patient_id
            AND target.entity_type = source.entity_type
            AND target.entity_id = source.entity_id
            AND target.event_type = source.event_type
        )
        WHEN MATCHED THEN UPDATE SET
            target.appointment_id = source.appointment_id,
            target.department_name = source.department_name,
            target.performed_by_user_id = source.performed_by_user_id,
            target.occurred_at = SYSTIMESTAMP,
            target.details = source.details
        WHEN NOT MATCHED THEN INSERT (
            patient_id, appointment_id, entity_type, entity_id, event_type,
            department_name, performed_by_user_id, occurred_at, details
        ) VALUES (
            source.patient_id, source.appointment_id, source.entity_type,
            source.entity_id, source.event_type, source.department_name,
            source.performed_by_user_id, SYSTIMESTAMP, source.details
        );
    END;
BEGIN
    SELECT user_id, staff_id, department_id
    INTO v_doctor_user_id, v_doctor_staff_id, v_doctor_department_id
    FROM (
        SELECT u.user_id, s.staff_id, s.department_id
        FROM users u
        JOIN roles r ON r.role_id = u.role_id
        JOIN staff s ON s.staff_id = u.staff_id
        JOIN departments d ON d.department_id = s.department_id
        WHERE u.is_active = 'Y'
          AND s.status = 'ACTIVE'
          AND d.status = 'ACTIVE'
          AND UPPER(r.role_name) IN ('DOCTOR', 'MEDICAL OFFICER')
        ORDER BY u.user_id
    )
    WHERE ROWNUM = 1;

    SELECT user_id INTO v_receptionist_user_id
    FROM (
        SELECT u.user_id
        FROM users u
        JOIN roles r ON r.role_id = u.role_id
        WHERE u.is_active = 'Y'
          AND UPPER(r.role_name) = 'RECEPTIONIST'
        ORDER BY u.user_id
    )
    WHERE ROWNUM = 1;

    SELECT user_id, staff_id
    INTO v_laboratory_user_id, v_laboratory_staff_id
    FROM (
        SELECT u.user_id, s.staff_id
        FROM users u
        JOIN roles r ON r.role_id = u.role_id
        JOIN staff s ON s.staff_id = u.staff_id
        WHERE u.is_active = 'Y'
          AND s.status = 'ACTIVE'
          AND UPPER(r.role_name) IN (
              'LAB TECHNICIAN', 'LABORATORY TECHNICIAN'
          )
        ORDER BY u.user_id
    )
    WHERE ROWNUM = 1;

    SELECT user_id, staff_id
    INTO v_nurse_user_id, v_nurse_staff_id
    FROM (
        SELECT u.user_id, s.staff_id
        FROM users u
        JOIN roles r ON r.role_id = u.role_id
        JOIN staff s ON s.staff_id = u.staff_id
        WHERE u.is_active = 'Y'
          AND s.status = 'ACTIVE'
          AND UPPER(r.role_name) IN ('NURSE', 'SENIOR NURSE')
        ORDER BY u.user_id
    )
    WHERE ROWNUM = 1;

    SELECT user_id INTO v_pharmacist_user_id
    FROM (
        SELECT u.user_id
        FROM users u
        JOIN roles r ON r.role_id = u.role_id
        WHERE u.is_active = 'Y'
          AND UPPER(r.role_name) IN ('PHARMACIST', 'PHARMACY ASSISTANT')
        ORDER BY u.user_id
    )
    WHERE ROWNUM = 1;

    SELECT user_id INTO v_finance_user_id
    FROM (
        SELECT u.user_id
        FROM users u
        JOIN roles r ON r.role_id = u.role_id
        WHERE u.is_active = 'Y'
          AND UPPER(r.role_name) IN ('ACCOUNTANT', 'FINANCE OFFICER')
        ORDER BY u.user_id
    )
    WHERE ROWNUM = 1;

    SELECT user_id, department_id
    INTO v_ward_manager_user_id, v_ward_department_id
    FROM (
        SELECT u.user_id, s.department_id
        FROM users u
        JOIN roles r ON r.role_id = u.role_id
        LEFT JOIN staff s ON s.staff_id = u.staff_id
        WHERE u.is_active = 'Y'
          AND UPPER(r.role_name) = 'WARD MANAGER'
        ORDER BY u.user_id
    )
    WHERE ROWNUM = 1;

    MERGE INTO patients target
    USING (
        SELECT 'DEMO-SJCH-0001' patient_number, 'Amina' first_name,
               'Demo' last_name, DATE '1991-04-12' date_of_birth,
               'Female' gender, '0000000001' phone,
               'patient1@example.invalid' email FROM dual
        UNION ALL SELECT 'DEMO-SJCH-0002', 'Brian', 'Sample',
               DATE '1987-09-23', 'Male', '0000000002',
               'patient2@example.invalid' FROM dual
        UNION ALL SELECT 'DEMO-SJCH-0003', 'Chloe', 'Demo',
               DATE '2000-02-08', 'Female', '0000000003',
               'patient3@example.invalid' FROM dual
        UNION ALL SELECT 'DEMO-SJCH-0004', 'Daniel', 'Sample',
               DATE '1978-11-17', 'Male', '0000000004',
               'patient4@example.invalid' FROM dual
    ) source
    ON (target.patient_number = source.patient_number)
    WHEN MATCHED THEN UPDATE SET
        target.first_name = source.first_name,
        target.last_name = source.last_name,
        target.date_of_birth = source.date_of_birth,
        target.gender = source.gender,
        target.phone = source.phone,
        target.email = source.email,
        target.address = 'DEMO DATA ONLY',
        target.emergency_contact = 'Demo Contact',
        target.emergency_phone = '0000000000',
        target.status = 'ACTIVE'
    WHEN NOT MATCHED THEN INSERT (
        patient_number, first_name, last_name, date_of_birth, gender, phone,
        email, address, emergency_contact, emergency_phone, status
    ) VALUES (
        source.patient_number, source.first_name, source.last_name,
        source.date_of_birth, source.gender, source.phone, source.email,
        'DEMO DATA ONLY', 'Demo Contact', '0000000000', 'ACTIVE'
    );

    SELECT patient_id INTO v_waiting_patient_id
    FROM patients WHERE patient_number = 'DEMO-SJCH-0001';
    SELECT patient_id INTO v_active_patient_id
    FROM patients WHERE patient_number = 'DEMO-SJCH-0002';
    SELECT patient_id INTO v_completed_patient_id
    FROM patients WHERE patient_number = 'DEMO-SJCH-0003';
    SELECT patient_id INTO v_follow_up_patient_id
    FROM patients WHERE patient_number = 'DEMO-SJCH-0004';

    MERGE INTO appointments target
    USING (
        SELECT v_waiting_patient_id patient_id,
               v_doctor_staff_id doctor_id,
               v_doctor_department_id department_id,
               TRUNC(SYSDATE) + 9 / 24 appointment_date,
               'DEMO-SEED: WAITING CONSULTATION' reason,
               'WAITING' status
        FROM dual
        UNION ALL
        SELECT v_active_patient_id, v_doctor_staff_id, v_doctor_department_id,
               TRUNC(SYSDATE) + 10 / 24,
               'DEMO-SEED: ACTIVE CONSULTATION', 'IN_PROGRESS'
        FROM dual
        UNION ALL
        SELECT v_completed_patient_id, v_doctor_staff_id,
               v_doctor_department_id,
               TRUNC(SYSDATE) + 11 / 24,
               'DEMO-SEED: COMPLETED CONSULTATION', 'COMPLETED'
        FROM dual
        UNION ALL
        SELECT v_follow_up_patient_id, v_doctor_staff_id,
               v_doctor_department_id,
               TRUNC(SYSDATE) + 7 + 9 / 24,
               'FOLLOW-UP: DEMO-SEED REVIEW', 'SCHEDULED'
        FROM dual
    ) source
    ON (
        target.patient_id = source.patient_id
        AND target.doctor_id = source.doctor_id
        AND target.reason = source.reason
    )
    WHEN MATCHED THEN UPDATE SET
        target.department_id = source.department_id,
        target.appointment_date = source.appointment_date,
        target.status = source.status
    WHEN NOT MATCHED THEN INSERT (
        patient_id, doctor_id, department_id, appointment_date, reason, status
    ) VALUES (
        source.patient_id, source.doctor_id, source.department_id,
        source.appointment_date, source.reason, source.status
    );

    SELECT appointment_id INTO v_waiting_appointment_id
    FROM appointments
    WHERE patient_id = v_waiting_patient_id
      AND doctor_id = v_doctor_staff_id
      AND reason = 'DEMO-SEED: WAITING CONSULTATION';
    SELECT appointment_id INTO v_active_appointment_id
    FROM appointments
    WHERE patient_id = v_active_patient_id
      AND doctor_id = v_doctor_staff_id
      AND reason = 'DEMO-SEED: ACTIVE CONSULTATION';
    SELECT appointment_id INTO v_completed_appointment_id
    FROM appointments
    WHERE patient_id = v_completed_patient_id
      AND doctor_id = v_doctor_staff_id
      AND reason = 'DEMO-SEED: COMPLETED CONSULTATION';
    SELECT appointment_id INTO v_follow_up_appointment_id
    FROM appointments
    WHERE patient_id = v_follow_up_patient_id
      AND doctor_id = v_doctor_staff_id
      AND reason = 'FOLLOW-UP: DEMO-SEED REVIEW';

    MERGE INTO consultations target
    USING (
        SELECT v_active_appointment_id appointment_id,
               v_active_patient_id patient_id, v_doctor_staff_id doctor_id,
               SYSTIMESTAMP - INTERVAL '15' MINUTE consultation_date,
               TO_CLOB('DEMO ONLY - example consultation; not clinical advice.')
                   diagnosis,
               TO_CLOB('Synthetic record for dashboard demonstration only.')
                   clinical_notes,
               'IN_PROGRESS' consultation_status
        FROM dual
        UNION ALL
        SELECT v_completed_appointment_id, v_completed_patient_id,
               v_doctor_staff_id,
               SYSTIMESTAMP - INTERVAL '2' HOUR,
               TO_CLOB('DEMO ONLY - example completed consultation.'),
               TO_CLOB('Synthetic record; not for patient care.'),
               'COMPLETED'
        FROM dual
    ) source
    ON (target.appointment_id = source.appointment_id)
    WHEN MATCHED THEN UPDATE SET
        target.patient_id = source.patient_id,
        target.doctor_id = source.doctor_id,
        target.consultation_date = source.consultation_date,
        target.diagnosis = source.diagnosis,
        target.clinical_notes = source.clinical_notes,
        target.consultation_status = source.consultation_status,
        target.updated_at = SYSTIMESTAMP
    WHEN NOT MATCHED THEN INSERT (
        appointment_id, patient_id, doctor_id, consultation_date, diagnosis,
        clinical_notes, consultation_status, created_by_user_id
    ) VALUES (
        source.appointment_id, source.patient_id, source.doctor_id,
        source.consultation_date, source.diagnosis, source.clinical_notes,
        source.consultation_status, v_doctor_user_id
    );

    SELECT consultation_id INTO v_active_consultation_id
    FROM consultations WHERE appointment_id = v_active_appointment_id;
    SELECT consultation_id INTO v_completed_consultation_id
    FROM consultations WHERE appointment_id = v_completed_appointment_id;

    MERGE INTO lab_requests target
    USING (
        SELECT v_active_consultation_id consultation_id,
               v_active_patient_id patient_id,
               v_doctor_staff_id requested_by_staff_id,
               'DEMO CBC' test_name,
               TO_CLOB('DEMO ONLY - synthetic laboratory request.')
                   instructions,
               'REQUESTED' request_status,
               CAST(NULL AS TIMESTAMP) sample_collected_at,
               CAST(NULL AS TIMESTAMP) processing_started_at,
               CAST(NULL AS TIMESTAMP) result_entered_at
        FROM dual
        UNION ALL
        SELECT v_completed_consultation_id, v_completed_patient_id,
               v_doctor_staff_id, 'DEMO MALARIA RDT',
               TO_CLOB('DEMO ONLY - synthetic laboratory request.'),
               'RESULT_ENTERED',
               SYSTIMESTAMP - INTERVAL '30' MINUTE,
               SYSTIMESTAMP - INTERVAL '20' MINUTE,
               SYSTIMESTAMP - INTERVAL '10' MINUTE
        FROM dual
    ) source
    ON (
        target.consultation_id = source.consultation_id
        AND target.test_name = source.test_name
    )
    WHEN MATCHED THEN UPDATE SET
        target.patient_id = source.patient_id,
        target.requested_by_staff_id = source.requested_by_staff_id,
        target.instructions = source.instructions,
        target.request_status = source.request_status,
        target.sample_collected_at = source.sample_collected_at,
        target.processing_started_at = source.processing_started_at,
        target.result_entered_at = source.result_entered_at
    WHEN NOT MATCHED THEN INSERT (
        consultation_id, patient_id, requested_by_staff_id, test_name,
        instructions, request_status, sample_collected_at,
        processing_started_at, result_entered_at
    ) VALUES (
        source.consultation_id, source.patient_id,
        source.requested_by_staff_id, source.test_name, source.instructions,
        source.request_status, source.sample_collected_at,
        source.processing_started_at, source.result_entered_at
    );

    SELECT lab_request_id INTO v_pending_lab_request_id
    FROM lab_requests
    WHERE consultation_id = v_active_consultation_id
      AND test_name = 'DEMO CBC';
    SELECT lab_request_id INTO v_result_lab_request_id
    FROM lab_requests
    WHERE consultation_id = v_completed_consultation_id
      AND test_name = 'DEMO MALARIA RDT';

    MERGE INTO lab_results target
    USING (
        SELECT v_result_lab_request_id lab_request_id,
               TO_CLOB('DEMO ONLY - synthetic result; do not use clinically.')
                   result_text,
               v_laboratory_staff_id entered_by_staff_id
        FROM dual
    ) source
    ON (target.lab_request_id = source.lab_request_id)
    WHEN MATCHED THEN UPDATE SET
        target.result_text = source.result_text,
        target.entered_by_staff_id = source.entered_by_staff_id,
        target.entered_at = SYSTIMESTAMP,
        target.reviewed_by_staff_id = NULL,
        target.reviewed_at = NULL
    WHEN NOT MATCHED THEN INSERT (
        lab_request_id, result_text, entered_by_staff_id
    ) VALUES (
        source.lab_request_id, source.result_text, source.entered_by_staff_id
    );

    SELECT medicine_id INTO v_medicine_id
    FROM (
        SELECT medicine_id
        FROM medicines
        WHERE UPPER(medicine_name) = 'PARACETAMOL'
          AND UPPER(strength) = '500 MG'
          AND is_active = 'Y'
        ORDER BY medicine_id
    )
    WHERE ROWNUM = 1;

    MERGE INTO prescriptions target
    USING (
        SELECT v_active_consultation_id consultation_id,
               v_active_patient_id patient_id,
               v_doctor_staff_id prescribed_by_staff_id,
               TO_CLOB('DEMO ONLY - not a real prescription.')
                   notes
        FROM dual
    ) source
    ON (target.consultation_id = source.consultation_id)
    WHEN MATCHED THEN UPDATE SET
        target.patient_id = source.patient_id,
        target.prescribed_by_staff_id = source.prescribed_by_staff_id,
        target.prescription_status = 'PENDING',
        target.created_at = SYSTIMESTAMP,
        target.notes = source.notes
    WHEN NOT MATCHED THEN INSERT (
        consultation_id, patient_id, prescribed_by_staff_id,
        prescription_status, notes
    ) VALUES (
        source.consultation_id, source.patient_id,
        source.prescribed_by_staff_id, 'PENDING', source.notes
    );

    SELECT prescription_id INTO v_prescription_id
    FROM prescriptions
    WHERE consultation_id = v_active_consultation_id;

    MERGE INTO prescription_items target
    USING (
        SELECT v_prescription_id prescription_id, v_medicine_id medicine_id,
               '1 tablet' dosage, 'Oral' route,
               'Demo only - not for patient care' frequency,
               1 duration_days, 2 quantity_prescribed,
               'DEMO ONLY - do not administer.' instructions
        FROM dual
    ) source
    ON (
        target.prescription_id = source.prescription_id
        AND target.medicine_id = source.medicine_id
    )
    WHEN MATCHED THEN UPDATE SET
        target.dosage = source.dosage,
        target.route = source.route,
        target.frequency = source.frequency,
        target.duration_days = source.duration_days,
        target.quantity_prescribed = source.quantity_prescribed,
        target.quantity_dispensed = 0,
        target.instructions = source.instructions
    WHEN NOT MATCHED THEN INSERT (
        prescription_id, medicine_id, dosage, route, frequency, duration_days,
        quantity_prescribed, quantity_dispensed, instructions
    ) VALUES (
        source.prescription_id, source.medicine_id, source.dosage,
        source.route, source.frequency, source.duration_days,
        source.quantity_prescribed, 0, source.instructions
    );

    SELECT pi.prescription_item_id INTO v_prescription_item_id
    FROM prescription_items pi
    WHERE pi.prescription_id = v_prescription_id
      AND pi.medicine_id = v_medicine_id;

    MERGE INTO wards target
    USING (
        SELECT 'DEMO GENERAL WARD' ward_name,
               'Demonstration' ward_type,
               v_ward_department_id department_id
        FROM dual
    ) source
    ON (UPPER(target.ward_name) = UPPER(source.ward_name))
    WHEN MATCHED THEN UPDATE SET
        target.ward_type = source.ward_type,
        target.department_id = source.department_id,
        target.is_active = 'Y'
    WHEN NOT MATCHED THEN INSERT (
        ward_name, ward_type, department_id, is_active
    ) VALUES (
        source.ward_name, source.ward_type, source.department_id, 'Y'
    );

    SELECT ward_id INTO v_demo_ward_id
    FROM wards WHERE UPPER(ward_name) = 'DEMO GENERAL WARD';

    MERGE INTO beds target
    USING (
        SELECT v_demo_ward_id ward_id, 'DEMO-GW-01' bed_number,
               'OCCUPIED' bed_status FROM dual
        UNION ALL SELECT v_demo_ward_id, 'DEMO-GW-02', 'AVAILABLE' FROM dual
        UNION ALL SELECT v_demo_ward_id, 'DEMO-GW-03', 'CLEANING' FROM dual
        UNION ALL SELECT v_demo_ward_id, 'DEMO-GW-04', 'OUT_OF_SERVICE' FROM dual
    ) source
    ON (
        target.ward_id = source.ward_id
        AND target.bed_number = source.bed_number
    )
    WHEN MATCHED THEN UPDATE SET target.bed_status = source.bed_status
    WHEN NOT MATCHED THEN INSERT (ward_id, bed_number, bed_status)
    VALUES (source.ward_id, source.bed_number, source.bed_status);

    SELECT bed_id INTO v_occupied_bed_id
    FROM beds
    WHERE ward_id = v_demo_ward_id AND bed_number = 'DEMO-GW-01';
    SELECT bed_id INTO v_previous_bed_id
    FROM beds
    WHERE ward_id = v_demo_ward_id AND bed_number = 'DEMO-GW-02';

    MERGE INTO admissions target
    USING (
        SELECT v_active_patient_id patient_id, v_occupied_bed_id bed_id,
               SYSTIMESTAMP - INTERVAL '1' DAY admitted_at,
               'ADMITTED' status
        FROM dual
    ) source
    ON (
        target.patient_id = source.patient_id
        AND target.bed_id = source.bed_id
        AND target.status = 'ADMITTED'
    )
    WHEN MATCHED THEN UPDATE SET
        target.admitted_at = source.admitted_at
    WHEN NOT MATCHED THEN INSERT (
        patient_id, bed_id, admitted_at, status
    ) VALUES (
        source.patient_id, source.bed_id, source.admitted_at, source.status
    );

    SELECT a.admission_id INTO v_admission_id
    FROM admissions a
    WHERE a.patient_id = v_active_patient_id
      AND a.bed_id = v_occupied_bed_id
      AND a.status = 'ADMITTED';

    MERGE INTO patient_transfers target
    USING (
        SELECT v_admission_id admission_id,
               v_previous_bed_id from_bed_id,
               v_occupied_bed_id to_bed_id,
               v_ward_manager_user_id requested_by_user_id,
               'DEMO-SEED: completed bed transfer' reason
        FROM dual
    ) source
    ON (
        target.admission_id = source.admission_id
        AND target.from_bed_id = source.from_bed_id
        AND target.to_bed_id = source.to_bed_id
        AND target.reason = source.reason
    )
    WHEN MATCHED THEN UPDATE SET
        target.requested_by_user_id = source.requested_by_user_id,
        target.completed_by_user_id = source.requested_by_user_id,
        target.transfer_status = 'COMPLETED',
        target.requested_at = SYSTIMESTAMP - INTERVAL '2' HOUR,
        target.completed_at = SYSTIMESTAMP - INTERVAL '2' HOUR
    WHEN NOT MATCHED THEN INSERT (
        admission_id, from_bed_id, to_bed_id, requested_by_user_id,
        completed_by_user_id, transfer_status, reason, requested_at,
        completed_at
    ) VALUES (
        source.admission_id, source.from_bed_id, source.to_bed_id,
        source.requested_by_user_id, source.requested_by_user_id,
        'COMPLETED', source.reason,
        SYSTIMESTAMP - INTERVAL '2' HOUR,
        SYSTIMESTAMP - INTERVAL '2' HOUR
    );

    MERGE INTO discharge_requests target
    USING (
        SELECT v_admission_id admission_id,
               v_ward_manager_user_id requested_by_user_id,
               'DEMO ONLY - sample discharge request.' notes
        FROM dual
    ) source
    ON (
        target.admission_id = source.admission_id
        AND DBMS_LOB.COMPARE(target.notes, TO_CLOB(source.notes)) = 0
    )
    WHEN MATCHED THEN UPDATE SET
        target.requested_by_user_id = source.requested_by_user_id,
        target.requested_at = SYSTIMESTAMP
    WHEN NOT MATCHED THEN INSERT (
        admission_id, requested_by_user_id, request_status, notes
    ) VALUES (
        source.admission_id, source.requested_by_user_id,
        'REQUESTED', source.notes
    );

    MERGE INTO nursing_observations target
    USING (
        SELECT v_admission_id admission_id,
               v_active_patient_id patient_id,
               v_nurse_user_id recorded_by_user_id,
               36.8 temperature_c, 78 pulse_rate,
               118 systolic_bp, 76 diastolic_bp,
               16 respiratory_rate, 98 oxygen_saturation,
               'DEMO-SEED: synthetic observation; not patient data.' notes
        FROM dual
    ) source
    ON (
        target.admission_id = source.admission_id
        AND target.recorded_by_user_id = source.recorded_by_user_id
        AND target.notes = source.notes
    )
    WHEN MATCHED THEN UPDATE SET
        target.patient_id = source.patient_id,
        target.recorded_at = SYSTIMESTAMP,
        target.temperature_c = source.temperature_c,
        target.pulse_rate = source.pulse_rate,
        target.systolic_bp = source.systolic_bp,
        target.diastolic_bp = source.diastolic_bp,
        target.respiratory_rate = source.respiratory_rate,
        target.oxygen_saturation = source.oxygen_saturation
    WHEN NOT MATCHED THEN INSERT (
        admission_id, patient_id, recorded_by_user_id, recorded_at,
        temperature_c, pulse_rate, systolic_bp, diastolic_bp,
        respiratory_rate, oxygen_saturation, notes
    ) VALUES (
        source.admission_id, source.patient_id, source.recorded_by_user_id,
        SYSTIMESTAMP, source.temperature_c, source.pulse_rate,
        source.systolic_bp, source.diastolic_bp, source.respiratory_rate,
        source.oxygen_saturation, source.notes
    );

    SELECT mb.batch_id INTO v_batch_id
    FROM medicine_batches mb
    WHERE mb.batch_number = 'DEMO-AN-001'
      AND mb.medicine_id = v_medicine_id;

    MERGE INTO dispensings target
    USING (
        SELECT v_prescription_id prescription_id,
               v_pharmacist_user_id dispensed_by_user_id,
               'DEMO-SEED: sample dispensing' notes
        FROM dual
    ) source
    ON (
        target.prescription_id = source.prescription_id
        AND target.notes = source.notes
    )
    WHEN MATCHED THEN UPDATE SET
        target.dispensed_by_user_id = source.dispensed_by_user_id,
        target.dispensed_at = SYSTIMESTAMP
    WHEN NOT MATCHED THEN INSERT (
        prescription_id, dispensed_by_user_id, dispensed_at, notes
    ) VALUES (
        source.prescription_id, source.dispensed_by_user_id,
        SYSTIMESTAMP, source.notes
    );

    SELECT d.dispensing_id INTO v_dispensing_id
    FROM dispensings d
    WHERE d.prescription_id = v_prescription_id
      AND d.notes = 'DEMO-SEED: sample dispensing';

    MERGE INTO dispensing_items target
    USING (
        SELECT v_dispensing_id dispensing_id,
               v_prescription_item_id prescription_item_id,
               v_batch_id batch_id, 1 quantity
        FROM dual
    ) source
    ON (
        target.dispensing_id = source.dispensing_id
        AND target.prescription_item_id = source.prescription_item_id
    )
    WHEN MATCHED THEN UPDATE SET
        target.batch_id = source.batch_id,
        target.quantity = source.quantity
    WHEN NOT MATCHED THEN INSERT (
        dispensing_id, prescription_item_id, batch_id, quantity
    ) VALUES (
        source.dispensing_id, source.prescription_item_id,
        source.batch_id, source.quantity
    );

    SELECT di.dispensing_item_id INTO v_dispensing_item_id
    FROM dispensing_items di
    WHERE di.dispensing_id = v_dispensing_id
      AND di.prescription_item_id = v_prescription_item_id;

    UPDATE prescription_items
    SET quantity_dispensed = 1
    WHERE prescription_item_id = v_prescription_item_id;
    UPDATE prescriptions
    SET prescription_status = 'PARTIALLY_DISPENSED'
    WHERE prescription_id = v_prescription_id;

    MERGE INTO inventory_transactions target
    USING (
        SELECT v_batch_id batch_id,
               v_dispensing_item_id related_dispensing_item_id,
               v_pharmacist_user_id recorded_by_user_id,
               'DEMO-SEED: sample dispensing' notes
        FROM dual
    ) source
    ON (
        target.related_dispensing_item_id =
            source.related_dispensing_item_id
        AND target.transaction_type = 'DISPENSE'
    )
    WHEN MATCHED THEN UPDATE SET
        target.batch_id = source.batch_id,
        target.quantity_delta = -1,
        target.recorded_by_user_id = source.recorded_by_user_id,
        target.recorded_at = SYSTIMESTAMP,
        target.notes = source.notes
    WHEN NOT MATCHED THEN INSERT (
        batch_id, transaction_type, quantity_delta,
        related_dispensing_item_id, recorded_by_user_id, notes
    ) VALUES (
        source.batch_id, 'DISPENSE', -1,
        source.related_dispensing_item_id, source.recorded_by_user_id,
        source.notes
    );

    MERGE INTO nursing_medication_administrations target
    USING (
        SELECT v_admission_id admission_id,
               v_active_patient_id patient_id,
               v_prescription_item_id prescription_item_id,
               v_nurse_user_id administered_by_user_id,
               '1 tablet (DEMO ONLY)' dose_given,
               'Oral' route_given,
               'DEMO-SEED: not a real administration.' notes
        FROM dual
    ) source
    ON (
        target.admission_id = source.admission_id
        AND target.prescription_item_id = source.prescription_item_id
        AND target.notes = source.notes
    )
    WHEN MATCHED THEN UPDATE SET
        target.patient_id = source.patient_id,
        target.administered_by_user_id = source.administered_by_user_id,
        target.administered_at = SYSTIMESTAMP,
        target.dose_given = source.dose_given,
        target.route_given = source.route_given
    WHEN NOT MATCHED THEN INSERT (
        admission_id, patient_id, prescription_item_id,
        administered_by_user_id, administered_at, dose_given, route_given,
        notes
    ) VALUES (
        source.admission_id, source.patient_id, source.prescription_item_id,
        source.administered_by_user_id, SYSTIMESTAMP, source.dose_given,
        source.route_given, source.notes
    );

    MERGE INTO invoices target
    USING (
        SELECT 'DEMO-INV-OPD-0001' invoice_number,
               v_completed_patient_id patient_id,
               v_completed_appointment_id appointment_id,
               'CONSULTATION' service_type,
               v_completed_consultation_id service_reference_id,
               'PAID' invoice_status, 50000 total_amount,
               SYSTIMESTAMP + INTERVAL '7' DAY due_at
        FROM dual
        UNION ALL
        SELECT 'DEMO-INV-OPD-0002', v_active_patient_id,
               v_active_appointment_id, 'CONSULTATION',
               v_active_consultation_id, 'ISSUED', 80000,
               SYSTIMESTAMP + INTERVAL '7' DAY
        FROM dual
        UNION ALL
        SELECT 'DEMO-INV-LAB-0001', v_completed_patient_id,
               v_completed_appointment_id, 'LAB_REQUEST',
               v_result_lab_request_id, 'PARTIALLY_PAID', 30000,
               SYSTIMESTAMP + INTERVAL '7' DAY
        FROM dual
    ) source
    ON (
        target.service_type = source.service_type
        AND target.service_reference_id = source.service_reference_id
    )
    WHEN MATCHED THEN UPDATE SET
        target.invoice_number = source.invoice_number,
        target.patient_id = source.patient_id,
        target.appointment_id = source.appointment_id,
        target.invoice_status = source.invoice_status,
        target.currency_code = 'UGX',
        target.total_amount = source.total_amount,
        target.created_by_user_id = v_finance_user_id,
        target.created_at = SYSTIMESTAMP,
        target.due_at = source.due_at
    WHEN NOT MATCHED THEN INSERT (
        invoice_number, patient_id, appointment_id, service_type,
        service_reference_id, invoice_status, currency_code, total_amount,
        created_by_user_id, due_at
    ) VALUES (
        source.invoice_number, source.patient_id, source.appointment_id,
        source.service_type, source.service_reference_id,
        source.invoice_status, 'UGX', source.total_amount,
        v_finance_user_id, source.due_at
    );

    SELECT invoice_id INTO v_invoice_paid_id
    FROM invoices WHERE invoice_number = 'DEMO-INV-OPD-0001';
    SELECT invoice_id INTO v_invoice_outstanding_id
    FROM invoices WHERE invoice_number = 'DEMO-INV-OPD-0002';
    SELECT invoice_id INTO v_invoice_partial_id
    FROM invoices WHERE invoice_number = 'DEMO-INV-LAB-0001';

    MERGE INTO invoice_items target
    USING (
        SELECT v_invoice_paid_id invoice_id,
               'DEMO ONLY - outpatient consultation' description,
               1 quantity, 50000 unit_price, 50000 line_total
        FROM dual
        UNION ALL
        SELECT v_invoice_outstanding_id,
               'DEMO ONLY - outpatient consultation',
               1, 80000, 80000 FROM dual
        UNION ALL
        SELECT v_invoice_partial_id,
               'DEMO ONLY - laboratory service',
               1, 30000, 30000 FROM dual
    ) source
    ON (
        target.invoice_id = source.invoice_id
        AND target.description = source.description
    )
    WHEN MATCHED THEN UPDATE SET
        target.quantity = source.quantity,
        target.unit_price = source.unit_price,
        target.line_total = source.line_total
    WHEN NOT MATCHED THEN INSERT (
        invoice_id, description, quantity, unit_price, line_total
    ) VALUES (
        source.invoice_id, source.description, source.quantity,
        source.unit_price, source.line_total
    );

    MERGE INTO payments target
    USING (
        SELECT v_invoice_paid_id invoice_id, 50000 amount,
               'MOBILE_MONEY' payment_method, 'COMPLETED' payment_status,
               'DEMO-PAY-OPD-0001' payment_reference
        FROM dual
        UNION ALL
        SELECT v_invoice_outstanding_id, 20000, 'CASH', 'PENDING',
               'DEMO-PAY-OPD-0002' FROM dual
        UNION ALL
        SELECT v_invoice_partial_id, 10000, 'CASH', 'COMPLETED',
               'DEMO-PAY-LAB-0001' FROM dual
    ) source
    ON (target.payment_reference = source.payment_reference)
    WHEN MATCHED THEN UPDATE SET
        target.invoice_id = source.invoice_id,
        target.amount = source.amount,
        target.payment_method = source.payment_method,
        target.payment_status = source.payment_status,
        target.received_by_user_id = v_finance_user_id,
        target.received_at = SYSTIMESTAMP,
        target.notes = 'DEMO ONLY - synthetic payment.'
    WHEN NOT MATCHED THEN INSERT (
        invoice_id, amount, payment_method, payment_status,
        payment_reference, received_by_user_id, received_at, notes
    ) VALUES (
        source.invoice_id, source.amount, source.payment_method,
        source.payment_status, source.payment_reference, v_finance_user_id,
        SYSTIMESTAMP, 'DEMO ONLY - synthetic payment.'
    );

    upsert_demo_event(
        v_waiting_patient_id, NULL, 'PATIENT', v_waiting_patient_id,
        'DEMO_PATIENT_REGISTERED', 'Reception', v_receptionist_user_id,
        'Synthetic patient registered by demo seed.'
    );
    upsert_demo_event(
        v_active_patient_id, NULL, 'PATIENT', v_active_patient_id,
        'DEMO_PATIENT_REGISTERED', 'Reception', v_receptionist_user_id,
        'Synthetic patient registered by demo seed.'
    );
    upsert_demo_event(
        v_completed_patient_id, NULL, 'PATIENT', v_completed_patient_id,
        'DEMO_PATIENT_REGISTERED', 'Reception', v_receptionist_user_id,
        'Synthetic patient registered by demo seed.'
    );
    upsert_demo_event(
        v_follow_up_patient_id, NULL, 'PATIENT', v_follow_up_patient_id,
        'DEMO_PATIENT_REGISTERED', 'Reception', v_receptionist_user_id,
        'Synthetic patient registered by demo seed.'
    );
    upsert_demo_event(
        v_waiting_patient_id, v_waiting_appointment_id, 'APPOINTMENT',
        v_waiting_appointment_id, 'DEMO_APPOINTMENT_BOOKED', 'Reception',
        v_receptionist_user_id, 'Synthetic waiting appointment.'
    );
    upsert_demo_event(
        v_active_patient_id, v_active_appointment_id, 'CONSULTATION',
        v_active_consultation_id, 'DEMO_CONSULTATION_STARTED', 'Outpatient',
        v_doctor_user_id, 'Synthetic consultation in progress.'
    );
    upsert_demo_event(
        v_completed_patient_id, v_completed_appointment_id, 'CONSULTATION',
        v_completed_consultation_id, 'DEMO_CONSULTATION_COMPLETED',
        'Outpatient', v_doctor_user_id, 'Synthetic completed consultation.'
    );
    upsert_demo_event(
        v_active_patient_id, v_active_appointment_id, 'LAB_REQUEST',
        v_pending_lab_request_id, 'DEMO_LAB_REQUESTED', 'Outpatient',
        v_doctor_user_id, 'Synthetic laboratory request.'
    );
    upsert_demo_event(
        v_completed_patient_id, v_completed_appointment_id, 'LAB_REQUEST',
        v_result_lab_request_id, 'DEMO_LAB_RESULT_ENTERED', 'Laboratory',
        v_laboratory_user_id, 'Synthetic result awaiting review.'
    );
    upsert_demo_event(
        v_active_patient_id, v_active_appointment_id, 'PRESCRIPTION',
        v_prescription_id, 'DEMO_PRESCRIPTION_CREATED', 'Outpatient',
        v_doctor_user_id, 'Synthetic prescription; not for patient care.'
    );
    upsert_demo_event(
        v_active_patient_id, NULL, 'ADMISSION', v_admission_id,
        'DEMO_PATIENT_ADMITTED', 'Inpatient', v_ward_manager_user_id,
        'Synthetic active admission in a demonstration ward.'
    );
    upsert_demo_event(
        v_active_patient_id, NULL, 'NURSING_OBSERVATION',
        v_admission_id, 'DEMO_VITALS_RECORDED', 'Nursing', v_nurse_user_id,
        'Synthetic observation; not a patient record.'
    );
    upsert_demo_event(
        v_completed_patient_id, v_completed_appointment_id, 'INVOICE',
        v_invoice_paid_id, 'DEMO_INVOICE_CREATED', 'Finance',
        v_finance_user_id, 'Synthetic paid invoice.'
    );
    upsert_demo_event(
        v_completed_patient_id, v_completed_appointment_id, 'INVOICE',
        v_invoice_paid_id, 'DEMO_PAYMENT_RECORDED', 'Finance',
        v_finance_user_id, 'Synthetic completed payment.'
    );

    COMMIT;
END;
/

PROMPT Synthetic hospital demo data has been inserted or refreshed.
PROMPT All patient, diagnosis, laboratory, medication, and payment rows are DEMO ONLY.
