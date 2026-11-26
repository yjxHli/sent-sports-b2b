package com.sent.sports.integration;

import com.sent.sports.support.BaseIntegrationTest;
import com.sent.sports.system.mapper.SensitiveWordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 违禁词库列表排序（2026-11-26，TDD 红→绿）。
 *
 * <p>运营口径：先按分类（辱骂脏话 → 广告导流 → 违法违禁 → 其他），
 * 同一分类内按词条<b>首个文字</b>的字母/拼音顺序（中文走 GBK 排序规则），
 * 首字相同时用 id 兜底保证分页稳定。不能按 id 倒序「最新在前」。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class SensitiveWordMapperIT extends BaseIntegrationTest {

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    @Test
    @DisplayName("列表排序：先分类后首字拼音，中文词条按拼音而非 id 顺序")
    void selectPageOrdersByCategoryThenFirstCharacter() {
        // 刻意倒序插入：结果必须来自 ORDER BY，不能靠插入顺序蒙对
        insert("其他", "OTHER");
        insert("违法", "ILLEGAL");
        insert("加V", "AD");
        insert("傻X", "PROFANITY");
        insert("广告导流", "AD");
        insert("妈的", "PROFANITY");
        insert("sb", "PROFANITY");
        insert("操你妈", "PROFANITY");
        insert("测试违禁词", "PROFANITY");

        List<String> words = sensitiveWordMapper.selectPage(null, null, null, 100, 0)
                .stream().map(row -> row.word()).toList();

        // 首字拼音序：cao < ce < ma < sha < s（中文排序规则里汉字排在拉丁字母前）
        assertThat(words).containsExactly(
                "操你妈", "测试违禁词", "妈的", "傻X", "sb",
                "广告导流", "加V",
                "违法",
                "其他");
    }

    @Test
    @DisplayName("分类筛选后仍按首字拼音排序，且筛选与总数同条件")
    void selectPageKeepsOrderUnderCategoryFilter() {
        insert("傻X", "PROFANITY");
        insert("测试违禁词", "PROFANITY");
        insert("加V", "AD");

        assertThat(sensitiveWordMapper.selectPage(null, "PROFANITY", null, 100, 0)
                .stream().map(row -> row.word()).toList())
                .containsExactly("测试违禁词", "傻X");
        assertThat(sensitiveWordMapper.countBy(null, "PROFANITY", null)).isEqualTo(2L);
    }

    private void insert(String word, String category) {
        sensitiveWordMapper.insert(word, category, 1, "IT 排序用例", 9001L);
    }
}
