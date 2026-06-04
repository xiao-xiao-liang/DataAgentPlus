package com.liang.data.agent.dal.connector.dialect;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JDBC 校验测试桩
 *
 * <p>用于模拟连接后校验查询是否命中目标 database/schema，避免单元测试依赖真实数据库。</p>
 */
final class JdbcValidationStub {

    private JdbcValidationStub() {
    }

    static Connection connectionWithValidationResult(boolean hasResult) {
        return (Connection) Proxy.newProxyInstance(
                JdbcValidationStub.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        return preparedStatementWithValidationResult(hasResult);
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement preparedStatementWithValidationResult(boolean hasResult) {
        return (PreparedStatement) Proxy.newProxyInstance(
                JdbcValidationStub.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("executeQuery".equals(method.getName())) {
                        return resultSetWithValidationResult(hasResult);
                    }
                    if ("setString".equals(method.getName()) || "close".equals(method.getName())) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ResultSet resultSetWithValidationResult(boolean hasResult) {
        AtomicBoolean firstRead = new AtomicBoolean(true);
        return (ResultSet) Proxy.newProxyInstance(
                JdbcValidationStub.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) {
                        return hasResult && firstRead.getAndSet(false);
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
