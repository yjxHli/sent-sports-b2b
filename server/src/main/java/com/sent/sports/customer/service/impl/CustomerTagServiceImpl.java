package com.sent.sports.customer.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.domain.TagCategory;
import com.sent.sports.customer.dto.admin.CustomerTagDtos;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.CustomerTagLinkMapper;
import com.sent.sports.customer.mapper.CustomerTagMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.mapper.po.CustomerTagPO;
import com.sent.sports.customer.service.CustomerTagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户标签服务实现（V63）。
 *
 * <p>几个刻意的取舍：</p>
 * <ol>
 *   <li><b>打标是差异增删，不是「先删光再插入」</b>：全删重插会把 {@code created_at} 刷成本次时间，
 *       「这个客户是哪天被标成羊毛党的」就查不出来了；而且并发下两个客服同时保存会互相覆盖；</li>
 *   <li><b>只认启用中的标签</b>：停用后不能再新打，但已打在客户身上的仍展示——
 *       否则运营停用一个标签，几十个客户的会话头部会一起变色，客服会以为数据丢了；</li>
 *   <li><b>排序在 Java 里再排一次</b>：SQL 的 {@code FIELD()} 已经排好，但 {@code tagsOf} 走的是
 *       关联查询，万一将来换成别的查询、或直接用 {@code selectTagIdsOfCustomer} 拼装，
 *       顺序就必须由服务层保证——客服扫一眼的顺序不能随实现漂移；</li>
 *   <li><b>删标签前查引用</b>：还有客户在用时拒绝并说明数量，
 *       而不是静默删除关联（那会让历史工单里提到的标签变成「不存在的标签」）。</li>
 * </ol>
 */
@Service
public class CustomerTagServiceImpl implements CustomerTagService {

    /** 可选状态白名单：写库严格，别猜运营想表达什么。 */
    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";
    private static final Set<String> STATUSES = Set.of(ACTIVE, DISABLED);

    /** 与 {@code customer_tag.name} 的列宽一致（VARCHAR(32)），超长直接给业务提示而不是 50001。 */
    private static final int NAME_MAX_LENGTH = 32;

    /** 展示顺序：先按分类业务顺序（优质在前、恶意在后），同类按 sort_no，最后按 id 兜底。 */
    private static final Comparator<CustomerTagDtos.TagVO> DISPLAY_ORDER =
            Comparator.comparingInt((CustomerTagDtos.TagVO vo) -> TagCategory.of(vo.category()).ordinal())
                    .thenComparing(vo -> vo.sortNo() == null ? Integer.MAX_VALUE : vo.sortNo())
                    .thenComparingLong(CustomerTagDtos.TagVO::tagId);

    private final CustomerTagMapper tagMapper;
    private final CustomerTagLinkMapper linkMapper;
    private final CustomerMapper customerMapper;

    public CustomerTagServiceImpl(CustomerTagMapper tagMapper,
                                  CustomerTagLinkMapper linkMapper,
                                  CustomerMapper customerMapper) {
        this.tagMapper = tagMapper;
        this.linkMapper = linkMapper;
        this.customerMapper = customerMapper;
    }

    @Override
    public List<CustomerTagDtos.TagVO> catalog(boolean includeDisabled) {
        List<CustomerTagPO> rows = tagMapper.selectCatalog(includeDisabled);
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(CustomerTagServiceImpl::toVO).toList();
    }

    @Override
    public List<CustomerTagDtos.TagVO> tagsOf(long customerId) {
        List<CustomerTagPO> rows = linkMapper.selectTagsOfCustomer(customerId);
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(CustomerTagServiceImpl::toVO).sorted(DISPLAY_ORDER).toList();
    }

    @Override
    @Transactional
    public List<CustomerTagDtos.TagVO> applyTags(long customerId, List<Long> tagIds, Long operatorId) {
        requireCustomer(customerId);

        List<Long> target = normalizeIds(tagIds);
        // 先校验再写：标签清单里只要有一个停用/不存在的，整次提交都不落库，
        // 避免「一半打上、一半没打上」这种客服自己也说不清的状态。
        requireUsableTags(target);

        List<Long> existingRaw = linkMapper.selectTagIdsOfCustomer(customerId);
        List<Long> existing = normalizeIds(existingRaw);
        Set<Long> before = new LinkedHashSet<>(existing);
        Set<Long> after = new LinkedHashSet<>(target);

        List<Long> added = target.stream().filter(id -> !before.contains(id)).toList();
        List<Long> removed = existing.stream().filter(id -> !after.contains(id)).toList();

        added.forEach(id -> linkMapper.insertIgnore(customerId, id, operatorId));
        removed.forEach(id -> linkMapper.deleteOne(customerId, id));

        if (!added.isEmpty() || !removed.isEmpty()) {
            tagMapper.insertLog(operatorId, "TAG_APPLY", String.valueOf(customerId),
                    changeJson(added, removed));
        }
        return tagsOf(customerId);
    }

    @Override
    @Transactional
    public CustomerTagDtos.TagVO create(CustomerTagDtos.TagForm form, Long operatorId) {
        String name = normalizeName(form == null ? null : form.name());
        TagCategory category = TagCategory.require(form == null ? null : form.category());
        requireNameFree(name, null);

        CustomerTagPO po = new CustomerTagPO();
        po.setName(name);
        po.setCategory(category.name());
        po.setStatus(ACTIVE);
        // 新增排在最后而不是插队：客服的肌肉记忆是按顺序找标签，插队会让他们点错。
        Integer maxSortNo = tagMapper.selectMaxSortNo();
        po.setSortNo((maxSortNo == null ? 0 : maxSortNo) + 1);
        po.setCreatedBy(operatorId);
        tagMapper.insert(po);

        tagMapper.insertLog(operatorId, "TAG_CREATE", String.valueOf(po.getId()),
                "{\"name\":\"" + name + "\",\"category\":\"" + category.name() + "\"}");
        return toVO(po);
    }

    @Override
    @Transactional
    public CustomerTagDtos.TagVO update(long tagId, CustomerTagDtos.TagForm form, Long operatorId) {
        CustomerTagPO po = requireTag(tagId);
        String name = normalizeName(form == null ? null : form.name());
        TagCategory category = TagCategory.require(form == null ? null : form.category());
        requireNameFree(name, tagId);

        po.setName(name);
        po.setCategory(category.name());
        tagMapper.updateById(po);

        tagMapper.insertLog(operatorId, "TAG_UPDATE", String.valueOf(tagId),
                "{\"name\":\"" + name + "\",\"category\":\"" + category.name() + "\"}");
        return toVO(po);
    }

    @Override
    @Transactional
    public CustomerTagDtos.TagVO setStatus(long tagId, String status, Long operatorId) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态只能是 ACTIVE（启用）或 DISABLED（停用）");
        }
        CustomerTagPO po = requireTag(tagId);
        // 重复点「停用」是幂等的：不给运营报错，但也不重复写留痕。
        if (normalized.equalsIgnoreCase(po.getStatus())) {
            return toVO(po);
        }
        po.setStatus(normalized);
        tagMapper.updateById(po);
        tagMapper.insertLog(operatorId, "TAG_STATUS", String.valueOf(tagId),
                "{\"status\":\"" + normalized + "\"}");
        return toVO(po);
    }

    @Override
    @Transactional
    public void delete(long tagId, Long operatorId) {
        CustomerTagPO po = requireTag(tagId);
        int inUse = linkMapper.countByTag(tagId);
        if (inUse > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "还有 " + inUse + " 个客户在用「" + po.getName() + "」，不能删除；可以先停用，它就不会再出现在弹窗里");
        }
        tagMapper.deleteById(tagId);
        tagMapper.insertLog(operatorId, "TAG_DELETE", String.valueOf(tagId),
                "{\"name\":\"" + po.getName() + "\"}");
    }

    /* ---------------- 内部 ---------------- */

    private CustomerTagPO requireTag(long tagId) {
        CustomerTagPO po = tagMapper.selectById(tagId);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        return po;
    }

    private void requireCustomer(long customerId) {
        CustomerPO customer = customerMapper.selectById(customerId);
        if (customer == null || (customer.getDeleted() != null && customer.getDeleted() != 0)) {
            throw new BizException(ErrorCode.NOT_FOUND, "客户不存在");
        }
    }

    /**
     * 校验目标标签全部存在且启用。
     * <p>{@code selectByIds} 返回的行数少于提交数，说明有的 ID 已被物理删除；
     * 逐条比对才能报出「是哪一个」不能再打。</p>
     */
    private void requireUsableTags(List<Long> target) {
        if (target.isEmpty()) {
            return;
        }
        List<CustomerTagPO> rows = tagMapper.selectByIds(target);
        Map<Long, CustomerTagPO> byId = (rows == null ? List.<CustomerTagPO>of() : rows).stream()
                .filter(po -> po.getId() != null)
                .collect(Collectors.toMap(CustomerTagPO::getId, Function.identity(), (left, right) -> left));
        for (Long id : target) {
            CustomerTagPO po = byId.get(id);
            if (po == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "标签不存在或已被删除（#" + id + "），请刷新后重试");
            }
            if (!ACTIVE.equalsIgnoreCase(po.getStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "标签「" + po.getName() + "」已停用，不能再打上；如需恢复请联系管理员启用");
            }
        }
    }

    private void requireNameFree(String name, Long selfId) {
        CustomerTagPO same = tagMapper.selectByName(name);
        if (same != null && (selfId == null || !selfId.equals(same.getId()))) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "标签「" + name + "」已存在（已停用的标签同样占名，可在维护列表里重新启用）");
        }
    }

    /** 名称归一：trim + 非空 + 限长。空白名直接拒绝，不要存成看起来像标签的空串。 */
    private static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标签名不能为空");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标签名最多 " + NAME_MAX_LENGTH + " 个字");
        }
        return name;
    }

    /** ID 清单归一：去 null、去非正数、去重并保留提交顺序。 */
    private static List<Long> normalizeIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        return new ArrayList<>(unique);
    }

    /** 留痕正文：加了什么、删了什么，事后争议时直接看这两个数组。 */
    private static String changeJson(List<Long> added, List<Long> removed) {
        return "{\"added\":" + added + ",\"removed\":" + removed + "}";
    }

    private static CustomerTagDtos.TagVO toVO(CustomerTagPO po) {
        TagCategory category = TagCategory.of(po.getCategory());
        return new CustomerTagDtos.TagVO(
                po.getId() == null ? 0L : po.getId(),
                po.getName(),
                category.name(),
                category.label(),
                category.colorKey(),
                po.getStatus() == null ? ACTIVE : po.getStatus(),
                po.getSortNo());
    }
}
