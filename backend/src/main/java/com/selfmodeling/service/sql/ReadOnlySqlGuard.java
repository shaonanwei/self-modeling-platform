package com.selfmodeling.service.sql;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.NextValExpression;
import net.sf.jsqlparser.expression.WindowDefinition;
import net.sf.jsqlparser.expression.WindowElement;
import net.sf.jsqlparser.expression.WindowOffset;
import net.sf.jsqlparser.expression.WindowRange;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Distinct;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.LateralView;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ReadOnlySqlGuard {

	private static final Set<String> FORBIDDEN_FUNCTIONS = Set.of(
			"benchmark", "sleep", "pg_sleep",
			"load_file", "readfile", "writefile", "load_extension", "pg_stat_file",
			"lo_import", "lo_export", "sys_eval", "sys_exec",
			"get_lock", "release_lock", "is_free_lock", "is_used_lock",
			"nextval", "currval", "setval", "lastval",
			"set_config", "setseed", "last_insert_id", "master_pos_wait",
			"pg_reload_conf", "pg_rotate_logfile", "pg_terminate_backend",
			"pg_cancel_backend", "pg_notify", "pg_logical_emit_message",
			"pg_create_restore_point", "pg_switch_wal", "pg_promote",
			"pg_export_snapshot", "openrowset", "opendatasource", "openquery");
	private static final List<String> FORBIDDEN_FUNCTION_PREFIXES = List.of(
			"pg_read_", "pg_ls_", "pg_file_", "pg_advisory_",
			"pg_replication_", "pg_backup_", "pg_wal_replay_", "dblink", "http_",
			"read_csv", "read_parquet", "read_json", "read_ndjson",
			"read_text", "read_blob", "read_xlsx");
	private static final List<String> FORBIDDEN_QUALIFIED_FUNCTION_COMPONENT_PREFIXES = List.of(
			"utl_", "dbms_");

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

		new CapabilityValidator().getTableList(parsedStatement);
        return normalized;
    }

	private static void rejectCapability() {
		throw new IllegalArgumentException("The SELECT uses a forbidden capability");
	}

	private static final class CapabilityValidator extends TablesNamesFinder {

		@Override
		public void visit(Select select) {
			if (select.getForClause() != null) {
				rejectCapability();
			}
			super.visit(select);
		}

		@Override
		public void visit(PlainSelect select) {
			if (select.getIntoTempTable() != null
					|| select.getIntoTables() != null && !select.getIntoTables().isEmpty()
					|| select.getForMode() != null
					|| select.getForUpdateTable() != null
					|| select.isNoWait() || select.isSkipLocked()) {
				rejectCapability();
			}
			visitWithItems(select.getWithItemsList());
			visitSelectItems(select.getSelectItems());
			visitDistinct(select.getDistinct());
			if (select.getFromItem() != null) {
				select.getFromItem().accept(this);
			}
			visitJoins(select.getJoins());
			visitExpression(select.getWhere());
			visitGroupBy(select);
			visitExpression(select.getHaving());
			visitExpression(select.getQualify());
			visitExpression(select.getOracleHierarchical());
			visitLateralViews(select.getLateralViews());
			if (select.getTop() != null) {
				visitExpression(select.getTop().getExpression());
			}
			visitWindowDefinitions(select.getWindowDefinitions());
			visitSelectTail(select);
		}

		@Override
		public void visit(SetOperationList select) {
			super.visit(select);
			visitSelectTail(select);
		}

		@Override
		public void visit(ParenthesedSelect select) {
			super.visit(select);
			visitSelectTail(select);
		}

		@Override
		public void visit(WithItem select) {
			super.visit(select);
			visitSelectTail(select);
		}

		@Override
		public void visit(Values select) {
			super.visit(select);
			visitSelectTail(select);
		}

		@Override
		public void visit(Function function) {
			String name;
			List<String> nameComponents;
			if (function.getMultipartName() != null
					&& !function.getMultipartName().isEmpty()) {
				name = function.getMultipartName().getLast();
				nameComponents = function.getMultipartName().stream()
						.map(CapabilityValidator::normalizeIdentifier)
						.toList();
			} else {
				name = function.getName();
				nameComponents = List.of(normalizeIdentifier(name));
			}
			if (name == null) {
				rejectCapability();
			}
			String normalizedName = normalizeIdentifier(name);
			if (FORBIDDEN_FUNCTIONS.contains(normalizedName)
					|| FORBIDDEN_FUNCTION_PREFIXES.stream()
					.anyMatch(normalizedName::startsWith)
					|| nameComponents.stream().anyMatch(component ->
							FORBIDDEN_QUALIFIED_FUNCTION_COMPONENT_PREFIXES.stream()
									.anyMatch(component::startsWith))) {
				rejectCapability();
			}
			super.visit(function);
		}

		@Override
		public void visit(NextValExpression nextValExpression) {
			rejectCapability();
		}

		private void visitWithItems(List<WithItem> withItems) {
			if (withItems == null) {
				return;
			}
			withItems.forEach(item -> item.accept(
					(net.sf.jsqlparser.statement.select.SelectVisitor) this));
		}

		private void visitSelectItems(List<SelectItem<?>> selectItems) {
			if (selectItems == null) {
				return;
			}
			selectItems.forEach(item -> item.accept(this));
		}

		private void visitDistinct(Distinct distinct) {
			if (distinct != null) {
				visitSelectItems(distinct.getOnSelectItems());
			}
		}

		private void visitJoins(List<Join> joins) {
			if (joins == null) {
				return;
			}
			for (Join join : joins) {
				if (join.getRightItem() != null) {
					join.getRightItem().accept(this);
				}
				if (join.getFromItem() != null) {
					join.getFromItem().accept(this);
				}
				if (join.getOnExpressions() != null) {
					join.getOnExpressions().forEach(this::visitExpression);
				}
			}
		}

		private void visitGroupBy(PlainSelect select) {
			if (select.getGroupBy() == null) {
				return;
			}
			visitExpressionList(select.getGroupBy().getGroupByExpressionList());
			if (select.getGroupBy().getGroupingSets() != null) {
				select.getGroupBy().getGroupingSets().forEach(this::visitExpressionList);
			}
		}

		private void visitLateralViews(List<LateralView> lateralViews) {
			if (lateralViews == null) {
				return;
			}
			lateralViews.forEach(view -> visitExpression(view.getGeneratorFunction()));
		}

		private void visitWindowDefinitions(List<WindowDefinition> definitions) {
			if (definitions == null) {
				return;
			}
			for (WindowDefinition definition : definitions) {
				visitOrderBy(definition.getOrderByElements());
				visitExpressionList(definition.getPartitionExpressionList());
				visitWindowElement(definition.getWindowElement());
			}
		}

		private void visitWindowElement(WindowElement element) {
			if (element == null) {
				return;
			}
			visitWindowOffset(element.getOffset());
			WindowRange range = element.getRange();
			if (range != null) {
				visitWindowOffset(range.getStart());
				visitWindowOffset(range.getEnd());
			}
		}

		private void visitWindowOffset(WindowOffset offset) {
			if (offset != null) {
				visitExpression(offset.getExpression());
			}
		}

		private void visitSelectTail(Select select) {
			visitOrderBy(select.getOrderByElements());
			if (select.getLimit() != null) {
				visitExpression(select.getLimit().getOffset());
				visitExpression(select.getLimit().getRowCount());
				visitExpressionList(select.getLimit().getByExpressions());
			}
			if (select.getLimitBy() != null) {
				visitExpression(select.getLimitBy().getOffset());
				visitExpression(select.getLimitBy().getRowCount());
				visitExpressionList(select.getLimitBy().getByExpressions());
			}
			if (select.getOffset() != null) {
				visitExpression(select.getOffset().getOffset());
			}
			if (select.getFetch() != null) {
				visitExpression(select.getFetch().getExpression());
			}
		}

		private void visitOrderBy(List<OrderByElement> elements) {
			if (elements == null) {
				return;
			}
			elements.forEach(element -> visitExpression(element.getExpression()));
		}

		private void visitExpressionList(ExpressionList<?> expressions) {
			if (expressions != null) {
				expressions.accept(this);
			}
		}

		private void visitExpression(Expression expression) {
			if (expression != null) {
				expression.accept(this);
			}
		}

		private static String normalizeIdentifier(String value) {
			if (value == null) {
				return "";
			}
			return value.replace("\"", "")
					.replace("`", "")
					.replace("[", "")
					.replace("]", "")
					.toLowerCase(Locale.ROOT);
		}
	}
}
