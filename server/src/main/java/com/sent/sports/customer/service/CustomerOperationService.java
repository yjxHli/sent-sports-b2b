package com.sent.sports.customer.service;

import com.sent.sports.customer.dto.admin.AdminCustomerDtos;

/**
 * 客户运营命令服务（管理端「客户与会员」页）。
 *
 * <p>早期 {@code AdminCustomerController} 直连的三个 P3 用例挂在进程内内存仓储上，
 * 按真库客户 ID 查询必然落空（40401），授级还会因等级码与 member 枚举不一致触发 50001。
 * 那三个用例及其内存仓储已于 2026-11-28 P2 批次删除。</p>
 *
 * <p>本服务把管理端的三个写动作统一收敛到真库 {@code customer} 表：
 * 条件更新 + 落流水，客户不存在才抛 NOT_FOUND，等级码白名单校验给明确的参数错误。
 * P3 用例与用户端 {@code /api/member/*} 接口保持不变，互不影响。</p>
 */
public interface CustomerOperationService {

    /** 改派归属：只更新当前归属并写操作流水，不改写历史订单归属。 */
    AdminCustomerDtos.TransferVO transfer(long customerId, long toStaffId, String reason);

    /** 人工授级：等级码须在真库值域内；有效期与原因记入操作流水。 */
    AdminCustomerDtos.LevelVO grantLevel(long customerId, String level, long validToMillis, String reason);

    /** 积分调整：增量落库并写 point_log 流水。 */
    AdminCustomerDtos.PointVO adjustPoints(long customerId, long delta, String reason,
                                           String bizType, String bizNo);
}
