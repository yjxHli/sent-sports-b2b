-- 评价中心（个人中心「订单中心 → 评价中心」）：
-- 页面顶部要展示「已写评论 / 浏览数 / 获赞数」三项，其中后两项是评价自身的累积指标。
-- 之前只有「好评率」这类商品维度聚合，没有单条评价的浏览与点赞计数，
-- 这里补两列：默认 0，历史数据不需要回填（没人看过就是 0，不造假）。
--
-- 索引说明：V55 已建 idx_review_customer(customer_id)，评价中心的
-- `WHERE customer_id = ? ORDER BY id DESC` 直接复用即可，
-- 这里不再追加 (customer_id, id) 复合索引——同名索引会让迁移在部分库上直接失败。
ALTER TABLE product_review
    ADD COLUMN view_count INT NOT NULL DEFAULT 0 COMMENT '该评价被浏览次数',
    ADD COLUMN like_count INT NOT NULL DEFAULT 0 COMMENT '该评价获赞次数';
