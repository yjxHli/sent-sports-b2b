package com.sent.sports.member;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.member.domain.Member;
import com.sent.sports.member.domain.PointFlow;
import com.sent.sports.member.repository.MemberRepository;
import com.sent.sports.member.repository.PointFlowRepository;
import com.sent.sports.member.service.AdjustPointUseCase;
import com.sent.sports.member.service.dto.AdjustPointCmd;
import com.sent.sports.member.service.dto.AdjustPointResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分调整用例测试（P3-46，TDD 红）：spec §6.4 / 验收——
 * 原因必填（PARAM_INVALID）、负积分允许、写积分流水（PointFlow）。
 * 此时 Member/AdjustPointUseCase 等尚未实现，编译即失败（红）。
 */
class AdjustPointUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PointFlowRepository pointFlowRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private AdjustPointUseCase useCase;
    @Captor
    private ArgumentCaptor<PointFlow> flowCaptor;

    @Test
    void positiveAdjustWritesFlowAndUpdatesPoints() {
        Member member = new Member(1L, com.sent.sports.member.domain.MemberLevel.NORMAL, 0L, 100L);
        when(memberRepository.findByCustomerId(1L)).thenReturn(Optional.of(member));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("PF")).thenReturn("PF0001");

        AdjustPointResult r = useCase.adjust(new AdjustPointCmd(1L, 50L, "活动奖励", "ADJUST", "BIZ1", 9L));

        verify(memberRepository).save(any(Member.class));
        verify(pointFlowRepository).save(flowCaptor.capture());
        PointFlow f = flowCaptor.getValue();
        assertEquals(1L, f.customerId());
        assertEquals(50L, f.changePoints());
        assertEquals(100L, f.beforePoints());
        assertEquals(150L, f.afterPoints());
        assertEquals(150L, r.afterPoints());
    }

    @Test
    void negativeAdjustAllowed() {
        Member member = new Member(1L, com.sent.sports.member.domain.MemberLevel.NORMAL, 0L, 100L);
        when(memberRepository.findByCustomerId(1L)).thenReturn(Optional.of(member));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("PF")).thenReturn("PF0001");

        AdjustPointResult r = useCase.adjust(new AdjustPointCmd(1L, -30L, "扣减", "ADJUST", "BIZ2", 9L));

        assertEquals(70L, r.afterPoints());
        verify(pointFlowRepository).save(flowCaptor.capture());
        assertEquals(-30L, flowCaptor.getValue().changePoints());
    }

    @Test
    void reasonMustNotBeBlank() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.adjust(new AdjustPointCmd(1L, 10L, "  ", "ADJUST", "BIZ3", 9L)));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(memberRepository, never()).save(any());
        verify(pointFlowRepository, never()).save(any());
    }
}
