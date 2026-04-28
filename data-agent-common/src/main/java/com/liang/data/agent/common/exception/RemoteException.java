package com.liang.data.agent.common.exception;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.errorcode.IErrorCode;

/**
 * 远程服务调用异常
 *
 * <p>比如调用 LLM API、MCP Server 失败时，向上抛出的异常应该是远程服务调用异常</p>
 */
public class RemoteException extends AbstractException {

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public RemoteException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "RemoteException{code='" + errorCode + "', message='" + errorMessage + "'}";
    }
}
