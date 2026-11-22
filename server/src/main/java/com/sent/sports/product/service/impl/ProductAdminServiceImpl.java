package com.sent.sports.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.dto.TierDto;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.util.JsonListCodec;
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
import java.util.ArrayList;
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

    /** 富内容上限（V54）：与《商品详情页改造方案》§4.2 一致。 */
    private static final int MAX_IMAGES = 8;
    private static final int MAX_DETAIL_IMAGES = 20;
    private static final int MAX_PARAMS = 20;
    private static final int MAX_SERVICE_TAGS = 6;
    private static final int MAX_FILE_ID_LENGTH = 512;
    private static final int MAX_SUBTITLE_LENGTH = 255;
    private static final int MAX_PARAM_LABEL_LENGTH = 32;
    private static final int MAX_PARAM_VALUE_LENGTH = 128;
    private static final int MAX_SERVICE_TAG_LENGTH = 16;

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
        RichContent rich = validateRichContent(form);
        ProductPO po = new ProductPO();
        po.setProductNo(idGen.next("SPU"));
        po.setTitle(form.name().trim());
        po.setSubtitle(rich.subtitle());
        po.setCategoryId(form.categoryId());
        po.setBrand(rich.brand());
        po.setMainImage(rich.mainImage());
        po.setImagesJson(JsonListCodec.write(rich.images()));
        po.setDetailImagesJson(JsonListCodec.write(rich.detailImages()));
        po.setDetailHtml(form.description());
        po.setParamsJson(JsonListCodec.write(rich.params()));
        po.setServiceTagsJson(JsonListCodec.write(rich.serviceTags()));
        po.setStatus(PRODUCT_ON_SHELF);
        po.setPublishedAt(LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()));
        po.setCanCustomize(rich.canCustomize());
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
        RichContent rich = validateRichContent(form);
        ProductPO po = productMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在：" + id);
        }
        po.setTitle(form.name().trim());
        po.setSubtitle(rich.subtitle());
        po.setCategoryId(form.categoryId());
        po.setBrand(rich.brand());
        po.setDetailHtml(form.description());
        // 富内容三态语义：null = 不改（保留库内旧值）；空数组 = 清空（必须写 "[]"，
        // 因为 MyBatis-Plus updateById 会跳过 null，写 null 等于"删不掉"）。
        if (form.mainImage() != null) {
            po.setMainImage(rich.mainImage());
        }
        if (form.images() != null) {
            po.setImagesJson(JsonListCodec.write(rich.images()));
            // 图集清空时主图要一起清：写 null 会被 updateById 跳过，于是详情页还挂着已删掉的图
            if (rich.images().isEmpty()) {
                po.setMainImage("");
            } else if (form.mainImage() == null) {
                po.setMainImage(rich.images().get(0));
            }
        }
        if (form.detailImages() != null) {
            po.setDetailImagesJson(JsonListCodec.write(rich.detailImages()));
        }
        if (form.params() != null) {
            po.setParamsJson(JsonListCodec.write(rich.params()));
        }
        if (form.serviceTags() != null) {
            po.setServiceTagsJson(JsonListCodec.write(rich.serviceTags()));
        }
        if (form.canCustomize() != null) {
            po.setCanCustomize(rich.canCustomize());
        }
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
        String imageFileId = trimToNull(form.imageFileId());
        if (imageFileId != null && imageFileId.length() > MAX_FILE_ID_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "款式图地址过长（最多 " + MAX_FILE_ID_LENGTH + " 字符）");
        }
        po.setImageFileId(imageFileId);
        po.setRetailPriceCent(form.retailPriceCent());
        // 成本价/底价不在编辑页表单里（由商品主数据维护），更新既有 SKU 时必须原样带回：
        // 之前一律写 0，导致「改个副标题保存」就把全店 SKU 变成零成本零底价，
        // 报表毛利失真、秒杀底价校验也形同失效（2026-11-21 由 price-repricing E2E 抓出）。
        po.setFloorPriceCent(existing == null || existing.getFloorPriceCent() == null
                ? 0L : existing.getFloorPriceCent());
        po.setCostPriceCent(existing == null || existing.getCostPriceCent() == null
                ? 0L : existing.getCostPriceCent());
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

    /** 校验并归一化后的富内容，避免 create/update 里散落一堆局部变量。 */
    private record RichContent(String subtitle, String brand, String mainImage, int canCustomize,
                               List<String> images, List<String> detailImages,
                               List<AdminProductDtos.ParamItem> params, List<String> serviceTags) {
    }

    /**
     * 富内容校验 + 归一化（V54）。所有上限在此收口，越界一律抛 {@code PARAM_INVALID}，
     * 不做静默截断——运营填了 9 张图必须被明确告知，而不是悄悄丢掉一张。
     */
    private static RichContent validateRichContent(AdminProductDtos.ProductForm form) {
        String subtitle = trimToNull(form.subtitle());
        if (subtitle != null && subtitle.length() > MAX_SUBTITLE_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "副标题最多 " + MAX_SUBTITLE_LENGTH + " 字");
        }
        String brand = trimToNull(form.brand());
        if (brand != null && brand.length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID, "品牌名最多 64 字");
        }
        List<String> images = normalizeFiles(form.images(), MAX_IMAGES, "商品图");
        List<String> detailImages = normalizeFiles(form.detailImages(), MAX_DETAIL_IMAGES, "详情图");
        List<AdminProductDtos.ParamItem> params = normalizeParams(form.params());
        List<String> serviceTags = normalizeServiceTags(form.serviceTags());
        String mainImage = trimToNull(form.mainImage());
        // 主图缺省取图集第一张：运营只传图集时也不会出现"无主图"的空列表页卡片
        if (mainImage == null && !images.isEmpty()) {
            mainImage = images.get(0);
        }
        int canCustomize = Boolean.TRUE.equals(form.canCustomize()) ? 1 : 0;
        return new RichContent(subtitle, brand, mainImage, canCustomize,
                images, detailImages, params, serviceTags);
    }

    /** 图片 fileId 列表：去空白、去重（保序）、限长、限量。 */
    private static List<String> normalizeFiles(List<String> values, int max, String label) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String fileId = trimToNull(value);
            if (fileId == null) {
                continue;
            }
            if (fileId.length() > MAX_FILE_ID_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        label + "地址过长（最多 " + MAX_FILE_ID_LENGTH + " 字符）");
            }
            if (!result.contains(fileId)) {
                result.add(fileId);
            }
        }
        if (result.size() > max) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "最多 " + max + " 张");
        }
        return result;
    }

    private static List<AdminProductDtos.ParamItem> normalizeParams(List<AdminProductDtos.ParamItem> values) {
        if (values == null) {
            return List.of();
        }
        List<AdminProductDtos.ParamItem> result = new ArrayList<>();
        for (AdminProductDtos.ParamItem item : values) {
            if (item == null) {
                continue;
            }
            String label = trimToNull(item.label());
            if (label == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "参数名不能为空");
            }
            if (label.length() > MAX_PARAM_LABEL_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "参数名最多 " + MAX_PARAM_LABEL_LENGTH + " 字：" + label);
            }
            String value = trimToNull(item.value());
            if (value != null && value.length() > MAX_PARAM_VALUE_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "参数值最多 " + MAX_PARAM_VALUE_LENGTH + " 字：" + label);
            }
            result.add(new AdminProductDtos.ParamItem(label, value));
        }
        if (result.size() > MAX_PARAMS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数最多 " + MAX_PARAMS + " 条");
        }
        return result;
    }

    private static List<String> normalizeServiceTags(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String tag = trimToNull(value);
            if (tag == null) {
                continue;
            }
            if (tag.length() > MAX_SERVICE_TAG_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "服务承诺单个最多 " + MAX_SERVICE_TAG_LENGTH + " 字：" + tag);
            }
            if (!result.contains(tag)) {
                result.add(tag);
            }
        }
        if (result.size() > MAX_SERVICE_TAGS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "服务承诺最多 " + MAX_SERVICE_TAGS + " 个");
        }
        return result;
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
