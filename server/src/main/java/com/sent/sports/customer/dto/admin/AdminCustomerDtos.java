package com.sent.sports.customer.dto.admin;

import com.sent.sports.member.domain.MemberLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 客户与会员管理接口 Web 层 DTO（P4-45）。
 * 请求体承载外部入参并带校验注解；响应体暴露给前端。
 * <p>授级目标等级在 Web 层由字符串映射为 {@link MemberLevel}（member 域枚举），
 * 避免在 controller 直接依赖其他模块 domain（ArchUnit 规则 4：跨模块只走 service）。</p>
 */
public final class AdminCustomerDtos {

    private AdminCustomerDtos() {
    }

    /** 改派请求：目标归属 + 原因。 */
    public record TransferCmd(
            @NotNull(message = "目标归属不能为空") Long toStaffId,
            @NotBlank(message = "改派原因不能为空") String reason) {
    }

    /** 人工授级请求：目标等级码 + 有效期截止毫秒 + 原因。 */
    public record GrantCmd(
            @NotBlank(message = "目标等级不能为空") String level,
            @NotNull(message = "有效期不能为空") Long validToMillis,
            @NotBlank(message = "授级原因不能为空") String reason) {

        /** 将等级码映射为 member 域枚举（转换发生在 Web 层，controller 不触碰其他模块 domain）。 */
        public MemberLevel toTargetLevel() {
            return MemberLevel.valueOf(level);
        }
    }

    /** 积分调整请求：增减额 + 原因 + 业务类型 + 业务单号。 */
    public record AdjustCmd(
            long delta,
            @NotBlank(message = "调整原因不能为空") String reason,
            String bizType,
            String bizNo) {
    }

    /** 改派响应。 */
    public record TransferVO(long customerId, long fromStaffId, long toStaffId) {
    }

    /**
     * 客户列表行：供「客户与会员」列表与其它页面选择客户使用。
     * 昵称可能为空，服务层用「客户+ID」兜底，前端不必再判空；
     * 字段名与前端 {@code CustomerVO} 对齐（level / createdAt / creditLimitCent）。
     *
     * <p>{@code creditLimitCent / creditUsedCent / enterpriseStatus} 来自
     * LEFT JOIN {@code enterprise_profile}：企业审核与授信页的「已授信 / 未授信」
     * 以及会员概览的额度都读这三个字段，缺了它们界面永远停在「未授信」。</p>
     */
    public record CustomerRow(long customerId, String customerNo, String name, String phone,
                              String type, String level, Long ownerStaffId, Long creditLimitCent,
                              long growthValue, long pointsBalance, long createdAt,
                              Long creditUsedCent, String enterpriseStatus) {

        /** 兼容旧调用方（不含企业授信列）的构造入口。 */
        public CustomerRow(long customerId, String customerNo, String name, String phone,
                           String type, String level, Long ownerStaffId, Long creditLimitCent,
                           long growthValue, long pointsBalance, long createdAt) {
            this(customerId, customerNo, name, phone, type, level, ownerStaffId, creditLimitCent,
                    growthValue, pointsBalance, createdAt, null, null);
        }
    }

    /** 授级响应。 */
    public record LevelVO(long customerId, String level, long validToMillis) {

        /** 由授级结果构造 VO（在 Web 层完成 MemberLevel→String 映射，controller 不触碰其他模块 domain）。 */
        public static LevelVO from(com.sent.sports.member.service.dto.GrantLevelResult r) {
            return new LevelVO(r.customerId(), r.level().name(), r.validToMillis());
        }
    }

    /** 积分调整响应。 */
    public record PointVO(long customerId, long beforePoints, long afterPoints, long delta) {
    }
}
