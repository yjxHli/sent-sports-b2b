package com.sent.sports.order.service;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 删除订单用例（淘宝式订单详情「删除订单」按钮，2026-11-15）。
 *
 * <p>三条口径：</p>
 * <ul>
 *   <li><b>软删不物删</b>：订单是发票、退款单、应收台账与库存流水的引用对象，物理删除会留下悬空引用，
 *       也没法回答「这笔钱怎么退的」；只写 {@code customer_deleted_at}，客户列表过滤它，其余读面照旧可见。</li>
 *   <li><b>只在终态可删</b>：待付款能取消、待发货能退款、待收货还能拒收——这些单删掉客服就找不到单了。
 *       允许 {@code COMPLETED / CANCELLED / CLOSED} 三种终态。</li>
 *   <li><b>越权统一 404</b>：他人订单与不存在的订单返回同一个错误码，不能用 403 泄露「这单存在」。</li>
 * </ul>
 */
@Service
public class DeleteOrderUseCase {

    /** 可删除的终态：交易已完成、已取消、已关闭。 */
    static final Set<String> DELETABLE_STATUS = Set.of("COMPLETED", "CANCELLED", "CLOSED");

    private final OrderMapper orderMapper;

    public DeleteOrderUseCase(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 删除（软删）某位客户的订单。
     *
     * @param orderNo    订单号
     * @param customerId 当前登录客户；为空表示未登录（管理端不走本用例）
     * @throws BizException 未登录 401、订单不存在或非本人 404、在途订单 409
     */
    public void delete(String orderNo, Long customerId) {
        // 先校验身份再查库：未登录不该产生任何订单信息泄露
        if (customerId == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        OrderPO order = orderMapper.selectByOrderNo(orderNo);
        if (order == null || !customerId.equals(order.getCustomerId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!DELETABLE_STATUS.contains(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "在途订单不能删除，请先完成收货或取消订单");
        }
        // 条件更新返回 0 行：已被删过一次（重复点击）或并发下已变更，按「找不到」处理
        if (orderMapper.markCustomerDeleted(orderNo, customerId) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }
}
