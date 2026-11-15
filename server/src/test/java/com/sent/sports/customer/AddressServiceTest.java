package com.sent.sports.customer;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.mapper.CustomerAddressMapper;
import com.sent.sports.customer.mapper.po.CustomerAddressPO;
import com.sent.sports.customer.service.dto.AddressCmd;
import com.sent.sports.customer.service.dto.AddressVO;
import com.sent.sports.customer.service.impl.AddressServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收货地址簿用例（2026-11-15，TDD 红）。
 *
 * <p>地址簿是订单快照的来源，三条口径必须守住：</p>
 * <ul>
 *   <li><b>每个人的地址互不可见</b>：所有读写都带 {@code customer_id}，他人地址统一 404；</li>
 *   <li><b>默认地址唯一</b>：设默认要把别人的默认清掉，否则结算页勾哪个全看 MySQL 心情；</li>
 *   <li><b>第一条自动成为默认</b>：新用户加完地址就能直接下单，不必再点一次「设为默认」。</li>
 * </ul>
 */
class AddressServiceTest extends BaseUnitTest {

    @Mock
    private CustomerAddressMapper addressMapper;

    private AddressServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AddressServiceImpl(addressMapper);
    }

    private CustomerAddressPO po(long id, long customerId, boolean isDefault) {
        CustomerAddressPO po = new CustomerAddressPO();
        po.setId(id);
        po.setCustomerId(customerId);
        po.setReceiver("张三");
        po.setPhone("13800138000");
        po.setProvince("江苏省");
        po.setCity("南京市");
        po.setDistrict("玄武区");
        po.setDetail("中山路 100 号");
        po.setIsDefault(isDefault ? 1 : 0);
        return po;
    }

    private AddressCmd cmd(boolean isDefault) {
        return new AddressCmd("张三", "13800138000", "江苏省", "南京市", "玄武区", "中山路 100 号", isDefault);
    }

    @Test
    @DisplayName("列表按默认优先返回，字段原样映射")
    void listMapsRows() {
        when(addressMapper.selectByCustomer(9002L))
                .thenReturn(List.of(po(1L, 9002L, true), po(2L, 9002L, false)));

        List<AddressVO> list = service.list(9002L);

        assertEquals(2, list.size());
        assertTrue(list.get(0).isDefault());
        assertEquals("张三", list.get(0).receiver());
        assertEquals("中山路 100 号", list.get(0).detail());
        assertEquals(2L, list.get(1).id());
    }

    @Test
    @DisplayName("第一条地址自动成为默认：新用户加完就能下单")
    void firstAddressBecomesDefault() {
        when(addressMapper.countByCustomer(9002L)).thenReturn(0);
        when(addressMapper.insert(any(CustomerAddressPO.class))).thenReturn(1);

        AddressVO vo = service.create(9002L, cmd(false));

        assertTrue(vo.isDefault(), "第一条地址必须自动默认");
        ArgumentCaptor<CustomerAddressPO> captor = ArgumentCaptor.forClass(CustomerAddressPO.class);
        verify(addressMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getIsDefault());
        assertEquals(9002L, captor.getValue().getCustomerId());
        // 只有一条地址时没必要去清别人的默认
        verify(addressMapper, never()).clearDefault(anyLong());
    }

    @Test
    @DisplayName("新增默认地址：先把旧默认清掉，再插入")
    void createDefaultClearsOldDefault() {
        when(addressMapper.countByCustomer(9002L)).thenReturn(2);
        when(addressMapper.insert(any(CustomerAddressPO.class))).thenReturn(1);

        service.create(9002L, cmd(true));

        verify(addressMapper).clearDefault(9002L);
    }

    @Test
    @DisplayName("新增非默认地址：不动默认标记")
    void createNonDefaultKeepsDefault() {
        when(addressMapper.countByCustomer(9002L)).thenReturn(2);
        when(addressMapper.insert(any(CustomerAddressPO.class))).thenReturn(1);

        AddressVO vo = service.create(9002L, cmd(false));

        assertEquals(false, vo.isDefault());
        verify(addressMapper, never()).clearDefault(anyLong());
    }

    @Test
    @DisplayName("设为默认：清旧默认 + 置新默认，两步都带 customer_id")
    void setDefaultClearsOthers() {
        when(addressMapper.selectOwned(5L, 9002L)).thenReturn(po(5L, 9002L, false));
        when(addressMapper.setDefault(5L, 9002L)).thenReturn(1);

        service.setDefault(9002L, 5L);

        verify(addressMapper).clearDefault(9002L);
        verify(addressMapper).setDefault(5L, 9002L);
    }

    @Test
    @DisplayName("他人地址一律 404：不能借 ID 探测别人的收货人")
    void foreignAddressIsNotFound() {
        when(addressMapper.selectOwned(9L, 9002L)).thenReturn(null);

        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BizException.class, () -> service.setDefault(9002L, 9L)).getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BizException.class, () -> service.delete(9002L, 9L)).getErrorCode());
        verify(addressMapper, never()).clearDefault(anyLong());
    }

    @Test
    @DisplayName("删掉默认地址后，剩下的某一条自动补位为默认")
    void deleteDefaultPromotesAnother() {
        when(addressMapper.selectOwned(5L, 9002L)).thenReturn(po(5L, 9002L, true));
        when(addressMapper.deleteOwned(5L, 9002L)).thenReturn(1);
        when(addressMapper.selectByCustomer(9002L)).thenReturn(List.of(po(6L, 9002L, false)));

        service.delete(9002L, 5L);

        verify(addressMapper).deleteOwned(5L, 9002L);
        verify(addressMapper).setDefault(6L, 9002L);
    }

    @Test
    @DisplayName("删除非默认地址不触发默认补位")
    void deleteNonDefaultSkipsPromotion() {
        when(addressMapper.selectOwned(6L, 9002L)).thenReturn(po(6L, 9002L, false));
        when(addressMapper.deleteOwned(6L, 9002L)).thenReturn(1);

        service.delete(9002L, 6L);

        verify(addressMapper, never()).setDefault(anyLong(), anyLong());
    }

    @Test
    @DisplayName("findOwned：没传 ID 直接空，不查库")
    void findOwnedWithNullId() {
        Optional<AddressVO> none = service.findOwned(9002L, null);

        assertTrue(none.isEmpty());
        verify(addressMapper, never()).selectOwned(anyLong(), anyLong());
    }
}
