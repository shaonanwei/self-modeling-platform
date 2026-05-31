package com.selfmodeling.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.selfmodeling.dto.PageResult;
import com.selfmodeling.dto.StepTreeResult;
import com.selfmodeling.entity.ModelInfo;
import com.selfmodeling.entity.ModelStep;
import com.selfmodeling.mapper.ModelInfoMapper;
import com.selfmodeling.mapper.ModelStepMapper;
import com.selfmodeling.request.InsertStepRequest;
import com.selfmodeling.service.ModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * ModelService 实现类
 */
@Service
public class ModelServiceImpl implements ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelServiceImpl.class);
    private static final int STEP_INTERVAL = 1000;

    @Autowired
    private ModelStepMapper stepMapper;

    @Autowired
    private ModelInfoMapper modelInfoMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageResult<ModelInfo> pageModels(int pageNum, int pageSize, String modelName, Integer status,
                                             LocalDateTime startDate, LocalDateTime endDate) {
        PageHelper.startPage(pageNum, pageSize);
        List<ModelInfo> list = modelInfoMapper.selectList();

        List<ModelInfo> filteredList = new ArrayList<>();
        for (ModelInfo model : list) {
            if (StrUtil.isNotBlank(modelName) && !model.getModelName().contains(modelName)) {
                continue;
            }
            if (status != null && !status.equals(model.getStatus())) {
                continue;
            }
            if (startDate != null && model.getCreateTime() != null && model.getCreateTime().isBefore(startDate)) {
                continue;
            }
            if (endDate != null && model.getCreateTime() != null && model.getCreateTime().isAfter(endDate)) {
                continue;
            }
            filteredList.add(model);
        }

        PageInfo<ModelInfo> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), filteredList);
    }

    @Override
    public ModelInfo getModelDetail(Long id) {
        ModelInfo modelInfo = modelInfoMapper.selectById(id);
        if (modelInfo == null) {
            throw new RuntimeException("建模不存在");
        }
        return modelInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelInfo createModel(ModelInfo modelInfo) {
        modelInfo.setVersion(1);
        modelInfo.setStatus(modelInfo.getStatus() != null ? modelInfo.getStatus() : 1);
        
        String creator = modelInfo.getCreator() != null ? modelInfo.getCreator() : "admin";
        String updater = modelInfo.getUpdater() != null ? modelInfo.getUpdater() : "admin";
        String dataSource = modelInfo.getDataSource() != null ? modelInfo.getDataSource() : "master";
        modelInfo.setCreator(creator);
        modelInfo.setUpdater(updater);
        modelInfo.setDataSource(dataSource);
        
        // 先设置一个临时编码
        String tempCode = "TEMP_" + System.currentTimeMillis();
        modelInfo.setModelCode(tempCode);
        
        // 使用 native SQL 插入
        modelInfoMapper.insert(modelInfo);
        
        // 生成正式编码
        String newCode = "MODEL" + modelInfo.getId();
        modelInfoMapper.updateCodeById(modelInfo.getId(), newCode);
        modelInfo.setModelCode(newCode);
        
        log.info("创建模型成功：id={}, code={}", modelInfo.getId(), modelInfo.getModelCode());
        return modelInfo;
    }

    @Override
    public void updateModel(Long id, ModelInfo modelInfo) {
        ModelInfo existing = modelInfoMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("建模不存在");
        }
        modelInfo.setId(id);
        modelInfo.setVersion(existing.getVersion() + 1);
        modelInfoMapper.updateById(modelInfo);
        log.info("更新模型成功：id={}, version={}", id, modelInfo.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long id) {
        modelInfoMapper.deleteById(id, "admin");
        stepMapper.deleteByModelId(id);
        log.info("删除模型成功：id={}", id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        ModelInfo modelInfo = modelInfoMapper.selectById(id);
        if (modelInfo == null) {
            throw new RuntimeException("建模不存在");
        }
        modelInfo.setStatus(status);
        modelInfo.setUpdater("admin");
        modelInfoMapper.updateById(modelInfo);
        log.info("更新模型状态：id={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelInfo copyModel(Long id) {
        ModelInfo original = modelInfoMapper.selectById(id);
        if (original == null) {
            throw new RuntimeException("建模不存在");
        }

        ModelInfo copy = new ModelInfo();
        copy.setModelName(original.getModelName() + " (副本)");
        copy.setModelDesc(original.getModelDesc());
        copy.setModelType(original.getModelType());
        copy.setDataSource(original.getDataSource() != null ? original.getDataSource() : "master");
        copy.setStatus(original.getStatus());
        copy.setCreator("admin");
        copy.setUpdater("admin");
        
        // 先设置一个临时编码
        String tempCode = "TEMP_COPY_" + System.currentTimeMillis();
        copy.setModelCode(tempCode);
        
        modelInfoMapper.insert(copy);
        
        String finalCode = original.getModelCode() + "_COPY_" + copy.getId();
        modelInfoMapper.updateCodeById(copy.getId(), finalCode);
        copy.setModelCode(finalCode);

        List<ModelStep> steps = stepMapper.selectByModelId(id);
        if (!steps.isEmpty()) {
            long baseTimestamp = System.currentTimeMillis();
            for (int i = 0; i < steps.size(); i++) {
                ModelStep step = steps.get(i);
                ModelStep newStep = new ModelStep();
                newStep.setModelId(copy.getId());
                newStep.setStepCode("STEP_" + copy.getId() + "_" + (baseTimestamp + i));
                newStep.setStepName(step.getStepName());
                newStep.setStepDesc(step.getStepDesc());
                newStep.setStepType(step.getStepType());
                newStep.setSortOrder(step.getSortOrder());
                newStep.setStepConfig(step.getStepConfig());
                newStep.setSqlStatement(step.getSqlStatement());
                
                stepMapper.insert(newStep);
            }
            log.info("复制模型步骤：count={}", steps.size());
        }

        log.info("复制模型成功：originalId={}, copyId={}", id, copy.getId());
        return copy;
    }

    @Override
    public List<ModelStep> getStepsByModelId(Long modelId) {
        return stepMapper.selectByModelId(modelId);
    }

    @Override
    public ModelStep getStepDetail(Long modelId, Long stepId) {
        ModelStep step = stepMapper.selectById(stepId);
        if (step == null || !step.getModelId().equals(modelId)) {
            throw new RuntimeException("步骤不存在");
        }
        return step;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelStep addStep(Long modelId, ModelStep step) {
        if (StrUtil.isBlank(step.getStepCode())) {
            step.setStepCode(generateStepCode(modelId));
        }

        step.setModelId(modelId);
        
        if (step.getSortOrder() == null || step.getSortOrder() == 0) {
            List<ModelStep> steps = stepMapper.selectByModelId(modelId);
            if (steps.isEmpty()) {
                step.setSortOrder(STEP_INTERVAL);
            } else {
                int maxSortOrder = steps.stream().mapToInt(ModelStep::getSortOrder).max().orElse(0);
                step.setSortOrder(maxSortOrder + STEP_INTERVAL);
            }
        }

        extractAndSetConfigFields(step);

        stepMapper.insert(step);
        
        log.info("添加步骤成功：modelId={}, stepId={}, name={}", modelId, step.getId(), step.getStepName());
        return step;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelStep insertStep(Long modelId, InsertStepRequest request) {
        ModelStep afterStep = stepMapper.selectById(request.getAfterStepId());
        if (afterStep == null || !afterStep.getModelId().equals(modelId)) {
            throw new RuntimeException("插入位置的前一步骤不存在");
        }

        List<ModelStep> allSteps = stepMapper.selectByModelId(modelId);
        ModelStep beforeStep = null;
        for (ModelStep s : allSteps) {
            if (s.getSortOrder() > afterStep.getSortOrder()) {
                if (beforeStep == null || s.getSortOrder() < beforeStep.getSortOrder()) {
                    beforeStep = s;
                }
            }
        }

        int newSortOrder;
        if (beforeStep == null) {
            newSortOrder = afterStep.getSortOrder() + STEP_INTERVAL;
        } else {
            int sum = afterStep.getSortOrder() + beforeStep.getSortOrder();
            newSortOrder = sum / 2;
            if (sum % 2 != 0) {
                renumberStepsAfter(modelId, afterStep.getSortOrder());
                newSortOrder = afterStep.getSortOrder() + STEP_INTERVAL;
            }
        }

        String stepCode = StrUtil.isNotBlank(request.getStepCode()) ? request.getStepCode() : generateStepCode(modelId);
        String stepConfigStr = null;
        try {
            if (request.getStepConfig() != null) {
                stepConfigStr = objectMapper.writeValueAsString(request.getStepConfig());
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("步骤配置序列化失败", e);
        }

        ModelStep newStep = new ModelStep();
        newStep.setModelId(modelId);
        newStep.setStepCode(stepCode);
        newStep.setStepName(request.getStepName());
        newStep.setStepDesc(request.getStepDesc());
        newStep.setStepType(request.getStepType());
        newStep.setSortOrder(newSortOrder);
        newStep.setStepConfig(stepConfigStr);
        
        extractAndSetConfigFields(newStep);
        
        stepMapper.insert(newStep);
        
        log.info("插入步骤成功：modelId={}, stepId={}, afterStepId={}", modelId, newStep.getId(), request.getAfterStepId());
        return newStep;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStep(Long modelId, Long stepId, ModelStep step) {
        getStepDetail(modelId, stepId);
        
        extractAndSetConfigFields(step);
        
        stepMapper.updateStepById(
            stepId, step.getStepName(), step.getStepType(), step.getStepDesc(),
            step.getStepConfig(), step.getSqlStatement()
        );
        
        log.info("更新步骤成功：modelId={}, stepId={}", modelId, stepId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStep(Long modelId, Long stepId) {
        ModelStep step = stepMapper.selectById(stepId);
        if (step == null || !step.getModelId().equals(modelId)) {
            throw new RuntimeException("步骤不存在");
        }

        stepMapper.deleteById(stepId);
        renumberSteps(modelId);
        
        log.info("删除步骤成功：modelId={}, stepId={}", modelId, stepId);
    }

    private void renumberSteps(Long modelId) {
        List<ModelStep> steps = stepMapper.selectByModelId(modelId);
        Collections.sort(steps, Comparator.comparingInt(ModelStep::getSortOrder));

        for (int i = 0; i < steps.size(); i++) {
            ModelStep s = steps.get(i);
            int newSortOrder = (i + 1) * STEP_INTERVAL;
            if (s.getSortOrder() != newSortOrder) {
                stepMapper.updateSortOrderById(s.getId(), newSortOrder);
            }
        }
    }

    private void renumberStepsAfter(Long modelId, int startSortOrder) {
        List<ModelStep> steps = stepMapper.selectByModelId(modelId);
        List<ModelStep> filteredSteps = new ArrayList<>();
        for (ModelStep s : steps) {
            if (s.getSortOrder() >= startSortOrder) {
                filteredSteps.add(s);
            }
        }
        Collections.sort(filteredSteps, Comparator.comparingInt(ModelStep::getSortOrder));

        int baseOrder = startSortOrder;
        for (int i = 0; i < filteredSteps.size(); i++) {
            ModelStep s = filteredSteps.get(i);
            int newSortOrder = baseOrder + (i + 1) * STEP_INTERVAL;
            if (s.getSortOrder() != newSortOrder) {
                stepMapper.updateSortOrderById(s.getId(), newSortOrder);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderStep(Long modelId, Long stepId, Long targetAfterStepId) {
        ModelStep step = getStepDetail(modelId, stepId);
        ModelStep targetAfter = stepMapper.selectById(targetAfterStepId);

        if (targetAfter == null || !targetAfter.getModelId().equals(modelId)) {
            throw new RuntimeException("目标位置不存在");
        }

        List<ModelStep> allSteps = stepMapper.selectByModelId(modelId);
        ModelStep targetBefore = null;
        for (ModelStep s : allSteps) {
            if (s.getSortOrder() > targetAfter.getSortOrder()) {
                if (targetBefore == null || s.getSortOrder() < targetBefore.getSortOrder()) {
                    targetBefore = s;
                }
            }
        }

        int newSortOrder;
        if (targetBefore == null) {
            newSortOrder = targetAfter.getSortOrder() + STEP_INTERVAL;
        } else {
            newSortOrder = (targetAfter.getSortOrder() + targetBefore.getSortOrder()) / 2;
        }

        step.setSortOrder(newSortOrder);
        stepMapper.updateById(step);
        
        log.info("重排步骤成功：modelId={}, stepId={}, newSortOrder={}", modelId, stepId, newSortOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void swapSteps(Long modelId, Long stepId, Long swapWithStepId) {
        ModelStep step1 = getStepDetail(modelId, stepId);
        ModelStep step2 = getStepDetail(modelId, swapWithStepId);

        int tempSortOrder = step1.getSortOrder();
        step1.setSortOrder(step2.getSortOrder());
        step2.setSortOrder(tempSortOrder);

        stepMapper.updateById(step1);
        stepMapper.updateById(step2);
        
        log.info("交换步骤成功：modelId={}, step1Id={}, step2Id={}", modelId, stepId, swapWithStepId);
    }

    @Override
    public StepTreeResult getStepTree(Long modelId) {
        List<ModelStep> steps = getStepsByModelId(modelId);

        StepTreeResult result = new StepTreeResult();
        List<StepTreeResult.FlowNode> nodes = new ArrayList<>();
        List<StepTreeResult.FlowEdge> edges = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            ModelStep step = steps.get(i);
            StepTreeResult.FlowNode node = new StepTreeResult.FlowNode();
            node.setId(step.getId());
            node.setStepName(step.getStepName());
            node.setStepType(step.getStepType());
            node.setX(250);
            node.setY(50 + i * 100);

            try {
                if (StrUtil.isNotBlank(step.getStepConfig())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> configMap = objectMapper.readValue(step.getStepConfig(), Map.class);
                    node.setConfig(configMap);
                }
            } catch (JsonProcessingException ignored) {
            }

            nodes.add(node);

            if (i < steps.size() - 1) {
                StepTreeResult.FlowEdge edge = new StepTreeResult.FlowEdge();
                edge.setSource(step.getId());
                edge.setTarget(steps.get(i + 1).getId());
                edge.setLabel("下一步");
                edges.add(edge);
            }
        }

        result.setNodes(nodes);
        result.setEdges(edges);
        return result;
    }

    private String generateStepCode(Long modelId) {
        return "STEP_" + modelId + "_" + System.currentTimeMillis() % 10000;
    }

    private String buildDetailedErrorLog(String dataSource, String sql, Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append("【执行失败】\n\n");
        sb.append("数据源：").append(dataSource).append("\n\n");
        sb.append("执行的SQL：\n").append(sql).append("\n\n");
        sb.append("错误类型：").append(e.getClass().getName()).append("\n\n");
        sb.append("错误信息：").append(e.getMessage()).append("\n\n");
        
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();
        
        if (stackTrace.length() > 3000) {
            sb.append("堆栈信息（最后3000字符）：\n");
            sb.append(stackTrace.substring(stackTrace.length() - 3000));
        } else {
            sb.append("堆栈信息：\n").append(stackTrace);
        }
        
        if (sb.length() > 4000) {
            return sb.substring(0, 4000);
        }
        return sb.toString();
    }

    @Override
    public String executeStep(Long modelId, Long stepId) {
        ModelInfo model = modelInfoMapper.selectById(modelId);
        if (model == null) {
            throw new RuntimeException("模型不存在");
        }
        
        String dataSourceName = StrUtil.isNotBlank(model.getDataSource()) ? model.getDataSource() : "master";
        JdbcTemplate dsJdbcTemplate = com.selfmodeling.config.DataSourceConfig.getJdbcTemplate(dataSourceName);
        if (dsJdbcTemplate == null) {
            throw new RuntimeException("数据源 " + dataSourceName + " 不存在");
        }
        
        ModelStep step = getStepDetail(modelId, stepId);
        if (step == null || StrUtil.isBlank(step.getSqlStatement())) {
            throw new RuntimeException("SQL语句为空");
        }

        String sql = step.getSqlStatement().trim();
        String resultTableName = "STEP" + stepId;
        
        LocalDateTime startTime = LocalDateTime.now();
        
        stepMapper.updateExecuteStart(stepId, startTime);
        log.info("步骤执行开始：stepId={}, dataSource={}, startTime={}", stepId, dataSourceName, startTime);
        final String finalDataSourceName = dataSourceName;
        final String finalResultTableName = resultTableName;
        
        CompletableFuture.runAsync(() -> {
            String status;
            String errorLog = null;
            try {   
                //测试  Thread.sleep(3000);
                if (sql.toUpperCase().startsWith("SELECT")) {
                    JdbcTemplate asyncJdbcTemplate = com.selfmodeling.config.DataSourceConfig.getJdbcTemplate(finalDataSourceName);
                    if (asyncJdbcTemplate != null) {
                        asyncJdbcTemplate.execute("DROP TABLE IF EXISTS " + finalResultTableName);
                        String createAndInsertSql = "CREATE TABLE " + finalResultTableName + " AS " + sql;
                        asyncJdbcTemplate.execute(createAndInsertSql);
                        List<Map<String, Object>> countResult = asyncJdbcTemplate.queryForList("SELECT COUNT(*) as count FROM " + finalResultTableName);
                        int rowCount = countResult.isEmpty() ? 0 : ((Number) countResult.get(0).get("count")).intValue();
                        status = "success";
                        errorLog = "";
                        log.info("异步执行SELECT成功：stepId={}, rows={}, resultTable={}", stepId, rowCount, finalResultTableName);
                    } else {
                        status = "failed";
                        errorLog = "执行失败：数据源 " + finalDataSourceName + " 不存在";
                        log.error("异步执行失败：数据源 {} 不存在", finalDataSourceName);
                    }
                } else {
                    JdbcTemplate asyncJdbcTemplate = com.selfmodeling.config.DataSourceConfig.getJdbcTemplate(finalDataSourceName);
                    if (asyncJdbcTemplate != null) {
                        asyncJdbcTemplate.update(sql);
                        status = "success";
                        errorLog = "";
                        log.info("异步执行SQL成功：stepId={}, affectedRows={}", stepId, 0);
                    } else {
                        status = "failed";
                        errorLog = "执行失败：数据源 " + finalDataSourceName + " 不存在";
                        log.error("异步执行失败：数据源 {} 不存在", finalDataSourceName);
                    }
                }
            } catch (Exception e) {
                status = "failed";
                errorLog = buildDetailedErrorLog(finalDataSourceName, sql, e);
                log.error("异步执行SQL失败：stepId={}, dataSource={}", stepId, finalDataSourceName, e);
            }
            
            LocalDateTime asyncEndTime = LocalDateTime.now();
            stepMapper.updateExecuteEnd(stepId, finalResultTableName, status, asyncEndTime, errorLog);
            log.info("步骤执行结束：stepId={}, status={}, endTime={}", stepId, status, asyncEndTime);
        });
        
        return resultTableName;
    }

    @Override
    public PageResult<Map<String, Object>> getStepResult(Long modelId, Long stepId, int pageNum, int pageSize) {
        ModelInfo model = modelInfoMapper.selectById(modelId);
        if (model == null) {
            throw new RuntimeException("模型不存在");
        }

        String dataSourceName = StrUtil.isNotBlank(model.getDataSource()) ? model.getDataSource() : "master";
        JdbcTemplate dsJdbcTemplate = com.selfmodeling.config.DataSourceConfig.getJdbcTemplate(dataSourceName);
        if (dsJdbcTemplate == null) {
            throw new RuntimeException("数据源 " + dataSourceName + " 不存在");
        }

        ModelStep step = getStepDetail(modelId, stepId);
        if (step == null) {
            throw new RuntimeException("步骤不存在");
        }

        String resultTableName = step.getResultTableName();
        if (StrUtil.isBlank(resultTableName)) {
            throw new RuntimeException("该步骤尚未执行，无结果表");
        }

        try {
            long total = dsJdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + resultTableName, Long.class);
            int offset = (pageNum - 1) * pageSize;
            List<Map<String, Object>> rows = dsJdbcTemplate.queryForList(
                    "SELECT * FROM " + resultTableName + " LIMIT ? OFFSET ?", pageSize, offset);

            log.info("查询结果表成功：stepId={}, dataSource={}, tableName={}, total={}, pageNum={}, pageSize={}",
                    stepId, dataSourceName, resultTableName, total, pageNum, pageSize);

            return PageResult.of(total, rows);
        } catch (Exception e) {
            log.error("查询结果表失败：dataSource={}, tableName={}", dataSourceName, resultTableName, e);
            throw new RuntimeException("查询结果表失败：" + e.getMessage(), e);
        }
    }

    private void extractAndSetConfigFields(ModelStep step) {
        log.info("开始处理 step_config，原始值: {}", step.getStepConfig());
        
        if (StrUtil.isBlank(step.getStepConfig())) {
            log.info("step_config 为空");
            step.setSqlStatement(null);
            return;
        }

        try {
            Map<String, Object> configMap = objectMapper.readValue(
                step.getStepConfig(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            
            log.info("解析后的 configMap 键: {}", configMap.keySet());
            
            // 从根级别提取 sqlStatement
            Object sqlStatementObj = configMap.get("sqlStatement");
            log.info("从根级别提取到 sqlStatement: {}", sqlStatementObj);
            
            step.setSqlStatement(sqlStatementObj != null ? String.valueOf(sqlStatementObj) : null);
            
            // 从根级别删除 configType 和 sqlStatement
            configMap.remove("configType");
            configMap.remove("sqlStatement");
            
            // 重新序列化 stepConfig，只保留 queryConfig
            String newStepConfig = objectMapper.writeValueAsString(configMap);
            step.setStepConfig(newStepConfig);
            log.info("处理后的 step_config: {}", newStepConfig);
            log.info("最终设置 sqlStatement: {}", step.getSqlStatement());
            
        } catch (JsonProcessingException e) {
            log.warn("处理 step_config 失败", e);
            step.setSqlStatement(null);
        }
    }
}
