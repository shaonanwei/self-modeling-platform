package com.selfmodeling.service.impl;

import com.selfmodeling.service.MetadataService;
import com.selfmodeling.service.sql.ReadOnlySqlGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlServiceImplExecutionTest {

    @Mock
    private MetadataService metadataService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private Statement statement;
    @Mock
    private ResultSet resultSet;
    @Mock
    private ResultSetMetaData resultSetMetaData;
    @Spy
    private ReadOnlySqlGuard readOnlySqlGuard = new ReadOnlySqlGuard();

    @InjectMocks
    private SqlServiceImpl sqlService;

    @Test
    void executesOriginalSqlWithReadOnlyConnectionAndHardBounds() throws Exception {
        when(metadataService.getJdbcTemplateByDataSourceId("master")).thenReturn(jdbcTemplate);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
        when(resultSetMetaData.getColumnCount()).thenReturn(0);
        when(resultSet.next()).thenReturn(false);

        Map<String, Object> result = sqlService.executeQuery("SELECT 1", 5000, "master");

        assertEquals(true, result.get("success"));
        verify(connection).setReadOnly(true);
        verify(statement).setMaxRows(1000);
        verify(statement).setQueryTimeout(60);
        verify(statement).executeQuery("SELECT 1");
    }

    @Test
    void rejectsUnsafeSqlBeforeLookingUpADataSource() {
        Map<String, Object> executeResult =
                sqlService.executeQuery("DELETE FROM sys_user", 50, "master");
        Map<String, Object> validateResult =
                sqlService.validateSql("SELECT 1; DELETE FROM sys_user", "master");

        assertEquals(false, executeResult.get("success"));
        assertEquals(false, validateResult.get("valid"));
        verifyNoInteractions(metadataService);
    }
}
