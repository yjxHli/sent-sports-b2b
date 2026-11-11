package com.sent.sports.cart.controller;

import com.sent.sports.cart.dto.CartDtos;
import com.sent.sports.cart.service.CartCommandService;
import com.sent.sports.cart.service.CartQuoteUseCase;
import com.sent.sports.cart.service.dto.CartQuoteCmd;
import com.sent.sports.cart.service.dto.CartQuoteResult;
import com.sent.sports.cart.service.dto.QuoteItemCmd;
import com.sent.sports.cart.service.dto.QuoteLine;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车控制器（P4-13）：加购 / 改量 / 删除 / 列表 / 结算试算（plan §8.4）。
 * <p>写操作依赖同模块命令服务 {@link CartCommandService}（桩，P5 接入仓储）；
 * 结算试算委托给用例 {@link CartQuoteUseCase}（P3-05，编排取价/运费/积分）。</p>
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartCommandService cartCommandService;
    private final CartQuoteUseCase cartQuoteUseCase;

    public CartController(CartCommandService cartCommandService, CartQuoteUseCase cartQuoteUseCase) {
        this.cartCommandService = cartCommandService;
        this.cartQuoteUseCase = cartQuoteUseCase;
    }

    @GetMapping
    public R<List<CartDtos.CartItemVO>> list() {
        return R.ok(cartCommandService.list());
    }

    @PostMapping("/items")
    @RequirePerm("cart:add")
    public R<CartDtos.CartItemVO> addItem(@Valid @RequestBody CartDtos.AddItemCmd cmd) {
        return R.ok(cartCommandService.addItem(cmd));
    }

    @PutMapping("/items/{id}")
    @RequirePerm("cart:update")
    public R<CartDtos.CartItemVO> updateQty(@PathVariable Long id,
                                            @Valid @RequestBody CartDtos.UpdateQtyCmd cmd) {
        return R.ok(cartCommandService.updateQty(id, cmd));
    }

    @DeleteMapping("/items/{id}")
    @RequirePerm("cart:update")
    public R<Void> removeItem(@PathVariable Long id) {
        cartCommandService.removeItem(id);
        return R.ok();
    }

    @PostMapping("/checkout-preview")
    public R<CartDtos.QuoteVO> checkoutPreview(@Valid @RequestBody CartDtos.CheckoutPreviewCmd cmd) {
        CartQuoteCmd quoteCmd = new CartQuoteCmd(cmd.availablePoints(), cmd.freightFreeThresholdCent(),
                cmd.freightFeeCent(), toQuoteItems(cmd.items()));
        return R.ok(toQuoteVo(cartQuoteUseCase.quote(quoteCmd)));
    }

    private List<QuoteItemCmd> toQuoteItems(List<CartDtos.PreviewItemCmd> items) {
        return items.stream().map(i -> new QuoteItemCmd(i.skuId(), i.qty(), i.customerTypeCode(),
                i.memberDiscountRate(), i.agreementDiscountRate(), i.allowStacking(), i.ladderTiers())).toList();
    }

    private CartDtos.QuoteVO toQuoteVo(CartQuoteResult r) {
        List<CartDtos.QuoteLineVO> lines = r.lines().stream().map(this::toLineVo).toList();
        return new CartDtos.QuoteVO(lines,
                r.goodsAmountCent(), MoneyUtil.toYuan(r.goodsAmountCent()),
                r.freightCent(), MoneyUtil.toYuan(r.freightCent()),
                r.pointDeductCent(), MoneyUtil.toYuan(r.pointDeductCent()),
                r.payableCent(), MoneyUtil.toYuan(r.payableCent()));
    }

    private CartDtos.QuoteLineVO toLineVo(QuoteLine l) {
        return new CartDtos.QuoteLineVO(l.skuId(), l.unitPriceCent(), MoneyUtil.toYuan(l.unitPriceCent()),
                l.sourceCode(), l.belowCost(), l.lineTotalCent(), MoneyUtil.toYuan(l.lineTotalCent()));
    }
}
