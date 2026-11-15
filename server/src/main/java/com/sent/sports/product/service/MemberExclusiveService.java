package com.sent.sports.product.service;

import java.util.List;

/**
 * 会员专属商品读面（2026-11-16，跨模块可用）。
 *
 * <p>会员中心的「会员专属商品」此前恒返回空列表（桩实现），页面永远显示「暂无专属商品」。
 * 实际业务里「专属」有两个来源，本服务把它们合成一张列表：</p>
 * <ul>
 *   <li><b>一客一价</b>（{@code customer_sku_price}）：给特定客户谈的专属价，最硬的「专属」；</li>
 *   <li><b>等级门槛</b>（{@code product.min_visible_level}）：达到该等级的会员才能享受。</li>
 * </ul>
 * <p>等级高低由调用方（member 模块的 {@code MemberTier}）算好后以 {@code levelRank}
 * 传入，商品模块不重复维护一份等级门槛表，避免两处口径漂移。</p>
 */
public interface MemberExclusiveService {

    /**
     * 该客户的专属商品（一客一价优先，其次等级专属；按商品去重）。
     *
     * @param customerId 客户主键
     * @param levelRank  等级序（NORMAL=0 / LV1=1 / LV2=2 / LV3=3），由 member 模块给出
     */
    List<ExclusiveProduct> listFor(long customerId, int levelRank);

    /**
     * 专属商品行。
     *
     * @param priceCent 一客一价取专属价；等级专属取最低在售 SKU 零售价
     * @param source    AGREEMENT（一客一价）/ LEVEL（等级专属），前端据此打标
     */
    record ExclusiveProduct(long productId, String productName, String mainImage,
                            long priceCent, String source) {
    }
}
