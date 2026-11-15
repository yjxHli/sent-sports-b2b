package com.sent.sports.finance.service;

import com.sent.sports.finance.dto.UserInvoiceDtos;

/**
 * 用户端开票服务（我的订单 → 申请开票）。
 * <p>与管理端 {@code AdminFinanceController} 的开票接口分属两侧：管理端是「按订单代客开票」，
 * 这里只处理当前登录客户对自己订单的申请与状态查询，归属一律由 {@code customerId} 收口，
 * 不允许通过参数指定他人订单。</p>
 */
public interface UserInvoiceService {

    /**
     * 提交开票申请。
     * <p>可开票口径 = 订单已支付（{@code PAID} / {@code PART_REFUNDED}）。同一订单已有
     * 待开（{@code APPLIED}）或已开（{@code ISSUED}）的申请时按幂等处理：不新增行，直接返回已有申请，
     * 避免用户重复点击产生多张重复发票。</p>
     *
     * @return 落库后的申请状态（含票面金额），前端据此渲染「已提交 / 已开具」
     */
    UserInvoiceDtos.InvoiceVO apply(long customerId, UserInvoiceDtos.ApplyCmd cmd);

    /**
     * 查询当前客户在某订单上最新一条开票申请；从未申请过返回 {@code null}。
     * <p>订单不存在或不属于当前客户时抛 {@code NOT_FOUND}——不区分「没有」与「不是你的」，
     * 避免用订单号探测他人订单是否存在。</p>
     */
    UserInvoiceDtos.InvoiceVO latest(long customerId, String orderNo);
}
