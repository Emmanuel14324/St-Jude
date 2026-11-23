package ug.sjch.security;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH = 256;

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] digest = derive(password, salt, ITERATIONS);
        return "PBKDF2_SHA256:" + ITERATIONS + ":" +
                Base64.getEncoder().encodeToString(salt) + ":" +
                Base64.getEncoder().encodeToString(digest);
    }

    public static boolean verify(String password, String encodedHash) {
        try {
            String[] parts = encodedHash.split(":");
            if (parts.length != 4 || !"PBKDF2_SHA256".equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            int difference = expected.length ^ actual.length;
            for (int i = 0; i < expected.length && i < actual.length; i++) {
                difference |= expected[i] ^ actual[i];
            }
            return difference == 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, iterations, KEY_LENGTH
            );
            return SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to process password", exception);
        }
    }
}
