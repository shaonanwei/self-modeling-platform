package com.selfmodeling.dto;

/**
 * 数据源信息 DTO
 * 用于返回可用数据源列表及其状态
 */
public class DataSourceInfo {

    private String dataSourceId;
    private String dataSourceName;
    private String type; // POSTGRESQL, HIVE, SQLITE
    private String url;
    private boolean connected;
    private String statusMessage;
    private Long lastCheckTime;

    public DataSourceInfo() {
    }

    public DataSourceInfo(String dataSourceId, String dataSourceName, String type) {
        this.dataSourceId = dataSourceId;
        this.dataSourceName = dataSourceName;
        this.type = type;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Long getLastCheckTime() {
        return lastCheckTime;
    }

    public void setLastCheckTime(Long lastCheckTime) {
        this.lastCheckTime = lastCheckTime;
    }
}
