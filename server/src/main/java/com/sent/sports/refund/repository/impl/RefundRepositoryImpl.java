package com.sent.sports.refund.repository.impl;

import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundOrderPO;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.support.ImageJsonCodec;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 退款单仓储真库实现（收口阶段）：把 {@code refund_order} 作为唯一真源，替换进程内 {@code InMemoryRefundRepository}。
 * <p>同时修复两处割裂：① 用户端「我的售后 / 详情」此前为 P4 空桩，现可经 Mapper 真查；
 * ② 管理端审核写读此前各走内存与真库，现统一落到同一张表，审核结果对用户端可见。</p>
 *
 * <p>save 语义为 upsert：新单（apply / 迟到支付 ONLY_REFUND）走 INSERT；已存在单（审核 / 回调状态推进）走 UPDATE。
 * 防重仅靠 DB 唯一键 {@code uk_refund_no}，不引入分布式锁，与项目口径一致。</p>
 *
 * <p>只依赖本模块 Mapper 与领域，符合 ArchUnit 规则 4。</p>
 */
@Repository
public class RefundRepositoryImpl implements RefundRepository {

    private final RefundOrderMapper mapper;

    public RefundRepositoryImpl(RefundOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(RefundOrder order) {
        RefundOrderPO existing = mapper.selectByRefundNo(order.getRefundNo());
        if (existing == null) {
            mapper.insert(toPO(order));
            return;
        }
        // 已存在：仅推进会随业务变化的字段。退款完成（REFUNDED）落账时间，其余保持不变。
        LocalDateTime refundedAt = order.getStatus() == RefundStatus.REFUNDED ? LocalDateTime.now() : null;
        // 退回积分数只在真的退了才覆盖（0 视为「本单没有积分动作」，不冲掉历史值）
        Long pointRefund = order.getPointRefund() > 0L ? order.getPointRefund() : null;
        mapper.updateRefundState(order.getRefundNo(), order.getStatus().name(), order.getAppealCount(),
                refundedAt, order.getAuditRemark(), pointRefund);
    }

    @Override
    public Optional<RefundOrder> findByRefundNo(String refundNo) {
        return Optional.ofNullable(mapper.selectByRefundNo(refundNo)).map(this::toDomain);
    }

    private RefundOrderPO toPO(RefundOrder order) {
        RefundOrderPO po = new RefundOrderPO();
        po.setRefundNo(order.getRefundNo());
        po.setOrderId(order.getOrderId());
        po.setType(order.getType());
        po.setAmountCent(order.getAmountCent());
        po.setPointRefund(order.getPointRefund());
        po.setStatus(order.getStatus().name());
        // 用户申请的自由文本原因落 remark；ONLY_REFUND（迟到支付）归 SYSTEM，其余归 CUSTOMER。
        boolean onlyRefund = "ONLY_REFUND".equals(order.getType());
        po.setReasonCode(onlyRefund ? "SYSTEM" : "USER_APPLY");
        po.setRemark(order.getReason());
        po.setApplicantType(onlyRefund ? "STAFF" : "CUSTOMER");
        // 人工复核标记只在申请时写一次：它是「申请时刻的判定快照」，后续审核不覆盖
        po.setNeedManualReview(order.isNeedManualReview() ? 1 : 0);
        // 凭证图片：fileId 数组以 JSON 落 images_json，审核端再解码成图片列表展示
        po.setImagesJson(ImageJsonCodec.encode(order.getImages()));
        // created_at / updated_at 由 MetaObjectHandler 自动填充，此处不重复写入。
        return po;
    }

    private RefundOrder toDomain(RefundOrderPO po) {
        long createdAt = toMillis(po.getCreatedAt());
        RefundOrder order = new RefundOrder(
                po.getRefundNo(),
                po.getOrderNo() == null ? "" : po.getOrderNo(),
                po.getOrderId() == null ? 0L : po.getOrderId(),
                po.getAmountCent() == null ? 0L : po.getAmountCent(),
                po.getType(),
                po.getRemark() != null ? po.getRemark() : po.getReasonCode(),
                RefundStatus.valueOf(po.getStatus()),
                createdAt);
        order.setAppealCount(po.getAppealCount() == null ? 0 : po.getAppealCount().intValue());
        order.setPointRefund(po.getPointRefund() == null ? 0L : po.getPointRefund());
        order.setImages(ImageJsonCodec.decode(po.getImagesJson()));
        order.setNeedManualReview(po.getNeedManualReview() != null && po.getNeedManualReview() == 1);
        return order;
    }

    private static long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
