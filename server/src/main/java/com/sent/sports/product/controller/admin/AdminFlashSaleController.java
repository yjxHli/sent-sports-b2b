package com.sent.sports.product.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.product.dto.admin.FlashSaleDtos;
import com.sent.sports.product.service.admin.FlashSaleAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 秒杀活动管理控制器（V54）。
 * <p>权限复用 {@code product:update}：秒杀属于商品运营的一部分，运营角色已有该权限；
 * 单独开 {@code product:flash-sale} 需要同步权限种子与角色授权，收益不足以抵消配置漂移风险。</p>
 */
@RestController
@RequestMapping("/api/admin/flash-sales")
public class AdminFlashSaleController {

    private final FlashSaleAdminService flashSaleAdminService;

    public AdminFlashSaleController(FlashSaleAdminService flashSaleAdminService) {
        this.flashSaleAdminService = flashSaleAdminService;
    }

    @GetMapping
    @RequirePerm("product:read")
    public R<PageResult<FlashSaleDtos.ActivityVO>> list(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.ok(flashSaleAdminService.list(page, keyword, status));
    }

    @GetMapping("/{id}")
    @RequirePerm("product:read")
    public R<FlashSaleDtos.ActivityVO> detail(@PathVariable Long id) {
        return R.ok(flashSaleAdminService.detail(id));
    }

    @PostMapping
    @RequirePerm("product:update")
    public R<FlashSaleDtos.ActivityVO> create(@Valid @RequestBody FlashSaleDtos.ActivityForm form) {
        return R.ok(flashSaleAdminService.create(form));
    }

    @PutMapping("/{id}")
    @RequirePerm("product:update")
    public R<FlashSaleDtos.ActivityVO> update(@PathVariable Long id,
                                              @Valid @RequestBody FlashSaleDtos.ActivityForm form) {
        return R.ok(flashSaleAdminService.update(id, form));
    }

    /** 发布（active=true）/ 下线（active=false）。 */
    @PostMapping("/{id}/status")
    @RequirePerm("product:update")
    public R<FlashSaleDtos.ActivityVO> setStatus(@PathVariable Long id,
                                                 @RequestParam boolean active) {
        return R.ok(flashSaleAdminService.setStatus(id, active));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("product:update")
    public R<Void> delete(@PathVariable Long id) {
        flashSaleAdminService.delete(id);
        return R.ok();
    }
}
