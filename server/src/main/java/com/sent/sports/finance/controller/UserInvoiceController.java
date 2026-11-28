package com.sent.sports.finance.controller;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.finance.dto.UserInvoiceDtos;
import com.sent.sports.finance.service.UserInvoiceService;
import com.sent.sports.finance.service.dto.InvoicePdfFile;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 用户端开票控制器：我的订单 → 申请开票；个人中心 → 我的发票。
 * <p>与管理端 {@code /api/admin/finance/invoices} 分离：这里只处理当前登录客户自己的订单与发票，
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

    /**
     * 我的发票列表。
     *
     * @param status 页签：{@code ISSUED} 已开具 / {@code OPEN} 申请中（待开 + 驳回 + 异常）/ 空=全部
     * @param keyword 订单号或发票号模糊匹配
     */
    @GetMapping
    public R<PageResult<UserInvoiceDtos.InvoiceRowVO>> mine(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(userInvoiceService.mine(requireUser(user), status, keyword, page));
    }

    /** 我的发票 → 未申请：已完成且仍可开票、但还没有有效申请的订单。 */
    @GetMapping("/pending")
    public R<PageResult<UserInvoiceDtos.PendingInvoiceVO>> pending(
            @ModelAttribute PageQuery page,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(userInvoiceService.pending(requireUser(user), page));
    }

    /**
     * 下载 / 预览自己的发票 PDF。
     * <p>前端以 blob 方式请求（带 Authorization 头），再交给浏览器 PDF 阅读器；
     * 直接在新标签页打开本地址不会带令牌，会得到 401。</p>
     */
    @GetMapping("/{invoiceId}/pdf")
    public ResponseEntity<byte[]> pdf(
            @PathVariable long invoiceId,
            @RequestParam(required = false, defaultValue = "false") boolean download,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        InvoicePdfFile file = userInvoiceService.pdf(requireUser(user), invoiceId);
        String disposition = (download ? "attachment" : "inline")
                + "; filename=\"invoice-" + invoiceId + ".pdf\""
                + "; filename*=UTF-8''" + rfc5987(file.fileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }

    /** RFC 5987 编码：URLEncoder 把空格编成 +，但 header 里必须还原成 %20。 */
    private static String rfc5987(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private long requireUser(CurrentUser user) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user.userId();
    }
}
