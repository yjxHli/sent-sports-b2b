package com.sent.sports.seed;

import com.sent.sports.common.storage.Storage;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customize.mapper.CustomDraftMapper;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomDraftPO;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.ContractMapper;
import com.sent.sports.finance.mapper.PaymentRecordMapper;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.WriteOffMapper;
import com.sent.sports.finance.mapper.po.ContractPO;
import com.sent.sports.finance.mapper.po.InvoiceRequestPO;
import com.sent.sports.finance.mapper.po.PaymentRecordPO;
import com.sent.sports.finance.mapper.po.ReceivablePO;
import com.sent.sports.finance.mapper.po.WriteOffPO;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.ShipmentMapper;
import com.sent.sports.order.mapper.po.OrderItemPO;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.payment.mapper.PaymentTxnMapper;
import com.sent.sports.payment.mapper.po.PaymentTxnPO;
import com.sent.sports.product.mapper.CatalogMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.SkuMapper;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.product.mapper.po.SkuPO;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundOrderPO;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.mapper.po.SkuStockPO;
import com.sent.sports.system.mapper.StaffMapper;
import com.sent.sports.system.mapper.po.SysStaffPO;
import com.sent.sports.chat.mapper.ChatMessageMapper;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.po.ChatMessagePO;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 演示数据生成器（收口阶段）：让系统看起来像真实运营了半年的 B2B 商城。
 *
 * <p><b>环境隔离</b>：{@code @Profile("!prod")} 保证不进生产；{@code @ConditionalOnProperty} 仅当
 * {@code app.demo-data.enabled=true} 时运行（dev / test 均默认关闭），避免在集成测试上下文里灌数据拖慢启动 / 污染隔离。
 * dev 默认值已改为 {@code ${APP_DEMO_DATA_ENABLED:false}}：清理过测试数据的库，重启不应被自动回灌；
 * 需要演示数据时单次启动带 {@code --app.demo-data.enabled=true}。</p>
 *
 * <p><b>幂等</b>：启动时先查订单数量，若 {@code > 100} 直接跳过，保证重复启动不会叠加数据。</p>
 *
 * <p><b>时间分布</b>：所有业务时间落在 {@code [now-180d, now]}，且用三次方偏置让「近期更密集」，
 * 以贴合真实运营曲线。历史时间直接写进 {@code created_at/updated_at}（MetaObjectHandler 仅对 null 字段回填，故手动赋值即生效）。</p>
 *
 * <p><b>架构合规</b>：本类是开发期夹具（{@link CommandLineRunner}），不是定时业务任务，
 * 独立放在 {@code com.sent.sports.seed} 包，故不进入 {@code ..job..} 的跨模块约束。
 * 它按原始 PO 批量灌库，只服务演示环境（{@code @Profile("!prod")} + 配置开关），
 * 不承载任何业务动作；真实业务动作仍只能走各模块用例。</p>
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = false)
public class DemoDataGenerator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataGenerator.class);

    private final Random random = new Random();
    private final LocalDateTime NOW = LocalDateTime.now();

    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final OrderMapper orderMapper;
    private final PaymentTxnMapper paymentTxnMapper;
    private final ReceivableMapper receivableMapper;
    private final WriteOffMapper writeOffMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final CustomOrderMapper customOrderMapper;
    private final CustomDraftMapper customDraftMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ShipmentMapper shipmentMapper;
    private final EnterpriseProfileMapper enterpriseProfileMapper;
    private final StaffMapper staffMapper;
    private final CatalogMapper catalogMapper;
    private final StockMapper stockMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final ContractMapper contractMapper;
    private final InvoiceMapper invoiceMapper;
    private final IdGen idGen;
    /** 演示图稿要落真实文件：审核台按 fileId 拼 /api/files/{fileId} 渲染缩略图，假 ID 会 404 破图。 */
    private final Storage fileStorage;

    @Autowired
    public DemoDataGenerator(CustomerMapper customerMapper, ProductMapper productMapper, SkuMapper skuMapper,
                             OrderMapper orderMapper, PaymentTxnMapper paymentTxnMapper,
                             ReceivableMapper receivableMapper, WriteOffMapper writeOffMapper,
                             PaymentRecordMapper paymentRecordMapper, CustomOrderMapper customOrderMapper,
                             CustomDraftMapper customDraftMapper, ChatSessionMapper chatSessionMapper,
                             ChatMessageMapper chatMessageMapper, ShipmentMapper shipmentMapper,
                             EnterpriseProfileMapper enterpriseProfileMapper, StaffMapper staffMapper,
                             CatalogMapper catalogMapper, StockMapper stockMapper,
                             RefundOrderMapper refundOrderMapper, ContractMapper contractMapper,
                             InvoiceMapper invoiceMapper, IdGen idGen, Storage fileStorage) {
        this.customerMapper = customerMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.orderMapper = orderMapper;
        this.paymentTxnMapper = paymentTxnMapper;
        this.receivableMapper = receivableMapper;
        this.writeOffMapper = writeOffMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.customOrderMapper = customOrderMapper;
        this.customDraftMapper = customDraftMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.shipmentMapper = shipmentMapper;
        this.enterpriseProfileMapper = enterpriseProfileMapper;
        this.staffMapper = staffMapper;
        this.catalogMapper = catalogMapper;
        this.stockMapper = stockMapper;
        this.refundOrderMapper = refundOrderMapper;
        this.contractMapper = contractMapper;
        this.invoiceMapper = invoiceMapper;
        this.idGen = idGen;
        this.fileStorage = fileStorage;
    }

    // ---- 跨方法共享的运行时数据 ----
    private List<Long> personalCustomerIds = new ArrayList<>();
    private List<Long> enterpriseCustomerIds = new ArrayList<>();
    private List<Long> allCustomerIds = new ArrayList<>();
    private List<Long> staffIds = new ArrayList<>();
    private List<SkuPO> skus = new ArrayList<>();
    private Map<Long, String> productTitle = new HashMap<>();
    /** 已被订单用到的 SKU：压低库存做预警时要避开它们，否则会把在售商品打成无货。 */
    private Set<Long> usedSkuIds = new HashSet<>();
    /** tiny 档的 E2E 验收账号（9002），惰性解析：库被清空时退化为第一个个人客户。 */
    private Long e2eCustomerId;

    /** 演示数据规模：{@code full}（默认，150+50 客户 / 2000 单）或 {@code tiny}（E2E 最小集）。 */
    @Value("${app.demo-data.scale:full}")
    private String scaleName;

    /**
     * 清空运行时列表，保证同一个实例可以安全地 {@code run()} 多次（集成测试会在每个用例前清库后重跑）。
     * 不重置会让上一轮已删除的客户 ID 混进 {@code allCustomerIds}，生成出指向不存在客户的订单。
     */
    private void resetRuntimeState() {
        personalCustomerIds = new ArrayList<>();
        enterpriseCustomerIds = new ArrayList<>();
        allCustomerIds = new ArrayList<>();
        staffIds = new ArrayList<>();
        skus = new ArrayList<>();
        productTitle = new HashMap<>();
        usedSkuIds = new HashSet<>();
        e2eCustomerId = null;
    }

    /** 演示图稿文件（内容固定 → 去重后磁盘上只有一份），返回可直接渲染的 fileId。 */
    private String demoDraftFileId() {
        if (demoDraftFileId == null) {
            demoDraftFileId = fileStorage.store(DEMO_PNG_BYTES, "design_v1.png");
        }
        return demoDraftFileId;
    }

    /** 8x8 纯色 PNG：够小、能正常解码，避免审核台显示破图。 */
    private static final byte[] DEMO_PNG_BYTES = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAHElEQVQoz2NkYPjPQAxgYiASjCoc"
                    + "WDiqgCgcAAA7dQIB0m4nBwAAAABJRU5ErkJggg==");

    private String demoDraftFileId;

    private static final String[] CATEGORIES = {
            "球类运动", "健身器材", "瑜伽用品", "跑步装备", "户外运动", "团队运动", "游泳装备", "运动服饰"
    };
    private static final String[] PRODUCT_NAMES = {
            "专业比赛篮球", "训练用足球", "天然橡胶瑜伽垫", "缓震跑步鞋", "可调节哑铃套装", "弹力带训练组",
            "碳素羽毛球拍", "网球拍", "高清泳镜", "速干运动毛巾", "护腕", "健身手套", "计数跳绳",
            "运动水壶", "加压护膝", "碳素登山杖", "骑行头盔", "速干训练T恤", "压缩长裤", "运动双肩包",
            "防爆平衡球", "悬挂训练带", "竞技壶铃", "护腰", "拳击手套", "飞盘", "进阶滑板", "攀岩动力绳", "心率带", "能量补给胶"
    };
    private static final String[] BRANDS = {"SENT", "森特", "KEEP", "DECATHLON", "李宁", "安踏"};
    private static final String[] CARRIERS = {"SF", "ZTO", "YTO", "JD"};
    private static final String[] CHAT_TEXTS = {
            "您好，这款篮球有 7 号标准款吗？", "麻烦发一下企业采购的报价单。", "我们的 logo 能印在球面吗？",
            "这套哑铃支持定制重量吗？", "发货大概要几天？", "发票可以开增值税专用发票吗？",
            "之前那批货质量不错，再来 50 套。", "能走对公转账吗？账期可以谈吗？", "样品什么时候能寄到？",
            "好的，那就按这个方案下单。", "麻烦确认下定制稿的颜色。", "收到，谢谢！"
    };

    @Override
    public void run(String... args) {
        DemoScale mode = DemoScale.of(scaleName);
        resetRuntimeState();

        long orderCount = orderMapper.selectCount(null);
        if (orderCount > mode.skipOrderThreshold()) {
            log.info("[DemoData] 检测到已存在 {} 条订单（>{}），跳过 {} 档演示数据生成。",
                    orderCount, mode.skipOrderThreshold(), mode);
            // 订单已就绪时不重灌全量数据，但财务/售后两页仍可能缺展示数据（合同表为空、
            // 退款单清一色 REFUNDED）。补齐动作本身幂等，每次启动都尝试一次。
            backfillFinanceShowcase();
            return;
        }
        log.info("[DemoData] 开始生成演示数据（档位={}，环境隔离：非生产 + demo-data.enabled=true）...", mode);
        long start = System.currentTimeMillis();

        loadStaff();
        ensureCatalog();
        ensureCustomers(mode);
        loadSellable();
        generateOrders(mode);
        generateChats(mode.chatSessions());
        if (mode.isTiny()) {
            ensureTinyRefunds();
            ensureTinyReceivables();
            ensureTinyInvoices();
            ensureLowStockSku();
        }
        backfillFinanceShowcase();

        long cost = (System.currentTimeMillis() - start) / 1000;
        log.info("[DemoData] 演示数据生成完成，耗时 {}s（档位={}）。客户={} 个人/{} 企业，SKU={}，订单≈{}，会话={}。",
                cost, mode, personalCustomerIds.size(), enterpriseCustomerIds.size(), skus.size(),
                mode.orderCount(), mode.chatSessions());
    }

    /**
     * 压 1 个 SKU 到安全库存以下，供「库存预警」页断言。
     * <p>不依赖 {@code ensureStockWarningShowcase()} 里「只挑 id &gt; 9300」的演示库约定：
     * tiny 档在空库上生成，SKU 主键是自增小数字，那个过滤条件会空转。</p>
     *
     * <p><b>为什么要留「兜底」</b>：一开始只挑「没被订单用过」的 SKU，靠的是「空库目录 SKU 比订单多」。
     * 但 tiny 档的订单计划用 {@code skuIndex = plan.size() % skus.size()} 铺满 115 个 SKU，
     * 而空库目录恰好也是 115 个 SKU —— 一个空闲 SKU 都不剩，挑不到就直接空转，
     * 库存预警页反而没有告警行。所以先优先挑空闲 SKU（不影响商品详情），挑不到就退而压最后一格。</p>
     */
    private void ensureLowStockSku() {
        if (!stockMapper.selectBelowSafety().isEmpty()) {
            return;
        }
        List<SkuStockPO> candidates = stockMapper.selectList(null).stream()
                .filter(r -> r.getSkuId() != null && r.getSafetyQty() != null && r.getSafetyQty() > 0)
                .filter(r -> r.getPhysicalQty() != null && r.getPhysicalQty() >= r.getSafetyQty())
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        SkuStockPO row = candidates.stream()
                .filter(r -> !usedSkuIds.contains(r.getSkuId()))
                .findFirst()
                .orElseGet(() -> candidates.get(candidates.size() - 1));
        int target = Math.max(0, row.getSafetyQty() - 2);
        if (stockMapper.updateLedger(row.getSkuId(), target, 0, 0, 0) > 0) {
            log.info("[DemoData] 库存预警展示：SKU {} 压到 {}（安全库存 {}）。",
                    row.getSkuId(), target, row.getSafetyQty());
        }
    }

    /**
     * 给 E2E 主账号 9002 补财务展示面：12 笔未结清应收 + 2 张未核销回款单。
     *
     * <p><b>为什么必须补</b>：{@code finance-invoice-pdf-paging.spec.ts} 固定用
     * {@code finance/payment-record?customerId=9002} 打开「核销与冲销」，勾上「仅看当前客户」后——
     * 回款明细里必须还有行，否则 {@code onLoaded} 拿不到 activeRecord，
     * 「加载该客户应收」按钮直接 disabled（用例点不动，2 分钟超时）；
     * 应收台账还要 &gt;10 笔未结清，才翻得到第 2 页。
     * 而 9002 是个人客户，生成器只给企业单开应收/回款，它名下一条都没有。</p>
     *
     * <p><b>补的口径</b>：应收挂在它自己的已完成/待发货订单上（{@code receivable.order_id}
     * 有唯一键，一单只能一行，故先查已占用的 order_id 再补）；回款单留
     * {@code written_off_cent = 0}，保证「最大可核销 &gt; 0」，超额核销用例才有意义。</p>
     *
     * <p><b>幂等</b>：9002 名下已有回款单就整体跳过 —— 不覆盖、不重复叠加。</p>
     */
    private void ensureTinyReceivables() {
        Long customerId = e2eCustomerId();
        if (customerId == null) {
            return;
        }
        boolean hasRecord = paymentRecordMapper.selectList(null).stream()
                .anyMatch(r -> customerId.equals(r.getCustomerId()));
        if (hasRecord) {
            return;
        }
        Set<Long> takenOrderIds = new HashSet<>();
        for (ReceivablePO r : receivableMapper.selectList(null)) {
            if (r.getOrderId() != null) {
                takenOrderIds.add(r.getOrderId());
            }
        }

        int created = 0;
        for (OrderPO o : orderMapper.selectList(null)) {
            if (created >= 12) {
                break;
            }
            if (!customerId.equals(o.getCustomerId()) || takenOrderIds.contains(o.getId())) {
                continue;
            }
            if (!"COMPLETED".equals(o.getStatus()) && !"WAIT_SHIP".equals(o.getStatus())) {
                continue;
            }
            ReceivablePO r = new ReceivablePO();
            r.setOrderId(o.getId());
            r.setCustomerId(customerId);
            r.setAmountCent(o.getPayableCent());
            r.setReceivedCent(0L);
            r.setDueDate(LocalDate.now().plusDays(30));
            r.setStatus("NORMAL");
            r.setCreatedAt(o.getCreatedAt());
            r.setUpdatedAt(o.getCreatedAt());
            receivableMapper.insert(r);
            created++;
        }

        for (int i = 0; i < 2; i++) {
            String recordNo = idGen.next("PR");
            LocalDateTime at = NOW.minusDays(i + 1L);
            PaymentRecordPO pr = new PaymentRecordPO();
            pr.setRecordNo(recordNo);
            pr.setCustomerId(customerId);
            pr.setAmountCent(200_000L);
            pr.setWrittenOffCent(0L);
            pr.setReceivedDate(at.toLocalDate());
            pr.setBankSerial("BKT" + System.nanoTime() + i);
            pr.setRemark("演示回款");
            pr.setStatus("PENDING");
            pr.setOperatorId(pick(staffIds));
            pr.setCreatedAt(at);
            pr.setUpdatedAt(at);
            paymentRecordMapper.insert(pr);
        }
        log.info("[DemoData] tiny 档为 9002 补 {} 笔未结清应收 + 2 张回款单，供财务「核销与冲销」翻页与超额校验。",
                created);
    }

    /**
     * 给 9002 补开票申请，让「发票管理」与「发票 PDF 预览」两页有真实数据。
     *
     * <p><b>为什么必须补</b>：清理脚本把 {@code invoice_request} 清空了（它对应用户提交的开票申请），
     * 而生成器只写 {@code orders.invoice_flag} 标记、从不建申请行。于是：
     * <ul>
     *   <li>{@code system-and-invoice.spec.ts} 的 {@code ensureIssuedInvoice()} 找不到「待开票」行就直接 return，
     *       随后断言「共 N 条 &gt; 0」直接红；</li>
     *   <li>{@code finance-invoice-pdf-paging.spec.ts} 的 PDF 用例取列表首行发票号，
     *       并要求下载文件名包含该发票号 —— 首行必须是<b>已开票</b>行，待开票行没有发票号。</li>
     * </ul>
     * 所以这里 3 张 APPLIED（供「现场开票」）+ 2 张 ISSUED（{@code created_at} 最新，
     * 保证列表首行有发票号），幂等：库里只要有一张发票就整体跳过。</p>
     */
    private void ensureTinyInvoices() {
        Long customerId = e2eCustomerId();
        if (customerId == null || !invoiceMapper.selectList(null).isEmpty()) {
            return;
        }
        List<Long> orderIds = orderMapper.selectList(null).stream()
                .filter(o -> customerId.equals(o.getCustomerId()))
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .map(OrderPO::getId)
                .toList();
        if (orderIds.isEmpty()) {
            return;
        }
        int made = 0;
        for (int i = 0; i < Math.min(3, orderIds.size()); i++) {
            insertInvoice(orderIds.get(i), customerId, "APPLIED", null, NOW.minusDays(3 - i));
            made++;
        }
        for (int i = 3; i < Math.min(5, orderIds.size()); i++) {
            insertInvoice(orderIds.get(i), customerId, "ISSUED", idGen.next("INV"), NOW.minusHours(5 - i));
            made++;
        }
        log.info("[DemoData] tiny 档为 9002 补 {} 张开票申请（3 待开票 + 2 已开票），供发票页与 PDF 预览用例。", made);
    }

    private void insertInvoice(Long orderId, Long customerId, String status, String invoiceNo, LocalDateTime at) {
        InvoiceRequestPO po = new InvoiceRequestPO();
        po.setOrderId(orderId);
        po.setCustomerId(customerId);
        po.setTitle("个人");
        po.setType("NORMAL");
        po.setEmail("invoice" + customerId + "@sent.test");
        po.setStatus(status);
        po.setInvoiceNo(invoiceNo);
        po.setInvoiceUrl(null);
        po.setIssuedAt("ISSUED".equals(status) ? at : null);
        po.setCreatedAt(at);
        po.setUpdatedAt(at);
        invoiceMapper.insert(po);
    }

    // ========================= 财务 / 售后展示数据补齐 =========================
    /**
     * 补齐「合同管理」与「退款审核」两页的展示数据（幂等，可在每次启动时安全重跑）。
     *
     * <p>两个历史缺口：
     * <ul>
     *   <li>{@code contract} 表从未有写入方 → 合同管理页永远是空表；</li>
     *   <li>{@code refund_order} 只由订单生成器写入，且一律落 {@code REFUNDED} 终态
     *       → 退款审核页 104 条全是「已退款」，审核按钮全部禁用，页面上没有任何可操作单据。</li>
     * </ul>
     * 这里只补「缺的部分」，不动已有行：合同表非空就跳过；已有非 REFUNDED 的退款单就跳过。</p>
     */
    private void backfillFinanceShowcase() {
        // 早退路径没有执行 loadStaff()，staffIds 为空会让 pick() 越界
        if (staffIds.isEmpty()) {
            loadStaff();
        }
        ensureContracts();
        ensureRefundStatusVariety();
        ensureStockWarningShowcase();
        ensureDashboardShowcase();
    }

    /**
     * 补齐「库存预警」页的展示数据（幂等，每次启动尝试一次）。
     *
     * <p>历史缺口：SKU 台账里 124 行都设了 safety_qty，但实物量一律远高于阈值，
     * 导致「仅看低于安全库存」永远 0 行、摘要恒为 0，页面看起来像坏了。
     * 只挑末尾 3 个非种子 SKU（避开 E2E 固定使用的 9101/9102）把实物量压到阈值以下，
     * 四口径占用本来就为 0，不影响下单与库存并发用例；已有的预警行一律保留，
     * 运营手工入库补回来的库存也不会在重启后被再次压低。</p>
     */
    private void ensureStockWarningShowcase() {
        if (!stockMapper.selectBelowSafety().isEmpty()) {
            return;
        }
        List<SkuStockPO> candidates = stockMapper.selectList(null).stream()
                .filter(r -> r.getSkuId() != null && r.getSkuId() > 9300)
                .filter(r -> r.getSafetyQty() != null && r.getSafetyQty() > 0)
                .filter(r -> r.getPhysicalQty() != null && r.getPhysicalQty() >= r.getSafetyQty())
                .sorted(Comparator.comparing(SkuStockPO::getSkuId).reversed())
                .limit(3)
                .toList();
        int[] gaps = {8, 5, 2};
        int made = 0;
        for (int i = 0; i < candidates.size(); i++) {
            SkuStockPO row = candidates.get(i);
            int target = Math.max(0, row.getSafetyQty() - gaps[i]);
            if (stockMapper.updateLedger(row.getSkuId(), target, 0, 0, 0) > 0) {
                made++;
            }
        }
        if (made > 0) {
            log.info("[DemoData] 补齐 {} 个低于安全库存的 SKU，供库存预警页展示。", made);
        }
    }

    /**
     * 补齐工作台看板的待办状态展示数据（幂等，每次启动尝试一次）。
     *
     * <p>历史缺口：{@code custom_order} 里没有 WAIT_REVIEW、{@code chat_session} 里没有 WAITING，
     * 于是「待审核定制」「待接入会话」两张卡恒为 0，审核台与客服排队队列也一条都没有。
     * 这里只做状态流转、不新增业务单据：把 3 条 WAIT_DRAFT 定制单推进到待审，
     * 把 2 条已结束会话重新放回排队。<b>只要库里已经有对应状态行就整体跳过</b>，
     * 运营真实产生的待办、以及客服已接待/已关闭的会话一律不会被重启覆盖。</p>
     *
     * <p>会话侧刻意排除 customer 9002：E2E 客服链路固定用该客户建会话并复用未结束会话，
     * 不能让演示排队数据干扰真实流程。</p>
     */
    private void ensureDashboardShowcase() {
        List<CustomOrderPO> customs = customOrderMapper.selectList(null);
        boolean hasPendingReview = customs.stream()
                .anyMatch(r -> "WAIT_REVIEW".equals(r.getStatus()));
        if (!hasPendingReview) {
            int made = 0;
            for (CustomOrderPO row : customs.stream()
                    .filter(r -> "WAIT_DRAFT".equals(r.getStatus()))
                    .limit(3)
                    .toList()) {
                row.setStatus("WAIT_REVIEW");
                row.setReviewDeadline(NOW.plusDays(2));
                row.setReviewerId(null);
                if (customOrderMapper.updateById(row) > 0) {
                    made++;
                }
            }
            if (made > 0) {
                log.info("[DemoData] 补齐 {} 张待审核定制单，供审核台与工作台待办展示。", made);
            }
        }

        List<ChatSessionPO> sessions = chatSessionMapper.selectList(null);
        boolean hasWaiting = sessions.stream()
                .anyMatch(r -> "WAITING".equals(r.getStatus()));
        if (!hasWaiting) {
            int made = 0;
            for (ChatSessionPO row : sessions.stream()
                    .filter(r -> "CLOSED".equals(r.getStatus()))
                    .filter(r -> r.getCustomerId() != null && r.getCustomerId() != 9002L)
                    .limit(2)
                    .toList()) {
                row.setStatus("WAITING");
                row.setStaffId(null);
                row.setEndedAt(null);
                row.setCloseReason(null);
                if (chatSessionMapper.updateById(row) > 0) {
                    made++;
                }
            }
            if (made > 0) {
                log.info("[DemoData] 补齐 {} 条排队会话，供客服工作台与工作台待办展示。", made);
            }
        }
    }

    /** 按企业客户补年度框架合同；到期日刻意分三档，让「到期状态」三个选项都有真实数据。 */
    private void ensureContracts() {
        if (contractMapper.selectCount(null) > 0) {
            return;
        }
        List<CustomerPO> enterprises = customerMapper.selectList(null).stream()
                .filter(c -> "ENTERPRISE".equals(c.getType()))
                .filter(c -> !Integer.valueOf(1).equals(c.getDeleted()))
                .toList();
        if (enterprises.isEmpty()) {
            return;
        }

        // 合同金额取该客户历史实付合计，让「合同金额」与订单数据自洽
        Map<Long, Long> paidByCustomer = new HashMap<>();
        for (OrderPO order : orderMapper.selectList(null)) {
            if (order.getCustomerId() == null) {
                continue;
            }
            long paid = order.getPaidCent() == null ? 0L : order.getPaidCent();
            paidByCustomer.merge(order.getCustomerId(), paid, Long::sum);
        }

        LocalDate today = LocalDate.now();
        String dayStamp = today.toString().replace("-", "");
        int seq = 0;
        for (CustomerPO customer : enterprises) {
            seq++;
            int bucket = seq % 7;
            LocalDate expireDate;
            if (bucket == 0 || bucket == 3) {
                // 约 2/7 已过期，留给「已到期」筛选
                expireDate = today.minusDays(6L + (seq % 120));
            } else if (bucket == 5) {
                // 约 1/7 落在 30 天内到期，留给「30 天内到期」筛选
                expireDate = today.plusDays(3L + (seq % 25));
            } else {
                expireDate = today.plusDays(45L + (seq % 300));
            }

            long amountCent = paidByCustomer.getOrDefault(customer.getId(), 0L);
            if (amountCent <= 0L) {
                amountCent = 3_000_000L + (long) (seq % 20) * 500_000L;
            }

            ContractPO contract = new ContractPO();
            contract.setCustomerId(customer.getId());
            contract.setContractNo("HT" + dayStamp + String.format("%04d", seq));
            contract.setSignDate(expireDate.minusDays(365));
            contract.setExpireDate(expireDate);
            contract.setDiscountRate(92 + (seq % 8));
            contract.setAmountCent(amountCent);
            contract.setStatus(expireDate.isBefore(today) ? "EXPIRED" : "ACTIVE");
            contractMapper.insert(contract);
        }
        log.info("[DemoData] 补齐企业年度合同 {} 份（含已过期 / 30 天内到期 / 正常三档）。", enterprises.size());
    }

    /**
     * 给退款审核页补一批「可操作」的退款单：PENDING / REFUNDING / CLOSED / FAILED 各占一部分。
     * <p>只挑「从未有过退款单、且实付>0、无售后状态」的订单，金额取实付的 30%~69%，
     * 既不触发 {@code sumInFlightAmount} 超额校验，也不会改动任何已退款订单的账。</p>
     */
    private void ensureRefundStatusVariety() {
        List<RefundOrderPO> existing = refundOrderMapper.selectList(null);
        boolean hasPending = existing.stream().anyMatch(r -> !"REFUNDED".equals(r.getStatus()));
        if (hasPending) {
            return;
        }
        java.util.Set<Long> refundedOrderIds = new java.util.HashSet<>();
        for (RefundOrderPO refund : existing) {
            if (refund.getOrderId() != null) {
                refundedOrderIds.add(refund.getOrderId());
            }
        }

        List<OrderPO> candidates = orderMapper.selectList(null).stream()
                .filter(o -> o.getPaidCent() != null && o.getPaidCent() > 0L)
                .filter(o -> o.getAfterSaleStatus() == null)
                .filter(o -> !refundedOrderIds.contains(o.getId()))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(24)
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        int index = 0;
        for (OrderPO order : candidates) {
            String status;
            String reasonCode;
            if (index < 10) {
                status = "PENDING";
                reasonCode = "QUALITY";
            } else if (index < 16) {
                status = "REFUNDING";
                reasonCode = "SIZE";
            } else if (index < 20) {
                status = "CLOSED";
                reasonCode = "LOGISTICS";
            } else {
                status = "FAILED";
                reasonCode = "PAY_GATEWAY";
            }

            long paidCent = order.getPaidCent();
            long amountCent = Math.max(paidCent * (30 + (index * 7) % 40) / 100, 100L);
            // 列表按 created_at DESC 排序：这 24 笔必须比历史 REFUNDED 更新，
            // 否则首屏仍会被「已退款」占满，看不到待审核/退款中的单据。按 90 分钟一档铺开约 36 小时。
            LocalDateTime createdAt = NOW.minusMinutes(index * 90L);

            RefundOrderPO refund = new RefundOrderPO();
            refund.setRefundNo(idGen.next("RF"));
            refund.setOrderId(order.getId());
            refund.setType("REFUND");
            refund.setAmountCent(amountCent);
            refund.setPointRefund(0L);
            refund.setStatus(status);
            refund.setReasonCode(reasonCode);
            refund.setRemark("演示售后申请（" + status + "）");
            refund.setApplicantType("CUSTOMER");
            if (!"PENDING".equals(status) && !"REFUNDING".equals(status)) {
                refund.setAuditorId(pick(staffIds));
                refund.setAuditRemark("CLOSED".equals(status) ? "不符合退款条件" : "支付渠道退款失败");
            }
            refund.setCreatedAt(createdAt);
            refund.setUpdatedAt(createdAt);
            refundOrderMapper.insert(refund);
            index++;
        }
        log.info("[DemoData] 补齐退款单 {} 笔（PENDING/REFUNDING/CLOSED/FAILED），退款审核页不再清一色「已退款」。",
                index);
    }

    // ========================= 基础数据 =========================

    private void loadStaff() {
        List<SysStaffPO> staff = staffMapper.selectList(null);
        staffIds = new ArrayList<>();
        for (SysStaffPO s : staff) {
            staffIds.add(s.getId());
        }
        if (staffIds.isEmpty()) {
            staffIds.add(9001L);
        }
    }

    /** 商品库过薄时（SKU<50）灌入一套基础目录，否则复用现有商品。 */
    private void ensureCatalog() {
        if (skuMapper.selectCount(null) >= 50) {
            return;
        }
        log.info("[DemoData] 商品库偏薄，生成基础目录（分类 + 商品 + SKU + 库存）...");

        if (catalogMapper.selectCategories().isEmpty()) {
            for (int i = 0; i < CATEGORIES.length; i++) {
                catalogMapper.insertCategory(CATEGORIES[i], i + 1);
            }
        }
        List<CatalogMapper.CategoryRow> cats = catalogMapper.selectCategories();

        for (int i = 0; i < PRODUCT_NAMES.length * 2; i++) {
            String name = PRODUCT_NAMES[i % PRODUCT_NAMES.length] + " " + (i < PRODUCT_NAMES.length ? "标准款" : "专业款");
            long catId = cats.get(i % cats.size()).getId();
            ProductPO p = new ProductPO();
            p.setProductNo(idGen.next("P"));
            p.setTitle(name);
            p.setCategoryId(catId);
            p.setBrand(BRANDS[random.nextInt(BRANDS.length)]);
            p.setMainImage("https://demo.sent.test/img/" + p.getProductNo() + ".jpg");
            p.setStatus("ON_SHELF");
            p.setPublishedAt(randomPastTime());
            p.setSalesCount((long) randInt(0, 500));
            p.setDeleted(0);
            LocalDateTime t = randomPastTime();
            p.setCreatedAt(t);
            p.setUpdatedAt(t);
            productMapper.insert(p);

            int skuN = 1 + random.nextInt(3);
            for (int k = 0; k < skuN; k++) {
                SkuPO sku = new SkuPO();
                sku.setSpuId(p.getId());
                sku.setSkuNo(idGen.next("S"));
                sku.setSpecJson("{\"color\":\"" + pick(new String[]{"黑", "白", "蓝", "红"}) + "\",\"size\":\"" + pick(new String[]{"S", "M", "L", "XL"}) + "\"}");
                long price = (long) (1990 + random.nextInt(980) * 100); // ¥19.9 ~ ¥999.9
                sku.setRetailPriceCent(price);
                sku.setFloorPriceCent((long) (price * 0.8));
                sku.setCostPriceCent((long) (price * 0.5));
                sku.setStatus("ON");
                LocalDateTime st = randomPastTime();
                sku.setCreatedAt(st);
                sku.setUpdatedAt(st);
                skuMapper.insert(sku);

                SkuStockPO stock = new SkuStockPO();
                stock.setSkuId(sku.getId());
                stock.setPhysicalQty(randInt(50, 500));
                stock.setLockedQty(0);
                stock.setPendingQty(0);
                stock.setFrozenQty(0);
                stock.setSafetyQty(10);
                stock.setVersion(0L);
                stockMapper.insert(stock);
            }
        }
    }

    /** 客户不足时按档位补齐：FULL 是 150 个人 + 50 企业，TINY 只需 10 个人 + 3 企业（企业数 3 才能凑出「已过期」合同）。 */
    private void ensureCustomers(DemoScale mode) {
        if (mode.isTiny()) {
            ensureTinyCustomers(mode);
            return;
        }
        if (customerMapper.selectCount(null) >= 50) {
            // 复用已有客户：必须按类型回填 ID 列表，否则 generateOrders 从空列表取客户会抛异常。
            for (CustomerPO c : customerMapper.selectList(null)) {
                boolean enterprise = "ENTERPRISE".equals(c.getType());
                if (enterprise) {
                    enterpriseCustomerIds.add(c.getId());
                } else {
                    personalCustomerIds.add(c.getId());
                }
                allCustomerIds.add(c.getId());
            }
            log.info("[DemoData] 复用已有客户：个人={} 企业={}", personalCustomerIds.size(), enterpriseCustomerIds.size());
            return;
        }
        log.info("[DemoData] 生成 150 个人客户 + 50 企业客户...");
        int counter = 0;
        for (int i = 0; i < 200; i++) {
            counter++;
            boolean enterprise = i >= 150;
            CustomerPO c = new CustomerPO();
            c.setCustomerNo("CUS-" + (20000 + counter));
            // 大陆手机号：真实号段（138/159/186/137/188/199）+ 8 位序号，
            // 既满足 ^1[3-9]\d{9}$，又由 counter 保证 200 条内不重复。
            c.setPhone(pick(new String[]{"138", "159", "186", "137", "188", "199"})
                    + String.format("%08d", counter));
            c.setPasswordHash("123456");
            c.setNickname((enterprise ? "企业客户" : "会员") + counter);
            c.setEmail("demo" + counter + "@sent.test");
            c.setType(enterprise ? "ENTERPRISE" : "PERSON");
            c.setLevelCode(pick(new String[]{"LV1", "LV2", "LV3", "NORMAL"}));
            c.setGrowthValue((long) randInt(0, 5000));
            c.setPointsBalance((long) randInt(0, 3000));
            c.setOwnerStaffId(pick(staffIds));
            c.setStatus("ACTIVE");
            c.setTokenVersion(0L);
            c.setDeleted(0);
            LocalDateTime t = randomPastTime();
            c.setCreatedAt(t);
            c.setUpdatedAt(t);
            customerMapper.insert(c);

            if (enterprise) {
                enterpriseProfileMapper.insertProfile(
                        c.getId(),
                        "演示企业" + counter + "有限公司",
                        "91440300" + String.format("%010d", counter),
                        "演示企业" + counter + "有限公司",
                        "91440300" + String.format("%010d", counter),
                        (long) (2_000_000 + random.nextInt(48) * 1_000_000),
                        0L,
                        pick(new int[]{30, 60, 90}),
                        "NORMAL");
                enterpriseCustomerIds.add(c.getId());
            } else {
                personalCustomerIds.add(c.getId());
            }
            allCustomerIds.add(c.getId());
        }
    }

    /**
     * tiny 档客户：按类型补齐到「10 个人 + 3 企业」。
     *
     * <p><b>为什么要 3 个企业</b>：{@code ensureContracts()} 按 {@code seq % 7} 分三档到期状态，
     * 只有 {@code seq % 7 == 0 或 3} 才是已过期；企业客户少于 3 个时永远凑不出「已到期」合同，
     * 财务页的该筛选会空。</p>
     *
     * <p>已有客户（dev 库保留的 9002 + 91001~91006）直接复用，只补差额，不删不覆盖。</p>
     */
    private void ensureTinyCustomers(DemoScale mode) {
        int seq = 0;
        for (CustomerPO c : customerMapper.selectList(null)) {
            boolean enterprise = "ENTERPRISE".equals(c.getType());
            if (enterprise) {
                enterpriseCustomerIds.add(c.getId());
            } else {
                personalCustomerIds.add(c.getId());
            }
            allCustomerIds.add(c.getId());
            seq++;
        }
        int created = 0;
        while (personalCustomerIds.size() < mode.personalCustomers()) {
            seq++;
            CustomerPO c = insertTinyCustomer(seq, false);
            personalCustomerIds.add(c.getId());
            allCustomerIds.add(c.getId());
            created++;
        }
        while (enterpriseCustomerIds.size() < mode.enterpriseCustomers()) {
            seq++;
            CustomerPO c = insertTinyCustomer(seq, true);
            enterpriseProfileMapper.insertProfile(
                    c.getId(),
                    "演示企业" + seq + "有限公司",
                    "91440300" + String.format("%010d", seq),
                    "演示企业" + seq + "有限公司",
                    "91440300" + String.format("%010d", seq),
                    3_000_000L,
                    0L,
                    30,
                    "ACTIVE");
            enterpriseCustomerIds.add(c.getId());
            allCustomerIds.add(c.getId());
            created++;
        }
        log.info("[DemoData] tiny 档客户：复用 {} 个，补建 {} 个 → 个人={} 企业={}",
                seq - created, created, personalCustomerIds.size(), enterpriseCustomerIds.size());
    }

    /** tiny 档客户行：手机号 / 客户号按序号生成，保证 ^1[3-9]\\d{9}$ 与唯一索引都不冲突。 */
    private CustomerPO insertTinyCustomer(int seq, boolean enterprise) {
        CustomerPO c = new CustomerPO();
        c.setCustomerNo("CUS-T" + String.format("%05d", seq));
        c.setPhone("199" + String.format("%08d", seq));
        c.setPasswordHash("123456");
        c.setNickname((enterprise ? "演示企业" : "会员") + seq);
        c.setEmail("tiny" + seq + "@sent.test");
        c.setType(enterprise ? "ENTERPRISE" : "PERSON");
        c.setLevelCode(pick(new String[]{"LV1", "LV2", "LV3"}));
        c.setGrowthValue((long) randInt(0, 3000));
        c.setPointsBalance((long) randInt(500, 2000));
        c.setOwnerStaffId(pick(staffIds));
        c.setStatus("ACTIVE");
        c.setTokenVersion(0L);
        c.setDeleted(0);
        LocalDateTime t = randomPastTime();
        c.setCreatedAt(t);
        c.setUpdatedAt(t);
        customerMapper.insert(c);
        return c;
    }

    private void loadSellable() {
        List<ProductPO> products = productMapper.selectList(null);
        for (ProductPO p : products) {
            productTitle.put(p.getId(), p.getTitle());
        }
        skus = skuMapper.selectList(null);
    }

    // ========================= 订单 =========================

    /**
     * 生成订单。{@code FULL} 按 {@code i % 100} 随机分配状态；{@code TINY} 走固定计划
     * （{@link #tinyOrderPlan()}），以便精确控制复购结构、唯一峰值日与企业应收。
     */
    private void generateOrders(DemoScale mode) {
        List<TinyOrder> plan = mode.isTiny() ? tinyOrderPlan() : List.of();
        int total = plan.isEmpty() ? mode.orderCount() : plan.size();
        log.info("[DemoData] 生成 {} 条订单（档位 {}，分布：待付款 / 待发货 / 已完成 / 售后退款）...", total, mode);
        int customizedTotal = 0;
        int receivableTotal = 0;

        for (int i = 0; i < total; i++) {
            TinyOrder spec = plan.isEmpty() ? null : plan.get(i);
            int bucket = i % 100; // 0-9 / 10-24 / 25-94 / 95-99 → 10/15/70/5
            String mainStatus;
            if (spec != null) {
                mainStatus = spec.status();
            } else if (bucket < 10) {
                mainStatus = "WAIT_PAY";
            } else if (bucket < 25) {
                mainStatus = "WAIT_SHIP";
            } else if (bucket < 95) {
                mainStatus = "COMPLETED";
            } else {
                mainStatus = "AFTERSALE";
            }

            boolean enterpriseCustomer = spec != null
                    ? spec.enterprise()
                    : (!enterpriseCustomerIds.isEmpty() && random.nextDouble() < 0.25);
            Long customerId = spec != null
                    ? tinyCustomerId(spec)
                    : (enterpriseCustomer ? pick(enterpriseCustomerIds) : pick(allCustomerIds));
            String customerType = enterpriseCustomer ? "ENTERPRISE" : "PERSON";

            boolean paid = !"WAIT_PAY".equals(mainStatus);
            boolean shipped = "WAIT_SHIP".equals(mainStatus) || "COMPLETED".equals(mainStatus) || "AFTERSALE".equals(mainStatus);

            // 时间线
            LocalDateTime createdAt;
            if (spec != null) {
                // tiny 档按计划落在指定「几天前」的 10 点档，保证趋势图有唯一峰值日、复购首单早于 30 天窗口
                createdAt = NOW.minusDays(spec.daysAgo())
                        .withHour(10 + (i % 8)).withMinute((i * 7) % 60).withSecond(0).withNano(0);
            } else if ("WAIT_PAY".equals(mainStatus)) {
                createdAt = NOW.minusMinutes(randInt(0, 120)); // 2 小时内
            } else {
                createdAt = randomPastTime();
            }
            LocalDateTime paidAt = null;
            LocalDateTime completedAt = null;
            if (paid) {
                paidAt = spec != null ? createdAt.plusMinutes(9) : clamp(createdAt.plusMinutes(5 + random.nextInt(600)));
                if ("COMPLETED".equals(mainStatus) || "AFTERSALE".equals(mainStatus)) {
                    completedAt = spec != null
                            ? clamp(paidAt.plusDays(2))
                            : clamp(paidAt.plusDays(1 + random.nextInt(20)));
                }
            }

            // 选品
            int lineN = spec != null ? 1 : 1 + random.nextInt(3);
            List<SkuPO> chosen = spec != null
                    ? List.of(skus.get(spec.skuIndex() % skus.size()))
                    : pickDistinct(skus, lineN);
            chosen.forEach(s -> usedSkuIds.add(s.getId()));
            long goodsAmount = 0;
            long customFeeTotal = 0;

            String orderNo = idGen.nextOrderNo();
            OrderPO order = new OrderPO();
            order.setOrderNo(orderNo);
            order.setCustomerId(customerId);
            order.setCustomerType(customerType);
            order.setOwnerStaffId(pick(staffIds));
            order.setStatus(mainStatus);
            order.setPayStatus(paid ? ("AFTERSALE".equals(mainStatus) ? "FULL_REFUNDED" : "PAID") : "UNPAID");
            order.setAfterSaleStatus("AFTERSALE".equals(mainStatus) ? "REFUNDED" : null);
            order.setIdemKey("DEMO-" + orderNo);
            order.setInvoiceFlag(random.nextDouble() < 0.3 ? 1 : 0);
            order.setRemark("");
            order.setAddressSnapshotJson(randomAddress());
            // WAIT_PAY 订单的过期时间设为未来，避免被「超时未支付」定时任务在本次演示期间立即取消，
            // 从而保留约 10% 的「待付款」占比；其余状态不涉及超时取消。
            order.setExpireAt("WAIT_PAY".equals(mainStatus) ? NOW.plusDays(1) : null);
            order.setPaidAt(paidAt);
            order.setCompletedAt(completedAt);
            order.setVersion(0L);

            // 是否定制：tiny 档由计划指定（9002 的 20 单全带定制行，撑起「我的定制」分页）；full 档随机
            boolean customized = spec != null
                    ? spec.custom()
                    : (shipped && random.nextDouble() < ("WAIT_SHIP".equals(mainStatus) ? 0.3 : 0.2));

            order.setCreatedAt(createdAt);
            order.setUpdatedAt(completedAt != null ? completedAt : (paidAt != null ? paidAt : createdAt));
            orderMapper.insert(order);
            long orderId = order.getId();

            // 订单行
            int customLineIndex = customized ? random.nextInt(chosen.size()) : -1;
            List<OrderItemPO> items = new ArrayList<>();
            for (int li = 0; li < chosen.size(); li++) {
                SkuPO sku = chosen.get(li);
                int qty = 1 + random.nextInt(5);
                long unit = sku.getRetailPriceCent();
                long lineAmount = unit * qty;
                goodsAmount += lineAmount;

                boolean isCustomLine = li == customLineIndex;
                long lineCustomFee = 0;
                if (isCustomLine) {
                    // 定制费口径是「10 元/件」（V35 起的硬约束，price-repricing 用例会按
                    // fee_cent = 1000 × qty 校验全库）。这里不能按金额百分比随便算，
                    // 否则演示数据自己就把这条不变量破坏了。
                    lineCustomFee = 1_000L * qty;
                    customFeeTotal += lineCustomFee;
                }

                OrderItemPO item = new OrderItemPO();
                item.setOrderId(orderId);
                item.setSkuId(sku.getId());
                item.setSpuTitleSnap(productTitle.getOrDefault(sku.getSpuId(), "商品"));
                item.setSpecSnap(sku.getSpecJson());
                item.setQty(qty);
                item.setUnitPriceCent(unit);
                item.setPriceSource("RETAIL");
                item.setPriceRuleId(null);
                item.setAmountCent(lineAmount);
                item.setDiscountAllocCent(0L);
                item.setShippedQty(shipped ? qty : 0);
                item.setCustomFlag(isCustomLine ? 1 : 0);
                item.setCustomFeeCent(lineCustomFee);
                orderMapper.insertItem(item);
                items.add(item);
            }

            long discount = (long) (goodsAmount * (random.nextDouble() < 0.7 ? 0 : random.nextDouble() * 0.1));
            long freight = random.nextDouble() < 0.7 ? 0 : (1000 + random.nextInt(3) * 500);
            long payable = goodsAmount + customFeeTotal - discount + freight;
            order.setGoodsAmountCent(goodsAmount);
            order.setCustomFeeCent(customFeeTotal);
            order.setDiscountCent(discount);
            order.setFreightCent(freight);
            order.setPointDeductCent(0L);
            order.setPayableCent(payable);
            order.setPaidCent(paid ? payable : 0L);
            order.setReceivedCent("COMPLETED".equals(mainStatus) || "AFTERSALE".equals(mainStatus) ? payable : 0L);
            orderMapper.updateById(order);

            // 支付流水
            if (paid) {
                PaymentTxnPO txn = new PaymentTxnPO();
                String txnNo = idGen.next("TX");
                txn.setTxnNo(txnNo);
                txn.setOrderNo(orderNo);
                txn.setAmountCent(payable);
                txn.setChannel(pick(new String[]{"WECHAT", "ALIPAY", "BANK"}));
                txn.setChannelTradeNo("CH" + System.nanoTime());
                txn.setIdemKey("DEMO-" + txnNo);
                txn.setStatus("PAID");
                txn.setRequestAt(paidAt);
                txn.setCallbackAt(paidAt);
                txn.setRawPayload(null);
                txn.setCreatedAt(paidAt);
                txn.setUpdatedAt(paidAt);
                paymentTxnMapper.insert(txn);
            }

            // 发货
            if (shipped) {
                String shipmentNo = idGen.next("SH");
                String carrier = pick(CARRIERS);
                String tracking = carrier + System.nanoTime();
                shipmentMapper.insertShipment(shipmentNo, orderId, carrier, tracking);
                Long shipmentId = shipmentMapper.selectIdByShipmentNo(shipmentNo);
                for (OrderItemPO it : items) {
                    shipmentMapper.insertItemForSku(shipmentId, orderId, it.getSkuId(), it.getQty());
                }
            }

            // 定制单 + 图稿
            if (customized) {
                List<OrderItemPO> persisted = orderMapper.selectItems(orderId);
                OrderItemPO customItem = null;
                for (OrderItemPO it : persisted) {
                    if (it.getCustomFlag() != null && it.getCustomFlag() == 1) {
                        customItem = it;
                        break;
                    }
                }
                if (customItem != null) {
                    CustomOrderPO co = new CustomOrderPO();
                    co.setCustomNo(idGen.next("CU"));
                    co.setOrderItemId(customItem.getId());
                    co.setCustomerId(customerId);
                    co.setServiceType("ATTACHED");
                    co.setPrintSide(pick(new String[]{"SINGLE", "DOUBLE"}));
                    co.setQty(customItem.getQty());
                    co.setFeeCent(customItem.getCustomFeeCent());
                    String customStatus = "WAIT_SHIP".equals(mainStatus) ? "IN_PRODUCTION"
                            : ("COMPLETED".equals(mainStatus) || "AFTERSALE".equals(mainStatus) ? "FINISHED" : "APPROVED");
                    co.setStatus(customStatus);
                    co.setCurrentVersion(1);
                    co.setLockedVersion(1);
                    co.setReviewDeadline(clamp(createdAt.plusDays(1 + random.nextInt(3))));
                    co.setReviewerId(pick(staffIds));
                    if ("FINISHED".equals(customStatus)) {
                        co.setProductionStartAt(clamp(paidAt != null ? paidAt.plusHours(2) : createdAt.plusHours(2)));
                        co.setFinishedAt(completedAt != null ? completedAt : clamp(NOW.minusDays(1)));
                    }
                    co.setExpectShipDate((completedAt != null ? completedAt : NOW).toLocalDate().plusDays(2));
                    co.setRemark("演示定制单");
                    co.setCreatedAt(createdAt);
                    co.setUpdatedAt(co.getFinishedAt() != null ? co.getFinishedAt() : createdAt);
                    customOrderMapper.insert(co);

                    CustomDraftPO draft = new CustomDraftPO();
                    draft.setCustomOrderId(co.getId());
                    draft.setVersion(1);
                    // 同一个内容只存一份（内容寻址去重），所有演示图稿共用一张 1x1 png 也够用
                    draft.setFileId(demoDraftFileId());
                    draft.setFileName("design_v1.png");
                    draft.setFileSize((long) randInt(50_000, 500_000));
                    draft.setUploadedAt(co.getReviewDeadline());
                    draft.setCreatedAt(co.getReviewDeadline());
                    draft.setUpdatedAt(co.getReviewDeadline());
                    customDraftMapper.insert(draft);

                    order.setCustomStatus(customStatus);
                    orderMapper.updateById(order);
                    customizedTotal++;
                }
            }

            // 企业客户应收 + 回款核销
            boolean enterpriseReceivable = enterpriseCustomer && ("COMPLETED".equals(mainStatus) || "AFTERSALE".equals(mainStatus) || "WAIT_SHIP".equals(mainStatus));
            if (enterpriseReceivable) {
                boolean closed = "COMPLETED".equals(mainStatus) || "AFTERSALE".equals(mainStatus);
                String recordNo = idGen.next("PR");
                PaymentRecordPO pr = new PaymentRecordPO();
                pr.setRecordNo(recordNo);
                pr.setCustomerId(customerId);
                pr.setAmountCent(payable);
                pr.setWrittenOffCent(closed ? payable : 0L);
                pr.setReceivedDate((paidAt != null ? paidAt : createdAt).toLocalDate());
                pr.setBankSerial("BK" + System.nanoTime());
                pr.setRemark("演示回款");
                pr.setStatus(closed ? "CONFIRMED" : "PENDING");
                pr.setOperatorId(pick(staffIds));
                pr.setCreatedAt(paidAt != null ? paidAt : createdAt);
                pr.setUpdatedAt(paidAt != null ? paidAt : createdAt);
                paymentRecordMapper.insert(pr);

                ReceivablePO r = new ReceivablePO();
                r.setOrderId(orderId);
                r.setCustomerId(customerId);
                r.setAmountCent(payable);
                r.setReceivedCent(closed ? payable : 0L);
                r.setDueDate((completedAt != null ? completedAt : createdAt).toLocalDate().plusDays(30));
                r.setStatus(closed ? "CLOSED" : "NORMAL");
                r.setCreatedAt(createdAt);
                r.setUpdatedAt(createdAt);
                receivableMapper.insert(r);

                if (closed) {
                    WriteOffPO wo = new WriteOffPO();
                    wo.setWriteOffNo(idGen.next("WO"));
                    wo.setPaymentRecordNo(recordNo);
                    wo.setReceivableId(r.getId());
                    wo.setCustomerId(customerId);
                    wo.setAmountCent(payable);
                    wo.setOperatorId(pick(staffIds));
                    wo.setCreatedAt(completedAt != null ? completedAt : NOW);
                    wo.setUpdatedAt(completedAt != null ? completedAt : NOW);
                    writeOffMapper.insert(wo);
                }
                receivableTotal++;
            }

            // 售后退款单
            if ("AFTERSALE".equals(mainStatus)) {
                RefundOrderPO rf = new RefundOrderPO();
                rf.setRefundNo(idGen.next("RF"));
                rf.setOrderId(orderId);
                rf.setOrderItemId(items.get(0).getId());
                rf.setType("REFUND");
                rf.setAmountCent(payable);
                rf.setPointRefund(0L);
                rf.setStatus("REFUNDED");
                rf.setReasonCode("QUALITY");
                rf.setRemark("演示退款");
                rf.setApplicantType("CUSTOMER");
                rf.setAuditorId(pick(staffIds));
                rf.setRefundedAt(completedAt != null ? completedAt : NOW);
                rf.setCreatedAt(completedAt != null ? completedAt : NOW);
                rf.setUpdatedAt(completedAt != null ? completedAt : NOW);
                refundOrderMapper.insert(rf);
            }

            if ((i + 1) % 250 == 0) {
                log.info("[DemoData] 已生成订单 {}/{}", i + 1, total);
            }
        }
        log.info("[DemoData] 订单生成完毕：其中定制单≈{}，企业应收≈{}。", customizedTotal, receivableTotal);
    }

    /** tiny 档的单条订单计划：状态 + 客户槽位（-1 = E2E 账号 9002）+ 「几天前」+ SKU 序号 + 是否带定制。 */
    private record TinyOrder(String status, boolean enterprise, int customerSlot,
                             int daysAgo, int skuIndex, boolean custom) {
    }

    /**
     * tiny 档订单计划（115 单），刻意构造出 E2E 断言依赖的四种形态：
     *
     * <ol>
     *   <li><b>分页深度</b>（{@code list-pagination.spec.ts} 切到每页 15 再点下一页）：
     *       E2E 账号 9002 独占 21 单（20 单带定制行 + 1 单在途不带定制）→
     *       「我的订单」21 条、「我的定制」20 条都能翻页；在途那单还让「待发货」Tab 非空，
     *       供 {@code my-batch-delete.spec.ts} 验证「在途单勾不动、接口也删不掉」。
     *       再给它 20 张退款单（{@link #ensureTinyRefunds()}）→ 「我的售后」也能翻页。</li>
     *   <li><b>复购三档</b>（{@code report-compact.spec.ts} 要 {@code h[2] > h[0]*3}）：
     *       槽位 1~6 各 6 单 → 「6 单以上」6 人；槽位 7 共 4 单 → 「3–5 单」1 人；槽位 8 共 2 单 → 「2 单」1 人。</li>
     *   <li><b>唯一峰值日</b>：2 天前 26 单（25 单企业待发货 + 9002 的在途单），远高于其它日，趋势图 peak 点唯一。</li>
     *   <li><b>企业台账</b>：25 个企业客户各 1 笔待发货（应收未结清 → 台账翻页）+ 1 笔已完成（合同金额自洽），
     *       合同由 {@code ensureContracts()} 按客户数生成 → 25 份，其中 {@code seq%7∈{0,3}} 已过期。</li>
     * </ol>
     *
     * <p>小数据量下最容易被忽略的是「新老客」判定：复购客要求首单早于 30 天窗口，
     * 所以复购客（槽位 1~8）都带 3 单窗口外历史；9002 与企业客户的首单都落在窗口内，
     * 归为「新客」，不干扰三档比例。</p>
     */
    private List<TinyOrder> tinyOrderPlan() {
        List<TinyOrder> plan = new ArrayList<>();
        // ① E2E 账号 9002：20 单带定制行（撑起「我的订单 / 我的定制」分页）+ 1 单在途（不带定制）。
        //    刻意落在 21~39 天前：都在「最近 30 天」窗口外，不参与趋势峰值与复购统计，避免把峰值日搅成并列。
        for (int d = 21; d <= 39; d++) {
            plan.add(new TinyOrder("COMPLETED", false, -1, d, plan.size(), true));
        }
        // 再补 1 笔售后单，个人中心的「退款/售后」角标不为 0
        plan.add(new TinyOrder("AFTERSALE", false, -1, 40, plan.size(), true));
        // 9002 还要有 1 笔在途单：「我的订单 · 待发货」Tab 必须非空，
        // 批量删除用例要在这里验证「在途单勾不动、接口也删不掉」。落在第 2 天，正是峰值日。
        plan.add(new TinyOrder("WAIT_SHIP", false, -1, 2, plan.size(), false));

        // ② 复购客：{窗口内 3 单, 窗口外 3 单}
        int[][][] repeat = {
                {{4, 11, 25}, {100, 70, 45}},
                {{4, 4, 18}, {110, 80, 50}},
                {{4, 11, 18}, {95, 65, 40}},
                {{4, 11, 18}, {105, 75, 48}},
                {{4, 11, 25}, {90, 62, 42}},
                {{4, 11, 25}, {115, 85, 55}},
        };
        for (int c = 0; c < repeat.length; c++) {
            for (int[] group : repeat[c]) {
                for (int daysAgo : group) {
                    plan.add(new TinyOrder("COMPLETED", false, 1 + c, daysAgo, plan.size(), false));
                }
            }
        }
        // 4 单档 / 2 单档
        for (int daysAgo : new int[]{18, 98, 68, 44}) {
            plan.add(new TinyOrder("COMPLETED", false, 7, daysAgo, plan.size(), false));
        }
        for (int daysAgo : new int[]{11, 88}) {
            plan.add(new TinyOrder("COMPLETED", false, 8, daysAgo, plan.size(), false));
        }

        // ③ 企业客户：25 家 × (1 待发货 + 1 已完成) → 应收台账 25 条未结清 + 25 条已结清、合同 25 份。
        //    待发货全压在同一天（第 2 天）造出唯一峰值；已完成的日期故意摊开，避免与峰值日并列。
        for (int e = 0; e < 25; e++) {
            plan.add(new TinyOrder("WAIT_SHIP", true, e, 2, plan.size(), false));
            plan.add(new TinyOrder("COMPLETED", true, e, 5 + (e % 5), plan.size(), false));
        }

        // ④ 待付款（pay_status=UNPAID，不参与任何成交口径统计）
        plan.add(new TinyOrder("WAIT_PAY", false, 9, 1, plan.size(), false));
        plan.add(new TinyOrder("WAIT_PAY", true, 2, 1, plan.size(), false));
        return plan;
    }

    /** tiny 档按「个人/企业 + 槽位」取客户；槽位 -1 表示 E2E 验收账号 9002，其余对池大小取模。 */
    private Long tinyCustomerId(TinyOrder spec) {
        if (spec.customerSlot() < 0) {
            return e2eCustomerId();
        }
        List<Long> pool = spec.enterprise() ? enterpriseCustomerIds : personalCustomerIds;
        return pool.get(spec.customerSlot() % pool.size());
    }

    /**
     * E2E 验收账号 9002；库被清空过（集成测试）时退化为第一个个人客户。
     * <p>用户端三个分页用例都固定用 9002 登录，所以这批「深数据」必须精确落在它身上。</p>
     */
    private Long e2eCustomerId() {
        if (e2eCustomerId == null) {
            e2eCustomerId = customerMapper.selectById(9002L) != null
                    ? 9002L
                    : personalCustomerIds.get(0);
        }
        return e2eCustomerId;
    }

    /**
     * 给 9002 的订单补 20 张退款单（PENDING/REFUNDING/CLOSED/FAILED 混合），
     * 让「我的售后」能翻页、退款审核页也有可操作单据。
     * <p>不依赖 {@code ensureRefundStatusVariety()}：它按订单 id 倒序挑 24 笔，
     * 小数据量下会把退款散到多个客户身上，用户端列表就翻不动了。</p>
     */
    private void ensureTinyRefunds() {
        Long customerId = e2eCustomerId();
        List<OrderPO> orders = orderMapper.selectList(null).stream()
                .filter(o -> customerId.equals(o.getCustomerId()))
                .filter(o -> o.getPaidCent() != null && o.getPaidCent() > 0L)
                .filter(o -> o.getAfterSaleStatus() == null)
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(20)
                .toList();
        String[] statuses = {"PENDING", "REFUNDING", "CLOSED", "FAILED"};
        String[] reasons = {"QUALITY", "SIZE", "LOGISTICS", "PAY_GATEWAY"};
        int made = 0;
        for (OrderPO order : orders) {
            int idx = made % statuses.length;
            RefundOrderPO refund = new RefundOrderPO();
            refund.setRefundNo(idGen.next("RF"));
            refund.setOrderId(order.getId());
            refund.setType("REFUND");
            refund.setAmountCent(Math.max(order.getPaidCent() * 30 / 100, 100L));
            refund.setPointRefund(0L);
            refund.setStatus(statuses[idx]);
            refund.setReasonCode(reasons[idx]);
            refund.setRemark("tiny 档演示售后（" + statuses[idx] + "）");
            refund.setApplicantType("CUSTOMER");
            if ("CLOSED".equals(statuses[idx]) || "FAILED".equals(statuses[idx])) {
                refund.setAuditorId(pick(staffIds));
                refund.setAuditRemark("CLOSED".equals(statuses[idx]) ? "不符合退款条件" : "支付渠道退款失败");
            }
            LocalDateTime t = NOW.minusMinutes(30L + made * 90L);
            refund.setCreatedAt(t);
            refund.setUpdatedAt(t);
            refundOrderMapper.insert(refund);
            made++;
        }
        log.info("[DemoData] tiny 档为 9002 补 {} 张退款单，供「我的售后」翻页与审核页展示。", made);
    }

    // ========================= 客服会话 =========================

    private void generateChats(int sessions) {
        log.info("[DemoData] 生成 {} 个客服会话（每会话 5-10 条消息，含商品卡片）...", sessions);
        List<ProductPO> products = productMapper.selectList(null);

        for (int s = 0; s < sessions; s++) {
            Long customerId = pick(allCustomerIds);
            Long staffId = pick(staffIds);
            boolean closed = random.nextDouble() < 0.8;
            LocalDateTime startedAt = randomPastTime();
            int msgN = 5 + random.nextInt(6);

            ChatSessionPO session = new ChatSessionPO();
            session.setCustomerId(customerId);
            session.setStaffId(staffId);
            session.setStatus(closed ? "CLOSED" : "ACTIVE");
            session.setSourceType(pick(new String[]{"PRODUCT", "ORDER", "HOME"}));
            session.setSourceId(idGen.next("SRC"));
            session.setVip(enterpriseCustomerIds.contains(customerId) ? 1 : 0);
            session.setPriority(random.nextInt(3));
            session.setSeq((long) msgN);
            session.setStartedAt(startedAt);
            session.setEndedAt(closed ? clamp(startedAt.plusMinutes(10 + random.nextInt(180))) : null);
            session.setCloseReason(closed ? pick(new String[]{"已解决", "用户离开", "无响应"}) : null);
            session.setCreatedAt(startedAt);
            session.setUpdatedAt(session.getEndedAt() != null ? session.getEndedAt() : startedAt);
            chatSessionMapper.insert(session);
            long sessionId = session.getId();

            LocalDateTime cursor = startedAt;
            for (int m = 1; m <= msgN; m++) {
                boolean fromCustomer = (m % 2 == 1);
                ChatMessagePO msg = new ChatMessagePO();
                msg.setMessageNo(idGen.next("MSG"));
                msg.setSessionId(sessionId);
                msg.setSeq((long) m);
                msg.setSenderType(fromCustomer ? "CUSTOMER" : "STAFF");
                msg.setSenderId(fromCustomer ? customerId : staffId);

                boolean productCard = !fromCustomer && (m % 3 == 0) && !products.isEmpty();
                if (productCard) {
                    ProductPO p = pick(products);
                    msg.setMsgType("PRODUCT");
                    msg.setContent(p.getTitle());
                    msg.setPayloadJson("{\"productId\":" + p.getId() + ",\"title\":\"" + p.getTitle() + "\",\"priceCent\":0}");
                } else {
                    msg.setMsgType("TEXT");
                    msg.setContent(pick(CHAT_TEXTS));
                    msg.setPayloadJson(null);
                }
                msg.setClientMsgId("CLI-" + UUID.randomUUID());
                cursor = clamp(cursor.plusMinutes(1 + random.nextInt(15)));
                msg.setCreatedAt(cursor);
                msg.setUpdatedAt(cursor);
                chatMessageMapper.insert(msg);
            }
            session.setLastMsgAt(cursor);
            session.setUpdatedAt(cursor);
            chatSessionMapper.updateById(session);
        }
    }

    // ========================= 工具方法 =========================

    private LocalDateTime randomPastTime() {
        // 近 180 天，三次方偏置让近期更密集
        double r = random.nextDouble();
        long days = (long) (180 * Math.pow(r, 3));
        int minutes = random.nextInt(60 * 24);
        LocalDateTime t = NOW.minusDays(days).minusMinutes(minutes);
        return t.isAfter(NOW) ? NOW.minusMinutes(1) : t;
    }

    private LocalDateTime clamp(LocalDateTime t) {
        return t.isAfter(NOW) ? NOW.minusMinutes(1) : t;
    }

    private int randInt(int min, int max) {
        if (min > max) {
            return max;
        }
        return min + random.nextInt(max - min + 1);
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    private String pick(String[] arr) {
        return arr[random.nextInt(arr.length)];
    }

    private int pick(int[] arr) {
        return arr[random.nextInt(arr.length)];
    }

    private <T> List<T> pickDistinct(List<T> source, int n) {
        List<T> copy = new ArrayList<>(source);
        List<T> result = new ArrayList<>();
        for (int i = 0; i < n && !copy.isEmpty(); i++) {
            int idx = random.nextInt(copy.size());
            result.add(copy.remove(idx));
        }
        return result;
    }

    private String randomAddress() {
        return "{\"receiver\":\"演示收货人\",\"phone\":\"13800000000\",\"province\":\"广东省\",\"city\":\"深圳市\","
                + "\"district\":\"南山区\",\"detail\":\"科技园路 " + randInt(1, 99) + " 号\"}";
    }
}
