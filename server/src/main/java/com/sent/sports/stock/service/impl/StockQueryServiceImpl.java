package com.sent.sports.stock.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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

    /** 与 CreateOrderUseCase 同款静态 mapper：快照/规格是纯数据，不必注入 Spring 的 ObjectMapper。 */
    private static final ObjectMapper SPEC_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> SPEC_MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    /**
     * 把 spec_json 拼成可读规格文本（R16）。
     * <p>JSON 是递归文法，正则只能覆盖「扁平字符串值」：嵌套对象会拼出半个 {@code {"color":}、
     * 数组会拼出半个 {@code [}，null 会被当成字符串「null」。这里按 JSON 类型递归取值，
     * 用「 / 」连接；单键规格仍返回原值，非对象 JSON（历史脏串）原样兜底。</p>
     */
    private static String plainSpec(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            Map<String, Object> spec = SPEC_MAPPER.readValue(json, SPEC_MAP_TYPE);
            StringJoiner parts = new StringJoiner(" / ");
            spec.values().forEach(value -> appendSpecValue(parts, value));
            return parts.length() == 0 ? json.trim() : parts.toString();
        } catch (JsonProcessingException e) {
            return json.trim();
        }
    }

    /** 标量直接拼；嵌套 Map / List 保持键的声明顺序递归展开；null 跳过。 */
    private static void appendSpecValue(StringJoiner parts, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> nested) {
            nested.values().forEach(v -> appendSpecValue(parts, v));
        } else if (value instanceof List<?> list) {
            list.forEach(v -> appendSpecValue(parts, v));
        } else {
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                parts.add(text);
            }
        }
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
