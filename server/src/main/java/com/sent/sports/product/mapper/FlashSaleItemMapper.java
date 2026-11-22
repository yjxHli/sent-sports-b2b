package com.sent.sports.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.product.mapper.po.FlashSaleItemPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀活动明细 Mapper（V54）。
 * <p>单独成一个 {@code BaseMapper}：活动主表与明细是两个实体，
 * 合在同一个 Mapper 里会让 {@code insert} 只认活动 PO。</p>
 */
@Mapper
public interface FlashSaleItemMapper extends BaseMapper<FlashSaleItemPO> {
}
