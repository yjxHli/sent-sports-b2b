package com.sent.sports.product;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.SensitiveWordFilter;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.CatalogDtos;
import com.sent.sports.product.service.ProductQueryService;
import com.sent.sports.product.service.ProductSearchUseCase;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商品搜索合规拦截（TDD 红）。
 *
 * <p>搜索不是「报错白屏」场景：命中违禁词必须抛 40004，由前端渲染专门的合规空状态；
 * 正常词（篮球）必须原样下沉到查询门面，一条 SQL 都不能少执行。</p>
 */
class ProductSearchUseCaseTest extends BaseUnitTest {

    @Mock
    private ProductQueryService productQueryService;

    @Mock
    private SensitiveWordFilter sensitiveWordFilter;

    @InjectMocks
    private ProductSearchUseCase useCase;

    @Test
    @DisplayName("搜索词含违禁词（毒品）：抛 ILLEGAL_SEARCH_KEYWORD 40004，完全不查库")
    void illegalKeywordRejectedBeforeQuery() {
        when(sensitiveWordFilter.contains("毒品")).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> useCase.search(new CatalogDtos.ProductFilter(null, "毒品"), new PageQuery()));

        assertEquals(ErrorCode.ILLEGAL_SEARCH_KEYWORD, ex.getErrorCode());
        verifyNoInteractions(productQueryService);
    }

    @Test
    @DisplayName("正常搜索词（篮球）：原样下沉到查询门面，结果原样返回")
    void normalKeywordDelegates() {
        CatalogDtos.ProductVO vo = new CatalogDtos.ProductVO(
                "9101", "斯伯丁篮球", 3L, "篮球", null, 0L, true, List.of());
        PageResult<CatalogDtos.ProductVO> expected = PageResult.of(List.of(vo), 1, 1, 12);
        CatalogDtos.ProductFilter filter = new CatalogDtos.ProductFilter(null, "篮球");
        PageQuery page = new PageQuery();
        when(productQueryService.search(filter, page)).thenReturn(expected);

        assertEquals(expected, useCase.search(filter, page));
        verify(productQueryService).search(filter, page);
    }

    @Test
    @DisplayName("搜索联想同样受风控：违禁词直接 40004，不返回任何商品建议")
    void illegalSuggestRejected() {
        when(sensitiveWordFilter.contains("枪支")).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> useCase.suggest("枪支", 10));

        assertEquals(ErrorCode.ILLEGAL_SEARCH_KEYWORD, ex.getErrorCode());
        verifyNoInteractions(productQueryService);
    }

    @Test
    @DisplayName("空白关键字不查库、不做风控（沿用查询门面既有口径）")
    void blankKeywordStillDelegates() {
        when(productQueryService.suggest(anyString(), anyInt())).thenReturn(List.of());

        useCase.suggest("   ", 10);

        verify(productQueryService).suggest(any(), anyInt());
    }
}
