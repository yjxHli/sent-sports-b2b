package com.sent.sports.customize.service.impl;

import com.sent.sports.common.util.IdGen;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.CustomizeShipGateway;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 定制可发货判定门面实现：仅 FINISHED（已完工）的定制单可发；无定制记录视为非定制可发。
 * <p>发货时同时把「带定制标记的订单行」落成 {@code custom_order} 真库记录，
 * 由唯一键保证同一订单行至多一张定制单。仅依赖 customize 模块内部与 common，符合 ArchUnit 规则 4。</p>
 */
@Service
public class CustomizeShipGatewayImpl implements CustomizeShipGateway {

    private final CustomizeRepository customizeRepository;
    private final CustomOrderMapper customOrderMapper;
    private final IdGen idGen;

    public CustomizeShipGatewayImpl(CustomizeRepository customizeRepository,
                                    CustomOrderMapper customOrderMapper,
                                    IdGen idGen) {
        this.customizeRepository = customizeRepository;
        this.customOrderMapper = customOrderMapper;
        this.idGen = idGen;
    }

    @Override
    public boolean isReadyToShip(String orderNo, long skuId) {
        return customizeRepository.statusOf(orderNo, skuId)
                .map(status -> status == CustomStatus.FINISHED)
                .orElse(true);
    }

    @Override
    public int createCustomOrdersForShipment(String orderNo) {
        List<CustomOrderMapper.CustomOrderSeed> seeds = customOrderMapper.selectMissingCustomLines(orderNo);
        int created = 0;
        for (CustomOrderMapper.CustomOrderSeed seed : seeds) {
            if (seed.getOrderItemId() == null || seed.getCustomerId() == null) {
                continue;
            }
            created += customOrderMapper.insertIfAbsent(
                    idGen.next("CU"),
                    seed.getOrderItemId(),
                    seed.getCustomerId(),
                    seed.getQty() == null ? 0 : seed.getQty(),
                    seed.getFeeCent() == null ? 0L : seed.getFeeCent());
        }
        return created;
    }
}
