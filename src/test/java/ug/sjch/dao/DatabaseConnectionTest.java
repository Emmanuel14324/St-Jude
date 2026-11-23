package ug.sjch.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import ug.sjch.DatabaseConnection;
import ug.sjch.DatabaseConnection;

class DatabaseConnectionTest {
    @Test
    void connectsToSjchSchemaAndRunsSelect() throws Exception {
        assumeTrue(
                System.getenv("SJCH_DB_URL") != null
                        && System.getenv("SJCH_DB_USER") != null
                        && System.getenv("SJCH_DB_PASSWORD") != null,
                "Set SJCH_DB_URL, SJCH_DB_USER and SJCH_DB_PASSWORD before running database tests"
        );

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1 FROM DUAL")) {
            resultSet.next();
            assertEquals(1, resultSet.getInt(1));
        }
    }
}
