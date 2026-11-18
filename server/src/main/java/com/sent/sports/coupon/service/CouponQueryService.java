package com.sent.sports.coupon.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.coupon.dto.CouponDtos;

import java.util.List;

/** 用户优惠券查询（商城端「我的券」/「可用券」/「领券中心」）。 */
public interface CouponQueryService {

    List<CouponDtos.CouponVO> listMine(long customerId);

    /** 按订单商品金额筛出可用券；goodsCent 为商品额+定制费，不含运费。 */
    List<CouponDtos.CouponVO> listUsable(long customerId, long goodsCent);

    /**
     * 带购物车 SKU 的可用券：品类券只有命中范围内商品时才算可用，
     * 否则用户选了券、订单却抵扣 0。skuIds 为空时退化为仅按金额判断。
     */
    List<CouponDtos.CouponVO> listUsable(long customerId, long goodsCent, List<Long> skuIds);

    /** 管理端可发放的券档位（模板）。 */
    List<CouponDtos.TemplateVO> listTemplates();

    /** 管理端最近发放记录。 */
    List<CouponDtos.IssueVO> listRecentIssues(int limit);

    /**
     * 领券中心可领券。个人客户看不到企业专属券（B2B 大额阶梯券个人不可领）；
     * claimed 标记当前客户是否已领过。
     */
    List<CouponDtos.ClaimableVO> listClaimable(long customerId);

    /** 管理端券模板分页列表（券号 / 创建人 / 创建时间，软删的不出现）。 */
    PageResult<CouponDtos.TemplatePageVO> listTemplatePage(PageQuery pageQuery, String couponType,
                                                           String keyword);

    /** 管理端发放记录分页列表（软删的不出现）。 */
    PageResult<CouponDtos.IssuePageVO> listIssuePage(PageQuery pageQuery, String source, String keyword);
}
