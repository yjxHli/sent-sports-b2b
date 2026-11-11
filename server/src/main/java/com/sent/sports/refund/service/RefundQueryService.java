package com.sent.sports.refund.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.refund.dto.RefundDtos;

/**
 * 退款查询门面（P4-22 接口契约，跨模块可用）。
 * <p>用户端「我的售后 / 详情」仍为 P4 桩（需绑定当前客户后补齐）；管理端审核列表在收口阶段
 * 接入真实 MyBatis-Plus 分页查询（退款单左联订单取订单号）。</p>
 */
public interface RefundQueryService {

    /** 我的售后单：按当前用户 + 分页 + 可选状态过滤。 */
    PageResult<RefundDtos.RefundVO> listMyRefunds(PageQuery pageQuery, long customerId, String status);

    /** 售后详情与进度（按当前用户做归属隔离，非本人返回 NOT_FOUND）。 */
    RefundDtos.RefundDetailVO getDetail(String refundNo, long customerId);

    /** 管理端退款审核列表：status 为空时不过滤，联查订单号与申请原因。 */
    PageResult<RefundDtos.RefundAuditVO> listAuditRefunds(PageQuery pageQuery, String status);
}
