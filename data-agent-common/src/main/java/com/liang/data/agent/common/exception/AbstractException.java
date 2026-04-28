package com.liang.data.agent.common.exception;

import com.liang.data.agent.common.errorcode.IErrorCode;
import lombok.Getter;

import java.util.Optional;

/**
 * 抽象项目中三类异常体系，客户端异常、服务端异常以及远程服务调用异常
 *
 * <p>
 * 遵循阿里巴巴错误码规范：
 * <ul>
 *   <li>A 类错误 → {@link com.liang.data.agent.common.exception.ClientException}</li>
 *   <li>B 类错误 → {@link com.liang.data.agent.common.exception.ServiceException}</li>
 *   <li>C 类错误 → {@link com.liang.data.agent.common.exception.RemoteException}</li>
 * </ul>
 * </p>
 */
@Getter
public abstract class AbstractException extends RuntimeException {

    public final String errorCode;

    public final String errorMessage;

    public AbstractException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable);
        this.errorCode = errorCode.code();
        this.errorMessage = Optional.ofNullable(
                message != null && !message.isEmpty() ? message : null
        ).orElse(errorCode.message());
    }

}
