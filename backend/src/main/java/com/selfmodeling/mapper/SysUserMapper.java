package com.selfmodeling.mapper;

import com.selfmodeling.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * SysUser Mapper
 */
@Mapper
public interface SysUserMapper {

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户信息
     */
    SysUser selectById(Long id);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    SysUser selectByUsername(String username);

    /**
     * 新增用户
     *
     * @param user 用户信息
     * @return 影响行数
     */
    int insert(SysUser user);

    /**
     * 根据ID更新用户信息
     *
     * @param user 用户信息
     * @return 影响行数
     */
    int updateById(SysUser user);

    /**
     * 根据ID逻辑删除用户
     *
     * @param id      用户ID
     * @param updater 更新者
     * @return 影响行数
     */
    int deleteById(Long id, String updater);
}
