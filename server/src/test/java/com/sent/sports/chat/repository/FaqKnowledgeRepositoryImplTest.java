package com.sent.sports.chat.repository;

import com.sent.sports.chat.domain.FaqKnowledge;
import com.sent.sports.chat.mapper.FaqKnowledgeMapper;
import com.sent.sports.chat.mapper.po.FaqKnowledgePO;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库仓储缓存测试（V60，TDD 红）。
 *
 * <p>机器人每次收到客户消息都要做一次匹配，知识库只有十几条但调用频次极高，
 * 所以在仓储侧放一层「启用条目快照」。缓存不是靠 TTL 过期，而是靠写路径
 * {@code invalidateCache()} 显式失效——运营在管理端改完话术，下一条消息就要读到新内容，
 * 靠 TTL 会出现「刚改的没生效」的投诉。</p>
 */
class FaqKnowledgeRepositoryImplTest extends BaseUnitTest {

    @Mock
    private FaqKnowledgeMapper faqKnowledgeMapper;

    @InjectMocks
    private FaqKnowledgeRepositoryImpl repository;

    private FaqKnowledgePO row(long id, String keywords, String reply) {
        FaqKnowledgePO po = new FaqKnowledgePO();
        po.setId(id);
        po.setKeywords(keywords);
        po.setReplyContent(reply);
        po.setHitCount(0);
        po.setStatus("ENABLED");
        return po;
    }

    @Test
    @DisplayName("命中快照被缓存：连续两次匹配只查一次库")
    void snapshotIsCached() {
        when(faqKnowledgeMapper.selectAllEnabledOrdered())
                .thenReturn(List.of(row(1L, "发货", "默认顺丰")));

        List<FaqKnowledge> first = repository.findAllEnabledOrdered();
        List<FaqKnowledge> second = repository.findAllEnabledOrdered();

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        verify(faqKnowledgeMapper, times(1)).selectAllEnabledOrdered();
    }

    @Test
    @DisplayName("写路径失效缓存：invalidate 之后下一条消息立刻读到新话术")
    void invalidateReloadsLatest() {
        when(faqKnowledgeMapper.selectAllEnabledOrdered())
                .thenReturn(List.of(row(1L, "发货", "旧话术")))
                .thenReturn(List.of(row(1L, "发货", "新话术")));

        assertEquals("旧话术", repository.findAllEnabledOrdered().get(0).getReplyContent());
        repository.invalidateCache();
        assertEquals("新话术", repository.findAllEnabledOrdered().get(0).getReplyContent(),
                "管理端改完话术必须秒级生效，不能等缓存自然过期");
        verify(faqKnowledgeMapper, times(2)).selectAllEnabledOrdered();
    }

    @Test
    @DisplayName("快照只取启用条目：SQL 侧就必须过滤，不能把停用话术捞进来")
    void snapshotQueriesEnabledOnly() {
        when(faqKnowledgeMapper.selectAllEnabledOrdered()).thenReturn(List.of());

        assertTrue(repository.findAllEnabledOrdered().isEmpty());
        verify(faqKnowledgeMapper).selectAllEnabledOrdered();
    }

    @Test
    @DisplayName("返回的快照不可被调用方改写：匹配逻辑只能读，改不动缓存本体")
    void snapshotIsImmutable() {
        when(faqKnowledgeMapper.selectAllEnabledOrdered())
                .thenReturn(List.of(row(1L, "发货", "默认顺丰")));

        List<FaqKnowledge> snapshot = repository.findAllEnabledOrdered();
        try {
            snapshot.clear();
        } catch (RuntimeException ignored) {
            // 不可变集合抛异常是预期行为之一，另一种实现是返回副本，两种都算通过
        }
        assertEquals(1, repository.findAllEnabledOrdered().size());
    }
}
