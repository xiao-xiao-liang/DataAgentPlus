package com.liang.data.agent.dal.connector;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.connector.bo.*;
import com.liang.data.agent.dal.connector.ddl.DdlExecutor;
import com.liang.data.agent.dal.connector.pool.DBConnectionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据库访问门面
 * <p>
 * 这是对外的唯一入口, 组合了 连接池 + DDL + SqlExecutor
 * <p>
 * Service 层和 Workflow 层只需要注入这一个类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseAccessor {

    private final List<DdlExecutor> ddlExecutors;
    private final List<DBConnectionPool> connectionPools;

    public String ping(DbConfigBO config) {
        return getPool(config.type()).ping(config);
    }

    public List<TableInfoBO> showTables(DbConfigBO config, String pattern) {
        try (Connection conn = getPool(config.type()).getConnection(config)) {
            return getDdl(config.type()).showTables(conn, config.schema(), pattern);
        } catch (SQLException e) {
            log.error("查询表列表失败: {}", e.getMessage());
            throw new ServiceException("查询表列表失败");
        }
    }

    public List<ColumnInfoBO> showColumns(DbConfigBO config, String table) {
        try (Connection conn = getPool(config.type()).getConnection(config)) {
            return getDdl(config.type()).showColumns(conn, config.schema(), table);
        } catch (SQLException e) {
            log.error("查询字段信息失败: {}", e.getMessage());
            throw new ServiceException("查询字段信息失败");
        }
    }

    public List<ForeignKeyInfoBO> showForeignKeys(DbConfigBO config, List<String> tables) {
        try (Connection conn = getPool(config.type()).getConnection(config)) {
            return getDdl(config.type()).showForeignKeys(conn, config.schema(), tables);
        } catch (SQLException e) {
            log.error("查询外键信息失败: {}", e.getMessage());
            throw new ServiceException("查询外键信息失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    public ResultSetBO executeSql(DbConfigBO config, String sql) {
        try (Connection conn = getPool(config.type()).getConnection(config)) {
            return SqlExecutor.execute(conn, config.schema(), sql);
        } catch (SQLException e) {
            log.error("执行 SQL 失败: sql={}, error={}", sql, e.getMessage());
            throw new ServiceException("执行 SQL 失败: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    public List<String> sampleColumn(DbConfigBO config, String table, String column) {
        try (Connection conn = getPool(config.type()).getConnection(config)) {
            return getDdl(config.type()).sampleColumn(conn, config.schema(), table, column);
        } catch (SQLException e) {
            log.error("字段采样失败: {}", e.getMessage());
            throw new ServiceException("字段采样失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private DBConnectionPool getPool(String type) {
        return connectionPools.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new ServiceException("不支持的数据库类型: " + type));
    }

    private DdlExecutor getDdl(String type) {
        return ddlExecutors.stream()
                .filter(d -> d.supports(type))
                .findFirst()
                .orElseThrow(() -> new ServiceException("不支持的数据库类型: " + type));
    }
}
