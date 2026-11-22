package com.sent.sports.order.service.dto;

import java.util.List;

/**
 * 我的订单「多选删除」结果（中立出参）。
 *
 * @param deleted 实际打上软删标记的条数（已删过的重复提交按 0 返回，不报错）
 * @param skipped 被跳过的单号与原因：在途订单、他人/不存在的订单
 */
public record BatchDeleteOrdersResult(int deleted, List<Skipped> skipped) {

    /** 单条跳过明细：原因直接是给用户看的中文，前端拼一句提示即可，不必再维护一份码表。 */
    public record Skipped(String orderNo, String reason) {
    }
}
