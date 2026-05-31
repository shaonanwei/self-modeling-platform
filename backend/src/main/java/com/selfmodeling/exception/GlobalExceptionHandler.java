package com.selfmodeling.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.selfmodeling.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常（RuntimeException）
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 参数校验异常（@Validated / @RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("参数校验失败：{}", message);
        return Result.error(400, message);
    }

    /**
     * 绑定异常（@RequestParam / @PathVariable）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("参数绑定失败：{}", message);
        return Result.error(400, message);
    }

    /**
     * 未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        log.warn("未登录：{}", e.getMessage());
        return Result.error(401, "未登录，请先登录");
    }

    /**
     * 权限不足
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        log.warn("权限不足：{}", e.getMessage());
        return Result.error(403, "权限不足");
    }

    /**
     * 角色不足
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotRoleException(NotRoleException e) {
        log.warn("角色不足：{}", e.getMessage());
        return Result.error(403, "角色不足");
    }

    /**
     * 唯一键冲突
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("唯一键冲突：{}", e.getMessage());
        return Result.error(409, "数据已存在");
    }

    /**
     * 其他未预期异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return Result.error(500, "服务器内部错误：" + e.getMessage());
    }
}
