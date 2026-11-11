package com.sent.sports.finance.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.po.InvoicePdfHeaderPO;
import com.sent.sports.finance.mapper.po.InvoicePdfItemPO;
import com.sent.sports.finance.pdf.InvoicePdfAssembler;
import com.sent.sports.finance.pdf.InvoicePdfModel;
import com.sent.sports.finance.pdf.InvoicePdfRenderer;
import com.sent.sports.finance.service.InvoicePdfService;
import com.sent.sports.finance.service.dto.InvoicePdfFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 发票 PDF 实现：取票头 → 取明细 → 组装票面模型 → 渲染。
 * <p>只读链路，不开事务；查无此票给 40401 而不是渲染一张空白票。</p>
 */
@Service
public class InvoicePdfServiceImpl implements InvoicePdfService {

    private final InvoiceMapper invoiceMapper;
    private final InvoicePdfRenderer renderer;
    private final InvoicePdfAssembler.Seller seller;

    public InvoicePdfServiceImpl(
            InvoiceMapper invoiceMapper,
            InvoicePdfRenderer renderer,
            // 默认值仅为虚构演示数据（与 application.yml 保持一致），绝不可用于真实开票。
            @Value("${app.invoice.seller-name:示例体育用品（演示）有限公司}") String sellerName,
            @Value("${app.invoice.seller-tax-no:DEMO-TAX-NO-0000000000000}") String sellerTaxNo,
            @Value("${app.invoice.seller-address-phone:示例市示例区演示路1号 000-00000000}")
            String sellerAddressPhone,
            @Value("${app.invoice.seller-bank-account:演示银行示例支行 0000 0000 0000 0000}")
            String sellerBankAccount,
            @Value("${app.invoice.code-prefix:0000000}") String codePrefix) {
        this.invoiceMapper = invoiceMapper;
        this.renderer = renderer;
        this.seller = new InvoicePdfAssembler.Seller(
                sellerName, sellerTaxNo, sellerAddressPhone, sellerBankAccount, codePrefix);
    }

    @Override
    public InvoicePdfFile render(long invoiceId) {
        if (invoiceId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票 ID 不合法，无法生成发票 PDF");
        }
        InvoicePdfHeaderPO header = invoiceMapper.selectPdfHeader(invoiceId);
        if (header == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "开票申请不存在，无法生成发票 PDF");
        }
        long orderId = header.getOrderId() == null ? 0L : header.getOrderId();
        List<InvoicePdfItemPO> items = orderId > 0L
                ? invoiceMapper.selectPdfItems(orderId)
                : List.of();
        InvoicePdfModel model = InvoicePdfAssembler.assemble(header, items, seller);
        return new InvoicePdfFile(renderer.render(model), fileName(header));
    }

    private static String fileName(InvoicePdfHeaderPO header) {
        String no = header.getInvoiceNo();
        if (no == null || no.isBlank()) {
            no = String.valueOf(header.getInvoiceId());
        }
        return "发票-" + no.trim() + ".pdf";
    }
}
