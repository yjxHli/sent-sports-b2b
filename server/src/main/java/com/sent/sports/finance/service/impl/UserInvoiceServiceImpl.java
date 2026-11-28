package com.sent.sports.finance.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.util.TimeUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.finance.dto.UserInvoiceDtos;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.po.InvoiceApplyRow;
import com.sent.sports.finance.mapper.po.InvoiceNotifyRow;
import com.sent.sports.finance.mapper.po.MyOrderStatePO;
import com.sent.sports.finance.mapper.po.PendingInvoiceRow;
import com.sent.sports.finance.mapper.po.UserInvoiceRow;
import com.sent.sports.finance.service.InvoiceNotificationService;
import com.sent.sports.finance.service.InvoicePdfService;
import com.sent.sports.finance.service.UserInvoiceService;
import com.sent.sports.finance.service.dto.InvoicePdfFile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 用户端开票实现。
 * <p>三个必须守住的点：
 * <ul>
 *   <li><b>归属</b>：所有 SQL 都带 {@code customer_id}，越权订单查不到行，直接按 {@code NOT_FOUND} 处理，
 *       不给「订单存在但不是你的」这一信息；</li>
 *   <li><b>可开票口径</b>（2026-11-15 用户要求收紧）：订单必须 <b>已完成</b>（{@code COMPLETED}），
 *       且支付状态仍在 {@code PAID / PART_REFUNDED}。在途订单（待发货 / 待收货）货还没到手、
 *       可能退换，票面金额与实际成交不一致，财务无法核销；整单全退（{@code FULL_REFUNDED}）
 *       的钱已经退回，也不该再开票；</li>
 *   <li><b>幂等</b>：同一订单已有 {@code APPLIED} / {@code ISSUED} 时由 SQL 层的 {@code NOT EXISTS} 拦住，
 *       插入 0 行不回错，而是回读已有申请返回，用户重复点击只会看到同一张发票。</li>
 * </ul>
 * 票面金额不落 {@code invoice_request}，一律联查 {@code orders.payable_cent}，与管理端发票列表同口径。</p>
 */
@Service
public class UserInvoiceServiceImpl implements UserInvoiceService {

    /** 订单主状态必须已完成，才能申请开票。 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /** 允许开票的支付状态：已完成且仍有实付金额；部分退款按订单应付票面开。 */
    private static final Set<String> PAYABLE_STATUS = Set.of("PAID", "PART_REFUNDED");

    private static final String TYPE_NORMAL = "NORMAL";
    private static final String TYPE_SPECIAL = "SPECIAL";
    /** 拒绝原因同时写进异常与站内信，用户看不到接口报错时也能从消息中心知道为什么。 */
    private static final String NOT_ELIGIBLE_REASON = "订单完成后才能申请开票";

    /** 我的发票页签：已开具。 */
    private static final String TAB_ISSUED = "ISSUED";
    /** 我的发票页签：申请中（待开 / 驳回 / 异常都需要继续处理）。 */
    private static final String TAB_OPEN = "OPEN";
    private static final String INVOICE_ISSUED = "ISSUED";

    private final InvoiceMapper invoiceMapper;
    private final InvoiceNotificationService invoiceNotificationService;
    private final InvoicePdfService invoicePdfService;

    public UserInvoiceServiceImpl(InvoiceMapper invoiceMapper,
                                  InvoiceNotificationService invoiceNotificationService,
                                  InvoicePdfService invoicePdfService) {
        this.invoiceMapper = invoiceMapper;
        this.invoiceNotificationService = invoiceNotificationService;
        this.invoicePdfService = invoicePdfService;
    }

    @Override
    @UseCase
    public UserInvoiceDtos.InvoiceVO apply(long customerId, UserInvoiceDtos.ApplyCmd cmd) {
        String orderNo = trim(cmd.orderNo());
        if (orderNo == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单号不能为空");
        }
        String title = trim(cmd.title());
        if (title == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票抬头不能为空");
        }
        String type = normalizeType(cmd.type());
        String taxNo = trim(cmd.taxNo());
        if (TYPE_SPECIAL.equals(type) && taxNo == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开具增值税专用发票必须填写税号");
        }

        // 先校验订单归属与可开票状态：查不到行 = 订单不存在或不属于当前账户
        MyOrderStatePO state = invoiceMapper.selectOrderStateOfMyOrder(orderNo, customerId);
        if (state == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!STATUS_COMPLETED.equals(state.getStatus()) || !PAYABLE_STATUS.contains(state.getPayStatus())) {
            invoiceNotificationService.applicationRejected(customerId, orderNo, NOT_ELIGIBLE_REASON);
            throw new BizException(ErrorCode.STATE_CONFLICT, NOT_ELIGIBLE_REASON);
        }

        invoiceMapper.insertUserApplication(orderNo, customerId, title, taxNo, type, trim(cmd.email()));

        // 插入 0 行有两种情况：已有待开/已开发票（幂等回读），或并发下被别的事务抢先插入。
        // 两种都应该返回「已有的那张」，因此这里统一回读，不区分。
        InvoiceApplyRow row = invoiceMapper.selectMyLatestByOrderNo(orderNo, customerId);
        if (row == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "开票申请提交失败，请稍后重试");
        }
        return toVo(row);
    }

    @Override
    public UserInvoiceDtos.InvoiceVO latest(long customerId, String orderNo) {
        String no = trim(orderNo);
        if (no == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单号不能为空");
        }
        if (invoiceMapper.selectOrderStateOfMyOrder(no, customerId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        InvoiceApplyRow row = invoiceMapper.selectMyLatestByOrderNo(no, customerId);
        return row == null ? null : toVo(row);
    }

    @Override
    public PageResult<UserInvoiceDtos.InvoiceRowVO> mine(long customerId, String status, String keyword,
                                                         PageQuery page) {
        String tab = normalizeTab(status);
        String kw = trim(keyword);
        IPage<UserInvoiceRow> rows = invoiceMapper.selectMyInvoicePage(
                new Page<>(page.getPage(), page.getSize()), customerId, tab, kw);
        long total = invoiceMapper.countMyInvoices(customerId, tab, kw);
        List<UserInvoiceDtos.InvoiceRowVO> list = rows.getRecords().stream()
                .map(this::toRowVo)
                .toList();
        return PageResult.of(list, total, page.getPage(), page.getSize());
    }

    @Override
    public PageResult<UserInvoiceDtos.PendingInvoiceVO> pending(long customerId, PageQuery page) {
        IPage<PendingInvoiceRow> rows = invoiceMapper.selectPendingInvoicePage(
                new Page<>(page.getPage(), page.getSize()), customerId);
        long total = invoiceMapper.countPendingInvoices(customerId);
        List<UserInvoiceDtos.PendingInvoiceVO> list = rows.getRecords().stream()
                .map(row -> {
                    long amountCent = row.getAmountCent() == null ? 0L : row.getAmountCent();
                    return new UserInvoiceDtos.PendingInvoiceVO(
                            row.getOrderNo(),
                            amountCent,
                            MoneyUtil.toYuan(amountCent),
                            row.getFirstTitle(),
                            TimeUtil.toMillis(row.getCompletedAt()));
                })
                .toList();
        return PageResult.of(list, total, page.getPage(), page.getSize());
    }

    @Override
    public InvoicePdfFile pdf(long customerId, long invoiceId) {
        InvoiceNotifyRow row = invoiceMapper.selectNotifyRow(invoiceId);
        // 不区分「票不存在」与「不是你的票」：都回 404，避免用发票 ID 探测他人订单
        if (row == null || row.getCustomerId() == null || row.getCustomerId() != customerId) {
            throw new BizException(ErrorCode.NOT_FOUND, "发票不存在");
        }
        if (!INVOICE_ISSUED.equals(row.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "发票尚未开具，暂不能下载");
        }
        return invoicePdfService.render(invoiceId);
    }

    /**
     * 页签归一：空=全部；只认 ISSUED / OPEN（大小写不敏感）。
     * <p>不认识的值直接 40001，避免前端写错页签时静默返回全量、把「申请中」的票混进「已开具」。</p>
     */
    private String normalizeTab(String raw) {
        String tab = trim(raw);
        if (tab == null) {
            return null;
        }
        String upper = tab.toUpperCase();
        if (!TAB_ISSUED.equals(upper) && !TAB_OPEN.equals(upper)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票页签只能是 ISSUED 或 OPEN");
        }
        return upper;
    }

    /** 发票类型归一：缺省普通发票；只认 NORMAL / SPECIAL，其余按参数非法拒绝（防止脏值写库）。 */
    private String normalizeType(String raw) {
        String type = trim(raw);
        if (type == null) {
            return TYPE_NORMAL;
        }
        String upper = type.toUpperCase();
        if (!TYPE_NORMAL.equals(upper) && !TYPE_SPECIAL.equals(upper)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票类型只能是普通发票或专用发票");
        }
        return upper;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserInvoiceDtos.InvoiceRowVO toRowVo(UserInvoiceRow row) {
        long amountCent = row.getAmountCent() == null ? 0L : row.getAmountCent();
        return new UserInvoiceDtos.InvoiceRowVO(
                row.getInvoiceId() == null ? null : String.valueOf(row.getInvoiceId()),
                row.getOrderNo(),
                row.getInvoiceNo(),
                row.getStatus(),
                row.getTitle(),
                row.getType(),
                amountCent,
                MoneyUtil.toYuan(amountCent),
                TimeUtil.toMillis(row.getCreatedAt()),
                TimeUtil.toMillis(row.getIssuedAt()));
    }

    private UserInvoiceDtos.InvoiceVO toVo(InvoiceApplyRow row) {
        long amountCent = row.getAmountCent() == null ? 0L : row.getAmountCent();
        return new UserInvoiceDtos.InvoiceVO(
                row.getInvoiceId() == null ? null : String.valueOf(row.getInvoiceId()),
                row.getOrderNo(),
                row.getStatus(),
                row.getTitle(),
                row.getTaxNo(),
                row.getType(),
                row.getEmail(),
                amountCent,
                MoneyUtil.toYuan(amountCent),
                row.getInvoiceNo(),
                row.getPdfUrl(),
                TimeUtil.toMillis(row.getCreatedAt()));
    }
}
