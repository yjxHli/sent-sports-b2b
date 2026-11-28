package com.sent.sports.customize.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customize.domain.CustomDraftTimeoutPolicy;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.domain.CustomStatusBucket;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderListItemPO;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.customize.mapper.po.CustomStatusCountRow;
import com.sent.sports.customize.service.CustomQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 定制查询实现（收口阶段）：详情与「我的定制单」均接入真实查询。
 * <p>列表经 {@link CustomOrderMapper#selectMyPage} 联查订单状态与最新图稿版本，总数由
 * {@link CustomOrderMapper#countMy} 统计，用 MyBatis-Plus 的 {@link Page} 驱动 LIMIT/OFFSET；
 * 详情按定制单号读取，查无单抛 {@code NOT_FOUND}。</p>
 */
@Service
public class CustomQueryServiceImpl implements CustomQueryService {

    private final CustomOrderMapper customOrderMapper;

    public CustomQueryServiceImpl(CustomOrderMapper customOrderMapper) {
        this.customOrderMapper = customOrderMapper;
    }

    @Override
    public CustomDtos.CustomVO detail(String customOrderId) {
        CustomOrderPO po = customOrderMapper.selectByCustomNo(customOrderId);
        if (po == null) {
            throw new com.sent.sports.common.error.BizException(
                    com.sent.sports.common.error.ErrorCode.NOT_FOUND);
        }
        long deadlineMillis = toMillis(po.getReviewDeadline());
        // 表无独立驳回原因列，remark 只在 REJECTED 状态作为驳回原因对外暴露，避免把普通备注误报成驳回原因
        String rejectReason = "REJECTED".equals(po.getStatus()) ? po.getRemark() : null;

        // 详情页要「看得见图稿、看得懂驳回」，把图稿版本与审图记录一起带出，省掉前端 N 次请求
        CustomOrderMapper.OrderRefRow orderRef = customOrderMapper.selectOrderRef(customOrderId);
        long pk = po.getId() == null ? 0L : po.getId();
        List<CustomOrderMapper.DraftRow> draftRows =
                pk == 0L ? List.of() : customOrderMapper.selectDrafts(pk);
        List<CustomOrderMapper.ReviewRow> logRows =
                pk == 0L ? List.of() : customOrderMapper.selectReviewLogs(pk);

        return new CustomDtos.CustomVO(
                po.getCustomNo(),
                po.getStatus(),
                po.getCurrentVersion() == null ? 0 : po.getCurrentVersion(),
                po.getRejectCount() == null ? 0 : po.getRejectCount(),
                rejectReason,
                deadlineMillis,
                draftDeadlineMillis(po, orderRef),
                orderRef == null ? null : orderRef.getOrderNo(),
                orderRef == null ? null : orderRef.getOrderStatus(),
                po.getServiceType(),
                po.getPrintSide(),
                po.getQty() == null ? 0 : po.getQty(),
                po.getFeeCent() == null ? 0L : po.getFeeCent(),
                po.getExpectShipDate() == null ? 0L
                        : po.getExpectShipDate().atStartOfDay(ZoneId.systemDefault())
                        .toInstant().toEpochMilli(),
                toMillis(po.getCreatedAt()),
                draftRows.stream().map(d -> new CustomDtos.DraftItemVO(
                        d.getVersion() == null ? 0 : d.getVersion(),
                        d.getFileId(),
                        d.getFileName(),
                        d.getFileSize() == null ? 0L : d.getFileSize(),
                        toMillis(d.getUploadedAt()))).toList(),
                logRows.stream().map(r -> new CustomDtos.ReviewLogVO(
                        r.getDraftVersion() == null ? 0 : r.getDraftVersion(),
                        r.getResult(),
                        r.getReasonCode(),
                        r.getRemark(),
                        toMillis(r.getCreatedAt()))).toList());
    }

    /** DATETIME → 毫秒；null 一律按「未设置」返回 0（前端据此显示占位，不显示 1970）。 */
    private static long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 图稿上传截止时刻：只有「待提交图稿 + 一稿未传 + 订单已支付」才回真实时间。
     * <p>已传过图稿（version > 0 或已进 WAIT_REVIEW/REJECTED）不存在倒计时；
     * 未支付的单由 OrderTimeoutJob 另行取消，也不该在详情页显示这条倒计时。</p>
     */
    private static long draftDeadlineMillis(CustomOrderPO po, CustomOrderMapper.OrderRefRow orderRef) {
        boolean waitingDraft = CustomStatus.WAIT_DRAFT.name().equals(po.getStatus())
                && (po.getCurrentVersion() == null || po.getCurrentVersion() == 0);
        if (!waitingDraft || orderRef == null || orderRef.getPaidAt() == null) {
            return 0L;
        }
        return toMillis(CustomDraftTimeoutPolicy.deadlineOf(orderRef.getPaidAt()));
    }

    @Override
    public PageResult<CustomDtos.MyCustomOrderVO> myOrders(long customerId, PageQuery pageQuery) {
        return myOrders(customerId, null, pageQuery);
    }

    @Override
    public PageResult<CustomDtos.MyCustomOrderVO> myOrders(long customerId, String bucket,
                                                          PageQuery pageQuery) {
        CustomStatusBucket resolved = CustomStatusBucket.of(bucket);
        List<String> statusNames = resolved == null ? null : resolved.statusNames();
        Page<CustomOrderListItemPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        customOrderMapper.selectMyPage(page, customerId, statusNames);
        // 总数与列表同条件：分页 total 与「共 N 单」都对得上，翻页也不会翻出空白页
        long total = customOrderMapper.countMy(customerId, statusNames);
        page.setTotal(total);

        java.util.List<CustomDtos.MyCustomOrderVO> list = page.getRecords().stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(list, total, pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public CustomDtos.MyCustomSummaryVO mySummary(long customerId) {
        long[] counts = new long[CustomStatusBucket.values().length];
        long total = 0L;
        for (CustomStatusCountRow row : customOrderMapper.countMyByStatus(customerId)) {
            long cnt = row.getTotal() == null ? 0L : row.getTotal();
            total += cnt;
            String status = row.getStatus();
            for (CustomStatusBucket bucket : CustomStatusBucket.values()) {
                if (bucket.matches(status)) {
                    counts[bucket.ordinal()] += cnt;
                }
            }
        }
        return new CustomDtos.MyCustomSummaryVO(
                counts[CustomStatusBucket.TODO.ordinal()],
                counts[CustomStatusBucket.REVIEW.ordinal()],
                counts[CustomStatusBucket.PRODUCING.ordinal()],
                counts[CustomStatusBucket.DONE.ordinal()],
                total);
    }

    @Override
    public List<CustomDtos.OrderCustomVO> ordersOfOrder(long customerId, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return List.of();
        }
        return customOrderMapper.selectByOrderNo(customerId, orderNo).stream()
                .map(r -> new CustomDtos.OrderCustomVO(
                        r.getCustomOrderId(),
                        r.getStatus(),
                        r.getVersion() == null ? 0 : r.getVersion(),
                        r.getRejectCount() == null ? 0 : r.getRejectCount(),
                        r.getQty() == null ? 0 : r.getQty(),
                        r.getFeeCent() == null ? 0L : r.getFeeCent(),
                        toMillis(r.getReviewDeadline())))
                .toList();
    }

    private CustomDtos.MyCustomOrderVO toVo(CustomOrderListItemPO po) {        long deadlineMillis = po.getReviewDeadline() == null ? 0L
                : po.getReviewDeadline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new CustomDtos.MyCustomOrderVO(
                po.getCustomOrderId(),
                po.getStatus(),
                po.getOrderStatus(),
                po.getVersion() == null ? 0 : po.getVersion(),
                po.getRejectCount() == null ? 0 : po.getRejectCount(),
                deadlineMillis);
    }
}
