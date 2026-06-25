package com.liang.data.agent.dal.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 智能体数据源关联 Mapper 单元测试。
 */
class AgentDatasourceMapperTest {

    @Test
    void activeDatasourceQueriesShouldIgnoreDeletedBindings() throws Exception {
        assertSelectSqlContainsDelFlag("getActiveDatasource", Integer.class);
        assertSelectSqlContainsDelFlag("getActiveBindingId", Integer.class);
    }

    private void assertSelectSqlContainsDelFlag(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AgentDatasourceMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);

        assertThat(select).isNotNull();
        assertThat(String.join(" ", select.value()).toLowerCase())
                .contains("del_flag = 0");
    }
}
