package com.liang.data.agent.handler;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.AbstractException;
import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.MediaType;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

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
    public Result<Void> defaultErrorHandler(HttpServletRequest request,
                                            HttpServletResponse response,
                                            Throwable throwable) {
        if (isClientDisconnect(response, throwable)) {
            log.info("[{}] {} [客户端已断开连接]", request.getMethod(), getUrl(request));
            return null;
        }
        log.error("[{}] {} [未知异常]", request.getMethod(), getUrl(request), throwable);
        return Results.failure(BaseErrorCode.SERVICE_ERROR.code(), "系统繁忙，请稍后再试");
    }

    /**
     * 判断异常是否由客户端断开已开始的 SSE 连接引起。
     */
    private boolean isClientDisconnect(HttpServletResponse response, Throwable throwable) {
        // 1. 已提交的 SSE 响应发生 I/O 异常时，不再尝试写入错误响应
        if (response.isCommitted()
                && isSseResponse(response)
                && hasCause(throwable, IOException.class)) {
            return true;
        }

        // 2. Spring 或 Tomcat 已明确识别为客户端断连的异常直接按断连处理
        return hasCause(throwable, AsyncRequestNotUsableException.class)
                || hasCause(throwable, ClientAbortException.class);
    }

    /**
     * 判断响应内容类型是否为 SSE。
     */
    private boolean isSseResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        try {
            return MediaType.TEXT_EVENT_STREAM.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * 判断异常链中是否包含指定类型。
     */
    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
