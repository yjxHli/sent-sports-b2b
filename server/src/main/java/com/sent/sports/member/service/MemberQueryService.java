package com.sent.sports.member.service;

import com.sent.sports.member.dto.MemberDtos;

import java.util.List;

/**
 * 会员查询门面（P4-26 接口契约，跨模块可用）。
 * <p>会员/积分实体与仓储尚未建设与读面（P5 DB 范畴），当前由
 * {@code MemberQueryServiceImpl} 桩实现占位；Phase 5 接入实体、取价与数据范围（@DataScope）后替换。
 * 契约（接口签名）保持稳定。</p>
 */
public interface MemberQueryService {

    /** 等级进度：当前等级、成长值与下一档门槛、进度比例。 */
    MemberDtos.LevelVO levelProgress(Long customerId);

    /** 积分明细：可用积分。 */
    MemberDtos.PointVO pointDetail(Long customerId);

    /** 额度与应收：信用额度与当前应收（分 + 元）。 */
    MemberDtos.CreditVO credit(Long customerId);

    /** 会员专属商品列表。 */
    List<MemberDtos.ExclusiveProductVO> exclusiveProducts(Long customerId);
}
