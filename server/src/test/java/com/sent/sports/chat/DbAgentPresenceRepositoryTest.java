package com.sent.sports.chat;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.mapper.StaffPresenceMapper;
import com.sent.sports.chat.mapper.po.StaffPresencePO;
import com.sent.sports.chat.repository.impl.DbAgentPresenceRepository;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 坐席状态真库仓储单测（2026-11-28 批次 2 / P1-08）。
 *
 * <p>此前 {@code InMemoryAgentPresenceRepository} 把状态存在 Map 里：重启即丢、多实例各看各的，
 * 而 {@code staff_presence} 表只被指标 SQL {@code LEFT JOIN}、{@code PresenceReclaimJob} 只做空转的
 * {@code UPDATE}——结果是「所有坐席恒为离线」。本类锁定新实现与表结构的映射口径。</p>
 */
class DbAgentPresenceRepositoryTest extends BaseUseCaseTest {

    @Mock
    private StaffPresenceMapper mapper;

    @InjectMocks
    private DbAgentPresenceRepository repository;

    private StaffPresencePO row(long staffId, String status, int active) {
        StaffPresencePO po = new StaffPresencePO();
        po.setStaffId(staffId);
        po.setStatus(status);
        po.setMaxSessions(3);
        po.setActiveSessions(active);
        po.setLastHeartbeatAt(LocalDateTime.of(2026, 11, 28, 17, 30));
        return po;
    }

    @Test
    @DisplayName("save：以 staff_id 为键 upsert，状态/上限/负载全部落库")
    void saveUpsertsRow() {
        repository.save(new AgentPresence(9003L, PresenceStatus.ONLINE, 2, 3, 0L));

        verify(mapper).upsert(9003L, "ONLINE", 3, 2);
    }

    @Test
    @DisplayName("findByStaffId：行存在时映射为领域对象（状态/负载/上限/心跳）")
    void findByStaffIdMapsRow() {
        when(mapper.selectByStaffId(9003L)).thenReturn(row(9003L, "BUSY", 1));

        Optional<AgentPresence> found = repository.findByStaffId(9003L);

        assertThat(found).isPresent();
        assertThat(found.get().getPresence()).isEqualTo(PresenceStatus.BUSY);
        assertThat(found.get().getActiveSessions()).isEqualTo(1);
        assertThat(found.get().getMaxSessions()).isEqualTo(3);
        assertThat(found.get().getUpdatedAtMillis()).isPositive();
    }

    @Test
    @DisplayName("findByStaffId：没有记录返回 empty（调用方按离线处理，不伪造在线）")
    void findByStaffIdMissingReturnsEmpty() {
        when(mapper.selectByStaffId(9003L)).thenReturn(null);

        assertThat(repository.findByStaffId(9003L)).isEmpty();
    }

    @Test
    @DisplayName("findAll：全量快照，供分配策略挑人")
    void findAllReturnsAllRows() {
        when(mapper.selectAll()).thenReturn(List.of(row(9003L, "ONLINE", 0), row(9008L, "OFFLINE", 0)));

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("库里出现未知状态字符串：回落 OFFLINE，不让脏数据把分配打崩")
    void unknownStatusFallsBackToOffline() {
        when(mapper.selectByStaffId(9003L)).thenReturn(row(9003L, "GHOST", 0));

        assertThat(repository.findByStaffId(9003L).orElseThrow().getPresence())
                .isEqualTo(PresenceStatus.OFFLINE);
    }
}
