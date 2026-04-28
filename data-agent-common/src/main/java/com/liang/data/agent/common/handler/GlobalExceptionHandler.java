package com.liang.data.agent.common.handler;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.AbstractException;
import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>
 * 拦截指定异常并通过优雅构建方式返回前端信息。
 * 项目使用 Spring MVC (Servlet)，SSE 流式输出通过 Controller 返回 {@code Flux<ServerSentEvent>} 实现，
 * 不影响常规 REST 接口的 Servlet 异常处理。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        String message = fieldErrors.isEmpty()
                ? "参数校验失败"
                : fieldErrors.stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        log.error("[{}] {} [参数校验异常] {}", request.getMethod(), getUrl(request), message);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message);
    }

    /**
     * 拦截方法参数级校验异常（@Validated + @NotBlank 等）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> constraintViolationException(HttpServletRequest request, ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.error("[{}] {} [约束校验异常] {}", request.getMethod(), getUrl(request), message);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message);
    }

    /**
     * 拦截应用内抛出的业务异常
     */
    @ExceptionHandler(AbstractException.class)
    public Result<Void> abstractException(HttpServletRequest request, AbstractException ex) {
        log.error("[{}] {} [业务异常] code={}, message={}",
                request.getMethod(), getUrl(request),
                ex.getErrorCode(), ex.getErrorMessage(), ex);
        return Results.failure(ex);
    }

    /**
     * 兜底：拦截未捕获的未知异常
     *
     * <p>对用户返回友好提示"系统繁忙，请稍后再试"，详细堆栈仅记录在日志中</p>
     */
    @ExceptionHandler(Throwable.class)
    public Result<Void> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} [未知异常]", request.getMethod(), getUrl(request), throwable);
        return Results.failure(BaseErrorCode.SERVICE_ERROR.code(), "系统繁忙，请稍后再试");
    }

    /**
     * 拼接完整请求 URL（含查询参数）
     */
    private String getUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + queryString;
    }
}
