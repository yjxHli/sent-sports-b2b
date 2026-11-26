package com.sent.sports.coupon.service;

import com.sent.sports.coupon.dto.CouponDtos;

import java.util.List;

/**
 * 客服发券服务（V64）：客服工作台「优惠券发放」tab 的唯一入口。
 *
 * <p>为什么独立于 {@link CouponIssueUseCase}：那个用例是<b>运营发券</b>——可以全量发、
 * 可以发任意面额。客服面对的是「这个客户正在生气」，需要的是小额、单人、有理由的补偿券。
 * 把两件事塞进一个用例，迟早会有人在客服侧误触「全量发放」。</p>
 *
 * <p>20 元上限在这里<b>硬判定</b>，不依赖前端下拉过滤：接口是公开可调的，
 * 前端隐藏只是体验，服务端拒绝才是权限。</p>
 */
public interface CustomerCouponGiftService {

    /** 客服可发放的券档位（面额 ≤ 20 元，已排除停用与软删）。 */
    List<CouponDtos.TemplateVO> giftOptions();

    /** 某客户最近的发券记录（含平台活动发的：客服要能看出「他手上已经有一堆券了」）。 */
    List<CouponDtos.GiftRecordVO> recordsOf(long customerId, int limit);

    /**
     * 给单个客户发一张券（并发站内信）。
     *
     * @param reason 发放原因；留空按「客服发放」兜底，不允许空串写进站内信
     * @return 实际发放张数 + 券名与面额（前端据此提示「已发放 20 元补偿券」）
     */
    CouponDtos.GiftResultVO gift(long customerId, long templateId, String reason, Long operatorId);
}
