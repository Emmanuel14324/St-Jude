package ug.sjch.security;

import ug.sjch.model.User;

public final class UserSession {
    private static User currentUser;

    private UserSession() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isAuthenticated() {
        return currentUser != null && currentUser.active();
    }

    public static boolean hasRole(String roleName) {
        return isAuthenticated()
                && roleName.equalsIgnoreCase(currentUser.roleName());
    }

    public static void clear() {
        currentUser = null;
    }
}
