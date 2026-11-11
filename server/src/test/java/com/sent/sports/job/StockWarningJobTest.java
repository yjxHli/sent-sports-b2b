package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.mapper.po.SkuStockPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** 库存预警任务单测（P5-44）：返回预警 SKU 数、异常跳过本轮、无数据返回 0。 */
@ExtendWith(MockitoExtension.class)
class StockWarningJobTest {

    @Mock
    private StockMapper stockMapper;

    private StockWarningJob job;

    @BeforeEach
    void setUp() {
        job = new StockWarningJob(stockMapper,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("统计到 N 个低于安全库存的 SKU")
    void countsBelowSafetySkus() {
        when(stockMapper.selectBelowSafety())
                .thenReturn(List.of(new SkuStockPO(), new SkuStockPO(), new SkuStockPO()));

        assertThat(job.runOnce()).isEqualTo(3);
    }

    @Test
    @DisplayName("查询抛业务异常时跳过本轮")
    void skipsRoundOnBizException() {
        when(stockMapper.selectBelowSafety())
                .thenThrow(new BizException(ErrorCode.SYSTEM_ERROR));

        assertThat(job.runOnce()).isZero();
    }

    @Test
    @DisplayName("没有预警 SKU 时返回 0")
    void noopWhenNothingBelowSafety() {
        when(stockMapper.selectBelowSafety()).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();
    }
}
