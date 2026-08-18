package com.selfmodeling.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SaInterceptor saInterceptor = new SaInterceptor(handle -> {
            StpUtil.checkLogin();
        });
        registry.addInterceptor(new HandlerInterceptor() {

            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                    Object handler) throws Exception {
                if (request.getDispatcherType() == DispatcherType.ASYNC) {
                    // SSE 续派发沿用初始请求的鉴权结果，避免在线程切换后重复读取 Sa-Token 上下文。
                    return true;
                }
                return saInterceptor.preHandle(request, response, handler);
            }
        })
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/v1/auth/login")
        .excludePathPatterns("/api/v1/auth/captcha")
        .excludePathPatterns("/api/v1/auth/refresh")
        .excludePathPatterns("/error");
    }
}
