package com.selfmodeling.mapper;

import com.selfmodeling.entity.ModelStep;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ModelStep Mapper
 */
@Mapper
public interface ModelStepMapper {

    /**
     * 根据ID查询步骤信息
     *
     * @param id 步骤ID
     * @return 步骤信息
     */
    ModelStep selectById(Long id);

    /**
     * 根据模型ID查询步骤列表
     *
     * @param modelId 模型ID
     * @return 步骤列表
     */
    List<ModelStep> selectByModelId(Long modelId);

    /**
     * 新增步骤信息
     *
     * @param modelStep 步骤信息
     * @return 影响行数
     */
    int insert(ModelStep modelStep);

    /**
     * 根据ID更新步骤信息
     *
     * @param modelStep 步骤信息
     * @return 影响行数
     */
    int updateById(ModelStep modelStep);

    /**
     * 根据ID逻辑删除步骤
     *
     * @param id 步骤ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 根据模型ID逻辑删除所有步骤
     *
     * @param modelId 模型ID
     * @return 影响行数
     */
    int deleteByModelId(Long modelId);

    /**
     * 根据步骤编码查询步骤ID
     *
     * @param stepCode 步骤编码
     * @return 步骤ID
     */
    Long selectIdByCode(String stepCode);

    /**
     * 根据步骤ID更新步骤详细信息
     *
     * @param stepId       步骤ID
     * @param stepName     步骤名称
     * @param stepType     步骤类型
     * @param stepDesc     步骤描述
     * @param stepConfig   步骤配置
     * @param sqlStatement SQL语句
     * @return 影响行数
     */
    int updateStepById(Long stepId,
                       String stepName,
                       String stepType,
                       String stepDesc,
                       String stepConfig,
                       String sqlStatement);

    /**
     * 根据步骤ID更新排序
     *
     * @param stepId    步骤ID
     * @param sortOrder 排序值
     * @return 影响行数
     */
    int updateSortOrderById(Long stepId, Integer sortOrder);

    /**
     * 更新执行结果
     *
     * @param stepId          步骤ID
     * @param resultTableName 结果表名
     * @param executeStatus   执行状态
     * @param executeStartTime 执行开始时间
     * @param executeEndTime   执行结束时间
     * @return 影响行数
     */
    int updateExecuteResult(Long stepId, String resultTableName, String executeStatus,
                           java.time.LocalDateTime executeStartTime, java.time.LocalDateTime executeEndTime);

    /**
     * 更新执行开始时间（清空结束时间）
     *
     * @param stepId          步骤ID
     * @param executeStartTime 执行开始时间
     * @return 影响行数
     */
    int updateExecuteStart(Long stepId, java.time.LocalDateTime executeStartTime);

    /**
     * 更新执行结束时间和状态
     *
     * @param stepId          步骤ID
     * @param resultTableName 结果表名
     * @param executeStatus   执行状态
     * @param executeEndTime  执行结束时间
     * @param executeLog      执行日志
     * @return 影响行数
     */
    int updateExecuteEnd(Long stepId, String resultTableName, String executeStatus,
                        java.time.LocalDateTime executeEndTime, String executeLog);
}
