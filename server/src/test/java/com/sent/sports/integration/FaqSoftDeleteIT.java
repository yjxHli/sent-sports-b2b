package com.sent.sports.integration;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.chat.mapper.FaqKnowledgeMapper;
import com.sent.sports.chat.mapper.po.FaqKnowledgePO;
import com.sent.sports.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 话术软删真库校验（V62）。
 *
 * <p>单测用 mock 只能证明「调了哪个方法」，证明不了 SQL 对不对。这里盯三件事：</p>
 * <ol>
 *   <li>{@code batchMarkDeleted} 的 {@code <script>} + {@code IN} 真能被 MyBatis 解析；</li>
 *   <li>删掉的话术必须同时退出<b>机器人候选集</b>与管理端列表——少一处就会出现「后台删了、
 *       机器人还在回」这种最难解释的现象；</li>
 *   <li>重复提交同一批 ID 返回 0 行而不是报错（幂等：运营连点两下不该看到失败）。</li>
 * </ol>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class FaqSoftDeleteIT extends BaseIntegrationTest {

    @Autowired
    private FaqKnowledgeMapper mapper;

    @BeforeEach
    void seed() {
        insert(1L, "发货,物流", "默认发顺丰");
        insert(2L, "退货,退款", "支持7天无理由");
    }

    private void insert(long id, String keywords, String reply) {
        jdbc.update("INSERT INTO faq_knowledge (id, keywords, reply_content, hit_count, status) "
                + "VALUES (?, ?, ?, 7, 'ENABLED')", id, keywords, reply);
    }

    @Test
    @DisplayName("软删后：退出机器人候选集 + 退出管理端列表，命中次数原样保留")
    void softDeleteHidesFromBotAndAdmin() {
        assertThat(mapper.selectAllEnabledOrdered()).hasSize(2);

        assertThat(mapper.batchMarkDeleted(List.of(2L))).isEqualTo(1);

        assertThat(mapper.selectAllEnabledOrdered())
                .as("被删的话术不能再被机器人命中")
                .extracting(FaqKnowledgePO::getId)
                .containsExactly(1L);
        assertThat(mapper.selectAdminById(2L)).as("管理端也查不到了").isNull();

        Page<FaqKnowledgePO> page = new Page<>(1, 20);
        mapper.selectAdminPage(page, "退款", null);
        assertThat(page.getRecords()).as("已删除条目连搜索都不该出现").isEmpty();

        // 行还在库里：命中次数是运营复盘「这条话术有没有用」的唯一依据，软删不丢数据
        Integer hit = jdbc.queryForObject(
                "SELECT hit_count FROM faq_knowledge WHERE id = 2", Integer.class);
        assertThat(hit).isEqualTo(7);
    }

    @Test
    @DisplayName("重复删除同一批 ID 返回 0 行（幂等），不会把没删掉的算进去")
    void softDeleteIsIdempotent() {
        assertThat(mapper.batchMarkDeleted(List.of(2L))).isEqualTo(1);
        assertThat(mapper.batchMarkDeleted(List.of(2L))).as("再删一次是 0 行，不是异常").isZero();
        assertThat(mapper.batchMarkDeleted(List.of(1L, 2L))).as("混着已删的，只算真正删掉的").isEqualTo(1);
    }
}
