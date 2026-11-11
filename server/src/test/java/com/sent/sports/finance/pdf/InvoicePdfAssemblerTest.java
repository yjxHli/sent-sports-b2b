package com.sent.sports.finance.pdf;

import com.sent.sports.finance.mapper.po.InvoicePdfHeaderPO;
import com.sent.sports.finance.mapper.po.InvoicePdfItemPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发票票面数据组装：把 invoice_request + orders + order_item 拼成票面模型。
 * 关键口径：① 订单金额是含税价，票面要拆成不含税金额 + 税额且合计分毫不差；
 * ② 历史订单行的 spu_title_snap / spec_snap 可能为 NULL，必须回退到商品表，不能印出空白或 null。
 */
class InvoicePdfAssemblerTest {

    private static final InvoicePdfAssembler.Seller SELLER = new InvoicePdfAssembler.Seller(
            "森特体育用品（江苏）有限公司", "91320000MA1ABCDE12",
            "南京市建邺区江东中路8号 025-66666666", "工商银行南京分行 9876 5432 1011",
            "0320019");

    private static InvoicePdfHeaderPO header() {
        InvoicePdfHeaderPO header = new InvoicePdfHeaderPO();
        header.setInvoiceId(4L);
        header.setInvoiceNo("INV-4");
        header.setStatus("ISSUED");
        header.setIssuedAt(LocalDateTime.of(2026, 11, 10, 19, 17, 39));
        header.setTitle("森特体育用品有限公司");
        header.setTaxNo("91320000TEST001");
        header.setCustomerId(9002L);
        header.setOrderId(2078L);
        header.setOrderNo("SO20261108900649");
        header.setPayableCent(20_000L);
        header.setCustomerNickname("林小满");
        return header;
    }

    private static InvoicePdfItemPO item(String title, String spec, int qty, long amount) {
        InvoicePdfItemPO po = new InvoicePdfItemPO();
        po.setSpuTitleSnap(title);
        po.setSpecSnap(spec);
        po.setQty(qty);
        po.setUnitPriceCent(amount / Math.max(qty, 1));
        po.setAmountCent(amount);
        po.setSkuId(9102L);
        return po;
    }

    @Test
    @DisplayName("正常组装：抬头/税号/订单号/开票日期/明细/合计/大写全部就位")
    void assemblesFully() {
        InvoicePdfModel model = InvoicePdfAssembler.assemble(
                header(), List.of(item("专业羽毛球拍 碳纤维", "4U/G5", 1, 20_000L)), SELLER);

        assertEquals("江苏增值税电子普通发票", model.title());
        assertEquals("INV-4", model.meta().invoiceNo());
        assertEquals("2026年11月10日", model.meta().issuedDate());
        assertEquals("森特体育用品有限公司", model.buyer().name());
        assertEquals("91320000TEST001", model.buyer().taxNo());
        assertEquals("森特体育用品（江苏）有限公司", model.seller().name());
        assertEquals(1, model.lines().size());
        assertEquals("专业羽毛球拍 碳纤维", model.lines().get(0).name());
        assertEquals("4U/G5", model.lines().get(0).spec());
        assertEquals("件", model.lines().get(0).unit());
        assertEquals("13%", model.lines().get(0).taxRate());
        assertEquals(20_000L, model.totals().grossCent());
        assertEquals(20_000L, model.totals().amountCent() + model.totals().taxCent(),
                "金额合计 + 税额合计 必须等于价税合计");
        assertTrue(model.remark().contains("SO20261108900649"), "备注应带订单号：" + model.remark());
    }

    @Test
    @DisplayName("发票代码 12 位、校验码 20 位，且同一张发票重复生成完全一致（不能每次下载都不一样）")
    void serialNumbersAreDeterministic() {
        InvoicePdfModel first = InvoicePdfAssembler.assemble(
                header(), List.of(item("球拍", "A", 1, 1_000L)), SELLER);
        InvoicePdfModel again = InvoicePdfAssembler.assemble(
                header(), List.of(item("球拍", "A", 1, 1_000L)), SELLER);

        assertEquals(12, first.meta().invoiceCode().length(), first.meta().invoiceCode());
        assertEquals(20, first.meta().checkCode().replaceAll(" ", "").length(),
                first.meta().checkCode());
        assertEquals(first.meta().invoiceCode(), again.meta().invoiceCode());
        assertEquals(first.meta().checkCode(), again.meta().checkCode());
    }

    @Test
    @DisplayName("历史订单行快照为 NULL：回退商品表标题，规格缺失印「无」，不出现 null 字样")
    void fallsBackForLegacyItems() {
        InvoicePdfItemPO legacy = item(null, null, 1, 20_000L);
        legacy.setProductTitle("篮球 7 号");

        InvoicePdfModel model = InvoicePdfAssembler.assemble(header(), List.of(legacy), SELLER);

        assertEquals("篮球 7 号", model.lines().get(0).name());
        assertEquals("无", model.lines().get(0).spec());
        assertFalse(model.lines().get(0).name().contains("null"));
    }

    @Test
    @DisplayName("商品标题也没有回退源时用 SKU 编号占位，不能印空")
    void usesSkuPlaceholder() {
        InvoicePdfModel model = InvoicePdfAssembler.assemble(
                header(), List.of(item(null, null, 1, 20_000L)), SELLER);

        assertEquals("商品#9102", model.lines().get(0).name());
    }

    @Test
    @DisplayName("抬头为空时回退客户昵称；订单无明细时用订单应付金额兜底一行")
    void fallsBackWhenTitleAndItemsMissing() {
        InvoicePdfHeaderPO header = header();
        header.setTitle("   ");

        InvoicePdfModel model = InvoicePdfAssembler.assemble(header, List.of(), SELLER);

        assertEquals("林小满", model.buyer().name());
        assertEquals(1, model.lines().size(), "无明细也要有一行体现价税合计");
        assertEquals(20_000L, model.totals().grossCent());
        assertEquals(20_000L, model.totals().amountCent() + model.totals().taxCent());
    }

    @Test
    @DisplayName("负数/空金额不出负数税票：一律夹到 0")
    void clampsNegativeAmounts() {
        InvoicePdfModel model = InvoicePdfAssembler.assemble(
                header(), List.of(item("退货冲减", "A", 1, -5_000L)), SELLER);

        assertEquals(0L, model.lines().get(0).amountCent());
        assertEquals(0L, model.lines().get(0).taxCent());
        assertEquals(0L, model.totals().grossCent());
    }
}
