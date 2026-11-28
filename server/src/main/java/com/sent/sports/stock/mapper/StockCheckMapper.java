package com.sent.sports.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.stock.mapper.po.StockCheckItemPO;
import com.sent.sports.stock.mapper.po.StockCheckPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 盘点单 Mapper（V5 的两张表，2026-11-28 起由真库仓储使用）。
 *
 * <p>为什么行表用 {@code ON DUPLICATE KEY UPDATE} 而不是「先查再写」：仓储的 save 语义是
 * 「落一份当前快照」，同一张盘点单会被 save 三次（创建 / 录实盘 / 审批），
 * 靠 {@code uk_check_sku} 把幂等交给数据库，和项目里 {@code stock_flow} 的防重思路一致。
 */
@Mapper
public interface StockCheckMapper extends BaseMapper<StockCheckPO> {

    @Select("SELECT id, check_no, status, operator_id, approved_by, created_at, updated_at, created_by "
            + "FROM stock_check WHERE check_no = #{checkNo}")
    StockCheckPO selectByCheckNo(@Param("checkNo") String checkNo);

    @Select("SELECT id, check_id, sku_id, book_qty, actual_qty, diff_qty, created_at, updated_at, created_by "
            + "FROM stock_check_item WHERE check_id = #{checkId} LIMIT 1")
    StockCheckItemPO selectItemByCheckId(@Param("checkId") Long checkId);

    @Insert("INSERT INTO stock_check_item (check_id, sku_id, book_qty, actual_qty, diff_qty) "
            + "VALUES (#{checkId}, #{skuId}, #{bookQty}, #{actualQty}, #{diffQty}) "
            + "ON DUPLICATE KEY UPDATE book_qty = VALUES(book_qty), "
            + "actual_qty = VALUES(actual_qty), diff_qty = VALUES(diff_qty)")
    int upsertItem(@Param("checkId") Long checkId,
                   @Param("skuId") Long skuId,
                   @Param("bookQty") Integer bookQty,
                   @Param("actualQty") Integer actualQty,
                   @Param("diffQty") Integer diffQty);
}
