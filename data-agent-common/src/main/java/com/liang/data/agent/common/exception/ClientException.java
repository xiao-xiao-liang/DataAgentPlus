package com.liang.data.agent.common.exception;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.errorcode.IErrorCode;

/**
 * 客户端异常
 *
 * <p>用户发起调用请求后因客户端提交参数或其他客户端问题导致的异常</p>
 */
public class ClientException extends AbstractException {

    public ClientException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ClientException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "ClientException{code='" + errorCode + "', message='" + errorMessage + "'}";
    }
}
