package com.sent.sports.customer.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.customer.dto.admin.CustomerTagDtos;
import com.sent.sports.customer.service.CustomerTagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户标签接口（V63）：客服打标 + 后台维护标签内容。
 *
 * <p>路径刻意分成两组，因为受众与权限完全不同：</p>
 * <ul>
 *   <li><b>{@code /api/admin/customer-tags}</b> 目录与维护：目录客服也要读（弹窗要选项），
 *       所以读路径挂 {@code chat:serve}；写路径（新增/改名/启停/删除）挂
 *       {@code customer:tag:manage} 且 <b>exact</b>——演示库里客服、销售、财务都持 {@code '*'}，
 *       不精确匹配的话「只有后台能决定有哪些标签」形同虚设，一人一套说法标签就废了。</li>
 *   <li><b>{@code /api/admin/customers/{id}/tags}</b> 某客户身上的标签：
 *       客服在会话头部打标/取消，挂 {@code chat:serve}。</li>
 * </ul>
 *
 * <p>操作人从 {@link CurrentUser} 取，取不到就传 null（写留痕时 {@code staff_id} 允许为空）——
 * 标签留痕宁可缺操作人，也不该因为取不到登录态就把打标本身失败掉。</p>
 *
 * <p><b>用户端不存在任何标签接口</b>：标签是内部评价，绝不能漏给客户。</p>
 */
@RestController
public class AdminCustomerTagController {

    private final CustomerTagService customerTagService;

    public AdminCustomerTagController(CustomerTagService customerTagService) {
        this.customerTagService = customerTagService;
    }

    /**
     * 标签目录。
     *
     * @param includeDisabled 管理端维护页要看到停用项（true）；客服弹窗默认只要可选项
     */
    @GetMapping("/api/admin/customer-tags")
    @RequirePerm("chat:serve")
    public R<List<CustomerTagDtos.TagVO>> catalog(
            @RequestParam(required = false, defaultValue = "false") boolean includeDisabled) {
        return R.ok(customerTagService.catalog(includeDisabled));
    }

    /** 某客户身上的标签（客服会话头部 chips）。 */
    @GetMapping("/api/admin/customers/{customerId}/tags")
    @RequirePerm("chat:serve")
    public R<List<CustomerTagDtos.TagVO>> tagsOf(@PathVariable long customerId) {
        return R.ok(customerTagService.tagsOf(customerId));
    }

    /**
     * 打标 / 取消：前端提交弹窗里的<b>全量勾选结果</b>，
     * 服务层按差异增删（空数组 = 全部取消），不需要前端自己算加了哪个删了哪个。
     */
    @PutMapping("/api/admin/customers/{customerId}/tags")
    @RequirePerm("chat:serve")
    public R<List<CustomerTagDtos.TagVO>> applyTags(
            @PathVariable long customerId,
            @Valid @RequestBody CustomerTagDtos.ApplyTagsCmd cmd,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(customerTagService.applyTags(customerId, cmd.tagIds(),
                user == null ? null : user.userId()));
    }

    /** 新增标签（后台维护）。 */
    @PostMapping("/api/admin/customer-tags")
    @RequirePerm(value = "customer:tag:manage", exact = true)
    public R<CustomerTagDtos.TagVO> create(
            @Valid @RequestBody CustomerTagDtos.TagForm form,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(customerTagService.create(form, user == null ? null : user.userId()));
    }

    /** 改名 / 换分类（后台维护）。 */
    @PutMapping("/api/admin/customer-tags/{tagId}")
    @RequirePerm(value = "customer:tag:manage", exact = true)
    public R<CustomerTagDtos.TagVO> update(
            @PathVariable long tagId,
            @Valid @RequestBody CustomerTagDtos.TagForm form,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(customerTagService.update(tagId, form, user == null ? null : user.userId()));
    }

    /** 启用 / 停用：停用只是不再可选，已打在客户身上的标签仍展示。 */
    @PutMapping("/api/admin/customer-tags/{tagId}/status")
    @RequirePerm(value = "customer:tag:manage", exact = true)
    public R<CustomerTagDtos.TagVO> updateStatus(
            @PathVariable long tagId,
            @Valid @RequestBody CustomerTagDtos.StatusForm form,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(customerTagService.setStatus(tagId, form.status(),
                user == null ? null : user.userId()));
    }

    /** 删除标签：有客户还在用会被拒（提示还有几个客户），改用停用。 */
    @DeleteMapping("/api/admin/customer-tags/{tagId}")
    @RequirePerm(value = "customer:tag:manage", exact = true)
    public R<Void> delete(@PathVariable long tagId,
                          @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        customerTagService.delete(tagId, user == null ? null : user.userId());
        return R.ok(null);
    }
}
