package com.sent.sports.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.customer.mapper.po.CustomerTagPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客户标签定义 Mapper（{@code customer_tag}，V63）。
 *
 * <p>排序口径写在 SQL 里：先按分类业务顺序（优质 → 中性 → 风险 → 恶意），再按 sort_no。
 * 用 {@code FIELD()} 而非让前端排——客服端与管理端必须完全同序，
 * 否则同一批标签在两个页面上的先后不一致，运营会以为「打的标不一样」。</p>
 */
@Mapper
public interface CustomerTagMapper extends BaseMapper<CustomerTagPO> {

    /**
     * 标签目录。
     *
     * @param includeDisabled 管理端维护页要看停用项（true）；客服弹窗只要可选项（false）
     */
    @Select("<script>"
            + "SELECT * FROM customer_tag "
            + "<if test='!includeDisabled'> WHERE status = 'ACTIVE' </if>"
            + "ORDER BY FIELD(category, 'EXCELLENT', 'NEUTRAL', 'RISK', 'MALICIOUS'), sort_no, id"
            + "</script>")
    List<CustomerTagPO> selectCatalog(@Param("includeDisabled") boolean includeDisabled);

    /** 按名精确查（含停用）：重名校验用，停用的名字也不允许被重新占用。 */
    @Select("SELECT * FROM customer_tag WHERE name = #{name} LIMIT 1")
    CustomerTagPO selectByName(@Param("name") String name);

    /** 批量取（校验「要打上的标签是否存在且启用」）。 */
    @Select("<script>SELECT * FROM customer_tag WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<CustomerTagPO> selectByIds(@Param("ids") List<Long> ids);

    /** 当前最大排序号：新增标签排在同类最后，不插队（插队会让客服的肌肉记忆失效）。 */
    @Select("SELECT COALESCE(MAX(sort_no), 0) FROM customer_tag")
    Integer selectMaxSortNo();

    /**
     * 写操作日志（{@code sys_operation_log}）。
     * <p>标签是内部评价，谁给谁打了「羊毛党」事后必须能查——这是最容易引发争议的一类数据。</p>
     */
    @Insert("INSERT INTO sys_operation_log "
            + "(staff_id, module, action, target_type, target_id, reason, before_json, after_json, created_by) "
            + "VALUES (#{staffId}, 'CUSTOMER', #{action}, 'CUSTOMER_TAG', #{targetId}, NULL, NULL, "
            + "#{afterJson,jdbcType=VARCHAR}, #{staffId})")
    int insertLog(@Param("staffId") Long staffId,
                  @Param("action") String action,
                  @Param("targetId") String targetId,
                  @Param("afterJson") String afterJson);
}
