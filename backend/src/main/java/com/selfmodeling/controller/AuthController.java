package com.selfmodeling.controller;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.selfmodeling.dto.CaptchaResponse;
import com.selfmodeling.dto.LoginResponse;
import com.selfmodeling.dto.Result;
import com.selfmodeling.entity.SysUser;
import com.selfmodeling.request.LoginRequest;
import com.selfmodeling.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private DefaultKaptcha defaultKaptcha;

    /**
     * 生成验证码图片
     *
     * @param session HTTP会话
     * @return 验证码key和Base64编码的图片
     */
    @GetMapping("/captcha")
    public Result<CaptchaResponse> getCaptcha(HttpSession session) {
        String captchaText = defaultKaptcha.createText();
        BufferedImage captchaImage = defaultKaptcha.createImage(captchaText);

        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        session.setAttribute("captcha:" + captchaKey, captchaText);

        String base64Image;
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ImageIO.write(captchaImage, "png", os);
            base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (Exception e) {
            return Result.error("验证码生成失败");
        }

        return Result.success(new CaptchaResponse(captchaKey, base64Image));
    }

    /**
     * 用户登录
     *
     * @param request 登录请求（含验证码）
     * @param session HTTP会话
     * @return 登录响应
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        //String storedCode = (String) session.getAttribute("captcha:" + request.getCaptchaKey());
        session.removeAttribute("captcha:" + request.getCaptchaKey());

        // if (storedCode == null) {
        //     return Result.error(400, "验证码已过期，请刷新验证码");
        // }
        // if (!storedCode.equalsIgnoreCase(request.getCaptchaCode())) {
        //     return Result.error(400, "验证码错误");
        // }

        LoginResponse response = authService.login(request);
        return Result.success("登录成功", response);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success("登出成功", null);
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody java.util.Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        LoginResponse response = authService.refresh(refreshToken);
        return Result.success("刷新成功", response);
    }

    @GetMapping("/userinfo")
    public Result<SysUser> getUserInfo() {
        SysUser user = authService.getCurrentUser();
        return Result.success(user);
    }
}
