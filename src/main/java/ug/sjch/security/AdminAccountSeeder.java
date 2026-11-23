package ug.sjch.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import ug.sjch.dao.DatabaseConnectionFactory;

public final class AdminAccountSeeder {

    private AdminAccountSeeder() {}

    public static void main(String[] args) {

        String password = System.getenv("SJCH_INITIAL_ADMIN_PASSWORD");

        if (password == null || password.isBlank()) {
            System.out.println("Administrator password was not provided.");
            return;
        }

        String username = "admin";

        try (Connection connection =
                     DatabaseConnectionFactory.openConnection()) {

            connection.setAutoCommit(false);

            try {
                // Prevent duplicate administrator accounts.
                String checkSql =
                        "SELECT COUNT(*) FROM USERS WHERE LOWER(USERNAME) = ?";

                try (PreparedStatement check =
                             connection.prepareStatement(checkSql)) {

                    check.setString(1, username);

                    try (ResultSet result = check.executeQuery()) {
                        result.next();

                        if (result.getInt(1) > 0) {
                            connection.rollback();
                            System.out.println(
                                    "Administrator account already exists."
                            );
                            return;
                        }
                    }
                }

                // Find the Administrator role and David Mugisha's staff record.
                String lookupSql = """
                        SELECT r.ROLE_ID, s.STAFF_ID, s.EMAIL
                        FROM ROLES r
                        CROSS JOIN STAFF s
                        JOIN DEPARTMENTS d
                          ON d.DEPARTMENT_ID = s.DEPARTMENT_ID
                        WHERE r.ROLE_NAME = 'Administrator'
                          AND s.STAFF_NUMBER = 'SJCH001'
                          AND d.DEPARTMENT_NAME = 'Administration'
                        """;

                long roleId;
                long staffId;
                String email;

                try (PreparedStatement lookup =
                             connection.prepareStatement(lookupSql);
                     ResultSet result = lookup.executeQuery()) {

                    if (!result.next()) {
                        throw new SQLException(
                                "Administrator role or staff record not found."
                        );
                    }

                    roleId = result.getLong("ROLE_ID");
                    staffId = result.getLong("STAFF_ID");
                    email = result.getString("EMAIL");
                }

                // Hash the password before saving it.
                String passwordHash = PasswordHasher.hash(password);

                String insertSql = """
                        INSERT INTO USERS
                            (USERNAME, PASSWORD_HASH, ROLE_ID,
                             STAFF_ID, EMAIL, IS_ACTIVE)
                        VALUES (?, ?, ?, ?, ?, 'Y')
                        """;

                try (PreparedStatement insert =
                             connection.prepareStatement(insertSql)) {

                    insert.setString(1, username);
                    insert.setString(2, passwordHash);
                    insert.setLong(3, roleId);
                    insert.setLong(4, staffId);
                    insert.setString(5, email);

                    insert.executeUpdate();
                }

                connection.commit();

                System.out.println(
                        "Administrator account created successfully."
                );
                System.out.println("Username: admin");
                System.out.println("Staff member: David Mugisha");
                System.out.println("Password stored as a secure hash.");

            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }

        } catch (Exception exception) {
            System.out.println("Administrator account creation failed.");
            System.out.println("Reason: " + exception.getMessage());
        }
    }
}
