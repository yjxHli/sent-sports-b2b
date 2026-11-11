package com.sent.sports.product.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.product.dto.admin.AdminProductDtos;
import com.sent.sports.product.service.admin.ProductAdminService;
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
 * 商品管理控制器（P4-34）：CRUD、上下架、阶梯价、一客一价（plan §8.10）。
 * <p>依赖同模块写侧服务 {@link ProductAdminService}（桩，P5 接入仓储）。
 * 写端点标 {@code @RequirePerm}（P5 拦截器生效前无害）。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminProductController {

    private final ProductAdminService productAdminService;

    public AdminProductController(ProductAdminService productAdminService) {
        this.productAdminService = productAdminService;
    }

    @GetMapping("/products")
    public R<PageResult<AdminProductDtos.ProductVO>> list(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean onShelf) {
        return R.ok(productAdminService.list(page, keyword, categoryId, onShelf));
    }

    @PostMapping("/products")
    @RequirePerm("product:create")
    public R<AdminProductDtos.ProductVO> create(@Valid @RequestBody AdminProductDtos.ProductForm form) {
        return R.ok(productAdminService.create(form));
    }

    @PutMapping("/products/{id}")
    @RequirePerm("product:update")
    public R<AdminProductDtos.ProductVO> update(@PathVariable Long id,
                                                @Valid @RequestBody AdminProductDtos.ProductForm form) {
        return R.ok(productAdminService.update(id, form));
    }

    @DeleteMapping("/products/{id}")
    @RequirePerm("product:update")
    public R<Void> delete(@PathVariable Long id) {
        productAdminService.delete(id);
        return R.ok();
    }

    @PostMapping("/products/{id}/publish")
    @RequirePerm("product:down")
    public R<AdminProductDtos.ProductVO> publish(@PathVariable Long id,
                                                 @Valid @RequestBody AdminProductDtos.PublishCmd cmd) {
        return R.ok(productAdminService.publish(id, cmd.onShelf()));
    }

    @PostMapping("/skus")
    @RequirePerm("product:update")
    public R<AdminProductDtos.SkuVO> addSku(@Valid @RequestBody AdminProductDtos.SkuForm form) {
        return R.ok(productAdminService.addSku(form));
    }

    @PutMapping("/skus/{id}")
    @RequirePerm("product:update")
    public R<AdminProductDtos.SkuVO> updateSku(@PathVariable Long id,
                                              @Valid @RequestBody AdminProductDtos.SkuForm form) {
        return R.ok(productAdminService.updateSku(id, form));
    }

    @DeleteMapping("/skus/{id}")
    @RequirePerm("product:update")
    public R<Void> deleteSku(@PathVariable Long id) {
        productAdminService.deleteSku(id);
        return R.ok();
    }

    @PutMapping("/skus/{id}/ladder-prices")
    @RequirePerm("product:update")
    public R<Void> configLadder(@PathVariable Long id, @Valid @RequestBody AdminProductDtos.LadderForm form) {
        productAdminService.configLadder(id, form.tiers());
        return R.ok();
    }

    /** 读取某 SKU 的既有阶梯价（折扣率），供编辑页回显与「删除折扣」。 */
    @GetMapping("/skus/{id}/ladder-prices")
    @RequirePerm("product:update")
    public R<AdminProductDtos.LadderVO> listLadderPrices(@PathVariable Long id) {
        return R.ok(new AdminProductDtos.LadderVO(id, productAdminService.listLadderPrices(id)));
    }

    @GetMapping("/customer-prices")
    @RequirePerm("product:update")
    public R<java.util.List<AdminProductDtos.CustomerPriceVO>> listCustomerPrices(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Long customerId) {
        return R.ok(productAdminService.listCustomerPrices(skuId, customerId));
    }

    @PostMapping("/customer-prices")
    @RequirePerm("product:update")
    public R<AdminProductDtos.CustomerPriceVO> upsertCustomerPrice(
            @Valid @RequestBody AdminProductDtos.CustomerPriceForm form) {
        return R.ok(productAdminService.upsertCustomerPrice(form));
    }

    @DeleteMapping("/customer-prices/{id}")
    @RequirePerm("product:update")
    public R<Void> deleteCustomerPrice(@PathVariable Long id) {
        productAdminService.deleteCustomerPrice(id);
        return R.ok();
    }
}
