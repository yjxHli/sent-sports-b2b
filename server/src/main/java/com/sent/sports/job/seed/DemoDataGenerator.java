package com.sent.sports.job.seed;

import com.sent.sports.common.util.IdGen;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customize.mapper.CustomDraftMapper;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomDraftPO;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.mapper.ContractMapper;
import com.sent.sports.finance.mapper.PaymentRecordMapper;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.WriteOffMapper;
import com.sent.sports.finance.mapper.po.ContractPO;
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
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 演示数据生成器（收口阶段）：让系统看起来像真实运营了半年的 B2B 商城。
 *
 * <p><b>环境隔离</b>：{@code @Profile("!prod")} 保证不进生产；{@code @ConditionalOnProperty} 仅当
 * {@code app.demo-data.enabled=true} 时运行（dev 开启、test 关闭），避免在集成测试上下文里灌数据拖慢启动 / 污染隔离。</p>
 *
 * <p><b>幂等</b>：启动时先查订单数量，若 {@code > 100} 直接跳过，保证重复启动不会叠加数据。</p>
 *
 * <p><b>时间分布</b>：所有业务时间落在 {@code [now-180d, now]}，且用三次方偏置让「近期更密集」，
 * 以贴合真实运营曲线。历史时间直接写进 {@code created_at/updated_at}（MetaObjectHandler 仅对 null 字段回填，故手动赋值即生效）。</p>
 *
 * <p><b>架构合规</b>：本类位于 {@code job.seed} 包，受 ArchUnit 规则 4 豁免（规则 4 仅约束
 * controller/service/event 跨模块依赖），因此可直接注入各模块 Mapper 做批量落库。</p>
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
    private final IdGen idGen;

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
                             IdGen idGen) {
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
        this.idGen = idGen;
    }

    // ---- 跨方法共享的运行时数据 ----
    private List<Long> personalCustomerIds = new ArrayList<>();
    private List<Long> enterpriseCustomerIds = new ArrayList<>();
    private List<Long> allCustomerIds = new ArrayList<>();
    private List<Long> staffIds = new ArrayList<>();
    private List<SkuPO> skus = new ArrayList<>();
    private Map<Long, String> productTitle = new HashMap<>();

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
        long orderCount = orderMapper.selectCount(null);
        if (orderCount > 100) {
            log.info("[DemoData] 检测到已存在 {} 条订单（>100），跳过演示数据生成。", orderCount);
            // 订单已就绪时不重灌全量数据，但财务/售后两页仍可能缺展示数据（合同表为空、
            // 退款单清一色 REFUNDED）。补齐动作本身幂等，每次启动都尝试一次。
            backfillFinanceShowcase();
            return;
        }
        log.info("[DemoData] 开始生成演示数据（环境隔离：非生产 + demo-data.enabled=true）...");
        long start = System.currentTimeMillis();

        loadStaff();
        ensureCatalog();
        ensureCustomers();
        loadSellable();
        generateOrders(2000);
        generateChats(30);
        backfillFinanceShowcase();

        long cost = (System.currentTimeMillis() - start) / 1000;
        log.info("[DemoData] 演示数据生成完成，耗时 {}s。客户={} 个人/{} 企业，SKU={}，订单≈2000，会话=30。",
                cost, personalCustomerIds.size(), enterpriseCustomerIds.size(), skus.size());
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

    /** 客户不足时（<50）生成 150 个人 + 50 企业客户，企业客户附带授信档案；否则复用已有客户并回填运行期 ID 列表。 */
    private void ensureCustomers() {
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

    private void loadSellable() {
        List<ProductPO> products = productMapper.selectList(null);
        for (ProductPO p : products) {
            productTitle.put(p.getId(), p.getTitle());
        }
        skus = skuMapper.selectList(null);
    }

    // ========================= 订单 =========================

    private void generateOrders(int total) {
        log.info("[DemoData] 生成 {} 条订单（分布：10% 待付款 / 15% 待发货 / 70% 已完成 / 5% 售后退款）...", total);
        int customizedTotal = 0;
        int receivableTotal = 0;

        for (int i = 0; i < total; i++) {
            int bucket = i % 100; // 0-9 / 10-24 / 25-94 / 95-99 → 10/15/70/5
            String mainStatus;
            if (bucket < 10) {
                mainStatus = "WAIT_PAY";
            } else if (bucket < 25) {
                mainStatus = "WAIT_SHIP";
            } else if (bucket < 95) {
                mainStatus = "COMPLETED";
            } else {
                mainStatus = "AFTERSALE";
            }

            boolean enterpriseCustomer = !enterpriseCustomerIds.isEmpty() && random.nextDouble() < 0.25;
            Long customerId = enterpriseCustomer ? pick(enterpriseCustomerIds) : pick(allCustomerIds);
            String customerType = enterpriseCustomer ? "ENTERPRISE" : "PERSON";

            boolean paid = !"WAIT_PAY".equals(mainStatus);
            boolean shipped = "WAIT_SHIP".equals(mainStatus) || "COMPLETED".equals(mainStatus) || "AFTERSALE".equals(mainStatus);

            // 时间线
            LocalDateTime createdAt;
            if ("WAIT_PAY".equals(mainStatus)) {
                createdAt = NOW.minusMinutes(randInt(0, 120)); // 2 小时内
            } else {
                createdAt = randomPastTime();
            }
            LocalDateTime paidAt = null;
            LocalDateTime completedAt = null;
            if (paid) {
                paidAt = clamp(createdAt.plusMinutes(5 + random.nextInt(600)));
                if ("COMPLETED".equals(mainStatus) || "AFTERSALE".equals(mainStatus)) {
                    completedAt = clamp(paidAt.plusDays(1 + random.nextInt(20)));
                }
            }

            // 选品
            int lineN = 1 + random.nextInt(3);
            List<SkuPO> chosen = pickDistinct(skus, lineN);
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

            // 是否定制（部分待发货/已完成/售后订单带定制）
            boolean customized = shipped && random.nextDouble() < ("WAIT_SHIP".equals(mainStatus) ? 0.3 : 0.2);

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
                    lineCustomFee = (long) (lineAmount * 0.1);
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
                    draft.setFileId((long) randInt(1000, 9999));
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
