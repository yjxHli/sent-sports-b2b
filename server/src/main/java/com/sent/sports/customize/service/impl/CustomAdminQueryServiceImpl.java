package com.sent.sports.customize.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customize.dto.admin.AdminCustomDtos;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.customize.service.CustomAdminQueryService;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

/**
 * 定制审核查询（Phase 10 解除 P4 空桩）：待审列表与详情全部走真库。
 * <p>列表用 MyBatis-Plus {@link Page} 驱动 LIMIT/OFFSET 与 count；详情额外联查
 * {@code custom_draft} / {@code custom_review_log}，把图稿版本历史与审图日志一并返回，
 * 客服无需跳库即可看到「驳回 → 重传 → 通过」的完整轨迹。</p>
 */
@Service
public class CustomAdminQueryServiceImpl implements CustomAdminQueryService {

    private final CustomOrderMapper customOrderMapper;

    public CustomAdminQueryServiceImpl(CustomOrderMapper customOrderMapper) {
        this.customOrderMapper = customOrderMapper;
    }

    @Override
    public PageResult<AdminCustomDtos.PendingItemVO> pendingList(PageQuery pageQuery) {
        Page<CustomOrderPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<CustomOrderPO> rows = customOrderMapper.selectAdminPage(page);
        List<AdminCustomDtos.PendingItemVO> list = rows.getRecords().stream().map(this::toPending).toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public AdminCustomDtos.CustomDetailVO detail(String customOrderId) {
        CustomOrderPO po = customOrderMapper.selectByCustomNo(customOrderId);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        long pk = po.getId() == null
                ? requirePk(customOrderId)
                : po.getId();
        List<AdminCustomDtos.DraftItemVO> drafts = customOrderMapper.selectDrafts(pk).stream()
                .map(d -> new AdminCustomDtos.DraftItemVO(
                        d.getVersion() == null ? 0 : d.getVersion(), d.getFileId(), d.getFileName(),
                        d.getFileSize() == null ? 0L : d.getFileSize()))
                .toList();
        List<AdminCustomDtos.ReviewItemVO> reviews = customOrderMapper.selectReviewLogs(pk).stream()
                .map(r -> new AdminCustomDtos.ReviewItemVO(
                        r.getDraftVersion() == null ? 0 : r.getDraftVersion(), r.getResult(),
                        r.getReasonCode(), r.getRemark(), r.getReviewerId()))
                .toList();
        String rejectReason = "REJECTED".equals(po.getStatus()) ? po.getRemark() : null;
        return new AdminCustomDtos.CustomDetailVO(
                po.getCustomNo(),
                po.getStatus(),
                po.getCurrentVersion() == null ? 0 : po.getCurrentVersion(),
                po.getRejectCount() == null ? 0 : po.getRejectCount(),
                rejectReason,
                po.getReviewDeadline() == null ? 0L
                        : po.getReviewDeadline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                customOrderMapper.selectLatestFileId(pk),
                po.getLockedVersion() == null ? 0 : po.getLockedVersion(),
                drafts.size(),
                reviews.size(),
                drafts,
                reviews);
    }

    private AdminCustomDtos.PendingItemVO toPending(CustomOrderPO po) {
        long deadlineMillis = po.getReviewDeadline() == null ? 0L
                : po.getReviewDeadline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new AdminCustomDtos.PendingItemVO(
                po.getCustomNo(), po.getStatus(),
                po.getCurrentVersion() == null ? 0 : po.getCurrentVersion(),
                po.getRejectCount() == null ? 0 : po.getRejectCount(),
                deadlineMillis,
                po.getLockedVersion() == null ? 0 : po.getLockedVersion());
    }

    private long requirePk(String customNo) {
        Long pk = customOrderMapper.selectIdByCustomNo(customNo);
        if (pk == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return pk;
    }
}
