CREATE TABLE roles (
    role_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_name VARCHAR2(50) CONSTRAINT uq_roles_name UNIQUE NOT NULL
);

CREATE TABLE users (
    user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id NUMBER NOT NULL,
    username VARCHAR2(100) CONSTRAINT uq_users_username UNIQUE NOT NULL,
    password_hash VARCHAR2(500),
    staff_id NUMBER,
    email VARCHAR2(200),
    is_active CHAR(1) DEFAULT 'Y' NOT NULL
        CONSTRAINT ck_users_active CHECK (is_active IN ('Y', 'N')),
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id)
        REFERENCES roles(role_id)
);

CREATE TABLE audit_logs (
    audit_log_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id NUMBER,
    action VARCHAR2(100) NOT NULL,
    details VARCHAR2(1000),
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);

CREATE TABLE departments (
    department_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    department_name VARCHAR2(100) CONSTRAINT uq_departments_name UNIQUE NOT NULL,
    is_active CHAR(1) DEFAULT 'Y' NOT NULL
        CONSTRAINT ck_departments_active CHECK (is_active IN ('Y', 'N'))
);

CREATE TABLE staff (
    staff_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    department_id NUMBER,
    staff_number VARCHAR2(30) CONSTRAINT uq_staff_number UNIQUE NOT NULL,
    full_name VARCHAR2(150) NOT NULL,
    job_title VARCHAR2(100) NOT NULL,
    email VARCHAR2(200),
    is_active CHAR(1) DEFAULT 'Y' NOT NULL
        CONSTRAINT ck_staff_active CHECK (is_active IN ('Y', 'N')),
    CONSTRAINT fk_staff_department FOREIGN KEY (department_id)
        REFERENCES departments(department_id)
);

ALTER TABLE users ADD CONSTRAINT fk_users_staff
    FOREIGN KEY (staff_id) REFERENCES staff(staff_id);

CREATE TABLE patients (
    patient_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_number VARCHAR2(30) CONSTRAINT uq_patients_number UNIQUE NOT NULL,
    full_name VARCHAR2(150) NOT NULL,
    date_of_birth DATE,
    sex VARCHAR2(20),
    phone VARCHAR2(30),
    address VARCHAR2(500),
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE appointments (
    appointment_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id NUMBER NOT NULL,
    staff_id NUMBER,
    department_id NUMBER,
    appointment_date DATE NOT NULL,
    status VARCHAR2(30) DEFAULT 'SCHEDULED' NOT NULL
        CONSTRAINT ck_appointments_status CHECK (
            status IN ('SCHEDULED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED')
        ),
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id)
        REFERENCES patients(patient_id),
    CONSTRAINT fk_appointments_staff FOREIGN KEY (staff_id)
        REFERENCES staff(staff_id),
    CONSTRAINT fk_appointments_department FOREIGN KEY (department_id)
        REFERENCES departments(department_id)
);

CREATE TABLE admissions (
    admission_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id NUMBER NOT NULL,
    admitted_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    discharge_date TIMESTAMP,
    status VARCHAR2(30) DEFAULT 'ADMITTED' NOT NULL,
    CONSTRAINT fk_admissions_patient FOREIGN KEY (patient_id)
        REFERENCES patients(patient_id)
);

INSERT INTO roles (role_name) VALUES ('Administrator');
INSERT INTO roles (role_name) VALUES ('Receptionist');
INSERT INTO roles (role_name) VALUES ('Doctor');
INSERT INTO roles (role_name) VALUES ('Nurse');
INSERT INTO roles (role_name) VALUES ('Lab Technician');
INSERT INTO roles (role_name) VALUES ('Pharmacist');
INSERT INTO roles (role_name) VALUES ('Accountant');
INSERT INTO roles (role_name) VALUES ('Ward Manager');

COMMIT;
