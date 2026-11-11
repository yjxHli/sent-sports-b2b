package com.sent.sports.product.domain.pricing;

import java.util.ArrayList;
import java.util.List;

/**
 * 取价上下文（纯数据载体，无 Spring 依赖，可单测）。
 * 由调用方（order/cart 服务）在取价前装配：客户档案、企业协议、SKU 价格、阶梯、一客一价等。
 */
public class PriceContext {

    private CustomerType customerType = CustomerType.PERSON;
    private int memberDiscountRate = 100;          // 会员等级折扣率百分比，100=不打折
    private AgreementInfo agreement;                // 企业协议价（可空）
    private Long customerSkuPriceCent;              // 一客一价（有效期内，可空）
    private long retailPriceCent;                   // 零售价（分）
    private long floorPriceCent;                    // 底价（分）
    private long costPriceCent;                     // 成本价（分）
    private List<LadderTier> ladderTiers = new ArrayList<>(); // 阶梯价档位
    private int qty = 1;                            // 购买数量（用于阶梯命中）
    private boolean allowStacking = false;          // 客户档案显式开启的"折上折"开关

    private PriceContext() {
    }

    public static ContextBuilder builder() {
        return new ContextBuilder();
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public int getMemberDiscountRate() {
        return memberDiscountRate;
    }

    public AgreementInfo getAgreement() {
        return agreement;
    }

    public Long getCustomerSkuPriceCent() {
        return customerSkuPriceCent;
    }

    public long getRetailPriceCent() {
        return retailPriceCent;
    }

    public long getFloorPriceCent() {
        return floorPriceCent;
    }

    public long getCostPriceCent() {
        return costPriceCent;
    }

    public List<LadderTier> getLadderTiers() {
        return ladderTiers;
    }

    public int getQty() {
        return qty;
    }

    public boolean isAllowStacking() {
        return allowStacking;
    }

    /** 协议是否对企业客户有效。 */
    public boolean agreementValid() {
        return agreement != null && agreement.valid() && customerType == CustomerType.ENTERPRISE;
    }

    /** 命中阶梯的区间档位（取首个匹配；档位应互斥）。 */
    public LadderTier matchLadder() {
        for (LadderTier t : ladderTiers) {
            if (t.matches(qty)) {
                return t;
            }
        }
        return null;
    }

    public static final class ContextBuilder {
        private final PriceContext ctx = new PriceContext();

        public ContextBuilder customerType(CustomerType v) {
            ctx.customerType = v;
            return this;
        }

        public ContextBuilder memberDiscountRate(int v) {
            ctx.memberDiscountRate = v;
            return this;
        }

        public ContextBuilder agreement(AgreementInfo v) {
            ctx.agreement = v;
            return this;
        }

        public ContextBuilder customerSkuPriceCent(Long v) {
            ctx.customerSkuPriceCent = v;
            return this;
        }

        public ContextBuilder retailPriceCent(long v) {
            ctx.retailPriceCent = v;
            return this;
        }

        public ContextBuilder floorPriceCent(long v) {
            ctx.floorPriceCent = v;
            return this;
        }

        public ContextBuilder costPriceCent(long v) {
            ctx.costPriceCent = v;
            return this;
        }

        public ContextBuilder ladderTiers(List<LadderTier> v) {
            ctx.ladderTiers = v == null ? new ArrayList<>() : new ArrayList<>(v);
            return this;
        }

        public ContextBuilder qty(int v) {
            ctx.qty = v;
            return this;
        }

        public ContextBuilder allowStacking(boolean v) {
            ctx.allowStacking = v;
            return this;
        }

        public PriceContext build() {
            return ctx;
        }
    }
}
