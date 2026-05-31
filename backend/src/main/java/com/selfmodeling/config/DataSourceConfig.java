package com.selfmodeling.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 动态数据源配置
 * 使用 dynamic-datasource-spring-boot-starter
 */
@Component
public class DataSourceConfig implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    /**
     * 获取所有数据源名称
     *
     * @return 数据源名称列表
     */
    public static Map<String, DataSource> getDataSources() {
        try {
            DynamicRoutingDataSource dataSource = applicationContext.getBean(DynamicRoutingDataSource.class);
            return dataSource.getDataSources();
        } catch (Exception e) {
            log.warn("获取动态数据源失败", e);
            return Map.of();
        }
    }

    /**
     * 根据数据源名称获取 JdbcTemplate
     *
     * @param dataSourceName 数据源名称
     * @return JdbcTemplate，如果失败则返回 null
     */
    public static org.springframework.jdbc.core.JdbcTemplate getJdbcTemplate(String dataSourceName) {
        try {
            DynamicRoutingDataSource dataSource = applicationContext.getBean(DynamicRoutingDataSource.class);
            log.info("getJdbcTemplate: requesting dataSourceName={}, available dataSources={}", dataSourceName, dataSource.getDataSources().keySet());
            DataSource ds = dataSource.getDataSource(dataSourceName);
            if (ds == null) {
                log.warn("数据源 {} 不存在，可用数据源: {}", dataSourceName, dataSource.getDataSources().keySet());
                return null;
            }
            log.info("getJdbcTemplate: resolved dataSourceName={} to DataSource={}", dataSourceName, ds.getClass().getName());
            return new org.springframework.jdbc.core.JdbcTemplate(ds);
        } catch (Exception e) {
            log.warn("获取数据源 {} 失败", dataSourceName, e);
            return null;
        }
    }
}
