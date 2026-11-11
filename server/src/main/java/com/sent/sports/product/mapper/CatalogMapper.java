package com.sent.sports.product.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 前台商品目录查询（任务 2：解除 P4 查询桩）。
 *
 * <p>用 MyBatis-Plus {@link Page} + {@link IPage} 做真实分页，联查 {@code product / sku /
 * sku_stock / product_category}。只暴露 {@code deleted=0 AND status='ON_SHELF'} 的 SPU，
 * SKU 可售库存按「实物 − 锁定 − 待出」计算，与库存四口径一致。</p>
 *
 * <p>行对象用嵌套静态类承载，避免为三个只读视图各建一个顶层文件。</p>
 */
@Mapper
public interface CatalogMapper {

    @Select("<script>"
            + "SELECT p.id, p.product_no, p.title, p.category_id, c.name AS category_name, p.status, "
            + "p.main_image, p.sales_count, p.detail_html AS detailHtml "
            + "FROM product p LEFT JOIN product_category c ON c.id = p.category_id "
            + "WHERE p.deleted = 0 AND p.status = 'ON_SHELF' "
            + "<if test=\"categoryId != null\"> AND p.category_id = #{categoryId}</if> "
            + "<if test=\"keyword != null and keyword != ''\"> AND p.title LIKE CONCAT('%', #{keyword}, '%')</if> "
            + "ORDER BY p.published_at DESC, p.id DESC"
            + "</script>")
    IPage<ProductRow> selectProductPage(Page<ProductRow> page,
                                        @Param("categoryId") Long categoryId,
                                        @Param("keyword") String keyword);

    @Select("<script>"
            + "SELECT COUNT(*) FROM product p WHERE p.deleted = 0 AND p.status = 'ON_SHELF' "
            + "<if test=\"categoryId != null\"> AND p.category_id = #{categoryId}</if> "
            + "<if test=\"keyword != null and keyword != ''\"> AND p.title LIKE CONCAT('%', #{keyword}, '%')</if> "
            + "</script>")
    long countProducts(@Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    /**
     * 管理端商品分页：**不**按上下架硬过滤，草稿与已下架商品必须留在列表里，
     * 否则运营下架后商品直接消失、无法再上架（前台只能看 ON_SHELF，不能复用本方法）。
     * {@code status} 为空表示全部状态，由列表页的「上架状态」筛选决定是否收窄。
     */
    @Select("<script>"
            + "SELECT p.id, p.product_no, p.title, p.category_id, c.name AS category_name, p.status, "
            + "p.main_image, p.sales_count, p.detail_html AS detailHtml "
            + "FROM product p LEFT JOIN product_category c ON c.id = p.category_id "
            + "WHERE p.deleted = 0 "
            + "<if test=\"status != null and status != ''\"> AND p.status = #{status}</if> "
            + "<if test=\"categoryId != null\"> AND p.category_id = #{categoryId}</if> "
            + "<if test=\"keyword != null and keyword != ''\"> AND p.title LIKE CONCAT('%', #{keyword}, '%')</if> "
            + "ORDER BY p.published_at DESC, p.id DESC"
            + "</script>")
    IPage<ProductRow> selectAdminProductPage(Page<ProductRow> page,
                                             @Param("categoryId") Long categoryId,
                                             @Param("keyword") String keyword,
                                             @Param("status") String status);

    @Select("SELECT p.id, p.product_no, p.title, p.category_id, c.name AS category_name, p.status, "
            + "p.main_image, p.sales_count, p.detail_html AS detailHtml "
            + "FROM product p LEFT JOIN product_category c ON c.id = p.category_id "
            + "WHERE p.id = #{productId} AND p.deleted = 0")
    ProductRow selectProductById(@Param("productId") long productId);

    @Select("SELECT s.id, s.sku_no, s.spec_json, s.retail_price_cent, s.status, "
            + "(SELECT MIN(l.price_cent) FROM ladder_price l WHERE l.sku_id = s.id) AS ladder_price_cent, "
            + "COALESCE(st.physical_qty - st.locked_qty - st.pending_qty - st.frozen_qty, 0) AS available_qty "
            + "FROM sku s LEFT JOIN sku_stock st ON st.sku_id = s.id "
            + "WHERE s.spu_id = #{spuId} AND (s.status IS NULL OR s.status <> 'OFF') ORDER BY s.id")
    List<SkuRow> selectSkusWithStock(@Param("spuId") long spuId);

    @Select("SELECT id, parent_id, name FROM product_category ORDER BY sort, id")
    List<CategoryRow> selectCategories();

    /** 演示数据：写入一个分类（id 由数据库自增生成）。 */
    @Insert("INSERT INTO product_category (name, sort) VALUES (#{name}, #{sort})")
    void insertCategory(@Param("name") String name, @Param("sort") int sort);

    /** SPU 列表/详情行。 */
    class ProductRow {
        private Long id;
        private String productNo;
        private String title;
        private Long categoryId;
        private String categoryName;
        private String status;
        private String mainImage;
        private Long salesCount;
        private String detailHtml;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProductNo() { return productNo; }
        public void setProductNo(String productNo) { this.productNo = productNo; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMainImage() { return mainImage; }
        public void setMainImage(String mainImage) { this.mainImage = mainImage; }
        public Long getSalesCount() { return salesCount; }
        public void setSalesCount(Long salesCount) { this.salesCount = salesCount; }
        public String getDetailHtml() { return detailHtml; }
        public void setDetailHtml(String detailHtml) { this.detailHtml = detailHtml; }
    }

    /** SKU + 可售库存行。 */
    class SkuRow {
        private Long id;
        private String skuNo;
        private String specJson;
        private Long retailPriceCent;
        private Long ladderPriceCent;
        private String status;
        private Integer availableQty;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getSkuNo() { return skuNo; }
        public void setSkuNo(String skuNo) { this.skuNo = skuNo; }
        public String getSpecJson() { return specJson; }
        public void setSpecJson(String specJson) { this.specJson = specJson; }
        public Long getRetailPriceCent() { return retailPriceCent; }
        public void setRetailPriceCent(Long retailPriceCent) { this.retailPriceCent = retailPriceCent; }
        public Long getLadderPriceCent() { return ladderPriceCent; }
        public void setLadderPriceCent(Long ladderPriceCent) { this.ladderPriceCent = ladderPriceCent; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getAvailableQty() { return availableQty; }
        public void setAvailableQty(Integer availableQty) { this.availableQty = availableQty; }
    }

    /** 分类行。 */
    class CategoryRow {
        private Long id;
        private Long parentId;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
