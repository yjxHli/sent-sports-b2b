package com.sent.sports.system.service.impl;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.SensitiveWordFilter;
import com.sent.sports.common.util.TimeUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.system.dto.admin.SensitiveWordDtos;
import com.sent.sports.system.mapper.SensitiveWordMapper;
import com.sent.sports.system.mapper.po.SensitiveWordRow;
import com.sent.sports.system.service.SensitiveWordService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 违禁词库维护实现（V46）。
 * <p>「改完立刻生效」靠每次写操作后 {@link #reloadFilter()}：从 DB 取全部启用词整体重建词树。
 * 词条数百级别，重建成本可忽略，比维护增量 diff 简单且不会漏。</p>
 */
@Service
public class SensitiveWordServiceImpl implements SensitiveWordService {

    /** 分类白名单：未知值直接拒绝，避免脏分类把列表筛选搞乱。 */
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "PROFANITY", "辱骂脏话",
            "AD", "广告导流",
            "ILLEGAL", "违法违禁",
            "OTHER", "其他");

    private static final String DEFAULT_CATEGORY = "OTHER";
    private static final int WORD_MAX = 64;
    private static final int REMARK_MAX = 255;

    private final SensitiveWordMapper sensitiveWordMapper;
    private final SensitiveWordFilter sensitiveWordFilter;

    public SensitiveWordServiceImpl(SensitiveWordMapper sensitiveWordMapper,
                                    SensitiveWordFilter sensitiveWordFilter) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    @Override
    public PageResult<SensitiveWordDtos.WordVO> list(String keyword, String category, Integer enabled,
                                                     PageQuery page) {
        String kw = blankToNull(keyword);
        String cat = blankToNull(category);
        List<SensitiveWordRow> rows = sensitiveWordMapper.selectPage(
                kw, cat, enabled, page.limit(), page.offset());
        long total = sensitiveWordMapper.countBy(kw, cat, enabled);
        return PageResult.of(rows.stream().map(this::toVO).toList(), total, page.getPage(), page.getSize());
    }

    @Override
    public SensitiveWordDtos.StatsVO stats() {
        long total = sensitiveWordMapper.countAll();
        long enabled = sensitiveWordMapper.countEnabled();
        return new SensitiveWordDtos.StatsVO(total, enabled, total - enabled);
    }

    @Override
    @UseCase
    public SensitiveWordDtos.WordVO create(SensitiveWordDtos.WordForm form, Long operatorId) {
        String word = normalizeWord(form.word());
        String category = normalizeCategory(form.category());
        String remark = normalizeRemark(form.remark());
        boolean enabled = form.enabled() == null || form.enabled();

        if (sensitiveWordMapper.selectByWord(word) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "违禁词「" + word + "」已存在");
        }
        try {
            sensitiveWordMapper.insert(word, category, enabled ? 1 : 0, remark, operatorId);
        } catch (DuplicateKeyException e) {
            // 并发下另一处已录入：唯一键兜底，按「已存在」返回同样的提示
            throw new BizException(ErrorCode.PARAM_INVALID, "违禁词「" + word + "」已存在");
        }
        SensitiveWordRow saved = sensitiveWordMapper.selectByWord(word);
        reloadFilter();
        return toVO(saved);
    }

    @Override
    @UseCase
    public SensitiveWordDtos.WordVO update(long id, SensitiveWordDtos.WordForm form) {
        SensitiveWordRow existing = sensitiveWordMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String word = normalizeWord(form.word());
        String category = normalizeCategory(form.category());
        String remark = normalizeRemark(form.remark());
        boolean enabled = form.enabled() == null ? existing.enabled() == 1 : form.enabled();

        if (!word.equals(existing.word())) {
            SensitiveWordRow sameWord = sensitiveWordMapper.selectByWord(word);
            if (sameWord != null && !sameWord.id().equals(id)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "违禁词「" + word + "」已存在");
            }
        }
        try {
            sensitiveWordMapper.update(id, word, category, enabled ? 1 : 0, remark);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "违禁词「" + word + "」已存在");
        }
        reloadFilter();
        return toVO(sensitiveWordMapper.selectById(id));
    }

    @Override
    @UseCase
    public SensitiveWordDtos.WordVO toggle(long id, boolean enabled) {
        SensitiveWordRow existing = sensitiveWordMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        sensitiveWordMapper.updateEnabled(id, enabled ? 1 : 0);
        reloadFilter();
        return toVO(sensitiveWordMapper.selectById(id));
    }

    @Override
    @UseCase
    public void delete(long id) {
        if (sensitiveWordMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        sensitiveWordMapper.deleteById(id);
        reloadFilter();
    }

    @Override
    public int reloadFilter() {
        List<String> words = sensitiveWordMapper.selectEnabledWords();
        sensitiveWordFilter.reload(words);
        return words.size();
    }

    /* ------------------------------------------------------------------ */

    /** DB 行 → 视图：分类标签在这里翻成人话，前端不再维护一份映射。 */
    private SensitiveWordDtos.WordVO toVO(SensitiveWordRow row) {
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return new SensitiveWordDtos.WordVO(
                row.id(), row.word(), row.category(),
                CATEGORY_LABELS.getOrDefault(row.category(), row.category()),
                row.enabled() != null && row.enabled() == 1,
                row.remark() == null ? "" : row.remark(),
                TimeUtil.toMillis(row.createdAt()),
                TimeUtil.toMillis(row.updatedAt()));
    }

    /** 词条归一：去首尾空白 + 空值/超长校验；中间的空格保留（「fuck you」这类英文词组需要它）。 */
    private static String normalizeWord(String raw) {
        String word = raw == null ? "" : raw.trim();
        if (word.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "违禁词不能为空");
        }
        if (word.length() > WORD_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "违禁词不能超过 " + WORD_MAX + " 个字符");
        }
        return word;
    }

    private static String normalizeCategory(String raw) {
        String category = raw == null || raw.isBlank() ? DEFAULT_CATEGORY : raw.trim().toUpperCase();
        if (!CATEGORY_LABELS.containsKey(category)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "违禁词分类不合法");
        }
        return category;
    }

    private static String normalizeRemark(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String remark = raw.trim();
        if (remark.length() > REMARK_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "备注不能超过 " + REMARK_MAX + " 个字符");
        }
        return remark;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
