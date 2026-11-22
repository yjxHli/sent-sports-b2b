package com.sent.sports.refund.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.refund.dto.RefundDtos;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundAuditDetailPO;
import com.sent.sports.refund.mapper.po.RefundAuditItemPO;
import com.sent.sports.refund.mapper.po.RefundDetailPO;
import com.sent.sports.refund.mapper.po.RefundListPO;
import com.sent.sports.refund.mapper.po.RefundOrderLinePO;
import com.sent.sports.refund.service.RefundQueryService;
import com.sent.sports.refund.support.AddressSnapshotCodec;
import com.sent.sports.refund.support.ImageJsonCodec;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 退款查询实现：管理端审核列表 / 审核详情与用户端「我的售后 / 详情」均接真实 MyBatis-Plus 查询。
 * 用户端按订单归属（orders.customer_id）过滤，非本人退款单详情返回 NOT_FOUND，保证归属隔离；
 * 管理端审核详情一次联出退款单 + 订单头 + 客户 + 物流 + 商品行，供独立审核页单请求渲染。
 */
@Service
public class RefundQueryServiceImpl implements RefundQueryService {

    private final RefundOrderMapper refundOrderMapper;

    public RefundQueryServiceImpl(RefundOrderMapper refundOrderMapper) {
        this.refundOrderMapper = refundOrderMapper;
    }

    @Override
    public PageResult<RefundDtos.RefundVO> listMyRefunds(PageQuery pageQuery, long customerId, String status) {
        Page<RefundListPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        refundOrderMapper.selectMyPage(page, customerId, status);
        long total = refundOrderMapper.countMy(customerId, status);
        page.setTotal(total);
        List<RefundDtos.RefundVO> list = page.getRecords().stream().map(this::toListVo).toList();
        return PageResult.of(list, total, pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public RefundDtos.RefundCountVO countMyRefunds(long customerId) {
        if (customerId <= 0) {
            return new RefundDtos.RefundCountVO(0L, 0L);
        }
        long total = refundOrderMapper.countMy(customerId, null);
        long inFlight = refundOrderMapper.countMyInFlight(customerId);
        return new RefundDtos.RefundCountVO(total, inFlight);
    }

    @Override
    public RefundDtos.RefundDetailVO getDetail(String refundNo, long customerId) {
        RefundDetailPO po = refundOrderMapper.selectDetailByRefundNo(refundNo);
        if (po == null || po.getCustomerId() == null || po.getCustomerId() != customerId) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toDetailVo(po);
    }

    @Override
    public PageResult<RefundDtos.RefundAuditVO> listAuditRefunds(PageQuery pageQuery, String status,
                                                                String keyword) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<RefundAuditItemPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        refundOrderMapper.selectAuditPage(page, status, normalizedKeyword);
        long total = refundOrderMapper.countAudit(status, normalizedKeyword);
        page.setTotal(total);
        List<RefundDtos.RefundAuditVO> list = page.getRecords().stream().map(this::toAuditVo).toList();
        return PageResult.of(list, total, pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public RefundDtos.RefundAuditDetailVO getAuditDetail(String refundNo) {
        RefundAuditDetailPO po = refundOrderMapper.selectAuditDetail(refundNo);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<RefundOrderLinePO> lineRows = po.getOrderId() == null
                ? List.of()
                : refundOrderMapper.selectOrderLines(po.getOrderId());
        List<RefundDtos.AuditOrderLineVO> lines = lineRows == null ? List.of() : lineRows.stream()
                .map(r -> new RefundDtos.AuditOrderLineVO(
                        r.getSkuId(), r.getSkuName(), r.getSpec(), r.getQty(),
                        r.getUnitPriceCent(), r.getAmountCent(), r.getLinePaidCent(),
                        r.getShippedQty(), r.getCustomStatus()))
                .toList();
        AddressSnapshotCodec.Receiver receiver = AddressSnapshotCodec.decode(po.getAddressJson());
        long amountCent = po.getAmountCent() == null ? 0L : po.getAmountCent();
        return new RefundDtos.RefundAuditDetailVO(
                po.getRefundNo(),
                po.getOrderNo() == null ? "" : po.getOrderNo(),
                po.getStatus(),
                amountCent,
                MoneyUtil.toYuan(amountCent),
                po.getPointRefund() == null ? 0L : po.getPointRefund(),
                po.getType(),
                po.getReason(),
                po.getApplicantType(),
                po.getAppealCount() == null ? 0 : po.getAppealCount().intValue(),
                ImageJsonCodec.decode(po.getImagesJson()),
                po.getAuditRemark(),
                toMillis(po.getCreatedAt()),
                toMillisOrNull(po.getRefundedAt()),
                toAuditOrderVo(po, receiver, lines),
                po.getNeedManualReview() != null && po.getNeedManualReview() == 1);
    }

    private RefundDtos.AuditOrderVO toAuditOrderVo(RefundAuditDetailPO po,
                                                   AddressSnapshotCodec.Receiver receiver,
                                                   List<RefundDtos.AuditOrderLineVO> lines) {
        boolean shipped = po.getShippedAt() != null
                || lines.stream().anyMatch(l -> l.shippedQty() != null && l.shippedQty() > 0);
        return new RefundDtos.AuditOrderVO(
                po.getOrderId(),
                po.getOrderNo() == null ? "" : po.getOrderNo(),
                po.getOrderStatus(),
                po.getPayStatus(),
                shipStatusOf(po.getOrderStatus()),
                po.getAfterSaleStatus(),
                po.getCustomerId(),
                po.getCustomerName(),
                po.getCustomerPhone(),
                po.getCustomerNo(),
                po.getCustomerType(),
                po.getCustomerLevel(),
                toMillisOrNull(po.getOrderCreatedAt()),
                toMillisOrNull(po.getPaidAt()),
                toMillisOrNull(po.getCompletedAt()),
                nonNull(po.getGoodsAmountCent()),
                nonNull(po.getFreightCent()),
                nonNull(po.getPointDeductCent()),
                nonNull(po.getDiscountCent()),
                nonNull(po.getPayableCent()),
                nonNull(po.getPaidCent()),
                receiver.receiver(),
                receiver.phone(),
                receiver.address(),
                shipped,
                po.getCarrier(),
                po.getTrackingNo(),
                toMillisOrNull(po.getShippedAt()),
                po.getOrderRemark(),
                lines);
    }

    /** orders 表没有独立的 ship_status 列，按主状态推导成前端 StatusTag 认识的枚举值（与订单详情同口径）。 */
    private static String shipStatusOf(String status) {
        if (status == null) {
            return "WAIT_SHIP";
        }
        return switch (status) {
            case "PART_SHIPPED" -> "PART_SHIPPED";
            case "WAIT_RECEIVE", "COMPLETED" -> "SHIPPED";
            case "CANCELLED", "CLOSED" -> "CLOSED";
            default -> "WAIT_SHIP";
        };
    }

    private static long nonNull(Long value) {
        return value == null ? 0L : value;
    }

    private RefundDtos.RefundVO toListVo(RefundListPO po) {
        long amountCent = po.getAmountCent() == null ? 0L : po.getAmountCent();
        return new RefundDtos.RefundVO(
                po.getRefundNo(),
                po.getOrderNo() == null ? "" : po.getOrderNo(),
                po.getStatus(),
                amountCent,
                MoneyUtil.toYuan(amountCent),
                toMillisOrNull(po.getCreatedAt()),
                po.getReason(),
                po.getItemName(),
                po.getItemQty() == null ? 0 : po.getItemQty(),
                po.getProductId() == null ? null : String.valueOf(po.getProductId()));
    }

    private RefundDtos.RefundDetailVO toDetailVo(RefundDetailPO po) {
        long amountCent = po.getAmountCent() == null ? 0L : po.getAmountCent();
        long createdAt = toMillis(po.getCreatedAt());
        Long refundedAt = toMillisOrNull(po.getRefundedAt());
        return new RefundDtos.RefundDetailVO(
                po.getRefundNo(),
                po.getOrderNo() == null ? "" : po.getOrderNo(),
                po.getStatus(),
                amountCent,
                MoneyUtil.toYuan(amountCent),
                po.getType(),
                po.getReason(),
                po.getApplicantType(),
                po.getAppealCount() == null ? 0 : po.getAppealCount().intValue(),
                ImageJsonCodec.decode(po.getImagesJson()),
                createdAt,
                refundedAt);
    }

    private RefundDtos.RefundAuditVO toAuditVo(RefundAuditItemPO po) {
        LocalDateTime createdAt = po.getCreatedAt();
        long createdAtMillis = createdAt == null ? 0L
                : createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new RefundDtos.RefundAuditVO(
                po.getRefundNo(),
                po.getOrderNo() == null ? "" : po.getOrderNo(),
                po.getStatus(),
                po.getAmountCent() == null ? 0L : po.getAmountCent(),
                po.getReason(),
                ImageJsonCodec.decode(po.getImagesJson()),
                createdAtMillis,
                po.getNeedManualReview() != null && po.getNeedManualReview() == 1);
    }

    private static long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static Long toMillisOrNull(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
