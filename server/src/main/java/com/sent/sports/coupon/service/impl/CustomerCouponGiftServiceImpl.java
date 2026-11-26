package com.sent.sports.coupon.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.dto.CouponDtos;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.CouponIssueUseCase;
import com.sent.sports.coupon.service.CustomerCouponGiftService;
import com.sent.sports.coupon.service.dto.IssueCouponCmd;
import com.sent.sports.customer.service.CustomerQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

/**
 * 客服发券实现（V64）。
 *
 * <p>四条规则，每条都对应一次真实的误操作风险：</p>
 * <ol>
 *   <li><b>面额 ≤ 20 元</b>：客服每天要处理几十个会话，若能随手发 100 元券，
 *       一周就能把毛利发穿。上限判定放在服务端，前端过滤只是顺手的体验；</li>
 *   <li><b>只发当前客户一张</b>：{@link IssueCouponCmd} 允许空 customerIds = 全员发放，
 *       这里强制传单人清单，杜绝「点错一下全量发券」；</li>
 *   <li><b>scene=AFTER_SALE</b>：站内信文案随之变成「平台补充：…，原因：…」，
 *       客户看到的是补偿而不是营销；发放记录也能按来源区分复盘；</li>
 *   <li><b>客户必须存在</b>：拿一个已注销的客户号发券，券会烂在库里，谁也查不到。</li>
 * </ol>
 */
@Service
public class CustomerCouponGiftServiceImpl implements CustomerCouponGiftService {

    /** 客服单张券面额上限：20 元 = 2000 分。改这个数等于改公司授权额度，别随手调。 */
    private static final long CAP_CENT = 2000L;

    /** 记录列表上限：客服只是想知道「他最近拿过什么」，不是来做对账的。 */
    private static final int MAX_RECORDS = 50;

    private static final String SCENE_AFTER_SALE = "AFTER_SALE";
    /**
     * 客服补偿券专用 scene（V65）：只有这 5 档无门槛券能从这里发。
     * 与 ADMIN（运营自建）/REGISTER（注册礼）分开，防止运营券混进客服下拉。
     */
    private static final String SCENE_CS_GIFT = "CS_GIFT";
    private static final String DEFAULT_REASON = "客服发放";

    private final CouponTemplateMapper templateMapper;
    private final CouponMapper couponMapper;
    private final CouponIssueUseCase issueUseCase;
    private final CustomerQueryService customerQueryService;

    public CustomerCouponGiftServiceImpl(CouponTemplateMapper templateMapper,
                                         CouponMapper couponMapper,
                                         CouponIssueUseCase issueUseCase,
                                         CustomerQueryService customerQueryService) {
        this.templateMapper = templateMapper;
        this.couponMapper = couponMapper;
        this.issueUseCase = issueUseCase;
        this.customerQueryService = customerQueryService;
    }

    @Override
    public List<CouponDtos.TemplateVO> giftOptions() {
        // 2026-11-26 用户要求：客服工作台只放 5 档无门槛券（5/8/10/15/20 元）。
        // scene=CS_GIFT 由 V65 种入；面额与门槛再兜一层，脏数据也不会漏进下拉。
        return templateMapper.selectTemplates(null, SCENE_CS_GIFT).stream()
                .filter(po -> po.getAmountCent() <= CAP_CENT && po.getThresholdCent() == 0L)
                .sorted(java.util.Comparator.comparingLong(CouponTemplatePO::getAmountCent))
                .map(po -> new CouponDtos.TemplateVO(po.getId(), po.getName(), po.getAmountCent(),
                        po.getThresholdCent(), po.getValidDays(), po.getCouponType(),
                        po.getDiscountType(), po.getDiscountRate(), po.getScopeType(),
                        po.getScopeIds(), po.getCustomerScope()))
                .toList();
    }

    @Override
    public List<CouponDtos.GiftRecordVO> recordsOf(long customerId, int limit) {
        if (customerId <= 0L) {
            return List.of();
        }
        int safe = Math.max(1, Math.min(limit, MAX_RECORDS));
        return couponMapper.selectGiftRecords(customerId, safe).stream()
                .map(po -> new CouponDtos.GiftRecordVO(po.getId(), po.getCouponNo(), po.getName(),
                        po.getAmountCent(), po.getThresholdCent(), po.getStatus(), po.getSource(),
                        po.getReason(), toMillis(po.getExpireAt()), toMillis(po.getCreatedAt())))
                .toList();
    }

    @Override
    @Transactional
    public CouponDtos.GiftResultVO gift(long customerId, long templateId, String reason, Long operatorId) {
        CouponTemplatePO template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "券模板不存在或已停用");
        }
        // 运营券即使面额很小也不能从客服通道发：权限面与用途都不同（V65）。
        if (!SCENE_CS_GIFT.equals(template.getScene())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客服只能发放「客服补偿券」");
        }
        if (template.getAmountCent() > CAP_CENT) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "客服发券单张最高 20 元，该券面额 " + yuan(template.getAmountCent())
                            + " 元，请换一张或走运营活动发放");
        }
        if (template.getThresholdCent() > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客服补偿券必须无门槛");
        }
        if (customerQueryService.typeOf(customerId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "客户不存在");
        }
        String text = reason == null || reason.isBlank() ? DEFAULT_REASON : reason.trim();
        int issued = issueUseCase.issue(new IssueCouponCmd(templateId, List.of(customerId),
                null, text, SCENE_AFTER_SALE));
        return new CouponDtos.GiftResultVO(issued, template.getName(), template.getAmountCent());
    }

    /** 分转元（仅用于报错文案，保留一位小数足够说明问题）。 */
    private static String yuan(long cent) {
        return String.format("%.1f", cent / 100.0);
    }

    private static long toMillis(java.time.LocalDateTime time) {
        return time == null ? 0L
                : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
