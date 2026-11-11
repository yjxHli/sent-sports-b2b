package com.sent.sports.stock.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.stock.dto.admin.AdminStockDtos;
import com.sent.sports.stock.mapper.StockFlowMapper;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.mapper.po.StockFlowPO;
import com.sent.sports.stock.service.StockQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 库存查询实现（收口阶段）：列表与流水从空桩换成真分页。
 * <p>台账只存四口径，可用量在本层统一计算（实物 − 锁定 − 待发 − 冻结，下限 0），
 * 避免前端各页面各算一套；SKU 名称由联查的 SPU 标题 + 规格拼出。</p>
 */
@Service
public class StockQueryServiceImpl implements StockQueryService {

    private final StockMapper stockMapper;
    private final StockFlowMapper stockFlowMapper;

    public StockQueryServiceImpl(StockMapper stockMapper, StockFlowMapper stockFlowMapper) {
        this.stockMapper = stockMapper;
        this.stockFlowMapper = stockFlowMapper;
    }

    @Override
    public PageResult<AdminStockDtos.StockRow> list(PageQuery pageQuery, Long skuId) {
        Page<StockMapper.StockLedgerRow> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<StockMapper.StockLedgerRow> rows = stockMapper.selectStockPage(page, skuId);
        List<AdminStockDtos.StockRow> list = rows.getRecords().stream()
                .map(StockQueryServiceImpl::toStockRow)
                .toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public PageResult<AdminStockDtos.FlowRow> flows(PageQuery pageQuery, Long skuId, String bizType) {
        String normalizedBizType = (bizType == null || bizType.isBlank()) ? null : bizType.trim();
        Page<StockFlowPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<StockFlowPO> rows = stockFlowMapper.selectFlowPage(page, skuId, normalizedBizType);
        List<AdminStockDtos.FlowRow> list = rows.getRecords().stream()
                .map(StockQueryServiceImpl::toFlowRow)
                .toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    private static AdminStockDtos.StockRow toStockRow(StockMapper.StockLedgerRow row) {
        int physical = nvl(row.getPhysicalQty());
        int locked = nvl(row.getLockedQty());
        int pending = nvl(row.getPendingQty());
        int frozen = nvl(row.getFrozenQty());
        int safe = nvl(row.getSafetyQty());
        int available = Math.max(0, physical - locked - pending - frozen);
        String spec = plainSpec(row.getSpecJson());
        String name = buildSkuName(row.getProductTitle(), spec, row.getSkuNo(), row.getSkuId());
        return new AdminStockDtos.StockRow(
                row.getSkuId() == null ? 0L : row.getSkuId(), name,
                physical, locked, pending, frozen, available, safe, available <= safe);
    }

    private static AdminStockDtos.FlowRow toFlowRow(StockFlowPO po) {
        return new AdminStockDtos.FlowRow(
                po.getId() == null ? 0L : po.getId(),
                po.getBizType() == null ? "" : po.getBizType(),
                po.getBizNo() == null ? "" : po.getBizNo(),
                po.getSkuId() == null ? 0L : po.getSkuId(),
                nvl(po.getChangeQty()), nvl(po.getBeforeQty()), nvl(po.getAfterQty()),
                po.getRemark() == null ? "" : po.getRemark(),
                toMillis(po.getCreatedAt()));
    }

    private static String buildSkuName(String productTitle, String spec, String skuNo, Long skuId) {
        StringBuilder name = new StringBuilder();
        if (productTitle != null && !productTitle.isBlank()) {
            name.append(productTitle.trim());
        }
        if (!spec.isBlank()) {
            if (name.length() > 0) name.append(" · ");
            name.append(spec);
        }
        if (name.length() == 0 && skuNo != null && !skuNo.isBlank()) {
            name.append(skuNo.trim());
        }
        if (name.length() == 0) {
            name.append("SKU ").append(skuId == null ? "" : skuId);
        }
        return name.toString();
    }

    /** 取 spec_json 首个值（形如 {"spec":"一筒(12只)"} 或 {"color":"黑"}），解析失败时用编码兜底。 */
    private static String plainSpec(String json) {
        if (json == null || json.isBlank()) return "";
        String body = json.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
        int idx = body.indexOf(':');
        if (idx < 0) return body.trim();
        String value = body.substring(idx + 1).trim();
        if (value.startsWith("\"")) value = value.substring(1);
        if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);
        return value.replace("\\\"", "\"").replace("\\\\", "\\").trim();
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
