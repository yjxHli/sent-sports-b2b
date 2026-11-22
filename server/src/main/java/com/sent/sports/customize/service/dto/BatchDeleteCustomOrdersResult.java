package com.sent.sports.customize.service.dto;

import java.util.List;

/**
 * 我的定制「多选删除」结果（客户侧）。
 *
 * <p>与商品订单侧的同名结果（{@code order.service.dto.BatchDeleteOrdersResult}）结构一致但互不引用：
 * 两个模块各自持有自己的出参类型，跨模块依赖对方 dto 会在 ArchUnit「只经 service 接口」上踩线。</p>
 *
 * @param deleted 实际软删的条数（条件更新影响行数；重复提交为 0）
 * @param skipped 未删除的明细，逐条带原因，前端据此提示而不是整批报错
 */
public record BatchDeleteCustomOrdersResult(int deleted, List<Skipped> skipped) {

    /**
     * 一条没删掉的定制单。
     *
     * @param orderNo 定制单号（字段名沿用前端勾选值的通用叫法，商品订单侧同名）
     * @param reason  面向用户的原因文案
     */
    public record Skipped(String orderNo, String reason) {
    }
}
