package com.liang.data.agent.dal.connector.bo;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 查询结果
 *
 * @param columns 列名列表, 如 ["id", "name", "age"]
 * @param data    每行数据, 如 [{id:"1", name:"张三"}, {id:"2", name:"李四"}]
 */
public record ResultSetBO(
        List<String> columns,
        List<Map<String, String>> data
) {
    public static ResultSetBO of(ResultSet rs) throws SQLException {
        // 获取结果集的元信息
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        // 把列名保存到 List 中
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) { // 注意 JDBC 下标从 1 开始, 不是 0
            columns.add(metaData.getColumnLabel(i)); // getColumnLabel 能拿到别名 (AS xxx)
        }

        // 逐行读取数据
        List<Map<String, String>> data = new ArrayList<>();
        while (rs.next()) {
            Map<String, String> row = new LinkedHashMap<>();
            for (String column : columns) {
                String value = rs.getString(column);
                row.put(column, value != null ? value : ""); // NULL 转空字符串
            }
            data.add(row);
        }
        
        return new ResultSetBO(columns, data);
    }
}
