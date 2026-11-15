package com.sent.sports.customer.controller.user;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.customer.service.AddressService;
import com.sent.sports.customer.service.dto.AddressCmd;
import com.sent.sports.customer.service.dto.AddressVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端收货地址簿（{@code /api/addresses}，2026-11-15）。
 *
 * <p>结算页要能「选地址 / 加地址 / 删地址 / 设默认」，所以这里是标准 CRUD。
 * 身份一律取 {@code AuthFilter} 注入的 {@link CurrentUser}（aud=user），
 * <b>绝不由请求体传 customerId</b>——否则换个 ID 就能读写别人的地址簿。</p>
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public R<List<AddressVO>> list(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(addressService.list(customerIdOf(user)));
    }

    @PostMapping
    public R<AddressVO> create(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @RequestBody AddressCmd cmd) {
        return R.ok(addressService.create(customerIdOf(user), cmd));
    }

    @PutMapping("/{id}")
    public R<AddressVO> update(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @PathVariable long id,
            @RequestBody AddressCmd cmd) {
        return R.ok(addressService.update(customerIdOf(user), id, cmd));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @PathVariable long id) {
        addressService.delete(customerIdOf(user), id);
        return R.ok();
    }

    /** 设为默认：结算页据此预选，默认地址全局唯一。 */
    @PutMapping("/{id}/default")
    public R<Void> setDefault(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @PathVariable long id) {
        addressService.setDefault(customerIdOf(user), id);
        return R.ok();
    }

    /** 只认 user 端令牌；管理端令牌访问个人地址簿一律 401，避免两套身份混用。 */
    private long customerIdOf(CurrentUser user) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user.userId();
    }
}
