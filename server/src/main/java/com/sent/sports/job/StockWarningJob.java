package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.mapper.po.SkuStockPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存预警（P5-44）：读取低于安全库存的 SKU 清单，返回预警 SKU 数并打日志。
 * <p>纯只读统计、不写数据，天然幂等、无需分布式锁；
 * 单轮查询若抛 {@link BizException} 只记录并跳过本轮，不冒泡到调度器，保证「不中断整轮」。
 */
@Component
public class StockWarningJob {

    private static final Logger log = LoggerFactory.getLogger(StockWarningJob.class);

    private final StockMapper stockMapper;
    private final Clock clock;

    public StockWarningJob(StockMapper stockMapper, Clock clock) {
        this.stockMapper = stockMapper;
        this.clock = clock;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.stock-warning-ms:60000}")
    public void scheduled() {
        runOnce();
    }

    /** 返回本轮预警 SKU 数并写日志（便于单测断言）。 */
    public int runOnce() {
        try {
            List<SkuStockPO> belowSafety = stockMapper.selectBelowSafety();
            int count = belowSafety.size();
            if (count > 0) {
                log.info("库存预警：{} 共 {} 个 SKU 低于安全库存", LocalDateTime.now(clock), count);
            }
            return count;
        } catch (BizException e) {
            log.debug("库存预警查询被业务异常打断，跳过本轮：{}", e.getMessage());
            return 0;
        }
    }
}
