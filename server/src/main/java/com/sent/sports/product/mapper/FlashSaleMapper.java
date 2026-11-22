package com.sent.sports.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.product.mapper.po.FlashSaleActivityPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动 Mapper（V54）。活动的增删改走 {@link BaseMapper}，明细与冲突校验走显式 SQL。
 */
@Mapper
public interface FlashSaleMapper extends BaseMapper<FlashSaleActivityPO> {

    /**
     * 活动明细（带商品名/规格/零售价/底价）。
     * <p>联表列一律带表前缀：{@code id} 在三张表里都有，不限定会撞名报 50001。</p>
     */
    @Select("SELECT i.id AS itemId, i.sku_id AS skuId, i.flash_price_cent AS flashPriceCent, "
            + "s.sku_no AS skuCode, s.spec_json AS specJson, s.retail_price_cent AS retailPriceCent, "
            + "s.floor_price_cent AS floorPriceCent, s.status AS skuStatus, p.title AS productName "
            + "FROM flash_sale_item i "
            + "JOIN sku s ON s.id = i.sku_id "
            + "JOIN product p ON p.id = s.spu_id "
            + "WHERE i.activity_id = #{activityId} "
            + "ORDER BY i.sort, i.id")
    List<ItemRow> selectItems(@Param("activityId") long activityId);

    /** 批量读明细（列表页算 itemCount，避免 N+1）。 */
    @Select("<script>"
            + "SELECT activity_id AS activityId, COUNT(*) AS itemCount FROM flash_sale_item "
            + "WHERE activity_id IN "
            + "<foreach collection='activityIds' item='aid' open='(' separator=',' close=')'>#{aid}</foreach>"
            + " GROUP BY activity_id"
            + "</script>")
    List<ItemCountRow> countItems(@Param("activityIds") List<Long> activityIds);

    /**
     * 时间窗冲突检测：同一 SKU 不得同时参与两个已发布且时间重叠的活动。
     * <p>区间重叠用 {@code start &lt; otherEnd AND end &gt; otherStart}；草稿与已下线活动不参与冲突判断。</p>
     */
    @Select("SELECT COUNT(*) FROM flash_sale_item i "
            + "JOIN flash_sale_activity a ON a.id = i.activity_id "
            + "WHERE i.sku_id = #{skuId} AND a.deleted = 0 AND a.status = 'ACTIVE' "
            + "AND a.id <> #{excludeActivityId} "
            + "AND a.start_at < #{endAt} AND a.end_at > #{startAt}")
    int countOverlap(@Param("skuId") long skuId,
                     @Param("excludeActivityId") long excludeActivityId,
                     @Param("startAt") LocalDateTime startAt,
                     @Param("endAt") LocalDateTime endAt);

    @Delete("DELETE FROM flash_sale_item WHERE activity_id = #{activityId}")
    int deleteItems(@Param("activityId") long activityId);

    /** 活动明细行。 */
    class ItemRow {
        private Long itemId;
        private Long skuId;
        private Long flashPriceCent;
        private String skuCode;
        private String specJson;
        private Long retailPriceCent;
        private Long floorPriceCent;
        private String skuStatus;
        private String productName;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Long getFlashPriceCent() { return flashPriceCent; }
        public void setFlashPriceCent(Long flashPriceCent) { this.flashPriceCent = flashPriceCent; }
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public String getSpecJson() { return specJson; }
        public void setSpecJson(String specJson) { this.specJson = specJson; }
        public Long getRetailPriceCent() { return retailPriceCent; }
        public void setRetailPriceCent(Long retailPriceCent) { this.retailPriceCent = retailPriceCent; }
        public Long getFloorPriceCent() { return floorPriceCent; }
        public void setFloorPriceCent(Long floorPriceCent) { this.floorPriceCent = floorPriceCent; }
        public String getSkuStatus() { return skuStatus; }
        public void setSkuStatus(String skuStatus) { this.skuStatus = skuStatus; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
    }

    /** 活动 → 明细数。 */
    class ItemCountRow {
        private Long activityId;
        private Integer itemCount;

        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Integer getItemCount() { return itemCount; }
        public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    }
}
