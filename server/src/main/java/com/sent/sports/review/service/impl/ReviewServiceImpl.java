package com.sent.sports.review.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.JsonListCodec;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.review.dto.ReviewDtos;
import com.sent.sports.review.mapper.ReviewMapper;
import com.sent.sports.review.mapper.po.ProductReviewPO;
import com.sent.sports.review.mapper.po.ReviewRow;
import com.sent.sports.review.mapper.po.ReviewSummaryRow;
import com.sent.sports.review.service.ReviewService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 商品评价实现（V55）。
 *
 * <p>五条口径：</p>
 * <ul>
 *   <li><b>先校验后落库</b>：入参（星级/字数/图片）在查订单之前拦掉，脏请求不打数据库；</li>
 *   <li><b>资格来自订单</b>：只有「该客户的已完成订单里含此商品」才能评价，
 *       资格由 {@link OrderQueryService} 判定，本模块不碰订单表；</li>
 *   <li><b>防重靠唯一索引</b>：不做「先查后插」（并发下必然漏），
 *       {@link DuplicateKeyException} 直接转 {@code STATE_CONFLICT}；</li>
 *   <li><b>匿名即遮蔽</b>：匿名评价对外连首字都不给，统一「匿名用户」；</li>
 *   <li><b>摘要算在 SQL</b>：好评率/均分由聚合查询给出，列表只负责脱敏映射。</li>
 * </ul>
 */
@Service
public class ReviewServiceImpl implements ReviewService {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_IMAGES = 3;
    private static final int MAX_FILE_ID_LENGTH = 512;
    /** rating ≥ 4 视为好评（与主流电商口径一致）。 */
    private static final int POSITIVE_RATING = 4;
    private static final String ANONYMOUS_NICKNAME = "匿名用户";

    private final ReviewMapper reviewMapper;
    private final OrderQueryService orderQueryService;

    public ReviewServiceImpl(ReviewMapper reviewMapper, OrderQueryService orderQueryService) {
        this.reviewMapper = reviewMapper;
        this.orderQueryService = orderQueryService;
    }

    @Override
    public ReviewDtos.ReviewVO submit(long customerId, ReviewDtos.CreateReviewCmd cmd) {
        if (customerId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        long productId = requireProductId(cmd == null ? null : cmd.productId());
        Long skuId = cmd.skuId();
        if (skuId != null && skuId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规格ID不合法");
        }
        int rating = requireRating(cmd.rating());
        String content = requireContent(cmd.content());
        List<String> images = requireImages(cmd.images());
        boolean anonymous = Boolean.TRUE.equals(cmd.anonymous());

        // 资格：必须是「含此商品的已完成订单」的客户；没有资格就不查重、不写库
        Optional<String> orderNo = orderQueryService.findCompletedOrderNoForReview(customerId, productId, skuId);
        if (orderNo.isEmpty() && skuId != null) {
            // 客户买的是同商品的另一个规格（详情页默认选中/手动切规格都会发生）：
            // 资格按商品兜底，否则「我明明买过，却因为点了另一个规格被拒」。
            orderNo = orderQueryService.findCompletedOrderNoForReview(customerId, productId, null);
        }
        if (orderNo.isEmpty() || orderNo.get().isBlank()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅购买过且订单已完成的客户可以评价");
        }

        ProductReviewPO po = new ProductReviewPO();
        po.setProductId(productId);
        po.setSkuId(skuId);
        po.setOrderNo(orderNo.get());
        po.setCustomerId(customerId);
        po.setRating(rating);
        po.setContent(content);
        po.setImagesJson(JsonListCodec.write(images));
        po.setIsAnonymous(anonymous ? 1 : 0);
        po.setCreatedBy(customerId);
        try {
            reviewMapper.insert(po);
        } catch (DuplicateKeyException e) {
            // uk_review_order_product：同一订单同一商品只能评一次
            throw new BizException(ErrorCode.STATE_CONFLICT, "该订单商品已评价过，请勿重复提交");
        }

        return new ReviewDtos.ReviewVO(
                po.getId() == null ? "" : String.valueOf(po.getId()),
                anonymous ? ANONYMOUS_NICKNAME : "我",
                anonymous ? "匿" : "我",
                rating, content, images, anonymous, toMillis(LocalDateTime.now()));
    }

    @Override
    public ReviewDtos.ReviewPageVO list(long productId, PageQuery pageQuery) {
        if (productId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品ID不合法");
        }
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        Page<ReviewRow> page = new Page<>(query.getPage(), query.getSize());
        IPage<ReviewRow> rows = reviewMapper.selectByProductPage(page, productId);
        List<ReviewDtos.ReviewVO> list = rows == null || rows.getRecords() == null
                ? List.of()
                : rows.getRecords().stream().map(this::toVo).toList();
        long total = rows == null ? 0L : rows.getTotal();
        return new ReviewDtos.ReviewPageVO(
                summary(productId), list, total, query.getPage(), query.getSize(),
                pageCount(total, query.getSize()));
    }

    @Override
    public ReviewDtos.ReviewEligibilityVO eligibility(long customerId, long productId) {
        // 未登录 / 非法 id 不抛 401：详情页在匿名状态下也要能完整渲染
        if (customerId <= 0 || productId <= 0) {
            return new ReviewDtos.ReviewEligibilityVO(false, false);
        }
        Optional<String> orderNo = orderQueryService.findCompletedOrderNoForReview(customerId, productId, null);
        if (orderNo.isEmpty() || orderNo.get().isBlank()) {
            return new ReviewDtos.ReviewEligibilityVO(false, false);
        }
        boolean reviewed = reviewMapper.countByOrderAndProduct(orderNo.get(), productId) > 0;
        return new ReviewDtos.ReviewEligibilityVO(!reviewed, reviewed);
    }

    private ReviewDtos.ReviewSummaryVO summary(long productId) {
        ReviewSummaryRow row = reviewMapper.selectSummary(productId);
        long total = row == null || row.getTotal() == null ? 0L : row.getTotal();
        long positive = row == null || row.getPositiveCount() == null ? 0L : row.getPositiveCount();
        int rate = total <= 0 ? 0 : (int) Math.round(positive * 100.0 / total);
        BigDecimal avg = row == null || row.getAverageRating() == null
                ? BigDecimal.ZERO : row.getAverageRating();
        return new ReviewDtos.ReviewSummaryVO(total, positive, rate,
                avg.setScale(1, RoundingMode.HALF_UP).toPlainString());
    }

    private ReviewDtos.ReviewVO toVo(ReviewRow row) {
        boolean anonymous = row.getIsAnonymous() != null && row.getIsAnonymous() == 1;
        // 匿名：连首字都不给，否则「张**」本身就泄露了姓氏
        String nickname = anonymous ? ANONYMOUS_NICKNAME : maskNickname(row.getNickname());
        String avatarText = nickname.substring(0, 1);
        return new ReviewDtos.ReviewVO(
                row.getId() == null ? "" : String.valueOf(row.getId()),
                nickname, avatarText,
                row.getRating() == null ? 0 : row.getRating(),
                row.getContent(),
                JsonListCodec.read(row.getImagesJson(), String.class),
                anonymous,
                toMillis(row.getCreatedAt()));
    }

    /** 昵称脱敏：保留首字，其余打码；空昵称退化为匿名。 */
    private String maskNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return ANONYMOUS_NICKNAME;
        }
        return nickname.trim().substring(0, 1) + "**";
    }

    private long requireProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品ID不合法");
        }
        return productId;
    }

    private int requireRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择 1~5 星评分");
        }
        return rating;
    }

    private String requireContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写评价内容");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价内容不超过 " + MAX_CONTENT_LENGTH + " 字");
        }
        return text;
    }

    /**
     * 图片 fileId 白名单校验：非空、≤3 张、长度受限、不含 {@code ..}。
     * <p>fileId 会被拼进 {@code /api/files/{fileId}} 读取，脏值直接落库等于把路径穿越的入口存进了数据。</p>
     */
    private List<String> requireImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        if (images.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "最多上传 " + MAX_IMAGES + " 张图片");
        }
        return images.stream().map(raw -> {
            String id = raw == null ? "" : raw.trim();
            if (id.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "图片地址不能为空");
            }
            if (id.length() > MAX_FILE_ID_LENGTH || id.contains("..")) {
                throw new BizException(ErrorCode.PARAM_INVALID, "图片地址不合法");
            }
            return id;
        }).toList();
    }

    private long pageCount(long total, int size) {
        return size <= 0 ? 0L : (long) Math.ceil((double) total / (double) size);
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
