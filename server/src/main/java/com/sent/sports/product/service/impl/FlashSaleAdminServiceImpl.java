package com.sent.sports.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.admin.FlashSaleDtos;
import com.sent.sports.product.mapper.FlashSaleItemMapper;
import com.sent.sports.product.mapper.FlashSaleMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.po.FlashSaleActivityPO;
import com.sent.sports.product.mapper.po.FlashSaleItemPO;
import com.sent.sports.product.mapper.po.SkuPO;
import com.sent.sports.product.service.admin.FlashSaleAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 秒杀活动管理实现（V54）。
 *
 * <p>三条设计约束：</p>
 * <ol>
 *   <li><b>价格校验前置</b>：秒杀价必须 &lt; 零售价且 &ge; 底价（底价为 0 视为未设置）。
 *       定价领域会对低于底价的结果抛 {@code BELOW_FLOOR_PRICE}，所以在发布前就拦住，
 *       不能让一个有问题的活动上线后把下单全打挂。</li>
 *   <li><b>时间窗不重叠</b>：同一 SKU 不能同时属于两个已发布且重叠的活动，否则取价不确定。
 *       保存与发布各校验一次（保存时可能是草稿，发布时才真正参与冲突）。</li>
 *   <li><b>状态不做定时任务</b>：{@code status} 只是运营开关，"进行中/已结束"按时间窗现算。</li>
 * </ol>
 */
@Service
public class FlashSaleAdminServiceImpl implements FlashSaleAdminService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final String SKU_ON = "ON";
    private static final int MAX_ITEMS = 50;
    private static final int MAX_TITLE = 128;

    private final FlashSaleMapper flashSaleMapper;
    private final FlashSaleItemMapper flashSaleItemMapper;
    private final ProductMapper productMapper;
    private final IdGen idGen;
    private final Clock clock;

    public FlashSaleAdminServiceImpl(FlashSaleMapper flashSaleMapper,
                                     FlashSaleItemMapper flashSaleItemMapper,
                                     ProductMapper productMapper,
                                     IdGen idGen,
                                     Clock clock) {
        this.flashSaleMapper = flashSaleMapper;
        this.flashSaleItemMapper = flashSaleItemMapper;
        this.productMapper = productMapper;
        this.idGen = idGen;
        this.clock = clock;
    }

    @Override
    public PageResult<FlashSaleDtos.ActivityVO> list(PageQuery page, String keyword, String status) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String st = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();

        LambdaQueryWrapper<FlashSaleActivityPO> query = new LambdaQueryWrapper<>();
        if (kw != null) {
            query.and(w -> w.like(FlashSaleActivityPO::getTitle, kw)
                    .or().like(FlashSaleActivityPO::getActivityNo, kw));
        }
        if (st != null) {
            query.eq(FlashSaleActivityPO::getStatus, st);
        }
        query.orderByDesc(FlashSaleActivityPO::getId);

        IPage<FlashSaleActivityPO> rows = flashSaleMapper.selectPage(
                new Page<>(page.getPage(), page.getSize()), query);
        List<FlashSaleActivityPO> records = rows.getRecords();
        Map<Long, Integer> counts = itemCounts(records.stream().map(FlashSaleActivityPO::getId).toList());
        LocalDateTime now = now();

        List<FlashSaleDtos.ActivityVO> list = records.stream()
                .map(po -> toVo(po, counts.getOrDefault(po.getId(), 0), now, List.of()))
                .toList();
        return PageResult.of(list, rows.getTotal(), page.getPage(), page.getSize());
    }

    @Override
    public FlashSaleDtos.ActivityVO detail(Long id) {
        FlashSaleActivityPO po = require(id);
        return toVo(po, 0, now(), flashSaleMapper.selectItems(id));
    }

    @Override
    @UseCase
    @Transactional
    public FlashSaleDtos.ActivityVO create(FlashSaleDtos.ActivityForm form) {
        validate(form, null, false);
        FlashSaleActivityPO po = new FlashSaleActivityPO();
        po.setActivityNo(idGen.next("FS"));
        applyForm(po, form);
        po.setStatus(STATUS_DRAFT);
        po.setDeleted(0);
        flashSaleMapper.insert(po);
        if (po.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "秒杀活动创建失败：未返回主键");
        }
        replaceItems(po.getId(), form.items());
        return detail(po.getId());
    }

    @Override
    @UseCase
    @Transactional
    public FlashSaleDtos.ActivityVO update(Long id, FlashSaleDtos.ActivityForm form) {
        FlashSaleActivityPO po = require(id);
        // 已发布的活动改价必须按"发布口径"重新校验（含 SKU 上下架）
        validate(form, id, STATUS_ACTIVE.equals(po.getStatus()));
        applyForm(po, form);
        flashSaleMapper.updateById(po);
        replaceItems(id, form.items());
        return detail(id);
    }

    @Override
    @UseCase
    @Transactional
    public FlashSaleDtos.ActivityVO setStatus(Long id, boolean active) {
        FlashSaleActivityPO po = require(id);
        if (active) {
            List<FlashSaleDtos.ItemForm> items = flashSaleMapper.selectItems(id).stream()
                    .map(row -> new FlashSaleDtos.ItemForm(row.getSkuId(), row.getFlashPriceCent()))
                    .toList();
            FlashSaleDtos.ActivityForm form = new FlashSaleDtos.ActivityForm(
                    po.getTitle(), po.getStartAt(), po.getEndAt(), po.getRemark(), items);
            validate(form, id, true);
            po.setStatus(STATUS_ACTIVE);
        } else {
            po.setStatus(STATUS_OFFLINE);
        }
        flashSaleMapper.updateById(po);
        return detail(id);
    }

    @Override
    @UseCase
    @Transactional
    public void delete(Long id) {
        require(id);
        // 软删活动，明细保留以便追溯历史价格；已发布的活动删除后立即失效（查询带 deleted = 0）
        flashSaleMapper.deleteById(id);
    }

    // ---------- 校验 ----------

    /**
     * 活动校验。{@code requirePublish=true} 时额外校验 SKU 上架状态——
     * 草稿允许先把下架商品挂进来，但发布时必须全是可售状态。
     */
    private void validate(FlashSaleDtos.ActivityForm form, Long excludeActivityId, boolean requirePublish) {
        if (form.title() == null || form.title().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "活动标题不能为空");
        }
        if (form.title().trim().length() > MAX_TITLE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "活动标题最多 " + MAX_TITLE + " 字");
        }
        if (form.startAt() == null || form.endAt() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "活动起止时间不能为空");
        }
        if (!form.endAt().isAfter(form.startAt())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        List<FlashSaleDtos.ItemForm> items = form.items();
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "至少选择一个商品");
        }
        if (items.size() > MAX_ITEMS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "一个活动最多 " + MAX_ITEMS + " 个商品");
        }
        Set<Long> seen = new HashSet<>();
        for (FlashSaleDtos.ItemForm item : items) {
            if (item == null || item.skuId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "活动商品不能为空");
            }
            if (!seen.add(item.skuId())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "同一规格不能重复添加：" + item.skuId());
            }
            if (item.flashPriceCent() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "秒杀价必须大于 0");
            }
            SkuPO sku = productMapper.selectSkuById(item.skuId());
            if (sku == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "规格不存在：" + item.skuId());
            }
            if (requirePublish && !SKU_ON.equals(sku.getStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "含已下架规格，无法发布：" + item.skuId());
            }
            long retail = sku.getRetailPriceCent() == null ? 0L : sku.getRetailPriceCent();
            if (retail > 0 && item.flashPriceCent() >= retail) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "秒杀价应低于零售价（规格 " + item.skuId() + "）");
            }
            long floor = sku.getFloorPriceCent() == null ? 0L : sku.getFloorPriceCent();
            if (floor > 0 && item.flashPriceCent() < floor) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "秒杀价低于该规格底价（规格 " + item.skuId() + "）");
            }
            long exclude = excludeActivityId == null ? 0L : excludeActivityId;
            if (flashSaleMapper.countOverlap(item.skuId(), exclude, form.startAt(), form.endAt()) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "该规格已参与重叠时段的秒杀活动：" + item.skuId());
            }
        }
    }

    // ---------- 落库 ----------

    private void applyForm(FlashSaleActivityPO po, FlashSaleDtos.ActivityForm form) {
        po.setTitle(form.title().trim());
        po.setStartAt(form.startAt());
        po.setEndAt(form.endAt());
        po.setRemark(form.remark());
    }

    private void replaceItems(long activityId, List<FlashSaleDtos.ItemForm> items) {
        flashSaleMapper.deleteItems(activityId);
        int sort = 0;
        for (FlashSaleDtos.ItemForm item : items) {
            FlashSaleItemPO po = new FlashSaleItemPO();
            po.setActivityId(activityId);
            po.setSkuId(item.skuId());
            po.setFlashPriceCent(item.flashPriceCent());
            po.setSort(sort++);
            flashSaleItemMapper.insert(po);
        }
    }

    private FlashSaleActivityPO require(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "活动ID不能为空");
        }
        FlashSaleActivityPO po = flashSaleMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "秒杀活动不存在：" + id);
        }
        return po;
    }

    private Map<Long, Integer> itemCounts(List<Long> activityIds) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (activityIds.isEmpty()) {
            return result;
        }
        for (FlashSaleMapper.ItemCountRow row : flashSaleMapper.countItems(activityIds)) {
            if (row.getActivityId() != null) {
                result.put(row.getActivityId(), row.getItemCount() == null ? 0 : row.getItemCount());
            }
        }
        return result;
    }

    // ---------- 视图 ----------

    private FlashSaleDtos.ActivityVO toVo(FlashSaleActivityPO po, int itemCount, LocalDateTime now,
                                          List<FlashSaleMapper.ItemRow> items) {
        List<FlashSaleDtos.ItemVO> itemVos = new ArrayList<>();
        for (FlashSaleMapper.ItemRow row : items) {
            itemVos.add(new FlashSaleDtos.ItemVO(
                    String.valueOf(row.getSkuId()), row.getSkuCode(), row.getProductName(),
                    plainSpec(row.getSpecJson()),
                    row.getRetailPriceCent() == null ? 0L : row.getRetailPriceCent(),
                    row.getFloorPriceCent() == null ? 0L : row.getFloorPriceCent(),
                    row.getFlashPriceCent() == null ? 0L : row.getFlashPriceCent()));
        }
        int count = items.isEmpty() ? itemCount : items.size();
        return new FlashSaleDtos.ActivityVO(
                String.valueOf(po.getId()), po.getActivityNo(), po.getTitle(), po.getStatus(),
                displayStatus(po, now), po.getStartAt(), po.getEndAt(), po.getRemark(),
                count, itemVos);
    }

    /** 展示态：草稿/已下线按开关，其余按时间窗现算（到点即"进行中"，到点即"已结束"）。 */
    private String displayStatus(FlashSaleActivityPO po, LocalDateTime now) {
        if (STATUS_OFFLINE.equals(po.getStatus())) {
            return "OFFLINE";
        }
        if (STATUS_DRAFT.equals(po.getStatus())) {
            return "DRAFT";
        }
        if (po.getStartAt() != null && now.isBefore(po.getStartAt())) {
            return "PENDING";
        }
        if (po.getEndAt() != null && !now.isBefore(po.getEndAt())) {
            return "ENDED";
        }
        return "ONGOING";
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    /** 把 {@code {"spec":"黑色 / L"}} 展平成「黑色 / L」，与商品侧口径一致。 */
    private static String plainSpec(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        String body = json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        int idx = body.indexOf(':');
        if (idx < 0) {
            return body.trim();
        }
        String value = body.substring(idx + 1).trim();
        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
