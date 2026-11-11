package com.sent.sports.customize.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.customize.mapper.po.CustomOrderListItemPO;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定制 Mapper（P5-16）：定制单与图稿版本同属一个聚合，故收敛在同一 Mapper。
 * <p>审图 SLA 依赖 {@code idx_custom_status(status, review_deadline)}：待审列表与超时扫描都走该索引，
 * 避免定时任务（P5-47）全表扫。
 */
@Mapper
public interface CustomOrderMapper extends BaseMapper<CustomOrderPO> {

    /**
     * 用户端「我的定制单」分页（收口阶段）：联查订单状态与最新图稿版本。
     * 由 MyBatis-Plus 分页拦截器按传入 {@link Page} 自动追加 LIMIT/OFFSET；总数另由 {@link #countMy} 统计。
     */
    @Select("SELECT co.custom_no AS customOrderId, co.status, co.reject_count AS rejectCount, "
            + "COALESCE(d.max_version, co.current_version) AS version, "
            + "o.status AS orderStatus, co.review_deadline AS reviewDeadline "
            + "FROM custom_order co "
            + "LEFT JOIN order_item oi ON oi.id = co.order_item_id "
            + "LEFT JOIN orders o ON o.id = oi.order_id "
            + "LEFT JOIN (SELECT custom_order_id, MAX(version) AS max_version FROM custom_draft "
            + "          GROUP BY custom_order_id) d ON d.custom_order_id = co.id "
            + "WHERE co.customer_id = #{customerId} "
            + "ORDER BY co.created_at DESC")
    IPage<CustomOrderListItemPO> selectMyPage(Page<CustomOrderListItemPO> page,
                                             @Param("customerId") long customerId);

    /** 用户端「我的定制单」总数（收口阶段）。 */
    @Select("SELECT COUNT(*) FROM custom_order WHERE customer_id = #{customerId}")
    long countMy(@Param("customerId") long customerId);

    /** 按定制单号读取。 */
    @Select("SELECT * FROM custom_order WHERE custom_no = #{customNo}")
    CustomOrderPO selectByCustomNo(@Param("customNo") String customNo);

    /** 按订单行读取（一个订单行至多一张定制单，唯一索引保证）。 */
    @Select("SELECT * FROM custom_order WHERE order_item_id = #{orderItemId}")
    CustomOrderPO selectByOrderItemId(@Param("orderItemId") long orderItemId);

    /**
     * 发货落账：找出订单里带定制标记、但还没有定制单的订单行。
     * 跨表联查沿袭 {@link #selectMyPage} 的既有口径（表间无物理外键，由 SQL 保证归属）。
     */
    @Select("SELECT oi.id AS orderItemId, o.customer_id AS customerId, oi.qty AS qty, "
            + "oi.custom_fee_cent AS feeCent "
            + "FROM orders o JOIN order_item oi ON oi.order_id = o.id "
            + "LEFT JOIN custom_order co ON co.order_item_id = oi.id "
            + "WHERE o.order_no = #{orderNo} AND oi.custom_flag = 1 AND co.id IS NULL")
    List<CustomOrderSeed> selectMissingCustomLines(@Param("orderNo") String orderNo);

    /** 新建定制单：{@code uk_custom_order_item} / {@code uk_custom_no} 冲突时静默跳过，天然幂等。 */
    @Insert("INSERT IGNORE INTO custom_order "
            + "(custom_no, order_item_id, customer_id, qty, fee_cent, status) "
            + "VALUES (#{customNo}, #{orderItemId}, #{customerId}, #{qty}, #{feeCent}, 'WAIT_DRAFT')")
    int insertIfAbsent(@Param("customNo") String customNo,
                       @Param("orderItemId") long orderItemId,
                       @Param("customerId") long customerId,
                       @Param("qty") int qty,
                       @Param("feeCent") long feeCent);

    /** 待审队列（按审核截止时间升序，越紧急越靠前）。 */
    @Select("SELECT * FROM custom_order WHERE status = 'WAIT_REVIEW' "
            + "ORDER BY review_deadline, id LIMIT #{limit}")
    List<CustomOrderPO> selectPendingReview(@Param("limit") int limit);

    /** 已超审核截止时间仍未处理的定制单（P5-47 升级提醒）。 */
    @Select("SELECT * FROM custom_order WHERE status = 'WAIT_REVIEW' "
            + "AND review_deadline IS NOT NULL AND review_deadline <= #{now} "
            + "ORDER BY review_deadline LIMIT #{limit}")
    List<CustomOrderPO> selectReviewTimeout(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 条件迁移：返回 0 行表示状态已被并发推进（如客户撤回与客服审核同时发生）。 */
    @Update("UPDATE custom_order SET status = #{to} WHERE custom_no = #{customNo} AND status = #{from}")
    int transitStatus(@Param("customNo") String customNo,
                      @Param("from") String from,
                      @Param("to") String to);

    /** 提交新图稿：版本号 +1 并回到待审，同时刷新审核截止时间。 */
    @Update("UPDATE custom_order SET current_version = current_version + 1, status = 'WAIT_REVIEW', "
            + "review_deadline = #{deadline} WHERE custom_no = #{customNo} AND status IN ('WAIT_DRAFT', 'REJECTED')")
    int submitDraft(@Param("customNo") String customNo, @Param("deadline") LocalDateTime deadline);

    /** 审核驳回：驳回次数 +1，回到待提交图稿。 */
    @Update("UPDATE custom_order SET status = 'REJECTED', reject_count = reject_count + 1, "
            + "reviewer_id = #{reviewerId}, remark = #{remark} WHERE custom_no = #{customNo} AND status = 'WAIT_REVIEW'")
    int reject(@Param("customNo") String customNo,
               @Param("reviewerId") Long reviewerId,
               @Param("remark") String remark);

    /** 写入图稿版本（版本号由 {@code uk_draft_version} 唯一键守护，重复提交同版本直接失败）。 */
    @Insert("INSERT INTO custom_draft (custom_order_id, version, file_id, file_name, file_size, uploaded_at) "
            + "VALUES (#{customOrderId}, #{version}, #{fileId}, #{fileName}, #{fileSize}, #{uploadedAt})")
    int insertDraft(@Param("customOrderId") long customOrderId,
                    @Param("version") int version,
                    @Param("fileId") String fileId,
                    @Param("fileName") String fileName,
                    @Param("fileSize") long fileSize,
                    @Param("uploadedAt") LocalDateTime uploadedAt);

    // ------------------------------------------------------------------
    // Phase 10：定制图稿 / 审图日志真库落账
    // ------------------------------------------------------------------

    /** 定制单号 → 物理主键（图稿与审图日志都通过此外键归属）。 */
    @Select("SELECT id FROM custom_order WHERE custom_no = #{customNo}")
    Long selectIdByCustomNo(@Param("customNo") String customNo);

    /** 真库仓储回写聚合状态：状态 / 版本 / 驳回次数 / 锁定版本 / 三个时间字段。 */
    @Update("UPDATE custom_order SET status = #{status}, current_version = #{currentVersion}, "
            + "reject_count = #{rejectCount}, locked_version = #{lockedVersion}, "
            + "review_deadline = #{reviewDeadline}, production_start_at = #{productionStartAt}, "
            + "finished_at = #{finishedAt} WHERE custom_no = #{customNo}")
    int updateAggregate(CustomOrderState state);

    /** save() 的 upsert 分支：单号不存在时补一条最小可用主记录（uk_custom_no 幂等）。 */
    @Insert("INSERT IGNORE INTO custom_order "
            + "(custom_no, customer_id, qty, status, current_version, reject_count, locked_version, review_deadline) "
            + "VALUES (#{customNo}, 0, 0, #{status}, #{currentVersion}, #{rejectCount}, "
            + "#{lockedVersion}, #{reviewDeadline})")
    int insertAggregateIfAbsent(CustomOrderState state);

    /** 审图日志：V13 唯一键 uk_review_once 兜底重复审核，INSERT IGNORE 返回 0 即重放。 */
    @Insert("INSERT IGNORE INTO custom_review_log "
            + "(custom_order_id, draft_version, result, reason_code, remark, reviewer_id, created_at) "
            + "VALUES (#{customOrderId}, #{draftVersion}, #{result}, #{reasonCode}, "
            + "#{remark}, #{reviewerId}, #{createdAt})")
    int insertReviewLog(@Param("customOrderId") long customOrderId,
                        @Param("draftVersion") int draftVersion,
                        @Param("result") String result,
                        @Param("reasonCode") String reasonCode,
                        @Param("remark") String remark,
                        @Param("reviewerId") Long reviewerId,
                        @Param("createdAt") LocalDateTime createdAt);

    /** 审核台列表：全部定制单按创建时间倒序（MyBatis-Plus 分页拦截器自动 count + LIMIT）。 */
    @Select("SELECT * FROM custom_order ORDER BY created_at DESC, id DESC")
    IPage<CustomOrderPO> selectAdminPage(Page<CustomOrderPO> page);

    @Select("SELECT COUNT(*) FROM custom_draft WHERE custom_order_id = #{customOrderId}")
    long countDrafts(@Param("customOrderId") long customOrderId);

    @Select("SELECT COUNT(*) FROM custom_review_log WHERE custom_order_id = #{customOrderId}")
    long countReviewLogs(@Param("customOrderId") long customOrderId);

    @Select("SELECT file_id FROM custom_draft WHERE custom_order_id = #{customOrderId} "
            + "ORDER BY version DESC LIMIT 1")
    String selectLatestFileId(@Param("customOrderId") long customOrderId);

    @Select("SELECT version AS version, file_id AS fileId, file_name AS fileName, file_size AS fileSize "
            + "FROM custom_draft WHERE custom_order_id = #{customOrderId} ORDER BY version")
    List<DraftRow> selectDrafts(@Param("customOrderId") long customOrderId);

    @Select("SELECT draft_version AS draftVersion, result, reason_code AS reasonCode, remark, "
            + "reviewer_id AS reviewerId FROM custom_review_log "
            + "WHERE custom_order_id = #{customOrderId} ORDER BY id")
    List<ReviewRow> selectReviewLogs(@Param("customOrderId") long customOrderId);

    /** 发货 / 退款门禁的轻量视图：按订单号 + SKU 联查该订单行对应定制单状态。 */
    @Select("SELECT co.status FROM custom_order co "
            + "JOIN order_item oi ON oi.id = co.order_item_id "
            + "JOIN orders o ON o.id = oi.order_id "
            + "WHERE o.order_no = #{orderNo} AND oi.sku_id = #{skuId} LIMIT 1")
    String selectStatusOfOrderLine(@Param("orderNo") String orderNo, @Param("skuId") long skuId);

    /** 真库仓储回写聚合用的参数载体（非表映射对象）。 */
    class CustomOrderState {
        private String customNo;
        private String status;
        private int currentVersion;
        private int rejectCount;
        private int lockedVersion;
        private LocalDateTime reviewDeadline;
        private LocalDateTime productionStartAt;
        private LocalDateTime finishedAt;

        public String getCustomNo() { return customNo; }
        public void setCustomNo(String customNo) { this.customNo = customNo; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }
        public int getRejectCount() { return rejectCount; }
        public void setRejectCount(int rejectCount) { this.rejectCount = rejectCount; }
        public int getLockedVersion() { return lockedVersion; }
        public void setLockedVersion(int lockedVersion) { this.lockedVersion = lockedVersion; }
        public LocalDateTime getReviewDeadline() { return reviewDeadline; }
        public void setReviewDeadline(LocalDateTime reviewDeadline) { this.reviewDeadline = reviewDeadline; }
        public LocalDateTime getProductionStartAt() { return productionStartAt; }
        public void setProductionStartAt(LocalDateTime productionStartAt) { this.productionStartAt = productionStartAt; }
        public LocalDateTime getFinishedAt() { return finishedAt; }
        public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    }

    /** 图稿版本行（审核台详情用）。 */
    class DraftRow {
        private Integer version;
        private String fileId;
        private String fileName;
        private Long fileSize;

        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    }

    /** 审图日志行（审核台详情用）。 */
    class ReviewRow {
        private Integer draftVersion;
        private String result;
        private String reasonCode;
        private String remark;
        private Long reviewerId;

        public Integer getDraftVersion() { return draftVersion; }
        public void setDraftVersion(Integer draftVersion) { this.draftVersion = draftVersion; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public Long getReviewerId() { return reviewerId; }
        public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    }

    /** 发货落账时联查得到的定制行快照（非表映射对象，只承载写入参数）。 */
    class CustomOrderSeed {
        private Long orderItemId;
        private Long customerId;
        private Integer qty;
        private Long feeCent;

        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
        public Long getFeeCent() { return feeCent; }
        public void setFeeCent(Long feeCent) { this.feeCent = feeCent; }
    }
}
