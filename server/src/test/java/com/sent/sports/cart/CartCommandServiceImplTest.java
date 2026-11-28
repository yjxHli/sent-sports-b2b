package com.sent.sports.cart;

import com.sent.sports.cart.dto.CartDtos;
import com.sent.sports.cart.mapper.CartMapper;
import com.sent.sports.cart.service.impl.CartCommandServiceImpl;
import com.sent.sports.common.web.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 购物车命令服务（TDD 红→绿）。
 *
 * <p>背景：个人中心「我的森特」的购物车卡要和「收藏 / 足迹」两卡保持同一口径
 * —— 商品图片 + 商品名称 + 商品价格。购物车接口原先只回 {@code skuName / qty}，
 * 前端只能画占位图、也没有价格可展示，因此列表行必须补齐
 * {@code mainImage}（SPU 主图）与 {@code unitPriceCent / unitPriceYuan}（加入时的价格快照）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CartCommandServiceImplTest {

    private static final long CUSTOMER = 9002L;

    @Mock
    private CartMapper cartMapper;

    @InjectMocks
    private CartCommandServiceImpl service;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void login() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, CUSTOMER, "jti-test"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("购物车列表带出商品主图与价格快照（个人中心购物车卡要显示图/名/价）")
    void listCarriesMainImageAndSnapshotPrice() {
        login();
        CartMapper.CartRow row = new CartMapper.CartRow();
        row.setId(11L);
        row.setSkuId(22L);
        row.setQty(2);
        row.setSelected(1);
        row.setPriceSnapshotCent(3990L);
        row.setSkuName("斯伯丁篮球 7号");
        row.setMainImage("file-main-image-1");
        when(cartMapper.selectByCustomer(CUSTOMER)).thenReturn(List.of(row));

        List<CartDtos.CartItemVO> list = service.list();

        assertEquals(1, list.size());
        CartDtos.CartItemVO vo = list.get(0);
        assertEquals("斯伯丁篮球 7号", vo.skuName());
        assertEquals("file-main-image-1", vo.mainImage());
        assertEquals(3990L, vo.unitPriceCent());
        assertEquals("39.90", vo.unitPriceYuan());
    }

    @Test
    @DisplayName("价格快照为空时不抛异常：价格字段回落为 null，前端显示占位")
    void listToleratesMissingSnapshotPrice() {
        login();
        CartMapper.CartRow row = new CartMapper.CartRow();
        row.setId(12L);
        row.setSkuId(23L);
        row.setQty(1);
        row.setSelected(1);
        when(cartMapper.selectByCustomer(CUSTOMER)).thenReturn(List.of(row));

        CartDtos.CartItemVO vo = service.list().get(0);

        assertEquals(null, vo.unitPriceCent());
        assertEquals(null, vo.unitPriceYuan());
        assertEquals(null, vo.mainImage());
    }
}
