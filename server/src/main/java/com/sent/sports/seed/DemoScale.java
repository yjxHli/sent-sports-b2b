package com.sent.sports.seed;

/**
 * 演示数据规模档位（{@code app.demo-data.scale}）。
 *
 * <p><b>为什么要有档位</b>：清过测试数据的库只剩几个账号，而 E2E 里有一批用例假设「页面里有数据」——
 * 跨页全选要客户多于一页、报表热销榜要 5 个成交 SKU、复购三档柱高要拉出 3 倍差、
 * 库存预警要至少 1 个低于安全库存、财务要有过期合同。全量演示数据（2000 单）回灌太重，
 * 只需要「够断言用」的最小数据面。</p>
 *
 * <ul>
 *   <li>{@link #FULL}：收口阶段的演示数据，150 个人 + 50 企业客户、2000 单，用于演示 / 截图。</li>
 *   <li>{@link #TINY}：E2E 专用最小集，12 个人 + 25 企业客户、115 单（含复购结构）、6 个会话。</li>
 * </ul>
 *
 * <p><b>幂等阈值</b>：FULL 沿用「订单 &gt; 100 就跳过」，TINY 必须更严（订单 &gt; 0 就跳过），
 * 否则每次重启都会再叠 115 单，几次之后 tiny 就退化成了全量。</p>
 *
 * <p><b>TINY 的数字不是随手给的</b>：每一条都对应 E2E 的硬断言——
 * 个人客户 12 让「我的订单/定制/退款」翻得到第 2 页，企业 25 让企业列表与合同、应收都越过 20 条门槛，
 * 115 单撑起 9002 的 21 单深水区 + 6 个复购客户三档分桶 + 25 个企业的 WAIT_SHIP/COMPLETED 双态。</p>
 */
public enum DemoScale {

    /** 全量演示数据：与改造前行为一致。 */
    FULL(150, 50, 2000, 30, 100),

    /** E2E 最小数据面：够每一页断言用，但远小于全量（115 单 vs 2000 单）。 */
    TINY(12, 25, 115, 6, 0);

    private final int personalCustomers;
    private final int enterpriseCustomers;
    private final int orderCount;
    private final int chatSessions;
    private final int skipOrderThreshold;

    DemoScale(int personalCustomers, int enterpriseCustomers, int orderCount,
              int chatSessions, int skipOrderThreshold) {
        this.personalCustomers = personalCustomers;
        this.enterpriseCustomers = enterpriseCustomers;
        this.orderCount = orderCount;
        this.chatSessions = chatSessions;
        this.skipOrderThreshold = skipOrderThreshold;
    }

    /** 解析配置值，未知值一律按 {@link #FULL} 处理（保守：宁可多造也不造残）。 */
    public static DemoScale of(String raw) {
        if (raw == null) {
            return FULL;
        }
        return "tiny".equalsIgnoreCase(raw.trim()) ? TINY : FULL;
    }

    public int personalCustomers() {
        return personalCustomers;
    }

    public int enterpriseCustomers() {
        return enterpriseCustomers;
    }

    public int orderCount() {
        return orderCount;
    }

    public int chatSessions() {
        return chatSessions;
    }

    public int skipOrderThreshold() {
        return skipOrderThreshold;
    }

    public boolean isTiny() {
        return this == TINY;
    }
}
