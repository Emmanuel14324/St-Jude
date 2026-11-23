package ug.sjch;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String DB_URL =
            System.getenv("SJCH_DB_URL");

    private static final String DB_USER =
            System.getenv("SJCH_DB_USER");

    private static final String DB_PASSWORD =
            System.getenv("SJCH_DB_PASSWORD");

    public static Connection getConnection() throws SQLException {

        if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
            throw new SQLException(
                    "Database credentials are missing. Check SJCH_DB_URL, SJCH_DB_USER and SJCH_DB_PASSWORD."
            );
        }

        return DriverManager.getConnection(
                DB_URL,
                DB_USER,
                DB_PASSWORD
        );
    }

    public static void testConnection() throws SQLException {
        try (Connection connection = getConnection()) {
            System.out.println("=================================");
            System.out.println("SJCH DATABASE CONNECTION SUCCESS!");
            System.out.println("Connected database user: "
                    + connection.getMetaData().getUserName());
            System.out.println("=================================");
        }
    }
}
