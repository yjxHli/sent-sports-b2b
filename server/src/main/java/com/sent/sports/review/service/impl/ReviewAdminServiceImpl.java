package com.sent.sports.review.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.JsonListCodec;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.review.dto.admin.AdminReviewDtos;
import com.sent.sports.review.mapper.ReviewMapper;
import com.sent.sports.review.mapper.po.AdminReviewRow;
import com.sent.sports.review.service.ReviewAdminService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 评价治理实现（V56）。
 *
 * <p>四条口径：</p>
 * <ul>
 *   <li><b>只改状态</b>：下架不等于删数据，正文/星级/图片原样保留，可随时恢复；</li>
 *   <li><b>幂等</b>：目标状态与当前一致时直接返回，不写库也不写日志（运营连点两下很常见）；</li>
 *   <li><b>必留痕</b>：真实变更写一条 {@code sys_operation_log}（HIDE/RESTORE + 前后状态 + 原因）；</li>
 *   <li><b>口径一致</b>：管理端与用户端用同一套昵称脱敏（{@link ReviewDisplayNames}）。</li>
 * </ul>
 */
@Service
public class ReviewAdminServiceImpl implements ReviewAdminService {

    /** 状态白名单：未知值直接拒绝，避免脏状态让用户端过滤条件失效。 */
    private static final Set<String> STATUSES = Set.of("NORMAL", "HIDDEN");
    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_HIDDEN = "HIDDEN";
    private static final String ACTION_HIDE = "HIDE";
    private static final String ACTION_RESTORE = "RESTORE";
    private static final int REASON_MAX = 200;

    private final ReviewMapper reviewMapper;

    public ReviewAdminServiceImpl(ReviewMapper reviewMapper) {
        this.reviewMapper = reviewMapper;
    }

    @Override
    public PageResult<AdminReviewDtos.AdminReviewVO> list(Long productId, Integer rating, String status,
                                                          PageQuery pageQuery) {
        if (productId != null && productId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品ID不合法");
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评分只能是 1~5 星");
        }
        String normalizedStatus = requireKnownStatus(status, false);

        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        Page<AdminReviewRow> page = new Page<>(query.getPage(), query.getSize());
        IPage<AdminReviewRow> rows = reviewMapper.selectAdminPage(page, productId, rating, normalizedStatus);
        List<AdminReviewDtos.AdminReviewVO> list = rows == null || rows.getRecords() == null
                ? List.of()
                : rows.getRecords().stream().map(this::toVo).toList();
        long total = rows == null ? 0L : rows.getTotal();
        return PageResult.of(list, total, query.getPage(), query.getSize());
    }

    @Override
    @UseCase
    public AdminReviewDtos.AdminReviewVO updateStatus(long reviewId, String status, String reason,
                                                      Long operatorId) {
        if (reviewId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价ID不合法");
        }
        String target = requireKnownStatus(status, true);
        String normalizedReason = normalizeReason(reason);

        AdminReviewRow existing = reviewMapper.selectAdminById(reviewId);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String before = normalizeCurrent(existing.getStatus());
        if (before.equals(target)) {
            // 幂等：重复点击「隐藏」不该产生第二条日志，也不该报错
            return toVo(existing);
        }
        if (reviewMapper.updateStatus(reviewId, target, operatorId) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        reviewMapper.insertStatusLog(operatorId,
                STATUS_HIDDEN.equals(target) ? ACTION_HIDE : ACTION_RESTORE,
                String.valueOf(reviewId), before,
                "{\"status\":\"" + target + "\"}", normalizedReason, operatorId);
        existing.setStatus(target);
        return toVo(existing);
    }

    private AdminReviewDtos.AdminReviewVO toVo(AdminReviewRow row) {
        boolean anonymous = row.getIsAnonymous() != null && row.getIsAnonymous() == 1;
        List<String> images = JsonListCodec.read(row.getImagesJson(), String.class);
        return new AdminReviewDtos.AdminReviewVO(
                row.getReviewId() == null ? "" : String.valueOf(row.getReviewId()),
                row.getProductId() == null ? 0L : row.getProductId(),
                row.getProductTitle() == null ? "" : row.getProductTitle(),
                row.getRating() == null ? 0 : row.getRating(),
                row.getContent(),
                images,
                ReviewDisplayNames.of(row.getNickname(), anonymous),
                anonymous,
                normalizeCurrent(row.getStatus()),
                toMillis(row.getCreatedAt()));
    }

    /**
     * @param required true 表示必填（状态变更）；false 表示筛选项，空白归一成 null
     */
    private String requireKnownStatus(String status, boolean required) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            if (required) {
                throw new BizException(ErrorCode.PARAM_INVALID, "状态不能为空");
            }
            return null;
        }
        if (!STATUSES.contains(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态只能是 NORMAL 或 HIDDEN");
        }
        return value;
    }

    /** 历史行理论上不会为空（V56 列 NOT NULL），仍兜底成 NORMAL，避免空串落进比较。 */
    private String normalizeCurrent(String status) {
        return status == null || status.isBlank() ? STATUS_NORMAL : status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeReason(String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > REASON_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "操作原因不超过 " + REASON_MAX + " 字");
        }
        return text;
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
