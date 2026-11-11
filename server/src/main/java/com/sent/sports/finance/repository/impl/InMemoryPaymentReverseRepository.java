package com.sent.sports.finance.repository.impl;

import com.sent.sports.finance.domain.PaymentReverse;
import com.sent.sports.finance.repository.PaymentReverseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 回款冲销记录（进程内适配器；接真库时映射 V8 的 payment_record.reversed_reason）。 */
@Repository
public class InMemoryPaymentReverseRepository implements PaymentReverseRepository {

    private final List<PaymentReverse> reverses = new CopyOnWriteArrayList<>();

    @Override
    public void save(PaymentReverse reverse) {
        reverses.add(reverse);
    }

    public List<PaymentReverse> all() {
        return List.copyOf(reverses);
    }
}
