package com.sent.sports.product.controller;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.product.dto.CatalogDtos;
import com.sent.sports.product.service.ProductQueryService;
import com.sent.sports.product.service.ProductSearchUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品浏览控制器（P4-10）：列表 / 详情 / SKU / 分类树。
 * <p>公开端点（plan §8.4 公开），不标 {@code @RequirePerm}。依赖同模块查询门面
 * {@link ProductQueryService}（当前为桩实现，Phase 5 接入实体与取价）。
 * 按身份给价依赖 {@code @CurrentUserArg}（Phase 5），本阶段仅校验零售价结构。</p>
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final ProductQueryService productQueryService;
    private final ProductSearchUseCase productSearchUseCase;

    public CatalogController(ProductQueryService productQueryService,
                             ProductSearchUseCase productSearchUseCase) {
        this.productQueryService = productQueryService;
        this.productSearchUseCase = productSearchUseCase;
    }

    @GetMapping("/products")
    public R<PageResult<CatalogDtos.ProductVO>> listProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @ModelAttribute PageQuery page) {
        CatalogDtos.ProductFilter filter = new CatalogDtos.ProductFilter(categoryId, keyword);
        return R.ok(productSearchUseCase.search(filter, page));
    }

    /**
     * 首页搜索联想：必须声明在 {@code /products/{productId}} 之前，
     * 否则 "suggest" 会被路径变量吞掉，被当成商品 id 查库报 NOT_FOUND。
     */
    @GetMapping("/products/suggest")
    public R<List<CatalogDtos.SuggestVO>> suggest(@RequestParam String keyword,
                                                  @RequestParam(defaultValue = "10") int limit) {
        return R.ok(productSearchUseCase.suggest(keyword, limit));
    }

    @GetMapping("/products/{productId}")
    public R<CatalogDtos.ProductVO> detail(@PathVariable String productId) {
        return R.ok(productQueryService.detail(productId));
    }

    @GetMapping("/products/{productId}/skus")
    public R<List<CatalogDtos.SkuVO>> skus(@PathVariable String productId) {
        return R.ok(productQueryService.skus(productId));
    }

    @GetMapping("/categories")
    public R<List<CatalogDtos.CategoryVO>> categories() {
        return R.ok(productQueryService.categories());
    }
}
