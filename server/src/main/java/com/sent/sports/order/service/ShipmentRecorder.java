package com.sent.sports.order.service;

import java.util.List;

/**
 * 发货落账门面（order 模块内部契约）：把「一次发货 = 一个包裹 + 若干明细」写进真库。
 * <p>用例只依赖这个窄接口，不直接碰 Mapper / 自增主键，单测可整体 Mock。</p>
 */
public interface ShipmentRecorder {

    /** @return 本次生成的包裹号 */
    String record(String orderNo, String carrier, String trackingNo, List<Line> lines);

    /** 包裹明细（中立参数，避免把 order 模块领域类型泄漏给持久层）。 */
    record Line(long skuId, int qty) {
    }
}
