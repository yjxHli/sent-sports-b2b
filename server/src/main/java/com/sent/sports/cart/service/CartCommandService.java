package com.sent.sports.cart.service;

import com.sent.sports.cart.dto.CartDtos;

import java.util.List;

/**
 * 购物车命令服务接口（P4-13 契约）。
 * <p>当前由 {@code CartCommandServiceImpl} 桩实现占位；实体/仓储在 Phase 5 接入。
 * 结算试算走 {@code CartQuoteUseCase}（P3-05，同模块），不在本接口内。</p>
 */
public interface CartCommandService {

    /** 加购：同 SKU 去重合并（P5 实现），当前桩返回占位项。 */
    CartDtos.CartItemVO addItem(CartDtos.AddItemCmd cmd);

    /** 改量 / 勾选。 */
    CartDtos.CartItemVO updateQty(Long itemId, CartDtos.UpdateQtyCmd cmd);

    /** 删除行。 */
    void removeItem(Long itemId);

    /** 购物车列表（含实时试算前置数据）。 */
    List<CartDtos.CartItemVO> list();
}
