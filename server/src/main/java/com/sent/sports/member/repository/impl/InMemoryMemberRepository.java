package com.sent.sports.member.repository.impl;

import com.sent.sports.member.domain.Member;
import com.sent.sports.member.repository.MemberRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 会员档案仓储（进程内适配器；接真库时映射 V3 的 customer / growth_log）。 */
@Repository
public class InMemoryMemberRepository implements MemberRepository {

    private final Map<Long, Member> byCustomerId = new ConcurrentHashMap<>();

    @Override
    public Optional<Member> findByCustomerId(long customerId) {
        return Optional.ofNullable(byCustomerId.get(customerId));
    }

    @Override
    public void save(Member member) {
        byCustomerId.put(member.getCustomerId(), member);
    }
}
