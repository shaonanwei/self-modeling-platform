package com.selfmodeling.service.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadOnlySqlGuardTest {

    private final ReadOnlySqlGuard guard = new ReadOnlySqlGuard();

    @Test
    void acceptsPlainSelect() {
        assertEquals("SELECT 1", guard.validate(" SELECT 1; "));
    }

    @Test
    void acceptsCommonTableExpressionSelect() {
        assertEquals("WITH x AS (SELECT 1 AS id) SELECT id FROM x",
                guard.validate("WITH x AS (SELECT 1 AS id) SELECT id FROM x"));
    }

    @Test
    void rejectsMutation() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("DELETE FROM sys_user"));
    }

    @Test
    void rejectsStackedStatements() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT 1; DELETE FROM sys_user"));
    }

    @Test
    void rejectsFileAndDelayFunctions() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT LOAD_FILE('/etc/passwd')"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT SLEEP(10)"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT pg_sleep(10)"));
    }

    @Test
    void rejectsForbiddenFunctionsWhenCommentsSplitTheCall() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT LOAD_FILE/**/('/etc/passwd')"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate("SELECT SLEEP/**/(10)"));
    }
}
