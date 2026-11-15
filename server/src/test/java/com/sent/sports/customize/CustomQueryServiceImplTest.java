package com.sent.sports.customize;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.customize.service.impl.CustomQueryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 定制单详情聚合（TDD 红→绿）。
 *
 * 背景：用户端「我的定制」列表点了打不开详情页。详情页要展示「我传过的每一版稿」
 * 和「客服为什么驳回」，所以 {@code GET /api/customize/orders/{no}} 必须把
 * {@code custom_draft} 与 {@code custom_review_log} 一起带出，而不是只有单头 6 个字段。
 */
@ExtendWith(MockitoExtension.class)
class CustomQueryServiceImplTest {

    private static final String NO = "CU20261115000001";
    private static final long PK = 11L;

    @Mock
    private CustomOrderMapper mapper;

    @InjectMocks
    private CustomQueryServiceImpl service;

    private static long millis(LocalDateTime dt) {
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static CustomOrderPO po(String status, int version, int rejectCount) {
        CustomOrderPO po = new CustomOrderPO();
        po.setId(PK);
        po.setCustomNo(NO);
        po.setStatus(status);
        po.setCurrentVersion(version);
        po.setRejectCount(rejectCount);
        po.setQty(30);
        po.setFeeCent(120_000L);
        po.setServiceType("UV");
        po.setPrintSide("FRONT");
        po.setReviewDeadline(LocalDateTime.of(2026, 11, 16, 10, 0));
        po.setExpectShipDate(LocalDate.of(2026, 11, 20));
        po.setRemark("图稿分辨率不足，请重新上传");
        po.setCreatedAt(LocalDateTime.of(2026, 11, 15, 9, 30));
        return po;
    }

    private static CustomOrderMapper.DraftRow draft(int version, String fileId, String name, long size) {
        CustomOrderMapper.DraftRow row = new CustomOrderMapper.DraftRow();
        row.setVersion(version);
        row.setFileId(fileId);
        row.setFileName(name);
        row.setFileSize(size);
        row.setUploadedAt(LocalDateTime.of(2026, 11, 15, 10, version));
        return row;
    }

    private static CustomOrderMapper.ReviewRow log(int version, String result, String remark) {
        CustomOrderMapper.ReviewRow row = new CustomOrderMapper.ReviewRow();
        row.setDraftVersion(version);
        row.setResult(result);
        row.setReasonCode("LOW_RESOLUTION");
        row.setRemark(remark);
        row.setReviewerId(9003L);
        row.setCreatedAt(LocalDateTime.of(2026, 11, 15, 11, 0));
        return row;
    }

    @Test
    @DisplayName("详情聚合：图稿版本、审图记录、关联订单与印刷参数都带出")
    void detailAggregatesDraftsLogsAndOrder() {
        when(mapper.selectByCustomNo(NO)).thenReturn(po("REJECTED", 2, 1));

        CustomOrderMapper.OrderRefRow ref = new CustomOrderMapper.OrderRefRow();
        ref.setOrderNo("SO20261115000002");
        ref.setOrderStatus("WAIT_SHIP");
        when(mapper.selectOrderRef(NO)).thenReturn(ref);

        when(mapper.selectDrafts(PK)).thenReturn(List.of(
                draft(1, "2026/11/aa/v1.png", "v1.png", 1024L),
                draft(2, "2026/11/bb/v2.png", "v2.png", 2048L)));
        when(mapper.selectReviewLogs(PK)).thenReturn(List.of(
                log(1, "REJECT", "图稿分辨率不足，请重新上传")));

        CustomDtos.CustomVO vo = service.detail(NO);

        assertEquals(NO, vo.customOrderId());
        assertEquals("REJECTED", vo.status());
        assertEquals(2, vo.version());
        assertEquals(1, vo.rejectCount());
        // 驳回态才把 remark 当驳回原因暴露
        assertEquals("图稿分辨率不足，请重新上传", vo.rejectReason());

        // 印刷/数量/费用/预计发货日/下单时间
        assertEquals("UV", vo.serviceType());
        assertEquals("FRONT", vo.printSide());
        assertEquals(30, vo.qty());
        assertEquals(120_000L, vo.feeCent());
        assertEquals(millis(LocalDateTime.of(2026, 11, 20, 0, 0)), vo.expectShipDateMillis());
        assertEquals(millis(LocalDateTime.of(2026, 11, 15, 9, 30)), vo.createdAtMillis());

        // 关联订单（详情页可跳订单详情）
        assertEquals("SO20261115000002", vo.orderNo());
        assertEquals("WAIT_SHIP", vo.orderStatus());

        // 图稿版本：按 version 升序，fileId 供 /api/files/{fileId} 预览
        assertEquals(2, vo.drafts().size());
        assertEquals(1, vo.drafts().get(0).version());
        assertEquals("2026/11/aa/v1.png", vo.drafts().get(0).fileId());
        assertEquals("v2.png", vo.drafts().get(1).fileName());
        assertEquals(2048L, vo.drafts().get(1).fileSize());
        assertTrue(vo.drafts().get(1).uploadedAtMillis() > 0);

        // 审图记录
        assertEquals(1, vo.reviewLogs().size());
        assertEquals(1, vo.reviewLogs().get(0).draftVersion());
        assertEquals("REJECT", vo.reviewLogs().get(0).result());
        assertEquals("图稿分辨率不足，请重新上传", vo.reviewLogs().get(0).remark());
        assertTrue(vo.reviewLogs().get(0).reviewedAtMillis() > 0);
    }

    @Test
    @DisplayName("详情：还没传稿的定制单给空列表，前端渲染空态而不是报错")
    void detailWithoutDraftsReturnsEmptyLists() {
        when(mapper.selectByCustomNo(NO)).thenReturn(po("WAIT_DRAFT", 0, 0));
        when(mapper.selectOrderRef(NO)).thenReturn(null);
        when(mapper.selectDrafts(PK)).thenReturn(List.of());
        when(mapper.selectReviewLogs(PK)).thenReturn(List.of());

        CustomDtos.CustomVO vo = service.detail(NO);

        assertEquals(0, vo.drafts().size());
        assertEquals(0, vo.reviewLogs().size());
        assertNull(vo.orderNo(), "订单缺失时不应抛错，只是不给跳转入口");
        // 非驳回态不把普通备注当驳回原因
        assertNull(vo.rejectReason());
        assertEquals(0L, vo.expectShipDateMillis() - millis(LocalDateTime.of(2026, 11, 20, 0, 0)));
    }

    @Test
    @DisplayName("详情：查无单抛 NOT_FOUND")
    void detailNotFound() {
        when(mapper.selectByCustomNo("CU404")).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.detail("CU404"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
