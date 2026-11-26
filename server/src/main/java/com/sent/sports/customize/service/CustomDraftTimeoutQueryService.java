package com.sent.sports.customize.service;

import com.sent.sports.customize.service.dto.CustomDraftWaiting;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图稿上传超时任务专用的只读查询口（跨模块契约）。
 *
 * <p>只暴露中立的 {@link CustomDraftWaiting}，不把 mapper / PO 泄漏给 job 模块，
 * 满足 ArchUnit 规则 4「跨模块只经对方 service」。</p>
 */
public interface CustomDraftTimeoutQueryService {

    /**
     * 取「待上传图稿 + 订单已支付」的定制单，按支付时间升序（最早快超时的排前面）。
     *
     * @param paidFrom 存量豁免线：支付时间早于它的一律不返回（规则上线前的老单不追溯取消）
     * @param limit    单轮上限，避免定时任务长时间占用调度线程
     */
    List<CustomDraftWaiting> findWaitingDrafts(LocalDateTime paidFrom, int limit);
}
