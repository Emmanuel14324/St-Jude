package ug.sjch.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import ug.sjch.config.DatabaseConfig;

public final class DatabaseConnectionFactory {
    private DatabaseConnectionFactory() {
    }

    public static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                DatabaseConfig.getUrl(),
                DatabaseConfig.getUsername(),
                DatabaseConfig.getPassword()
        );
    }
}
