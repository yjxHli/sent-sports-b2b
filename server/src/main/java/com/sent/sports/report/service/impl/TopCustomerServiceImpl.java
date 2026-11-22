package com.sent.sports.report.service.impl;

import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.report.dto.TopCustomerDtos;
import com.sent.sports.report.mapper.TopCustomerMapper;
import com.sent.sports.report.mapper.po.TopCustomerRow;
import com.sent.sports.report.service.TopCustomerService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 大客户采购排行服务（2026-11-21）。
 *
 * <p>口径：{@code paid_at} 时间窗 + 按 {@code customer_id} 聚合实付金额；
 * 数据范围由 Mapper 的 {@code @DataScope} 下推到 SQL，本层不重复判断角色。</p>
 *
 * <p>Mapper 返回全量聚合行（客户基数可控），Top N 截断与占比在内存完成，
 * 汇总字段因此恒为区间全量口径，占比分母不受 {@code limit} 影响。</p>
 */
@Service
public class TopCustomerServiceImpl implements TopCustomerService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final TopCustomerMapper mapper;
    private final Clock clock;

    public TopCustomerServiceImpl(TopCustomerMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public TopCustomerDtos.TopCustomerVO topCustomers(int days, int limit) {
        int safeDays = Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
        int safeLimit = Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, limit));
        LocalDate today = LocalDate.now(clock);
        LocalDate fromDate = today.minusDays(safeDays - 1L);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        List<TopCustomerRow> rows = mapper.selectTopCustomers(from, to);
        List<TopCustomerRow> valid = new ArrayList<>();
        long totalAmountCent = 0L;
        long totalOrderCount = 0L;
        for (TopCustomerRow row : rows) {
            if (row == null || row.getCustomerId() == null) {
                continue;
            }
            valid.add(row);
            totalAmountCent += longOf(row.getAmountCent());
            totalOrderCount += intOf(row.getOrderCount());
        }

        // SQL 已按金额降序；服务层再排一次，避免 Mapper 被改写后名次口径漂移
        valid.sort(Comparator
                .comparingLong((TopCustomerRow row) -> longOf(row.getAmountCent())).reversed()
                .thenComparing(Comparator
                        .comparingLong((TopCustomerRow row) -> intOf(row.getOrderCount())).reversed())
                .thenComparingLong(TopCustomerRow::getCustomerId));

        List<TopCustomerDtos.TopCustomerItemVO> items = new ArrayList<>(
                Math.min(safeLimit, valid.size()));
        int rank = 1;
        for (TopCustomerRow row : valid.subList(0, Math.min(safeLimit, valid.size()))) {
            long amountCent = longOf(row.getAmountCent());
            items.add(new TopCustomerDtos.TopCustomerItemVO(
                    rank++,
                    row.getCustomerId(),
                    nameOf(row),
                    row.getPhone() == null ? "" : row.getPhone(),
                    intOf(row.getOrderCount()),
                    amountCent,
                    MoneyUtil.toYuan(amountCent),
                    sharePctOf(amountCent, totalAmountCent)));
        }

        return new TopCustomerDtos.TopCustomerVO(
                fromDate.toString(), today.toString(), safeDays, safeLimit,
                totalAmountCent, MoneyUtil.toYuan(totalAmountCent),
                totalOrderCount, valid.size(), List.copyOf(items));
    }

    /** 名称兜底：SQL 已做三层回落，这里只防空白（避免前端出现空行）。 */
    private static String nameOf(TopCustomerRow row) {
        String name = row.getCustomerName();
        if (name == null || name.isBlank()) {
            return "客户#" + row.getCustomerId();
        }
        return name.trim();
    }

    private static String sharePctOf(long amountCent, long totalAmountCent) {
        if (totalAmountCent <= 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", amountCent * 100.0 / totalAmountCent);
    }

    private static long longOf(Long value) {
        return value == null ? 0L : value;
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }
}
