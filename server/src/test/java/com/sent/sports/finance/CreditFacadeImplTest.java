package com.sent.sports.finance;

import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.ReceivablePO;
import com.sent.sports.finance.service.impl.CreditFacadeImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关单授信补偿：额度只在「确认收货 → 生成应收」时占用，
 * 因此仅当该订单确有应收记录才释放，绝不误减其它订单占用的额度。
 */
@ExtendWith(MockitoExtension.class)
class CreditFacadeImplTest {

    @Mock
    private EnterpriseProfileMapper enterpriseProfileMapper;
    @Mock
    private ReceivableMapper receivableMapper;

    @Test
    @DisplayName("有应收记录：按该单金额释放企业已用额度")
    void releasesCreditWhenReceivableExists() {
        when(receivableMapper.selectOrderIdByOrderNo("SO-1")).thenReturn(11L);
        ReceivablePO po = new ReceivablePO();
        po.setCustomerId(9002L);
        po.setAmountCent(20000L);
        when(receivableMapper.selectByOrderId(11L)).thenReturn(po);
        CreditFacadeImpl facade = new CreditFacadeImpl(enterpriseProfileMapper, receivableMapper);

        facade.releaseCreditForOrder("SO-1");

        verify(enterpriseProfileMapper).subtractCreditUsed(9002L, 20000L);
    }

    @Test
    @DisplayName("无应收（关单均发生在收货前）：不动额度")
    void skipsWhenNoReceivable() {
        when(receivableMapper.selectOrderIdByOrderNo("SO-1")).thenReturn(null);
        CreditFacadeImpl facade = new CreditFacadeImpl(enterpriseProfileMapper, receivableMapper);

        facade.releaseCreditForOrder("SO-1");

        verify(enterpriseProfileMapper, never()).subtractCreditUsed(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("查到订单但尚无应收行：同样不动额度")
    void skipsWhenReceivableRowMissing() {
        when(receivableMapper.selectOrderIdByOrderNo("SO-1")).thenReturn(11L);
        when(receivableMapper.selectByOrderId(11L)).thenReturn(null);
        CreditFacadeImpl facade = new CreditFacadeImpl(enterpriseProfileMapper, receivableMapper);

        facade.releaseCreditForOrder("SO-1");

        verify(enterpriseProfileMapper, never()).subtractCreditUsed(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }
}
