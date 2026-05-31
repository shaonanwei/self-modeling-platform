package com.selfmodeling.dto;

import java.util.List;

/**
 * 智能推荐结果
 */
public class SmartRecommendResult {
    private List<RelationRecommend> relations;
    private List<AggregateRecommend> aggregates;
    private List<ConditionRecommend> conditions;

    public SmartRecommendResult() {}

    public List<RelationRecommend> getRelations() { return relations; }
    public void setRelations(List<RelationRecommend> relations) { this.relations = relations; }

    public List<AggregateRecommend> getAggregates() { return aggregates; }
    public void setAggregates(List<AggregateRecommend> aggregates) { this.aggregates = aggregates; }

    public List<ConditionRecommend> getConditions() { return conditions; }
    public void setConditions(List<ConditionRecommend> conditions) { this.conditions = conditions; }

    /**
     * 关联推荐
     */
    public static class RelationRecommend {
        private String sourceTable;
        private String sourceField;
        private String targetTable;
        private String targetField;
        private String recommendType;
        private double confidence;

        public RelationRecommend() {}

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }

        public String getSourceField() { return sourceField; }
        public void setSourceField(String sourceField) { this.sourceField = sourceField; }

        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }

        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }

        public String getRecommendType() { return recommendType; }
        public void setRecommendType(String recommendType) { this.recommendType = recommendType; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    /**
     * 聚合推荐
     */
    public static class AggregateRecommend {
        private String fieldName;
        private String fieldType;
        private List<String> recommendedFunctions;

        public AggregateRecommend() {}

        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }

        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }

        public List<String> getRecommendedFunctions() { return recommendedFunctions; }
        public void setRecommendedFunctions(List<String> recommendedFunctions) { this.recommendedFunctions = recommendedFunctions; }
    }

    /**
     * 条件推荐
     */
    public static class ConditionRecommend {
        private String fieldName;
        private String fieldType;
        private List<String> recommendedOperators;
        private List<Object> suggestedValues;

        public ConditionRecommend() {}

        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }

        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }

        public List<String> getRecommendedOperators() { return recommendedOperators; }
        public void setRecommendedOperators(List<String> recommendedOperators) { this.recommendedOperators = recommendedOperators; }

        public List<Object> getSuggestedValues() { return suggestedValues; }
        public void setSuggestedValues(List<Object> suggestedValues) { this.suggestedValues = suggestedValues; }
    }
}
