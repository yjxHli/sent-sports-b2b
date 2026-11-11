package com.sent.sports.finance.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发票 PDF 渲染（增值税电子普通发票版式）。
 * 断言方式：渲染成字节流后用 PDFBox 反解文字，验证票面关键栏位真的落在纸上，
 * 而不是「只要生成了 PDF 就算通过」。
 */
class InvoicePdfRendererTest {

    static InvoicePdfModel sampleModel() {
        return new InvoicePdfModel(
                "江苏增值税电子普通发票",
                new InvoicePdfModel.Meta("032001900111", "INV-4", "2026年11月10日",
                        "01234 56789 01234 56789"),
                new InvoicePdfModel.Party("森特体育用品有限公司", "91320000TEST001",
                        "南京市玄武区中山路1号 025-88888888", "中国银行南京分行 4567 8901 2345"),
                new InvoicePdfModel.Party("森特体育用品（江苏）有限公司", "91320000MA1ABCDE12",
                        "南京市建邺区江东中路8号 025-66666666", "工商银行南京分行 9876 5432 1011"),
                "订单号 SO20261108900649",
                List.of(new InvoicePdfLine("专业羽毛球拍 碳纤维", "4U/G5", "件", 1,
                        17_699L, 17_699L, "13%", 2_301L)),
                new InvoicePdfModel.Totals(17_699L, 2_301L, 20_000L),
                "张三", "李四", "王五");
    }

    @Test
    @DisplayName("渲染出单页 PDF，票面含发票代码/号码、购买方、销售方、明细、价税合计与大写金额")
    void rendersRealisticInvoice() throws Exception {
        File font = CjkFontProvider.resolve(null);
        Assumptions.assumeTrue(font != null, "本机没有可用的中文字体，跳过票面渲染校验");

        byte[] pdf = InvoicePdfRenderer.render(sampleModel(), font);

        assertTrue(pdf.length > 1_500, "PDF 体积异常：" + pdf.length);
        assertEquals('%', pdf[0]);
        assertEquals('P', pdf[1]);
        assertEquals('D', pdf[2]);
        assertEquals('F', pdf[3]);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            PDPage page = doc.getPage(0);
            // 票面比例接近真实发票（宽 > 高，横向开票）
            assertTrue(page.getMediaBox().getWidth() > page.getMediaBox().getHeight(),
                    "发票应为横向版式");

            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", "");
            assertTrue(text.contains("江苏增值税电子普通发票"), "缺少票头：" + text);
            assertTrue(text.contains("发票代码"), "缺少发票代码栏：" + text);
            assertTrue(text.contains("032001900111"), "缺少发票代码值：" + text);
            assertTrue(text.contains("发票号码"), "缺少发票号码栏：" + text);
            assertTrue(text.contains("INV-4"), "缺少发票号码值：" + text);
            assertTrue(text.contains("开票日期"), "缺少开票日期栏：" + text);
            assertTrue(text.contains("购买方"), "缺少购买方标识：" + text);
            assertTrue(text.contains("91320000TEST001"), "缺少购买方税号：" + text);
            assertTrue(text.contains("销售方"), "缺少销售方标识：" + text);
            assertTrue(text.contains("91320000MA1ABCDE12"), "缺少销售方税号：" + text);
            assertTrue(text.contains("专业羽毛球拍碳纤维"), "缺少商品名称：" + text);
            assertTrue(text.contains("4U/G5"), "缺少规格型号：" + text);
            assertTrue(text.contains("13%"), "缺少税率：" + text);
            assertTrue(text.contains("176.99"), "缺少不含税金额：" + text);
            assertTrue(text.contains("23.01"), "缺少税额：" + text);
            assertTrue(text.contains("价税合计"), "缺少价税合计栏：" + text);
            assertTrue(text.contains("贰佰元整"), "缺少大写金额：" + text);
            assertTrue(text.contains("200.00"), "缺少小写价税合计：" + text);
            assertTrue(text.contains("收款人"), "缺少收款人栏：" + text);
            assertTrue(text.contains("开票人"), "缺少开票人栏：" + text);
        }
    }

    @Test
    @DisplayName("明细超过票面可容纳行数时，多余行折叠成「其余 N 项」且金额仍计入合计")
    void foldsOverflowLines() throws Exception {
        File font = CjkFontProvider.resolve(null);
        Assumptions.assumeTrue(font != null, "本机没有可用的中文字体，跳过票面渲染校验");

        List<InvoicePdfLine> many = List.of(
                new InvoicePdfLine("羽毛球", "A", "件", 1, 1000L, 1000L, "13%", 130L),
                new InvoicePdfLine("球拍", "B", "件", 1, 1000L, 1000L, "13%", 130L),
                new InvoicePdfLine("球鞋", "C", "件", 1, 1000L, 1000L, "13%", 130L),
                new InvoicePdfLine("球袜", "D", "件", 1, 1000L, 1000L, "13%", 130L),
                new InvoicePdfLine("护腕", "E", "件", 1, 1000L, 1000L, "13%", 130L),
                new InvoicePdfLine("护膝", "F", "件", 1, 1000L, 1000L, "13%", 130L),
                new InvoicePdfLine("球包", "G", "件", 1, 1000L, 1000L, "13%", 130L),
                new InvoicePdfLine("毛巾", "H", "件", 1, 1000L, 1000L, "13%", 130L));
        InvoicePdfModel model = new InvoicePdfModel(
                "江苏增值税电子普通发票",
                new InvoicePdfModel.Meta("032001900111", "INV-9", "2026年11月10日", "01234"),
                new InvoicePdfModel.Party("森特体育用品有限公司", "91320000TEST001", "", ""),
                new InvoicePdfModel.Party("森特体育用品（江苏）有限公司", "91320000MA1ABCDE12", "", ""),
                null, many,
                new InvoicePdfModel.Totals(8_000L, 1_040L, 9_040L),
                "张三", "李四", "王五");

        byte[] pdf = InvoicePdfRenderer.render(model, font);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", "");
            assertEquals(1, doc.getNumberOfPages(), "多余明细折叠，不额外开页");
            assertTrue(text.contains("其余"), "超出行数应折叠成「其余 N 项」：" + text);
            assertTrue(text.contains("羽毛球"), text);
        }
    }

    @Test
    @DisplayName("未提供字体文件时快速失败，不生成半张乱码发票")
    void missingFontFailsFast() {
        boolean failed = false;
        try {
            InvoicePdfRenderer.render(sampleModel(), null);
        } catch (RuntimeException e) {
            failed = true;
        }
        assertTrue(failed, "字体缺失必须抛异常");
    }
}
