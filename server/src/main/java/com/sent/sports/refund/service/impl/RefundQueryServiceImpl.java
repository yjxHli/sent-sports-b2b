package com.sent.sports.refund.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.refund.dto.RefundDtos;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundAuditItemPO;
import com.sent.sports.refund.mapper.po.RefundDetailPO;
import com.sent.sports.refund.mapper.po.RefundListPO;
import com.sent.sports.refund.service.RefundQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 退款查询实现：管理端审核列表与用户端「我的售后 / 详情」均接真实 MyBatis-Plus 分页查询。
 * 用户端按订单归属（orders.customer_id）过滤，非本人退款单详情返回 NOT_FOUND，保证归属隔离。
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
    public RefundDtos.RefundDetailVO getDetail(String refundNo, long customerId) {
        RefundDetailPO po = refundOrderMapper.selectDetailByRefundNo(refundNo);
        if (po == null || po.getCustomerId() == null || po.getCustomerId() != customerId) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toDetailVo(po);
    }

    @Override
    public PageResult<RefundDtos.RefundAuditVO> listAuditRefunds(PageQuery pageQuery, String status) {
        Page<RefundAuditItemPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        refundOrderMapper.selectAuditPage(page, status);
        long total = refundOrderMapper.countAudit(status);
        page.setTotal(total);
        List<RefundDtos.RefundAuditVO> list = page.getRecords().stream().map(this::toAuditVo).toList();
        return PageResult.of(list, total, pageQuery.getPage(), pageQuery.getSize());
    }

    private RefundDtos.RefundVO toListVo(RefundListPO po) {
        long amountCent = po.getAmountCent() == null ? 0L : po.getAmountCent();
        return new RefundDtos.RefundVO(
                po.getRefundNo(),
                po.getOrderNo() == null ? "" : po.getOrderNo(),
                po.getStatus(),
                amountCent,
                MoneyUtil.toYuan(amountCent));
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
                createdAtMillis);
    }

    private static long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static Long toMillisOrNull(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
