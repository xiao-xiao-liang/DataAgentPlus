package com.liang.data.agent.dal.connector;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.connector.bo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据库访问门面
 * <p>
 * 这是对外的唯一入口, 组合了 DataSourceManager + SqlExecutor
 * <p>
 * Service 层和 Workflow 层只需要注入这一个类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseAccessor {

    private final DataSourceManager dataSourceManager;

    public String ping(DbConfigBO config) {
        return dataSourceManager.ping(config);
    }

    public List<TableInfoBO> showTables(DbConfigBO config, String pattern) {
        try (Connection conn = dataSourceManager.getConnection(config)) {
            return dataSourceManager.getDialect(config.type()).showTables(conn, config.schema(), pattern);
        } catch (SQLException e) {
            log.error("查询表列表失败: {}", e.getMessage());
            throw new ServiceException("查询表列表失败");
        }
    }

    public List<ColumnInfoBO> showColumns(DbConfigBO config, String table) {
        try (Connection conn = dataSourceManager.getConnection(config)) {
            return dataSourceManager.getDialect(config.type()).showColumns(conn, config.schema(), table);
        } catch (SQLException e) {
            log.error("查询字段信息失败: {}", e.getMessage());
            throw new ServiceException("查询字段信息失败");
        }
    }

    public List<ForeignKeyInfoBO> showForeignKeys(DbConfigBO config, List<String> tables) {
        try (Connection conn = dataSourceManager.getConnection(config)) {
            return dataSourceManager.getDialect(config.type()).showForeignKeys(conn, config.schema(), tables);
        } catch (SQLException e) {
            log.error("查询外键信息失败: {}", e.getMessage());
            throw new ServiceException("查询外键信息失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    public ResultSetBO executeSql(DbConfigBO config, String sql) {
        try (Connection conn = dataSourceManager.getConnection(config)) {
            // 注意：未来可以在 Dialect 中包装 executeSql 增强安全性
            return SqlExecutor.execute(conn, config.schema(), sql);
        } catch (SQLException e) {
            log.error("执行 SQL 失败: sql={}, error={}", sql, e.getMessage());
            throw new ServiceException("执行 SQL 失败: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    public List<String> sampleColumn(DbConfigBO config, String table, String column) {
        try (Connection conn = dataSourceManager.getConnection(config)) {
            return dataSourceManager.getDialect(config.type()).sampleColumn(conn, config.schema(), table, column);
        } catch (SQLException e) {
            log.error("字段采样失败: {}", e.getMessage());
            throw new ServiceException("字段采样失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
