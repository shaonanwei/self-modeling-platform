package com.selfmodeling.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.selfmodeling.entity.SysUser;
import com.selfmodeling.mapper.SysUserMapper;
import com.selfmodeling.request.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthServiceImplLoggingTest {

    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void loginLogsUsernameButNotPasswordOrStoredHash(CapturedOutput output) {
        String submittedPassword = "submitted-secret-marker";
        String storedHash = BCrypt.hashpw("different-password");

        SysUser user = new SysUser();
        user.setUsername("security-test-user");
        user.setPassword(storedHash);
        user.setStatus(1);
        when(userMapper.selectByUsername("security-test-user")).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("security-test-user");
        request.setPassword(submittedPassword);

        assertThrows(RuntimeException.class, () -> authService.login(request));

        assertTrue(output.getOut().contains("security-test-user"));
        assertFalse(output.getOut().contains(submittedPassword));
        assertFalse(output.getOut().contains(storedHash));
    }
}
