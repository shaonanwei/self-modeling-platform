package com.selfmodeling.entity;

import java.time.LocalDateTime;

public class ModelInfo {
    private Long id;
    private String modelCode;
    private String modelName;
    private String modelDesc;
    private String modelType;
    private String dataSource;
    private Integer status;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Integer deleted;

    public ModelInfo() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelDesc() { return modelDesc; }
    public void setModelDesc(String modelDesc) { this.modelDesc = modelDesc; }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
