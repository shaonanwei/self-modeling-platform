package com.selfmodeling.entity;

import java.time.LocalDateTime;

public class ModelStep {
    private Long id;
    private Long modelId;
    private String stepCode;
    private String stepName;
    private String stepDesc;
    private String stepType;
    private Integer sortOrder;
    private String stepConfig;
    private String sqlStatement;
    private String resultTableName;
    private String executeStatus;
    private LocalDateTime executeStartTime;
    private LocalDateTime executeEndTime;
    private String executeLog;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public ModelStep() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public String getStepDesc() { return stepDesc; }
    public void setStepDesc(String stepDesc) { this.stepDesc = stepDesc; }

    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStepConfig() { return stepConfig; }
    public void setStepConfig(String stepConfig) { this.stepConfig = stepConfig; }

    public String getSqlStatement() { return sqlStatement; }
    public void setSqlStatement(String sqlStatement) { this.sqlStatement = sqlStatement; }

    public String getResultTableName() { return resultTableName; }
    public void setResultTableName(String resultTableName) { this.resultTableName = resultTableName; }

    public String getExecuteStatus() { return executeStatus; }
    public void setExecuteStatus(String executeStatus) { this.executeStatus = executeStatus; }

    public LocalDateTime getExecuteStartTime() { return executeStartTime; }
    public void setExecuteStartTime(LocalDateTime executeStartTime) { this.executeStartTime = executeStartTime; }

    public LocalDateTime getExecuteEndTime() { return executeEndTime; }
    public void setExecuteEndTime(LocalDateTime executeEndTime) { this.executeEndTime = executeEndTime; }

    public String getExecuteLog() { return executeLog; }
    public void setExecuteLog(String executeLog) { this.executeLog = executeLog; }

    public LocalDateTime getCreateTime() { return createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
