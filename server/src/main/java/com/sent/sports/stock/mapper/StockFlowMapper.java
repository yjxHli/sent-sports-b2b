package com.sent.sports.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.stock.mapper.po.StockFlowPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 库存流水 Mapper（P5-12 配套）：流水只增不改（审计要求），故不提供 update 语义。
 * <p>{@code uk_flow_biz(biz_type, biz_no, sku_id)} 是防重的落点：重复插入抛
 * {@code DuplicateKeyException}，调用方据此判定「这笔业务已经记过账」并跳过，无需分布式锁。
 */
@Mapper
public interface StockFlowMapper extends BaseMapper<StockFlowPO> {

    /** 某 SKU 的流水（时间序）。 */
    @Select("SELECT * FROM stock_flow WHERE sku_id = #{skuId} ORDER BY id")
    List<StockFlowPO> selectBySkuId(@Param("skuId") long skuId);

    /** 某业务单据的全部流水（按单据对账）。 */
    @Select("SELECT * FROM stock_flow WHERE biz_no = #{bizNo} ORDER BY id")
    List<StockFlowPO> selectByBizNo(@Param("bizNo") String bizNo);

    /** 某 SKU 的流水变动合计，用于校验台账与流水是否一致（可重算性）。 */
    @Select("SELECT COALESCE(SUM(change_qty), 0) FROM stock_flow WHERE sku_id = #{skuId}")
    long sumChangeQty(@Param("skuId") long skuId);

    /** 管理端流水分页：按 SKU 与业务类型收窄，最新的在前。 */
    @Select("<script>"
            + "SELECT * FROM stock_flow WHERE 1 = 1 "
            + "<if test=\"skuId != null\"> AND sku_id = #{skuId}</if> "
            + "<if test=\"bizType != null and bizType != ''\"> AND biz_type = #{bizType}</if> "
            + "ORDER BY id DESC"
            + "</script>")
    IPage<StockFlowPO> selectFlowPage(Page<StockFlowPO> page,
                                     @Param("skuId") Long skuId,
                                     @Param("bizType") String bizType);
}
