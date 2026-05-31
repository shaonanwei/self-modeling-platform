package com.selfmodeling.mapper;

import com.selfmodeling.entity.ModelInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ModelInfo Mapper
 */
@Mapper
public interface ModelInfoMapper {

    /**
     * 根据ID查询模型信息
     *
     * @param id 模型ID
     * @return 模型信息
     */
    ModelInfo selectById(Long id);

    /**
     * 查询所有未删除的模型列表
     *
     * @return 模型列表
     */
    List<ModelInfo> selectList();

    /**
     * 新增模型信息
     *
     * @param modelInfo 模型信息
     * @return 影响行数
     */
    int insert(ModelInfo modelInfo);

    /**
     * 根据ID更新模型信息
     *
     * @param modelInfo 模型信息
     * @return 影响行数
     */
    int updateById(ModelInfo modelInfo);

    /**
     * 根据ID逻辑删除模型
     *
     * @param id      模型ID
     * @param updater 更新者
     * @return 影响行数
     */
    int deleteById(Long id, String updater);

    /**
     * 根据模型编码查询模型ID
     *
     * @param modelCode 模型编码
     * @return 模型ID
     */
    Long selectIdByCode(String modelCode);

    /**
     * 根据ID更新模型编码
     *
     * @param id        模型ID
     * @param modelCode 新的模型编码
     * @return 影响行数
     */
    int updateCodeById(Long id, String modelCode);
}
