package com.sent.sports.finance;

import com.sent.sports.finance.domain.ReceivableStatus;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.OverdueReceivableRow;
import com.sent.sports.finance.service.dto.OverdueReceivable;
import com.sent.sports.finance.service.impl.ReceivableJobServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivableJobServiceImplTest {

    @Mock
    private ReceivableMapper receivableMapper;

    @Test
    @DisplayName("逾期明细：带单号/客户/金额/到期日，且已是 OVERDUE 的行不再返回")
    void findsOverdueDetailsWithOrderNoAndSkipsAlreadyOverdue() {
        LocalDate today = LocalDate.of(2026, 11, 4);
        when(receivableMapper.selectOverdueDetails(today)).thenReturn(List.of(
                row(1L, 9002L, 150000L, LocalDate.of(2026, 11, 1), "SO-1", ReceivableStatus.NORMAL.name()),
                row(2L, 9002L, 200000L, LocalDate.of(2026, 10, 1), "SO-2", ReceivableStatus.OVERDUE.name())));

        List<OverdueReceivable> details = new ReceivableJobServiceImpl(receivableMapper)
                .findOverdueDetails(today);

        assertThat(details).hasSize(1);
        assertThat(details.get(0).receivableId()).isEqualTo(1L);
        assertThat(details.get(0).customerId()).isEqualTo(9002L);
        assertThat(details.get(0).amountCent()).isEqualTo(150000L);
        assertThat(details.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(details.get(0).orderNo()).isEqualTo("SO-1");
    }

    private OverdueReceivableRow row(long id, long customerId, long amountCent, LocalDate dueDate,
                                     String orderNo, String status) {
        OverdueReceivableRow po = new OverdueReceivableRow();
        po.setId(id);
        po.setCustomerId(customerId);
        po.setAmountCent(amountCent);
        po.setDueDate(dueDate);
        po.setStatus(status);
        po.setOrderNo(orderNo);
        return po;
    }
}
