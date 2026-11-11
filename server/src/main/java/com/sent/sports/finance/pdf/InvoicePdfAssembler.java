package com.sent.sports.finance.pdf;

import com.sent.sports.finance.mapper.po.InvoicePdfHeaderPO;
import com.sent.sports.finance.mapper.po.InvoicePdfItemPO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 把 {@code invoice_request + orders + order_item} 组装成票面模型。
 * <p>三条硬口径：</p>
 * <ol>
 *   <li>订单金额是含税价，票面必须拆成「不含税金额 + 税额」，且合计与价税合计分毫不差；</li>
 *   <li>历史订单行的 {@code spu_title_snap / spec_snap} 可能为 NULL，必须回退商品表，
 *       不能把 {@code null} 印到发票上；</li>
 *   <li>发票代码 / 校验码由发票主键确定性推导 —— 同一张发票重复下载必须是同一份票面，
 *       否则财务对不上账。</li>
 * </ol>
 */
public final class InvoicePdfAssembler {

    /** 票面明细最多行数（真正的容量判断在渲染器里，这里只提供通用折叠工具）。 */
    public static final int MAX_LINES = 6;

    /** 税率文本（体育用品 13%）。 */
    static final String TAX_RATE = "13%";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");

    private InvoicePdfAssembler() {
    }

    /**
     * 销售方开票信息（本系统只有一个开票主体，来自配置）。
     *
     * @param codePrefix 发票代码前 7 位，与发票主键拼成 12 位发票代码
     */
    public record Seller(String name, String taxNo, String addressPhone, String bankAccount,
                         String codePrefix) {
    }

    public static InvoicePdfModel assemble(InvoicePdfHeaderPO header, List<InvoicePdfItemPO> items,
                                           Seller seller) {
        List<InvoicePdfLine> lines = new ArrayList<>();
        if (items != null) {
            for (InvoicePdfItemPO item : items) {
                lines.add(toLine(item));
            }
        }
        if (lines.isEmpty()) {
            // 订单没有明细（历史数据/异常单）：用订单应付金额兜底一行，票不能是空表
            lines.add(toLine(fallbackItem(header)));
        }
        long amountCent = lines.stream().mapToLong(InvoicePdfLine::amountCent).sum();
        long taxCent = lines.stream().mapToLong(InvoicePdfLine::taxCent).sum();
        long grossCent = lines.stream().mapToLong(line -> line.amountCent() + line.taxCent()).sum();

        return new InvoicePdfModel(
                "江苏增值税电子普通发票",
                new InvoicePdfModel.Meta(
                        invoiceCode(seller.codePrefix(), header.getInvoiceId()),
                        invoiceNo(header),
                        issuedDate(header),
                        checkCode(header)),
                new InvoicePdfModel.Party(
                        firstNonBlank(header.getTitle(), header.getCustomerNickname(), "个人"),
                        firstNonBlank(header.getTaxNo(), "—"),
                        "",
                        ""),
                new InvoicePdfModel.Party(
                        seller.name(), seller.taxNo(), seller.addressPhone(), seller.bankAccount()),
                remark(header),
                lines,
                new InvoicePdfModel.Totals(amountCent, taxCent, grossCent),
                "财务部", "财务主管", "开票员");
    }

    private static InvoicePdfLine toLine(InvoicePdfItemPO item) {
        int qty = item.getQty() == null || item.getQty() <= 0 ? 1 : item.getQty();
        long gross = item.getAmountCent() == null ? 0L : Math.max(item.getAmountCent(), 0L);
        InvoiceTaxCalculator.TaxSplit split = InvoiceTaxCalculator.split13(gross, qty);
        return new InvoicePdfLine(
                firstNonBlank(item.getSpuTitleSnap(), item.getProductTitle(),
                        "商品#" + (item.getSkuId() == null ? "-" : item.getSkuId())),
                firstNonBlank(item.getSpecSnap(), "无"),
                "件",
                qty,
                split.unitNetCent(),
                split.netCent(),
                TAX_RATE,
                split.taxCent());
    }

    private static InvoicePdfItemPO fallbackItem(InvoicePdfHeaderPO header) {
        InvoicePdfItemPO item = new InvoicePdfItemPO();
        item.setSpuTitleSnap(firstNonBlank(header.getRemark(), "商品销售",
                "订单 " + firstNonBlank(header.getOrderNo(), "-")));
        item.setSpecSnap(null);
        item.setQty(1);
        item.setAmountCent(header.getPayableCent() == null
                ? 0L : Math.max(header.getPayableCent(), 0L));
        item.setSkuId(null);
        return item;
    }

    /** 超出票面行数时保留前 N-1 行，末行折叠成「其余 N 项商品」并把数量/金额/税额累加进去。 */
    public static List<InvoicePdfLine> foldToFit(List<InvoicePdfLine> lines, int maxLines) {
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<InvoicePdfLine> visible = new ArrayList<>(lines.subList(0, maxLines - 1));
        List<InvoicePdfLine> rest = lines.subList(maxLines - 1, lines.size());
        int qty = rest.stream().mapToInt(InvoicePdfLine::qty).sum();
        long amount = rest.stream().mapToLong(InvoicePdfLine::amountCent).sum();
        long tax = rest.stream().mapToLong(InvoicePdfLine::taxCent).sum();
        visible.add(new InvoicePdfLine("其余 " + rest.size() + " 项商品", "无", "件", qty,
                qty > 0 ? amount / qty : 0L, amount, TAX_RATE, tax));
        return visible;
    }

    private static String invoiceCode(String prefix, Long invoiceId) {
        String head = prefix == null || prefix.isBlank() ? "0320019" : prefix.trim();
        if (head.length() > 7) {
            head = head.substring(0, 7);
        }
        long id = invoiceId == null ? 0L : Math.abs(invoiceId);
        return head + String.format("%05d", id % 100000L);
    }

    private static String invoiceNo(InvoicePdfHeaderPO header) {
        return firstNonBlank(header.getInvoiceNo(), "NO-" + header.getInvoiceId());
    }

    private static String issuedDate(InvoicePdfHeaderPO header) {
        LocalDateTime time = header.getIssuedAt() != null ? header.getIssuedAt() : header.getCreatedAt();
        return time == null ? "-" : time.format(DATE);
    }

    /** 20 位校验码，按发票号+主键确定性生成（同票永远同码）。 */
    private static String checkCode(InvoicePdfHeaderPO header) {
        String seedKey = firstNonBlank(header.getInvoiceNo(), "") + "#" + header.getInvoiceId();
        Random random = new Random(seedKey.hashCode());
        StringBuilder sb = new StringBuilder(23);
        for (int i = 0; i < 20; i++) {
            sb.append(random.nextInt(10));
            if (i % 5 == 4 && i != 19) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static String remark(InvoicePdfHeaderPO header) {
        String orderNo = header.getOrderNo();
        String base = orderNo == null || orderNo.isBlank() ? "电子发票" : "订单号 " + orderNo;
        String extra = header.getRemark();
        return extra == null || extra.isBlank() ? base : base + "；" + extra.trim();
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
