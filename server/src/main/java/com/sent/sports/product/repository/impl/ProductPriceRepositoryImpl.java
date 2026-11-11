package com.sent.sports.product.repository.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.product.domain.pricing.PriceContext;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.po.LadderPricePO;
import com.sent.sports.product.mapper.po.SkuPO;
import com.sent.sports.product.domain.pricing.LadderTier;
import com.sent.sports.product.repository.ProductPriceRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 取价上下文仓储（P5，真库）：从 {@code sku} / {@code ladder_price} 读取零售价、底价、成本与阶梯价，
 * 组装成定价领域需要的 {@link PriceContext}。一客一价、协议价由用例层按客户上下文另行叠加。
 */
@Repository
public class ProductPriceRepositoryImpl implements ProductPriceRepository {

    private final ProductMapper productMapper;

    public ProductPriceRepositoryImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public PriceContext loadContext(long skuId) {
        SkuPO sku = productMapper.selectSkuById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SKU 不存在");
        }
        // 下架商品不可定价/下单：状态非 ON 即视为已下架（与 CatalogMapper/ProductQueryServiceImpl 的过滤口径一致）。
        if (!"ON".equals(sku.getStatus())) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF, "商品已下架");
        }
        List<LadderTier> tiers = new ArrayList<>();
        long retail = sku.getRetailPriceCent() == null ? 0L : sku.getRetailPriceCent();
        for (LadderPricePO po : productMapper.selectLadderPrices(skuId)) {
            // 表内存绝对价（分），领域 LadderTier 存折扣率（百分比），按零售价折算；无零售价时视为不打折
            int rate = retail > 0 ? (int) (po.getPriceCent() * 100 / retail) : 100;
            int maxQty = po.getMaxQty() == null ? -1 : po.getMaxQty();
            tiers.add(new LadderTier(po.getMinQty(), maxQty, rate));
        }
        return PriceContext.builder()
                .retailPriceCent(sku.getRetailPriceCent())
                .floorPriceCent(sku.getFloorPriceCent())
                .costPriceCent(sku.getCostPriceCent())
                .ladderTiers(tiers)
                .qty(1)
                .build();
    }
}
