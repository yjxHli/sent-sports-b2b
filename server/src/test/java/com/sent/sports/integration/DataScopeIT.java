package com.sent.sports.integration;

import com.sent.sports.common.mybatis.DataScopeContext;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5-28 数据范围真库验证：MyBatis 拦截器确实在 SQL 执行前追加了 ALL / TEAM / SELF 条件。
 * <p>单测只能证明 SQL 字符串改写正确；本测试用完整 Spring 上下文 + 真实 MySQL 证明
 * 「插件被注册、注解被解析、BoundSql 被改写、MySQL 执行结果符合范围」四件事同时成立。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class DataScopeIT extends FullStackIntegrationTest {

    @Autowired
    private OrderMapper orderMapper;

    @AfterEach
    void clearScope() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("P5-28：ALL 全量 / TEAM 含下属 / SELF 仅自己 / 无上下文不改写")
    void dataScopeFiltersAdminOrderList() {
        seedOrder("SO-DS-1", 7L);
        seedOrder("SO-DS-2", 8L);

        DataScopeContext.set(DataScopeContext.Scope.self(7L));
        assertThat(orderMapper.selectForAdminList("WAIT_PAY", 10)).hasSize(1);

        DataScopeContext.set(DataScopeContext.Scope.team(7L, List.of(7L, 8L)));
        assertThat(orderMapper.selectForAdminList("WAIT_PAY", 10)).hasSize(2);

        DataScopeContext.set(DataScopeContext.Scope.team(7L, List.of()));
        assertThat(orderMapper.selectForAdminList("WAIT_PAY", 10)).as("TEAM 无下属时拒绝全表").isEmpty();

        DataScopeContext.set(DataScopeContext.Scope.all(7L));
        assertThat(orderMapper.selectForAdminList("WAIT_PAY", 10)).hasSize(2);

        DataScopeContext.clear();
        assertThat(orderMapper.selectForAdminList("WAIT_PAY", 10))
                .as("定时任务等无请求上下文的场景不改写").hasSize(2);
    }

    private void seedOrder(String orderNo, long ownerStaffId) {
        jdbc.update("INSERT INTO orders (order_no, customer_id, owner_staff_id, status, pay_status, "
                        + "payable_cent, idem_key) VALUES (?, 1, ?, 'WAIT_PAY', 'UNPAID', 10000, ?)",
                orderNo, ownerStaffId, "IDEM-" + orderNo);
    }
}
