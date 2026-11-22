package com.sent.sports.integration;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.review.dto.ReviewDtos;
import com.sent.sports.review.service.ReviewService;
import com.sent.sports.support.FullStackIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商品评价闭环集成测试（V55）。
 *
 * <p>证明的不是「接口能返回 200」，而是三件事真的落到库里：</p>
 * <ol>
 *   <li>只有 <b>已完成订单</b> 里的商品可评价——在途订单、没买过的客户都被真实 SQL 拦住；</li>
 *   <li>重复评价被 <b>联合唯一索引</b> 挡住并转成 40901，而不是写进去两条；</li>
 *   <li>列表的摘要（好评率/均分）与脱敏昵称来自真实聚合与 JOIN。</li>
 * </ol>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class ReviewIT extends FullStackIntegrationTest {

    private static final long PRODUCT_ID = 9501L;
    private static final long SKU_ID = 9511L;
    /** 同商品的另一个规格：用于「买了 A 规格、页面选中 B 规格」的兜底资格 */
    private static final long SKU_ALT_ID = 9512L;
    private static final long CUSTOMER_ID = 9601L;
    private static final long OTHER_CUSTOMER_ID = 9602L;
    private static final String ORDER_NO = "SO2600000009501";

    @Autowired
    private ReviewService reviewService;

    @BeforeEach
    void seed() {
        // SKU 的 spu_id 必须真的是 PRODUCT_ID，评价资格按 sku.spu_id 判定
        jdbc.update("INSERT INTO sku (id, spu_id, sku_no, retail_price_cent, floor_price_cent, "
                        + "cost_price_cent, status) VALUES (?, ?, ?, 19900, 15900, 9900, 'ON')",
                SKU_ID, PRODUCT_ID, "SKU-" + SKU_ID);
        jdbc.update("INSERT INTO sku (id, spu_id, sku_no, retail_price_cent, floor_price_cent, "
                        + "cost_price_cent, status) VALUES (?, ?, ?, 18900, 14900, 8900, 'ON')",
                SKU_ALT_ID, PRODUCT_ID, "SKU-" + SKU_ALT_ID);
        jdbc.update("INSERT INTO customer (id, customer_no, phone, nickname, type) "
                + "VALUES (?, 'C9601', '13900009601', '张伟', 'PERSON')", CUSTOMER_ID);
    }

    /** 播一笔订单（可直接指定状态），行里挂 SKU_ID。 */
    private void seedOrder(String orderNo, long customerId, String status) {
        jdbc.update("INSERT INTO orders (id, order_no, customer_id, status, pay_status, "
                        + "goods_amount_cent, payable_cent, paid_cent) "
                        + "VALUES (?, ?, ?, ?, 'PAID', 19900, 19900, 19900)",
                orderNo.hashCode() & 0x7fffffffL, orderNo, customerId, status);
        Long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
        jdbc.update("INSERT INTO order_item (order_id, sku_id, qty, unit_price_cent, amount_cent) "
                + "VALUES (?, ?, 1, 19900, 19900)", orderId, SKU_ID);
    }

    private ReviewDtos.CreateReviewCmd cmd(int rating, String content, List<String> images, boolean anonymous) {
        return new ReviewDtos.CreateReviewCmd(PRODUCT_ID, SKU_ID, rating, content, images, anonymous);
    }

    @Test
    @DisplayName("已完成订单可评价：落库订单号/星级/图片 JSON，列表能读回脱敏昵称与好评率")
    void completedOrderCanReview() {
        seedOrder(ORDER_NO, CUSTOMER_ID, "COMPLETED");

        ReviewDtos.ReviewVO vo = reviewService.submit(CUSTOMER_ID,
                cmd(5, "手感很好，训练够用", List.of("2026/11/aa/hash1.png"), false));

        assertThat(vo.rating()).isEqualTo(5);
        assertThat(vo.images()).containsExactly("2026/11/aa/hash1.png");
        assertThat(countOf("product_review")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT order_no FROM product_review WHERE product_id = ?",
                String.class, PRODUCT_ID)).isEqualTo(ORDER_NO);

        ReviewDtos.ReviewPageVO page = reviewService.list(PRODUCT_ID, new PageQuery());
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.list()).hasSize(1);
        assertThat(page.list().get(0).nickname()).isEqualTo("张**");
        assertThat(page.list().get(0).avatarText()).isEqualTo("张");
        assertThat(page.summary().total()).isEqualTo(1);
        assertThat(page.summary().positiveRate()).isEqualTo(100);
        assertThat(page.summary().avgRating()).isEqualTo("5.0");
    }

    @Test
    @DisplayName("重复评价被唯一索引拦住：抛 STATE_CONFLICT，库里仍只有 1 条")
    void duplicateReviewRejectedByUniqueIndex() {
        seedOrder(ORDER_NO, CUSTOMER_ID, "COMPLETED");
        reviewService.submit(CUSTOMER_ID, cmd(5, "第一次评价", List.of(), false));

        assertThatThrownBy(() -> reviewService.submit(CUSTOMER_ID, cmd(4, "再来一条", List.of(), false)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STATE_CONFLICT);

        assertThat(countOf("product_review")).isEqualTo(1);
    }

    @Test
    @DisplayName("在途订单不可评价：待发货订单抛 FORBIDDEN，且不写库")
    void pendingOrderCannotReview() {
        seedOrder(ORDER_NO, CUSTOMER_ID, "WAIT_SHIP");

        assertThatThrownBy(() -> reviewService.submit(CUSTOMER_ID, cmd(5, "还没收到货", List.of(), false)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        assertThat(countOf("product_review")).isZero();
    }

    @Test
    @DisplayName("没买过的客户不可评价：别人买得再多也与他无关")
    void strangerCannotReview() {
        seedOrder(ORDER_NO, CUSTOMER_ID, "COMPLETED");

        assertThatThrownBy(() -> reviewService.submit(OTHER_CUSTOMER_ID, cmd(5, "路过评价", List.of(), false)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        assertThat(countOf("product_review")).isZero();
    }

    @Test
    @DisplayName("买了 A 规格、页面选中 B 规格：资格按商品兜底，仍然可以评价")
    void reviewWithAnotherSkuOfSameProduct() {
        seedOrder(ORDER_NO, CUSTOMER_ID, "COMPLETED");

        ReviewDtos.ReviewVO vo = reviewService.submit(CUSTOMER_ID,
                new ReviewDtos.CreateReviewCmd(PRODUCT_ID, SKU_ALT_ID, 4, "同款另一个规格", List.of(), false));

        assertThat(vo.rating()).isEqualTo(4);
        assertThat(countOf("product_review")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sku_id FROM product_review WHERE order_no = ?",
                Long.class, ORDER_NO)).isEqualTo(SKU_ALT_ID);
    }

    @Test
    @DisplayName("评价资格：已完成订单且未评价 → 可评价；提交后翻转为「已评价」")
    void eligibilityFlipsAfterSubmit() {
        seedOrder(ORDER_NO, CUSTOMER_ID, "COMPLETED");

        assertThat(reviewService.eligibility(CUSTOMER_ID, PRODUCT_ID).eligible()).isTrue();
        assertThat(reviewService.eligibility(CUSTOMER_ID, PRODUCT_ID).reviewed()).isFalse();

        reviewService.submit(CUSTOMER_ID, cmd(5, "很好", List.of(), false));

        ReviewDtos.ReviewEligibilityVO after = reviewService.eligibility(CUSTOMER_ID, PRODUCT_ID);
        assertThat(after.eligible()).isFalse();
        assertThat(after.reviewed()).isTrue();
        assertThat(reviewService.eligibility(OTHER_CUSTOMER_ID, PRODUCT_ID).eligible()).isFalse();
    }

    @Test
    @DisplayName("匿名评价：列表显示「匿名用户」，不泄露真实昵称；两条评价的好评率按 rating≥4 计算")
    void anonymousReviewMaskedAndSummary() {
        seedOrder(ORDER_NO, CUSTOMER_ID, "COMPLETED");
        seedOrder(ORDER_NO + "B", OTHER_CUSTOMER_ID, "COMPLETED");
        jdbc.update("INSERT INTO customer (id, customer_no, phone, nickname, type) "
                + "VALUES (?, 'C9602', '13900009602', '李小明', 'PERSON')", OTHER_CUSTOMER_ID);

        reviewService.submit(CUSTOMER_ID, cmd(5, "很好", List.of(), true));
        reviewService.submit(OTHER_CUSTOMER_ID, cmd(3, "一般", List.of(), false));

        ReviewDtos.ReviewPageVO page = reviewService.list(PRODUCT_ID, new PageQuery());
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.summary().positiveRate()).isEqualTo(50);
        assertThat(page.summary().avgRating()).isEqualTo("4.0");
        assertThat(page.list()).anySatisfy(item -> {
            assertThat(item.anonymous()).isTrue();
            assertThat(item.nickname()).isEqualTo("匿名用户");
        });
        assertThat(page.list()).noneSatisfy(item ->
                assertThat(item.nickname()).isEqualTo("张伟"));
    }
}
