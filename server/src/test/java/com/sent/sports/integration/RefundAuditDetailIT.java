package com.sent.sports.integration;

import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderItemPO;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundAuditDetailPO;
import com.sent.sports.refund.mapper.po.RefundOrderLinePO;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退款审核详情 SQL 集成测试（持久层切片）：
 * {@code selectAuditDetail} 要一次联出退款单 + 订单头 + 客户 + 最近物流，
 * {@code selectOrderLines} 要按「快照优先、缺失回退当前商品」的口径出订单行。
 *
 * <p>这两条 SQL 是管理端独立审核页的唯一数据来源，字段映射错一个就整页错位，
 * 因此用真库（{@code sent_test}）跑，避免只靠 Mock 的单元测试自证正确。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class RefundAuditDetailIT extends BaseIntegrationTest {

    @Autowired
    private RefundOrderMapper refundOrderMapper;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private CustomerMapper customerMapper;

    @Test
    @DisplayName("审核详情 SQL：退款单/订单头/客户/订单行字段全部落位，行实付按扣减后算出")
    void auditDetailJoinsEverything() {
        String suffix = String.valueOf(System.currentTimeMillis());

        CustomerPO customer = new CustomerPO();
        customer.setCustomerNo("ITC-" + suffix);
        customer.setPhone("19" + suffix.substring(Math.max(0, suffix.length() - 9)));
        customer.setNickname("IT审核客户");
        customer.setType("PERSON");
        customer.setLevelCode("LV1");
        customer.setStatus("ACTIVE");
        customer.setDeleted(0);
        customer.setGrowthValue(0L);
        customer.setPointsBalance(0L);
        customer.setTokenVersion(0L);
        customerMapper.insert(customer);

        String orderNo = "IT-AD-ORDER-" + suffix;
        OrderPO order = new OrderPO();
        order.setOrderNo(orderNo);
        order.setCustomerId(customer.getId());
        order.setIdemKey("idem-" + orderNo);
        order.setStatus("PART_SHIPPED");
        order.setPayStatus("PAID");
        order.setAfterSaleStatus("REFUNDING");
        order.setGoodsAmountCent(9980L);
        order.setFreightCent(0L);
        order.setPointDeductCent(0L);
        order.setDiscountCent(0L);
        order.setPayableCent(9980L);
        order.setPaidCent(9980L);
        order.setRemark("IT 订单备注");
        order.setAddressSnapshotJson("{\"receiver\":\"IT收货人\",\"phone\":\"13800001111\","
                + "\"province\":\"广东省\",\"city\":\"深圳市\",\"district\":\"南山区\",\"detail\":\"科技园路 9 号\"}");
        orderMapper.insert(order);

        OrderItemPO item = new OrderItemPO();
        item.setOrderId(order.getId());
        item.setSkuId(9101L);
        item.setSpuTitleSnap("IT快干短袖");
        item.setSpecSnap("黑色 / L");
        item.setQty(2);
        item.setUnitPriceCent(4990L);
        item.setPriceSource("MEMBER");
        item.setAmountCent(9980L);
        item.setDiscountAllocCent(980L);
        item.setShippedQty(2);
        item.setCustomFlag(0);
        item.setCustomFeeCent(0L);
        orderMapper.insertItem(item);

        String refundNo = "RF-IT-AD-" + suffix;
        RefundOrder refund = new RefundOrder(refundNo, orderNo, order.getId(), 9000L, "REFUND",
                "尺码不符", RefundStatus.PENDING, System.currentTimeMillis());
        refund.setImages(List.of("2026/11/ab/proof.png"));
        refundRepository.save(refund);

        RefundAuditDetailPO po = refundOrderMapper.selectAuditDetail(refundNo);

        assertThat(po).isNotNull();
        assertThat(po.getOrderNo()).isEqualTo(orderNo);
        assertThat(po.getReason()).isEqualTo("尺码不符");
        assertThat(po.getImagesJson()).contains("2026/11/ab/proof.png");
        assertThat(po.getOrderId()).isEqualTo(order.getId());
        assertThat(po.getOrderStatus()).isEqualTo("PART_SHIPPED");
        assertThat(po.getPayStatus()).isEqualTo("PAID");
        assertThat(po.getAfterSaleStatus()).isEqualTo("REFUNDING");
        assertThat(po.getPayableCent()).isEqualTo(9980L);
        assertThat(po.getPaidCent()).isEqualTo(9980L);
        assertThat(po.getCustomerId()).isEqualTo(customer.getId());
        assertThat(po.getCustomerName()).isEqualTo("IT审核客户");
        assertThat(po.getCustomerPhone()).isEqualTo(customer.getPhone());
        assertThat(po.getCustomerLevel()).isEqualTo("LV1");
        assertThat(po.getAddressJson()).contains("IT收货人");
        assertThat(po.getOrderCreatedAt()).isNotNull();
        assertThat(po.getCreatedAt()).isNotNull();

        List<RefundOrderLinePO> lines = refundOrderMapper.selectOrderLines(po.getOrderId());

        assertThat(lines).hasSize(1);
        RefundOrderLinePO line = lines.get(0);
        assertThat(line.getSkuName()).isEqualTo("IT快干短袖");
        assertThat(line.getSpec()).isEqualTo("黑色 / L");
        assertThat(line.getQty()).isEqualTo(2);
        assertThat(line.getUnitPriceCent()).isEqualTo(4990L);
        assertThat(line.getShippedQty()).isEqualTo(2);
        assertThat(line.getLinePaidCent()).isEqualTo(9000L);
        assertThat(line.getCustomStatus()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("审核详情 SQL：退款单不存在返回 null（业务层据此抛 NOT_FOUND）")
    void auditDetailMissingReturnsNull() {
        assertThat(refundOrderMapper.selectAuditDetail("RF-NOT-EXIST-9999")).isNull();
    }

    @Test
    @DisplayName("订单列表提示 SQL：只回在途（PENDING/REFUNDING）且未软删的退款单号，已退款/已关闭不提示")
    void inFlightOrderNosFilterByStatusAndDeleted() {
        String suffix = String.valueOf(System.currentTimeMillis());
        CustomerPO customer = new CustomerPO();
        customer.setCustomerNo("ITH-" + suffix);
        customer.setPhone("18" + suffix.substring(Math.max(0, suffix.length() - 9)));
        customer.setNickname("IT提示客户");
        customer.setType("PERSON");
        customer.setLevelCode("NORMAL");
        customer.setStatus("ACTIVE");
        customer.setDeleted(0);
        customer.setGrowthValue(0L);
        customer.setPointsBalance(0L);
        customer.setTokenVersion(0L);
        customerMapper.insert(customer);

        List<String> orderNos = List.of(
                "IT-HINT-PENDING-" + suffix, "IT-HINT-REFUNDING-" + suffix,
                "IT-HINT-REFUNDED-" + suffix, "IT-HINT-CLOSED-" + suffix,
                "IT-HINT-DELETED-" + suffix);
        List<Long> orderIds = new ArrayList<>();
        for (String orderNo : orderNos) {
            OrderPO order = new OrderPO();
            order.setOrderNo(orderNo);
            order.setCustomerId(customer.getId());
            order.setIdemKey("idem-" + orderNo);
            order.setStatus("WAIT_SHIP");
            order.setPayStatus("PAID");
            order.setPayableCent(9900L);
            orderMapper.insert(order);
            orderIds.add(order.getId());
        }

        refundRepository.save(refund("RF-ITH-P-" + suffix, orderNos.get(0), orderIds.get(0), RefundStatus.PENDING));
        refundRepository.save(refund("RF-ITH-R-" + suffix, orderNos.get(1), orderIds.get(1), RefundStatus.REFUNDING));
        refundRepository.save(refund("RF-ITH-D-" + suffix, orderNos.get(2), orderIds.get(2), RefundStatus.REFUNDED));
        refundRepository.save(refund("RF-ITH-C-" + suffix, orderNos.get(3), orderIds.get(3), RefundStatus.CLOSED));
        refundRepository.save(refund("RF-ITH-X-" + suffix, orderNos.get(4), orderIds.get(4), RefundStatus.PENDING));
        refundOrderMapper.batchMarkDeleted(List.of("RF-ITH-X-" + suffix));

        assertThat(refundOrderMapper.selectInFlightOrderNos(orderNos))
                .containsExactlyInAnyOrder(orderNos.get(0), orderNos.get(1));
    }

    private static RefundOrder refund(String refundNo, String orderNo, long orderId, RefundStatus status) {
        return new RefundOrder(refundNo, orderNo, orderId, 2000L, "REFUND",
                "尺码不符", status, System.currentTimeMillis());
    }
}
