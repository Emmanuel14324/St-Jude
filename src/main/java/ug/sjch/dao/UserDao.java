package ug.sjch.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import ug.sjch.model.User;

public class UserDao {
    public record AuthUser(User user, String passwordHash) {
    }

    public AuthUser findByUsername(String username) throws SQLException {
        String sql = """
                SELECT u.user_id, u.username, u.password_hash, u.is_active,
                       u.staff_id, r.role_name
                FROM USERS u
                JOIN ROLES r ON r.role_id = u.role_id
                WHERE LOWER(u.username) = LOWER(?)
                """;

        try (Connection connection = DatabaseConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                boolean active = "Y".equalsIgnoreCase(
                        resultSet.getString("is_active")
                );
                long staffIdValue = resultSet.getLong("staff_id");
                Long staffId = resultSet.wasNull() ? null : staffIdValue;
                User user = new User(
                        resultSet.getLong("user_id"),
                        resultSet.getString("username"),
                        resultSet.getString("role_name"),
                        active,
                        staffId
                );
                return new AuthUser(user, resultSet.getString("password_hash"));
            }
        }
    }
}
