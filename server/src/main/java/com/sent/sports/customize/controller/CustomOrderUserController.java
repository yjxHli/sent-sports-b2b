package com.sent.sports.customize.controller;

import com.sent.sports.common.dto.admin.BatchDeleteDtos;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.service.BatchDeleteCustomOrdersUseCase;
import com.sent.sports.customize.service.CustomQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端「我的定制单」控制器（收口阶段）：{@code GET /api/custom-orders/my}。
 * <p>从 {@code AuthFilter} 注入的 {@link CurrentUser} 取客户身份（aud=user），
 * 经同模块查询门面读取分页列表（联查订单状态与图稿版本）。未携带 user 端令牌按匿名拒绝（40101）。</p>
 */
@RestController
@RequestMapping("/api/custom-orders")
public class CustomOrderUserController {

    private final CustomQueryService customQueryService;
    private final BatchDeleteCustomOrdersUseCase batchDeleteUseCase;

    public CustomOrderUserController(CustomQueryService customQueryService,
                                     BatchDeleteCustomOrdersUseCase batchDeleteUseCase) {
        this.customQueryService = customQueryService;
        this.batchDeleteUseCase = batchDeleteUseCase;
    }

    @GetMapping("/my")
    public R<PageResult<CustomDtos.MyCustomOrderVO>> my(@RequestAttribute(value = CurrentUser.ATTR,
            required = false) CurrentUser user, @ModelAttribute PageQuery pageQuery) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return R.ok(customQueryService.myOrders(user.userId(), pageQuery));
    }

    /**
     * 订单详情页的「UV 定制」卡片：{@code GET /api/custom-orders/by-order/{orderNo}}。
     * <p>商品页勾选 UV 定制后提示「下单后上传图稿」，订单详情必须能直接看到定制单并上传，
     * 不该逼用户去「我的定制」里自己找。归属校验下沉到查询（只返回本客户订单的定制单）。</p>
     */
    @GetMapping("/by-order/{orderNo}")
    public R<java.util.List<CustomDtos.OrderCustomVO>> byOrder(@RequestAttribute(value = CurrentUser.ATTR,
            required = false) CurrentUser user, @PathVariable String orderNo) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return R.ok(customQueryService.ordersOfOrder(user.userId(), orderNo));
    }

    /**
     * 我的定制「多选删除」：{@code POST /api/custom-orders/batch-delete}，体 {@code {"ids":["CU..."]}}。
     *
     * <p>与订单侧同构：能删的（已发货/已取消）照删，进行中的逐条回原因，整批不会因一条不可删而失败。
     * 身份只认 {@code aud=user} 的令牌——定制单承载图稿与审图 SLA，管理员要清理走管理端
     * {@code /api/admin/custom/batch-delete}（写的是 {@code deleted} 列，两边互不影响）。</p>
     */
    @PostMapping("/batch-delete")
    public R<CustomDtos.BatchDeleteResult> batchDelete(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @Valid @RequestBody BatchDeleteDtos.BatchDeleteCmd cmd) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        var result = batchDeleteUseCase.delete(cmd.ids(), user.userId());
        return R.ok(new CustomDtos.BatchDeleteResult(result.deleted(),
                result.skipped().stream()
                        .map(s -> new CustomDtos.SkippedItem(s.orderNo(), s.reason()))
                        .toList()));
    }
}
