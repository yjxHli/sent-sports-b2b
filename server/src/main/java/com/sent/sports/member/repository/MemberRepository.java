package com.sent.sports.member.repository;

import com.sent.sports.member.domain.Member;

import java.util.Optional;

/**
 * 会员仓储端口（P3-45 引入，实现见 Phase 5）：按客户读取与持久化会员聚合。
 */
public interface MemberRepository {
    Optional<Member> findByCustomerId(long customerId);

    void save(Member member);
}
