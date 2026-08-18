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

	@Test
	void rejectsExternalIoAndStateChangingFunctions() {
		String[] dangerousSql = {
				"SELECT pg_read_file('/etc/passwd')",
				"SELECT pg_catalog.pg_read_file('/etc/passwd')",
				"WITH leaked AS (SELECT pg_read_file('/etc/passwd')) SELECT * FROM leaked",
				"SELECT 1 UNION ALL SELECT pg_read_file('/etc/passwd')",
				"SELECT pg_read_binary_file('/etc/passwd')",
				"SELECT pg_stat_file('/etc/passwd')",
				"SELECT load_file('/etc/passwd')",
				"SELECT readfile('/etc/passwd')",
				"SELECT writefile('/tmp/result', 'secret')",
				"SELECT load_extension('unsafe')",
				"SELECT dblink_connect('secret')",
				"SELECT http_get('https://example.invalid')",
				"SELECT utl_http.request('https://example.invalid') FROM dual",
				"SELECT sys.utl_http.request('https://example.invalid') FROM dual",
				"SELECT * FROM read_csv_auto('https://example.invalid/data.csv')",
				"SELECT pg_advisory_lock(1)",
				"SELECT pg_advisory_unlock(1)",
				"SELECT pg_terminate_backend(123)",
				"SELECT nextval('orders_id_seq')",
				"SELECT setval('orders_id_seq', 10)",
				"SELECT get_lock('orders', 10)",
				"SELECT release_lock('orders')"
		};

		for (String sql : dangerousSql) {
			assertThrows(IllegalArgumentException.class, () -> guard.validate(sql), sql);
		}
	}

	@Test
	void rejectsSelectIntoLockingAndDataModifyingCteCapabilities() {
		String[] dangerousSql = {
				"SELECT id INTO archived_orders FROM orders",
				"SELECT id FROM orders INTO OUTFILE '/tmp/orders.csv'",
				"SELECT id FROM orders INTO DUMPFILE '/tmp/orders.bin'",
				"SELECT id FROM orders FOR UPDATE",
				"SELECT id FROM orders FOR SHARE",
				"SELECT id FROM orders LOCK IN SHARE MODE",
				"WITH changed AS (DELETE FROM orders RETURNING id) SELECT id FROM changed",
				"WITH changed AS (UPDATE orders SET status = 'done' RETURNING id) SELECT id FROM changed",
				"WITH changed AS (INSERT INTO audit_log(id) VALUES (1) RETURNING id) SELECT id FROM changed"
		};

		for (String sql : dangerousSql) {
			assertThrows(IllegalArgumentException.class, () -> guard.validate(sql), sql);
		}
	}

	@Test
	void doesNotTreatCommentsOrStringLiteralsAsCapabilities() {
		assertEquals("SELECT 'pg_read_file(' AS note",
				guard.validate("SELECT 'pg_read_file(' AS note"));
		assertEquals("SELECT 1 /* FOR UPDATE pg_advisory_lock( */",
				guard.validate("SELECT 1 /* FOR UPDATE pg_advisory_lock( */"));
	}

	@Test
	void rejectsDangerousFunctionsInGroupingAndOrderingExpressions() {
		assertThrows(IllegalArgumentException.class,
				() -> guard.validate("SELECT category, COUNT(*) FROM orders "
						+ "GROUP BY pg_read_file('/etc/passwd')"));
		assertThrows(IllegalArgumentException.class,
				() -> guard.validate("SELECT id FROM orders "
						+ "ORDER BY pg_read_file('/etc/passwd')"));
	}

	@Test
	void acceptsOrdinaryGroupingAndOrderingExpressions() {
		String sql = "SELECT category, COUNT(*) FROM orders "
				+ "GROUP BY category ORDER BY COUNT(*) DESC";
		assertEquals(sql, guard.validate(sql));
	}
}
