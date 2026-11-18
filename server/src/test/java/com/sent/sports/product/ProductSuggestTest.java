package com.sent.sports.product;

import com.sent.sports.product.dto.CatalogDtos;
import com.sent.sports.product.mapper.CatalogMapper;
import com.sent.sports.product.service.impl.ProductQueryServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 首页搜索联想（2026-11-17）：空白不查库、结果带分类供点击跳结果页。 */
class ProductSuggestTest extends BaseUnitTest {

    @Mock
    private CatalogMapper catalogMapper;

    @InjectMocks
    private ProductQueryServiceImpl queryService;

    @Test
    void blankKeywordNeverHitsDatabase() {
        assertTrue(queryService.suggest("   ", 10).isEmpty());
        assertTrue(queryService.suggest(null, 10).isEmpty());
        verify(catalogMapper, never()).selectSuggestions(anyString(), anyInt());
    }

    @Test
    void suggestReturnsProductIdAndCategory() {
        CatalogMapper.ProductRow row = new CatalogMapper.ProductRow();
        row.setId(9101L);
        row.setTitle("斯伯丁 5号 比赛用球");
        row.setCategoryId(2L);
        row.setCategoryName("足球");
        when(catalogMapper.selectSuggestions("球", 20)).thenReturn(List.of(row));

        List<CatalogDtos.SuggestVO> result = queryService.suggest(" 球 ", 999);

        assertEquals(1, result.size());
        assertEquals("9101", result.get(0).productId());
        assertEquals("斯伯丁 5号 比赛用球", result.get(0).name());
        assertEquals(2L, result.get(0).categoryId(), "点击联想要靠 categoryId 跳商品结果页");
        // limit 防刷：最大 20
        verify(catalogMapper).selectSuggestions("球", 20);
    }
}
