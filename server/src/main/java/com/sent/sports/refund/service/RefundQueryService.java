package com.sent.sports.refund.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.refund.dto.RefundDtos;

import java.util.List;
import java.util.Set;

/**
 * 退款查询门面（P4-22 接口契约，跨模块可用）。
 * <p>用户端「我的售后 / 详情」仍为 P4 桩（需绑定当前客户后补齐）；管理端审核列表在收口阶段
 * 接入真实 MyBatis-Plus 分页查询（退款单左联订单取订单号）。</p>
 */
public interface RefundQueryService {

    /** 我的售后单：按当前用户 + 分页 + 可选状态过滤。 */
    PageResult<RefundDtos.RefundVO> listMyRefunds(PageQuery pageQuery, long customerId, String status);

    /**
     * 我的售后计数（2026-11-16）：个人中心「退款/售后」角标用。
     *
     * <p>此前前端拿本地订单列表的 {@code refundStatus} 字段做布尔计数，而订单接口基本不回该字段，
     * 于是角标恒为 0、与实际退款单一笔也对不上。改为直查 {@code refund_order}：
     * {@code total}=全部售后单，{@code inFlight}=待审核 + 退款中。</p>
     */
    RefundDtos.RefundCountVO countMyRefunds(long customerId);

    /** 售后详情与进度（按当前用户做归属隔离，非本人返回 NOT_FOUND）。 */
    RefundDtos.RefundDetailVO getDetail(String refundNo, long customerId);

    /**
     * 管理端退款审核列表：status 为空时不过滤，联查订单号与申请原因。
     *
     * @param keyword 退款单号 / 订单号模糊匹配（两者都查，客服手上拿到哪个号都能定位）
     */
    PageResult<RefundDtos.RefundAuditVO> listAuditRefunds(PageQuery pageQuery, String status,
                                                         String keyword);

    /**
     * 管理端退款审核详情（单笔）：退款单 + 订单基本信息 + 客户 + 商品明细 + 物流一次返回。
     * <p>退款单不存在按契约抛 {@code NOT_FOUND}，不返回半截空对象（前端拿空壳会白屏）。</p>
     */
    RefundDtos.RefundAuditDetailVO getAuditDetail(String refundNo);

    /**
     * 管理端订单列表用：批量给出这批订单中存在在途退款单（PENDING / REFUNDING）的订单号。
     * <p>只读门面，order 模块经此接口取数，不直连 refund 的 Mapper（ArchUnit 规则 4）。</p>
     */
    Set<String> inFlightOrderNos(List<String> orderNos);
}
