package com.sent.sports.coupon.domain;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户持有优惠券（纯领域对象，不依赖任何框架）。
 *
 * <p>四类券型：无门槛券、满减券、品类券、企业专属券。金额单位「分」。
 * 品类券支持按 SPU/SKU 限定范围，并支持按折扣率（如 9 折）计算；其余券型按面额封顶。</p>
 *
 * <p>{@link #discountFor(long)} 的入参是「已按券范围筛过的可抵扣金额」，
 * 范围匹配由 {@link #matchesSku(long, Long)} 在应用层逐行完成。</p>
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
}
