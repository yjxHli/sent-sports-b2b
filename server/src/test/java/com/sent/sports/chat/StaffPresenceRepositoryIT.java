package com.sent.sports.chat;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.mapper.StaffPresenceMapper;
import com.sent.sports.chat.repository.impl.DbAgentPresenceRepository;
import com.sent.sports.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 坐席状态真库集成测试（2026-11-28 批次 2 / P1-08）。
 *
 * <p>用「新建一个仓储实例再读」模拟重启：新实例不带任何进程内状态，能读到才说明数据真的落表。
 * 这是判断「内存实现换成真库」是否真的生效的唯一可靠方式——单测只能证明调了哪条 SQL。</p>
 */
@org.junit.jupiter.api.condition.EnabledIf("com.sent.sports.support.ItDatabase#available")
class StaffPresenceRepositoryIT extends BaseIntegrationTest {

    private static final long STAFF_ID = 9003L;

    @Autowired
    private StaffPresenceMapper mapper;

    /** 每次 new 一个实例 = 模拟重启后的新进程（无内存态）。 */
    private DbAgentPresenceRepository freshRepository() {
        return new DbAgentPresenceRepository(mapper);
    }

    @Test
    @DisplayName("切换在线 → 落 staff_presence；换新实例（等价重启）仍能读到")
    void presenceSurvivesRestart() {
        freshRepository().save(new AgentPresence(STAFF_ID, PresenceStatus.ONLINE, 0, 3, 0L));

        AgentPresence reloaded = freshRepository().findByStaffId(STAFF_ID).orElseThrow();

        assertThat(reloaded.getPresence()).isEqualTo(PresenceStatus.ONLINE);
        assertThat(reloaded.getMaxSessions()).isEqualTo(3);
        assertThat(reloaded.getActiveSessions()).isZero();
        assertThat(mapper.selectByStaffId(STAFF_ID).getLastHeartbeatAt())
                .as("每次 save 都要刷新心跳，否则回收任务会把在线坐席误判为掉线")
                .isNotNull();
    }

    @Test
    @DisplayName("同一坐席二次保存是更新而不是插重复行（staff_id 主键 upsert）")
    void secondSaveUpdatesInsteadOfDuplicating() {
        freshRepository().save(new AgentPresence(STAFF_ID, PresenceStatus.ONLINE, 2, 3, 0L));
        freshRepository().save(new AgentPresence(STAFF_ID, PresenceStatus.BUSY, 1, 5, 0L));

        assertThat(countOf("staff_presence")).isEqualTo(1);
        AgentPresence reloaded = freshRepository().findByStaffId(STAFF_ID).orElseThrow();
        assertThat(reloaded.getPresence()).isEqualTo(PresenceStatus.BUSY);
        assertThat(reloaded.getActiveSessions()).isEqualTo(1);
        assertThat(reloaded.getMaxSessions()).isEqualTo(5);
    }

    @Test
    @DisplayName("findAll 返回全部坐席快照，供分配策略使用")
    void findAllReturnsAllAgents() {
        freshRepository().save(new AgentPresence(STAFF_ID, PresenceStatus.ONLINE, 0, 3, 0L));
        freshRepository().save(new AgentPresence(9008L, PresenceStatus.OFFLINE, 0, 3, 0L));

        assertThat(freshRepository().findAll()).hasSize(2);
    }
}
