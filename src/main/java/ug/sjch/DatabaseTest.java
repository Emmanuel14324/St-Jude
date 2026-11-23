
package ug.sjch;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseTest {

    public static void main(String[] args) {

        System.out.println("Testing SJCH Oracle Database connection...");

        try (Connection connection = DatabaseConnection.getConnection()) {

            System.out.println("==================================");
            System.out.println("DATABASE CONNECTION SUCCESSFUL!");
            System.out.println("Database user: "
                    + connection.getMetaData().getUserName());
            System.out.println("Database: "
                    + connection.getMetaData().getDatabaseProductName());
            System.out.println("==================================");

        } catch (SQLException e) {

            System.out.println("DATABASE CONNECTION FAILED!");
            System.out.println("Error code: " + e.getErrorCode());
            System.out.println("SQL State: " + e.getSQLState());
            System.out.println("Error message: " + e.getMessage());

            e.printStackTrace();
        }
    }
}
