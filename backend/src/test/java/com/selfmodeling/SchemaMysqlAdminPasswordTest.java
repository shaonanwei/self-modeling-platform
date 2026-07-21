package com.selfmodeling;

import cn.hutool.crypto.digest.BCrypt;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMysqlAdminPasswordTest {

    private static final Pattern ADMIN_PASSWORD = Pattern.compile(
            "VALUES\\s*\\(1,\\s*'admin',\\s*'([^']+)'",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    void seedsDevelopmentAdminWithBcryptHash() throws IOException {
        String schema;
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/schema-mysql.sql"))) {
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher matcher = ADMIN_PASSWORD.matcher(schema);
        assertTrue(matcher.find(), "schema must seed the development admin account");

        String storedPassword = matcher.group(1);
        assertNotEquals("admin123", storedPassword, "schema must not store a plaintext password");
        assertTrue(storedPassword.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}"),
                "seeded password must use BCrypt format");
        assertTrue(BCrypt.checkpw("admin123", storedPassword),
                "seeded BCrypt hash must match the documented development password");
    }
}
