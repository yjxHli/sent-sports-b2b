package com.sent.sports.cart.service.impl;

import com.sent.sports.cart.dto.CartDtos;
import com.sent.sports.cart.mapper.CartMapper;
import com.sent.sports.cart.service.CartCommandService;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 购物车命令服务（任务 2）：从 P4 占位实现换成 {@code cart_item} 真库读写。
 *
 * <p>{@link CartCommandService} 的契约签名不带 customerId（P4 约定），因此主体从
 * {@code AuthFilter} 注入的 {@link CurrentUser} 读取；匿名访问列表返回空、写操作拒绝，
 * 由数据归属保证不会串号。</p>
 */
@Service
public class CartCommandServiceImpl implements CartCommandService {

    private final CartMapper cartMapper;

    public CartCommandServiceImpl(CartMapper cartMapper) {
        this.cartMapper = cartMapper;
    }

    @Override
    public CartDtos.CartItemVO addItem(CartDtos.AddItemCmd cmd) {
        long customerId = requireCustomer();
        Long price = cartMapper.selectRetailPrice(cmd.skuId());
        if (price == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SKU 不存在");
        }
        cartMapper.upsertItem(customerId, cmd.skuId(), cmd.qty(), price);
        Long itemId = cartMapper.selectItemId(customerId, cmd.skuId());
        String name = cartMapper.selectSkuName(cmd.skuId());
        return new CartDtos.CartItemVO(itemId, cmd.skuId(), name, cmd.qty(), true);
    }

    @Override
    public CartDtos.CartItemVO updateQty(Long itemId, CartDtos.UpdateQtyCmd cmd) {
        long customerId = requireCustomer();
        if (cartMapper.updateItem(customerId, itemId, cmd.qty(), cmd.selected()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return new CartDtos.CartItemVO(itemId, null, null, cmd.qty(), cmd.selected());
    }

    @Override
    public void removeItem(Long itemId) {
        long customerId = requireCustomer();
        cartMapper.deleteItem(customerId, itemId);
    }

    @Override
    public List<CartDtos.CartItemVO> list() {
        Long customerId = currentCustomerId();
        if (customerId == null) {
            return List.of();
        }
        return cartMapper.selectByCustomer(customerId).stream()
                .map(r -> new CartDtos.CartItemVO(r.getId(), r.getSkuId(), r.getSkuName(),
                        r.getQty() == null ? 0 : r.getQty(),
                        r.getSelected() != null && r.getSelected() == 1))
                .toList();
    }

    private long requireCustomer() {
        Long customerId = currentCustomerId();
        if (customerId == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return customerId;
    }

    private Long currentCustomerId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        Object value = request.getAttribute(CurrentUser.ATTR);
        return value instanceof CurrentUser current && !current.isAdmin() ? current.userId() : null;
    }
}
