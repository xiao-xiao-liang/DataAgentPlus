package com.liang.data.agent.workflow.util;

import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void shouldMaskCommonSensitiveColumns() {
        ResultSetBO result = SensitiveDataMasker.mask(new ResultSetBO(
                List.of("mobile_phone", "email", "id_card", "address", "api_token", "name"),
                List.of(row(
                        "mobile_phone", "13800138000",
                        "email", "alice@example.com",
                        "id_card", "110101199001011234",
                        "address", "北京市朝阳区望京街道",
                        "api_token", "token-value",
                        "name", "Alice"
                ))
        ));

        assertThat(result.data().getFirst()).containsEntry("mobile_phone", "138****8000")
                .containsEntry("email", "a***@example.com")
                .containsEntry("id_card", "110***********1234")
                .containsEntry("address", "北京市***")
                .containsEntry("api_token", "******")
                .containsEntry("name", "Alice");
    }

    @Test
    void shouldRecognizeChineseAndCamelCaseColumnNames() {
        ResultSetBO result = SensitiveDataMasker.mask(new ResultSetBO(
                List.of("手机号", "clientSecret", "电子邮箱"),
                List.of(row(
                        "手机号", "13912345678",
                        "clientSecret", "secret-value",
                        "电子邮箱", "bob@example.com"
                ))
        ));

        assertThat(result.data().getFirst()).containsEntry("手机号", "139****5678")
                .containsEntry("clientSecret", "******")
                .containsEntry("电子邮箱", "b***@example.com");
    }

    @Test
    void shouldMaskSensitiveValuesEvenWhenColumnUsesAlias() {
        ResultSetBO result = SensitiveDataMasker.mask(new ResultSetBO(
                List.of("contact", "account", "document"),
                List.of(row(
                        "contact", "13800138000",
                        "account", "alice@example.com",
                        "document", "110101199001011234"
                ))
        ));

        assertThat(result.data().getFirst()).containsEntry("contact", "138****8000")
                .containsEntry("account", "a***@example.com")
                .containsEntry("document", "110***********1234");
    }

    @Test
    void shouldNotMaskAggregateColumns() {
        ResultSetBO original = new ResultSetBO(
                List.of("phone_count", "emailTotal", "address_avg"),
                List.of(row("phone_count", "12", "emailTotal", "20", "address_avg", "3"))
        );

        ResultSetBO result = SensitiveDataMasker.mask(original);

        assertThat(result).isSameAs(original);
    }

    @Test
    void shouldNotMaskColumnsThatOnlyContainShortSensitiveKeyword() {
        ResultSetBO original = new ResultSetBO(
                List.of("hotel_name", "detail"),
                List.of(row("hotel_name", "North Hotel", "detail", "normal"))
        );

        ResultSetBO result = SensitiveDataMasker.mask(original);

        assertThat(result).isSameAs(original);
    }

    @Test
    void shouldNotMutateOriginalRows() {
        Map<String, String> originalRow = row("phone", "13800138000");
        ResultSetBO result = SensitiveDataMasker.mask(new ResultSetBO(List.of("phone"), List.of(originalRow)));

        assertThat(originalRow).containsEntry("phone", "13800138000");
        assertThat(result.data().getFirst()).containsEntry("phone", "138****8000");
    }

    private static Map<String, String> row(String... values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(values[i], values[i + 1]);
        }
        return row;
    }
}
