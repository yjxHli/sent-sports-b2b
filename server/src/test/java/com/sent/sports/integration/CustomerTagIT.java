package com.sent.sports.integration;

import com.sent.sports.customer.mapper.CustomerTagLinkMapper;
import com.sent.sports.customer.mapper.CustomerTagMapper;
import com.sent.sports.customer.mapper.po.CustomerTagPO;
import com.sent.sports.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户标签真库校验（V63）。
 *
 * <p>单测用 mock 证明不了三件事，这里补上：</p>
 * <ol>
 *   <li>{@code FIELD(category, ...)} 的排序真能按「优质 → 中性 → 风险 → 恶意」出来
 *       （如果 MySQL 版本对 {@code FIELD} 有异议，客服看到的顺序会变成建表顺序，谁也说不清）；</li>
 *   <li>{@code INSERT IGNORE} + {@code uk_customer_tag} 真的幂等——客服连点两次保存不能插两条；</li>
 *   <li>{@code after_json} 是 JSON 列，服务层拼的 {@code {"added":[..]}} 必须能被 MySQL 接受，
 *       格式写错会在真实打标时才 500，单测完全看不见。</li>
 * </ol>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class CustomerTagIT extends BaseIntegrationTest {

    @Autowired
    private CustomerTagMapper tagMapper;
    @Autowired
    private CustomerTagLinkMapper linkMapper;

    @BeforeEach
    void seed() {
        insertTag(1L, "忠实老客", "EXCELLENT", "ACTIVE", 1);
        insertTag(2L, "价格敏感", "NEUTRAL", "ACTIVE", 1);
        insertTag(3L, "投诉倾向", "RISK", "ACTIVE", 1);
        insertTag(4L, "羊毛党", "MALICIOUS", "ACTIVE", 1);
        insertTag(5L, "废弃标签", "NEUTRAL", "DISABLED", 2);
    }

    private void insertTag(long id, String name, String category, String status, int sortNo) {
        jdbc.update("INSERT INTO customer_tag (id, name, category, status, sort_no) VALUES (?, ?, ?, ?, ?)",
                id, name, category, status, sortNo);
    }

    @Test
    @DisplayName("目录：按分类业务顺序排列，客服弹窗默认不含停用项")
    void catalogOrderedByCategory() {
        assertThat(tagMapper.selectCatalog(false))
                .as("优质 → 中性 → 风险 → 恶意，停用的不出现")
                .extracting(CustomerTagPO::getName)
                .containsExactly("忠实老客", "价格敏感", "投诉倾向", "羊毛党");

        assertThat(tagMapper.selectCatalog(true))
                .as("管理端维护页要能看到停用项，否则没法重新启用")
                .hasSize(5);
    }

    @Test
    @DisplayName("打标：同客户同标签重复插入只有一行（INSERT IGNORE + uk_customer_tag）")
    void insertIgnoreIsIdempotent() {
        assertThat(linkMapper.insertIgnore(9002L, 1L, 9001L)).isEqualTo(1);
        assertThat(linkMapper.insertIgnore(9002L, 1L, 9001L)).as("再打一次是 0 行，不是异常").isZero();
        assertThat(countOf("customer_tag_link")).isEqualTo(1);

        linkMapper.insertIgnore(9002L, 4L, 9001L);
        assertThat(linkMapper.selectTagsOfCustomer(9002L))
                .as("客户身上的标签同样按分类顺序排：好话在前、风险在后")
                .extracting(CustomerTagPO::getName)
                .containsExactly("忠实老客", "羊毛党");

        assertThat(linkMapper.deleteOne(9002L, 1L)).isEqualTo(1);
        assertThat(linkMapper.selectTagIdsOfCustomer(9002L)).containsExactly(4L);
        assertThat(linkMapper.countByTag(4L)).isEqualTo(1);
        assertThat(linkMapper.countByTag(1L)).as("取消后不再算引用").isZero();
    }

    @Test
    @DisplayName("留痕：after_json 拼出来的花括号串必须能被 JSON 列吃下")
    void insertLogAcceptsJsonColumn() {
        tagMapper.insertLog(9001L, "TAG_APPLY", "9002", "{\"added\":[3],\"removed\":[1]}");

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE module = 'CUSTOMER' "
                        + "AND action = 'TAG_APPLY' AND target_type = 'CUSTOMER_TAG' "
                        + "AND target_id = '9002' AND JSON_EXTRACT(after_json, '$.added[0]') = 3",
                Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("排序号：新增标签取 max+1，排在同类最后（不插队，客服的肌肉记忆才守得住）")
    void newTagGoesLastWithinCategory() {
        assertThat(tagMapper.selectMaxSortNo()).isEqualTo(2);

        insertTag(6L, "沟通正常", "NEUTRAL", "ACTIVE", tagMapper.selectMaxSortNo() + 1);

        assertThat(tagMapper.selectCatalog(false))
                .extracting(CustomerTagPO::getName)
                .containsExactly("忠实老客", "价格敏感", "沟通正常", "投诉倾向", "羊毛党");
    }
}
