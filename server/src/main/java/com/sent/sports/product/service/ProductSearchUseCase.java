package com.sent.sports.product.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.SensitiveWordFilter;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.CatalogDtos;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品搜索合规用例：在所有公开搜索入口之前查一次 DFA 词库。
 *
 * <p>为什么放在 use case 而不是 Controller：搜索列表与搜索联想是两个端点，
 * 规则必须只有一处装配点；Controller 只做 HTTP 参数绑定，业务拒绝统一由
 * {@link BizException} 走 {@code GlobalExceptionHandler} 返回 40004。</p>
 *
 * <p>命中后不查库、不返回任何商品建议——前端据此渲染专门的合规空状态，
 * 绝不能退化成「没有符合条件的商品」，那等于把违规词当普通空结果。</p>
 */
@Service
public class ProductSearchUseCase {

    private final ProductQueryService productQueryService;
    private final SensitiveWordFilter sensitiveWordFilter;

    public ProductSearchUseCase(ProductQueryService productQueryService,
                                SensitiveWordFilter sensitiveWordFilter) {
        this.productQueryService = productQueryService;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    /** 商品列表搜索：违禁词直接 40004，正常词原样下沉到查询门面。 */
    @UseCase
    public PageResult<CatalogDtos.ProductVO> search(CatalogDtos.ProductFilter filter, PageQuery pageQuery) {
        if (filter != null) {
            requireSafeKeyword(filter.keyword());
        }
        return productQueryService.search(filter, pageQuery);
    }

    /** 首页搜索联想：与列表同一套词库口径，避免「列表拦了、下拉还给出商品」。 */
    @UseCase
    public List<CatalogDtos.SuggestVO> suggest(String keyword, int limit) {
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            requireSafeKeyword(kw);
        }
        return productQueryService.suggest(keyword, limit);
    }

    /** 命中即抛业务异常，提示语与前端合规空状态共用同一句。 */
    private void requireSafeKeyword(String keyword) {
        if (sensitiveWordFilter.contains(keyword)) {
            throw new BizException(ErrorCode.ILLEGAL_SEARCH_KEYWORD);
        }
    }
}
