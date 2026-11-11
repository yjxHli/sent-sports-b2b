package com.sent.sports.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.product.mapper.po.AgreementPricePO;
import com.sent.sports.product.mapper.po.LadderPricePO;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.product.mapper.po.SkuPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品聚合 Mapper（P5-11）：SPU 的 CRUD 走 {@link BaseMapper}，另补 SKU / 阶梯价 / 协议价三类定价查询。
 * <p>查询一律带 {@code deleted = 0} 与上下架过滤，避免把草稿或已删商品暴露给前台；
 * 一客一价（{@code customer_sku_price}）由 P5-19 之后的定价仓储在 service 层组合，此处不展开。
 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductPO> {

    /** 按分类与关键字检索已上架 SPU（关键字命中标题，参数为空则该条件不生效）。 */
    @Select("<script>"
            + "SELECT * FROM product WHERE deleted = 0 AND status = 'ON_SHELF'"
            + "<if test=\"categoryId != null\"> AND category_id = #{categoryId}</if>"
            + "<if test=\"keyword != null and keyword != ''\"> AND title LIKE CONCAT('%', #{keyword}, '%')</if>"
            + " ORDER BY published_at DESC, id DESC"
            + "</script>")
    List<ProductPO> searchOnShelf(@Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    /** 分类下的全部后代分类主键（含自身），供列表页按一级分类聚合。 */
    @Select("WITH RECURSIVE cte AS ("
            + "  SELECT id FROM product_category WHERE id = #{categoryId}"
            + "  UNION ALL"
            + "  SELECT c.id FROM product_category c JOIN cte ON c.parent_id = cte.id"
            + ") SELECT id FROM cte")
    List<Long> selectCategoryAndDescendants(@Param("categoryId") long categoryId);

    /** 某 SPU 下的全部 SKU（下架 SKU 也返回，由调用方按 status 过滤展示）。 */
    @Select("SELECT * FROM sku WHERE spu_id = #{spuId} ORDER BY id")
    List<SkuPO> selectSkusBySpu(@Param("spuId") long spuId);

    /** 按 SKU 主键读取（下单校验所需）。 */
    @Select("SELECT * FROM sku WHERE id = #{skuId}")
    SkuPO selectSkuById(@Param("skuId") long skuId);

    /** 批量读取 SKU（购物车结算避免 N+1）。 */
    @Select("<script>"
            + "SELECT * FROM sku WHERE id IN"
            + "<foreach collection=\"skuIds\" item=\"sid\" open=\"(\" separator=\",\" close=\")\">#{sid}</foreach>"
            + " ORDER BY id"
            + "</script>")
    List<SkuPO> selectSkusByIds(@Param("skuIds") List<Long> skuIds);

    /** 某 SKU 的阶梯价，按起订量升序，便于顺序匹配。 */
    @Select("SELECT * FROM ladder_price WHERE sku_id = #{skuId} ORDER BY min_qty")
    List<LadderPricePO> selectLadderPrices(@Param("skuId") long skuId);

    /** 某客户在指定时刻生效的协议价（有效期为空视为不限期；同客户预期唯一）。 */
    @Select("SELECT * FROM agreement_price WHERE customer_id = #{customerId} "
            + "AND (valid_from IS NULL OR valid_from <= NOW(3)) "
            + "AND (valid_to IS NULL OR valid_to >= NOW(3)) "
            + "ORDER BY id DESC LIMIT 1")
    AgreementPricePO selectEffectiveAgreement(@Param("customerId") long customerId);

    /** 上架 / 下架（条件更新，避免无谓写放大）。 */
    @org.apache.ibatis.annotations.Update("UPDATE product SET status = #{status} "
            + "WHERE id = #{productId} AND deleted = 0")
    int updateStatus(@Param("productId") long productId, @Param("status") String status);

    // ------------------------------------------------------------------
    // 折扣写侧（收口阶段）：阶梯价全量替换 + 一客一价 upsert / 删除 / 列表
    // ------------------------------------------------------------------

    /** 清空某 SKU 的全部阶梯价，为「全量替换」做准备。 */
    @Delete("DELETE FROM ladder_price WHERE sku_id = #{skuId}")
    int deleteLadderPrices(@Param("skuId") long skuId);

    /** 批量写回阶梯价；调用方保证集合非空（空集合交给 deleteLadderPrices 表达「清空折扣」）。 */
    @Insert("<script>"
            + "INSERT INTO ladder_price (sku_id, min_qty, max_qty, price_cent, created_at, updated_at) VALUES "
            + "<foreach collection=\"tiers\" item=\"t\" separator=\",\">"
            + "(#{t.skuId}, #{t.minQty}, #{t.maxQty}, #{t.priceCent}, NOW(3), NOW(3))"
            + "</foreach>"
            + "</script>")
    int insertLadderPrices(@Param("tiers") List<LadderPricePO> tiers);

    /** upsert 后回读真实主键，供前端删除 / 编辑使用。 */
    @Select("SELECT id FROM customer_sku_price WHERE customer_id = #{customerId} AND sku_id = #{skuId}")
    Long selectCustomerPriceId(@Param("customerId") long customerId, @Param("skuId") long skuId);

    /** 一客一价按 uk_cus_sku_price 唯一键 upsert；有效期为空表示不限期。 */
    @Insert("INSERT INTO customer_sku_price "
            + "(customer_id, sku_id, price_cent, valid_from, valid_to, created_at, updated_at) "
            + "VALUES (#{customerId}, #{skuId}, #{priceCent}, #{validFrom}, #{validTo}, NOW(3), NOW(3)) "
            + "ON DUPLICATE KEY UPDATE price_cent = VALUES(price_cent), valid_from = VALUES(valid_from), "
            + "valid_to = VALUES(valid_to), updated_at = NOW(3)")
    int upsertCustomerPrice(@Param("customerId") long customerId,
                            @Param("skuId") long skuId,
                            @Param("priceCent") long priceCent,
                            @Param("validFrom") LocalDateTime validFrom,
                            @Param("validTo") LocalDateTime validTo);

    @Delete("DELETE FROM customer_sku_price WHERE id = #{id}")
    int deleteCustomerPrice(@Param("id") long id);

    /**
     * 一客一价列表：联查客户（昵称/手机号）、SKU（编码/规格）、SPU（名称/零售价），
     * 让管理端一次请求就能展示「谁 · 买什么 · 多少钱」，避免前端按 id 逐个回查。
     */
    @Select("<script>"
            + "SELECT csp.id, csp.customer_id AS customerId, csp.sku_id AS skuId, "
            + "csp.price_cent AS priceCent, csp.valid_from AS validFrom, csp.valid_to AS validTo, "
            + "cu.nickname AS customerName, cu.phone AS customerPhone, "
            + "p.id AS productId, p.title AS productName, "
            + "s.sku_no AS skuCode, s.spec_json AS specJson, s.retail_price_cent AS retailPriceCent "
            + "FROM customer_sku_price csp "
            + "LEFT JOIN customer cu ON cu.id = csp.customer_id "
            + "LEFT JOIN sku s ON s.id = csp.sku_id "
            + "LEFT JOIN product p ON p.id = s.spu_id "
            + "WHERE 1 = 1 "
            + "<if test=\"skuId != null\"> AND csp.sku_id = #{skuId}</if> "
            + "<if test=\"customerId != null\"> AND csp.customer_id = #{customerId}</if> "
            + "ORDER BY csp.id DESC"
            + "</script>")
    List<CustomerPriceRow> selectCustomerPrices(@Param("skuId") Long skuId,
                                                @Param("customerId") Long customerId);

    /** 一客一价列表行（有效期用 LocalDateTime 承载，读侧再换算毫秒；其余为联查展示字段）。 */
    class CustomerPriceRow {
        private Long id;
        private Long customerId;
        private Long skuId;
        private Long priceCent;
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
        private String customerName;
        private String customerPhone;
        private Long productId;
        private String productName;
        private String skuCode;
        private String specJson;
        private Long retailPriceCent;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }
        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Long getPriceCent() { return priceCent; }
        public void setPriceCent(Long priceCent) { this.priceCent = priceCent; }
        public LocalDateTime getValidFrom() { return validFrom; }
        public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
        public LocalDateTime getValidTo() { return validTo; }
        public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public String getCustomerPhone() { return customerPhone; }
        public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public String getSpecJson() { return specJson; }
        public void setSpecJson(String specJson) { this.specJson = specJson; }
        public Long getRetailPriceCent() { return retailPriceCent; }
        public void setRetailPriceCent(Long retailPriceCent) { this.retailPriceCent = retailPriceCent; }
    }
}
