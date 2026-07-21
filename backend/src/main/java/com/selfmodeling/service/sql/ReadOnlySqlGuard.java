package com.selfmodeling.service.sql;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ReadOnlySqlGuard {

    private static final Pattern FORBIDDEN_SELECT_CAPABILITIES = Pattern.compile(
            "(?is)\\b(INTO\\s+(OUTFILE|DUMPFILE)|LOAD_FILE\\s*\\(|SLEEP\\s*\\(|"
                    + "BENCHMARK\\s*\\(|PG_SLEEP\\s*\\()"
    );

    public String validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }

        String normalized = sql.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        final Statement parsedStatement;
        try {
            Statements parsed = CCJSqlParserUtil.parseStatements(normalized);
            if (parsed.size() != 1 || !(parsed.get(0) instanceof Select)) {
                throw new IllegalArgumentException("Exactly one SELECT statement is required");
            }
            parsedStatement = parsed.get(0);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("SQL syntax is invalid", e);
        }

        String upper = (normalized + "\n" + parsedStatement).toUpperCase(Locale.ROOT);
        if (FORBIDDEN_SELECT_CAPABILITIES.matcher(upper).find()) {
            throw new IllegalArgumentException("The SELECT uses a forbidden capability");
        }
        return normalized;
    }
}
