package ug.sjch.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class OracleSchemaInspector {
    private OracleSchemaInspector() {
    }

    static boolean tableExists(Connection connection, String tableName)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_tables WHERE table_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    static boolean columnExists(Connection connection, String tableName,
                                String columnName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM user_tab_columns
                WHERE table_name = ?
                  AND column_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }
}
