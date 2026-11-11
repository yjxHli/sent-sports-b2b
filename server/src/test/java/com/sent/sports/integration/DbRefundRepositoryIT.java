package com.sent.sports.integration;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundDetailPO;
import com.sent.sports.refund.mapper.po.RefundListPO;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.support.BaseIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退款仓储真库实现集成测试（收口阶段，持久层切片）：
 * 验证 DB 化退款仓储的 upsert 往返不丢信息，以及用户端「我的售后 / 详情」所依赖的 Mapper SQL
 * 按订单归属（orders.customer_id）正确过滤——非本人退款单不应出现在本人的列表 / 详情投影中。
 *
 * <p>注意：本测试继承 {@link BaseIntegrationTest}，加载的是持久层切片
 * {@code PersistenceTestApplication}（只装配 Repository + Mapper，不加载 @Service 层），
 * 因此归属隔离的业务判定（非本人抛 NOT_FOUND）由单元测试 {@code RefundQueryServiceImplTest} 覆盖，
 * 此处只验证 SQL 层过滤的正确性。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class DbRefundRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private RefundOrderMapper refundOrderMapper;
    @Autowired
    private OrderMapper orderMapper;

    private long insertOwnedOrder(String orderNo, long customerId) {
        OrderPO o = new OrderPO();
        o.setOrderNo(orderNo);
        o.setCustomerId(customerId);
        o.setIdemKey("idem-" + orderNo);
        o.setStatus("WAIT_SHIP");
        o.setPayStatus("PAID");
        o.setPayableCent(5000L);
        orderMapper.insert(o);
        return o.getId();
    }

    @Test
    @DisplayName("save 新单 INSERT、读回 orderId/orderNo；用户端 list/detail SQL 真查；非本人单不混入")
    void refundRoundTripAndUserScopedQuery() {
        long orderId = insertOwnedOrder("IT-RF-ORDER", 9002L);

        RefundOrder o = new RefundOrder("RF-IT-1", "IT-RF-ORDER", orderId, 5000L, "REFUND", "质量原因",
                RefundStatus.PENDING, System.currentTimeMillis());
        refundRepository.save(o);

        // 写库后可读回，且 orderId / orderNo 正确回填
        RefundOrder loaded = refundRepository.findByRefundNo("RF-IT-1").orElseThrow();
        assertThat(loaded.getOrderId()).isEqualTo(orderId);
        assertThat(loaded.getOrderNo()).isEqualTo("IT-RF-ORDER");
        assertThat(loaded.getStatus()).isEqualTo(RefundStatus.PENDING);

        // 用户端列表 SQL 真查（按 orders.customer_id 过滤）
        Page<RefundListPO> page = new Page<>(1, 10);
        refundOrderMapper.selectMyPage(page, 9002L, null);
        assertThat(page.getRecords()).extracting(RefundListPO::getRefundNo).contains("RF-IT-1");
        assertThat(refundOrderMapper.countMy(9002L, null)).isEqualTo(1L);

        // 详情 SQL 真查：投影出归属客户与申诉次数
        RefundDetailPO detail = refundOrderMapper.selectDetailByRefundNo("RF-IT-1");
        assertThat(detail).isNotNull();
        assertThat(detail.getCustomerId()).isEqualTo(9002L);
        assertThat(detail.getAppealCount()).isZero();

        // 审核 / 回调推进状态：UPDATE 分支（不重复 INSERT）
        o.setStatus(RefundStatus.REFUNDING);
        refundRepository.save(o);
        RefundOrder updated = refundRepository.findByRefundNo("RF-IT-1").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RefundStatus.REFUNDING);
    }

    @Test
    @DisplayName("用户端列表 SQL：仅返回归属客户自身退款单，他人单不泄露")
    void listScopedToOwningCustomer() {
        long mine = insertOwnedOrder("IT-RF-MINE", 9002L);
        long others = insertOwnedOrder("IT-RF-OTHER", 9003L);

        refundRepository.save(new RefundOrder("RF-MINE", "IT-RF-MINE", mine, 3000L, "REFUND", "r",
                RefundStatus.REFUNDED, System.currentTimeMillis()));
        refundRepository.save(new RefundOrder("RF-OTHER", "IT-RF-OTHER", others, 4000L, "REFUND", "r",
                RefundStatus.REFUNDED, System.currentTimeMillis()));

        Page<RefundListPO> minePage = new Page<>(1, 10);
        refundOrderMapper.selectMyPage(minePage, 9002L, null);
        assertThat(minePage.getRecords()).extracting(RefundListPO::getRefundNo).containsExactly("RF-MINE");

        Page<RefundListPO> otherPage = new Page<>(1, 10);
        refundOrderMapper.selectMyPage(otherPage, 9003L, null);
        assertThat(otherPage.getRecords()).extracting(RefundListPO::getRefundNo).containsExactly("RF-OTHER");

        // SQL 层：非本人单的归属客户确为他人，业务层据此判定不可见
        RefundDetailPO otherDetail = refundOrderMapper.selectDetailByRefundNo("RF-OTHER");
        assertThat(otherDetail.getCustomerId()).isEqualTo(9003L);
    }
}
