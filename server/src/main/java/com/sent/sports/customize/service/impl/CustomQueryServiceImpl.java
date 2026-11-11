package com.sent.sports.customize.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderListItemPO;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.customize.service.CustomQueryService;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

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
        long deadlineMillis = po.getReviewDeadline() == null ? 0L
                : po.getReviewDeadline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // 表无独立驳回原因列，remark 只在 REJECTED 状态作为驳回原因对外暴露，避免把普通备注误报成驳回原因
        String rejectReason = "REJECTED".equals(po.getStatus()) ? po.getRemark() : null;
        return new CustomDtos.CustomVO(
                po.getCustomNo(),
                po.getStatus(),
                po.getCurrentVersion() == null ? 0 : po.getCurrentVersion(),
                po.getRejectCount() == null ? 0 : po.getRejectCount(),
                rejectReason,
                deadlineMillis);
    }

    @Override
    public PageResult<CustomDtos.MyCustomOrderVO> myOrders(long customerId, PageQuery pageQuery) {
        Page<CustomOrderListItemPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        customOrderMapper.selectMyPage(page, customerId);
        long total = customOrderMapper.countMy(customerId);
        page.setTotal(total);

        java.util.List<CustomDtos.MyCustomOrderVO> list = page.getRecords().stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(list, total, pageQuery.getPage(), pageQuery.getSize());
    }

    private CustomDtos.MyCustomOrderVO toVo(CustomOrderListItemPO po) {
        long deadlineMillis = po.getReviewDeadline() == null ? 0L
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
