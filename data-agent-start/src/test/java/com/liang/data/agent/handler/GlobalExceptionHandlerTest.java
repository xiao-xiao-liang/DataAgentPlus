package com.liang.data.agent.handler;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理器测试。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldNotWriteResponseWhenCommittedSseConnectionIsAborted() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/graph/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
        response.setCommitted(true);

        Result<Void> result = handler.defaultErrorHandler(
                request,
                response,
                new IOException("客户端连接已中止")
        );

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnServiceErrorForOrdinaryIOException() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/files");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<Void> result = handler.defaultErrorHandler(
                request,
                response,
                new IOException("读取文件失败")
        );

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(BaseErrorCode.SERVICE_ERROR.code());
    }
}
