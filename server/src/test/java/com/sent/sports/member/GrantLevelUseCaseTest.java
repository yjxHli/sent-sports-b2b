package com.sent.sports.member;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.member.domain.Member;
import com.sent.sports.member.domain.MemberLevel;
import com.sent.sports.member.repository.MemberRepository;
import com.sent.sports.member.service.GrantLevelUseCase;
import com.sent.sports.member.service.dto.GrantLevelCmd;
import com.sent.sports.member.service.dto.GrantLevelResult;
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
 * 人工授级用例测试（P3-44，TDD 红）：spec §6.3.1 / 验收——
 * 原因必填（PARAM_INVALID）、有效期（validToMillis）、到期回落（expireGrantIfNeeded）。
 * 此时 Member/GrantLevelUseCase 等尚未实现，编译即失败（红）。
 */
class GrantLevelUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private GrantLevelUseCase useCase;
    @Captor
    private ArgumentCaptor<Member> memberCaptor;

    @Test
    void grantSetsLevelAndValidity() {
        Member member = new Member(1L, MemberLevel.NORMAL, 0L, 0L);
        when(memberRepository.findByCustomerId(1L)).thenReturn(Optional.of(member));
        when(clock.instant()).thenReturn(NOW);

        GrantLevelResult r = useCase.grant(new GrantLevelCmd(1L, MemberLevel.VIP1, NOW.toEpochMilli() + 86_400_000L, "达标授级", 9L));

        verify(memberRepository).save(memberCaptor.capture());
        Member saved = memberCaptor.getValue();
        assertEquals(MemberLevel.VIP1, saved.getLevel());
        assertEquals("达标授级", saved.getLastGrantReason());
        assertEquals(NOW.toEpochMilli() + 86_400_000L, saved.getLevelValidToMillis());
        assertEquals(MemberLevel.VIP1, r.level());
    }

    @Test
    void reasonMustNotBeBlank() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.grant(new GrantLevelCmd(1L, MemberLevel.VIP1, NOW.toEpochMilli() + 86_400_000L, "   ", 9L)));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void memberNotFound() {
        when(memberRepository.findByCustomerId(2L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.grant(new GrantLevelCmd(2L, MemberLevel.VIP1, NOW.toEpochMilli() + 86_400_000L, "达标", 9L)));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }
}
