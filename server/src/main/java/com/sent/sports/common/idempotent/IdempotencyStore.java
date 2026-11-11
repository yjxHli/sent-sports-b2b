package com.sent.sports.common.idempotent;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等存储端口（P5-29）：抽象 PROCESSING/DONE 状态与结果快照的存取。
 * <p>拦截器只依赖此接口（低耦合）；真实实现为 Redis（SET NX PROCESSING + SET DONE:{json}），
 * 单测用伪造实现替换。</p>
 */
public interface IdempotencyStore {

    /**
     * 尝试以 PROCESSING 态占用键（等价 Redis {@code SET key PROCESSING NX EX ttl}）。
     *
     * @return true=首次占用成功；false=键已存在
     */
    boolean tryStart(String key, Duration ttl);

    /** 键当前是否为 PROCESSING 态。 */
    boolean isProcessing(String key);

    /** 若键为 DONE 态，返回首次响应 JSON 快照。 */
    Optional<String> doneResult(String key);

    /** 写入 DONE 态与响应快照（覆盖 PROCESSING），保留 ttl。 */
    void complete(String key, String responseJson, Duration ttl);

    /** 删除键（业务异常允许重试时调用）。 */
    void release(String key);
}
