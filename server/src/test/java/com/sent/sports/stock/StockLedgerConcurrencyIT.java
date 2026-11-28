package com.sent.sports.stock;

import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.mapper.po.SkuStockPO;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存台账并发集成测试（2026-11-28 批次 1，P0-05）。
 *
 * <p>复现的是审计里的超卖窗口：管理员入库（读-改-写，绝对值覆盖）与客户下单预占
 * （{@code locked_qty = locked_qty + n} 原子加）在同一行上交错。修复前覆盖写会把并发预占抹掉，
 * 可用量虚高 → 超卖；修复后 {@code save()} 在行锁内只改实物量，锁定/在途/冻结沿用锁内读数。</p>
 *
 * <p>为什么必须连真库：单元测试里的 mock 无法验证 InnoDB 行锁与 REPEATABLE READ 的交互，
 * 而这正是丢更新发生的土壤。</p>
 */
@org.junit.jupiter.api.condition.EnabledIf("com.sent.sports.support.ItDatabase#available")
class StockLedgerConcurrencyIT extends BaseIntegrationTest {

    private static final long SKU_ID = 91001L;
    private static final int INITIAL_PHYSICAL = 1000;
    private static final int LOCKERS = 4;
    private static final int LOCKS_PER_THREAD = 20;
    private static final int INBOUND_ROUNDS = 20;
    private static final int INBOUND_QTY = 2;

    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private StockMapper stockMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void seedLedger() {
        tx = new TransactionTemplate(transactionManager);
        stockMapper.insertIfAbsent(SKU_ID, 0);
        jdbc.update("UPDATE sku_stock SET physical_qty = ?, locked_qty = 0, pending_qty = 0, "
                        + "frozen_qty = 0, safety_qty = 10, version = 0 WHERE sku_id = ?",
                INITIAL_PHYSICAL, SKU_ID);
    }

    @Test
    @DisplayName("入库与并发预占交错：预占全部记账，不被绝对值覆盖（防超卖）")
    void inboundDoesNotClobberConcurrentLocks() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(LOCKERS + 1);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < LOCKERS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < LOCKS_PER_THREAD; j++) {
                    // 与真实下单同一入口：条件更新 +1，独立自动提交事务（最严苛的交错）
                    stockRepository.lock(SKU_ID, 1);
                }
                return null;
            }));
        }
        futures.add(pool.submit(() -> {
            start.await();
            for (int j = 0; j < INBOUND_ROUNDS; j++) {
                // 与 InboundUseCase 同构：@UseCase 事务内「读 - 改 - 绝对值落库」
                tx.executeWithoutResult(status -> {
                    StockItem item = stockRepository.findBySku(SKU_ID).orElseThrow();
                    item.inbound(INBOUND_QTY);
                    stockRepository.save(SKU_ID, item);
                });
            }
            return null;
        }));

        start.countDown();
        for (Future<?> future : futures) {
            future.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        SkuStockPO after = stockMapper.selectBySkuId(SKU_ID);
        assertThat(after.getLockedQty())
                .as("并发预占必须一条不丢：修复前入库的绝对值覆盖会把它们抹平")
                .isEqualTo(LOCKERS * LOCKS_PER_THREAD);
        assertThat(after.getPhysicalQty())
                .as("入库实物量按差值累加，不能被并发覆盖")
                .isEqualTo(INITIAL_PHYSICAL + INBOUND_ROUNDS * INBOUND_QTY);
    }
}
