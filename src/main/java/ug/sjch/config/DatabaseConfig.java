package ug.sjch.config;

import java.util.Map;

public final class DatabaseConfig {

    private static final String URL_ENV = "SJCH_DB_URL";
    private static final String USER_ENV = "SJCH_DB_USER";
    private static final String PASSWORD_ENV = "SJCH_DB_PASSWORD";

    private DatabaseConfig() {
    }

    public static String getUrl() {
        return getRequiredEnvironmentVariable(URL_ENV);
    }

    public static String getUsername() {
        return getRequiredEnvironmentVariable(USER_ENV);
    }

    public static String getPassword() {
        return getRequiredEnvironmentVariable(PASSWORD_ENV);
    }

    private static String getRequiredEnvironmentVariable(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing environment variable: " + name
            );
        }

        return value;
    }

    public static boolean isConfigured() {
        return System.getenv(URL_ENV) != null
                && System.getenv(USER_ENV) != null
                && System.getenv(PASSWORD_ENV) != null;
    }

    public static Map<String, String> testConnectionDetailsWithoutPassword() {
        return Map.of(
                "url", getUrl(),
                "username", getUsername(),
                "passwordConfigured",
                Boolean.toString(isConfigured())
        );
    }
}
