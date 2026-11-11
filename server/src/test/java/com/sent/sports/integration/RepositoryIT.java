package com.sent.sports.integration;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.domain.PaymentRecord;
import com.sent.sports.finance.domain.Receivable;
import com.sent.sports.finance.domain.ReceivableStatus;
import com.sent.sports.finance.domain.WriteOff;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.repository.PaymentRecordRepository;
import com.sent.sports.finance.repository.ReceivableRepository;
import com.sent.sports.finance.repository.WriteOffRepository;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.mapper.StockFlowMapper;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.support.BaseIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 仓储适配器集成测试（P5-19 ~ P5-22）：验证 domain ↔ PO 双向转换在真实库上「不丢信息」，
 * 以及用例层依赖的三条硬语义：幂等键冲突、乐观锁冲突、原子扣减失败。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class RepositoryIT extends BaseIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ChatSessionRepository chatSessionRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ReceivableRepository receivableRepository;
    @Autowired
    private PaymentRecordRepository paymentRecordRepository;
    @Autowired
    private WriteOffRepository writeOffRepository;
    @Autowired
    private StockMapper stockMapper;
    @Autowired
    private StockFlowMapper stockFlowMapper;
    @Autowired
    private ReceivableMapper receivableMapper;

    @Test
    @DisplayName("P5-19：订单往返不丢信息；更新走乐观锁，版本递增")
    void orderRoundTripAndOptimisticUpdate() {
        Order order = newOrder("O1001", "idem-1001");
        orderRepository.save(order);

        Order loaded = orderRepository.findByOrderNo("O1001").orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.WAIT_PAY);
        assertThat(loaded.getItems()).hasSize(2);
        assertThat(loaded.getItems().get(0).getUnitPriceCent()).isEqualTo(9900L);
        assertThat(loaded.getItems().get(0).getLineTotalCent()).isEqualTo(19800L);
        assertThat(loaded.getVersion()).isZero();

        loaded.setStatus(OrderStatus.WAIT_SHIP);
        orderRepository.save(loaded);

        Order updated = orderRepository.findByOrderNo("O1001").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.WAIT_SHIP);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("P5-19：同一幂等键重复落库被拒（DUPLICATE_PROCESSING）")
    void orderIdemKeyConflictIsRejected() {
        orderRepository.save(newOrder("O1002", "idem-1002"));

        Order duplicate = newOrder("O1003", "idem-1002");
        assertThatThrownBy(() -> orderRepository.save(duplicate))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PROCESSING);
    }

    @Test
    @DisplayName("P5-20：预占不足抛 STOCK_NOT_ENOUGH；整单释放按订单行扣回预占")
    void stockLockAndReleaseAreGuarded() {
        stockMapper.insertIfAbsent(6001L, 0);
        stockMapper.adjustPhysical(6001L, 5);

        assertThatThrownBy(() -> stockRepository.lock(6001L, 6))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH);

        stockRepository.lock(6001L, 3);
        StockItem item = stockRepository.findBySku(6001L).orElseThrow();
        assertThat(item.getLockedQty()).isEqualTo(3);
        assertThat(item.availableQty()).isEqualTo(2);

        // 落一张订单用于整单释放（订单行 2 件，与上面的预占量一致）
        stockMapper.adjustPhysical(6001L, 5);
        stockRepository.lock(6001L, 2);
        orderRepository.save(newOrder("O2001", "idem-2001"));

        stockRepository.releaseByOrderNo("O2001");
        stockRepository.releaseByOrderNo("O2001"); // 第二次被 uk_flow_biz 拦下，不会重复释放
        assertThat(stockRepository.findBySku(6001L).orElseThrow().getLockedQty()).isEqualTo(3);
        assertThat(stockFlowMapper.selectByBizNo("O2001")).hasSize(1);

        StockItem current = stockRepository.findBySku(6001L).orElseThrow();
        stockRepository.save(6001L, new StockItem(current.getPhysicalQty(), 0, 0, 0));
        assertThat(stockRepository.findBySku(6001L).orElseThrow().getLockedQty()).isZero();
    }

    @Test
    @DisplayName("P5-22：会话先发号后落库，状态与消息均可往返；消息按客户端号幂等")
    void chatSessionAndMessageRoundTrip() {
        long sessionId = chatSessionRepository.nextId();
        ChatSession session = new ChatSession(sessionId, 88L);
        chatSessionRepository.save(session);

        ChatSession loaded = chatSessionRepository.findById(sessionId).orElseThrow();
        assertThat(loaded.getCustomerId()).isEqualTo(88L);
        assertThat(loaded.getStatus()).isEqualTo(ChatSessionStatus.WAITING);

        loaded.assign(7L);
        chatSessionRepository.save(loaded);
        assertThat(chatSessionRepository.findById(sessionId).orElseThrow().getStatus())
                .isEqualTo(ChatSessionStatus.ACTIVE);
        assertThat(chatSessionRepository.findActiveByStaff(7L)).hasSize(1);
        assertThat(chatSessionRepository.countActive()).isEqualTo(1);

        chatMessageRepository.save(new ChatMessage("M0001", sessionId, SenderType.CUSTOMER, "在吗",
                "client-1", 1, System.currentTimeMillis()));
        chatMessageRepository.save(new ChatMessage("M0002", sessionId, SenderType.STAFF, "在的",
                "client-2", 2, System.currentTimeMillis()));

        assertThat(chatMessageRepository.findBySessionAndClientMsgId(sessionId, "client-2"))
                .get()
                .extracting(ChatMessage::getContent)
                .isEqualTo("在的");
        assertThat(chatMessageRepository.findBySessionId(sessionId)).hasSize(2);

        // 回归：结束会话后必须能重新加载（历史上 toDomain 会重跑 close() 状态机 → STATE_CONFLICT）
        ChatSession active = chatSessionRepository.findById(sessionId).orElseThrow();
        active.close("DONE");
        chatSessionRepository.save(active);
        ChatSession closed = chatSessionRepository.findById(sessionId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(ChatSessionStatus.CLOSED);
        assertThat(closed.getCloseReason()).isEqualTo("DONE");
        assertThat(chatSessionRepository.findByCustomerId(88L)).hasSize(1);
    }

    @Test
    @DisplayName("P5-21：回款单登记/查询、核销多行聚合、应收核销进度落库")
    void financeAggregatesRoundTrip() {
        paymentRecordRepository.save(new PaymentRecord("RC0001", 88L, 10000L, 0L, "BANK-1",
                PaymentRecord.STATUS_PENDING, 7L, System.currentTimeMillis(), "首笔回款"));
        PaymentRecord record = paymentRecordRepository.findByPaymentNo("RC0001").orElseThrow();
        assertThat(record.getAmountCent()).isEqualTo(10000L);
        assertThat(record.getRemark()).isEqualTo("首笔回款");
        assertThat(paymentRecordRepository.existsBySerialNo("BANK-1")).isTrue();
        assertThat(paymentRecordRepository.existsBySerialNo("BANK-X")).isFalse();

        receivableMapper.insertIfAbsent(3001L, 88L, 6000L, LocalDate.now().plusDays(30), "NORMAL");
        receivableMapper.insertIfAbsent(3002L, 88L, 4000L, LocalDate.now().plusDays(30), "NORMAL");
        Receivable first = receivableRepository.findAllByCustomerId(88L).get(0);

        first.receive(6000L);
        receivableRepository.save(first);
        Receivable reloaded = receivableRepository.findById(first.getId()).orElseThrow();
        assertThat(reloaded.getReceivedCent()).isEqualTo(6000L);
        assertThat(reloaded.getStatus()).isEqualTo(ReceivableStatus.CLOSED);

        writeOffRepository.save(new WriteOff("WO0001", "RC0001", 88L, 10000L,
                List.of(new WriteOff.Item(first.getId(), 6000L), new WriteOff.Item(first.getId() + 1, 4000L)),
                System.currentTimeMillis(), 7L));

        List<WriteOff> writeOffs = writeOffRepository.findByPaymentNo("RC0001");
        assertThat(writeOffs).hasSize(1);
        assertThat(writeOffs.get(0).getTotalCent()).isEqualTo(10000L);
        assertThat(writeOffs.get(0).getItems()).hasSize(2);
    }

    private Order newOrder(String orderNo, String idemKey) {
        List<OrderItem> items = List.of(
                new OrderItem(6001L, 2, 9900L, "RETAIL", 19800L),
                new OrderItem(6002L, 1, 5000L, "RETAIL", 5000L));
        return new Order(orderNo, 88L, idemKey, items, 24800L, 0L, 0L, 0L, 0L, 24800L,
                System.currentTimeMillis());
    }
}
