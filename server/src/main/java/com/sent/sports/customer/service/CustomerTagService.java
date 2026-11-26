package com.sent.sports.customer.service;

import com.sent.sports.customer.dto.admin.CustomerTagDtos;

import java.util.List;

/**
 * 客户标签服务（V63）：客服打标 + 管理端维护标签内容。
 *
 * <p>单独成服务而不是塞进 {@code CustomerQueryService}，是因为它有两类完全不同的调用方：</p>
 * <ul>
 *   <li><b>客服</b>：读目录、读写某客户的标签——高频、轻量、必须快；</li>
 *   <li><b>后台</b>：改「有哪些标签」——低频、需要重名校验与引用检查。</li>
 * </ul>
 *
 * <p>标签只在客服端与管理端可见。<b>用户端任何接口都不得返回</b>——
 * 让客户看到自己被标成「羊毛党」是重大事故，所以这里没有任何 {@code customerId} 自查入口，
 * 也没有开放给 {@code /api/**}（用户端）的路由。</p>
 */
public interface CustomerTagService {

    /**
     * 标签目录（按分类业务顺序：优质 → 中性 → 风险 → 恶意）。
     *
     * @param includeDisabled 管理端维护页要看停用项（true）；客服弹窗只要可选项（false）
     */
    List<CustomerTagDtos.TagVO> catalog(boolean includeDisabled);

    /** 某客户身上的标签（含已停用的：停用只是不能再新打，已打上的仍要让客服看到）。 */
    List<CustomerTagDtos.TagVO> tagsOf(long customerId);

    /**
     * 打标（差异增删）：只插新增、只删取消的，未变动的关联保持原位。
     *
     * @param tagIds     提交的全量勾选结果，空列表表示全部取消
     * @param operatorId 操作客服（写留痕用；测试/系统调用可为 null）
     * @return 打标后该客户的最新标签清单
     */
    List<CustomerTagDtos.TagVO> applyTags(long customerId, List<Long> tagIds, Long operatorId);

    /** 新增标签（后台维护）。 */
    CustomerTagDtos.TagVO create(CustomerTagDtos.TagForm form, Long operatorId);

    /** 改名 / 改分类（后台维护）。 */
    CustomerTagDtos.TagVO update(long tagId, CustomerTagDtos.TagForm form, Long operatorId);

    /** 启用 / 停用（后台维护）。 */
    CustomerTagDtos.TagVO setStatus(long tagId, String status, Long operatorId);

    /** 删除标签：有客户还在用时拒绝（否则那些客户头像旁的标签会凭空消失）。 */
    void delete(long tagId, Long operatorId);
}
