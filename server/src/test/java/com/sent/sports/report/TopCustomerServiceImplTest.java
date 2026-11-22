package com.sent.sports.report;

import com.sent.sports.report.dto.TopCustomerDtos;
import com.sent.sports.report.mapper.TopCustomerMapper;
import com.sent.sports.report.mapper.po.TopCustomerRow;
import com.sent.sports.report.service.impl.TopCustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 大客户采购排行服务（TDD 2026-11-21）：时间窗、天数/条数收敛、Top N 与名次、
 * 全量口径汇总、占比、空数据。
 */
@ExtendWith(MockitoExtension.class)
class TopCustomerServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private TopCustomerMapper mapper;

    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // 2026-11-12 10:00 +08:00
        fixedClock = Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), ZONE);
    }

    private TopCustomerServiceImpl service() {
        return new TopCustomerServiceImpl(mapper, fixedClock);
    }

    private static TopCustomerRow row(long customerId, String name, String phone,
                                      int orderCount, long amountCent) {
        TopCustomerRow row = new TopCustomerRow();
        row.setCustomerId(customerId);
        row.setCustomerName(name);
        row.setPhone(phone);
        row.setOrderCount(orderCount);
        row.setAmountCent(amountCent);
        return row;
    }

    @Test
    @DisplayName("近 7 天时间窗是 [11-06 00:00, 11-13 00:00)，由 SQL 下推")
    void pushesDateWindow() {
        when(mapper.selectTopCustomers(any(), any())).thenReturn(List.of());

        service().topCustomers(7, 5);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectTopCustomers(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 6, 0, 0));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 13, 0, 0));
    }

    @Test
    @DisplayName("天数收敛 1~90、条数收敛 1~50")
    void clampsDaysAndLimit() {
        when(mapper.selectTopCustomers(any(), any())).thenReturn(List.of());

        service().topCustomers(0, 0);
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectTopCustomers(from.capture(), any());
        assertThat(from.getValue()).as("days=0 收敛为 1 天")
                .isEqualTo(LocalDateTime.of(2026, 11, 12, 0, 0));

        TopCustomerDtos.TopCustomerVO clamped = service().topCustomers(999, 999);
        assertThat(clamped.days()).isEqualTo(90);
        assertThat(clamped.limit()).isEqualTo(50);
    }

    @Test
    @DisplayName("Top N 按金额降序截断，名次从 1 连续")
    void truncatesToTopNAndRanks() {
        when(mapper.selectTopCustomers(any(), any())).thenReturn(List.of(
                row(3L, "丙公司", "13800000003", 1, 1_000L),
                row(1L, "甲公司", "13800000001", 9, 9_000L),
                row(2L, "乙公司", "13800000002", 4, 4_000L)));

        TopCustomerDtos.TopCustomerVO vo = service().topCustomers(30, 2);

        assertThat(vo.items()).hasSize(2);
        assertThat(vo.items()).extracting(TopCustomerDtos.TopCustomerItemVO::customerId)
                .containsExactly(1L, 2L);
        assertThat(vo.items()).extracting(TopCustomerDtos.TopCustomerItemVO::rank)
                .containsExactly(1, 2);
        assertThat(vo.items().get(0).amountYuan()).isEqualTo("90.00");
    }

    @Test
    @DisplayName("总额 / 客户数 / 订单数恒为全量口径，不随 limit 变小")
    void totalsStayFullRange() {
        when(mapper.selectTopCustomers(any(), any())).thenReturn(List.of(
                row(1L, "甲公司", "13800000001", 3, 10_000L),
                row(2L, "乙公司", "13800000002", 2, 4_000L),
                row(3L, "丙公司", null, 1, 1_000L)));

        TopCustomerDtos.TopCustomerVO vo = service().topCustomers(30, 2);

        assertThat(vo.totalAmountCent()).isEqualTo(15_000L);
        assertThat(vo.totalAmountYuan()).isEqualTo("150.00");
        assertThat(vo.totalOrderCount()).isEqualTo(6L);
        assertThat(vo.customerCount()).isEqualTo(3);
        assertThat(vo.items()).hasSize(2);
    }

    @Test
    @DisplayName("占比 = 该客户金额 / 区间总金额，保留 1 位小数；手机号为空给空串")
    void computesSharePercent() {
        when(mapper.selectTopCustomers(any(), any())).thenReturn(List.of(
                row(1L, "甲公司", null, 1, 6_670L),
                row(2L, "乙公司", "13800000002", 1, 3_330L)));

        TopCustomerDtos.TopCustomerVO vo = service().topCustomers(30, 5);

        assertThat(vo.items().get(0).sharePct()).isEqualTo("66.7");
        assertThat(vo.items().get(1).sharePct()).isEqualTo("33.3");
        assertThat(vo.items().get(0).phone()).isEmpty();
    }

    @Test
    @DisplayName("区间内无成交：items 空、汇总归零、不出现除零")
    void emptyRange() {
        when(mapper.selectTopCustomers(any(), any())).thenReturn(List.of());

        TopCustomerDtos.TopCustomerVO vo = service().topCustomers(30, 5);

        assertThat(vo.items()).isEmpty();
        assertThat(vo.totalAmountCent()).isZero();
        assertThat(vo.totalAmountYuan()).isEqualTo("0.00");
        assertThat(vo.totalOrderCount()).isZero();
        assertThat(vo.customerCount()).isZero();
    }
}
