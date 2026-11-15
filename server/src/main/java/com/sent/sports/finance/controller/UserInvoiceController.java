package com.sent.sports.finance.controller;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.finance.dto.UserInvoiceDtos;
import com.sent.sports.finance.service.UserInvoiceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端开票控制器：我的订单 → 申请开票 / 查开票状态。
 * <p>与管理端 {@code /api/admin/finance/invoices} 分离：这里只处理当前登录客户自己的订单，
 * 客户身份取自 {@link CurrentUser}（aud=user），不接受请求体传 customerId，
 * 从根上避免借参数越权给他人订单开票。</p>
 */
@RestController
@RequestMapping("/api/invoices")
public class UserInvoiceController {

    private final UserInvoiceService userInvoiceService;

    public UserInvoiceController(UserInvoiceService userInvoiceService) {
        this.userInvoiceService = userInvoiceService;
    }

    /** 提交开票申请；同一订单重复提交按幂等返回已有申请。 */
    @PostMapping
    @RequirePerm("invoice:apply")
    public R<UserInvoiceDtos.InvoiceVO> apply(
            @Valid @RequestBody UserInvoiceDtos.ApplyCmd cmd,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(userInvoiceService.apply(requireUser(user), cmd));
    }

    /**
     * 查询该订单最新的开票申请；从未申请过时 {@code data} 为 null。
     * <p>前端据此把「申请开票」按钮换成「已提交 / 已开具（下载 PDF）」，不需要在订单列表里多带字段。</p>
     */
    @GetMapping("/order/{orderNo}")
    public R<UserInvoiceDtos.InvoiceVO> latest(
            @PathVariable String orderNo,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(userInvoiceService.latest(requireUser(user), orderNo));
    }

    private long requireUser(CurrentUser user) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user.userId();
    }
}
