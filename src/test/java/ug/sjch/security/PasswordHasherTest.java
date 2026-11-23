package ug.sjch.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    @Test
    void acceptsMatchingPasswordAndRejectsWrongPassword() {
        String hash = PasswordHasher.hash("TestPassword!2026");

        assertTrue(PasswordHasher.verify("TestPassword!2026", hash));
        assertFalse(PasswordHasher.verify("WrongPassword", hash));
    }
}
