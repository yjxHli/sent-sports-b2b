package com.sent.sports.coupon.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 券型策略枚举（R7）：把原先散落在 5 个类里的 8 段 {@code if (type == xxx)} 收拢成「每型一个常量」。
 *
 * <p>每个常量自己回答四个问题，新增券型只改这一个文件：</p>
 * <ol>
 *   <li>{@link #requireScope()}：是否必须指定 SPU/SKU 范围（品类券）；</li>
 *   <li>{@link #requireThreshold()} / {@link #normalizeThreshold(long)}：门槛是否必填、是否需要归零；</li>
 *   <li>{@link #customerScope()}：落库时的客户域（企业券恒为企业专属）；</li>
 *   <li>{@link #claimValidDays(long, int)}：会员日领券后的有效期。</li>
 * </ol>
 *
 * <p>为什么不是「策略接口 + 四个实现类」：券型是封闭集合、无运行时装配需求，
 * 枚举天然带编译期穷尽检查，新增券型时漏实现方法直接编译不过——这正是本次重构要的约束。</p>
 */
public enum CouponType {

    /** 无门槛券：门槛恒为 0；小额券只给 3 天有效期（促首单），其余 7 天。 */
    NO_THRESHOLD(Coupon.TYPE_NO_THRESHOLD, "无门槛券") {
        @Override
        public long normalizeThreshold(long thresholdCent) {
            return 0L;
        }

        @Override
        public int claimValidDays(long amountCent, int templateValidDays) {
            return amountCent <= SMALL_AMOUNT_CENT ? SMALL_NO_THRESHOLD_DAYS : NO_THRESHOLD_DAYS;
        }
    },

    /** 满减券：必须有门槛；会员日领券 15 天，覆盖完整决策周期。 */
    FULL_REDUCTION(Coupon.TYPE_FULL_REDUCTION, "满减券") {
        @Override
        public boolean requireThreshold() {
            return true;
        }

        @Override
        public int claimValidDays(long amountCent, int templateValidDays) {
            return FULL_REDUCTION_DAYS;
        }
    },

    /** 品类券：必须选 SPU/SKU 范围，否则等价于全场券；有效期沿用模板。 */
    CATEGORY(Coupon.TYPE_CATEGORY, "品类券") {
        @Override
        public boolean requireScope() {
            return true;
        }
    },

    /** 企业专属券：必须有门槛，客户域恒为 ENTERPRISE，跟着券型走而不是跟着表单走。 */
    ENTERPRISE(Coupon.TYPE_ENTERPRISE, "企业专属券") {
        @Override
        public boolean requireThreshold() {
            return true;
        }

        @Override
        public String customerScope() {
            return Coupon.CUSTOMER_ENTERPRISE;
        }
    };

    /** 无门槛小额券阈值：500 分 = 5 元，≤ 此值按 3 天，否则 7 天。 */
    static final long SMALL_AMOUNT_CENT = 500L;
    static final int SMALL_NO_THRESHOLD_DAYS = 3;
    static final int NO_THRESHOLD_DAYS = 7;
    static final int FULL_REDUCTION_DAYS = 15;

    private static final Map<String, CouponType> BY_CODE = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(CouponType::code, Function.identity()));

    private final String code;
    private final String label;

    CouponType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 库里的券型编码（coupon_type 列取值）。 */
    public String code() {
        return code;
    }

    /** 运营可读名称，用于错误提示。 */
    public String label() {
        return label;
    }

    /** 是否必须指定适用商品范围。 */
    public boolean requireScope() {
        return false;
    }

    /** 是否必须设置使用门槛（0 门槛等于发钱）。 */
    public boolean requireThreshold() {
        return false;
    }

    /** 门槛归一：无门槛券一律 0，其余保持原值（负数已在用例层收敛）。 */
    public long normalizeThreshold(long thresholdCent) {
        return thresholdCent;
    }

    /** 落库的客户域：企业专属券恒为 ENTERPRISE，其余 ALL。 */
    public String customerScope() {
        return Coupon.CUSTOMER_ALL;
    }

    /** 会员日领券后的有效期天数；未覆盖的券型沿用模板配置。 */
    public int claimValidDays(long amountCent, int templateValidDays) {
        return templateValidDays;
    }

    /**
     * 建模板时的券型约束（R7 第 2 步）：范围与门槛的必填规则交给券型自己判断，
     * 用例层只负责字段清洗与落库。
     *
     * @throws BizException {@code 40001}，文案直接面向运营
     */
    public void validateTemplate(String scopeType, String scopeIds, long thresholdCent) {
        if (requireScope() && (Coupon.SCOPE_ALL.equals(scopeType) || scopeIds == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "必须选择适用商品");
        }
        if (requireThreshold() && thresholdCent <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "必须设置使用门槛");
        }
    }

    /** 编码 → 券型；未知/空白返回 {@code null}（调用方决定是拒绝还是走历史兜底）。 */
    public static CouponType from(String code) {
        return code == null ? null : BY_CODE.get(code.trim().toUpperCase());
    }

    /** 编码 → 券型；未知编码抛 {@code 40001}（建模板等「必须合法」的场景）。 */
    public static CouponType of(String code) {
        CouponType type = from(code);
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "券型不合法");
        }
        return type;
    }
}
