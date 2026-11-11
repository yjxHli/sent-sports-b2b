package com.sent.sports.integration;

import com.sent.sports.product.dto.CatalogDtos;
import com.sent.sports.product.dto.admin.AdminProductDtos;
import com.sent.sports.product.service.ProductQueryService;
import com.sent.sports.product.service.admin.ProductAdminService;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品编辑保存后的「重新进入页面」真库回归：新增 / 移除 / 修改 SKU 行、价格、库存、描述都要保持，
 * 且公开目录详情不再返回已移除的 SKU。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class ProductAdminCrudIT extends FullStackIntegrationTest {

    @Autowired
    private ProductAdminService productAdminService;

    @Autowired
    private ProductQueryService productQueryService;

    @Test
    @DisplayName("编辑商品保存后，新增/移除/修改的 SKU 行重新查详情仍然生效")
    void updatePersistsSkuMatrixChanges() {
        seedProduct();

        productAdminService.update(9103L, new AdminProductDtos.ProductForm(
                "新商品", 1L, "新描述", List.of(
                new AdminProductDtos.SkuForm(9205L, "SKU-9205", "室内外通用", 1L, 29900L, 210),
                new AdminProductDtos.SkuForm(null, "", "新规格", 1L, 39900L, 50))));

        assertThat(jdbc.queryForObject("SELECT title FROM product WHERE id = ?", String.class, 9103L))
                .isEqualTo("新商品");
        assertThat(jdbc.queryForObject("SELECT detail_html FROM product WHERE id = ?", String.class, 9103L))
                .isEqualTo("新描述");
        assertThat(jdbc.queryForObject("SELECT status FROM sku WHERE id = ?", String.class, 9206L))
                .isEqualTo("OFF");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sku WHERE spu_id = ? AND status = 'ON'", Integer.class, 9103L))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT retail_price_cent FROM sku WHERE id = ?", Long.class, 9205L))
                .isEqualTo(29900L);
        assertThat(jdbc.queryForObject(
                "SELECT physical_qty FROM sku_stock WHERE sku_id = ?", Integer.class, 9205L))
                .isEqualTo(210);

        Long newSkuId = jdbc.queryForObject(
                "SELECT id FROM sku WHERE spu_id = ? AND id <> ? AND status = 'ON'",
                Long.class, 9103L, 9205L);
        assertThat(newSkuId).isNotNull();
        assertThat(jdbc.queryForObject("SELECT sku_no FROM sku WHERE id = ?", String.class, newSkuId))
                .isNotBlank();
        assertThat(jdbc.queryForObject(
                "SELECT physical_qty FROM sku_stock WHERE sku_id = ?", Integer.class, newSkuId))
                .isEqualTo(50);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ladder_price WHERE sku_id = ?", Integer.class, 9206L))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_sku_price WHERE sku_id = ?", Integer.class, 9206L))
                .isZero();

        CatalogDtos.ProductVO detail = productQueryService.detail("9103");
        assertThat(detail.name()).isEqualTo("新商品");
        assertThat(detail.description()).isEqualTo("新描述");
        assertThat(detail.skus()).hasSize(2);
        assertThat(detail.skus()).extracting(CatalogDtos.SkuVO::skuId).doesNotContain("9206");
        assertThat(detail.skus()).extracting(CatalogDtos.SkuVO::skuCode).contains("SKU-9205");
    }

    @Test
    @DisplayName("新建商品保存后，SKU 行与初始库存重新查详情可见")
    void createPersistsProductAndSkuRows() {
        seedCategory();

        AdminProductDtos.ProductVO vo = productAdminService.create(new AdminProductDtos.ProductForm(
                "新上架商品", 1L, "创建描述",
                List.of(new AdminProductDtos.SkuForm("", "标准款", 1L, 10000L, 7))));

        long productId = Long.parseLong(vo.productId());
        assertThat(vo.skuCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM product WHERE id = ?", String.class, productId))
                .isEqualTo("新上架商品");
        assertThat(jdbc.queryForObject("SELECT status FROM product WHERE id = ?", String.class, productId))
                .isEqualTo("ON_SHELF");
        Long skuId = jdbc.queryForObject("SELECT id FROM sku WHERE spu_id = ?", Long.class, productId);
        assertThat(skuId).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT physical_qty FROM sku_stock WHERE sku_id = ?", Integer.class, skuId))
                .isEqualTo(7);

        CatalogDtos.ProductVO detail = productQueryService.detail(String.valueOf(productId));
        assertThat(detail.description()).isEqualTo("创建描述");
        assertThat(detail.skus()).hasSize(1);
        assertThat(detail.skus().get(0).skuCode()).isNotBlank();
    }

    private void seedCategory() {
        jdbc.update("INSERT INTO product_category (id, name, sort) VALUES (1, '球类', 0)");
    }

    private void seedProduct() {
        seedCategory();
        jdbc.update("INSERT INTO product "
                        + "(id, product_no, title, category_id, detail_html, status, sales_count, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 'ON_SHELF', 0, 0)",
                9103L, "IT-SPU-9103", "旧商品", 1L, "旧描述");
        jdbc.update("INSERT INTO sku "
                        + "(id, spu_id, sku_no, spec_json, retail_price_cent, floor_price_cent, cost_price_cent, status) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 0, 'ON')",
                9205L, 9103L, "SKU-9205", "{\"spec\":\"旧规格\"}", 10000L);
        jdbc.update("INSERT INTO sku "
                        + "(id, spu_id, sku_no, spec_json, retail_price_cent, floor_price_cent, cost_price_cent, status) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 0, 'ON')",
                9206L, 9103L, "SKU-9206", "{\"spec\":\"待移除规格\"}", 20000L);
        jdbc.update("INSERT INTO sku_stock "
                        + "(sku_id, physical_qty, locked_qty, pending_qty, frozen_qty, safety_qty) "
                        + "VALUES (?, 200, 0, 0, 0, 0)",
                9205L);
        jdbc.update("INSERT INTO sku_stock "
                        + "(sku_id, physical_qty, locked_qty, pending_qty, frozen_qty, safety_qty) "
                        + "VALUES (?, 300, 0, 0, 0, 0)",
                9206L);
        jdbc.update("INSERT INTO ladder_price (sku_id, min_qty, max_qty, price_cent) VALUES (9206, 1, 9, 9000)");
        jdbc.update("INSERT INTO customer_sku_price (customer_id, sku_id, price_cent) VALUES (9002, 9206, 9000)");
    }
}
