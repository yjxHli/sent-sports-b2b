package com.sent.sports.report.service.impl;

import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.report.dto.HotProductDtos;
import com.sent.sports.report.mapper.HotProductMapper;
import com.sent.sports.report.mapper.po.HotProductRow;
import com.sent.sports.report.service.HotProductService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 热销商品报表服务（2026-11-21）。
 *
 * <p>口径：{@code paid_at} 时间窗 + 按 {@code sku_id} 聚合 {@code SUM(amount_cent)} / {@code SUM(qty)}，
 * 只算 PAID / PART_REFUNDED；数据范围由 Mapper 的 {@code @DataScope} 下推到 SQL，
 * 本层不重复判断角色（否则两处实现必然漂移）。</p>
 *
 * <p>Top N 截断与占比在内存完成：Mapper 返回全量 SKU 聚合行（基数数百），
 * 总额、总件数、SKU 数因此天然是全量口径，不受 {@code limit} 影响 —— 占比分母必须稳定。</p>
 */
@Service
public class HotProductServiceImpl implements HotProductService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final HotProductMapper mapper;
    private final Clock clock;

    public HotProductServiceImpl(HotProductMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public HotProductDtos.HotProductVO hotProducts(int days, int limit) {
        int safeDays = Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
        int safeLimit = Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, limit));
        LocalDate today = LocalDate.now(clock);
        LocalDate fromDate = today.minusDays(safeDays - 1L);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        List<HotProductRow> rows = mapper.selectHotProducts(from, to);
        List<HotProductRow> valid = new ArrayList<>();
        long totalAmountCent = 0L;
        long totalQty = 0L;
        for (HotProductRow row : rows) {
            if (row == null || row.getSkuId() == null) {
                continue;
            }
            valid.add(row);
            totalAmountCent += longOf(row.getAmountCent());
            totalQty += longOf(row.getQty());
        }

        // SQL 已按金额降序；服务层再排一次，避免 Mapper 被改写后名次口径漂移
        valid.sort(Comparator
                .comparingLong((HotProductRow row) -> longOf(row.getAmountCent())).reversed()
                .thenComparing(Comparator
                        .comparingLong((HotProductRow row) -> longOf(row.getQty())).reversed())
                .thenComparingLong(HotProductRow::getSkuId));

        List<HotProductDtos.HotProductItemVO> items = new ArrayList<>(
                Math.min(safeLimit, valid.size()));
        int rank = 1;
        for (HotProductRow row : valid.subList(0, Math.min(safeLimit, valid.size()))) {
            long amountCent = longOf(row.getAmountCent());
            items.add(new HotProductDtos.HotProductItemVO(
                    rank++,
                    row.getSkuId(),
                    nameOf(row),
                    row.getSpec() == null ? "" : row.getSpec(),
                    longOf(row.getQty()),
                    amountCent,
                    MoneyUtil.toYuan(amountCent),
                    intOf(row.getOrderCount()),
                    sharePctOf(amountCent, totalAmountCent)));
        }

        return new HotProductDtos.HotProductVO(
                fromDate.toString(), today.toString(), safeDays, safeLimit,
                totalAmountCent, MoneyUtil.toYuan(totalAmountCent),
                totalQty, valid.size(), List.copyOf(items));
    }

    /** 快照为空 / 空白都回落「商品#{skuId}」，保证前端图例不出现空名。 */
    private static String nameOf(HotProductRow row) {
        String name = row.getSkuName();
        if (name == null || name.isBlank()) {
            return "商品#" + row.getSkuId();
        }
        return name.trim();
    }

    /** 占比保留 1 位小数；总额为 0 时给 0.0，不做除零。 */
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
