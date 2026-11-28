package com.sent.sports.finance;

import com.sent.sports.finance.mapper.ContractMapper;
import com.sent.sports.finance.mapper.po.ContractRemindRow;
import com.sent.sports.finance.service.dto.ContractReminder;
import com.sent.sports.finance.service.impl.ContractJobServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 合同提醒取数：30 天窗口按剩余天数分档，已提醒过的档位不再返回。 */
@ExtendWith(MockitoExtension.class)
class ContractJobServiceImplTest {

    @Mock
    private ContractMapper contractMapper;

    @Test
    @DisplayName("30/7/到期三档：按剩余天数算目标档，只返回尚未提醒的行")
    void classifiesRemindLevelsAndSkipsAlreadyReminded() {
        LocalDate today = LocalDate.of(2026, 11, 4);
        when(contractMapper.selectExpiring(today)).thenReturn(List.of(
                row(1L, 9002L, "CT-30", 150000L, LocalDate.of(2026, 11, 24), 0),
                row(2L, 9002L, "CT-7", 200000L, LocalDate.of(2026, 11, 9), 1),
                row(3L, 9002L, "CT-DUE", 300000L, today, 2),
                row(4L, 9002L, "CT-DONE", 400000L, LocalDate.of(2026, 11, 24), 1)));

        List<ContractReminder> list = new ContractJobServiceImpl(contractMapper).findExpiring(today);

        assertThat(list).extracting(ContractReminder::contractNo)
                .containsExactly("CT-30", "CT-7", "CT-DUE");
        assertThat(list).extracting(ContractReminder::remindLevel)
                .containsExactly(1, 2, 3);
        assertThat(list.get(0).amountCent()).isEqualTo(150000L);
        assertThat(list.get(0).expireDate()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    @DisplayName("条件更新把标记与提醒时间一起写入 finance 模块内，业务侧只看影响行数")
    void marksRemindedViaConditionalUpdate() {
        when(contractMapper.updateRemindLevel(5L, 2)).thenReturn(1);

        assertThat(new ContractJobServiceImpl(contractMapper).markReminded(5L, 2)).isEqualTo(1);
        verify(contractMapper).updateRemindLevel(5L, 2);
    }

    private static ContractRemindRow row(long id, long customerId, String contractNo,
                                         long amountCent, LocalDate expireDate, int level) {
        ContractRemindRow row = new ContractRemindRow();
        row.setContractId(id);
        row.setCustomerId(customerId);
        row.setContractNo(contractNo);
        row.setAmountCent(amountCent);
        row.setExpireDate(expireDate);
        row.setRemindLevel(level);
        return row;
    }
}
