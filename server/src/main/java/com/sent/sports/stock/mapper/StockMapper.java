package com.sent.sports.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.stock.mapper.po.LockReleaseLinePO;
import com.sent.sports.stock.mapper.po.SkuStockPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 库存 Mapper（P5-12）：核心是「条件更新」实现的原子预占，而非「先查后改」。
 * <p>并发语义：{@link #lockStock} 把「可用量是否足够」交给数据库在行锁内判定，
 * 返回 0 行即代表可用不足，调用方（P5-20 仓储实现）据此抛 {@code STOCK_NOT_ENOUGH(40910)}，
 * 由 {@code @UseCase} 事务整体回滚——20 线程抢 1 件时只会有 1 个 UPDATE 命中（P5-49 验证）。
 * <p>本 Mapper 只管 sku_stock 一张表；「按订单号释放」需要订单行信息，由仓储实现经 order 模块 service 取数后逐 SKU 调用 {@link #releaseStock}，
 * 避免 stock 直接读写 order 的表。
 */
@Mapper
public interface StockMapper extends BaseMapper<SkuStockPO> {

    /** 初始化某 SKU 的库存行（存在则忽略，保证幂等）。 */
    @Insert("INSERT INTO sku_stock (sku_id, location_code, physical_qty, locked_qty, pending_qty, frozen_qty, safety_qty, version) "
            + "VALUES (#{skuId}, 'DEFAULT', 0, 0, 0, 0, #{safetyQty}, 0) "
            + "ON DUPLICATE KEY UPDATE sku_id = sku_id")
    int insertIfAbsent(@Param("skuId") long skuId, @Param("safetyQty") int safetyQty);

    /**
     * 原子预占：可用量足够才扣减（可用 = physical - locked - pending - frozen）。
     *
     * @return 1 表示预占成功；0 表示可用不足或 SKU 不存在
     */
    @Update("UPDATE sku_stock SET locked_qty = locked_qty + #{qty}, version = version + 1 "
            + "WHERE sku_id = #{skuId} "
            + "AND physical_qty - locked_qty - pending_qty - frozen_qty >= #{qty}")
    int lockStock(@Param("skuId") long skuId, @Param("qty") int qty);

    /** 原子释放预占（幂等下限保护，避免脏数据把 locked 写成负数）。 */
    @Update("UPDATE sku_stock SET locked_qty = GREATEST(locked_qty - #{qty}, 0), version = version + 1 "
            + "WHERE sku_id = #{skuId}")
    int releaseStock(@Param("skuId") long skuId, @Param("qty") int qty);

    /** 出库：预占转为实扣（physical 与 locked 同步减），条件为预占量足够。 */
    @Update("UPDATE sku_stock SET physical_qty = physical_qty - #{qty}, locked_qty = locked_qty - #{qty}, "
            + "version = version + 1 WHERE sku_id = #{skuId} AND locked_qty >= #{qty} AND physical_qty >= #{qty}")
    int shipStock(@Param("skuId") long skuId, @Param("qty") int qty);

    /** 实盘调整：physical 增减（入库 / 报损 / 盘点差异统一走此入口），不得把 physical 调成负数。 */
    @Update("UPDATE sku_stock SET physical_qty = physical_qty + #{delta}, version = version + 1 "
            + "WHERE sku_id = #{skuId} AND physical_qty + #{delta} >= 0")
    int adjustPhysical(@Param("skuId") long skuId, @Param("delta") int delta);

    /** 读取某 SKU 台账。 */
    @Select("SELECT * FROM sku_stock WHERE sku_id = #{skuId}")
    SkuStockPO selectBySkuId(@Param("skuId") long skuId);

    /** 以绝对值覆盖台账（仓储 save 使用，字段与领域对象一一对应）。 */
    @Update("UPDATE sku_stock SET physical_qty = #{physicalQty}, locked_qty = #{lockedQty}, "
            + "pending_qty = #{pendingQty}, frozen_qty = #{frozenQty}, version = version + 1 "
            + "WHERE sku_id = #{skuId}")
    int updateLedger(@Param("skuId") long skuId,
                     @Param("physicalQty") long physicalQty,
                     @Param("lockedQty") long lockedQty,
                     @Param("pendingQty") long pendingQty,
                     @Param("frozenQty") long frozenQty);

    /**
     * 某订单的预占明细（SKU + 数量），用于整单释放预占。
     * <p>说明：这里只<b>读</b> order_item 快照，不写订单表；替代方案「经 order 模块 service 取数」会形成
     * order→stock→order 的 Bean 循环依赖，故选择只读快照，边界权衡记录于实施说明。
     */
    @Select("SELECT i.sku_id AS sku_id, i.qty AS qty FROM order_item i "
            + "JOIN orders o ON o.id = i.order_id WHERE o.order_no = #{orderNo} ORDER BY i.id")
    List<LockReleaseLinePO> selectLockedLinesByOrderNo(@Param("orderNo") String orderNo);

    /** 加行锁读取（核销/盘点等需要「读-改-写」串行的场景）。 */
    @Select("SELECT * FROM sku_stock WHERE sku_id = #{skuId} FOR UPDATE")
    SkuStockPO selectBySkuIdForUpdate(@Param("skuId") long skuId);

    /** 低于安全库存的 SKU 清单（P5-44 库存预警任务）。 */
    @Select("SELECT * FROM sku_stock "
            + "WHERE safety_qty > 0 AND physical_qty - locked_qty - pending_qty - frozen_qty < safety_qty "
            + "ORDER BY sku_id")
    List<SkuStockPO> selectBelowSafety();

    /**
     * 管理端库存台账分页：联查 SKU / SPU 带出商品名与规格，可按 SKU ID 精确筛选。
     * 一个 SKU 一行台账（sku_id 主键），可用量由服务层按四口径计算。
     */
    @Select("<script>"
            + "SELECT st.sku_id, st.physical_qty, st.locked_qty, st.pending_qty, st.frozen_qty, "
            + "st.safety_qty, s.sku_no, s.spec_json, p.title AS product_title "
            + "FROM sku_stock st "
            + "LEFT JOIN sku s ON s.id = st.sku_id "
            + "LEFT JOIN product p ON p.id = s.spu_id "
            + "WHERE 1 = 1 "
            + "<if test=\"skuId != null\"> AND st.sku_id = #{skuId}</if> "
            + "ORDER BY st.sku_id"
            + "</script>")
    IPage<StockLedgerRow> selectStockPage(Page<StockLedgerRow> page, @Param("skuId") Long skuId);

    /** 库存台账联查行。 */
    class StockLedgerRow {
        private Long skuId;
        private Integer physicalQty;
        private Integer lockedQty;
        private Integer pendingQty;
        private Integer frozenQty;
        private Integer safetyQty;
        private String skuNo;
        private String specJson;
        private String productTitle;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getPhysicalQty() { return physicalQty; }
        public void setPhysicalQty(Integer physicalQty) { this.physicalQty = physicalQty; }
        public Integer getLockedQty() { return lockedQty; }
        public void setLockedQty(Integer lockedQty) { this.lockedQty = lockedQty; }
        public Integer getPendingQty() { return pendingQty; }
        public void setPendingQty(Integer pendingQty) { this.pendingQty = pendingQty; }
        public Integer getFrozenQty() { return frozenQty; }
        public void setFrozenQty(Integer frozenQty) { this.frozenQty = frozenQty; }
        public Integer getSafetyQty() { return safetyQty; }
        public void setSafetyQty(Integer safetyQty) { this.safetyQty = safetyQty; }
        public String getSkuNo() { return skuNo; }
        public void setSkuNo(String skuNo) { this.skuNo = skuNo; }
        public String getSpecJson() { return specJson; }
        public void setSpecJson(String specJson) { this.specJson = specJson; }
        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
    }
}
