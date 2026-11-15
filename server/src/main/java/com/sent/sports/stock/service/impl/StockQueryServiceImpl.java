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
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public PageResult<AdminStockDtos.StockRow> list(PageQuery pageQuery, Long skuId,
                                                     boolean warningOnly) {
        Page<StockMapper.StockLedgerRow> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<StockMapper.StockLedgerRow> rows = stockMapper.selectStockPage(page, skuId, warningOnly);
        List<AdminStockDtos.StockRow> list = rows.getRecords().stream()
                .map(StockQueryServiceImpl::toStockRow)
                .toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public AdminStockDtos.StockWarningSummary warningSummary() {
        StockMapper.StockLedgerRow row = stockMapper.selectMostCriticalWarning();
        if (row == null) {
            return new AdminStockDtos.StockWarningSummary(0L, "暂无预警", 0, 0);
        }
        AdminStockDtos.StockRow stock = toStockRow(row);
        return new AdminStockDtos.StockWarningSummary(
                stock.skuId(), stock.skuName(), stock.availableQty(), stock.safeQty());
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

    /** 规格 JSON 的键值对：只取字符串值，跳过键名与转义引号。 */
    private static final Pattern SPEC_VALUE = Pattern.compile(
            "\"(?:[^\"\\\\]|\\\\.)*\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\s]+)");

    /**
     * 把 spec_json 拼成可读规格文本。
     * <p>老实现只切第一个冒号后面的整段，多键规格（如 {@code {"size":"XL","color":"红"}}）
     * 会把 {@code XL", "color": "红} 这种半截 JSON 拼进商品名。这里逐个取值用「 / 」连接；
     * 单键规格仍返回原值（与既有列表口径一致），不是对象 JSON 时原样兜底。</p>
     */
    private static String plainSpec(String json) {
        if (json == null || json.isBlank()) return "";
        Matcher matcher = SPEC_VALUE.matcher(json);
        StringJoiner parts = new StringJoiner(" / ");
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            value = value.replace("\\\"", "\"").replace("\\\\", "\\").trim();
            if (!value.isEmpty()) {
                parts.add(value);
            }
        }
        return parts.length() == 0 ? json.trim() : parts.toString();
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
