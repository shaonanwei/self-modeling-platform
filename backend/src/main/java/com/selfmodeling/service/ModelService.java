package com.selfmodeling.service;

import com.selfmodeling.dto.PageResult;
import com.selfmodeling.dto.StepTreeResult;
import com.selfmodeling.entity.ModelInfo;
import com.selfmodeling.entity.ModelStep;
import com.selfmodeling.request.InsertStepRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ModelService 接口
 */
public interface ModelService {

    /**
     * 分页查询模型列表
     *
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param modelName  模型名称（模糊查询）
     * @param status     状态
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @return 分页结果
     */
    PageResult<ModelInfo> pageModels(int pageNum, int pageSize, String modelName, Integer status,
                                     LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 获取模型详情
     *
     * @param id 模型ID
     * @return 模型信息
     */
    ModelInfo getModelDetail(Long id);

    /**
     * 创建模型
     *
     * @param modelInfo 模型信息
     * @return 创建后的模型
     */
    ModelInfo createModel(ModelInfo modelInfo);

    /**
     * 更新模型
     *
     * @param id        模型ID
     * @param modelInfo 模型信息
     */
    void updateModel(Long id, ModelInfo modelInfo);

    /**
     * 删除模型
     *
     * @param id 模型ID
     */
    void deleteModel(Long id);

    /**
     * 更新模型状态
     *
     * @param id     模型ID
     * @param status 状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 复制模型
     *
     * @param id 模型ID
     * @return 复制后的模型
     */
    ModelInfo copyModel(Long id);

    /**
     * 获取模型步骤列表
     *
     * @param modelId 模型ID
     * @return 步骤列表
     */
    List<ModelStep> getStepsByModelId(Long modelId);

    /**
     * 获取步骤详情
     *
     * @param modelId 模型ID
     * @param stepId  步骤ID
     * @return 步骤信息
     */
    ModelStep getStepDetail(Long modelId, Long stepId);

    /**
     * 添加步骤
     *
     * @param modelId 模型ID
     * @param step    步骤信息
     * @return 添加后的步骤
     */
    ModelStep addStep(Long modelId, ModelStep step);

    /**
     * 插入步骤到指定位置
     *
     * @param modelId 模型ID
     * @param request 插入请求
     * @return 插入的步骤
     */
    ModelStep insertStep(Long modelId, InsertStepRequest request);

    /**
     * 更新步骤
     *
     * @param modelId 模型ID
     * @param stepId  步骤ID
     * @param step    步骤信息
     */
    void updateStep(Long modelId, Long stepId, ModelStep step);

    /**
     * 删除步骤
     *
     * @param modelId 模型ID
     * @param stepId  步骤ID
     */
    void deleteStep(Long modelId, Long stepId);

    /**
     * 重新排序步骤
     *
     * @param modelId          模型ID
     * @param stepId           步骤ID
     * @param targetAfterStepId 目标位置步骤ID
     */
    void reorderStep(Long modelId, Long stepId, Long targetAfterStepId);

    /**
     * 交换两个步骤位置
     *
     * @param modelId       模型ID
     * @param stepId        步骤ID
     * @param swapWithStepId 交换目标步骤ID
     */
    void swapSteps(Long modelId, Long stepId, Long swapWithStepId);

    /**
     * 获取步骤树形结构
     *
     * @param modelId 模型ID
     * @return 步骤树
     */
    StepTreeResult getStepTree(Long modelId);

    /**
     * 执行步骤SQL
     *
     * @param modelId 模型ID
     * @param stepId  步骤ID
     * @return 执行结果
     */
    String executeStep(Long modelId, Long stepId);

    /**
     * 获取步骤执行结果（分页）
     *
     * @param modelId 模型ID
     * @param stepId  步骤ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<Map<String, Object>> getStepResult(Long modelId, Long stepId, int pageNum, int pageSize);
}
