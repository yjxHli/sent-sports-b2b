package com.sent.sports.refund.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端退款审核列表批量删除：只打 {@code refund_order.deleted} 软删标记。
 *
 * <p><b>不动财务</b>：退款金额、支付流水、应收均不改写；已删除的行从审核列表与 count 同步消失，
 * 用户端「我的退款」仍可见自己的申请（用户侧查询不带管理端 deleted 条件）。</p>
 */
@Service
public class RefundBatchDeleteUseCase {

    private final RefundOrderMapper refundOrderMapper;

    public RefundBatchDeleteUseCase(RefundOrderMapper refundOrderMapper) {
        this.refundOrderMapper = refundOrderMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return refundOrderMapper.batchMarkDeleted(BatchIds.normalize(ids));
    }
}
