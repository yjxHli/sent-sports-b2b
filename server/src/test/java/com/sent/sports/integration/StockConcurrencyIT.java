package com.sent.sports.integration;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5-49 并发抢单：20 个线程抢最后 1 件，成功恒为 1、库存不为负、无重复订单。
 * <p>正确性完全来自 DB 条件更新 {@code UPDATE sku_stock SET locked_qty = locked_qty + qty
 * WHERE physical_qty - locked_qty - pending_qty - frozen_qty >= qty} 与行锁，
 * 不引入 Redis / 分布式锁（用户明确口径）。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class StockConcurrencyIT extends FullStackIntegrationTest {

    private static final long SKU_ID = 9002L;
    private static final int THREADS = 20;

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Test
    @DisplayName("P5-49：20 线程抢 1 件，成功=1，无负库存、无重复订单")
    void twentyThreadsCompeteForLastItem() throws Exception {
        seedSku(SKU_ID, 10_000L, 1);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                final String idemKey = "IDEM-RACE-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        createOrderUseCase.create(orderCmd(idemKey, SKU_ID, 1));
                        return "SUCCESS";
                    } catch (BizException e) {
                        return e.getErrorCode() == ErrorCode.STOCK_NOT_ENOUGH
                                ? "STOCK_NOT_ENOUGH" : "OTHER:" + e.getErrorCode();
                    }
                }));
            }
            start.countDown();
            int success = 0;
            int insufficient = 0;
            List<String> unexpected = new ArrayList<>();
            for (Future<String> f : futures) {
                String outcome = f.get(30, TimeUnit.SECONDS);
                if ("SUCCESS".equals(outcome)) {
                    success++;
                } else if ("STOCK_NOT_ENOUGH".equals(outcome)) {
                    insufficient++;
                } else {
                    unexpected.add(outcome);
                }
            }
            assertThat(unexpected).as("不应出现库存不足以外的异常").isEmpty();
            assertThat(success).isEqualTo(1);
            assertThat(insufficient).isEqualTo(THREADS - 1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(orderCount()).isEqualTo(1L);
        assertThat(physicalQtyOf(SKU_ID)).isEqualTo(1);
        assertThat(lockedQtyOf(SKU_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sku_stock WHERE sku_id = ? "
                        + "AND physical_qty - locked_qty - pending_qty - frozen_qty >= 0",
                Integer.class, SKU_ID)).isEqualTo(1);
        assertThat(stockFlowCount(SKU_ID, "LOCK")).as("只有成功的一单记 LOCK 流水").isEqualTo(1);
    }
}
