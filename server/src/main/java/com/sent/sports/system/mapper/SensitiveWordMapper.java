package com.sent.sports.system.mapper;

import com.sent.sports.system.mapper.po.SensitiveWordRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 违禁词库 Mapper（{@code sys_sensitive_word}，V46）。
 * <p>词库是「配置型小表」：列表页要分页 + 筛选，匹配侧要一次性拿到全部启用词。
 * 列表与 count 共用同一段 WHERE（{@link #FILTERS}），避免出现「筛选后总数不对」的老问题；
 * 新建/改名靠唯一键 {@code uk_sensitive_word} 兜重复，不引分布式锁。</p>
 */
@Mapper
public interface SensitiveWordMapper {

    /** 列表页筛选条件：关键字（词/备注）、分类、启停；三项都可缺省。列表与 count 必须同条件。 */
    String FILTERS = "<where>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND (word LIKE CONCAT('%', #{keyword}, '%') OR remark LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "<if test='category != null and category != \"\"'> AND category = #{category} </if>"
            + "<if test='enabled != null'> AND enabled = #{enabled} </if>"
            + "</where>";

    String COLUMNS = "id, word, category, enabled, remark, "
            + "created_at AS createdAt, updated_at AS updatedAt";

    /**
     * 分类展示顺序与管理端下拉一致（辱骂脏话 → 广告导流 → 违法违禁 → 其他）。
     * 未知分类落到「其他」组，不会插在最前面。
     */
    String CATEGORY_ORDER = "CASE category WHEN 'PROFANITY' THEN 1 WHEN 'AD' THEN 2 "
            + "WHEN 'ILLEGAL' THEN 3 ELSE 4 END";

    /**
     * 分页列表：先分类、再按词条首个文字的字母/拼音顺序。
     * <p>首字统一转 utf8mb4 后用 {@code utf8mb4_zh_0900_as_cs} 排：汉字按拼音，
     * 且与英文/数字落在同一字母序里（GBK 会把 ASCII 全部顶到汉字前面，中英混排时不对）。
     * 首字相同再用 id 兜底，保证翻页不抖动。</p>
     */
    @Select("<script>SELECT " + COLUMNS + " FROM sys_sensitive_word " + FILTERS
            + " ORDER BY " + CATEGORY_ORDER
            + ", CONVERT(LEFT(word, 1) USING utf8mb4) COLLATE utf8mb4_zh_0900_as_cs ASC, id ASC"
            + " LIMIT #{limit} OFFSET #{offset}</script>")
    List<SensitiveWordRow> selectPage(@Param("keyword") String keyword,
                                      @Param("category") String category,
                                      @Param("enabled") Integer enabled,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    /** 与 {@link #selectPage} 同条件的总数。 */
    @Select("<script>SELECT COUNT(*) FROM sys_sensitive_word " + FILTERS + "</script>")
    long countBy(@Param("keyword") String keyword,
                 @Param("category") String category,
                 @Param("enabled") Integer enabled);

    /** 全部启用词：只取 word 列，供 {@code SensitiveWordFilter.reload} 整树重建。 */
    @Select("SELECT word FROM sys_sensitive_word WHERE enabled = 1")
    List<String> selectEnabledWords();

    @Select("SELECT " + COLUMNS + " FROM sys_sensitive_word WHERE id = #{id}")
    SensitiveWordRow selectById(@Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM sys_sensitive_word WHERE word = #{word}")
    SensitiveWordRow selectByWord(@Param("word") String word);

    @Insert("INSERT INTO sys_sensitive_word (word, category, enabled, remark, created_by) "
            + "VALUES (#{word}, #{category}, #{enabled}, #{remark}, #{createdBy})")
    int insert(@Param("word") String word,
               @Param("category") String category,
               @Param("enabled") Integer enabled,
               @Param("remark") String remark,
               @Param("createdBy") Long createdBy);

    /** 整行更新：改词 / 改分类 / 改备注同时也能启停（前端编辑弹窗一次提交全部字段）。 */
    @Update("UPDATE sys_sensitive_word SET word = #{word}, category = #{category}, "
            + "enabled = #{enabled}, remark = #{remark} WHERE id = #{id}")
    int update(@Param("id") long id,
               @Param("word") String word,
               @Param("category") String category,
               @Param("enabled") Integer enabled,
               @Param("remark") String remark);

    /** 只切启停：列表页开关不提交其它字段，避免把并发编辑改过的备注覆盖掉。 */
    @Update("UPDATE sys_sensitive_word SET enabled = #{enabled} WHERE id = #{id}")
    int updateEnabled(@Param("id") long id, @Param("enabled") int enabled);

    @Delete("DELETE FROM sys_sensitive_word WHERE id = #{id}")
    int deleteById(@Param("id") long id);

    /** 统计：总数 / 启用数 / 停用数一次查齐（首页顶部三个数字）。 */
    @Select("SELECT COUNT(*) FROM sys_sensitive_word")
    long countAll();

    @Select("SELECT COUNT(*) FROM sys_sensitive_word WHERE enabled = 1")
    long countEnabled();
}
