package com.sent.sports.system;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.SensitiveWordFilter;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.system.dto.admin.SensitiveWordDtos;
import com.sent.sports.system.mapper.SensitiveWordMapper;
import com.sent.sports.system.mapper.po.SensitiveWordRow;
import com.sent.sports.system.service.impl.SensitiveWordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 违禁词库维护（V46）：重点不是 CRUD 本身，而是「写完立刻生效」——
 * 每次增删改都必须把 DB 的启用词重新灌进 {@link SensitiveWordFilter}。
 */
@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 21, 0, 0);

    @Mock
    private SensitiveWordMapper mapper;

    /** 真过滤器 + 真 DFA：要断言的是「改完词库里能不能立刻拦住」，mock 掉就测不出来了。 */
    private SensitiveWordFilter filter;
    private SensitiveWordServiceImpl service;

    @BeforeEach
    void setUp() {
        filter = new SensitiveWordFilter(List.of("加V"));
        service = new SensitiveWordServiceImpl(mapper, filter);
    }

    private static SensitiveWordRow row(long id, String word, String category, int enabled, String remark) {
        return new SensitiveWordRow(id, word, category, enabled, remark, NOW, NOW);
    }

    private static SensitiveWordDtos.WordForm form(String word, String category, Boolean enabled, String remark) {
        return new SensitiveWordDtos.WordForm(word, category, enabled, remark);
    }

    @Test
    @DisplayName("新增：落库后立即参与匹配，分类标签翻成人话返回")
    void createTakesEffectImmediately() {
        SensitiveWordRow saved = row(11L, "代开发票", "AD", 1, "违规交易");
        when(mapper.selectByWord("代开发票")).thenReturn(null, saved);
        when(mapper.selectEnabledWords()).thenReturn(List.of("加V", "代开发票"));

        SensitiveWordDtos.WordVO vo = service.create(form(" 代开发票 ", "ad", null, " 违规交易 "), 9001L);

        assertTrue(filter.contains("代开发票"), "新词必须马上生效，不需要重启");
        assertEquals("代开发票", vo.word());
        assertEquals("广告导流", vo.categoryLabel());
        assertTrue(vo.enabled());
        verify(mapper).insert("代开发票", "AD", 1, "违规交易", 9001L);
    }

    @Test
    @DisplayName("新增重复词：先查一次给明确提示，唯一键再兜并发")
    void createDuplicateRejected() {
        when(mapper.selectByWord("加V")).thenReturn(row(1L, "加V", "AD", 1, null));

        BizException e = assertThrows(BizException.class,
                () -> service.create(form("加V", "AD", true, null), 9001L));

        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        assertEquals("违禁词「加V」已存在", e.resolvedMessage());
        verify(mapper, never()).insert(anyString(), anyString(), anyInt(), anyString(), eq(9001L));
    }

    @Test
    @DisplayName("停用：改完立刻不再拦，词还在库里（可以随时再启用）")
    void toggleOffStopsBlockingImmediately() {
        when(mapper.selectById(1L)).thenReturn(
                row(1L, "加V", "AD", 1, null),
                row(1L, "加V", "AD", 0, null));
        when(mapper.selectEnabledWords()).thenReturn(List.of());

        SensitiveWordDtos.WordVO vo = service.toggle(1L, false);

        assertFalse(vo.enabled());
        assertFalse(filter.contains("加V"), "停用后必须立刻放行");
        verify(mapper).updateEnabled(1L, 0);
        verify(mapper, never()).deleteById(1L);
    }

    @Test
    @DisplayName("删除：词从词树里消失；不存在的 id 抛 404")
    void deleteRemovesWordAndRejectsMissingId() {
        when(mapper.selectById(5L)).thenReturn(row(5L, "代开发票", "AD", 1, null));
        when(mapper.selectEnabledWords()).thenReturn(List.of("加V"));

        service.delete(5L);

        verify(mapper).deleteById(5L);

        when(mapper.selectById(404L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.delete(404L));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("编辑：改词后新词生效；空词/非法分类直接拒绝")
    void updateValidatesAndReloads() {
        when(mapper.selectById(1L)).thenReturn(
                row(1L, "加微信", "AD", 1, null),
                row(1L, "加V信", "AD", 1, null));
        when(mapper.selectByWord("加V信")).thenReturn(null);
        when(mapper.selectEnabledWords()).thenReturn(List.of("加V信"));

        SensitiveWordDtos.WordVO vo = service.update(1L, form("加V信", "AD", true, null));

        assertEquals("加V信", vo.word());
        verify(mapper).update(1L, "加V信", "AD", 1, null);

        BizException blank = assertThrows(BizException.class,
                () -> service.update(1L, form("   ", "AD", true, null)));
        assertEquals(ErrorCode.PARAM_INVALID, blank.getErrorCode());

        BizException badCategory = assertThrows(BizException.class,
                () -> service.update(1L, form("正常词", "SPAM", true, null)));
        assertEquals("违禁词分类不合法", badCategory.resolvedMessage());
    }

    @Test
    @DisplayName("列表：列表与 count 同条件，时间转毫秒、启停转布尔")
    void listMapsRowsAndKeepsSameFilters() {
        PageQuery page = new PageQuery();
        page.setPage(1);
        page.setSize(20);
        when(mapper.selectPage("加", "AD", 1, 20, 0))
                .thenReturn(List.of(row(2L, "加V", "AD", 1, "E2E 断言用")));
        when(mapper.countBy("加", "AD", 1)).thenReturn(37L);

        PageResult<SensitiveWordDtos.WordVO> result = service.list(" 加 ", " AD ", 1, page);

        assertEquals(37L, result.getTotal());
        assertEquals(1, result.getList().size());
        SensitiveWordDtos.WordVO vo = result.getList().get(0);
        assertTrue(vo.enabled());
        assertEquals("E2E 断言用", vo.remark());
        assertTrue(vo.updatedAtMillis() > 0);
    }

    @Test
    @DisplayName("统计：停用数 = 总数 - 启用数")
    void statsSplitsEnabledAndDisabled() {
        when(mapper.countAll()).thenReturn(30L);
        when(mapper.countEnabled()).thenReturn(26L);

        SensitiveWordDtos.StatsVO stats = service.stats();

        assertEquals(30L, stats.total());
        assertEquals(26L, stats.enabled());
        assertEquals(4L, stats.disabled());
    }
}
