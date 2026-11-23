-- Optional demo data only. Do not run this against a production database.
-- Adds nine sample products and 150 total stock units across three categories
-- (50 units per category). Re-running the script will not duplicate its rows.
-- The expiry dates are deliberately short for demonstrating expiry alerts.

MERGE INTO suppliers target
USING (
    SELECT 'SJCH DEMO SUPPLIER' AS supplier_name FROM dual
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

COMMIT;
