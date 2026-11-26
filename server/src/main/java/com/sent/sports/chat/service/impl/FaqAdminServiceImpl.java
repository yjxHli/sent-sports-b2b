package com.sent.sports.chat.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.chat.domain.FaqStatus;
import com.sent.sports.chat.dto.admin.AdminFaqDtos;
import com.sent.sports.chat.mapper.FaqKnowledgeMapper;
import com.sent.sports.chat.mapper.po.FaqKnowledgePO;
import com.sent.sports.chat.repository.FaqKnowledgeRepository;
import com.sent.sports.chat.service.FaqAdminService;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.common.util.TimeUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 话术配置实现（V60）。
 *
 * <p>四条口径：</p>
 * <ol>
 *   <li><b>关键词归一</b>：运营从微信/Excel 粘过来的串常带全角逗号、空格、重复词，
 *       落库前统一成 {@code 词1,词2}——匹配侧按逗号拆词，不归一就会出现「退换货 」这种永远匹配不上的词；</li>
 *   <li><b>写后必刷缓存</b>：每个写方法的最后一步都是 {@code invalidateCache()}，
 *       漏了不会报错，只会让客户继续看到旧话术；</li>
 *   <li><b>停用不删</b>：命中次数是运营判断「这条话术有没有用」的唯一依据，物理删除等于丢数据；</li>
 *   <li><b>留痕</b>：新增 / 修改 / 启停都写 {@code sys_operation_log}（module=FAQ），
 *       话术直接面向客户，改错了要能追溯是谁改的、改成了什么。</li>
 * </ol>
 */
@Service
public class FaqAdminServiceImpl implements FaqAdminService {

    private static final Logger log = LoggerFactory.getLogger(FaqAdminServiceImpl.class);

    /** 与 {@code faq_knowledge.reply_content} 列宽一致：超了会在 SQL 层报错，不如提前给业务提示。 */
    private static final int REPLY_MAX = 500;
    private static final int KEYWORDS_MAX = 255;
    /** 单个关键词上限：超过这个长度必然是粘错了（整段话塞进关键词框）。 */
    private static final int KEYWORD_MAX = 32;
    /** 搜索词上限：防止把整段话当 LIKE 条件扫全表。 */
    private static final int SEARCH_MAX = 32;

    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_STATUS = "STATUS";
    private static final String ACTION_DELETE = "DELETE";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FaqKnowledgeMapper faqKnowledgeMapper;
    private final FaqKnowledgeRepository faqKnowledgeRepository;

    public FaqAdminServiceImpl(FaqKnowledgeMapper faqKnowledgeMapper,
                               FaqKnowledgeRepository faqKnowledgeRepository) {
        this.faqKnowledgeMapper = faqKnowledgeMapper;
        this.faqKnowledgeRepository = faqKnowledgeRepository;
    }

    @Override
    public PageResult<AdminFaqDtos.FaqVO> list(String keyword, String status, PageQuery pageQuery) {
        String search = blankToNull(keyword);
        if (search != null && search.length() > SEARCH_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "搜索词最长 " + SEARCH_MAX + " 字");
        }
        String statusFilter = blankToNull(status) == null ? null : FaqStatus.require(status).name();

        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        Page<FaqKnowledgePO> page = new Page<>(query.getPage(), query.getSize());
        IPage<FaqKnowledgePO> rows = faqKnowledgeMapper.selectAdminPage(page, search, statusFilter);
        List<AdminFaqDtos.FaqVO> list = new ArrayList<>();
        if (rows != null && rows.getRecords() != null) {
            for (FaqKnowledgePO po : rows.getRecords()) {
                list.add(toVo(po));
            }
        }
        long total = rows == null ? 0L : rows.getTotal();
        return PageResult.of(list, total, query.getPage(), query.getSize());
    }

    @Override
    @UseCase
    public AdminFaqDtos.FaqVO create(AdminFaqDtos.FaqCmd cmd, Long operatorId) {
        String keywords = normalizeKeywords(cmd == null ? null : cmd.keywords());
        String reply = normalizeReply(cmd == null ? null : cmd.replyContent());
        FaqStatus status = cmd == null ? FaqStatus.ENABLED : statusOrDefault(cmd.status(), FaqStatus.ENABLED);

        FaqKnowledgePO po = new FaqKnowledgePO();
        po.setKeywords(keywords);
        po.setReplyContent(reply);
        po.setHitCount(0);
        po.setStatus(status.name());
        po.setCreatedBy(operatorId);
        faqKnowledgeMapper.insert(po);
        writeLog(operatorId, ACTION_CREATE, String.valueOf(po.getId()), null, po);
        faqKnowledgeRepository.invalidateCache();
        return toVo(po);
    }

    @Override
    @UseCase
    public AdminFaqDtos.FaqVO update(long id, AdminFaqDtos.FaqCmd cmd, Long operatorId) {
        if (id <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "话术ID不合法");
        }
        FaqKnowledgePO existing = faqKnowledgeMapper.selectAdminById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String keywords = normalizeKeywords(cmd == null ? null : cmd.keywords());
        String reply = normalizeReply(cmd == null ? null : cmd.replyContent());
        // 不传状态就沿用原状态：编辑弹窗里忘了带状态，不该把启用的话术改成停用
        FaqStatus status = statusOrDefault(cmd == null ? null : cmd.status(),
                FaqStatus.of(existing.getStatus()));

        FaqKnowledgePO patch = new FaqKnowledgePO();
        patch.setId(id);
        patch.setKeywords(keywords);
        patch.setReplyContent(reply);
        patch.setStatus(status.name());
        // 不校验受影响行数：内容没改动时 MySQL 返回 0，那不是「不存在」
        faqKnowledgeMapper.updateById(patch);

        writeLog(operatorId, ACTION_UPDATE, String.valueOf(id), existing, patch);
        faqKnowledgeRepository.invalidateCache();

        FaqKnowledgePO latest = faqKnowledgeMapper.selectAdminById(id);
        return toVo(latest == null ? patch : latest);
    }

    @Override
    @UseCase
    public AdminFaqDtos.FaqVO updateStatus(long id, String status, Long operatorId) {
        if (id <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "话术ID不合法");
        }
        FaqStatus target = FaqStatus.require(status);
        FaqKnowledgePO existing = faqKnowledgeMapper.selectAdminById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (FaqStatus.of(existing.getStatus()) == target) {
            // 幂等：重复点「停用」不写库、不刷缓存、不留日志（缓存无谓失效会让下个客户多查一次库）
            return toVo(existing);
        }
        faqKnowledgeMapper.updateStatus(id, target.name());
        existing.setStatus(target.name());
        writeLog(operatorId, ACTION_STATUS, String.valueOf(id), null, existing);
        faqKnowledgeRepository.invalidateCache();
        return toVo(existing);
    }

    @Override
    @UseCase
    public int delete(List<String> ids, Long operatorId) {
        // 归一（trim → 去空 → 去重 → 升序）+ 数字校验都收敛在 BatchIds：
        // 前端换确认令牌用的是同一套归一化，两边不一致就会出现「输了「删除」仍 40302」
        List<Long> normalized = BatchIds.normalizeNumbers(ids);
        int deleted = faqKnowledgeMapper.batchMarkDeleted(normalized);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", normalized);
        body.put("deleted", deleted);
        writeLog(operatorId, ACTION_DELETE,
                normalized.stream().map(String::valueOf)
                        .collect(Collectors.joining(",")), null, toJson(body));
        // 删掉的条目必须立刻退出候选集，否则机器人还会拿旧快照回复「已删除」的话术
        faqKnowledgeRepository.invalidateCache();
        return deleted;
    }

    /* ---------- 归一与校验 ---------- */
    /** 拆词 → 去空 → 去重（保序）→ 用英文逗号拼回。 */
    private String normalizeKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写关键词");
        }
        Set<String> words = new LinkedHashSet<>();
        for (String piece : raw.split("[,，]")) {
            String word = piece.trim();
            if (word.isEmpty()) {
                continue;
            }
            if (word.length() > KEYWORD_MAX) {
                throw new BizException(ErrorCode.PARAM_INVALID, "单个关键词最长 " + KEYWORD_MAX + " 字");
            }
            words.add(word);
        }
        if (words.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写关键词");
        }
        String joined = String.join(",", words);
        if (joined.length() > KEYWORDS_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关键词总长度不能超过 " + KEYWORDS_MAX + " 字");
        }
        return joined;
    }

    private String normalizeReply(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写回复内容");
        }
        String reply = raw.trim();
        if (reply.length() > REPLY_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回复内容最长 " + REPLY_MAX + " 字");
        }
        return reply;
    }

    private FaqStatus statusOrDefault(String raw, FaqStatus fallback) {
        return raw == null || raw.isBlank() ? fallback : FaqStatus.require(raw);
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    /* ---------- 行 → VO ---------- */

    private AdminFaqDtos.FaqVO toVo(FaqKnowledgePO po) {
        return new AdminFaqDtos.FaqVO(
                po.getId() == null ? 0L : po.getId(),
                po.getKeywords() == null ? "" : po.getKeywords(),
                po.getReplyContent() == null ? "" : po.getReplyContent(),
                po.getHitCount() == null ? 0 : po.getHitCount(),
                FaqStatus.of(po.getStatus()).name(),
                TimeUtil.toMillis(po.getUpdatedAt()));
    }

    /* ---------- 审计留痕 ---------- */

    private void writeLog(Long operatorId, String action, String targetId,
                          FaqKnowledgePO before, FaqKnowledgePO after) {
        writeLog(operatorId, action, targetId,
                before == null ? null : toJson(before), toJson(after));
    }

    /** 原始 JSON 版本：批量删除的「after」不是单条 PO（是 ids + 影响行数），单独走这条路径。 */
    private void writeLog(Long operatorId, String action, String targetId,
                          String beforeJson, String afterJson) {
        try {
            faqKnowledgeMapper.insertFaqLog(operatorId, action, targetId,
                    beforeJson, afterJson, operatorId);
        } catch (RuntimeException e) {
            // 日志写失败不该让运营的这次保存失败（话术已经生效了），留 warn 事后可追
            log.warn("话术操作日志写入失败, action={}, targetId={}", action, targetId, e);
        }
    }

    private String toJson(FaqKnowledgePO po) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keywords", po.getKeywords());
        body.put("replyContent", po.getReplyContent());
        body.put("status", po.getStatus());
        return toJson(body);
    }

    private String toJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            return "{}";
        }
    }
}
