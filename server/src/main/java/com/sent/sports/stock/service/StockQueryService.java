package com.sent.sports.stock.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.stock.dto.admin.AdminStockDtos;

/**
 * 库存查询门面（P4-35 接口契约，跨模块可用）。
 * <p>列表与流水已接入真实分页查询；台账可用量按「实物 − 锁定 − 待发 − 冻结」统一计算，
 * 流水可按 SKU 与业务类型收窄（入库页看 INBOUND、报损页看 LOSS）。</p>
 */
public interface StockQueryService {

    /**
     * 库存列表：四口径 + 可用量 + 安全库存 + 分页；skuId 为空表示全部。
     *
     * @param warningOnly 只看「可用 <= 安全库存」的告急 SKU，条件由 SQL 下推，
     *                    不在服务层或前端过滤（否则单页上限会漏数据、总数也会错位）
     */
    PageResult<AdminStockDtos.StockRow> list(PageQuery pageQuery, Long skuId, boolean warningOnly);

    /** 库存流水列表 + 分页；bizType 为空表示全部业务类型。 */
    PageResult<AdminStockDtos.FlowRow> flows(PageQuery pageQuery, Long skuId, String bizType);

    /** 预警摘要：最紧急的一条告急 SKU；无预警时返回 skuId = 0 的「暂无预警」占位。 */
    AdminStockDtos.StockWarningSummary warningSummary();
}
