package com.selfmodeling.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.selfmodeling.dto.LoginResponse;
import com.selfmodeling.entity.SysUser;
import com.selfmodeling.mapper.SysUserMapper;
import com.selfmodeling.request.LoginRequest;
import com.selfmodeling.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AuthService 实现类
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Autowired
    private SysUserMapper userMapper;

    private boolean checkPassword(String rawPassword, String storedPassword) {
        log.info("验证密码 - 输入密码: [{}], 数据库密码: [{}]", rawPassword, storedPassword);
        // 正常的 BCrypt 验证
        try {
            boolean result = BCrypt.checkpw(rawPassword, storedPassword);
            log.info("BCrypt 验证结果: {}", result);
            return result;
        } catch (Exception e) {
            log.error("BCrypt 验证异常", e);
            return false;
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("尝试登录 - username: [{}], password: [{}]", request.getUsername(), request.getPassword());
        SysUser user = userMapper.selectByUsername(request.getUsername());

        if (user == null) {
            log.warn("用户不存在");
            throw new RuntimeException("用户名或密码错误");
        }
        
        if (!checkPassword(request.getPassword(), user.getPassword())) {
            log.warn("密码不匹配");
            throw new RuntimeException("用户名或密码错误");
        }

        if (user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用");
        }

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        
        log.info("用户登录成功：userId={}, username={}", user.getId(), user.getUsername());

        return LoginResponse.of(token, token, 1800L);
    }

    @Override
    public void logout() {
        StpUtil.logout();
        log.info("用户登出成功");
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        throw new RuntimeException("刷新功能暂未实现，请重新登录");
    }

    @Override
    public SysUser getCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setPassword(null);
        return user;
    }
}
