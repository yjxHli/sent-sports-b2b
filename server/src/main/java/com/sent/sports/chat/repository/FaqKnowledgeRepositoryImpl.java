package com.sent.sports.chat.repository;

import com.sent.sports.chat.domain.FaqKnowledge;
import com.sent.sports.chat.domain.FaqStatus;
import com.sent.sports.chat.mapper.FaqKnowledgeMapper;
import com.sent.sports.chat.mapper.po.FaqKnowledgePO;
import com.sent.sports.common.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 机器人知识库仓储实现（V59 / V60）：PO ↔ 领域对象转换 + 启用条目内存快照。
 *
 * <p>为什么在这层加缓存而不是上 Redis：知识库只有十几条、单机部署，
 * 每条客户消息都要匹配一次，跑一次全表 SELECT 是纯浪费；而 TTL 缓存会让「运营刚改的话术」
 * 在一段时间内不生效，所以这里用<b>写路径显式失效</b>的弱引用快照：读无锁、写清空、
 * 下一次读重新加载。</p>
 *
 * <p>{@code invalidateCache()} 只是把 {@code volatile} 引用置空，不需要任何锁；
 * 并发场景下最多两个线程各加载一次（幂等），不会出现半截数据——
 * 加载完才整体替换引用，读方要么看到旧快照要么看到新快照。</p>
 */
@Repository
public class FaqKnowledgeRepositoryImpl implements FaqKnowledgeRepository {

    private static final Logger log = LoggerFactory.getLogger(FaqKnowledgeRepositoryImpl.class);

    private final FaqKnowledgeMapper faqKnowledgeMapper;

    /** 启用条目快照；null 表示需要重新加载。整体替换，不做原地修改。 */
    private volatile List<FaqKnowledge> enabledSnapshot;

    public FaqKnowledgeRepositoryImpl(FaqKnowledgeMapper faqKnowledgeMapper) {
        this.faqKnowledgeMapper = faqKnowledgeMapper;
    }

    @Override
    public List<FaqKnowledge> findAllEnabledOrdered() {
        List<FaqKnowledge> local = enabledSnapshot;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (enabledSnapshot == null) {
                enabledSnapshot = loadEnabled();
            }
            return enabledSnapshot;
        }
    }

    @Override
    public void invalidateCache() {
        enabledSnapshot = null;
        log.debug("知识库快照已失效，下一次匹配重新加载");
    }

    @Override
    public void recordHit(long faqId) {
        try {
            faqKnowledgeMapper.incrementHit(faqId);
        } catch (RuntimeException e) {
            // 统计数字掉了无所谓，不能因为一次计数失败把客户的消息一起吞掉
            log.warn("知识库命中计数失败, faqId={}", faqId, e);
        }
    }

    /** 一次性加载：不可变列表，避免调用方（匹配逻辑）误改缓存本体。 */
    private List<FaqKnowledge> loadEnabled() {
        List<FaqKnowledgePO> rows = faqKnowledgeMapper.selectAllEnabledOrdered();
        List<FaqKnowledge> list = new ArrayList<>(rows.size());
        for (FaqKnowledgePO po : rows) {
            list.add(new FaqKnowledge(po.getId(),
                    po.getKeywords(), po.getReplyContent(),
                    po.getHitCount() == null ? 0 : po.getHitCount(),
                    FaqStatus.of(po.getStatus()),
                    TimeUtil.toMillis(po.getUpdatedAt())));
        }
        return List.copyOf(list);
    }
}
