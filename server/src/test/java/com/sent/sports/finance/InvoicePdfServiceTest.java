package com.sent.sports.finance;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.po.InvoicePdfHeaderPO;
import com.sent.sports.finance.mapper.po.InvoicePdfItemPO;
import com.sent.sports.finance.pdf.InvoicePdfModel;
import com.sent.sports.finance.pdf.InvoicePdfRenderer;
import com.sent.sports.finance.service.dto.InvoicePdfFile;
import com.sent.sports.finance.service.impl.InvoicePdfServiceImpl;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发票 PDF 服务：查无此票要给可定位的 40401，正常路径要落到「一张 PDF + 可下载文件名」。
 */
class InvoicePdfServiceTest extends BaseUseCaseTest {

    @Mock
    private InvoiceMapper invoiceMapper;

    @Mock
    private InvoicePdfRenderer renderer;

    @InjectMocks
    private InvoicePdfServiceImpl service;

    private static InvoicePdfHeaderPO header() {
        InvoicePdfHeaderPO po = new InvoicePdfHeaderPO();
        po.setInvoiceId(4L);
        po.setInvoiceNo("INV-4");
        po.setStatus("ISSUED");
        po.setIssuedAt(LocalDateTime.of(2026, 11, 10, 19, 17, 39));
        po.setTitle("森特体育用品有限公司");
        po.setCustomerNickname("林小满");
        po.setOrderId(2078L);
        po.setOrderNo("SO20261108900649");
        po.setPayableCent(20_000L);
        return po;
    }

    private static InvoicePdfItemPO item() {
        InvoicePdfItemPO po = new InvoicePdfItemPO();
        po.setSpuTitleSnap("专业羽毛球拍 碳纤维");
        po.setSpecSnap("4U/G5");
        po.setQty(1);
        po.setUnitPriceCent(20_000L);
        po.setAmountCent(20_000L);
        po.setSkuId(9102L);
        return po;
    }

    @Test
    @DisplayName("正常出票：查票头 + 明细，交给渲染器，返回 PDF 字节与下载文件名")
    void rendersPdfForExistingInvoice() {
        when(invoiceMapper.selectPdfHeader(4L)).thenReturn(header());
        when(invoiceMapper.selectPdfItems(2078L)).thenReturn(List.of(item()));
        byte[] bytes = new byte[]{'%', 'P', 'D', 'F'};
        when(renderer.render(any(InvoicePdfModel.class))).thenReturn(bytes);

        InvoicePdfFile file = service.render(4L);

        assertEquals("发票-INV-4.pdf", file.fileName());
        assertEquals(4, file.content().length);

        ArgumentCaptor<InvoicePdfModel> captor = ArgumentCaptor.forClass(InvoicePdfModel.class);
        verify(renderer).render(captor.capture());
        assertEquals("INV-4", captor.getValue().meta().invoiceNo());
        assertEquals(1, captor.getValue().lines().size());
        assertEquals(20_000L, captor.getValue().totals().grossCent());
    }

    @Test
    @DisplayName("查无此发票：40401 且提示「开票申请不存在」，不去渲染空白票")
    void notFoundGivesActionableMessage() {
        when(invoiceMapper.selectPdfHeader(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.render(999L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("开票申请不存在"), ex.resolvedMessage());
        verify(renderer, never()).render(any());
    }

    @Test
    @DisplayName("非法 ID：40001，避免拿 0/负数去查库")
    void rejectsInvalidId() {
        BizException ex = assertThrows(BizException.class, () -> service.render(0L));

        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(invoiceMapper, never()).selectPdfHeader(0L);
    }

    @Test
    @DisplayName("发票号缺失时文件名仍合法（不会出现「发票-null.pdf」）")
    void fileNameFallsBackToId() {
        InvoicePdfHeaderPO po = header();
        po.setInvoiceNo(null);
        when(invoiceMapper.selectPdfHeader(4L)).thenReturn(po);
        when(invoiceMapper.selectPdfItems(2078L)).thenReturn(List.of(item()));
        when(renderer.render(any(InvoicePdfModel.class))).thenReturn(new byte[]{1, 2});

        InvoicePdfFile file = service.render(4L);

        assertTrue(file.fileName().contains("4"), file.fileName());
        assertTrue(file.fileName().endsWith(".pdf"), file.fileName());
        assertTrue(!file.fileName().contains("null"), file.fileName());
    }
}
