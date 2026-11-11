package com.sent.sports.order.service.admin;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.order.dto.admin.AdminOrderDtos;
import com.sent.sports.order.service.dto.ShipOrderResult;

/**
 * 订单管理（管理端）服务接口（P4-40 契约）。
 * <p>代客下单/拆单/关单实体与仓储尚未建设，当前由 {@code OrderAdminServiceImpl} 桩实现占位；
 * 发货委托同模块用例 {@link com.sent.sports.order.service.ShipOrderUseCase}；
 * 列表委托同模块门面 {@link com.sent.sports.order.service.OrderQueryService}。</p>
 */
public interface OrderAdminService {

    /** 代客下单。 */
    AdminOrderDtos.AdminOrderVO agentCreate(AdminOrderDtos.AgentCreateCmd cmd);

    /** 拆单。 */
    AdminOrderDtos.AdminOrderVO split(AdminOrderDtos.SplitCmd cmd);

    /** 发货（委托 ShipOrderUseCase）。 */
    ShipOrderResult ship(String orderNo, AdminOrderDtos.ShipCmd cmd);

    /** 关单：真实状态机 + 全额退款 + 库存/授信补偿 + 操作日志；订单号必须是业务单号。 */
    AdminOrderDtos.AdminOrderVO closeOrder(String orderNo, String reason);

    /**
     * 列表：status 为空时全量分页；发货工作台传 WAIT_SHIP / PART_SHIPPED 只取可发货单。
     * <p>此前接口只接 PageQuery，前端传的 status 被静默丢弃，导致选到已完成订单后发货 409。</p>
     */
    PageResult<AdminOrderDtos.AdminOrderVO> list(PageQuery page, String status);

    /** 详情：订单头 + 金额 + 订单行（真实查询；订单号不存在抛 NOT_FOUND）。 */
    AdminOrderDtos.AdminOrderDetailVO detail(String orderNo);
}
