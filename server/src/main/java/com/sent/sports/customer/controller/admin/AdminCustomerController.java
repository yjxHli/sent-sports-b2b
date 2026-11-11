package com.sent.sports.customer.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.customer.dto.admin.AdminCustomerDtos;
import com.sent.sports.customer.service.CustomerOperationService;
import com.sent.sports.customer.service.CustomerQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户与会员管理控制器（P4-46）：列表 / 详情 / 改派 / 授级 / 积分调整（plan §8.12）。
 *
 * <p>写动作统一走 {@link CustomerOperationService}（真库 customer 表 + 流水）。
 * 此前直连 P3 的 TransferCustomerUseCase / GrantLevelUseCase / AdjustPointUseCase，
 * 它们挂在进程内内存仓储上，真库客户一律查不到 → 40401（浏览器 404），
 * 且授级等级码与 member 枚举不一致会抛 50001。那三个用例仍服务于用户端 /api/member/*，
 * 此处不再调用，避免管理端与用户端互相牵连。</p>
 */
@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {

    private final CustomerOperationService customerOperationService;
    private final CustomerQueryService customerQueryService;

    public AdminCustomerController(CustomerOperationService customerOperationService,
                                   CustomerQueryService customerQueryService) {
        this.customerOperationService = customerOperationService;
        this.customerQueryService = customerQueryService;
    }

    @GetMapping
    public R<PageResult<?>> list(@ModelAttribute PageQuery page,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String level,
                                 @RequestParam(required = false) String type) {
        return R.ok(customerQueryService.list(page, keyword, level, type));
    }

    @GetMapping("/{id}")
    public R<Object> detail(@PathVariable long id) {
        return R.ok(customerQueryService.detail(id));
    }

    @PostMapping("/{id}/transfer")
    @RequirePerm("customer:transfer")
    public R<AdminCustomerDtos.TransferVO> transfer(@PathVariable long id,
                                                    @Valid @RequestBody AdminCustomerDtos.TransferCmd cmd) {
        return R.ok(customerOperationService.transfer(id, cmd.toStaffId(), cmd.reason()));
    }

    @PostMapping("/{id}/level")
    @RequirePerm("customer:grant")
    public R<AdminCustomerDtos.LevelVO> grantLevel(@PathVariable long id,
                                                   @Valid @RequestBody AdminCustomerDtos.GrantCmd cmd) {
        return R.ok(customerOperationService.grantLevel(
                id, cmd.level(), cmd.validToMillis(), cmd.reason()));
    }

    @PostMapping("/{id}/points")
    @RequirePerm("customer:adjust")
    public R<AdminCustomerDtos.PointVO> adjustPoints(@PathVariable long id,
                                                     @Valid @RequestBody AdminCustomerDtos.AdjustCmd cmd) {
        return R.ok(customerOperationService.adjustPoints(
                id, cmd.delta(), cmd.reason(), cmd.bizType(), cmd.bizNo()));
    }
}
