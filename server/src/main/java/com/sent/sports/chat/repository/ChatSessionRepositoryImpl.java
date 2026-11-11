package com.sent.sports.chat.repository;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 会话仓储实现（P5-22）。
 * <p>主键采用「先发号后落库」：{@link #nextId()} 插入一条骨架行拿到自增主键，用例据此构造领域对象，
 * 随后的 {@link #save(ChatSession)} 走更新。这样既保留了数据库自增主键，又满足领域对象「id 不可变」的要求。
 * <p>队列查询（{@link #findWaiting()}）沿用 SQL 侧排序（会员优先 → 人工调序 → 先到先接），
 * 不在内存里重排，避免会话量上来后拖垮轮询接口。
 */
@Repository
public class ChatSessionRepositoryImpl implements ChatSessionRepository {

    private static final int QUEUE_SCAN_LIMIT = 1000;

    private final ChatSessionMapper chatSessionMapper;

    public ChatSessionRepositoryImpl(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    @Override
    public long nextId() {
        ChatSessionPO skeleton = new ChatSessionPO();
        skeleton.setCustomerId(0L);
        skeleton.setStatus(ChatSessionStatus.WAITING.name());
        skeleton.setSourceType("NONE");
        skeleton.setVip(0);
        skeleton.setPriority(0);
        skeleton.setSeq(0L);
        chatSessionMapper.insert(skeleton);
        return skeleton.getId();
    }

    @Override
    public void save(ChatSession session) {
        ChatSessionPO po = toPO(session);
        po.setId(session.getId());
        if (chatSessionMapper.updateById(po) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在，请先通过 nextId 分配主键");
        }
    }

    @Override
    public Optional<ChatSession> findById(long id) {
        ChatSessionPO po = chatSessionMapper.selectById(id);
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public List<ChatSession> findByCustomerId(long customerId) {
        return toDomainList(chatSessionMapper.selectByCustomerId(customerId));
    }

    @Override
    public List<ChatSession> findWaiting() {
        return toDomainList(chatSessionMapper.selectWaitingQueue(QUEUE_SCAN_LIMIT));
    }

    @Override
    public List<ChatSession> findByStatus(ChatSessionStatus status) {
        return toDomainList(chatSessionMapper.selectByStatus(status.name()));
    }

    @Override
    public List<ChatSession> findActiveByStaff(long staffId) {
        return toDomainList(chatSessionMapper.selectActiveByStaff(staffId));
    }

    @Override
    public int countActive() {
        return chatSessionMapper.countActive();
    }

    private List<ChatSession> toDomainList(List<ChatSessionPO> pos) {
        List<ChatSession> list = new ArrayList<>(pos.size());
        for (ChatSessionPO po : pos) {
            list.add(toDomain(po));
        }
        return list;
    }

    private ChatSession toDomain(ChatSessionPO po) {
        // 用 rehydrate 还原（含 close_reason），不重跑状态机：
        // 否则「加载一条已结束会话」会触发 close() 的 STATE_CONFLICT 守卫，整条查询直接失败。
        return ChatSession.rehydrate(po.getId(), po.getCustomerId(),
                po.getVip() != null && po.getVip() == 1,
                ChatSessionStatus.valueOf(po.getStatus()), po.getStaffId(), po.getCloseReason(),
                toMillis(po.getCreatedAt()), toMillis(po.getLastMsgAt()),
                po.getSeq() == null ? 0 : po.getSeq().intValue());
    }

    private ChatSessionPO toPO(ChatSession session) {
        ChatSessionPO po = new ChatSessionPO();
        po.setCustomerId(session.getCustomerId());
        po.setStaffId(session.getStaffId());
        po.setStatus(session.getStatus().name());
        po.setVip(session.isVip() ? 1 : 0);
        po.setSeq((long) session.getSeq());
        po.setCloseReason(session.getCloseReason());
        po.setLastMsgAt(fromMillis(session.getLastActiveAtMillis()));
        return po;
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime fromMillis(long millis) {
        return millis <= 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
