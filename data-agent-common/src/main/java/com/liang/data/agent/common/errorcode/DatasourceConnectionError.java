package com.liang.data.agent.common.errorcode;

/**
 * 数据源连接错误信息
 *
 * <p>封装数据源连接异常对应的平台错误码与用户展示消息。</p>
 *
 * @param errorCode 平台错误码
 * @param message 用户展示消息
 */
public record DatasourceConnectionError(IErrorCode errorCode, String message) {
}
