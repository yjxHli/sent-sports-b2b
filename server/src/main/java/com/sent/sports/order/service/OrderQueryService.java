package com.sent.sports.order.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.order.dto.OrderDtos;
import com.sent.sports.order.service.dto.OrderPaymentStatusDto;

import java.util.Optional;

/**
 * 订单查询门面（跨模块契约）：payment 等模块通过此接口获取订单支付态中立视图，
 * 不直接依赖 order 领域类型（ArchUnit 规则 4）。
 */
public interface OrderQueryService {
    Optional<OrderPaymentStatusDto> getPaymentStatus(String orderNo);

    /**
     * 我的订单：按当前用户 + 状态 + 关键字 + 分页。
     * <p>{@code keyword} 同时匹配订单号与商品名（用户可能只记得其中一种），
     * 过滤下推到 SQL，保证分页 total 与每页条数一致。</p>
     */
    PageResult<OrderDtos.OrderListItem> listMyOrders(Long customerId, String status, String keyword,
                                                     PageQuery pageQuery);

    /** 订单详情：四维度状态 + 金额（P4 桩，P5 接入仓储后替换）。 */
    OrderDtos.OrderVO getDetail(String orderNo);

    /**
     * 评价资格校验（V55）：该客户是否存在「含此商品（可限定 SKU）且已完成」的订单。
     *
     * <p>review 模块按下单事实判定「买过没买过」，自己不去读订单表（ArchUnit 规则 4），
     * 因此把这条只读查询收敛到订单门面里。{@code skuId} 为空时按商品级判定。</p>
     *
     * @return 命中的订单号；无资格返回 {@link java.util.Optional#empty()}
     */
    java.util.Optional<String> findCompletedOrderNoForReview(long customerId, long productId, Long skuId);
}
