package com.sent.sports.member.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.member.domain.Member;
import com.sent.sports.member.repository.MemberRepository;
import com.sent.sports.member.service.dto.GrantLevelCmd;
import com.sent.sports.member.service.dto.GrantLevelResult;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 人工授级用例（P3-45）：spec §6.3.1 / 验收——
 * 原因必填（PARAM_INVALID）、有效期（validToMillis）、到期回落（expireGrantIfNeeded 由用例触发）。
 * 仅依赖 member 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class GrantLevelUseCase {

    private final MemberRepository memberRepository;
    private final Clock clock;

    public GrantLevelUseCase(MemberRepository memberRepository, Clock clock) {
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @UseCase
    public GrantLevelResult grant(GrantLevelCmd cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        Member member = memberRepository.findByCustomerId(cmd.customerId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        long now = clock.instant().toEpochMilli();
        member.expireGrantIfNeeded(now); // 先清算过期的历史授级，再写入新授级
        member.grantLevel(cmd.targetLevel(), cmd.validToMillis(), cmd.reason());
        memberRepository.save(member);
        return new GrantLevelResult(member.getCustomerId(), member.getLevel(), member.getLevelValidToMillis());
    }
}
