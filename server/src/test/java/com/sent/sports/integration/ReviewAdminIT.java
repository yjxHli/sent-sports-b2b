package com.sent.sports.integration;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.review.dto.ReviewDtos;
import com.sent.sports.review.dto.admin.AdminReviewDtos;
import com.sent.sports.review.service.ReviewAdminService;
import com.sent.sports.review.service.ReviewService;
import com.sent.sports.support.FullStackIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 评价治理闭环集成测试（V56）。
 *
 * <p>证明的不是「接口返回 200」，而是四件事真的发生：</p>
 * <ol>
 *   <li>运营侧的隐藏落到 {@code product_review.status='HIDDEN'}；</li>
 *   <li>用户端 {@code GET /api/products/{id}/reviews} 背后的查询<b>查不到</b>隐藏评价，总数与好评率同步重算；</li>
 *   <li>每次状态变更在 {@code sys_operation_log} 留痕（action / 前后状态 / 操作人）；</li>
 *   <li>恢复后用户端立即能看到，且「同一订单只能评一次」的防重约束不受隐藏影响。</li>
 * </ol>
 *
 * <p>注：{@link FullStackIntegrationTest} 是 NONE 环境（不起 Tomcat），
 * 这里直接调用 Controller 背后的同一 Service；权限与路由链路由 E2E 覆盖。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class ReviewAdminIT extends FullStackIntegrationTest {

    private static final long PRODUCT_ID = 9701L;
    private static final long SKU_ID = 9711L;
    /** 两条评价必须来自不同客户/订单：同一订单同一商品只能评一次（uk_review_order_product）。 */
    private static final long GOOD_CUSTOMER_ID = 9801L;
    private static final long BAD_CUSTOMER_ID = 9802L;
    private static final long OPERATOR_ID = 9001L;
    private static final String GOOD_ORDER_NO = "SO2600000009701";
    private static final String BAD_ORDER_NO = "SO2600000009702";

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewAdminService reviewAdminService;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO product (id, product_no, title, status) "
                + "VALUES (?, 'P9701', '森特速干训练短袖', 'ON_SALE')", PRODUCT_ID);
        jdbc.update("INSERT INTO sku (id, spu_id, sku_no, retail_price_cent, floor_price_cent, "
                        + "cost_price_cent, status) VALUES (?, ?, ?, 19900, 15900, 9900, 'ON')",
                SKU_ID, PRODUCT_ID, "SKU-" + SKU_ID);
        seedCustomer(GOOD_CUSTOMER_ID, "C9801", "13900009801", "张伟");
        seedCustomer(BAD_CUSTOMER_ID, "C9802", "13900009802", "李小明");
        seedOrder(97011L, GOOD_ORDER_NO, GOOD_CUSTOMER_ID);
        seedOrder(97012L, BAD_ORDER_NO, BAD_CUSTOMER_ID);
    }

    private void seedCustomer(long id, String no, String phone, String nickname) {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, nickname, type) "
                + "VALUES (?, ?, ?, ?, 'PERSON')", id, no, phone, nickname);
    }

    private void seedOrder(long orderId, String orderNo, long customerId) {
        jdbc.update("INSERT INTO orders (id, order_no, customer_id, status, pay_status, "
                        + "goods_amount_cent, payable_cent, paid_cent) VALUES (?, ?, ?, 'COMPLETED', 'PAID', 19900, 19900, 19900)",
                orderId, orderNo, customerId);
        jdbc.update("INSERT INTO order_item (order_id, sku_id, qty, unit_price_cent, amount_cent) "
                + "VALUES (?, ?, 1, 19900, 19900)", orderId, SKU_ID);
    }

    private ReviewDtos.ReviewVO submit(long customerId, int rating, String content) {
        return reviewService.submit(customerId,
                new ReviewDtos.CreateReviewCmd(PRODUCT_ID, SKU_ID, rating, content, List.of(), false));
    }

    @Test
    @DisplayName("管理端列表：按商品/评分/状态筛选真实库，商品标题联查出来，昵称脱敏")
    void adminListFilters() {
        submit(GOOD_CUSTOMER_ID, 5, "手感很好");
        submit(BAD_CUSTOMER_ID, 2, "尺码偏大");

        PageResult<AdminReviewDtos.AdminReviewVO> all =
                reviewAdminService.list(PRODUCT_ID, null, null, new PageQuery());
        assertThat(all.getTotal()).isEqualTo(2);
        assertThat(all.getList()).allSatisfy(vo -> {
            assertThat(vo.productTitle()).isEqualTo("森特速干训练短袖");
            assertThat(vo.userLabel()).endsWith("**");
            assertThat(vo.status()).isEqualTo("NORMAL");
        });
        assertThat(all.getList()).extracting(AdminReviewDtos.AdminReviewVO::userLabel)
                .containsExactlyInAnyOrder("张**", "李**");

        assertThat(reviewAdminService.list(PRODUCT_ID, 2, null, new PageQuery()).getTotal()).isEqualTo(1);
        assertThat(reviewAdminService.list(PRODUCT_ID, null, "HIDDEN", new PageQuery()).getTotal()).isZero();
        assertThat(reviewAdminService.list(9999L, null, null, new PageQuery()).getTotal()).isZero();
    }

    @Test
    @DisplayName("隐藏差评：用户端列表查不到、总数与好评率/差评数同步重算，操作日志留痕")
    void hideRemovesFromUserSideAndRecalculatesSummary() {
        long goodId = Long.parseLong(submit(GOOD_CUSTOMER_ID, 5, "手感很好").reviewId());
        long badId = Long.parseLong(submit(BAD_CUSTOMER_ID, 2, "尺码偏大").reviewId());

        ReviewDtos.ReviewPageVO before = reviewService.list(PRODUCT_ID, new PageQuery());
        assertThat(before.total()).isEqualTo(2);
        assertThat(before.summary().positiveRate()).isEqualTo(50);
        assertThat(before.summary().negativeCount()).isEqualTo(1);

        AdminReviewDtos.AdminReviewVO hidden =
                reviewAdminService.updateStatus(badId, "HIDDEN", "含引导私下交易的表述", OPERATOR_ID);
        assertThat(hidden.status()).isEqualTo("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT status FROM product_review WHERE id = ?", String.class, badId))
                .isEqualTo("HIDDEN");

        // 用户端（GET /api/products/{id}/reviews 的同一查询）：隐藏的那条必须查不到
        ReviewDtos.ReviewPageVO after = reviewService.list(PRODUCT_ID, new PageQuery());
        assertThat(after.total()).isEqualTo(1);
        assertThat(after.list()).extracting(ReviewDtos.ReviewVO::reviewId)
                .containsExactly(String.valueOf(goodId))
                .doesNotContain(String.valueOf(badId));
        assertThat(after.summary().total()).isEqualTo(1);
        assertThat(after.summary().positiveRate())
                .as("隐藏差评后好评率必须按剩余评价重算为 100%")
                .isEqualTo(100);
        assertThat(after.summary().negativeCount()).isZero();

        // 管理端仍能看到被隐藏的那条（治理页要能恢复）
        PageResult<AdminReviewDtos.AdminReviewVO> hiddenPage =
                reviewAdminService.list(PRODUCT_ID, null, "HIDDEN", new PageQuery());
        assertThat(hiddenPage.getTotal()).isEqualTo(1);
        assertThat(hiddenPage.getList().get(0).reviewId()).isEqualTo(String.valueOf(badId));

        // 操作日志：module/action/前后状态/操作人
        Map<String, Object> log = jdbc.queryForMap(
                "SELECT staff_id, module, action, target_type, target_id, before_json, after_json, reason "
                        + "FROM sys_operation_log WHERE target_id = ? ORDER BY id DESC LIMIT 1",
                String.valueOf(badId));
        // JDBC 可能把 BIGINT 映射成 Integer/Long，统一按 Number 比较，避免 9001 vs 9001L 的假红
        assertThat(((Number) log.get("staff_id")).longValue()).isEqualTo(OPERATOR_ID);
        assertThat(log.get("module")).isEqualTo("REVIEW");
        assertThat(log.get("action")).isEqualTo("HIDE");
        assertThat(log.get("target_type")).isEqualTo("PRODUCT_REVIEW");
        assertThat(String.valueOf(log.get("before_json"))).contains("NORMAL");
        assertThat(String.valueOf(log.get("after_json"))).contains("HIDDEN");
        assertThat(log.get("reason")).isEqualTo("含引导私下交易的表述");
    }

    @Test
    @DisplayName("恢复展示：用户端立即可见，好评率回到隐藏前；幂等重复隐藏不写第二条日志")
    void restoreBringsReviewBackAndIsIdempotent() {
        submit(GOOD_CUSTOMER_ID, 5, "手感很好");
        long badId = Long.parseLong(submit(BAD_CUSTOMER_ID, 2, "尺码偏大").reviewId());

        reviewAdminService.updateStatus(badId, "HIDDEN", null, OPERATOR_ID);
        reviewAdminService.updateStatus(badId, "HIDDEN", null, OPERATOR_ID);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE target_id = ?", Integer.class, String.valueOf(badId)))
                .as("重复隐藏是幂等的，不应写第二条日志")
                .isEqualTo(1);

        reviewAdminService.updateStatus(badId, "NORMAL", "申诉通过，恢复展示", OPERATOR_ID);

        ReviewDtos.ReviewPageVO page = reviewService.list(PRODUCT_ID, new PageQuery());
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.summary().positiveRate()).isEqualTo(50);
        assertThat(page.summary().negativeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE target_id = ? AND action = 'RESTORE'",
                Integer.class, String.valueOf(badId))).isEqualTo(1);
    }

    @Test
    @DisplayName("隐藏不改防重：同一订单商品隐藏后再提交仍被判重复（40901）")
    void hiddenReviewStillBlocksDuplicate() {
        long reviewId = Long.parseLong(submit(BAD_CUSTOMER_ID, 2, "尺码偏大").reviewId());
        reviewAdminService.updateStatus(reviewId, "HIDDEN", null, OPERATOR_ID);

        assertThatThrownBy(() -> submit(BAD_CUSTOMER_ID, 5, "再评一次"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STATE_CONFLICT);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_review WHERE product_id = ?",
                Integer.class, PRODUCT_ID)).isEqualTo(1);
    }
}
