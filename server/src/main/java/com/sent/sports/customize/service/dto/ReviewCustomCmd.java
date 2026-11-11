package com.sent.sports.customize.service.dto;

/**
 * 审核定制单命令（中立入参）：定制单号 + 动作(PASS/REJECT) + 驳回原因分类 + 说明 + 审核人。
 * spec §6.5.3：驳回必须选择原因分类并填写说明。
 * <p>{@code reviewerId} 由管理端控制器从 JWT 注入的 {@code CurrentUser} 传入，
 * 审图日志据此记录审核人；直接调用用例的集成测试可用四参构造省略（列可空）。</p>
 */
public record ReviewCustomCmd(String customOrderId, String action, String reasonCode,
                              String remark, Long reviewerId) {

    public ReviewCustomCmd(String customOrderId, String action, String reasonCode, String remark) {
        this(customOrderId, action, reasonCode, remark, null);
    }
}
