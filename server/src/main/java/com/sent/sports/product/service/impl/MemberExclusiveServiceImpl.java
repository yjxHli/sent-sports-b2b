package com.sent.sports.product.service.impl;

import com.sent.sports.product.mapper.MemberExclusiveMapper;
import com.sent.sports.product.mapper.po.ExclusiveProductRow;
import com.sent.sports.product.service.MemberExclusiveService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员专属商品实现（2026-11-16）。
 *
 * <p>合并规则：</p>
 * <ol>
 *   <li>一客一价的商品排前面——那是真正谈过的价，比「够等级就能买」更有说服力；</li>
 *   <li>等级专属商品按 {@code min_visible_level} 与当前等级序比较，够门槛才进列表；</li>
 *   <li>同一商品两边都命中时只保留一客一价那一行（价格更低、来源更硬）。</li>
 * </ol>
 * <p>未登录（customerId&lt;=0）时只剩等级专属，一客一价查询直接跳过。</p>
 */
@Service
public class MemberExclusiveServiceImpl implements MemberExclusiveService {

    /** 会员中心只展示一屏，超过 12 条没有意义（页面是 5 列网格，最多两行半）。 */
    private static final int MAX_ROWS = 12;

    /** 等级序：与 member 模块 {@code MemberTier} 的门槛顺序一致，0 表示无等级门槛。 */
    private static final Map<String, Integer> LEVEL_RANK = Map.of(
            "LV1", 1,
            "LV2", 2,
            "LV3", 3);

    private final MemberExclusiveMapper exclusiveMapper;

    public MemberExclusiveServiceImpl(MemberExclusiveMapper exclusiveMapper) {
        this.exclusiveMapper = exclusiveMapper;
    }

    @Override
    public List<ExclusiveProduct> listFor(long customerId, int levelRank) {
        Map<Long, ExclusiveProduct> merged = new LinkedHashMap<>();
        if (customerId > 0) {
            List<ExclusiveProductRow> agreements = exclusiveMapper.selectAgreementProducts(customerId);
            if (agreements != null) {
                for (ExclusiveProductRow row : agreements) {
                    putIfAbsent(merged, row);
                }
            }
        }
        List<ExclusiveProductRow> levels = exclusiveMapper.selectLevelProducts();
        if (levels != null) {
            for (ExclusiveProductRow row : levels) {
                if (rankOf(row.getMinLevel()) <= 0 || rankOf(row.getMinLevel()) > levelRank) {
                    continue;
                }
                putIfAbsent(merged, row);
            }
        }
        List<ExclusiveProduct> all = new ArrayList<>(merged.values());
        return all.size() > MAX_ROWS ? List.copyOf(all.subList(0, MAX_ROWS)) : List.copyOf(all);
    }

    private void putIfAbsent(Map<Long, ExclusiveProduct> merged, ExclusiveProductRow row) {
        if (row == null || row.getProductId() == null) {
            return;
        }
        merged.putIfAbsent(row.getProductId(), new ExclusiveProduct(
                row.getProductId(),
                row.getProductName() == null ? "会员专属商品" : row.getProductName(),
                row.getMainImage(),
                row.getPriceCent() == null ? 0L : row.getPriceCent(),
                row.getSource() == null ? "LEVEL" : row.getSource()));
    }

    private int rankOf(String level) {
        if (level == null || level.isBlank()) {
            return 0;
        }
        Integer rank = LEVEL_RANK.get(level.trim().toUpperCase(java.util.Locale.ROOT));
        return rank == null ? 0 : rank;
    }
}
