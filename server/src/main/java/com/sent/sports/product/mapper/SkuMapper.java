package com.sent.sports.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.product.mapper.po.SkuPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * SKU Mapper：既供演示数据生成器批量读取可用 SKU，也承接商品编辑页的写侧操作。
 * <p>从商品编辑页移除某行采用 {@code status='OFF'} 软下线，保留历史订单行与库存台账可追溯；
 * 公开目录读侧过滤 OFF。</p>
 */
@Mapper
public interface SkuMapper extends BaseMapper<SkuPO> {

    /** 按 sku_no 读取，用于唯一编码校验；含已下线 SKU，避免编码复用冲突。 */
    @Select("SELECT * FROM sku WHERE sku_no = #{skuNo} LIMIT 1")
    SkuPO selectBySkuNo(@Param("skuNo") String skuNo);

    /** 从商品编辑页移除某行 / 删除商品时，软下线该 SKU。 */
    @Update("UPDATE sku SET status = 'OFF' WHERE id = #{skuId}")
    int markOff(@Param("skuId") long skuId);

    /** 下线 SKU 时清理其阶梯价，避免目录读侧仍读到孤儿折扣。 */
    @Delete("DELETE FROM ladder_price WHERE sku_id = #{skuId}")
    int deleteLadderPrices(@Param("skuId") long skuId);

    /** 下线 SKU 时清理其一客一价，避免后续重新启用编码时串价。 */
    @Delete("DELETE FROM customer_sku_price WHERE sku_id = #{skuId}")
    int deleteCustomerPrices(@Param("skuId") long skuId);
}
