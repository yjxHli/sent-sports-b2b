package com.sent.sports.product.domain.pricing;

/**
 * 取价结果。
 * @param unitPriceCent 成交单价（分）
 * @param source        唯一取价来源
 * @param ruleId        命中的规则标识（落库 order_item.price_rule_id，管理端可举证）
 * @param belowCost     是否低于成本价（仅标记，不阻塞下单）
 */
public class PriceResult {

    private final long unitPriceCent;
    private final PriceSource source;
    private final String ruleId;
    private final boolean belowCost;

    public PriceResult(long unitPriceCent, PriceSource source, String ruleId, boolean belowCost) {
        this.unitPriceCent = unitPriceCent;
        this.source = source;
        this.ruleId = ruleId;
        this.belowCost = belowCost;
    }

    public long getUnitPriceCent() {
        return unitPriceCent;
    }

    public PriceSource getSource() {
        return source;
    }

    public String getRuleId() {
        return ruleId;
    }

    public boolean isBelowCost() {
        return belowCost;
    }
}
