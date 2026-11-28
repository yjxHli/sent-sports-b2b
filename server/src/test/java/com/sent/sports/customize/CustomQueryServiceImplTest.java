package com.sent.sports.customize;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderListItemPO;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.customize.mapper.po.CustomStatusCountRow;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    // ------------------------------------------------------------------
    // 图稿上传倒计时（2026-11-23）：支付后 72 小时未上传将自动取消退款
    // ------------------------------------------------------------------

    @Test
    @DisplayName("待提交图稿且订单已支付：详情带出上传截止时刻（支付 + 72 小时）")
    void detailExposesDraftDeadlineForUnpaidDraftOrder() {
        when(mapper.selectByCustomNo(NO)).thenReturn(po("WAIT_DRAFT", 0, 0));
        CustomOrderMapper.OrderRefRow ref = new CustomOrderMapper.OrderRefRow();
        ref.setOrderNo("SO20261120000001");
        ref.setOrderStatus("WAIT_SHIP");
        ref.setPaidAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        when(mapper.selectOrderRef(NO)).thenReturn(ref);
        when(mapper.selectDrafts(PK)).thenReturn(List.of());
        when(mapper.selectReviewLogs(PK)).thenReturn(List.of());

        CustomDtos.CustomVO vo = service.detail(NO);

        assertEquals(millis(LocalDateTime.of(2026, 11, 23, 10, 0)), vo.draftDeadlineMillis(),
                "截止时刻必须是支付时间 + 72 小时");
    }

    @Test
    @DisplayName("已传过图稿 / 未支付：不倒计时（0 = 前端不展示该模块）")
    void detailOmitsDraftDeadlineWhenDraftAlreadySubmittedOrUnpaid() {
        // 已提交过一稿（版本 > 0）
        when(mapper.selectByCustomNo(NO)).thenReturn(po("WAIT_REVIEW", 1, 0));
        CustomOrderMapper.OrderRefRow paid = new CustomOrderMapper.OrderRefRow();
        paid.setPaidAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        when(mapper.selectOrderRef(NO)).thenReturn(paid);
        when(mapper.selectDrafts(PK)).thenReturn(List.of());
        when(mapper.selectReviewLogs(PK)).thenReturn(List.of());

        assertEquals(0L, service.detail(NO).draftDeadlineMillis());

        // 待提交但订单还没支付（未支付单由 OrderTimeoutJob 处理，不显示倒计时）
        when(mapper.selectByCustomNo(NO)).thenReturn(po("WAIT_DRAFT", 0, 0));
        CustomOrderMapper.OrderRefRow unpaid = new CustomOrderMapper.OrderRefRow();
        unpaid.setPaidAt(null);
        when(mapper.selectOrderRef(NO)).thenReturn(unpaid);

        assertEquals(0L, service.detail(NO).draftDeadlineMillis());
    }

    // ------------------------------------------------------------------
    // 订单详情页的定制卡片（2026-11-21 用户反馈）
    // ------------------------------------------------------------------

    private static CustomOrderMapper.OrderCustomRow orderCustomRow(String no, String status,
                                                                   int version, int reject, int qty) {
        CustomOrderMapper.OrderCustomRow row = new CustomOrderMapper.OrderCustomRow();
        row.setCustomOrderId(no);
        row.setStatus(status);
        row.setVersion(version);
        row.setRejectCount(reject);
        row.setQty(qty);
        row.setFeeCent(300_00L);
        row.setReviewDeadline(LocalDateTime.of(2026, 11, 22, 18, 0));
        return row;
    }

    @Test
    @DisplayName("按订单查定制单：订单详情页拿到定制单号、状态与图稿版本，可直接跳上传/详情")
    void ordersOfOrderMapsRows() {
        String orderNo = "SO20261121000001";
        when(mapper.selectByOrderNo(9002L, orderNo)).thenReturn(List.of(
                orderCustomRow(NO, "WAIT_DRAFT", 0, 0, 30),
                orderCustomRow("CU20261121000002", "REJECTED", 2, 1, 10)));

        List<CustomDtos.OrderCustomVO> list = service.ordersOfOrder(9002L, orderNo);

        assertEquals(2, list.size());
        CustomDtos.OrderCustomVO first = list.get(0);
        assertEquals(NO, first.customOrderId());
        assertEquals("WAIT_DRAFT", first.status());
        assertEquals(0, first.version());
        assertEquals(30, first.qty());
        assertEquals(300_00L, first.feeCent());
        assertEquals(millis(LocalDateTime.of(2026, 11, 22, 18, 0)), first.reviewDeadlineMillis());
        assertEquals("REJECTED", list.get(1).status());
        assertEquals(2, list.get(1).version());
        assertEquals(1, list.get(1).rejectCount());
    }

    @Test
    @DisplayName("按订单查定制单：订单没有定制件时返回空列表（不抛错，页面不渲染卡片）")
    void ordersOfOrderReturnsEmptyForPlainOrder() {
        when(mapper.selectByOrderNo(9002L, "SO20261121000003")).thenReturn(List.of());
        assertTrue(service.ordersOfOrder(9002L, "SO20261121000003").isEmpty());
    }

    // ---------- 四类筛选桶（2026-11-28） ----------

    private CustomOrderListItemPO listItem(String no, String status) {
        CustomOrderListItemPO po = new CustomOrderListItemPO();
        po.setCustomOrderId(no);
        po.setStatus(status);
        po.setOrderStatus("COMPLETED");
        po.setVersion(1);
        po.setRejectCount(0);
        return po;
    }

    @Test
    @DisplayName("bucket=todo：查询条件落到 status IN (WAIT_DRAFT, REJECTED)，列表与总数同源")
    void myOrdersFiltersByBucketStatuses() {
        List<String> todo = List.of("WAIT_DRAFT", "REJECTED");
        when(mapper.selectMyPage(any(), eq(9002L), eq(todo))).thenAnswer(inv -> {
            Page<CustomOrderListItemPO> page = inv.getArgument(0);
            page.setRecords(List.of(listItem(NO, "WAIT_DRAFT")));
            return page;
        });
        when(mapper.countMy(9002L, todo)).thenReturn(1L);

        PageResult<CustomDtos.MyCustomOrderVO> result =
                service.myOrders(9002L, "TODO", new PageQuery());

        assertEquals(1L, result.getTotal(), "总数必须与列表同条件，否则分页会翻出空白页");
        assertEquals(1, result.getList().size());
        assertEquals(NO, result.getList().get(0).customOrderId());
    }

    @Test
    @DisplayName("非法 / 空 bucket 退回「全部」：statusNames 传 null，不带 IN 条件")
    void myOrdersFallsBackToAllForUnknownBucket() {
        when(mapper.selectMyPage(any(), eq(9002L), isNull())).thenAnswer(inv -> {
            Page<CustomOrderListItemPO> page = inv.getArgument(0);
            page.setRecords(List.of());
            return page;
        });
        when(mapper.countMy(eq(9002L), isNull())).thenReturn(0L);

        assertEquals(0L, service.myOrders(9002L, "bogus", new PageQuery()).getTotal());
        assertEquals(0L, service.myOrders(9002L, "  ", new PageQuery()).getTotal());
        assertEquals(0L, service.myOrders(9002L, null, new PageQuery()).getTotal());
    }

    @Test
    @DisplayName("四类角标：按 status 分组计数归到桶里，已取消不属于任何桶但计入 total")
    void mySummaryGroupsStatusesIntoBuckets() {
        when(mapper.countMyByStatus(9002L)).thenReturn(List.of(
                countRow("WAIT_DRAFT", 2L),
                countRow("REJECTED", 1L),
                countRow("WAIT_REVIEW", 4L)));

        CustomDtos.MyCustomSummaryVO onlyTodo = service.mySummary(9002L);
        assertEquals(3L, onlyTodo.todo(), "待提交图稿 + 审核驳回 = 3");
        assertEquals(4L, onlyTodo.review());
        assertEquals(0L, onlyTodo.producing());
        assertEquals(0L, onlyTodo.done());

        when(mapper.countMyByStatus(9002L)).thenReturn(List.of(
                countRow("APPROVED", 1L),
                countRow("IN_PRODUCTION", 2L),
                countRow("FINISHED", 3L),
                countRow("SHIPPED", 4L),
                countRow("CANCELLED", 5L),
                countRow("ESCALATED", 1L)));

        CustomDtos.MyCustomSummaryVO all = service.mySummary(9002L);

        assertEquals(0L, all.todo());
        assertEquals(1L, all.review(), "待主管处理也算「审核中」");
        assertEquals(3L, all.producing(), "审核通过 + 生产中 = 3");
        assertEquals(7L, all.done(), "已完工 + 已发货 = 7");
        assertEquals(16L, all.total(), "total 含已取消（5 单），所以可能大于四桶之和");
    }

    private CustomStatusCountRow countRow(String status, long total) {
        CustomStatusCountRow row = new CustomStatusCountRow();
        row.setStatus(status);
        row.setTotal(total);
        return row;
    }
}
