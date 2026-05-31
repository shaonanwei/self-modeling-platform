package com.selfmodeling.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * WHERE 条件配置
 * 支持嵌套的 AND/OR 逻辑组合
 */
public class WhereCondition {
    private String logic;
    private List<ConditionItem> conditions;
    private List<WhereCondition> groups;

    public WhereCondition() {
        this.logic = "AND";
        this.conditions = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    public String getLogic() { return logic; }
    public void setLogic(String logic) { this.logic = logic; }

    public List<ConditionItem> getConditions() { return conditions; }
    public void setConditions(List<ConditionItem> conditions) { this.conditions = conditions; }

    public List<WhereCondition> getGroups() { return groups; }
    public void setGroups(List<WhereCondition> groups) { this.groups = groups; }

    /**
     * 单个条件项
     */
    public static class ConditionItem {
        private String field;
        private String operator;
        private Object value;

        public ConditionItem() {}

        public ConditionItem(String field, String operator, Object value) {
            this.field = field;
            this.operator = operator;
            this.value = value;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }
}
