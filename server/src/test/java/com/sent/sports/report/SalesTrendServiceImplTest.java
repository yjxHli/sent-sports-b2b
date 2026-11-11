package com.sent.sports.report;

import com.sent.sports.report.dto.SalesTrendDtos;
import com.sent.sports.report.mapper.SalesTrendMapper;
import com.sent.sports.report.mapper.po.SalesTrendRow;
import com.sent.sports.report.service.impl.SalesTrendServiceImpl;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售趋势服务（批 4，TDD 红灯先行）：
 * 以 paid_at 为时间基准、按 paid_cent 聚合，近 N 天连续补零，汇总客单价。
 * SQL 层「只计 PAID/PART_REFUNDED、剔除 FULL_REFUNDED」由 SalesTrendMapperIT 真库校验。
 */
@ExtendWith(MockitoExtension.class)
class SalesTrendServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private SalesTrendMapper mapper;

    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // 2026-11-09 10:00 +08:00
        fixedClock = Clock.fixed(Instant.parse("2026-11-09T02:00:00Z"), ZONE);
    }

    private SalesTrendServiceImpl service() {
        return new SalesTrendServiceImpl(mapper, fixedClock);
    }

    private static SalesTrendRow row(String day, long amountCent, int orderCount) {
        SalesTrendRow r = new SalesTrendRow();
        r.setDay(day);
        r.setAmountCent(amountCent);
        r.setOrderCount(orderCount);
        return r;
    }

    @Test
    @DisplayName("近 3 天按日连续补零：无销售的日期也要有 0 值点，汇总金额/订单数/客单价正确")
    void trendBuildsContinuousDaysAndSummary() {
        when(mapper.selectDailyTrend(any(), any())).thenReturn(List.of(
                row("2026-11-07", 30000L, 2),
                row("2026-11-09", 10000L, 1)));

        SalesTrendDtos.SalesTrendVO vo = service().trend(3);

        assertEquals("2026-11-07", vo.fromDate());
        assertEquals("2026-11-09", vo.toDate());
        assertEquals(3, vo.days());
        assertEquals(3, vo.points().size());

        assertEquals("2026-11-07", vo.points().get(0).date());
        assertEquals(30000L, vo.points().get(0).amountCent());
        assertEquals("300.00", vo.points().get(0).amountYuan());
        assertEquals(2, vo.points().get(0).orderCount());
        assertEquals(15000L, vo.points().get(0).avgOrderCent());

        // 中间这天没成交，必须补零而不是被跳过
        assertEquals("2026-11-08", vo.points().get(1).date());
        assertEquals(0L, vo.points().get(1).amountCent());
        assertEquals(0, vo.points().get(1).orderCount());
        assertEquals(0L, vo.points().get(1).avgOrderCent());

        assertEquals(10000L, vo.points().get(2).amountCent());

        assertEquals(40000L, vo.totalAmountCent());
        assertEquals("400.00", vo.totalAmountYuan());
        assertEquals(3, vo.totalOrderCount());
        assertEquals(13333L, vo.avgOrderCent()); // 40000 / 3 向下取整
    }

    @Test
    @DisplayName("时间窗口按 paid_at 边界下推：起始日 00:00（含）到次日 00:00（不含）")
    void trendPassesPaidAtRangeToMapper() {
        when(mapper.selectDailyTrend(any(), any())).thenReturn(List.of());

        service().trend(30);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectDailyTrend(from.capture(), to.capture());
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 0), to.getValue());
        assertEquals(LocalDateTime.of(2026, 10, 11, 0, 0), from.getValue());
    }

    @Test
    @DisplayName("days 越界收敛到 1~90，避免全表扫描")
    void trendClampsDays() {
        when(mapper.selectDailyTrend(any(), any())).thenReturn(List.of());

        assertEquals(1, service().trend(0).days());
        assertEquals(1, service().trend(-5).days());
        assertEquals(90, service().trend(1000).days());
    }
}
