package com.sent.sports.member.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.member.domain.Member;
import com.sent.sports.member.domain.PointFlow;
import com.sent.sports.member.repository.MemberRepository;
import com.sent.sports.member.repository.PointFlowRepository;
import com.sent.sports.member.service.dto.AdjustPointCmd;
import com.sent.sports.member.service.dto.AdjustPointResult;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 积分调整用例（P3-47）：spec §6.4 / 验收——
 * 原因必填（PARAM_INVALID）、负积分允许、写积分流水（PointFlow）。
 * 仅依赖 member 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class AdjustPointUseCase {

    private final MemberRepository memberRepository;
    private final PointFlowRepository pointFlowRepository;
    private final IdGen idGen;
    private final Clock clock;

    public AdjustPointUseCase(MemberRepository memberRepository, PointFlowRepository pointFlowRepository,
                             IdGen idGen, Clock clock) {
        this.memberRepository = memberRepository;
        this.pointFlowRepository = pointFlowRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public AdjustPointResult adjust(AdjustPointCmd cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        Member member = memberRepository.findByCustomerId(cmd.customerId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        long before = member.getAvailablePoints();
        member.adjustPoints(cmd.delta());
        long after = member.getAvailablePoints();
        long now = clock.instant().toEpochMilli();
        memberRepository.save(member);
        pointFlowRepository.save(new PointFlow(idGen.next("PF"), cmd.customerId(), cmd.delta(),
                before, after, cmd.bizType(), cmd.bizNo(), cmd.operatorId(), cmd.reason(), now));
        return new AdjustPointResult(cmd.customerId(), before, after, cmd.delta());
    }
}
