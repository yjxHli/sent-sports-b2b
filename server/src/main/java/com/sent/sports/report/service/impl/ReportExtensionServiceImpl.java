package com.sent.sports.report.service.impl;

import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.report.dto.ReportExtensionDtos;
import com.sent.sports.report.mapper.ReportExtensionMapper;
import com.sent.sports.report.mapper.po.CustomerMixRow;
import com.sent.sports.report.mapper.po.ProductDetailRow;
import com.sent.sports.report.mapper.po.RepeatCustomerRow;
import com.sent.sports.report.service.ReportExtensionService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 拓展报表服务（2026-11-23）。
 *
 * <p>时间窗用注入的 Clock（Asia/Shanghai），天数收敛 1~90、明细条数收敛 1~50；
 * 数据范围由 Mapper 的 {@code @DataScope} 下推，本层不重复判断角色。</p>
 *
 * <p>占比一律以区间全量为分母，结果保留 1 位小数的字符串（避免浮点序列化噪音）；
 * 空区间不除零，三类结构仍返回齐全的固定项，前端环形图不会出现空壳。</p>
 */
@Service
public class ReportExtensionServiceImpl implements ReportExtensionService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final ReportExtensionMapper mapper;
    private final Clock clock;

    public ReportExtensionServiceImpl(ReportExtensionMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public ReportExtensionDtos.CustomerMixVO customerMix(int days) {
        Window window = window(days);
        List<CustomerMixRow> rows = mapper.selectCustomerMix(window.from, window.to);

        long enterpriseAmount = 0L;
        long enterpriseCustomers = 0L;
        long enterpriseOrders = 0L;
        long personAmount = 0L;
        long personCustomers = 0L;
        long personOrders = 0L;
        long unknownAmount = 0L;
        long unknownCustomers = 0L;
        long unknownOrders = 0L;
        for (CustomerMixRow row : rows) {
            if (row == null) {
                continue;
            }
            String type = row.getCustomerType() == null || row.getCustomerType().isBlank()
                    ? "UNKNOWN" : row.getCustomerType().trim().toUpperCase(Locale.ROOT);
            switch (type) {
                case "ENTERPRISE" -> {
                    enterpriseAmount += longOf(row.getAmountCent());
                    enterpriseCustomers += longOf(row.getCustomerCount());
                    enterpriseOrders += longOf(row.getOrderCount());
                }
                case "PERSON" -> {
                    personAmount += longOf(row.getAmountCent());
                    personCustomers += longOf(row.getCustomerCount());
                    personOrders += longOf(row.getOrderCount());
                }
                default -> {
                    unknownAmount += longOf(row.getAmountCent());
                    unknownCustomers += longOf(row.getCustomerCount());
                    unknownOrders += longOf(row.getOrderCount());
                }
            }
        }

        long total = enterpriseAmount + personAmount + unknownAmount;
        List<ReportExtensionDtos.CustomerMixItemVO> items = List.of(
                mixItem("ENTERPRISE", "企业客户", enterpriseCustomers, enterpriseOrders, enterpriseAmount, total),
                mixItem("PERSON", "个人客户", personCustomers, personOrders, personAmount, total),
                mixItem("UNKNOWN", "未分类", unknownCustomers, unknownOrders, unknownAmount, total));

        return new ReportExtensionDtos.CustomerMixVO(
                window.fromDate.toString(), window.today.toString(), window.days,
                total, MoneyUtil.toYuan(total), List.copyOf(items));
    }

    @Override
    public ReportExtensionDtos.ProductDetailVO productDetails(int days, int limit) {
        Window window = window(days);
        int safeLimit = Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, limit));
        List<ProductDetailRow> rows = mapper.selectProductDetails(window.from, window.to);

        List<ProductDetailRow> valid = new ArrayList<>();
        long totalAmount = 0L;
        long totalQty = 0L;
        long totalProfit = 0L;
        for (ProductDetailRow row : rows) {
            if (row == null || row.getSkuId() == null) {
                continue;
            }
            long amount = longOf(row.getAmountCent());
            long cost = longOf(row.getCostPriceCent()) * longOf(row.getQty());
            valid.add(row);
            totalAmount += amount;
            totalQty += longOf(row.getQty());
            totalProfit += amount - cost;
        }
        valid.sort(Comparator
                .comparingLong((ProductDetailRow row) -> longOf(row.getAmountCent())).reversed()
                .thenComparingLong(ProductDetailRow::getSkuId));

        List<ReportExtensionDtos.ProductDetailItemVO> items = new ArrayList<>(
                Math.min(safeLimit, valid.size()));
        int rank = 1;
        for (ProductDetailRow row : valid.subList(0, Math.min(safeLimit, valid.size()))) {
            long amount = longOf(row.getAmountCent());
            long profit = amount - longOf(row.getCostPriceCent()) * longOf(row.getQty());
            items.add(new ReportExtensionDtos.ProductDetailItemVO(
                    rank++,
                    row.getSkuId(),
                    blankTo(row.getSkuName(), "商品#" + row.getSkuId()),
                    row.getSpec() == null ? "" : row.getSpec(),
                    longOf(row.getQty()),
                    amount,
                    MoneyUtil.toYuan(amount),
                    profit,
                    MoneyUtil.toYuan(profit),
                    pct(profit, amount),
                    row.getPriceSources() == null ? "" : row.getPriceSources(),
                    intOf(row.getStockQty()),
                    intOf(row.getOrderCount())));
        }

        return new ReportExtensionDtos.ProductDetailVO(
                window.fromDate.toString(), window.today.toString(), window.days, safeLimit,
                totalAmount, MoneyUtil.toYuan(totalAmount), totalQty, totalProfit,
                pct(totalProfit, totalAmount), valid.size(), List.copyOf(items));
    }

    @Override
    public ReportExtensionDtos.RepeatCustomerVO repeatCustomers(int days) {
        Window window = window(days);
        List<RepeatCustomerRow> rows = mapper.selectRepeatCustomers(window.from, window.to);

        long newCustomers = 0L;
        long newAmount = 0L;
        long repeatCustomers = 0L;
        long repeatAmount = 0L;
        long totalOrders = 0L;
        long[] bucketCounts = new long[BUCKET_LABELS.length];
        long[] bucketAmounts = new long[BUCKET_LABELS.length];
        for (RepeatCustomerRow row : rows) {
            if (row == null || row.getCustomerId() == null) {
                continue;
            }
            long amount = longOf(row.getPeriodAmountCent());
            totalOrders += intOf(row.getPeriodOrderCount());
            if (row.getFirstPaidAt() != null && row.getFirstPaidAt().isBefore(window.from)) {
                repeatCustomers++;
                repeatAmount += amount;
                int bucket = bucketOf(intOf(row.getTotalOrderCount()));
                bucketCounts[bucket]++;
                bucketAmounts[bucket] += amount;
            } else {
                newCustomers++;
                newAmount += amount;
            }
        }
        long totalCustomers = newCustomers + repeatCustomers;
        String avgOrder = totalCustomers == 0 ? "0.0"
                : String.format(Locale.ROOT, "%.1f", totalOrders * 1.0 / totalCustomers);

        List<ReportExtensionDtos.FrequencyBucketVO> buckets = new ArrayList<>(BUCKET_LABELS.length);
        for (int i = 0; i < BUCKET_LABELS.length; i++) {
            buckets.add(new ReportExtensionDtos.FrequencyBucketVO(
                    BUCKET_LABELS[i], bucketCounts[i], bucketAmounts[i],
                    MoneyUtil.toYuan(bucketAmounts[i]),
                    pct(bucketCounts[i], repeatCustomers)));
        }

        return new ReportExtensionDtos.RepeatCustomerVO(
                window.fromDate.toString(), window.today.toString(), window.days,
                totalCustomers, newCustomers, repeatCustomers,
                pctDecimal(repeatCustomers * 100.0 / Math.max(1L, totalCustomers), totalCustomers == 0),
                newAmount, MoneyUtil.toYuan(newAmount),
                repeatAmount, MoneyUtil.toYuan(repeatAmount), avgOrder,
                List.copyOf(buckets));
    }

    /** 复购频次档位：历史总订单 2 单 / 3–5 单 / 6 单以上。 */
    private static final String[] BUCKET_LABELS = {"2 单", "3–5 单", "6 单以上"};

    private static int bucketOf(int totalOrderCount) {
        if (totalOrderCount <= 2) {
            return 0;
        }
        return totalOrderCount <= 5 ? 1 : 2;
    }

    private Window window(int days) {
        int safeDays = Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
        LocalDate today = LocalDate.now(clock);
        return new Window(today.minusDays(safeDays - 1L), today, safeDays,
                today.minusDays(safeDays - 1L).atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private record Window(LocalDate fromDate, LocalDate today, int days,
                          LocalDateTime from, LocalDateTime to) {
    }

    private static ReportExtensionDtos.CustomerMixItemVO mixItem(String type, String label,
                                                                  long customers, long orders,
                                                                  long amount, long total) {
        return new ReportExtensionDtos.CustomerMixItemVO(
                type, label, customers, orders, amount, MoneyUtil.toYuan(amount), pct(amount, total));
    }

    /** 占比 = 分子 / 分母 × 100，1 位小数；分母为 0 时给 0.0。 */
    private static String pct(long numerator, long denominator) {
        return pctDecimal(numerator * 100.0 / Math.max(1L, denominator), denominator <= 0);
    }

    private static String pctDecimal(double value, boolean empty) {
        return String.format(Locale.ROOT, "%.1f", empty ? 0.0 : value);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long longOf(Long value) {
        return value == null ? 0L : value;
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }
}
