package com.sent.sports.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.dto.TierDto;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.admin.AdminProductDtos;
import com.sent.sports.product.mapper.CatalogMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.SkuMapper;
import com.sent.sports.product.mapper.po.LadderPricePO;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.product.mapper.po.SkuPO;
import com.sent.sports.product.service.admin.ProductAdminService;
import com.sent.sports.stock.service.SkuStockInitService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 商品管理写侧：商品 / SKU 的增删改、阶梯价、一客一价全部真落库。
 * <p>商品编辑页保存 SKU 矩阵采用「按提交行全量替换」：带 skuId 的行更新，空 skuId 的行新增，
 * 本次未提交的既有行软下线。库存经 stock 模块 {@link SkuStockInitService} 服务端口同步，
 * 不直接跨模块写 {@code sku_stock}。</p>
 */
@Service
public class ProductAdminServiceImpl implements ProductAdminService {

    private static final String PRODUCT_ON_SHELF = "ON_SHELF";
    private static final String PRODUCT_DRAFT = "DRAFT";
    private static final String SKU_ON = "ON";
    private static final String SKU_OFF = "OFF";

    private final CatalogMapper catalogMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final SkuStockInitService skuStockInitService;
    private final IdGen idGen;
    private final Clock clock;

    public ProductAdminServiceImpl(CatalogMapper catalogMapper,
                                   ProductMapper productMapper,
                                   SkuMapper skuMapper,
                                   SkuStockInitService skuStockInitService,
                                   IdGen idGen,
                                   Clock clock) {
        this.catalogMapper = catalogMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.skuStockInitService = skuStockInitService;
        this.idGen = idGen;
        this.clock = clock;
    }

    @Override
    @UseCase
    public AdminProductDtos.ProductVO create(AdminProductDtos.ProductForm form) {
        ProductPO po = new ProductPO();
        po.setProductNo(idGen.next("SPU"));
        po.setTitle(form.name().trim());
        po.setCategoryId(form.categoryId());
        po.setDetailHtml(form.description());
        po.setStatus(PRODUCT_ON_SHELF);
        po.setPublishedAt(LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()));
        po.setCanCustomize(0);
        po.setSalesCount(0L);
        po.setDeleted(0);
        productMapper.insert(po);
        Long productId = po.getId();
        if (productId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品创建失败：未返回主键");
        }
        List<AdminProductDtos.SkuForm> skuForms = normalizeSkus(form.skus());
        replaceSkus(productId, skuForms);
        return new AdminProductDtos.ProductVO(String.valueOf(productId), po.getTitle(),
                po.getCategoryId(), true, skuForms.size(), null, 0);
    }

    @Override
    @UseCase
    public AdminProductDtos.ProductVO update(Long id, AdminProductDtos.ProductForm form) {
        requireId(id, "商品ID不能为空");
        ProductPO po = productMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在：" + id);
        }
        po.setTitle(form.name().trim());
        po.setCategoryId(form.categoryId());
        po.setDetailHtml(form.description());
        productMapper.updateById(po);
        List<AdminProductDtos.SkuForm> skuForms = normalizeSkus(form.skus());
        replaceSkus(id, skuForms);
        return new AdminProductDtos.ProductVO(String.valueOf(id), po.getTitle(), po.getCategoryId(),
                PRODUCT_ON_SHELF.equals(po.getStatus()), skuForms.size(), null, 0);
    }

    @Override
    @UseCase
    public void delete(Long id) {
        requireId(id, "商品ID不能为空");
        ProductPO po = productMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在：" + id);
        }
        productMapper.deleteById(id);
        for (SkuPO sku : activeSkus(id)) {
            if (sku.getId() != null) {
                deactivateSku(sku.getId());
            }
        }
    }

    @Override
    @UseCase
    public AdminProductDtos.ProductVO publish(Long id, boolean onShelf) {
        requireId(id, "商品ID不能为空");
        ProductPO po = productMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在：" + id);
        }
        productMapper.updateStatus(id, onShelf ? PRODUCT_ON_SHELF : PRODUCT_DRAFT);
        return new AdminProductDtos.ProductVO(String.valueOf(id), po.getTitle(), po.getCategoryId(),
                onShelf, activeSkus(id).size(), null, 0);
    }

    @Override
    @UseCase
    public AdminProductDtos.SkuVO addSku(AdminProductDtos.SkuForm form) {
        Long spuId = form.spuId();
        if (spuId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少所属商品ID");
        }
        if (productMapper.selectById(spuId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在：" + spuId);
        }
        String skuNo = resolveSkuNo(null, form.skuCode());
        SkuPO po = buildSku(spuId, null, form, skuNo);
        skuMapper.insert(po);
        Long skuId = po.getId();
        if (skuId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SKU 创建失败：未返回主键");
        }
        skuStockInitService.syncPhysicalQty(skuId, form.stock());
        return toSkuVO(po, form.stock());
    }

    @Override
    @UseCase
    public AdminProductDtos.SkuVO updateSku(Long id, AdminProductDtos.SkuForm form) {
        requireId(id, "SKU ID不能为空");
        SkuPO existing = skuMapper.selectById(id);
        if (existing == null || SKU_OFF.equalsIgnoreCase(existing.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "SKU 不存在：" + id);
        }
        if (existing.getSpuId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SKU 缺少所属商品：" + id);
        }
        String skuNo = resolveSkuNo(existing, form.skuCode());
        SkuPO po = buildSku(existing.getSpuId(), existing, form, skuNo);
        po.setId(id);
        skuMapper.updateById(po);
        skuStockInitService.syncPhysicalQty(id, form.stock());
        return toSkuVO(po, form.stock());
    }

    @Override
    @UseCase
    public void deleteSku(Long id) {
        requireId(id, "SKU ID不能为空");
        SkuPO po = skuMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SKU 不存在：" + id);
        }
        deactivateSku(id);
    }

    @Override
    @UseCase
    public void configLadder(Long skuId, List<TierDto> tiers) {
        // 空阶梯 = 清空折扣；不必再读 SKU，也避免 Mockito 之类的严格桩误触发
        productMapper.deleteLadderPrices(skuId);
        if (tiers == null || tiers.isEmpty()) {
            return;
        }
        SkuPO sku = productMapper.selectSkuById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SKU 不存在，无法配置阶梯价：" + skuId);
        }
        long retail = sku.getRetailPriceCent() == null ? 0L : sku.getRetailPriceCent();
        List<LadderPricePO> rows = tiers.stream().map(tier -> {
            LadderPricePO row = new LadderPricePO();
            row.setSkuId(skuId);
            row.setMinQty(tier.minQty());
            row.setMaxQty(tier.maxQty() < 0 ? null : tier.maxQty());
            row.setPriceCent(retail * tier.rate() / 100);
            return row;
        }).toList();
        productMapper.insertLadderPrices(rows);
    }

    @Override
    public List<TierDto> listLadderPrices(Long skuId) {
        SkuPO sku = productMapper.selectSkuById(skuId);
        if (sku == null) {
            return List.of();
        }
        long retail = sku.getRetailPriceCent() == null ? 0L : sku.getRetailPriceCent();
        return productMapper.selectLadderPrices(skuId).stream()
                .map(row -> new TierDto(
                        row.getMinQty() == null ? 1 : row.getMinQty(),
                        row.getMaxQty() == null ? -1 : row.getMaxQty(),
                        retail > 0 ? (int) (row.getPriceCent() * 100 / retail) : 100))
                .toList();
    }

    @Override
    @UseCase
    public AdminProductDtos.CustomerPriceVO upsertCustomerPrice(AdminProductDtos.CustomerPriceForm form) {
        productMapper.upsertCustomerPrice(form.customerId(), form.skuId(), form.priceCent(),
                toDateTime(form.validFromMillis()), toDateTime(form.validToMillis()));
        Long id = productMapper.selectCustomerPriceId(form.customerId(), form.skuId());
        List<AdminProductDtos.CustomerPriceVO> saved = listCustomerPrices(form.skuId(), form.customerId());
        if (!saved.isEmpty()) {
            return saved.get(0);
        }
        return new AdminProductDtos.CustomerPriceVO(id, form.skuId(), form.customerId(),
                form.priceCent(), form.validFromMillis(), form.validToMillis());
    }

    @Override
    @UseCase
    public void deleteCustomerPrice(Long id) {
        productMapper.deleteCustomerPrice(id);
    }

    @Override
    public List<AdminProductDtos.CustomerPriceVO> listCustomerPrices(Long skuId, Long customerId) {
        return productMapper.selectCustomerPrices(skuId, customerId).stream()
                .map(row -> new AdminProductDtos.CustomerPriceVO(
                        row.getId(), row.getSkuId(), row.getCustomerId(),
                        row.getPriceCent() == null ? 0L : row.getPriceCent(),
                        toMillis(row.getValidFrom()), toMillis(row.getValidTo()),
                        row.getCustomerName(), row.getCustomerPhone(),
                        row.getProductId(), row.getProductName(), row.getSkuCode(),
                        plainSpec(row.getSpecJson()),
                        row.getRetailPriceCent() == null ? 0L : row.getRetailPriceCent()))
                .toList();
    }

    @Override
    public PageResult<AdminProductDtos.ProductVO> list(PageQuery page, String keyword, Long categoryId,
                                                       Boolean onShelf) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String status = onShelf == null ? null : (onShelf ? PRODUCT_ON_SHELF : PRODUCT_DRAFT);
        Page<CatalogMapper.ProductRow> query = new Page<>(page.getPage(), page.getSize());
        IPage<CatalogMapper.ProductRow> rows = catalogMapper.selectAdminProductPage(
                query, categoryId, normalizedKeyword, status);
        List<AdminProductDtos.ProductVO> mapped = rows.getRecords().stream().map(row -> {
            List<CatalogMapper.SkuRow> skus = catalogMapper.selectSkusWithStock(row.getId());
            int discountSkuCount = (int) skus.stream()
                    .filter(s -> s.getLadderPriceCent() != null)
                    .count();
            return new AdminProductDtos.ProductVO(String.valueOf(row.getId()), row.getTitle(),
                    row.getCategoryId(), PRODUCT_ON_SHELF.equals(row.getStatus()),
                    skus.size(), row.getCategoryName(), discountSkuCount);
        }).toList();
        return PageResult.of(mapped, rows.getTotal(), page.getPage(), page.getSize());
    }

    private void replaceSkus(long spuId, List<AdminProductDtos.SkuForm> forms) {
        List<SkuPO> existingRows = activeSkus(spuId);
        Map<Long, SkuPO> byId = new HashMap<>();
        Map<String, SkuPO> byNo = new HashMap<>();
        for (SkuPO po : existingRows) {
            if (po.getId() != null) {
                byId.put(po.getId(), po);
            }
            if (po.getSkuNo() != null && !po.getSkuNo().isBlank()) {
                byNo.putIfAbsent(po.getSkuNo(), po);
            }
        }

        Set<Long> keptIds = new HashSet<>();
        for (AdminProductDtos.SkuForm form : forms) {
            SkuPO existing = null;
            if (form.skuId() != null) {
                existing = byId.get(form.skuId());
                if (existing == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "SKU 不属于当前商品：" + form.skuId());
                }
            } else {
                String code = trimToNull(form.skuCode());
                if (code != null) {
                    existing = byNo.get(code);
                }
            }
            String skuNo = resolveSkuNo(existing, form.skuCode());
            SkuPO po = buildSku(spuId, existing, form, skuNo);
            if (existing != null) {
                Long existingId = existing.getId();
                if (existingId == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "既有 SKU 缺少主键，无法更新");
                }
                po.setId(existingId);
                skuMapper.updateById(po);
                keptIds.add(existingId);
                skuStockInitService.syncPhysicalQty(existingId, form.stock());
            } else {
                skuMapper.insert(po);
                Long newId = po.getId();
                if (newId == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "SKU 创建失败：未返回主键");
                }
                keptIds.add(newId);
                skuStockInitService.syncPhysicalQty(newId, form.stock());
            }
        }

        for (SkuPO old : existingRows) {
            if (old.getId() != null && !keptIds.contains(old.getId())) {
                deactivateSku(old.getId());
            }
        }
    }

    private List<SkuPO> activeSkus(long spuId) {
        List<SkuPO> rows = productMapper.selectSkusBySpu(spuId);
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .filter(po -> !SKU_OFF.equalsIgnoreCase(po.getStatus()))
                .toList();
    }

    private String resolveSkuNo(SkuPO existing, String desired) {
        String code = trimToNull(desired);
        if (code != null) {
            SkuPO same = skuMapper.selectBySkuNo(code);
            if (same != null && (existing == null || !Objects.equals(same.getId(), existing.getId()))) {
                throw new BizException(ErrorCode.PARAM_INVALID, "SKU 编码已存在：" + code);
            }
            return code;
        }
        if (existing != null && existing.getSkuNo() != null && !existing.getSkuNo().isBlank()) {
            return existing.getSkuNo();
        }
        String generated = idGen.next("SKU");
        for (int i = 0; i < 5 && generated != null && skuMapper.selectBySkuNo(generated) != null; i++) {
            generated = idGen.next("SKU");
        }
        if (generated == null || skuMapper.selectBySkuNo(generated) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无法生成唯一 SKU 编码");
        }
        return generated;
    }

    private static SkuPO buildSku(long spuId, SkuPO existing, AdminProductDtos.SkuForm form, String skuNo) {
        SkuPO po = new SkuPO();
        if (existing != null) {
            po.setId(existing.getId());
        }
        po.setSpuId(spuId);
        po.setSkuNo(skuNo);
        po.setSpecJson(toSpecJson(form.spec()));
        po.setRetailPriceCent(form.retailPriceCent());
        po.setFloorPriceCent(0L);
        po.setCostPriceCent(0L);
        po.setWeightG(0);
        po.setStatus(SKU_ON);
        return po;
    }

    private void deactivateSku(long skuId) {
        skuMapper.markOff(skuId);
        skuMapper.deleteLadderPrices(skuId);
        skuMapper.deleteCustomerPrices(skuId);
    }

    private static AdminProductDtos.SkuVO toSkuVO(SkuPO po, int stock) {
        long retail = po.getRetailPriceCent() == null ? 0L : po.getRetailPriceCent();
        return new AdminProductDtos.SkuVO(String.valueOf(po.getId()), po.getSkuNo(),
                plainSpec(po.getSpecJson()), retail, stock);
    }

    private static List<AdminProductDtos.SkuForm> normalizeSkus(List<AdminProductDtos.SkuForm> skus) {
        return skus == null ? List.of() : skus.stream().filter(Objects::nonNull).toList();
    }

    private static void requireId(Long id, String message) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String toSpecJson(String spec) {
        String value = spec == null ? "" : spec.trim();
        if (value.isEmpty()) {
            return "{}";
        }
        String escaped = value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "{\"spec\":\"" + escaped + "\"}";
    }

    private static String plainSpec(String json) {
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
        int idx = body.indexOf(':');
        if (idx < 0) {
            return body.trim();
        }
        String value = body.substring(idx + 1).trim();
        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static LocalDateTime toDateTime(long millis) {
        return millis <= 0 ? null
                : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private static long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
