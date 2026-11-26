package com.sent.sports.customer.mapper;

import com.sent.sports.customer.mapper.po.CustomerTagPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客户 ↔ 标签关联 Mapper（{@code customer_tag_link}，V63）。
 *
 * <p>「取消标签」是物理删除而不是打标记：这张表只回答「此刻这个客户身上有哪些标签」，
 * 历史由 {@code sys_operation_log} 承担。靠软删列过滤会让每个查询都多一个条件，
 * 漏一处就会出现「取消了还在」。防重靠 {@code uk_customer_tag}。</p>
 */
@Mapper
public interface CustomerTagLinkMapper {

    /** 某客户身上的标签（含已停用的：停用只是不能再新打，已经打上的仍要给客服看到）。 */
    @Select("SELECT t.* FROM customer_tag t "
            + "JOIN customer_tag_link l ON l.tag_id = t.id "
            + "WHERE l.customer_id = #{customerId} "
            + "ORDER BY FIELD(t.category, 'EXCELLENT', 'NEUTRAL', 'RISK', 'MALICIOUS'), t.sort_no, t.id")
    List<CustomerTagPO> selectTagsOfCustomer(@Param("customerId") long customerId);

    /** 只取 ID：算差异增删用，不必把定义整行读出来。 */
    @Select("SELECT tag_id FROM customer_tag_link WHERE customer_id = #{customerId}")
    List<Long> selectTagIdsOfCustomer(@Param("customerId") long customerId);

    /**
     * 打标：{@code INSERT IGNORE} 依赖 {@code uk_customer_tag}，
     * 重复提交同一标签影响 0 行也算成功（与项目「防重只用 DB 唯一索引」一致，不加分布式锁）。
     */
    @Insert("INSERT IGNORE INTO customer_tag_link (customer_id, tag_id, created_by, created_at) "
            + "VALUES (#{customerId}, #{tagId}, #{operatorId}, NOW(3))")
    int insertIgnore(@Param("customerId") long customerId,
                     @Param("tagId") long tagId,
                     @Param("operatorId") Long operatorId);

    /** 取消单个标签。 */
    @Delete("DELETE FROM customer_tag_link WHERE customer_id = #{customerId} AND tag_id = #{tagId}")
    int deleteOne(@Param("customerId") long customerId, @Param("tagId") long tagId);

    /** 该标签被多少客户使用（删标签前的引用检查）。 */
    @Select("SELECT COUNT(*) FROM customer_tag_link WHERE tag_id = #{tagId}")
    int countByTag(@Param("tagId") long tagId);
}
