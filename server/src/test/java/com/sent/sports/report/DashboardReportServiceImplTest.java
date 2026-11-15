package com.sent.sports.report;

import com.sent.sports.report.dto.DashboardReportDtos;
import com.sent.sports.report.mapper.DashboardReportMapper;
import com.sent.sports.report.mapper.po.SalesCountRow;
import com.sent.sports.report.mapper.po.SalesRankRow;
import com.sent.sports.report.service.impl.DashboardReportServiceImpl;
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
 * 销售业绩排行 / 工作台看板服务（TDD）：时间窗下推、排行名次与金额口径、六卡计数来源。
 * SQL 本身的口径由 MapperIT 真库校验，这里只锁定服务层映射与参数。
 */
@ExtendWith(MockitoExtension.class)
class DashboardReportServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private DashboardReportMapper mapper;

    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // 2026-11-12 10:00 +08:00
        fixedClock = Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), ZONE);
    }

    private DashboardReportServiceImpl service() {
        return new DashboardReportServiceImpl(mapper, fixedClock);
    }

    @Test
    @DisplayName("近 7 天排行时间窗是 [11-06 00:00, 11-13 00:00)，由 SQL 下推")
    void salesRankPushesDateWindow() {
        when(mapper.selectSalesRank(any(), any())).thenReturn(List.of());

        service().salesRank(7);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectSalesRank(from.capture(), to.capture());
        assertEquals(LocalDateTime.of(2026, 11, 6, 0, 0), from.getValue());
        assertEquals(LocalDateTime.of(2026, 11, 13, 0, 0), to.getValue());
    }

    @Test
    @DisplayName("天数收敛到 1~90，避免一次扫描过大区间")
    void salesRankClampsDays() {
        when(mapper.selectSalesRank(any(), any())).thenReturn(List.of());

        service().salesRank(999);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectSalesRank(from.capture(), any());
        assertEquals(LocalDateTime.of(2026, 8, 15, 0, 0), from.getValue());
    }

    @Test
    @DisplayName("排行按 SQL 降序结果连续编名次，未分配归属兜底为「未分配」")
    void salesRankMapsRows() {
        when(mapper.selectSalesRank(any(), any())).thenReturn(List.of(
                rankRow(9001L, "超级管理员", 40, 88, 998000L),
                rankRow(null, null, 12, 76, 1316700L)));

        List<DashboardReportDtos.SalesRankVO> ranks = service().salesRank(30);

        assertEquals(2, ranks.size());
        assertEquals(1, ranks.get(0).rank());
        assertEquals("超级管理员", ranks.get(0).name());
        assertEquals(998000L, ranks.get(0).amountCent());
        assertEquals(2, ranks.get(1).rank());
        assertEquals("未分配", ranks.get(1).name());
        assertEquals(76, ranks.get(1).orderCount());
    }

    @Test
    @DisplayName("聚合字段为 NULL 时按 0 兜底，不抛 NPE")
    void salesRankHandlesNullAggregates() {
        when(mapper.selectSalesRank(any(), any())).thenReturn(List.of(
                rankRow(9005L, "销售员", null, null, null)));

        var rank = service().salesRank(30).get(0);

        assertEquals(0, rank.customerCount());
        assertEquals(0, rank.orderCount());
        assertEquals(0L, rank.amountCent());
    }

    @Test
    @DisplayName("看板六卡与待办数量全部取自真库计数，今日窗口只覆盖当天")
    void dashboardReadsAllCounts() {
        SalesCountRow today = new SalesCountRow();
        today.setOrderCount(3L);
        today.setAmountCent(128800L);
        when(mapper.selectSales(any(), any())).thenReturn(today);
        when(mapper.countPendingShip()).thenReturn(303L);
        when(mapper.countPendingCustomReview()).thenReturn(2L);
        when(mapper.countWaitingChat()).thenReturn(1L);
        when(mapper.countWarningSku()).thenReturn(3L);
        when(mapper.countPendingRefund()).thenReturn(7L);
        when(mapper.countOverdueReceivable()).thenReturn(39L);

        var vo = service().dashboard();

        assertEquals("2026-11-12", vo.date());
        assertEquals(3L, vo.todayOrderCount());
        assertEquals(128800L, vo.todayAmountCent());
        assertEquals(303L, vo.pendingShipCount());
        assertEquals(2L, vo.pendingCustomReviewCount());
        assertEquals(1L, vo.pendingChatCount());
        assertEquals(3L, vo.warningSkuCount());
        assertEquals(7L, vo.pendingRefundCount());
        assertEquals(39L, vo.overdueReceivableCount());

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectSales(from.capture(), to.capture());
        assertEquals(LocalDateTime.of(2026, 11, 12, 0, 0), from.getValue());
        assertEquals(LocalDateTime.of(2026, 11, 13, 0, 0), to.getValue());
    }

    @Test
    @DisplayName("今日无成交时如实返回 0，不伪造占位数据")
    void dashboardHandlesNoSalesToday() {
        when(mapper.selectSales(any(), any())).thenReturn(null);

        var vo = service().dashboard();

        assertEquals(0L, vo.todayOrderCount());
        assertEquals(0L, vo.todayAmountCent());
    }

    private static SalesRankRow rankRow(Long staffId, String name, Integer customers,
                                        Integer orders, Long amountCent) {
        SalesRankRow row = new SalesRankRow();
        row.setStaffId(staffId);
        row.setStaffName(name);
        row.setCustomerCount(customers);
        row.setOrderCount(orders);
        row.setAmountCent(amountCent);
        return row;
    }
}
