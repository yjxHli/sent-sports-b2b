package com.sent.sports.report.service.impl;

import com.sent.sports.report.dto.DashboardReportDtos;
import com.sent.sports.report.mapper.DashboardReportMapper;
import com.sent.sports.report.mapper.po.SalesCountRow;
import com.sent.sports.report.mapper.po.SalesRankRow;
import com.sent.sports.report.service.DashboardReportService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售业绩排行与工作台看板实现。
 *
 * <p>此前两个报表在 {@code SystemServiceImpl} 里是恒返「占位」的 P4 桩，
 * 工作台另有三张卡前端从未赋值，页面看起来像没接后端。这里全部改为真库聚合：
 * 排行按 {@code orders.owner_staff_id} 左联 {@code sys_staff} 取姓名，
 * 六卡分别取今日成交、待发货、待审定制、待接入会话、预警 SKU、待审退款、逾期应收。</p>
 *
 * <p>时间用注入的 {@link Clock}（Asia/Shanghai），天数收敛到 1~90；
 * 0 是真实查询结果（例如今日无成交、当前无排队会话），由前端如实展示，不做数据伪造。</p>
 */
@Service
public class DashboardReportServiceImpl implements DashboardReportService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;
    private static final String UNASSIGNED = "未分配";

    private final DashboardReportMapper mapper;
    private final Clock clock;

    public DashboardReportServiceImpl(DashboardReportMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public List<DashboardReportDtos.SalesRankVO> salesRank(int days) {
        int safeDays = Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
        LocalDate today = LocalDate.now(clock);
        LocalDateTime from = today.minusDays(safeDays - 1L).atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        List<SalesRankRow> rows = mapper.selectSalesRank(from, to);
        List<DashboardReportDtos.SalesRankVO> result = new ArrayList<>(rows.size());
        int rank = 1;
        for (SalesRankRow row : rows) {
            if (row == null) {
                continue;
            }
            String name = row.getStaffName() == null || row.getStaffName().isBlank()
                    ? UNASSIGNED : row.getStaffName();
            result.add(new DashboardReportDtos.SalesRankVO(
                    rank++, row.getStaffId(), name,
                    intOf(row.getCustomerCount()), intOf(row.getOrderCount()),
                    longOf(row.getAmountCent())));
        }
        return List.copyOf(result);
    }

    @Override
    public DashboardReportDtos.DashboardSummaryVO dashboard() {
        LocalDate today = LocalDate.now(clock);
        SalesCountRow sales = mapper.selectSales(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        return new DashboardReportDtos.DashboardSummaryVO(
                today.toString(),
                sales == null ? 0L : longOf(sales.getOrderCount()),
                sales == null ? 0L : longOf(sales.getAmountCent()),
                mapper.countPendingShip(),
                mapper.countPendingCustomReview(),
                mapper.countWaitingChat(),
                mapper.countWarningSku(),
                mapper.countPendingRefund(),
                mapper.countOverdueReceivable());
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }

    private static long longOf(Long value) {
        return value == null ? 0L : value;
    }
}
