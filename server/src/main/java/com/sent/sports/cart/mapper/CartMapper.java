package com.sent.sports.cart.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 购物车持久层（任务 2）：真库读写 {@code cart_item}，行名与价格快照联查 SPU/SKU。
 *
 * <p>加购依赖唯一键 {@code uk_cart_item(customer_id, sku_id, customize_flag)} 做合并，
 * 不用分布式锁；所有写操作都带 {@code customer_id} 条件，避免越权改动他人购物车。</p>
 */
@Mapper
public interface CartMapper {

    @Select("SELECT ci.id, ci.sku_id, ci.qty, ci.selected, ci.price_snapshot_cent, "
            + "COALESCE(p.title, s.sku_no, CONCAT('SKU ', ci.sku_id)) AS sku_name "
            + "FROM cart_item ci "
            + "LEFT JOIN sku s ON s.id = ci.sku_id "
            + "LEFT JOIN product p ON p.id = s.spu_id "
            + "WHERE ci.customer_id = #{customerId} ORDER BY ci.id DESC")
    List<CartRow> selectByCustomer(@Param("customerId") long customerId);

    @Select("SELECT retail_price_cent FROM sku WHERE id = #{skuId}")
    Long selectRetailPrice(@Param("skuId") long skuId);

    @Select("SELECT COALESCE(p.title, s.sku_no, CONCAT('SKU ', #{skuId})) "
            + "FROM sku s LEFT JOIN product p ON p.id = s.spu_id WHERE s.id = #{skuId}")
    String selectSkuName(@Param("skuId") long skuId);

    @Select("SELECT id FROM cart_item "
            + "WHERE customer_id = #{customerId} AND sku_id = #{skuId} AND customize_flag = 0")
    Long selectItemId(@Param("customerId") long customerId, @Param("skuId") long skuId);

    @Insert("INSERT INTO cart_item (customer_id, sku_id, qty, customize_flag, selected, price_snapshot_cent) "
            + "VALUES (#{customerId}, #{skuId}, #{qty}, 0, 1, #{priceCent}) "
            + "ON DUPLICATE KEY UPDATE qty = qty + VALUES(qty), "
            + "price_snapshot_cent = VALUES(price_snapshot_cent)")
    int upsertItem(@Param("customerId") long customerId, @Param("skuId") long skuId,
                   @Param("qty") int qty, @Param("priceCent") long priceCent);

    @Update("UPDATE cart_item SET qty = #{qty}, selected = #{selected} "
            + "WHERE id = #{itemId} AND customer_id = #{customerId}")
    int updateItem(@Param("customerId") long customerId, @Param("itemId") long itemId,
                   @Param("qty") int qty, @Param("selected") boolean selected);

    @Delete("DELETE FROM cart_item WHERE id = #{itemId} AND customer_id = #{customerId}")
    int deleteItem(@Param("customerId") long customerId, @Param("itemId") long itemId);

    /** 购物车行（含联查得到的商品名）。 */
    class CartRow {
        private Long id;
        private Long skuId;
        private Integer qty;
        private Integer selected;
        private Long priceSnapshotCent;
        private String skuName;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
        public Integer getSelected() { return selected; }
        public void setSelected(Integer selected) { this.selected = selected; }
        public Long getPriceSnapshotCent() { return priceSnapshotCent; }
        public void setPriceSnapshotCent(Long priceSnapshotCent) { this.priceSnapshotCent = priceSnapshotCent; }
        public String getSkuName() { return skuName; }
        public void setSkuName(String skuName) { this.skuName = skuName; }
    }
}
