package com.sent.sports.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.CatalogDtos;
import com.sent.sports.product.mapper.CatalogMapper;
import com.sent.sports.product.service.ProductQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 前台商品目录查询（任务 2）：从 P4 空桩换成 MyBatis-Plus 真分页查询。
 *
 * <p>列表/详情读 {@code product} 与 {@code product_category}，SKU 读 {@code sku} 并联
 * {@code sku_stock} 计算可售库存；价格一律用零售价（分）并附元字符串，符合 §8.1 金额口径。</p>
 */
@Service
public class ProductQueryServiceImpl implements ProductQueryService {

    private final CatalogMapper catalogMapper;

    public ProductQueryServiceImpl(CatalogMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @Override
    public PageResult<CatalogDtos.ProductVO> search(CatalogDtos.ProductFilter filter, PageQuery pageQuery) {
        Page<CatalogMapper.ProductRow> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<CatalogMapper.ProductRow> rows = catalogMapper.selectProductPage(
                page, filter.categoryId(), filter.keyword());
        List<CatalogDtos.ProductVO> list = rows.getRecords().stream().map(this::toProductVo).toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public CatalogDtos.ProductVO detail(String productId) {
        CatalogMapper.ProductRow row = catalogMapper.selectProductById(parseId(productId));
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toProductVo(row);
    }

    @Override
    public List<CatalogDtos.SkuVO> skus(String productId) {
        long spuId = parseId(productId);
        if (catalogMapper.selectProductById(spuId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return skuVos(spuId);
    }

    @Override
    public List<CatalogDtos.CategoryVO> categories() {
        return catalogMapper.selectCategories().stream()
                .map(c -> new CatalogDtos.CategoryVO(
                        String.valueOf(c.getId()), c.getName(),
                        c.getParentId() == null ? null : String.valueOf(c.getParentId()), List.of()))
                .toList();
    }

    private CatalogDtos.ProductVO toProductVo(CatalogMapper.ProductRow row) {
        return new CatalogDtos.ProductVO(
                String.valueOf(row.getId()), row.getTitle(), row.getCategoryId(), row.getCategoryName(),
                row.getMainImage(), row.getSalesCount(),
                "ON_SHELF".equals(row.getStatus()), skuVos(row.getId()), row.getDetailHtml());
    }

    private List<CatalogDtos.SkuVO> skuVos(long spuId) {
        return catalogMapper.selectSkusWithStock(spuId).stream().map(s -> {
            long cent = s.getRetailPriceCent() == null ? 0L : s.getRetailPriceCent();
            Long ladder = s.getLadderPriceCent();
            CatalogDtos.PriceVO price = new CatalogDtos.PriceVO(
                    cent, MoneyUtil.toYuan(cent), null, null, null, null,
                    ladder, ladder == null ? null : MoneyUtil.toYuan(ladder));
            int qty = s.getAvailableQty() == null ? 0 : Math.max(0, s.getAvailableQty());
            String skuNo = s.getSkuNo() == null || s.getSkuNo().isBlank()
                    ? String.valueOf(s.getId())
                    : s.getSkuNo();
            String spec = formatSpec(s.getSpecJson());
            if (spec.isBlank()) {
                spec = skuNo;
            }
            return new CatalogDtos.SkuVO(String.valueOf(s.getId()), skuNo, spec, qty,
                    "ON".equalsIgnoreCase(s.getStatus()), price);
        }).toList();
    }

    /** 把 {@code {"color":"黑","size":"M"}} 展平为「黑 / M」，便于前端直接展示规格。 */
    private static String formatSpec(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        String body = json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        List<String> values = new java.util.ArrayList<>();
        for (String pair : body.split(",")) {
            int idx = pair.indexOf(':');
            if (idx < 0) {
                continue;
            }
            String v = pair.substring(idx + 1).trim();
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length() - 1);
            }
            if (!v.isBlank()) {
                values.add(v);
            }
        }
        return String.join(" / ", values);
    }

    private long parseId(String productId) {
        try {
            return Long.parseLong(productId);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
    }
}
