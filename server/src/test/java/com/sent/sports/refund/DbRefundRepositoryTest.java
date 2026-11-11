package com.sent.sports.refund;

import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundOrderPO;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.repository.impl.RefundRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款仓储真库实现测试（收口阶段，TDD 绿）：save 为新单 INSERT / 已存在单 UPDATE，findByRefundNo 映射订单号。
 * （PO 字段映射的正确性由真实库的 DbRefundRepositoryIT 校验，避免 BaseMapper.insert 重载歧义。）
 */
@ExtendWith(MockitoExtension.class)
class DbRefundRepositoryTest {

    @Mock
    private RefundOrderMapper mapper;

    private RefundRepository repo;

    @BeforeEach
    void setUp() {
        repo = new RefundRepositoryImpl(mapper);
    }

    @Test
    @DisplayName("save 新单：走 INSERT 分支，不触发 UPDATE")
    void saveInsertsNewRefund() {
        when(mapper.selectByRefundNo("RF1")).thenReturn(null);

        RefundOrder o = new RefundOrder("RF1", "SO1", 11L, 5000L, "REFUND", "质量原因",
                RefundStatus.PENDING, 0L);
        repo.save(o);

        verify(mapper).insert(any(RefundOrderPO.class));
        verify(mapper, never()).updateRefundState(anyString(), anyString(), anyInt(), isNull());
    }

    @Test
    @DisplayName("save 已存在单：走 UPDATE 分支（状态 + 申诉次数），不重复 INSERT")
    void saveUpdatesExistingRefund() {
        RefundOrderPO existing = new RefundOrderPO();
        existing.setRefundNo("RF1");
        when(mapper.selectByRefundNo("RF1")).thenReturn(existing);

        RefundOrder o = new RefundOrder("RF1", "SO1", 11L, 5000L, "REFUND", "质量原因",
                RefundStatus.REFUNDING, 0L);
        o.setAppealCount(1);
        repo.save(o);

        verify(mapper).updateRefundState(eq("RF1"), eq("REFUNDING"), eq(1), isNull());
        verify(mapper, never()).insert(any(RefundOrderPO.class));
    }

    @Test
    @DisplayName("findByRefundNo：联查得到的 order_no / order_id 正确回填到领域对象")
    void findByRefundNoMapsOrderNoFromJoin() {
        RefundOrderPO po = new RefundOrderPO();
        po.setRefundNo("RF1");
        po.setOrderNo("SO1");
        po.setOrderId(11L);
        po.setAmountCent(5000L);
        po.setType("REFUND");
        po.setRemark("r");
        po.setStatus("REFUNDED");
        po.setAppealCount(0L);
        when(mapper.selectByRefundNo("RF1")).thenReturn(po);

        Optional<RefundOrder> opt = repo.findByRefundNo("RF1");

        assertTrue(opt.isPresent());
        assertEquals("SO1", opt.get().getOrderNo());
        assertEquals(11L, opt.get().getOrderId());
        assertEquals(RefundStatus.REFUNDED, opt.get().getStatus());
    }
}
