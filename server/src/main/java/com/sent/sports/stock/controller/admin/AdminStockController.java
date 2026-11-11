package com.sent.sports.stock.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.stock.dto.admin.AdminStockDtos;
import com.sent.sports.stock.service.InboundUseCase;
import com.sent.sports.stock.service.LossUseCase;
import com.sent.sports.stock.service.StockCheckUseCase;
import com.sent.sports.stock.service.StockQueryService;
import com.sent.sports.stock.service.dto.ApproveCheckCmd;
import com.sent.sports.stock.service.dto.ApproveCheckResult;
import com.sent.sports.stock.service.dto.CreateCheckCmd;
import com.sent.sports.stock.service.dto.CreateCheckResult;
import com.sent.sports.stock.service.dto.InboundCmd;
import com.sent.sports.stock.service.dto.InboundResult;
import com.sent.sports.stock.service.dto.LossCmd;
import com.sent.sports.stock.service.dto.LossResult;
import com.sent.sports.stock.service.dto.RecordCheckCmd;
import com.sent.sports.stock.service.dto.RecordCheckResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存管理控制器（P4-37）：列表 / 流水 / 入库 / 报损 / 盘点（创建·录入·审批）。
 * <p>依赖同模块用例（{@link InboundUseCase} / {@link LossUseCase} / {@link StockCheckUseCase}）
 * 与查询门面 {@link StockQueryService}（桩，P5 接入仓储）。操作人绑定（@CurrentUserArg）在 Phase 5，
 * 现以占位常量 {@code OPERATOR_PLACEHOLDER} 传入用例（P5 绑定当前用户）。</p>
 */
@RestController
@RequestMapping("/api/admin/stock")
public class AdminStockController {

    private final InboundUseCase inboundUseCase;
    private final LossUseCase lossUseCase;
    private final StockCheckUseCase stockCheckUseCase;
    private final StockQueryService stockQueryService;

    /** P5 绑定当前用户：阶段占位操作人 ID。 */
    private static final long OPERATOR_PLACEHOLDER = 0L;

    public AdminStockController(InboundUseCase inboundUseCase, LossUseCase lossUseCase,
                                StockCheckUseCase stockCheckUseCase, StockQueryService stockQueryService) {
        this.inboundUseCase = inboundUseCase;
        this.lossUseCase = lossUseCase;
        this.stockCheckUseCase = stockCheckUseCase;
        this.stockQueryService = stockQueryService;
    }

    @GetMapping
    public R<PageResult<?>> list(@ModelAttribute PageQuery page,
                                 @RequestParam(required = false) Long skuId) {
        return R.ok(stockQueryService.list(page, skuId));
    }

    @GetMapping("/flows")
    public R<PageResult<?>> flows(@ModelAttribute PageQuery page,
                                  @RequestParam(required = false) Long skuId,
                                  @RequestParam(required = false) String bizType) {
        return R.ok(stockQueryService.flows(page, skuId, bizType));
    }

    @PostMapping("/inbound")
    @RequirePerm("stock:inbound")
    public R<AdminStockDtos.InboundVO> inbound(@Valid @RequestBody AdminStockDtos.InboundCmd cmd) {
        InboundResult r = inboundUseCase.inbound(new InboundCmd(
                cmd.skuId(), cmd.qty(), cmd.reason(), OPERATOR_PLACEHOLDER, cmd.remark()));
        return R.ok(new AdminStockDtos.InboundVO(r.skuId(), r.afterPhysicalQty(), r.afterAvailableQty()));
    }

    @PostMapping("/loss")
    @RequirePerm("stock:loss")
    public R<AdminStockDtos.LossVO> loss(@Valid @RequestBody AdminStockDtos.LossCmd cmd) {
        LossResult r = lossUseCase.loss(new LossCmd(
                cmd.skuId(), cmd.qty(), cmd.reason(), OPERATOR_PLACEHOLDER, cmd.remark()));
        return R.ok(new AdminStockDtos.LossVO(r.skuId(), r.afterPhysicalQty(), r.afterAvailableQty()));
    }

    @PostMapping("/checks")
    @RequirePerm("stock:check")
    public R<AdminStockDtos.CheckVO> createCheck(@Valid @RequestBody AdminStockDtos.CheckCreateCmd cmd) {
        CreateCheckResult r = stockCheckUseCase.create(new CreateCheckCmd(cmd.skuId(), OPERATOR_PLACEHOLDER));
        return R.ok(new AdminStockDtos.CheckVO(r.checkNo(), r.skuId(), r.bookQty(), r.status()));
    }

    @PutMapping("/checks/{id}/count")
    @RequirePerm("stock:check")
    public R<AdminStockDtos.CheckCountVO> count(@PathVariable String id,
                                                @Valid @RequestBody AdminStockDtos.CheckCountCmd cmd) {
        RecordCheckResult r = stockCheckUseCase.record(new RecordCheckCmd(id, cmd.actualQty(), OPERATOR_PLACEHOLDER));
        return R.ok(new AdminStockDtos.CheckCountVO(r.checkNo(), r.bookQty(), r.actualQty(), r.diffQty(), r.status()));
    }

    @PostMapping("/checks/{id}/approve")
    @RequirePerm("stock:check:approve")
    public R<AdminStockDtos.CheckApproveVO> approve(@PathVariable String id,
                                                    @RequestBody AdminStockDtos.CheckApproveCmd cmd) {
        // 确认令牌由 P5 确认令牌拦截器校验（CONFIRM_TOKEN_INVALID 40302），此处仅透传，不做令牌逻辑
        ApproveCheckResult r = stockCheckUseCase.approve(new ApproveCheckCmd(id, OPERATOR_PLACEHOLDER));
        return R.ok(new AdminStockDtos.CheckApproveVO(r.checkNo(), r.status(), r.afterPhysicalQty()));
    }
}
