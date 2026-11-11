package com.sent.sports.payment.service.impl;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.payment.dto.PaymentDtos;
import com.sent.sports.payment.service.PaymentQueryService;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 支付查询桩实现（P4-19 占位，Phase 5 替换）。
 * 读面尚未建设，本实现仅保证编译与 Bean 注册：列表返回空页，轮询默认 PENDING。
 * P5 接入真实仓储后实现业务逻辑（含 PAID/FAILED 判定）。
 */
@Service
public class PaymentQueryServiceImpl implements PaymentQueryService {

    @Override
    public PageResult<PaymentDtos.PayResultVO> listMyPayments(PageQuery pageQuery) {
        // P5 接入实体与仓储后替换
        return PageResult.of(Collections.emptyList(), 0L, pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public PaymentDtos.PayResultVO getPayResult(String orderNo) {
        // P5 接入实体与仓储后替换；当前默认 PENDING（前端轮询等待）
        return new PaymentDtos.PayResultVO(orderNo, "PENDING", null, null, null);
    }
}
