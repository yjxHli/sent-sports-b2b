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

    /** 库存列表：四口径 + 可用量 + 安全库存 + 分页；skuId 为空表示全部。 */
    PageResult<AdminStockDtos.StockRow> list(PageQuery pageQuery, Long skuId);

    /** 库存流水列表 + 分页；bizType 为空表示全部业务类型。 */
    PageResult<AdminStockDtos.FlowRow> flows(PageQuery pageQuery, Long skuId, String bizType);
}
