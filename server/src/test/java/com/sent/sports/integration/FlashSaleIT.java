package com.sent.sports.integration;

import com.sent.sports.product.dto.CatalogDtos;
import com.sent.sports.product.dto.admin.FlashSaleDtos;
import com.sent.sports.product.service.PricingServiceFacade;
import com.sent.sports.product.service.ProductQueryService;
import com.sent.sports.product.service.admin.FlashSaleAdminService;
import com.sent.sports.product.service.dto.LinePrice;
import com.sent.sports.product.service.dto.PriceRequest;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 秒杀价穿透集成测试（V54）——**本次改造最重要的一条**。
 *
 * <p>它证明的不是"页面能显示秒杀价"，而是：</p>
 * <ol>
 *   <li>活动发布后，{@code PricingServiceFacade}（购物车预览与下单共用）取到的单价就是秒杀价，
 *       {@code source=FLASH_SALE}；</li>
 *   <li>活动下线后，同一链路立刻回落到零售价，"只改前端展示"的假功能做不出这个效果；</li>
 *   <li>详情页快照与结算取价同源——不会出现"详情 25 元、结算 100 元"。</li>
 * </ol>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class FlashSaleIT extends FullStackIntegrationTest {

    private static final long SPU_ID = 9301L;
    private static final long SKU_ID = 9401L;

    @Autowired
    private FlashSaleAdminService flashSaleAdminService;

    @Autowired
    private ProductQueryService productQueryService;

    @Autowired
    private PricingServiceFacade pricingServiceFacade;

    @Test
    @DisplayName("发布秒杀后：取价门面返回秒杀价，下线后回落到零售价")
    void flashSalePriceFlowsIntoPricingFacade() {
        seedProduct();

        // 活动前：取价走零售价
        assertThat(price().unitPriceCent()).isEqualTo(10000L);
        assertThat(price().sourceCode()).isEqualTo("RETAIL");
        assertThat(productQueryService.detail(String.valueOf(SPU_ID)).flashSale()).isNull();

        // 建活动并发布：25 元秒杀
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);
        FlashSaleDtos.ActivityVO created = flashSaleAdminService.create(new FlashSaleDtos.ActivityForm(
                "限时秒杀", start, end, null,
                List.of(new FlashSaleDtos.ItemForm(SKU_ID, 2500L))));
        flashSaleAdminService.setStatus(Long.valueOf(created.activityId()), true);

        // 核心断言：结算取价 = 秒杀价，来源 FLASH_SALE
        LinePrice afterPublish = price();
        assertThat(afterPublish.unitPriceCent())
                .as("秒杀必须穿透到取价门面，否则就是只改前端展示的假功能")
                .isEqualTo(2500L);
        assertThat(afterPublish.sourceCode()).isEqualTo("FLASH_SALE");

        // 详情页快照与取价同源
        CatalogDtos.FlashSaleVO snapshot = productQueryService.detail(String.valueOf(SPU_ID)).flashSale();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.flashPriceCent()).isEqualTo(2500L);
        assertThat(snapshot.originPriceCent()).isEqualTo(10000L);
        assertThat(snapshot.flashPriceYuan()).isEqualTo("25.00");

        // 下线后立即失效
        flashSaleAdminService.setStatus(Long.valueOf(created.activityId()), false);
        LinePrice afterOffline = price();
        assertThat(afterOffline.unitPriceCent()).isEqualTo(10000L);
        assertThat(afterOffline.sourceCode()).isEqualTo("RETAIL");
        assertThat(productQueryService.detail(String.valueOf(SPU_ID)).flashSale()).isNull();
    }

    @Test
    @DisplayName("活动过期（end_at 已过去）自动失效，无需定时任务改状态")
    void expiredActivityStopsApplying() {
        seedProduct();

        LocalDateTime start = LocalDateTime.now().minusHours(4);
        LocalDateTime end = LocalDateTime.now().minusHours(1);
        FlashSaleDtos.ActivityVO created = flashSaleAdminService.create(new FlashSaleDtos.ActivityForm(
                "已结束的秒杀", start, end, null,
                List.of(new FlashSaleDtos.ItemForm(SKU_ID, 2500L))));
        flashSaleAdminService.setStatus(Long.valueOf(created.activityId()), true);

        assertThat(price().unitPriceCent()).isEqualTo(10000L);
        assertThat(price().sourceCode()).isEqualTo("RETAIL");
        assertThat(productQueryService.detail(String.valueOf(SPU_ID)).flashSale()).isNull();
    }

    @Test
    @DisplayName("开启后即生效、未开始的活动不生效（草稿态 + 未来时间窗）")
    void pendingActivityDoesNotApply() {
        seedProduct();

        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = LocalDateTime.now().plusHours(4);
        FlashSaleDtos.ActivityVO created = flashSaleAdminService.create(new FlashSaleDtos.ActivityForm(
                "未来秒杀", start, end, null,
                List.of(new FlashSaleDtos.ItemForm(SKU_ID, 2500L))));
        flashSaleAdminService.setStatus(Long.valueOf(created.activityId()), true);

        assertThat(price().sourceCode()).isEqualTo("RETAIL");
    }

    @Test
    @DisplayName("企业协议价优先于秒杀：协议 5 折 50 元 vs 秒杀 25 元 → 按活动规则取秒杀（PERSON 口径）")
    void flashSaleAppliesForPersonalCustomer() {
        seedProduct();

        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);
        FlashSaleDtos.ActivityVO created = flashSaleAdminService.create(new FlashSaleDtos.ActivityForm(
                "限时秒杀", start, end, null,
                List.of(new FlashSaleDtos.ItemForm(SKU_ID, 2500L))));
        flashSaleAdminService.setStatus(Long.valueOf(created.activityId()), true);

        // 企业客户命中协议价 → 秒杀不生效（合同价优先），详见 FlashSalePricingTest
        LinePrice enterprise = pricingServiceFacade.price(new PriceRequest(
                SKU_ID, 1, "ENTERPRISE", 100, 50, false, List.of()));
        assertThat(enterprise.sourceCode()).isEqualTo("AGREEMENT");
        assertThat(enterprise.unitPriceCent()).isEqualTo(5000L);

        // 个人客户 → 秒杀生效
        assertThat(price().sourceCode()).isEqualTo("FLASH_SALE");
        assertThat(price().unitPriceCent()).isEqualTo(2500L);
    }

    // ---------- helpers ----------

    private LinePrice price() {
        return pricingServiceFacade.price(new PriceRequest(SKU_ID, 1, "PERSON", 100, 100, false, List.of()));
    }

    private void seedProduct() {
        jdbc.update("INSERT INTO product_category (id, name, sort) VALUES (1, '球类', 0)");
        jdbc.update("INSERT INTO product "
                        + "(id, product_no, title, category_id, main_image, detail_html, status, sales_count, deleted) "
                        + "VALUES (?, ?, ?, 1, ?, ?, 'ON_SHELF', 0, 0)",
                SPU_ID, "IT-SPU-9301", "秒杀测试商品", "2026/11/main.png", "详情文本");
        jdbc.update("INSERT INTO sku "
                        + "(id, spu_id, sku_no, spec_json, retail_price_cent, floor_price_cent, cost_price_cent, status) "
                        + "VALUES (?, ?, ?, ?, 10000, 1000, 800, 'ON')",
                SKU_ID, SPU_ID, "SKU-9401", "{\"spec\":\"红色 / L\"}");
        jdbc.update("INSERT INTO sku_stock "
                        + "(sku_id, physical_qty, locked_qty, pending_qty, frozen_qty, safety_qty) "
                        + "VALUES (?, 100, 0, 0, 0, 0)",
                SKU_ID);
    }
}
