package com.sent.sports.integration;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.auth.service.LoginUseCase;
import com.sent.sports.auth.service.dto.LoginCmd;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.cart.dto.CartDtos;
import com.sent.sports.cart.service.CartCommandService;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.customer.service.CustomerGrowthService;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.ReviewCustomUseCase;
import com.sent.sports.customize.service.SubmitDraftUseCase;
import com.sent.sports.customize.service.dto.ReviewCustomCmd;
import com.sent.sports.customize.service.dto.ReviewCustomResult;
import com.sent.sports.customize.service.dto.SubmitDraftCmd;
import com.sent.sports.customize.service.dto.SubmitDraftResult;
import com.sent.sports.finance.domain.CreditLine;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.ReceivablePO;
import com.sent.sports.finance.repository.CreditLineRepository;
import com.sent.sports.finance.service.OrderSettlementService;
import com.sent.sports.finance.service.PaymentRecordUseCase;
import com.sent.sports.finance.service.WriteOffUseCase;
import com.sent.sports.finance.service.dto.PaymentRecordCmd;
import com.sent.sports.finance.service.dto.PaymentRecordResult;
import com.sent.sports.finance.service.dto.WriteOffCmd;
import com.sent.sports.finance.service.dto.WriteOffLine;
import com.sent.sports.finance.service.dto.WriteOffResult;
import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.ShipOrderUseCase;
import com.sent.sports.order.service.dto.ConfirmReceiptCmd;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.order.service.dto.OrderLineCmd;
import com.sent.sports.order.service.dto.ReceiveLineCmd;
import com.sent.sports.order.service.dto.ShipLineCmd;
import com.sent.sports.order.service.dto.ShipOrderCmd;
import com.sent.sports.payment.gateway.MockPayGateway;
import com.sent.sports.payment.service.CreatePaymentUseCase;
import com.sent.sports.payment.service.PayCallbackUseCase;
import com.sent.sports.payment.service.dto.CreatePaymentCmd;
import com.sent.sports.payment.service.dto.CreatePaymentResult;
import com.sent.sports.payment.service.dto.PayCallbackCmd;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 全流程验收（P4 接口契约 + P5 真实数据库）：企业客户登录 → 加购 → 下单（幂等键）→ 支付网关回调
 * → 定制图稿驳回 / 重传 V2 / 通过 → 分批发货两个包裹 → 确认收货 → 财务登记回款并核销。
 *
 * <p>运行方式：完整 Spring 上下文（继承自 {@link FullStackIntegrationTest}）+ 真实 MySQL，
 * 直接调用 UseCase（等价于 MockMvc 打通后的服务层），每一步都用 {@code JdbcTemplate} 断言数据库状态。
 * 数据库既可以是本机 MySQL（默认，{@code application-test.yml} 的 sent_test），也可以是
 * Testcontainers 启动的一次性 MySQL（{@code mvn -o verify -Dit.testcontainers=true}，需 Docker）。</p>
 *
 * <p><b>验收口径（重要）：</b>支付流水、订单实付、包裹、定制单、图稿版本、审图日志、
 * 成长值流水、应收与企业授信全部为真库落账，断言直接校验行数、金额与关键字段。
 * Phase 10 起 {@link com.sent.sports.customize.repository.impl.DbCustomizeRepository}
 * 取代内存适配器，{@code custom_draft} / {@code custom_review_log} 由
 * {@code uk_draft_version} / {@code uk_review_once} 唯一键守护幂等，本用例不再有验收缺口。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class FullBusinessFlowIT extends FullStackIntegrationTest {

    private static final long CUSTOMER_ID = 8801L;
    private static final long STANDARD_SKU = 9101L;
    private static final long CUSTOM_SKU = 9102L;
    private static final long STANDARD_PRICE_CENT = 10_000L;
    private static final long CUSTOM_PRICE_CENT = 20_000L;
    private static final int STANDARD_QTY = 5;
    private static final int CUSTOM_QTY = 10;
    private static final long PAYABLE_CENT =
            STANDARD_PRICE_CENT * STANDARD_QTY + CUSTOM_PRICE_CENT * CUSTOM_QTY;
    private static final int SEED_PHYSICAL_QTY = 100;
    private static final String IDEM_KEY = "IDEM-ACCEPT-FLOW-1";
    private static final String CUSTOM_NO = "CO-ACCEPT-FLOW-1";
    private static final long CREDIT_TOTAL_CENT = 1_000_000L;

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private LoginUseCase loginUseCase;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private CartCommandService cartCommandService;
    @Autowired
    private CreateOrderUseCase createOrderUseCase;
    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;
    @Autowired
    private PayCallbackUseCase payCallbackUseCase;
    @Autowired
    private CustomizeRepository customizeRepository;
    @Autowired
    private SubmitDraftUseCase submitDraftUseCase;
    @Autowired
    private ReviewCustomUseCase reviewCustomUseCase;
    @Autowired
    private ShipOrderUseCase shipOrderUseCase;
    @Autowired
    private ConfirmReceiptUseCase confirmReceiptUseCase;
    @Autowired
    private ReceivableMapper receivableMapper;
    @Autowired
    private PaymentRecordUseCase paymentRecordUseCase;
    @Autowired
    private WriteOffUseCase writeOffUseCase;
    @Autowired
    private CreditLineRepository creditLineRepository;
    @Autowired
    private OrderSettlementService orderSettlementService;
    @Autowired
    private CustomerGrowthService customerGrowthService;

    @Test
    @DisplayName("全流程验收：登录→加购→下单→支付→定制→分批发货→收货→回款核销")
    void fullBusinessFlow() {
        seedEnterpriseCustomer();

        // ------------------------------------------------------------------
        // 1) 企业客户登录，获取 Token
        // ------------------------------------------------------------------
        userAccountRepository.save(new UserAccount(CUSTOMER_ID, "ent-buyer", "P@ssw0rd"));
        TokenPair tokens = loginUseCase.login(new LoginCmd("ent-buyer", "P@ssw0rd", "user", "device-accept"));
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        // 登录闭环：登录返回的 AT 必须是 AuthFilter 能验签的真实 JWT，sub = 当前客户
        TokenService.Verified loginVerified = tokenService.verify(tokens.accessToken());
        assertThat(loginVerified.aud()).isEqualTo("user");
        assertThat(loginVerified.userId()).isEqualTo(CUSTOMER_ID);

        // 真实 JWT 身份链路：签发 → 校验 → 与 DB 里的企业类型对齐
        TokenService.Issued issued = tokenService.issueAccess(
                new TokenService.Claims("user", CUSTOMER_ID, 0L, "jti-accept"));
        TokenService.Verified verified = tokenService.verify(issued.accessToken());
        assertThat(verified.aud()).isEqualTo("user");
        assertThat(verified.userId()).isEqualTo(CUSTOMER_ID);
        assertThat(jdbc.queryForObject("SELECT type FROM customer WHERE id = ?", String.class, CUSTOMER_ID))
                .isEqualTo("ENTERPRISE");

        // ------------------------------------------------------------------
        // 2) 加入购物车：标品 5 个、UV 定制件 10 个
        //    任务 2 后 CartCommandServiceImpl 从 AuthFilter 注入的 CurrentUser 取主体并真写
        //    cart_item；本用例直接调服务（无 Servlet 容器），用 MockHttpServletRequest
        //    还原「JWT sub → CurrentUser → 数据归属」这段身份链路。
        // ------------------------------------------------------------------
        seedSku(STANDARD_SKU, STANDARD_PRICE_CENT, SEED_PHYSICAL_QTY);
        seedSku(CUSTOM_SKU, CUSTOM_PRICE_CENT, SEED_PHYSICAL_QTY);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(CurrentUser.ATTR,
                new CurrentUser(CurrentUser.AUD_USER, CUSTOMER_ID, "jti-accept-flow"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        CartDtos.CartItemVO standardCart;
        CartDtos.CartItemVO customCart;
        try {
            standardCart = cartCommandService.addItem(new CartDtos.AddItemCmd(STANDARD_SKU, STANDARD_QTY));
            customCart = cartCommandService.addItem(new CartDtos.AddItemCmd(CUSTOM_SKU, CUSTOM_QTY));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        assertThat(standardCart.skuId()).isEqualTo(STANDARD_SKU);
        assertThat(standardCart.qty()).isEqualTo(STANDARD_QTY);
        assertThat(customCart.skuId()).isEqualTo(CUSTOM_SKU);
        assertThat(customCart.qty()).isEqualTo(CUSTOM_QTY);

        assertThat(countOf("cart_item"))
                .as("任务 2 解除 P4 查询桩后，addItem 必须真写 cart_item")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT SUM(qty) FROM cart_item WHERE customer_id = ?", Long.class, CUSTOMER_ID))
                .isEqualTo((long) (STANDARD_QTY + CUSTOM_QTY));
        assertThat(jdbc.queryForObject(
                "SELECT price_snapshot_cent FROM cart_item WHERE sku_id = ?", Long.class, STANDARD_SKU))
                .isEqualTo(STANDARD_PRICE_CENT);
        assertThat(jdbc.queryForObject(
                "SELECT price_snapshot_cent FROM cart_item WHERE sku_id = ?", Long.class, CUSTOM_SKU))
                .isEqualTo(CUSTOM_PRICE_CENT);

        // ------------------------------------------------------------------
        // 3) 生成订单（带 Idempotency-Key）：两行、价格快照、库存预占
        //    SKU / 库存台账已在步骤 2 播种（加购需要真实零售价）
        // ------------------------------------------------------------------
        CreateOrderCmd orderCmd = new CreateOrderCmd(CUSTOMER_ID, IDEM_KEY, 0,
                Integer.MAX_VALUE, 0, List.of(
                new OrderLineCmd(STANDARD_SKU, STANDARD_QTY, "PERSON", 100, 100, false, List.of()),
                new OrderLineCmd(CUSTOM_SKU, CUSTOM_QTY, "PERSON", 100, 100, false, List.of())));
        CreateOrderResult order = createOrderUseCase.create(orderCmd);

        assertThat(order.status()).isEqualTo("WAIT_PAY");
        assertThat(order.payableCent()).isEqualTo(PAYABLE_CENT);
        assertThat(orderCount()).isEqualTo(1L);
        assertThat(countOf("order_item")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT idem_key FROM orders WHERE order_no = ?",
                String.class, order.orderNo())).isEqualTo(IDEM_KEY);
        assertThat(jdbc.queryForObject("SELECT payable_cent FROM orders WHERE order_no = ?",
                Long.class, order.orderNo())).isEqualTo(PAYABLE_CENT);
        assertThat(lockedQtyOf(STANDARD_SKU)).isEqualTo(STANDARD_QTY);
        assertThat(lockedQtyOf(CUSTOM_SKU)).isEqualTo(CUSTOM_QTY);
        assertThat(stockFlowCount(STANDARD_SKU, "LOCK")).isGreaterThanOrEqualTo(1);
        assertThat(stockFlowCount(CUSTOM_SKU, "LOCK")).isGreaterThanOrEqualTo(1);

        // 同一 Idempotency-Key 重放：DB 唯一索引兜底，且补偿释放不残留预占
        assertThatThrownBy(() -> createOrderUseCase.create(orderCmd))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PROCESSING);
        assertThat(orderCount()).isEqualTo(1L);
        assertThat(lockedQtyOf(STANDARD_SKU)).isEqualTo(STANDARD_QTY);
        assertThat(lockedQtyOf(CUSTOM_SKU)).isEqualTo(CUSTOM_QTY);

        // ------------------------------------------------------------------
        // 4) 模拟支付网关回调：WAIT_SHIP / PAID，库存锁定量保持
        // ------------------------------------------------------------------
        CreatePaymentResult payment = createPaymentUseCase.create(new CreatePaymentCmd(
                order.orderNo(), PAYABLE_CENT, "MOCK", "PAY-IDEM-ACCEPT-1"));
        assertThat(payment.status()).isEqualTo("CREATED");

        MockPayGateway gateway = new MockPayGateway("test-only-fixture-secret", Clock.systemUTC());
        Map<String, String> callback = gateway.buildCallback(
                payment.txnNo(), order.orderNo(), PAYABLE_CENT, true);
        assertThat(gateway.verify(callback)).as("回调签名必须校验通过").isTrue();
        Map<String, String> tampered = new HashMap<>(callback);
        tampered.put("amountCent", "1");
        assertThat(gateway.verify(tampered)).as("篡改金额后签名必须失败").isFalse();

        assertThat(payCallbackUseCase.handle(new PayCallbackCmd(
                callback.get("txnNo"), callback.get("orderNo"), PAYABLE_CENT, "MOCK",
                "CH-ACCEPT-1", System.currentTimeMillis())).status()).isEqualTo("SUCCESS");
        assertThat(statusOf(order.orderNo())).isEqualTo("WAIT_SHIP");
        assertThat(payStatusOf(order.orderNo())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT paid_cent FROM orders WHERE order_no = ?",
                Long.class, order.orderNo()))
                .as("回调必须把渠道实付金额回写 orders.paid_cent")
                .isEqualTo(PAYABLE_CENT);
        assertThat(lockedQtyOf(STANDARD_SKU)).isEqualTo(STANDARD_QTY);
        assertThat(lockedQtyOf(CUSTOM_SKU)).isEqualTo(CUSTOM_QTY);
        assertThat(physicalQtyOf(STANDARD_SKU)).as("支付阶段只锁不扣实物").isEqualTo(SEED_PHYSICAL_QTY);
        assertThat(countOf("payment_txn"))
                .as("发起支付 + 回调必须真实落一条 payment_txn")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_txn WHERE txn_no = ?",
                String.class, payment.txnNo())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT amount_cent FROM payment_txn WHERE txn_no = ?",
                Long.class, payment.txnNo())).isEqualTo(PAYABLE_CENT);

        // 重复回调：uk_txn_no + 条件更新兜底，不产生第二条流水、不二次回写 paid_cent
        assertThat(payCallbackUseCase.handle(new PayCallbackCmd(
                callback.get("txnNo"), callback.get("orderNo"), PAYABLE_CENT, "MOCK",
                "CH-ACCEPT-1", System.currentTimeMillis())).status()).isEqualTo("ALREADY_PAID");
        assertThat(countOf("payment_txn")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT paid_cent FROM orders WHERE order_no = ?",
                Long.class, order.orderNo())).isEqualTo(PAYABLE_CENT);

        // ------------------------------------------------------------------
        // 5) 客服审核定制图稿：V1 → 驳回一次 → 客户重传 V2 → 通过
        // ------------------------------------------------------------------
        long deadline = System.currentTimeMillis() + 24L * 3600 * 1000;
        customizeRepository.save(new CustomOrder(CUSTOM_NO, CustomStatus.WAIT_DRAFT, 0, 0, deadline));
        SubmitDraftResult v1 = submitDraftUseCase.submit(
                new SubmitDraftCmd(CUSTOM_NO, "FILE-V1", "draft-v1.ai", 2048L));
        assertThat(v1.version()).isEqualTo(1);
        assertThat(v1.status()).isEqualTo("WAIT_REVIEW");

        ReviewCustomResult rejected = reviewCustomUseCase.review(
                new ReviewCustomCmd(CUSTOM_NO, "REJECT", "SIZE", "图稿尺寸不符"));
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(customizeRepository.findByCustomOrderId(CUSTOM_NO).orElseThrow().getRejectCount())
                .isEqualTo(1);

        SubmitDraftResult v2 = submitDraftUseCase.submit(
                new SubmitDraftCmd(CUSTOM_NO, "FILE-V2", "draft-v2.ai", 4096L));
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.status()).isEqualTo("WAIT_REVIEW");

        ReviewCustomResult passed = reviewCustomUseCase.review(
                new ReviewCustomCmd(CUSTOM_NO, "PASS", null, null));
        assertThat(passed.status()).isEqualTo("APPROVED");
        assertThat(passed.lockedVersion()).isEqualTo(2);
        assertThat(customizeRepository.findByCustomOrderId(CUSTOM_NO).orElseThrow().getStatus())
                .isEqualTo(CustomStatus.APPROVED);
        assertThat(countOf("custom_order"))
                .as("定制单聚合经真库仓储 save() 落 custom_order（发货单尚未生成时仅此一条）")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM custom_order WHERE custom_no = ?",
                String.class, CUSTOM_NO)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT locked_version FROM custom_order WHERE custom_no = ?",
                Integer.class, CUSTOM_NO))
                .as("审核通过必须把锁定的图稿版本回写真库（V13 新增列）")
                .isEqualTo(2);

        assertThat(countOf("custom_draft"))
                .as("图稿 V1 / V2 两次提交必须真实落 custom_draft")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT file_id FROM custom_draft WHERE version = 1", String.class))
                .as("V13 把 file_id 改为 VARCHAR，字符串文件标识必须原样落库")
                .isEqualTo("FILE-V1");
        assertThat(jdbc.queryForObject("SELECT file_id FROM custom_draft WHERE version = 2", String.class))
                .isEqualTo("FILE-V2");

        assertThat(countOf("custom_review_log"))
                .as("一次驳回 + 一次通过必须真实落两条 custom_review_log")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT result FROM custom_review_log WHERE draft_version = 1", String.class))
                .isEqualTo("REJECT");
        assertThat(jdbc.queryForObject(
                "SELECT reason_code FROM custom_review_log WHERE draft_version = 1", String.class))
                .isEqualTo("SIZE");
        assertThat(jdbc.queryForObject(
                "SELECT result FROM custom_review_log WHERE draft_version = 2", String.class))
                .isEqualTo("PASS");

        // ------------------------------------------------------------------
        // 6) 仓管分批发货：先标品、后定制件（两次调用 = 两个包裹）
        // ------------------------------------------------------------------
        assertThat(shipOrderUseCase.ship(new ShipOrderCmd(order.orderNo(), "SF", "TRACK-ACCEPT-1",
                List.of(new ShipLineCmd(STANDARD_SKU, STANDARD_QTY)))).status()).isEqualTo("PART_SHIPPED");
        assertThat(statusOf(order.orderNo())).isEqualTo("PART_SHIPPED");
        assertThat(shippedQtyOf(order.orderNo(), STANDARD_SKU)).isEqualTo(STANDARD_QTY);
        assertThat(shippedQtyOf(order.orderNo(), CUSTOM_SKU)).isZero();
        assertThat(countOf("custom_order"))
                .as("第一个包裹只发标品，此时还不应为定制行生成定制单")
                .isEqualTo(1);

        // arrange：把 UV 定制件那一行标记为定制行。放在两个包裹之间，是为了让
        // 「定制完工门禁」在本次发货前仍能看到「该行暂无定制记录」，从而不阻塞验收主链路。
        jdbc.update("UPDATE order_item SET custom_flag = 1, custom_fee_cent = 0 "
                        + "WHERE order_id = (SELECT id FROM orders WHERE order_no = ?) AND sku_id = ?",
                order.orderNo(), CUSTOM_SKU);

        assertThat(shipOrderUseCase.ship(new ShipOrderCmd(order.orderNo(), "SF", "TRACK-ACCEPT-2",
                List.of(new ShipLineCmd(CUSTOM_SKU, CUSTOM_QTY)))).status()).isEqualTo("WAIT_RECEIVE");
        assertThat(statusOf(order.orderNo())).isEqualTo("WAIT_RECEIVE");
        assertThat(shippedQtyOf(order.orderNo(), CUSTOM_SKU)).isEqualTo(CUSTOM_QTY);
        assertThat(countOf("shipment"))
                .as("两次发货 = 两个真实包裹记录")
                .isEqualTo(2);
        assertThat(countOf("shipment_item"))
                .as("每个包裹各一条明细，指向 order_item 真实主键")
                .isEqualTo(2);
        assertThat(countOf("custom_order"))
                .as("步骤 5 的审图定制单 + 发货时为定制行生成的定制单，共两条")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM custom_order WHERE order_item_id IS NOT NULL", String.class))
                .as("发货落账生成的定制单应处于 WAIT_DRAFT")
                .isEqualTo("WAIT_DRAFT");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM custom_order WHERE custom_no = ?", String.class, CUSTOM_NO))
                .as("审图链路的 APPROVED 状态必须留在真库")
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipment s JOIN shipment_item si ON si.shipment_id = s.id "
                        + "WHERE s.tracking_no = 'TRACK-ACCEPT-1'", Integer.class))
                .as("第一个包裹必须带明细").isEqualTo(1);

        // ------------------------------------------------------------------
        // 7) 客户确认收货：COMPLETED + 成长值 / 信用额度核对
        // ------------------------------------------------------------------
        assertThat(confirmReceiptUseCase.confirm(new ConfirmReceiptCmd(order.orderNo(), List.of(
                new ReceiveLineCmd(STANDARD_SKU, STANDARD_QTY),
                new ReceiveLineCmd(CUSTOM_SKU, CUSTOM_QTY)))).completed()).isTrue();
        assertThat(statusOf(order.orderNo())).isEqualTo("COMPLETED");
        assertThat(versionOf(order.orderNo()))
                .as("支付 / 两次发货 / 收货共推进 4 次乐观锁")
                .isGreaterThanOrEqualTo(4L);
        long growth = PAYABLE_CENT / 100; // 口径：1 元 = 1 成长值
        assertThat(jdbc.queryForObject("SELECT growth_value FROM customer WHERE id = ?",
                Long.class, CUSTOMER_ID))
                .as("订单完成必须累加 customer.growth_value")
                .isEqualTo(growth);
        assertThat(countOf("growth_log"))
                .as("订单完成必须落一条成长值流水")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT change_value FROM growth_log WHERE source_id = ?",
                Long.class, order.orderNo())).isEqualTo(growth);
        assertThat(jdbc.queryForObject("SELECT source_type FROM growth_log WHERE source_id = ?",
                String.class, order.orderNo())).isEqualTo("ORDER");
        assertThat(jdbc.queryForObject("SELECT credit_used_cent FROM enterprise_profile WHERE customer_id = ?",
                Long.class, CUSTOMER_ID))
                .as("确认收货形成应收时应占用企业授信")
                .isEqualTo(PAYABLE_CENT);

        // 幂等重放：uk_growth_source / uk_receivable_order 兜底，不重复发成长值、不重复占用额度
        customerGrowthService.grantGrowth(CUSTOMER_ID, growth, "ORDER", order.orderNo());
        orderSettlementService.onOrderCompleted(order.orderNo(), CUSTOMER_ID, PAYABLE_CENT);
        assertThat(countOf("growth_log")).isEqualTo(1);
        assertThat(countOf("receivable")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT growth_value FROM customer WHERE id = ?",
                Long.class, CUSTOMER_ID)).isEqualTo(growth);
        assertThat(jdbc.queryForObject("SELECT credit_used_cent FROM enterprise_profile WHERE customer_id = ?",
                Long.class, CUSTOMER_ID)).isEqualTo(PAYABLE_CENT);

        // ------------------------------------------------------------------
        // 8) 财务登记回款并核销：应收台账结清
        // ------------------------------------------------------------------
        long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?",
                Long.class, order.orderNo());
        // 应收与授信占用均由步骤 7 的「确认收货」链路真实生成，不再由测试手工补前置条件
        ReceivablePO receivable = receivableMapper.selectByOrderId(orderId);
        assertThat(receivable).isNotNull();
        assertThat(countOf("receivable")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_cent FROM receivable WHERE order_id = ?",
                Long.class, orderId)).isEqualTo(PAYABLE_CENT);

        PaymentRecordResult record = paymentRecordUseCase.record(new PaymentRecordCmd(
                CUSTOMER_ID, PAYABLE_CENT, "BANK-ACCEPT-1", 0L, "全额回款"));
        assertThat(record.status()).isEqualTo("PENDING");
        assertThat(countOf("payment_record")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT written_off_cent FROM payment_record WHERE record_no = ?",
                Long.class, record.paymentNo())).isZero();

        WriteOffResult writeOff = writeOffUseCase.writeOff(new WriteOffCmd(
                CUSTOMER_ID, record.paymentNo(),
                List.of(new WriteOffLine(receivable.getId(), PAYABLE_CENT)), 0L));
        assertThat(writeOff.status()).isEqualTo("SUCCESS");
        assertThat(writeOff.totalWrittenOff()).isEqualTo(PAYABLE_CENT);
        assertThat(writeOff.closedReceivableIds()).contains(receivable.getId());

        assertThat(jdbc.queryForObject("SELECT received_cent FROM receivable WHERE id = ?",
                Long.class, receivable.getId())).isEqualTo(PAYABLE_CENT);
        assertThat(jdbc.queryForObject("SELECT status FROM receivable WHERE id = ?",
                String.class, receivable.getId())).isEqualTo("CLOSED");
        assertThat(countOf("write_off")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_cent FROM write_off WHERE receivable_id = ?",
                Long.class, receivable.getId())).isEqualTo(PAYABLE_CENT);
        assertThat(jdbc.queryForObject("SELECT written_off_cent FROM payment_record WHERE record_no = ?",
                Long.class, record.paymentNo())).isEqualTo(PAYABLE_CENT);

        CreditLine creditLine = creditLineRepository.findByCustomerId(CUSTOMER_ID).orElseThrow();
        assertThat(creditLine.getUsedCent()).as("核销后占用额度释放为 0").isZero();
        assertThat(creditLine.available()).isEqualTo(CREDIT_TOTAL_CENT);
        assertThat(jdbc.queryForObject("SELECT credit_used_cent FROM enterprise_profile WHERE customer_id = ?",
                Long.class, CUSTOMER_ID))
                .as("回款核销释放占用后，企业授信列必须对称回写为 0")
                .isZero();
    }

    private void seedEnterpriseCustomer() {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, password_hash, nickname, type, "
                        + "level_code, growth_value, points_balance, status, token_version, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 'ENTERPRISE', 'LV1', 0, 0, 'ACTIVE', 0, 0)",
                CUSTOMER_ID, "ENT-8801", "13800008801", "hash", "森特企业客户");
        jdbc.update("INSERT INTO enterprise_profile (customer_id, company_name, credit_limit_cent, "
                        + "credit_used_cent, payment_term_days, status) VALUES (?, ?, ?, 0, 30, 'ACTIVE')",
                CUSTOMER_ID, "森特体育企业", CREDIT_TOTAL_CENT);
    }

    private int shippedQtyOf(String orderNo, long skuId) {
        Integer value = jdbc.queryForObject(
                "SELECT oi.shipped_qty FROM order_item oi JOIN orders o ON o.id = oi.order_id "
                        + "WHERE o.order_no = ? AND oi.sku_id = ?", Integer.class, orderNo, skuId);
        return value == null ? 0 : value;
    }
}
