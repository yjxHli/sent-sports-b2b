package com.sent.sports.finance.pdf;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 增值税电子普通发票渲染器：按真实票面栅格绝对定位绘制，一页出票。
 * <p>版式顺序与真实发票一致：票头 → 发票代码/号码/开票日期/校验码 → 购买方 →
 * 明细表（名称/规格/单位/数量/单价/金额/税率/税额）→ 合计 → 价税合计（大写+小写）→
 * 备注 → 销售方 → 收款人/复核/开票人。</p>
 * <p>明细行数按实际内容自适应（最少 3 行、最多 6 行），页高随之收缩 —— 真实电子发票
 * 是连续纸，不会为了对齐表单留一整片空白框；超出上限的明细折叠成「其余 N 项商品」。</p>
 * <p>渲染只吃 {@link InvoicePdfModel}，不碰数据库、不依赖 Spring 上下文，可脱离 Web 层单测。</p>
 */
@Component
public class InvoicePdfRenderer {

    // ---- 票面尺寸（1pt = 1/72 inch，720 宽接近真实发票 240×140mm 的横向比例） ----
    private static final float W = 720f;
    private static final float BASE_H = 524f;
    private static final float M = 24f;
    private static final float INNER = W - M * 2;
    private static final float LABEL_W = 42f;

    // ---- 明细表列宽（合计 = INNER） ----
    private static final float[] COL = {200f, 85f, 45f, 50f, 85f, 105f, 50f, 52f};
    private static final String[] HEAD = {
            "货物或应税劳务、服务名称", "规格型号", "单位", "数量", "单价", "金额", "税率", "税额"};
    /** 0 左对齐 / 1 居中 / 2 右对齐。 */
    private static final int[] ALIGN = {0, 1, 1, 1, 2, 2, 1, 2};

    private static final float TBL_TOP = 192f;
    private static final float TBL_HEAD_H = 22f;
    private static final float TBL_ROW_H = 18f;
    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 6;
    private static final float TOTAL_ROW_H = 18f;
    private static final float GROSS_ROW_H = 24f;
    private static final float SELLER_H = 92f;
    /** 满行（6 行）时销售方区块的顶部位置；行数变少时整体上移。 */
    private static final float SELLER_TOP_BASE = 404f;

    private final CjkFontProvider fontProvider;

    public InvoicePdfRenderer(CjkFontProvider fontProvider) {
        this.fontProvider = fontProvider;
    }

    /** Spring 侧入口：字体自动探测。 */
    public byte[] render(InvoicePdfModel model) {
        return render(model, fontProvider.resolve());
    }

    /**
     * 纯函数入口：直接给字体文件。
     *
     * @throws BizException 字体缺失（快速失败，不生成一张全是空框的「发票」）
     */
    public static byte[] render(InvoicePdfModel model, File fontFile) {
        if (fontFile == null || !fontFile.isFile()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "未找到可用的中文字体，无法生成发票 PDF；"
                            + "请配置 app.invoice.font-file 指向 simhei.ttf 等单体 TTF 字体");
        }
        try {
            return build(model, fontFile);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, (Throwable) e,
                    "发票 PDF 生成失败：" + e.getMessage());
        }
    }

    private static byte[] build(InvoicePdfModel model, File fontFile) throws IOException {
        List<InvoicePdfLine> lines = InvoicePdfAssembler.foldToFit(model.lines(), MAX_ROWS);
        int rows = Math.max(MIN_ROWS, Math.min(lines.size(), MAX_ROWS));
        float height = BASE_H - (MAX_ROWS - rows) * TBL_ROW_H;

        try (PDDocument doc = new PDDocument();
             // PDFBox 3 不再支持 load(doc, File)：字体流必须活到 save()，因为子集化在保存时才发生
             InputStream fontStream = new FileInputStream(fontFile)) {
            PDType0Font font = PDType0Font.load(doc, fontStream, true);
            PDPage page = new PDPage(new PDRectangle(W, height));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                new Painter(cs, font, height, rows, lines).paint(model);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 绝对定位绘图工具：所有坐标都用「距页顶多少 pt」表达，可读性接近设计稿。 */
    private static final class Painter {

        private final PDPageContentStream cs;
        private final PDFont font;
        private final float pageHeight;
        private final int rows;
        private final List<InvoicePdfLine> lines;
        /** 少用的行数需要把下方区块整体上移的量。 */
        private final float shift;

        Painter(PDPageContentStream cs, PDFont font, float pageHeight, int rows,
                List<InvoicePdfLine> lines) {
            this.cs = cs;
            this.font = font;
            this.pageHeight = pageHeight;
            this.rows = rows;
            this.lines = lines;
            this.shift = (MAX_ROWS - rows) * TBL_ROW_H;
        }

        private float y(float top) {
            return pageHeight - top;
        }

        private float tableBottom() {
            return TBL_TOP + TBL_HEAD_H + rows * TBL_ROW_H;
        }

        private float totalTop() {
            return tableBottom();
        }

        private float grossTop() {
            return totalTop() + TOTAL_ROW_H;
        }

        private float grossBottom() {
            return grossTop() + GROSS_ROW_H;
        }

        private float sellerTop() {
            return SELLER_TOP_BASE - shift;
        }

        private float sellerBottom() {
            return sellerTop() + SELLER_H;
        }

        void paint(InvoicePdfModel model) throws IOException {
            head(model);
            metaRow(model);
            partyBox(100f, "购买方", model.buyer());
            table(model);
            grossRow(model);
            remark(model);
            partyBox(sellerTop(), "销售方", model.seller());
            footer(model);
        }

        // ---------------- 各区块 ----------------

        private void head(InvoicePdfModel model) throws IOException {
            String title = model.title() == null ? "增值税电子普通发票" : model.title();
            textAligned(title, M, INNER, 46f, 17f, 1, true);
            hLine(M, M + INNER, 52f, 1.4f);
            hLine(M, M + INNER, 55f, 0.6f);
        }

        private void metaRow(InvoicePdfModel model) throws IOException {
            InvoicePdfModel.Meta meta = model.meta();
            text("发票代码：" + nz(meta.invoiceCode()), M + 4f, 72f, 9f, false);
            right("发票号码：" + nz(meta.invoiceNo()), M + INNER - 4f, 72f, 9f);
            text("开票日期：" + nz(meta.issuedDate()), M + 4f, 90f, 9f, false);
            right("校验码：" + nz(meta.checkCode()), M + INNER - 4f, 90f, 9f);
            hLine(M, M + INNER, 100f, 0.8f);
        }

        /** 购买方 / 销售方：左侧竖排名称 + 右侧四行开票信息。 */
        private void partyBox(float top, String label, InvoicePdfModel.Party party) throws IOException {
            float bottom = top + SELLER_H;
            rect(M, top, LABEL_W, SELLER_H, 0.8f);
            verticalText(label, M, LABEL_W, top, bottom, 9f);
            vLine(M + LABEL_W, top, bottom, 0.8f);
            // 右侧内容区的顶边框（购买方这条线已由开票日期下的分隔线提供，重复描一遍无副作用）
            hLine(M + LABEL_W, M + INNER, top, 0.8f);

            float lx = M + LABEL_W + 8f;
            float vx = M + LABEL_W + 78f;
            kv("名称：", nz(party.name()), lx, vx, top + 18f);
            kv("纳税人识别号：", nz(party.taxNo()), lx, vx, top + 38f);
            kv("地址、电话：", nz(party.addressPhone()), lx, vx, top + 58f);
            kv("开户行及账号：", nz(party.bankAccount()), lx, vx, top + 78f);
            hLine(M, M + INNER, bottom, 0.8f);
        }

        private void table(InvoicePdfModel model) throws IOException {
            rect(M, TBL_TOP, INNER, TBL_HEAD_H + rows * TBL_ROW_H, 0.8f);
            hLine(M, M + INNER, TBL_TOP + TBL_HEAD_H, 0.8f);
            for (int i = 1; i < rows; i++) {
                hLine(M, M + INNER, TBL_TOP + TBL_HEAD_H + i * TBL_ROW_H, 0.4f);
            }
            float x = M;
            for (int c = 0; c < COL.length - 1; c++) {
                x += COL[c];
                vLine(x, TBL_TOP, tableBottom(), 0.4f);
            }

            float cx = M;
            for (int c = 0; c < COL.length; c++) {
                textAligned(HEAD[c], cx, COL[c], TBL_TOP + 15f, 8.5f, 1, false);
                cx += COL[c];
            }

            for (int i = 0; i < rows && i < lines.size(); i++) {
                lineRow(lines.get(i), TBL_TOP + TBL_HEAD_H + i * TBL_ROW_H + 13f);
            }

            // 合计行：金额合计 / 税额合计
            float top = totalTop();
            rect(M, top, INNER, TOTAL_ROW_H, 0.8f);
            vLine(M + 465f, top, top + TOTAL_ROW_H, 0.4f);
            vLine(M + 570f, top, top + TOTAL_ROW_H, 0.4f);
            vLine(M + 620f, top, top + TOTAL_ROW_H, 0.4f);
            textAligned("合计", M, COL[0], top + 13f, 8.5f, 0, false);
            textAligned("￥" + MoneyUtil.toYuan(model.totals().amountCent()),
                    M + 465f, 105f, top + 13f, 8.5f, 2, false);
            textAligned("￥" + MoneyUtil.toYuan(model.totals().taxCent()),
                    M + 620f, 52f, top + 13f, 8.5f, 2, false);
        }

        private void lineRow(InvoicePdfLine line, float baseline) throws IOException {
            String[] cells = {
                    ellipsis(nz(line.name()), COL[0] - 8f, 8.5f),
                    nz(line.spec()),
                    nz(line.unit()),
                    String.valueOf(line.qty()),
                    MoneyUtil.toYuan(line.unitNetCent()),
                    MoneyUtil.toYuan(line.amountCent()),
                    nz(line.taxRate()),
                    MoneyUtil.toYuan(line.taxCent())};
            float x = M;
            for (int c = 0; c < cells.length; c++) {
                textAligned(cells[c], x, COL[c], baseline, 8.5f, ALIGN[c], false);
                x += COL[c];
            }
        }

        private void grossRow(InvoicePdfModel model) throws IOException {
            float top = grossTop();
            rect(M, top, INNER, GROSS_ROW_H, 0.8f);
            vLine(M + 430f, top, top + GROSS_ROW_H, 0.4f);
            textAligned("价税合计（大写）  " + RmbUpper.upper(model.totals().grossCent()),
                    M, 430f, top + 16f, 9f, 0, false);
            textAligned("（小写）  ￥" + MoneyUtil.toYuan(model.totals().grossCent()),
                    M + 430f, INNER - 430f, top + 16f, 9f, 0, false);
            hLine(M, M + INNER, grossBottom(), 0.8f);
        }

        private void remark(InvoicePdfModel model) throws IOException {
            List<String> wrapped = wrap("备注：" + nz(model.remark()), INNER - 10f, 8.5f, 2);
            float top = grossBottom() + 16f;
            for (String line : wrapped) {
                text(line, M + 4f, top, 8.5f, false);
                top += 14f;
            }
        }

        private void footer(InvoicePdfModel model) throws IOException {
            float baseline = sellerBottom() + 16f;
            text("收款人：" + nz(model.payee()), M + 4f, baseline, 9f, false);
            text("复核：" + nz(model.reviewer()), M + 180f, baseline, 9f, false);
            text("开票人：" + nz(model.drawer()), M + 320f, baseline, 9f, false);
            right("销售方：（章）", M + INNER - 4f, baseline, 9f);
            hLine(M, M + INNER, pageHeight - 6f, 0.8f);
        }

        // ---------------- 绘图原语 ----------------

        private void kv(String label, String value, float labelX, float valueX, float top)
                throws IOException {
            text(label, labelX, top, 9f, false);
            text(value, valueX, top, 9f, false);
            vLine(valueX - 4f, top - 9f, top + 5f, 0.3f);
        }

        private void text(String value, float x, float top, float fontSize, boolean bold)
                throws IOException {
            if (value == null || value.isEmpty()) {
                return;
            }
            cs.saveGraphicsState();
            if (bold) {
                cs.setRenderingMode(RenderingMode.FILL_STROKE);
                cs.setLineWidth(0.35f);
            }
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.newLineAtOffset(x, y(top));
            cs.showText(value);
            cs.endText();
            cs.restoreGraphicsState();
        }

        private void right(String value, float rightEdge, float top, float fontSize)
                throws IOException {
            if (value == null || value.isEmpty()) {
                return;
            }
            text(value, rightEdge - width(value, fontSize), top, fontSize, false);
        }

        private void textAligned(String value, float x, float boxWidth, float top,
                                 float fontSize, int align, boolean bold) throws IOException {
            if (value == null || value.isEmpty()) {
                return;
            }
            float textWidth = width(value, fontSize);
            float startX;
            if (align == 1) {
                startX = x + (boxWidth - textWidth) / 2f;
            } else if (align == 2) {
                startX = x + boxWidth - textWidth - 3f;
            } else {
                startX = x + 3f;
            }
            text(value, startX, top, fontSize, bold);
        }

        /** 竖排标签（整段文字旋转 -90°，与真实发票「购买方 / 销售方」一致）。 */
        private void verticalText(String value, float cellX, float cellWidth,
                                  float top, float bottom, float fontSize) throws IOException {
            float length = width(value, fontSize);
            float centerY = (y(top) + y(bottom)) / 2f;
            float startY = centerY + length / 2f;
            float startX = cellX + cellWidth / 2f - fontSize * 0.3f;

            cs.saveGraphicsState();
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.setTextMatrix(Matrix.getRotateInstance(-Math.PI / 2, startX, startY));
            cs.showText(value);
            cs.endText();
            cs.restoreGraphicsState();
        }

        private void hLine(float x1, float x2, float top, float lineWidth) throws IOException {
            cs.setLineWidth(lineWidth);
            cs.moveTo(x1, y(top));
            cs.lineTo(x2, y(top));
            cs.stroke();
        }

        private void vLine(float x, float top1, float top2, float lineWidth) throws IOException {
            cs.setLineWidth(lineWidth);
            cs.moveTo(x, y(top1));
            cs.lineTo(x, y(top2));
            cs.stroke();
        }

        private void rect(float x, float top, float width, float height, float lineWidth)
                throws IOException {
            cs.setLineWidth(lineWidth);
            cs.addRect(x, y(top + height), width, height);
            cs.stroke();
        }

        private float width(String value, float fontSize) throws IOException {
            if (value == null || value.isEmpty()) {
                return 0f;
            }
            return font.getStringWidth(value) / 1000f * fontSize;
        }

        private String ellipsis(String value, float maxWidth, float fontSize) throws IOException {
            if (value == null || value.isEmpty() || width(value, fontSize) <= maxWidth) {
                return value == null ? "" : value;
            }
            String ell = "…";
            StringBuilder sb = new StringBuilder();
            for (char ch : value.toCharArray()) {
                if (width(sb.toString() + ch + ell, fontSize) > maxWidth) {
                    break;
                }
                sb.append(ch);
            }
            return sb + ell;
        }

        private List<String> wrap(String value, float maxWidth, float fontSize, int maxLines)
                throws IOException {
            List<String> out = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (char ch : (value == null ? "" : value).toCharArray()) {
                if (ch == '\n') {
                    out.add(current.toString());
                    current.setLength(0);
                    if (out.size() >= maxLines) {
                        return out;
                    }
                    continue;
                }
                if (width(current.toString() + ch, fontSize) > maxWidth) {
                    out.add(current.toString());
                    current.setLength(0);
                    if (out.size() >= maxLines) {
                        return out;
                    }
                }
                current.append(ch);
            }
            if (current.length() > 0 && out.size() < maxLines) {
                out.add(current.toString());
            }
            return out;
        }

        private static String nz(String value) {
            return value == null ? "" : value;
        }
    }
}
