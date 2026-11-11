package com.sent.sports.report.service.impl;

import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.report.dto.SalesTrendDtos;
import com.sent.sports.report.mapper.SalesTrendMapper;
import com.sent.sports.report.mapper.po.SalesTrendRow;
import com.sent.sports.report.service.SalesTrendService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售趋势服务实现（批 4）。
 *
 * <p>口径：以 {@code orders.paid_at} 为时间基准、严格按 {@code SUM(paid_cent)} 聚合，
 * 只统计 {@code pay_status IN ('PAID','PART_REFUNDED')}；全额退款 {@code FULL_REFUNDED}
 * 与未支付订单由 SQL 整单剔除。近 N 天返回<b>连续</b>日期序列，无成交日补零，
 * 便于前端直接画趋势而不必自行补点。</p>
 *
 * <p>时间用注入的 {@link Clock}（Asia/Shanghai），不直接读系统时钟；天数收敛到 1~90，
 * 避免一次查询扫描过大范围。只依赖本模块 Mapper 与 common，符合 ArchUnit 规则 4。</p>
 */
@Service
public class SalesTrendServiceImpl implements SalesTrendService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;

    private final SalesTrendMapper mapper;
    private final Clock clock;

    public SalesTrendServiceImpl(SalesTrendMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public SalesTrendDtos.SalesTrendVO trend(int days) {
        int safeDays = Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
        LocalDate today = LocalDate.now(clock);
        LocalDate fromDate = today.minusDays(safeDays - 1L);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        Map<String, SalesTrendRow> byDay = new HashMap<>();
        for (SalesTrendRow row : mapper.selectDailyTrend(from, to)) {
            if (row != null && row.getDay() != null) {
                byDay.put(row.getDay(), row);
            }
        }

        long totalAmountCent = 0L;
        int totalOrderCount = 0;
        List<SalesTrendDtos.SalesTrendPointVO> points = new ArrayList<>(safeDays);
        for (int i = 0; i < safeDays; i++) {
            String date = fromDate.plusDays(i).toString();
            SalesTrendRow row = byDay.get(date);
            long amountCent = row == null || row.getAmountCent() == null ? 0L : row.getAmountCent();
            int orderCount = row == null || row.getOrderCount() == null ? 0 : row.getOrderCount();
            long avgOrderCent = orderCount == 0 ? 0L : amountCent / orderCount;
            points.add(new SalesTrendDtos.SalesTrendPointVO(
                    date, amountCent, MoneyUtil.toYuan(amountCent), orderCount, avgOrderCent));
            totalAmountCent += amountCent;
            totalOrderCount += orderCount;
        }

        long avgOrderCent = totalOrderCount == 0 ? 0L : totalAmountCent / totalOrderCount;
        return new SalesTrendDtos.SalesTrendVO(
                fromDate.toString(), today.toString(), safeDays,
                totalAmountCent, MoneyUtil.toYuan(totalAmountCent),
                totalOrderCount, avgOrderCent, MoneyUtil.toYuan(avgOrderCent), points);
    }
}
