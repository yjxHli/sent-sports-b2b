package com.sent.sports.product.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.CatalogDtos;

/**
 * 商品查询门面（P4-08 接口契约，跨模块可用）。
 * <p>商品/SKU 实体与仓储尚未建设（属 P2 领域 / P5 DB 范畴），当前由
 * {@code ProductQueryServiceImpl} 桩实现占位；Phase 5 接入实体、取价门面
 * （{@link PricingServiceFacade}）与数据范围（@DataScope）后替换。契约（接口签名）保持稳定的。</p>
 */
public interface ProductQueryService {

    /** 列表：按分类/关键字筛选 + 分页，按当前身份取价。 */
    PageResult<CatalogDtos.ProductVO> search(CatalogDtos.ProductFilter filter, PageQuery pageQuery);

    /** 详情：含 SKU 与按身份得出的价格。 */
    CatalogDtos.ProductVO detail(String productId);

    /** 指定商品的 SKU 与可售库存。 */
    java.util.List<CatalogDtos.SkuVO> skus(String productId);

    /** 分类树。 */
    java.util.List<CatalogDtos.CategoryVO> categories();
}
