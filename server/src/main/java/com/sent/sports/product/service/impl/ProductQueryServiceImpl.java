package com.sent.sports.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.JsonListCodec;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.CatalogDtos;
import com.sent.sports.product.mapper.CatalogMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.service.ProductQueryService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
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
    private final ProductMapper productMapper;
    private final Clock clock;

    public ProductQueryServiceImpl(CatalogMapper catalogMapper, ProductMapper productMapper, Clock clock) {
        this.catalogMapper = catalogMapper;
        this.productMapper = productMapper;
        this.clock = clock;
    }

    @Override
    public PageResult<CatalogDtos.ProductVO> search(CatalogDtos.ProductFilter filter, PageQuery pageQuery) {
        Page<CatalogMapper.ProductRow> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<CatalogMapper.ProductRow> rows = catalogMapper.selectProductPage(
                page, filter.categoryId(), filter.keyword());
        // 列表不查秒杀：一页 10 条就是 10 次额外查询，而列表卡片用不到秒杀快照
        List<CatalogDtos.ProductVO> list = rows.getRecords().stream()
                .map(row -> toProductVo(row, null)).toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public java.util.List<CatalogDtos.SuggestVO> suggest(String keyword, int limit) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return java.util.List.of();
        int size = Math.max(1, Math.min(limit, 20));
        return catalogMapper.selectSuggestions(kw, size).stream()
                .map(row -> new CatalogDtos.SuggestVO(String.valueOf(row.getId()), row.getTitle(),
                        row.getCategoryId(), row.getCategoryName()))
                .toList();
    }

    @Override
    public CatalogDtos.ProductVO detail(String productId) {
        CatalogMapper.ProductRow row = catalogMapper.selectProductById(parseId(productId));
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 详情页才查秒杀：展示价与结算价同源（同一个 SQL 口径 + 同一个 Clock）
        return toProductVo(row, LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
    }

    @Override
    public List<CatalogDtos.SkuVO> skus(String productId) {
        long spuId = parseId(productId);
        if (catalogMapper.selectProductById(spuId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 纯规格列表接口不返回秒杀价：详情接口已带，这里再查一次毫无意义
        return skuVos(spuId, List.of());
    }

    @Override
    public List<CatalogDtos.CategoryVO> categories() {
        return catalogMapper.selectCategories().stream()
                .map(c -> new CatalogDtos.CategoryVO(
                        String.valueOf(c.getId()), c.getName(),
                        c.getParentId() == null ? null : String.valueOf(c.getParentId()), List.of()))
                .toList();
    }

    @Override
    public java.util.Map<Long, Long> spuIdsOfSkus(java.util.List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return java.util.Map.of();
        }
        return catalogMapper.selectSkuSpuIds(skuIds).stream()
                .filter(row -> row.getSkuId() != null && row.getSpuId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        CatalogMapper.SkuSpuRow::getSkuId, CatalogMapper.SkuSpuRow::getSpuId,
                        (a, b) -> a));
    }

    /**
     * @param now 非空时才查秒杀快照（列表传 null，避免一页 N 次查询）
     */
    private CatalogDtos.ProductVO toProductVo(CatalogMapper.ProductRow row, LocalDateTime now) {
        // 富内容一律容错解析：库里可能存着历史脏 JSON，详情页不能因此 500
        List<String> images = JsonListCodec.read(row.getImagesJson(), String.class);
        // 主图缺省回退到图集第一张：老商品只有 images_json 时也不会出现空主图
        String mainImage = row.getMainImage();
        if (mainImage == null || mainImage.isBlank()) {
            mainImage = images.isEmpty() ? null : images.get(0);
        }
        // 一次查出该商品全部生效秒杀明细：既供 SKU 级展示，也供横幅取最低价
        List<ProductMapper.FlashSaleSkuRow> flashRows = now == null
                ? List.of()
                : productMapper.selectActiveFlashSaleSkus(row.getId(), now);
        return new CatalogDtos.ProductVO(
                String.valueOf(row.getId()), row.getTitle(), row.getCategoryId(), row.getCategoryName(),
                mainImage, row.getSalesCount(),
                "ON_SHELF".equals(row.getStatus()), skuVos(row.getId(), flashRows), row.getDetailHtml(),
                row.getSubtitle(), row.getBrand(), images,
                JsonListCodec.read(row.getDetailImagesJson(), String.class),
                JsonListCodec.read(row.getParamsJson(), CatalogDtos.ParamVO.class),
                JsonListCodec.read(row.getServiceTagsJson(), String.class),
                now == null ? null : flashSale(flashRows),
                canCustomize(row));
    }

    /**
     * 是否支持 UV 定制：库里是 {@code tinyint(1)}（0/1），列表 SQL 不取该列时为 null，
     * 一律按「不支持」处理，避免编辑页回显成 true。
     */
    private static boolean canCustomize(CatalogMapper.ProductRow row) {
        return Integer.valueOf(1).equals(row.getCanCustomize());
    }

    /**
     * 秒杀横幅快照：取活动内最低秒杀价那条（SQL 已按价格升序），原价用<b>同一行</b>的零售价，
     * 而不是全商品最低零售价——否则「¥9.9 秒杀（原价 ¥15）」会配上一个不存在的原价。
     * <p>无生效活动返回 null，前端据此整块不渲染。</p>
     */
    private CatalogDtos.FlashSaleVO flashSale(List<ProductMapper.FlashSaleSkuRow> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        ProductMapper.FlashSaleSkuRow row = rows.get(0);
        if (row.getPriceCent() == null) {
            return null;
        }
        long flashPrice = row.getPriceCent();
        long originPrice = row.getRetailPriceCent() == null ? flashPrice : row.getRetailPriceCent();
        return new CatalogDtos.FlashSaleVO(
                row.getActivityId() == null ? null : String.valueOf(row.getActivityId()),
                row.getTitle(), flashPrice, MoneyUtil.toYuan(flashPrice),
                originPrice, MoneyUtil.toYuan(originPrice),
                row.getStartAt() == null ? null : row.getStartAt().toString(),
                row.getEndAt() == null ? null : row.getEndAt().toString());
    }

    private List<CatalogDtos.SkuVO> skuVos(long spuId, List<ProductMapper.FlashSaleSkuRow> flashRows) {
        java.util.Map<Long, ProductMapper.FlashSaleSkuRow> flashBySku = new java.util.HashMap<>();
        for (ProductMapper.FlashSaleSkuRow flashRow : flashRows) {
            if (flashRow.getSkuId() != null) {
                flashBySku.put(flashRow.getSkuId(), flashRow);
            }
        }
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
            // 秒杀价只挂在真正参加活动的规格上，未参加的保持 null（详情页据此回落零售价）
            ProductMapper.FlashSaleSkuRow flash = flashBySku.get(s.getId());
            Long flashPriceCent = flash == null ? null : flash.getPriceCent();
            return new CatalogDtos.SkuVO(String.valueOf(s.getId()), skuNo, spec, qty,
                    "ON".equalsIgnoreCase(s.getStatus()), price, s.getImageFileId(),
                    flashPriceCent,
                    flashPriceCent == null ? null : MoneyUtil.toYuan(flashPriceCent));
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
