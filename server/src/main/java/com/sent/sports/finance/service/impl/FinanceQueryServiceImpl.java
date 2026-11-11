package com.sent.sports.finance.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.finance.dto.admin.AdminFinanceDtos;
import com.sent.sports.finance.mapper.ContractMapper;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.PaymentRecordMapper;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.ContractListItemPO;
import com.sent.sports.finance.mapper.po.InvoiceListItemPO;
import com.sent.sports.finance.mapper.po.PaymentRecordPO;
import com.sent.sports.finance.mapper.po.ReceivableListRow;
import com.sent.sports.finance.service.FinanceQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务查询实现（收口阶段）：发票 / 合同 / 开票动作 / 应收台账均接真实 MyBatis-Plus 分页。
 * <p>时间统一按系统时区换算成毫秒；金额一律「分」，不做二次换算。</p>
 */
@Service
public class FinanceQueryServiceImpl implements FinanceQueryService {

    private final InvoiceMapper invoiceMapper;
    private final ContractMapper contractMapper;
    private final ReceivableMapper receivableMapper;
    private final PaymentRecordMapper paymentRecordMapper;

    public FinanceQueryServiceImpl(InvoiceMapper invoiceMapper, ContractMapper contractMapper,
                                   ReceivableMapper receivableMapper,
                                   PaymentRecordMapper paymentRecordMapper) {
        this.invoiceMapper = invoiceMapper;
        this.contractMapper = contractMapper;
        this.receivableMapper = receivableMapper;
        this.paymentRecordMapper = paymentRecordMapper;
    }

    @Override
    public PageResult<AdminFinanceDtos.ReceivableVO> receivables(PageQuery pageQuery, Long customerId,
                                                                 String status, boolean openOnly) {
        String normalizedStatus = normalize(status);
        Page<ReceivableListRow> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        receivableMapper.selectReceivablePage(page, customerId, normalizedStatus, openOnly);
        long total = receivableMapper.countReceivable(customerId, normalizedStatus, openOnly);
        page.setTotal(total);
        LocalDate today = LocalDate.now();
        List<AdminFinanceDtos.ReceivableVO> list = page.getRecords().stream()
                .map(row -> toReceivableVo(row, today)).toList();
        return PageResult.of(list, total, pageQuery.getPage(), pageQuery.getSize());
    }

    private AdminFinanceDtos.ReceivableVO toReceivableVo(ReceivableListRow row, LocalDate today) {
        LocalDate dueAt = row.getDueAt();
        long dueAtMillis = dueAt == null ? 0L
                : dueAt.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String status = row.getStatus() == null ? "" : row.getStatus();
        int overdueDays = 0;
        if (dueAt != null && !"CLOSED".equals(status) && dueAt.isBefore(today)) {
            overdueDays = (int) ChronoUnit.DAYS.between(dueAt, today);
        }
        return new AdminFinanceDtos.ReceivableVO(
                row.getReceivableId() == null ? 0L : row.getReceivableId(),
                row.getOrderNo(),
                row.getCustomerId() == null ? 0L : row.getCustomerId(),
                row.getAmountCent() == null ? 0L : row.getAmountCent(),
                row.getWrittenOffCent() == null ? 0L : row.getWrittenOffCent(),
                dueAtMillis,
                status,
                overdueDays);
    }

    @Override
    public PageResult<AdminFinanceDtos.InvoiceVO> invoices(PageQuery pageQuery, String status) {
        Page<InvoiceListItemPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        invoiceMapper.selectInvoicePage(page, status);
        long total = invoiceMapper.countInvoice(status);
        page.setTotal(total);
        List<AdminFinanceDtos.InvoiceVO> list = page.getRecords().stream().map(this::toInvoiceVo).toList();
        return PageResult.of(list, total, pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public PageResult<AdminFinanceDtos.ContractVO> contracts(PageQuery pageQuery, String status,
                                                            String dueState) {
        String normalizedStatus = normalize(status);
        String normalizedDueState = normalizeDueState(dueState);
        Page<ContractListItemPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        contractMapper.selectContractPage(page, normalizedStatus, normalizedDueState);
        long total = contractMapper.countContract(normalizedStatus, normalizedDueState);
        page.setTotal(total);
        List<AdminFinanceDtos.ContractVO> list = page.getRecords().stream().map(this::toContractVo).toList();
        return PageResult.of(list, total, pageQuery.getPage(), pageQuery.getSize());
    }

    /** 空白归一 null；只认 EXPIRED / SOON / NORMAL，其余一律当「不限」，避免手拼参数把列表筛空。 */
    private static String normalizeDueState(String dueState) {
        if (dueState == null || dueState.isBlank()) {
            return null;
        }
        String upper = dueState.trim().toUpperCase();
        return switch (upper) {
            case "EXPIRED", "SOON", "NORMAL" -> upper;
            default -> null;
        };
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @Override
    public PageResult<AdminFinanceDtos.PaymentRecordVO> paymentRecords(PageQuery pageQuery,
                                                                       Long customerId, String status) {
        String normalizedStatus = (status == null || status.isBlank()) ? null : status.trim();
        Page<PaymentRecordPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        paymentRecordMapper.selectPaymentRecordPage(page, customerId, normalizedStatus);
        long total = paymentRecordMapper.countPaymentRecord(customerId, normalizedStatus);
        page.setTotal(total);
        List<AdminFinanceDtos.PaymentRecordVO> list =
                page.getRecords().stream().map(this::toPaymentRecordVo).toList();
        return PageResult.of(list, total, pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    @UseCase
    public AdminFinanceDtos.InvoiceIssueVO applyInvoice(AdminFinanceDtos.InvoiceApplyCmd cmd) {
        int rows = invoiceMapper.insertFromOrder(cmd.orderNo(), cmd.title(), cmd.taxNo(), cmd.email());
        if (rows == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在，无法开具发票：" + cmd.orderNo());
        }
        Long invoiceId = invoiceMapper.selectLatestIdByOrderNo(cmd.orderNo());
        if (invoiceId == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        String invoiceNo = "INV-" + invoiceId;
        String pdfUrl = resolvePdfUrl(invoiceId, cmd.pdfUrl());
        invoiceMapper.markIssued(invoiceId, invoiceNo, pdfUrl);
        return new AdminFinanceDtos.InvoiceIssueVO(invoiceId, invoiceNo, cmd.orderNo(),
                "ISSUED", pdfUrl);
    }

    @Override
    public Object issueInvoice(long invoiceId, String pdfUrl, String remark) {
        String invoiceNo = "INV-" + invoiceId;
        String resolvedUrl = resolvePdfUrl(invoiceId, pdfUrl);
        int rows = invoiceMapper.markIssued(invoiceId, invoiceNo, resolvedUrl);
        if (rows == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "开票申请不存在，请先按订单号新建开票申请");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("invoiceId", invoiceId);
        snapshot.put("invoiceNo", invoiceNo);
        snapshot.put("status", "ISSUED");
        snapshot.put("pdfUrl", resolvedUrl);
        if (remark != null && !remark.isBlank()) {
            snapshot.put("remark", remark);
        }
        return snapshot;
    }

    /**
     * PDF 地址：票面由后端现场渲染，未指定时按发票主键给出下载端点，
     * 不再要求财务手填一个「其实并不存在的 /files/*.pdf」。
     */
    private static String resolvePdfUrl(long invoiceId, String pdfUrl) {
        if (pdfUrl != null && !pdfUrl.isBlank()) {
            return pdfUrl.trim();
        }
        return "/api/admin/invoices/" + invoiceId + "/pdf";
    }

    private AdminFinanceDtos.InvoiceVO toInvoiceVo(InvoiceListItemPO po) {
        return new AdminFinanceDtos.InvoiceVO(
                po.getInvoiceId() == null ? 0L : po.getInvoiceId(),
                po.getInvoiceNo(),
                po.getCustomerId() == null ? 0L : po.getCustomerId(),
                po.getAmountCent() == null ? 0L : po.getAmountCent(),
                po.getStatus(),
                po.getPdfUrl(),
                toMillis(po.getCreatedAt()));
    }

    private AdminFinanceDtos.ContractVO toContractVo(ContractListItemPO po) {
        LocalDate validTo = po.getValidTo();
        long validToMillis = validTo == null ? 0L
                : validTo.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new AdminFinanceDtos.ContractVO(
                po.getContractId() == null ? 0L : po.getContractId(),
                po.getContractNo(),
                po.getCustomerId() == null ? 0L : po.getCustomerId(),
                po.getAmountCent() == null ? 0L : po.getAmountCent(),
                po.getStatus(),
                validToMillis);
    }

    private AdminFinanceDtos.PaymentRecordVO toPaymentRecordVo(PaymentRecordPO po) {
        LocalDate receivedDate = po.getReceivedDate();
        long receivedDateMillis = receivedDate == null ? 0L
                : receivedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new AdminFinanceDtos.PaymentRecordVO(
                po.getRecordNo(),
                zeroIfNull(po.getCustomerId()),
                zeroIfNull(po.getAmountCent()),
                zeroIfNull(po.getWrittenOffCent()),
                po.getBankSerial(),
                po.getStatus(),
                receivedDateMillis,
                toMillis(po.getCreatedAt()),
                po.getRemark(),
                po.getReversedReason());
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
