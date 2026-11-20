package com.sent.sports.coupon.domain;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户持有优惠券（纯领域对象，不依赖任何框架）。
 *
 * <p>四类券型：无门槛券、满减券、品类券、企业专属券。金额单位「分」。
 * 品类券支持按 SPU/SKU 限定范围，并支持按折扣率（如 9 折）计算；其余券型按面额封顶。</p>
 *
 * <p>{@link #discountFor(long)} 的入参是「已按券范围筛过的可抵扣金额」，
 * 范围匹配由 {@link #matchesSku(long, Long)} 完成；「到底能不能用、能抵多少」的完整判定
 * 统一走 {@link #applicability(String, long, List, LocalDateTime)}（R9），调用方不得自己拼条件。
 */
public class Coupon {

    public static final String UNUSED = "UNUSED";
    public static final String USED = "USED";
    public static final String EXPIRED = "EXPIRED";

    public static final String TYPE_NO_THRESHOLD = "NO_THRESHOLD";
    public static final String TYPE_FULL_REDUCTION = "FULL_REDUCTION";
    public static final String TYPE_CATEGORY = "CATEGORY";
    public static final String TYPE_ENTERPRISE = "ENTERPRISE";

    public static final String DISCOUNT_AMOUNT = "AMOUNT";
    public static final String DISCOUNT_PERCENT = "PERCENT";

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_SPU = "SPU";
    public static final String SCOPE_SKU = "SKU";

    public static final String CUSTOMER_ALL = "ALL";
    public static final String CUSTOMER_ENTERPRISE = "ENTERPRISE";

    private final long id;
    private final long customerId;
    private final String couponNo;
    private final String name;
    private final long amountCent;
    private final long thresholdCent;
    private final String couponType;
    private final String discountType;
    private final int discountRate;
    private final String scopeType;
    private final String scopeIds;
    private final String customerScope;
    private final String source;
    private final String reason;
    private final LocalDateTime expireAt;
    private String status;
    private LocalDateTime usedAt;
    private String orderNo;

    /** 兼容旧调用：默认满减/金额/全场/所有客户。 */
    public Coupon(long id, long customerId, String couponNo, String name, long amountCent,
                  long thresholdCent, String status, String source, String reason,
                  LocalDateTime expireAt, LocalDateTime usedAt, String orderNo) {
        this(id, customerId, couponNo, name, amountCent, thresholdCent,
                thresholdCent > 0 ? TYPE_FULL_REDUCTION : TYPE_NO_THRESHOLD,
                DISCOUNT_AMOUNT, 100, SCOPE_ALL, null, CUSTOMER_ALL,
                status, source, reason, expireAt, usedAt, orderNo);
    }

    public Coupon(long id, long customerId, String couponNo, String name,
                  long amountCent, long thresholdCent, String couponType,
                  String discountType, int discountRate, String scopeType, String scopeIds,
                  String customerScope, String status, String source, String reason,
                  LocalDateTime expireAt, LocalDateTime usedAt, String orderNo) {
        this.id = id;
        this.customerId = customerId;
        this.couponNo = couponNo;
        this.name = name;
        this.amountCent = amountCent;
        this.thresholdCent = thresholdCent;
        this.couponType = couponType == null ? TYPE_FULL_REDUCTION : couponType;
        this.discountType = discountType == null ? DISCOUNT_AMOUNT : discountType;
        this.discountRate = discountRate <= 0 || discountRate > 100 ? 100 : discountRate;
        this.scopeType = scopeType == null ? SCOPE_ALL : scopeType;
        this.scopeIds = scopeIds;
        this.customerScope = customerScope == null ? CUSTOMER_ALL : customerScope;
        this.status = status;
        this.source = source;
        this.reason = reason;
        this.expireAt = expireAt;
        this.usedAt = usedAt;
        this.orderNo = orderNo;
    }

    /** 可用条件：未使用、未过期、可抵扣金额达到门槛。 */
    public boolean usableFor(long eligibleCent, LocalDateTime now) {
        return UNUSED.equals(status)
                && expireAt != null && !expireAt.isBefore(now)
                && eligibleCent >= thresholdCent;
    }

    /**
     * 券对「该客户 + 该笔订单」的完整适用性（R9 唯一真源）：客户域 → 券范围 → 门槛 → 抵扣额。
     *
     * <p>为什么必须收敛在这里：可用券列表与下单抵扣原先是两份实现（一份按 {@code goodsCent}、
     * 一份按逐行金额），口径漂移的典型症状就是「列表说这张券能用，结算却抵 0 元」。
     * 两侧现在都只调本方法，列表取 {@link Eligibility#usable()}，下单取 {@link Eligibility#discountCent()}。</p>
     *
     * <p>门槛按整单商品额（{@code goodsCent}，与 {@code /coupons/usable} 入参同口径）判定，
     * 抵扣额按「命中范围的行金额」计算——品类券只对命中 SPU/SKU 的行打折，这与运营对券的描述一致。</p>
     *
     * @param customerType 客户类型（{@code PERSON} / {@code ENTERPRISE}），企业券据此排除个人客户
     * @param goodsCent    整单商品额（含定制费、不含运费）
     * @param lines        订单行；为空表示「只判总额、不判范围」的老调用方。
     *                     <b>要拿到准确抵扣额必须带每行金额</b>，只判可用性时可以传 0
     * @param now          判定时刻，过期与否以它为准
     */
    public Eligibility applicability(String customerType, long goodsCent, List<Line> lines, LocalDateTime now) {
        if (isEnterpriseOnly() && !CUSTOMER_ENTERPRISE.equals(customerType)) {
            return Eligibility.UNAVAILABLE;
        }
        List<Line> rows = lines == null ? List.of() : lines;
        if (!rows.isEmpty() && rows.stream().noneMatch(line -> matchesSku(line.skuId(), line.spuId()))) {
            return Eligibility.UNAVAILABLE;
        }
        if (!usableFor(goodsCent, now)) {
            return Eligibility.UNAVAILABLE;
        }
        long eligible = rows.isEmpty()
                ? Math.max(0L, goodsCent)
                : rows.stream().filter(line -> matchesSku(line.skuId(), line.spuId()))
                        .mapToLong(line -> Math.max(0L, line.amountCent())).sum();
        return new Eligibility(true, eligible, discountFor(eligible));
    }

    /**
     * 券范围是否命中某一行。
     *
     * @param skuId 订单行 SKU
     * @param spuId 该 SKU 所属商品；SKU 券可为 null
     */
    public boolean matchesSku(long skuId, Long spuId) {
        if (SCOPE_ALL.equals(scopeType) || scopeType == null) {
            return true;
        }
        Set<Long> ids = scopeIdSet();
        if (SCOPE_SKU.equals(scopeType)) {
            return ids.contains(skuId);
        }
        if (SCOPE_SPU.equals(scopeType)) {
            return spuId != null && ids.contains(spuId);
        }
        return false;
    }

    /** 实际抵扣额：折扣券按支付比例计算，金额券按面额封顶，均不超过可抵扣金额。 */
    public long discountFor(long eligibleCent) {
        long eligible = Math.max(0L, eligibleCent);
        if (DISCOUNT_PERCENT.equals(discountType)) {
            return Math.min(eligible, Math.floorDiv(eligible * (100L - discountRate), 100L));
        }
        return Math.min(amountCent, eligible);
    }

    public void markUsed(String orderNo, LocalDateTime now) {
        this.status = USED;
        this.usedAt = now;
        this.orderNo = orderNo;
    }

    private Set<Long> scopeIdSet() {
        if (scopeIds == null || scopeIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopeIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    public long id() {
        return id;
    }

    public long customerId() {
        return customerId;
    }

    public String couponNo() {
        return couponNo;
    }

    public String name() {
        return name;
    }

    public long amountCent() {
        return amountCent;
    }

    public long thresholdCent() {
        return thresholdCent;
    }

    public String couponType() {
        return couponType;
    }

    public String discountType() {
        return discountType;
    }

    public int discountRate() {
        return discountRate;
    }

    public String scopeType() {
        return scopeType;
    }

    public String scopeIds() {
        return scopeIds;
    }

    public String customerScope() {
        return customerScope;
    }

    public boolean isEnterpriseOnly() {
        return CUSTOMER_ENTERPRISE.equals(customerScope);
    }

    public String status() {
        return status;
    }

    public String source() {
        return source;
    }

    public String reason() {
        return reason;
    }

    public LocalDateTime expireAt() {
        return expireAt;
    }

    public LocalDateTime usedAt() {
        return usedAt;
    }

    public String orderNo() {
        return orderNo;
    }

    /**
     * 参与适用性判定的订单行：SKU 主键 + 所属 SPU（SKU 券可为空）+ 行金额（分）。
     * <p>SPU 由调用方查好后传入：领域对象不该反向依赖商品模块的查询服务。</p>
     */
    public record Line(long skuId, Long spuId, long amountCent) {
    }

    /**
     * 适用性结论：是否可用、可抵扣基数、实际抵扣额（分）。
     * <p>{@code eligibleCent} 只在行金额真实传入时才有意义（下单侧）；列表侧只读 {@code usable}。</p>
     */
    public record Eligibility(boolean usable, long eligibleCent, long discountCent) {

        /** 不可用（客户域不符 / 范围不命中 / 已用已过期 / 未达门槛）的统一取值。 */
        public static final Eligibility UNAVAILABLE = new Eligibility(false, 0L, 0L);
    }
}
