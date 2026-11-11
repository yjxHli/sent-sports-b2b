package com.sent.sports.member.domain;

/**
 * 会员聚合（member 模块，P3-45/P3-47）：纯 POJO，无 Spring 注解。
 * 持有等级、成长值、可用积分与授级有效期；等级只升不降，唯一回落为授级到期（expireGrantIfNeeded）。
 * 对应 spec §6.3.1 / §6.4。
 */
public class Member {

    private final long customerId;
    private MemberLevel level;
    private long growthValue;
    private long availablePoints;
    private long levelValidToMillis;
    private String lastGrantReason;

    public Member(long customerId, MemberLevel level, long growthValue, long availablePoints) {
        this.customerId = customerId;
        this.level = level;
        this.growthValue = growthValue;
        this.availablePoints = availablePoints;
        this.levelValidToMillis = 0L;
        this.lastGrantReason = null;
    }

    /** 人工授级：写入目标等级、有效期与原因（原因非空由用例层校验）。 */
    public void grantLevel(MemberLevel target, long validToMillis, String reason) {
        this.level = target;
        this.levelValidToMillis = validToMillis;
        this.lastGrantReason = reason;
    }

    /** 授级到期回落：若过了有效期，按当前成长值重算等级并清除授级标记（唯一降级路径）。 */
    public void expireGrantIfNeeded(long nowMillis) {
        if (levelValidToMillis > 0 && nowMillis > levelValidToMillis) {
            this.level = LevelCalculator.calcLevel(growthValue);
            this.levelValidToMillis = 0L;
            this.lastGrantReason = null;
        }
    }

    /** 调整积分：增量可正可负，允许负余额（负积分抵扣由 PointDeduction 在结算时按 0 处理）。 */
    public void adjustPoints(long delta) {
        this.availablePoints += delta;
    }

    /** 累计成长值：仅当新等级高于当前时升级（等级只升不降）。 */
    public void addGrowth(long growth) {
        this.growthValue += growth;
        MemberLevel up = LevelCalculator.calcLevel(growthValue);
        if (up.ordinal() > level.ordinal()) {
            this.level = up;
        }
    }

    public long getCustomerId() {
        return customerId;
    }

    public MemberLevel getLevel() {
        return level;
    }

    public long getGrowthValue() {
        return growthValue;
    }

    public long getAvailablePoints() {
        return availablePoints;
    }

    public long getLevelValidToMillis() {
        return levelValidToMillis;
    }

    public String getLastGrantReason() {
        return lastGrantReason;
    }
}
