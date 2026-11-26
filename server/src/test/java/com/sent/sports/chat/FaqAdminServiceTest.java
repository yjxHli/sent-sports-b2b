package com.sent.sports.chat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.chat.dto.admin.AdminFaqDtos;
import com.sent.sports.chat.mapper.FaqKnowledgeMapper;
import com.sent.sports.chat.mapper.po.FaqKnowledgePO;
import com.sent.sports.chat.repository.FaqKnowledgeRepository;
import com.sent.sports.chat.service.impl.FaqAdminServiceImpl;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 话术配置服务测试（V60，TDD 红）。
 *
 * <p>四组口径：</p>
 * <ul>
 *   <li><b>关键词归一</b>：运营从 Excel 粘过来的常带空格、中文逗号、重复词，落库前统一成 {@code a,b}；</li>
 *   <li><b>写必失效缓存</b>：新增/修改/停用都要触发 {@code invalidateCache()}，否则客户下一条消息还在用旧话术；</li>
 *   <li><b>状态白名单</b>：未知状态直接 PARAM_INVALID，重复切换同状态幂等（不写库、不刷缓存、不留日志）；</li>
 *   <li><b>分页筛选</b>：空白关键词归一成 null，别让 {@code LIKE '%%'} 变成拖库条件。</li>
 * </ul>
 */
class FaqAdminServiceTest extends BaseUseCaseTest {

    private static final long OPERATOR = 9001L;

    @Mock
    private FaqKnowledgeMapper faqKnowledgeMapper;

    @Mock
    private FaqKnowledgeRepository faqKnowledgeRepository;

    @InjectMocks
    private FaqAdminServiceImpl service;

    private FaqKnowledgePO row(long id, String keywords, String reply, String status) {
        FaqKnowledgePO po = new FaqKnowledgePO();
        po.setId(id);
        po.setKeywords(keywords);
        po.setReplyContent(reply);
        po.setHitCount(4);
        po.setStatus(status);
        po.setUpdatedAt(LocalDateTime.of(2026, 11, 24, 10, 30));
        return po;
    }

    @Test
    @DisplayName("新增：关键词归一（空格/中文逗号/重复词）+ 落库 + 缓存失效")
    void createNormalizesKeywordsAndInvalidatesCache() {
        when(faqKnowledgeMapper.insert(any(FaqKnowledgePO.class))).thenAnswer(inv -> {
            inv.getArgument(0, FaqKnowledgePO.class).setId(12L);
            return 1;
        });

        AdminFaqDtos.FaqVO vo = service.create(
                new AdminFaqDtos.FaqCmd(" 退换货 ，退货,,退换货 ", "支持7天无理由", null), OPERATOR);

        ArgumentCaptor<FaqKnowledgePO> captor = ArgumentCaptor.forClass(FaqKnowledgePO.class);
        verify(faqKnowledgeMapper).insert(captor.capture());
        assertEquals("退换货,退货", captor.getValue().getKeywords(),
                "中文逗号要转英文、空格要去掉、重复词只留一次");
        assertEquals("ENABLED", captor.getValue().getStatus(), "不传状态默认启用");
        assertEquals(OPERATOR, captor.getValue().getCreatedBy());
        assertEquals(12L, vo.id());
        assertEquals("退换货,退货", vo.keywords());
        assertEquals(0, vo.hitCount(), "新建话术还没被命中过");
        verify(faqKnowledgeRepository).invalidateCache();
    }

    @Test
    @DisplayName("新增：回复为空 / 超 500 字 / 关键词为空都拒绝")
    void createValidatesInput() {
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class, () -> service.create(
                new AdminFaqDtos.FaqCmd("退换货", "   ", null), OPERATOR)).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class, () -> service.create(
                new AdminFaqDtos.FaqCmd("退换货", "字".repeat(501), null), OPERATOR)).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class, () -> service.create(
                new AdminFaqDtos.FaqCmd(" , ， ", "支持7天无理由", null), OPERATOR)).getErrorCode());
        verify(faqKnowledgeMapper, never()).insert(any(FaqKnowledgePO.class));
        verify(faqKnowledgeRepository, never()).invalidateCache();
    }

    @Test
    @DisplayName("修改：不存在回 NOT_FOUND，存在则更新并失效缓存")
    void updateWritesAndInvalidates() {
        FaqKnowledgePO existing = row(12L, "退换货", "旧话术", "ENABLED");
        when(faqKnowledgeMapper.selectAdminById(12L)).thenReturn(existing);
        when(faqKnowledgeMapper.updateById(any(FaqKnowledgePO.class))).thenAnswer(inv -> {
            inv.getArgument(0, FaqKnowledgePO.class).setId(12L);
            return 1;
        });

        AdminFaqDtos.FaqVO vo = service.update(12L,
                new AdminFaqDtos.FaqCmd("退换货,退货", "支持7天无理由", "ENABLED"), OPERATOR);

        ArgumentCaptor<FaqKnowledgePO> captor = ArgumentCaptor.forClass(FaqKnowledgePO.class);
        verify(faqKnowledgeMapper).updateById(captor.capture());
        assertEquals(12L, captor.getValue().getId());
        assertEquals("支持7天无理由", captor.getValue().getReplyContent());
        assertEquals("ENABLED", vo.status());
        verify(faqKnowledgeRepository).invalidateCache();

        when(faqKnowledgeMapper.selectAdminById(404L)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BizException.class, () -> service.update(404L,
                new AdminFaqDtos.FaqCmd("退换货", "支持7天无理由", null), OPERATOR)).getErrorCode());
    }

    @Test
    @DisplayName("启停：未知状态拒绝；同状态幂等；真实变更才写库 + 失效缓存")
    void updateStatusRules() {
        when(faqKnowledgeMapper.selectAdminById(12L))
                .thenReturn(row(12L, "退换货", "支持7天无理由", "ENABLED"));
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.updateStatus(12L, "ON", OPERATOR)).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.updateStatus(12L, " ", OPERATOR)).getErrorCode());
        verify(faqKnowledgeMapper, never()).updateStatus(anyLong(), anyString());

        // 幂等：本来就是启用，再点启用不该刷缓存（缓存无谓失效会让下一个客户多查一次库）
        AdminFaqDtos.FaqVO same = service.updateStatus(12L, "ENABLED", OPERATOR);
        assertEquals("ENABLED", same.status());
        verify(faqKnowledgeRepository, never()).invalidateCache();

        when(faqKnowledgeMapper.updateStatus(eq(12L), eq("DISABLED"))).thenReturn(1);
        AdminFaqDtos.FaqVO off = service.updateStatus(12L, "DISABLED", OPERATOR);
        assertEquals("DISABLED", off.status());
        verify(faqKnowledgeMapper).updateStatus(eq(12L), eq("DISABLED"));
        verify(faqKnowledgeRepository).invalidateCache();
    }

    @Test
    @DisplayName("批量删除：软删（条目与命中次数保留）+ 缓存失效 + 留痕")
    void deleteSoftDeletesAndInvalidatesCache() {
        when(faqKnowledgeMapper.batchMarkDeleted(List.of(12L, 13L))).thenReturn(2);

        // 前端勾选顺序不可控、还可能重复，服务层必须归一后再落库
        int deleted = service.delete(List.of(" 13 ", "12", "13"), OPERATOR);

        assertEquals(2, deleted);
        verify(faqKnowledgeMapper).batchMarkDeleted(List.of(12L, 13L));
        verify(faqKnowledgeMapper).insertFaqLog(eq(OPERATOR), eq("DELETE"), eq("12,13"),
                isNull(), anyString(), eq(OPERATOR));
        verify(faqKnowledgeRepository).invalidateCache();
    }

    @Test
    @DisplayName("批量删除：空选择 / 非数字 ID 直接拒绝，不写库不刷缓存")
    void deleteValidatesIds() {
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.delete(List.of("  ", ""), OPERATOR)).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.delete(List.of("abc"), OPERATOR)).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.delete(null, OPERATOR)).getErrorCode());

        verify(faqKnowledgeMapper, never()).batchMarkDeleted(any());
        verify(faqKnowledgeRepository, never()).invalidateCache();
    }

    @Test
    @DisplayName("分页：空白筛选归一 null，命中次数与更新时间原样下发")
    void listNormalizesFiltersAndMapsRows() {
        Page<FaqKnowledgePO> rows = new Page<>(1, 20);
        rows.setRecords(List.of(row(12L, "退换货", "支持7天无理由", "ENABLED")));
        rows.setTotal(1L);
        when(faqKnowledgeMapper.selectAdminPage(any(), isNull(), isNull())).thenReturn(rows);

        PageResult<AdminFaqDtos.FaqVO> result = service.list("   ", "", new PageQuery());

        assertEquals(1L, result.getTotal());
        AdminFaqDtos.FaqVO vo = result.getList().get(0);
        assertEquals("退换货", vo.keywords());
        assertEquals(4, vo.hitCount());
        assertEquals("ENABLED", vo.status());
        assertTrue(vo.updatedAtMillis() > 0L, "更新时间要能给前端格式化");
        verify(faqKnowledgeMapper).selectAdminPage(any(), isNull(), isNull());
    }
}
