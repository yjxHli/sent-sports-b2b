package com.sent.sports.order.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.order.mapper.ShipmentMapper;
import com.sent.sports.order.service.ShipmentRecorder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 发货落账实现：与 {@code ShipOrderUseCase} 同处一个 {@code @UseCase} 事务，
 * 订单头/行回写与包裹落账要么全成、要么全滚。
 */
@Service
public class ShipmentRecorderImpl implements ShipmentRecorder {

    private final ShipmentMapper shipmentMapper;
    private final IdGen idGen;

    public ShipmentRecorderImpl(ShipmentMapper shipmentMapper, IdGen idGen) {
        this.shipmentMapper = shipmentMapper;
        this.idGen = idGen;
    }

    @Override
    public String record(String orderNo, String carrier, String trackingNo, List<Line> lines) {
        Long orderId = shipmentMapper.selectOrderIdByOrderNo(orderNo);
        if (orderId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在，无法生成包裹");
        }
        String shipmentNo = idGen.next("SH");
        shipmentMapper.insertShipment(shipmentNo, orderId, carrier, trackingNo);
        Long shipmentId = shipmentMapper.selectIdByShipmentNo(shipmentNo);
        if (shipmentId == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "包裹写入失败");
        }
        for (Line line : lines) {
            shipmentMapper.insertItemForSku(shipmentId, orderId, line.skuId(), line.qty());
        }
        return shipmentNo;
    }
}
