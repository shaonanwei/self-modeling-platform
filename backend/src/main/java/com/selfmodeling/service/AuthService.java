package com.selfmodeling.service;

import com.selfmodeling.dto.LoginResponse;
import com.selfmodeling.entity.SysUser;
import com.selfmodeling.request.LoginRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    void logout();
    LoginResponse refresh(String refreshToken);
    SysUser getCurrentUser();
}
