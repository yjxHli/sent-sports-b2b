package com.sent.sports.stock;

import com.sent.sports.stock.domain.StockCheck;
import com.sent.sports.stock.mapper.StockCheckMapper;
import com.sent.sports.stock.repository.impl.StockCheckRepositoryImpl;
import com.sent.sports.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 盘点单仓储真库集成测试（2026-11-28，P0）。
 *
 * <p>背景：修复前 {@code InMemoryStockCheckRepository} 用 {@code ConcurrentHashMap} 存盘点单，
 * 应用重启即全部丢失（审计报告 §2.1）。本类用「新建一个仓储实例再读」来模拟重启——
 * 新实例不带任何进程内状态，能读到数据才说明数据真的在库里，而不是在某个 Map 里。</p>
 *
 * <p>注意：{@code stock_check} 头表只有 {@code check_no/status/operator_id/approved_by}，
 * SKU、账面数、实盘数、差异值都在 {@code stock_check_item} 行表，
 * 所以每条断言都要同时覆盖「头」和「行」两侧，只验其一是假的绿。</p>
 */
@org.junit.jupiter.api.condition.EnabledIf("com.sent.sports.support.ItDatabase#available")
class StockCheckRepositoryIT extends BaseIntegrationTest {

    private static final long SKU_ID = 90001L;
    private static final long OPERATOR_ID = 7L;
    private static final String CHECK_NO = "CHK-IT-0001";

    @Autowired
    private StockCheckMapper stockCheckMapper;

    /** 每次 new 一个实例 = 模拟一次「重启后的新应用进程」，实例内不存在任何缓存。 */
    private StockCheckRepositoryImpl freshRepository() {
        return new StockCheckRepositoryImpl(stockCheckMapper);
    }

    @Test
    @DisplayName("新建盘点单：头与行都落库，换新仓储实例（等价重启）仍能读到")
    void persistedCheckSurvivesRepositoryRestart() {
        long now = System.currentTimeMillis();
        freshRepository().save(new StockCheck(CHECK_NO, SKU_ID, 100, OPERATOR_ID, now));

        // 1) 真的写进了两张表，而不是内存
        assertThat(countOf("stock_check")).as("盘点单头必须落 stock_check").isEqualTo(1);
        assertThat(countOf("stock_check_item")).as("盘点单行必须落 stock_check_item").isEqualTo(1);

        // 2) 全新实例读回：字段逐个对齐
        StockCheck reloaded = freshRepository().findByCheckId(CHECK_NO)
                .orElseThrow(() -> new AssertionError("重启后读不到盘点单，说明数据仍在内存"));

        assertThat(reloaded.getCheckNo()).isEqualTo(CHECK_NO);
        assertThat(reloaded.getSkuId()).isEqualTo(SKU_ID);
        assertThat(reloaded.getBookQty()).isEqualTo(100);
        assertThat(reloaded.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(reloaded.getStatus()).isEqualTo(StockCheck.DRAFT);
        // 未录实盘时领域用 -1 表示「还没数」，库里允许 NULL，读回必须还原成 -1 而不是 0
        assertThat(reloaded.getActualQty()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("录入实盘 + 审批：状态、实盘数、差异值都落库，重启后不变")
    void recordAndApproveArePersisted() {
        long now = System.currentTimeMillis();
        StockCheck check = new StockCheck(CHECK_NO, SKU_ID, 100, OPERATOR_ID, now);
        freshRepository().save(check);

        check.record(93, now + 1_000);
        freshRepository().save(check);

        StockCheck afterCount = freshRepository().findByCheckId(CHECK_NO).orElseThrow();
        assertThat(afterCount.getStatus()).as("录入实盘后应为 COUNTED").isEqualTo(StockCheck.COUNTED);
        assertThat(afterCount.getActualQty()).isEqualTo(93L);
        assertThat(afterCount.getDiffQty()).as("差异 = 实盘 - 账面 = -7").isEqualTo(-7L);

        check.approve(now + 2_000);
        freshRepository().save(check);

        StockCheck afterApprove = freshRepository().findByCheckId(CHECK_NO).orElseThrow();
        assertThat(afterApprove.getStatus()).as("审批后应为 APPROVED").isEqualTo(StockCheck.APPROVED);
        assertThat(afterApprove.getDiffQty()).isEqualTo(-7L);
        // 头表 approved_by 是审批留痕，审批后不能为空
        assertThat(jdbc.queryForObject(
                "SELECT approved_by FROM stock_check WHERE check_no = ?", Long.class, CHECK_NO))
                .as("审批人必须落库").isEqualTo(OPERATOR_ID);
    }

    @Test
    @DisplayName("同一盘点单反复 save 只更新不新增（靠 uk_check_no / uk_check_sku 防重）")
    void saveIsUpsertNotInsert() {
        long now = System.currentTimeMillis();
        StockCheck check = new StockCheck(CHECK_NO, SKU_ID, 100, OPERATOR_ID, now);
        freshRepository().save(check);
        check.record(100, now + 1);
        freshRepository().save(check);
        check.approve(now + 2);
        freshRepository().save(check);

        assertThat(countOf("stock_check")).as("三次 save 后头表只能有一行").isEqualTo(1);
        assertThat(countOf("stock_check_item")).as("三次 save 后行表只能有一行").isEqualTo(1);
    }

    @Test
    @DisplayName("查不存在的盘点单返回空，不抛异常")
    void missingCheckReturnsEmpty() {
        assertThat(freshRepository().findByCheckId("CHK-NOT-EXIST")).isEmpty();
    }
}
