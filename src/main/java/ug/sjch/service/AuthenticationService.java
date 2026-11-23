package ug.sjch.service;

import java.sql.SQLException;

import ug.sjch.dao.UserDao;
import ug.sjch.model.User;
import ug.sjch.security.PasswordHasher;
import ug.sjch.security.UserSession;

public class AuthenticationService {
    private final UserDao userDao;

    public AuthenticationService() {
        this(new UserDao());
    }

    AuthenticationService(UserDao userDao) {
        this.userDao = userDao;
    }

    public User authenticate(String username, String password)
            throws SQLException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Enter your username.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Enter your password.");
        }

        UserDao.AuthUser authUser = userDao.findByUsername(username.trim());
        if (authUser == null
                || !authUser.user().active()
                || !PasswordHasher.verify(password, authUser.passwordHash())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        UserSession.setCurrentUser(authUser.user());
        return authUser.user();
    }
}
