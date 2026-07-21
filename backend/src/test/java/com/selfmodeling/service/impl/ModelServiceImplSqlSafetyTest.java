package com.selfmodeling.service.impl;

import com.selfmodeling.entity.ModelInfo;
import com.selfmodeling.entity.ModelStep;
import com.selfmodeling.mapper.ModelInfoMapper;
import com.selfmodeling.mapper.ModelStepMapper;
import com.selfmodeling.service.sql.ReadOnlySqlGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelServiceImplSqlSafetyTest {

    private static final long MODEL_ID = 10L;
    private static final long STEP_ID = 20L;

    @Mock
    private ModelStepMapper stepMapper;
    @Mock
    private ModelInfoMapper modelInfoMapper;
    @Spy
    private ReadOnlySqlGuard readOnlySqlGuard = new ReadOnlySqlGuard();

    @InjectMocks
    private ModelServiceImpl modelService;

    @Test
    void rejectsUnsafeSqlWhenUpdatingAStep() {
        when(stepMapper.selectById(STEP_ID)).thenReturn(existingStep("SELECT 1"));

        assertThrows(IllegalArgumentException.class,
                () -> modelService.updateStep(MODEL_ID, STEP_ID, updateWithSql("DELETE FROM sys_user")));

        verify(stepMapper, never()).updateStepById(any(), any(), any(), any(), any(), any());
    }

    @Test
    void normalizesReadOnlySqlWhenUpdatingAStep() {
        when(stepMapper.selectById(STEP_ID)).thenReturn(existingStep("SELECT 1"));

        modelService.updateStep(MODEL_ID, STEP_ID, updateWithSql("  SELECT 1;  "));

        verify(stepMapper).updateStepById(
                eq(STEP_ID), eq("step"), eq("task"), eq("description"), eq("{}"), eq("SELECT 1"));
    }

    @Test
    void rejectsUnsafeSqlBeforeStartingStepExecution() {
        ModelInfo model = new ModelInfo();
        model.setId(MODEL_ID);
        model.setDataSource("missing");
        when(modelInfoMapper.selectById(MODEL_ID)).thenReturn(model);
        when(stepMapper.selectById(STEP_ID)).thenReturn(existingStep("TRUNCATE TABLE audit_log"));

        assertThrows(IllegalArgumentException.class,
                () -> modelService.executeStep(MODEL_ID, STEP_ID));

        verify(stepMapper, never()).updateExecuteStart(eq(STEP_ID), any());
    }

    private ModelStep existingStep(String sql) {
        ModelStep step = new ModelStep();
        step.setId(STEP_ID);
        step.setModelId(MODEL_ID);
        step.setSqlStatement(sql);
        return step;
    }

    private ModelStep updateWithSql(String sql) {
        ModelStep step = new ModelStep();
        step.setStepName("step");
        step.setStepType("task");
        step.setStepDesc("description");
        step.setStepConfig("{\"configType\":\"SQL\",\"sqlStatement\":\"" + sql + "\"}");
        return step;
    }
}
