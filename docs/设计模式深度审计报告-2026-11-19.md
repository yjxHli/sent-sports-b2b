# 森特体育 B2B · 设计模式深度审计报告

> 审计对象：`server`（Spring Boot 3.2.5 / Java 17 / 20 个业务包）、`web-admin`、`web-user`、`packages/shared`
> 审计时间：2026-11-19
> 审计口径：设计模式 **应用质量**（是否真的解耦）与 **缺失识别**（该用没用，或用成空壳）
> 方法：全量静态扫描 + 逐文件取证 + 关键结论二次复核（所有结论均带 `文件:行` 证据，可逐条验证）

---

## 0. 一页结论

### 0.1 评分

| 维度 | 得分 | 依据 |
|---|---|---|
| 后端模式应用质量 | **8.4 / 10** | 取价策略、表驱动状态机、数据权限 SQL 改写属教科书级；扣分在"事件总线空转""状态机被绕过" |
| 后端模式缺失程度 | **6.5 / 10** | 缺的不是模式认知，而是**已有模式没用全**；另有 5 处硬编码/复制粘贴落在资金与权益链路上 |
| 前端模式应用质量 | **8.0 / 10** | HTTP 内核（代理模式）是全仓质量最高的一层；Pinia 单源未读数做得干净 |
| 前端模式缺失程度 | **6.8 / 10** | 缺 `useTable` 这类真正的表格门面，导致 25 处 `onSize`、13 处翻页拉全量、7 处局部状态字典 |
| 文档与代码一致性 | **5.0 / 10** | 多处注释写着"所有状态变更必须走状态机""由审计模块监听持久化"，代码并未如此 |

### 0.2 三句话结论

1. **这个仓库不缺设计模式，缺的是"模式闭环"**：8 个领域事件类、7 个发布点、**0 个订阅者**；`@Idempotent` / `@RateLimit` 两个注解装配完整但**业务代码零使用**（全仓 grep 无命中）。模式被建成了接口，却没接上线。
2. **最该修的 5 个问题里，有 4 个不是"不够优雅"，而是真实资损/越权/安全缺陷**——自动确认收货漏发积分与应收、退款不退积分、支付回调不验签、定制费双份硬编码。
3. **前端的问题集中在"没抽象"而不是"抽象错了"**：`usePagination` 只封装了状态没封装请求，于是 25 个页面各写一遍 `onSize`；状态字典已有 `STATUS_LABELS`，却有 7 个页面自建同名字典。

### 0.3 Top 5 风险（按资损/安全排序）

| # | 问题 | 位置 | 性质 |
|---|---|---|---|
| 1 | 自动确认收货绕过用例，**只改状态不发积分/成长值/应收台账** | `job/AutoConfirmJob.java:59` vs `order/service/ConfirmReceiptUseCase.java:69-77` | 功能缺陷 + 用户权益缺失 |
| 2 | 支付回调**从不验签**，`PayGateway.verify()` 零调用，仅比对金额 | `payment/service/PayCallbackUseCase.java:60-77` | 安全缺陷（可伪造支付成功） |
| 3 | 退款事件无人监听，**退款不退积分/成长值** | `member/service/impl/MemberRefundGatewayImpl.java:23` | 事件空转导致的权益缺失 |
| 4 | 退款 Saga 硬编码在用例里，且**外部支付网关调用落在 DB 事务内** | `order/service/CloseOrderUseCase.java:92-106` → `payment/service/impl/PaymentRefundGatewayImpl.java:41` | 一致性风险（钱退了单没关） |
| 5 | 定制费 `1000L` 硬编码两份，唯一计费引擎是死代码 | `cart/service/CartQuoteUseCase.java:53`、`order/service/CreateOrderUseCase.java:55` | 试算/实付不一致风险 |

---

## 1. 审计方法与统计口径

| 项 | 数量 | 说明 |
|---|---|---|
| 后端主代码 Java 文件 | 677 | `server/src/main/java` |
| 后端测试 Java 文件 | 208 | `server/src/test/java` |
| 前端 `.vue` + `.ts`（双双 src） | 144 | 不含 node_modules / dist |
| `.vue` 总行数 | 29,495 | 单文件最大 1,694 行 |
| `switch (` 全仓出现次数 | **6** | 见 2.1.1，**本仓不存在巨型 switch** |
| `@UseCase` 标注 | 106 | 事务边界统一由切面承担 |
| `@RequirePerm` 标注 | 123 | 权限码全部为裸字符串 |
| `@EventListener` / `ApplicationListener` / `@Async` / `@EnableAsync` | **0** | 复核命令见附录 A.1 |
| `@Idempotent` / `@RateLimit` 业务使用 | **0** | 仅注解定义与拦截器自身 |
| 领域事件类 / 发布点 / 订阅者 | 8 / 7 / **0** | 见 1.1.5 |

> **重要方法说明**：为避免"AI 编造模式"，本次审计对每条结论做了**反向验证**——先由扫描给出候选，再用 `grep` 复核数量、用文件片段复核行号。例如"前端没有 useTable"这条，是 `grep -rn "useTable"` 零命中后确认的，因此本报告纠正了任务描述中的一个前提假设（详见 1.2.1）。

---

# 维度一 · 已应用的模式盘点（提炼亮点）

## 1.1 后端

### 1.1.1 策略模式 — 取价引擎（教科书级 ★★★★★）

**证据链**

| 文件:行 | 类 / 成员 | 说明 |
|---|---|---|
| `product/domain/pricing/PriceStrategy.java:7-16` | `interface PriceStrategy { int order(); boolean match(); PriceResult price(); }` | 三方法策略接口，零 Spring 依赖，可纯单测 |
| `product/domain/pricing/PricingService.java:20` | `private final List<PriceStrategy> strategies` | 策略注册表 |
| `product/domain/pricing/PricingService.java:22-29` | 无参构造 | 显式装配 5 个策略（**硬编码注册，唯一瑕疵**） |
| `product/domain/pricing/PricingService.java:31-34` | `PricingService(List)` | `sort(Comparator.comparingInt(PriceStrategy::order))` 定序 |
| `product/domain/pricing/PricingService.java:49-53` | `calc(PriceContext)` | `for` + `if (s.match(ctx)) return finalize(...)` — **首次命中即返回，严格优先级** |
| `product/domain/pricing/PricingService.java:60-66` | `finalize(...)` | 底价拦截 `BELOW_FLOOR_PRICE(40920)`、成本价只置 `belowCost` 标记 |
| `CustomerSkuPriceStrategy:7` / `AgreementPriceStrategy:7` / `LadderPriceStrategy:7` / `MemberPriceStrategy:7` / `RetailPriceStrategy:7` | `order()` = 1/2/3/4/5 | 一客一价 → 协议价 → 阶梯价 → 会员价 → 零售兜底 |
| `product/domain/pricing/PriceSource.java:4-11` | 6 值枚举 | 每次报价都能举证"为什么是这个价" |

**评语**：这是全仓最值得当范本的多态替换。`RetailPriceStrategy.match()` 恒 `true` 保证策略链必然收敛，`PriceSource` 让"价格来源"成为可审计对象，底价/成本双重校验放在统一出口而不是散在 5 个策略里——**新增一种价格体系只需加一个类 + 一行注册**，完全符合开闭原则。

**缺口（2 处，详见 R6）**：
- 策略注册硬编码在 `PricingService` 构造器，未用 `@Order` + `List<PriceStrategy>` 注入，新增策略仍需改构造器；
- `PricingServiceFacadeImpl.java:28` 直接 `new PricingService()`，策略链无法被 Spring 替换/装饰（如加缓存、加埋点）。

---

### 1.1.2 状态模式 / 状态机（★★★☆☆ 结构教科书，执行有漏洞）

**实现形态**：不是 GoF 的对象态模式，而是**表驱动状态机**——`EnumMap<状态, EnumMap<动作, 状态>>`，纯 Java 静态类、零 Spring 依赖、可单测。

| 文件:行 | 内容 |
|---|---|
| `order/domain/OrderStateMachine.java:16` | `Map<OrderStatus, Map<OrderAction, OrderStatus>> TRANSITIONS = new EnumMap<>(...)` |
| `order/domain/OrderStateMachine.java:18-40` | `static {}` 块声明 19 条迁移（`put(from, action, to)`） |
| `order/domain/OrderStateMachine.java:46-55` | `static transit(from, action)` — 非法迁移抛 `STATE_CONFLICT(40901)` |
| `order/domain/OrderStateMachine.java:57-63` | `canTransit(...)` — 只读预判，供用例前置检查 |
| `order/domain/PayStateMachine.java:15-40` | 同构实现，7 条迁移，与订单主状态**分离**（支付维度独立） |
| `customize/domain/CustomStateMachine.java:16-57` | 同构，额外支持 `rejectCount` 重载：第 4 次提交 `REJECTED→ESCALATED` |
| `order/domain/Order.java:155-157` | `transit(OrderAction)` — 聚合内唯一迁移入口 |
| `order/domain/OrderStatus.java:4-11` | 7 状态 + `terminal` 标志 |

**优点**：多维流转（订单主状态 / 支付状态 / 定制状态）分表建模，而不是挤进一个大枚举——这是本仓架构判断力的体现。

**执行漏洞（高危，详见 R2）**——注释写"所有状态变更必须集中走此方法"（`OrderStateMachine.java:11`），实际有 4 处绕过：

| 绕过点 | 代码 | 后果 |
|---|---|---|
| `order/service/ShipOrderUseCase.java:71` | `order.setStatus(ShipmentProgress.recalc(...))` | 发货状态由计算函数定，状态机不知情 |
| `order/service/ConfirmReceiptUseCase.java:70` | `order.setStatus(OrderStatus.COMPLETED)` | 配合 `:28` 的白名单，与状态机冲突（见 R2） |
| `order/service/impl/OrderPaymentServiceImpl.java:51` | `order.setPayStatus(PayStatus.PAID)` | `PayStateMachine.transit` 全仓仅 `CloseOrderUseCase.java:82` 调用 1 次 |
| `refund/service/RefundAuditUseCase.java:64/75/101` | `order.setStatus(REFUNDING/CLOSED/PENDING)` | **退款模块根本没有状态机**（见 R3） |

---

### 1.1.3 代理模式 — 事务 / 权限 / 幂等 / 限流 / 数据权限（★★★★☆）

**关键洞察**：这 5 个"切面"其实是**三种完全不同的织入机制**，不能笼统称为"代理模式"。这是本报告第一个需要纠正的认知：

| 机制 | 实现 | 织入点 | 增强对象 |
|---|---|---|---|
| **Spring AOP 代理**（JDK/CGLIB 动态代理） | `common/anno/UseCaseAspect.java:13-15,23` | `@Around("@annotation(..UseCase)")` | 事务：`TransactionTemplate.execute`（`:25-35`），异常 `setRollbackOnly` |
| **Spring MVC 拦截器**（HandlerInterceptor，非 AOP） | `PermissionInterceptor:24,33-66`<br>`IdempotentInterceptor:27,45-98`<br>`RateLimitInterceptor:20,31-46`<br>`DataScopeWebInterceptor:15,24-36` | `preHandle` / `afterCompletion` | 权限 40101/40301、幂等快照回放、滑动窗口 429、Scope 写 ThreadLocal |
| **MyBatis 插件**（`Plugin.wrap` JDK 动态代理） | `common/mybatis/DataScopeInterceptor.java:36-39,44-63` | `StatementHandler.prepare` | 反射改写 `BoundSql.sql`（`:126-134`），追加数据范围 WHERE |

**亮点细节**：

- `IdempotentInterceptor.java:45-98` 是完整的幂等状态机：`PROCESSING → DONE` 快照回放、`X-Idempotent-Replay` 响应头、**业务异常删键 / 系统异常保键**（这个区分很专业）；
- `common/mybatis/DataScopeSqlAppender.java:51-142` 不引 SQL 解析库，自实现词法扫描：跳过引号与注释、按括号深度判断、遇到 `UNION` 直接拒绝——这是少见的"安全默认"实现；
- `WebInterceptorConfig.java:41-58` 注册顺序 限流→幂等→权限→数据范围，顺序有讲究（先挡流量再判权限），并用 `ObjectProvider` 做切片测试降级。

**缺口（死代码）**：`@Idempotent` 与 `@RateLimit` 在业务代码中**使用次数为 0**（复核：`grep -rn "@Idempotent\|@RateLimit" | grep -v "common/anno\|common/interceptor"` 返回空）。拦截器已装配但永不触发——下单幂等实际靠数据库唯一索引 `uk_order_idem`（`OrderRepositoryImpl` 捕获 `DuplicateKeyException`），这不算错，但两个注解成了"看起来有的能力"。

---

### 1.1.4 适配器模式 — 支付是，文件存储不是（★★★☆☆）

**真正的端口与适配器（Hexagonal）**：

| 端口（接口） | 适配器（实现） | 装配点 |
|---|---|---|
| `payment/gateway/PayGateway.java:10-26`（`create` / `verify` / `refund`） | `payment/gateway/MockPayGateway.java:25,56-65`（HMAC-SHA256 + 常数时间比较） | `payment/gateway/PayGatewayConfig.java:13-17` `@Bean` 决定实现 |
| `order/service/OrderRefundGateway.java:7-10` | `order/service/impl/OrderRefundGatewayImpl.java:18`（`@Service`） | 跨模块契约，明示 ArchUnit 规则 4 |
| `member/service/MemberRefundGateway.java` / `customize/service/CustomizeRefundGateway.java` / `stock/service/StockRefundGateway.java` | 各自 `impl` | 同上 |

**结论**：支付通道替换（Mock ↔ 支付宝 ↔ 微信）只需换 `PayGateway` 实现 + 改 `PayGatewayConfig` 一行，业务层零改动 —— 这是标准适配器 + 工厂装配。

**但文件存储不是适配器**：

```java
// common/storage/LocalFileStorage.java:27-29
/** 将来切对象存储只需替换本类 */
public class LocalFileStorage {          // ← 没有 Storage 接口
```
- `file/controller/FileController.java:42-45` 构造注入**具体类** `LocalFileStorage`；
- `common/storage/` 目录下只有这一个文件，无接口。

所谓"替换本类即可"在编译期并不成立：调用方已依赖具体类型，切 OSS 时要么改调用方，要么让新类继承旧类（伪适配）。**这是本仓最典型的"注释承诺 > 代码事实"**（详见 R8）。

---

### 1.1.5 观察者模式 — 领域事件（☆☆☆☆☆ 空转）

**这是本次审计最重要的发现，必须单独强调。**

```java
// common/event/LoggingDomainEventPublisher.java:16-19
@Override
public void publish(Object event) {
    log.info("[DOMAIN-EVENT] {}", event == null ? "null" : event.getClass().getSimpleName());
}
```

- 接口存在：`common/event/DomainEventPublisher.java:7-9`；
- 实现只有一行日志，**没有任何分发逻辑**；
- 全仓 `@EventListener` / `ApplicationListener` / `@TransactionalEventListener` = **0**；
- 全仓 `@Async` / `@EnableAsync` = **0**（`common/config/` 下只有 `ScheduleConfig` 单线程调度器）。

**8 个事件类 / 7 个发布点 / 0 个订阅者**：

| 事件类 | 发布点 | 订阅者 |
|---|---|---|
| `auth/event/LoginAuditEvent.java:6` | `auth/service/LoginUseCase.java:91` | 无 → 登录审计从未落库 |
| `order/event/OrderCreatedEvent.java:6` | `order/service/CreateOrderUseCase.java:159` | 无 |
| `order/event/OrderPaidEvent.java:6` | `order/service/impl/OrderPaymentServiceImpl.java:58` | 无 |
| `order/event/ShipEvent.java:6` | `order/service/ShipOrderUseCase.java:83` | 无 |
| `stock/event/StockInboundEvent.java:7` | `stock/service/InboundUseCase.java:55` | 无 |
| `stock/event/StockLockedEvent.java:6` | **从未发布**（无 `new StockLockedEvent`） | 无 |
| `member/event/MemberRewardEvent.java:7` | `member/service/impl/MemberRewardServiceImpl.java:32` | 无 |
| `member/event/MemberRefundEvent.java:7` | `member/service/impl/MemberRefundGatewayImpl.java:23` | 无 → **退款不退积分** |

**危害等级**：这不只是"模式没用起来"。`LoginAuditEvent.java:4` 注释写"由审计模块监听持久化"，`RefundCallbackUseCase` 把"调 `MemberRefundGateway`"当作"积分已回退"的完成步骤——**事件一丢，业务流程照常提交，账面看起来成功，实际权益没有回退**。这属于可通过代码指认的功能缺陷（详见 R4）。

---

### 1.1.6 单例模式（Spring Bean）（★★★★☆）

| 类型 | 代表 | 线程安全评估 |
|---|---|---|
| 无状态单例（安全） | 绝大多数 `@Service`，构造注入 `final` 依赖：`PricingServiceFacadeImpl.java:27-32`、`OrderPaymentServiceImpl.java:26-35` | ✅ 无可变字段 |
| 有状态单例 | `InMemoryIdempotencyStore.java:22`（`ConcurrentHashMap`）、`InMemoryRateLimiter.java:19,34-43`（`Map<String, Deque<Long>>` + `synchronized`）、`chat/ws/InMemorySessionBroadcaster.java:34-36` | ⚠️ 并发安全，但 **key 永不回收 → 内存泄漏** |
| 静态单例 | `OrderStateMachine.java:16`（`static final EnumMap`） | ✅ 静态初始化后只读 |
| 无状态工具 | `LocalFileStorage.java:34`（`final Path` / `final Clock`） | ✅ |

**唯一实质隐患**：`InMemoryRateLimiter` 的窗口 key（IP / 用户 × 路径）只增不减，长期运行会持续占用内存；`InMemoryIdempotencyStore` 靠 TTL 惰性清理，命中率低时同样堆积。生产环境应换 Redis 或加 `Caffeine` 上限。

---

### 1.1.7 其他真实存在的模式

| 模式 | 证据 | 评语 |
|---|---|---|
| **仓储 Repository（依赖倒置）** | 22 个接口 + 24 个实现；`order/repository/OrderRepository.java:10`、`OrderRepositoryImpl.java`（含领域↔PO 转换与乐观锁）；`common/mybatis/BasePO.java:17` 统一审计字段 | ★★★★☆ 落地扎实 |
| **门面 Facade** | `product/service/PricingServiceFacade.java:10` + `impl:25-56`；`member/service/PointFacade.java:7` + `impl:11-16`；`finance/service/CreditFacade.java` | ★★★★☆ 对上层屏蔽领域组装 |
| **模板方法（回调式，非继承）** | `common/service/BatchDeleteExecutor.java:12,18,23`：固定算法骨架 + `Function<List<Long>,Integer>` 回调，8 处复用（`CustomerBatchDeleteUseCase.java:22` 等） | ★★★★☆ 用组合代替继承，比 GoF 原版更合适 |
| **建造者 Builder** | `product/domain/pricing/PriceContext.java:26,85-141`（`ContextBuilder` 11 个链式方法）— **全仓唯一 Builder** | ★★★☆☆ 用对了地方（11 参数构造） |
| **责任链** | `auth/filter/AuthFilter.java:35,53-104` + `SecurityConfig.java:44` `addFilterAfter` | ★★☆☆☆ 只有一环，是 Servlet 规范链而非 GoF 责任链族 |
| **统一异常策略** | `common/error/ErrorCode.java:9-50`（枚举内含 httpStatus）+ `BizException` + `GlobalExceptionHandler`（`@RestControllerAdvice`） | ★★★★★ |
| **调度** | `job/` 下 10 个 `@Scheduled` Job（`OrderTimeoutJob.java:42`、`StockWarningJob.java:34` 等）+ `common/config/ScheduleConfig.java:24-31` | ★★☆☆☆ **无基类、无模板、无分布式锁**，见 R11 |

---

## 1.2 前端

### 1.2.1 外观模式（Facade）— 需要纠正一个前提（★★★★☆）

**任务描述假设存在 `useTable`，实际不存在。** 复核：`grep -rn "useTable" web-admin/src web-user/src packages/shared/src` → **零命中**。真实的门面只有 5 个：

| 门面 | 文件:行 | 封装了什么 | 调用方 |
|---|---|---|---|
| `usePagination` | `web-admin/src/composables/usePagination.ts:46-72` | **仅**页码/条数 + 按 `route.path` 存 sessionStorage（`:51,58-68`） | `customer/list.vue:141`、`finance/payment-record.vue:328`、`finance/contract.vue:83` |
| `useBatchDelete` | `web-admin/src/composables/useBatchDelete.ts:12-31` | 删除态 + 勾选 + 退出清空（`watch(deleteMode)`） | 10 处：`finance/invoice.vue:187`、`custom/list.vue:103`、`payment-record.vue:243/288` |
| `useChatSocket` | `web-admin/src/composables/useChatSocket.ts:86-238`<br>`web-user/src/composables/useChatSocket.ts:87-229` | WS 建连、指数退避（`:156`）、信封解析（`:94`）、幂等键（`:208`） | `chat/workbench.vue:548`、`web-user chat/index.vue:237` |
| `useChatSession` | `web-user/src/composables/useChatSession.ts:52→374` | 再包一层：REST 建会话 + WS 收发 + 兜底，页面只依赖 `{messages, sessionId, online, start, stop, send, refresh}` | `web-user chat/index.vue` |
| `createIdempotencyScope` | `web-admin/src/utils/idempotent.ts:36-55`（`:61 useIdempotencyKey`、`:68 withIdempotency`） | 后端 `Idempotency-Key` 协议（8~64 位） | `checkout/index.vue:238-239`、`pay/index.vue:107` |

**评语**：门面边界是干净的——页面不碰 WebSocket、不碰幂等键、不碰分页存储。**但 `usePagination` 只封装了状态，没封装请求**，于是"加载/翻页/重置"这套真正的重复逻辑流落到了 25 个页面（见 F1）。这正是"外观模式只做了一半"的典型症状。

---

### 1.2.2 代理模式 — Axios 拦截器（★★★★★ 全仓最高分）

`packages/shared/src/http.ts` 的 `PureHttp` 代理了 5 类横切关注点，双端只注入差异（`web-admin/src/utils/http/index.ts:18-33`、`web-user/src/utils/http.ts`）。

| 代理点 | 行号 | 做了什么 |
|---|---|---|
| 请求拦截（注入 Token + 预判续期） | `:398-415` | `skipAuth` / 白名单跳过；`isAccessTokenExpired(0)` 时先 `refreshSingleFlight()` 再塞 `Authorization` |
| 响应拦截（统一错误码分流） | `:444-455` / `:457-476` | 两分支同解析 `body.code`（后端用 HTTP 401 + body.code 双通道） |
| 401 单飞刷新 + 重放 | `:478-490` / `:493-519` | `_retry` 防循环、**原样重放 `Idempotency-Key` 与 `X-Confirm-Token`**、`this.refreshing` 等待队列 + 独立裸 `rawHttp` 防递归（`:381`） |
| 静默续期 | `:423-429` + `createTokenStorage.setAccessTokenIfNewer:183-197` | 读 `X-New-Token`，**只接受 exp 更大者**（防旧盖新） |
| Token 存储 | `createTokenStorage:102-267` | 前缀隔离、SSR/隐私模式降级内存、`onTokenChanged:260` 广播 |
| 基础设施错误提示 | `notify:438-442` | 40301/42901/50001 走 `onError` 钩子，错误对象统一 `HttpError:274` |

**评语**：这是全仓工程质量最高的一层——注释即契约，双端零重复。特别是"重放时原样带回幂等键与确认令牌"这一点，很多团队都会漏，漏了就会在刷新 token 后产生重复下单。

---

### 1.2.3 策略模式 — 状态字典映射（★★★★☆）

**实现方式是 Record 查表 + 纯函数，不是 if-else 链**——这点做得对：

```ts
// packages/shared/src/enums.ts:109-134
export const STATUS_LABELS: Record<string, string> = { ... }
// enums.ts:136-148
export const TONE_MAP: Record<string, StatusTone> = { ... }
// enums.ts:150-156
export function statusLabel(status) { return (status && STATUS_LABELS[status]) || status || '-' }
export function statusTone(status)  { return (status && TONE_MAP[status]) || 'info' }
```

唯一渲染入口 `web-admin/src/components/StatusTag.vue:21-30`（`props.label ?? statusLabel(...)` + `TONES[props.tone ?? statusTone(...)]`），25 个 view 引用。

**缺口**：`web-user/src/components/` **没有 `StatusTag`**，用户端只能回到手写（见 F3）；并且 7 个页面自建了同名或同义的字典函数，直接绕过了这张策略表。

---

### 1.2.4 观察者模式 — Pinia（★★★★☆）

| 应用 | Store | 联动机制 |
|---|---|---|
| admin | `store/modules/useAuthStore.ts:25` | `revision` ref 订阅 `onTokenChanged`（`:29-37`），`accessToken` 等全部 `computed(() => { void revision.value; ... })` —— **用 revision 计数器强制响应式刷新 token，是很聪明的做法** |
| admin | `store/modules/permission.ts:13` | `menus: computed` 依赖 `permissions`，`load()` 后自动重算 |
| user | `store/modules/useAuthStore.ts:28` | 同上，另加 `userId: computed` 从 JWT `sub` 解 |
| user | `store/modules/useCartStore.ts:19-74` | `totalQty` / `selectedItems` 派生，`remove()` 同步改 `items` + `selectedIds` |
| user | `store/modules/useMessageStore.ts:20-52` | **未读数唯一真源** |

**最佳实践证据（未读数单源驱动双 UI）**：

```vue
<!-- web-user/src/layout/index.vue:44 -->
消息<i v-if="msg.unreadCount > 0" class="msg-badge">{{ msg.unreadCount > 99 ? '99+' : msg.unreadCount }}</i>
```
同一 ref 同时驱动顶栏徽标（`layout/index.vue:156`）与消息页（`views/message/index.vue:18-19`），标记已读后 `refresh()` 两处同刷——这是 Pinia 用得最标准的场景，比"每个页面各自请求未读数"高一个档次。

---

### 1.2.5 前端其他模式

| 模式 | 证据 | 说明 |
|---|---|---|
| **命令模式** | `utils/confirm.ts:15-24`（`CONFIRM_ACTIONS` 短语表）+ `:80-110 openConfirm`（弹窗 → 换 `X-Confirm-Token` → 返回 `grant.headers`）；调用方 `order/list.vue:171-176` | 把"二次确认"封装成可携带令牌的命令对象 |
| **模板方法** | `components/SentTable.vue:1-88` 固定骨架，页面只填 `toolbar` / `col-{prop}` / `operations` / `empty` 插槽（`:5,51,64,68`） | 表格骨架复用做得好 |
| **适配器** | `createSentHttp`（差异仅 baseURL/storage/onLogout）；`utils/auth.ts` 双端同构（换 prefix/aud/publicPath）；`api/order.ts` 管理端 VO 与用户端 VO 各自适配同一后端 | 适配层清晰 |
| **命令回调注入** | `components/BatchDeleteButton.vue:30-50` 把 `deleteFn` 作为命令注入 | 比在每个页面写 `if (deleting)` 好 |

---

## 1.3 模式应用矩阵

| 模式 | auth | order | payment | refund | coupon | product | stock | chat | finance | 前端 |
|---|---|---|---|---|---|---|---|---|---|---|
| 策略 | | | | | △ | ★ | | | | ★ |
| 状态机 | | ★ | ★ | ✗ | | | | △ | | |
| 代理/切面 | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ |
| 适配器 | | ★ | ★ | ★ | | | | | | ★ |
| 观察者 | ✗ | ✗ | ✗ | ✗ | | | ✗ | △ | | ★ |
| 工厂/建造者 | | △ | △ | | | ★ | | | | |
| 门面 | | | | | | ★ | | ★ | ★ | ★ |
| 仓储 | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ | |

> ★ 落地良好 ／ △ 局部使用 ／ ✗ 有接口无闭环

---

# 维度二 · 缺失与重构审计

> 编号规则：**R** = 后端（Refactor），**F** = 前端（Frontend）。严重度按"是否影响钱/权/数据一致性"排序，而非按代码美观度。

## 2.1 后端：本该用模式，却写死/复制的地方

### R1【高】自动确认收货绕过用例，漏发积分、成长值、应收台账

**位置**：`job/AutoConfirmJob.java:44-69`

```java
// AutoConfirmJob.java:57-64
int rows = orderMapper.transitStatus(order.getOrderNo(), FROM, TO);
if (rows > 0) { confirmed++; }
```
对照用户主动确认收货：
```java
// order/service/ConfirmReceiptUseCase.java:69-77
if (allReceived) {
    order.setStatus(OrderStatus.COMPLETED);
    memberRewardService.grantOrderCompleted(order.getCustomerId(), order.getOrderNo(), order.getPayableCent());
    orderSettlementService.onOrderCompleted(order.getOrderNo(), order.getCustomerId(), order.getPayableCent());
}
```

**问题本质**：Job 直连 `OrderMapper` 做条件更新，**只推进状态，不触发任何下游副作用**。7 天未确认的订单，用户拿不到成长值/积分，财务拿不到应收台账，企业授信不会占用。这不是"代码不优雅"，是**按业务规则必然发生的资损**（且因为幂等键 `uk_receivable_order`，事后补数还要人工）。

**为什么该用模式**：这是典型的"同一业务动作存在两个入口"——应该由**用例（Command）作为唯一入口**，Job 只负责"发现到期项并调用用例"，而不是自己实现动作。模式上属于**命令模式 + 模板方法**：`ScheduledJob` 只做「扫描 → 逐条调 command → 汇总」，动作委托给用例。

**重构方案**：
1. `AutoConfirmJob` 改为注入 `ConfirmReceiptUseCase`，逐单调用 `confirm(new ConfirmReceiptCmd(orderNo, null))`（全量收货，`lines=null` 已在 `:55-62` 分支支持）；
2. 抽 `abstract class ScheduledBatchJob<T>`：`protected abstract List<T> scan(); protected abstract boolean handle(T item); protected int runOnce()`，10 个 Job 统一继承（顺带解决 R11）；
3. 验收：新增集成测试"Job 自动确认后 `member_point_flow` 与 `fin_receivable` 各有一行"。

---

### R2【高】已有状态机，却在发货/收货手写状态白名单，与状态机表冲突

**位置**：`order/service/ConfirmReceiptUseCase.java:28,45,70`、`order/service/ShipOrderUseCase.java:31,53,71`

```java
// ConfirmReceiptUseCase.java:28
private static final List<OrderStatus> RECEIVABLE = List.of(OrderStatus.WAIT_RECEIVE);
// ConfirmReceiptUseCase.java:45
if (!RECEIVABLE.contains(order.getStatus())) { throw new BizException(STATE_CONFLICT, "订单当前状态不可确认收货"); }
// ConfirmReceiptUseCase.java:70
order.setStatus(OrderStatus.COMPLETED);
```
而状态机明确写了 `PART_SHIPPED --CONFIRM_RECEIVE--> COMPLETED`（`OrderStateMachine.java:32`）。

**问题本质**：同一个动作有两套准入答案——状态机说"部分发货也能确认收货"，用例白名单说"只有待收货可以"。这类分歧最终表现为"前端按钮可点、后端 40901"或反之的幽灵 bug。

**为什么该用模式**：状态机已经存在且正确，问题是**没把它当成唯一裁决者**。这不是缺模式，是**模式的权威性被旁路**（违反"单一真源"）。

**重构方案**：
1. `ConfirmReceiptUseCase`：删除 `RECEIVABLE`，改为 `order.transit(OrderAction.CONFIRM_RECEIVE)`；部分收货场景由"是否全收"决定是否 transit（`allReceived` 已有）；
2. `ShipOrderUseCase`：删除 `SHIPPABLE`，按 `shippedQty` 选 `SHIP_PARTIAL / SHIP_CONTINUE / SHIP_FULL`，交给 `order.transit(...)`，删除 `ShipmentProgress.recalc` 直接赋值的写法；
3. `Order.setStatus` 降级为 `package-private`（同包仅 `Order`/`OrderRepositoryImpl` 可用），让编译期阻止新旁路；
4. 补 ArchUnit：`noClasses().that().resideOutsideOfPackage("..order.domain..").should().callMethod(Order.class, "setStatus", OrderStatus.class)`。

---

### R3【高】退款审核用 `switch(String)` + 三份重复守卫，退款模块无状态机

**位置**：`refund/service/RefundAuditUseCase.java:50-55`、`:61-63`、`:69-71`、`:101-103`

```java
switch (cmd.action()) {
    case "APPROVE" -> approve(order);
    case "REJECT"  -> reject(order, cmd.reason());
    case "APPEAL"  -> appeal(order);
    default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知的审核动作");
}
...
if (order.getStatus() != RefundStatus.PENDING) { throw ... "仅待审核退款单可批准"; }   // :61
if (order.getStatus() != RefundStatus.PENDING) { throw ... "仅待审核退款单可驳回"; }   // :69
if (order.getStatus() != RefundStatus.CLOSED)  { throw ... "仅被驳回的退款单可申诉"; } // :101
```

**问题本质**：`RefundStatus`（`refund/domain/RefundStatus.java:4-9`）只有 5 个枚举值，**没有迁移表、没有 `transit`**；状态准入被切成 3 份各自为政的 if，错误文案散落；动作是**魔法字符串**（`AdminRefundController.java:66` 还用布尔 `approved` 反推 `"PASS"`）。同一仓库里 `OrderStateMachine`/`CustomStateMachine` 已经是标准答案，退款却退回了命令式写法。

**为什么该用模式**：状态机 + 策略分派。新增"部分退款/转人工"时，当前实现要改 switch + 写新守卫 + 改文案三处；模式化后只加 1 个枚举值 + 1 行迁移。

**重构方案**：
1. 建 `refund/domain/RefundAction`（`APPROVE/REJECT/APPEAL`）与 `RefundStateMachine`，照抄 `CustomStateMachine.java:16-57` 的 `EnumMap<from, EnumMap<action,to>>`；
2. `RefundOrder` 增加 `transit(RefundAction)`，用 `Map<RefundAction, Consumer<RefundOrder>> HANDLERS` 注册差异动作（驳回额外写 `auditRemark`、申诉额外校验次数）；
3. Cmd 里 `action` 改枚举类型（Jackson 自动 400 非法值），Controller 删掉 `approved` 布尔转换；
4. 验收：新增"非法迁移统一抛 40901"的参数化测试，覆盖 5 状态 × 3 动作矩阵。

---

### R4【高】领域事件是空壳，导致退款不退积分（功能性缺失）

**位置**：`common/event/LoggingDomainEventPublisher.java:16-19`；`member/service/impl/MemberRefundGatewayImpl.java:22-24`

```java
// MemberRefundGatewayImpl.java:22-24
public void refundPoints(long customerId, long amountCent) {
    eventPublisher.publish(new MemberRefundEvent(customerId, amountCent));   // 只打日志
}
```
调用方 `refund/service/RefundCallbackUseCase.java:58-92` 把它当成"积分已回退"的完成步骤继续往下走。

**问题本质**：用"发事件"规避跨模块依赖，但既没有分发（无 `ApplicationEventPublisher`），也没有异步（无 `@EnableAsync`），**解耦收益为 0，可靠性反而降低**——同步调用至少会抛异常，事件发出去连异常都没有。

**为什么该用模式**：观察者模式要成立，必须满足三件事：**有订阅者、有事务边界语义（`AFTER_COMMIT`）、有失败可观测**。当前三者皆无。

**重构方案（二选一，不要停在中间态）**：
- **方案 A（推荐，需要真解耦）**：实现 `SpringDomainEventPublisher implements DomainEventPublisher`，委托 `ApplicationEventPublisher`；`common/config/AsyncConfig` 开 `@EnableAsync` + 独立 `notificationExecutor`（核心 4 / 队列 500 / `CallerRunsPolicy`）；监听方用 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`；`MemberRefundEvent` 监听者改调 `PointFacade` 真实回退，并写 `event_consume_log` 保证可重放。
- **方案 B（推荐，若暂无异步诉求）**：**删掉 `DomainEventPublisher` 与 8 个事件类**，`MemberRefundGatewayImpl` 直接调 `PointFacade.refund(...)`。同步、可见、可回滚。假接口比没有接口更危险。

**判定标准**：如果 3 个月内不打算做异步/审计落库，选 B。当前代码处于"已付出接口成本、未获得任何收益"的最差象限。

---

### R5【高】退款 Saga 硬编码在用例里，外部支付网关调用落在 DB 事务内

**位置**：`order/service/CloseOrderUseCase.java:92-106`、`refund/service/RefundCallbackUseCase.java:58-92`

```java
// CloseOrderUseCase.java:92-106（@UseCase 事务内）
orderMapper.closeOnAdminRefund(...);                                  // 92
String refundNo = refundService.createCloseRefund(...);               // 97 → PaymentRefundGatewayImpl:41 payGateway.refund() 外部 HTTP
stockRepository.releaseByOrderNo(orderNo);                            // 102 跨模块仓储
creditFacade.releaseCreditForOrder(orderNo);                          // 105
systemService.recordLog(...);                                         // 106
```
退款回调侧：
```java
// RefundCallbackUseCase.java:58-64
try { paymentRefundGateway.markRefunded(orderNo); }
catch (BizException e) { refundOrder.setStatus(RefundStatus.FAILED); refundRepository.save(refundOrder); return; }  // 吞异常后继续
```

**问题本质**：三段式问题叠加——
1. **事务包住外部调用**：`payGateway.refund()` 成功但后续 DB 回滚 = 钱退了、单没关；
2. **补偿写成 try-catch**：`FAILED` 状态把"渠道失败"与"副作用待补"混为一谈；
3. **无 Saga 建模**：5 个 Gateway（payment/stock/order/member/customize）的调用顺序、失败语义、可重试性，全靠阅读用例代码才能知道。

**为什么该用模式**：这是标准的**Saga（编排式）+ 模板方法**场景——步骤表 + 每步幂等键 + 每步失败动作 + 统一重试。

**重构方案**：
1. 建 `refund/saga/RefundSaga`：`List<SagaStep>`（`name / action / compensate / retryable`），步骤顺序显式声明；
2. 事务边界改为「先落 `REFUNDING` + 写 `refund_outbox`（同事务）→ 提交 → 事务外逐条执行网关调用 → 每步结果写 `refund_step_log`」；
3. 失败由 `RefundRetryJob` 按 `refund_step_log` 重放，`FAILED` 细分为 `CHANNEL_FAILED` / `SIDE_EFFECT_PENDING`；
4. 外部网关调用加 `@Transactional(propagation = NOT_SUPPORTED)` 兜底，防止后人再把它塞回事务；
5. 验收：集成测试模拟 `payGateway.refund` 抛异常，断言订单仍为原状态且 outbox 有待办。

---

### R6【高】定制费 `1000L` 硬编码两份，唯一计费引擎是死代码

**位置**：
- `cart/service/CartQuoteUseCase.java:51-55`：`lineCustomFee = 1000L * item.qty();`
- `order/service/CreateOrderUseCase.java:55,114-116`：`CUSTOM_FEE_PER_UNIT_CENT = 1000L`
- `customize/domain/CustomFeeCalculator.java:16-21`：面数单价 × 阶梯折扣的完整引擎

**复核结论**：`grep -rn "CustomFeeCalculator"` 全仓只在 `src/test/.../CustomFeeCalculatorTest.java` 出现——**生产链路零调用**。

**问题本质**：`CustomFeeConfig` 里精心设计的 SINGLE/DOUBLE/FULL 三档单价与数量阶梯（10/50 件折扣）全部成了摆设；"10 元/件"被复制成字面量与常量两个版本。运营要按面数计价或调价，必须改 2 个模块 + 重新发版；且 Cart 与 Order 一旦不同步，就会出现**购物车试算价比实付便宜**（用户投诉高发点）。

**为什么该用模式**：策略/领域服务已存在且测试完备，问题在于**用例绕过了领域**（贫血 vs 充血的选择没有贯彻）。

**重构方案**：
1. `CustomFeeConfig` 由 `app.custom-tier.*` 配置装配成 Bean（`@ConfigurationProperties`）；
2. 两个 UseCase 注入 `CustomFeeCalculator`（改为 Spring Bean，或保留静态 + 传 config），删除 `1000L` 与 `CUSTOM_FEE_PER_UNIT_CENT`；
3. `OrderLineCmd.customFlag` 升级为 `faceSide`（默认 `SINGLE`），前端定制页按面数传值；
4. 验收：调价只改 yml；新增单测"Cart 报价 == CreateOrder 落库金额"（同一 Config 下）。

---

### R7【中高】券型规则散落 8 处 `if`，新增券型要改 4 个类

**位置**：`coupon/service/CouponTemplateAdminUseCase.java:80-98,111-112`、`coupon/service/MemberDayPolicy.java:47-54`、`coupon/domain/Coupon.java:122`、`coupon/service/CouponUseCase.java:50-63`、`coupon/service/impl/CouponQueryServiceImpl.java:65-78`

```java
// CouponTemplateAdminUseCase.java:80-82
if (Coupon.TYPE_CATEGORY.equals(couponType)) {
    if (Coupon.SCOPE_ALL.equals(scopeType) || scopeIds == null) { throw ... "品类券必须选择适用商品"; }
}
// :111-112
po.setCustomerScope(Coupon.TYPE_ENTERPRISE.equals(couponType) ? Coupon.CUSTOMER_ENTERPRISE : Coupon.CUSTOMER_ALL);
```
复核：`grep TYPE_NO_THRESHOLD|TYPE_FULL_REDUCTION|TYPE_CATEGORY|TYPE_ENTERPRISE` 在 domain 之外共 **8 处**。

**问题本质**：券型的 5 条规则（必须选范围 / 必须有门槛 / 专属客户域 / 领券有效期 / 抵扣算法）被拆到 5 个 if 分支，**无编译期约束**。漏一处就发出"永远抵 0"的券——`CouponAssembler.java:7-8` 的注释正是为这类事故写的。

**为什么该用模式**：券型是天然的**枚举 + 抽象方法**（策略族）。`CouponType` 枚举承载 `validate(cmd)` / `applyDefaults(po)` / `validDays(policy)` / `discountFor(eligible, rate)`。

**重构方案**：
1. 建 `coupon/domain/CouponType` 枚举（4 值），每值实现 4 个方法；`Map<String, CouponType> BY_CODE` 注册；
2. `CouponTemplateAdminUseCase` 的 3 段校验收敛为 `CouponType.of(couponType).validate(cmd)`；
3. `MemberDayPolicy.claimValidDays` 委托 `type.validDays(...)`；
4. `CouponUseCase` 与 `CouponQueryServiceImpl` 的"是否可用"统一走 R9 的领域方法（两者必须同时改，否则列表与下单继续分歧）。

---

### R8【中高】文件存储没有接口，注释承诺"替换本类即可"不成立

**位置**：`common/storage/LocalFileStorage.java:27-29`、`file/controller/FileController.java:42-45`、`file/controller/EnterpriseLicenseUploadController.java:44-59`

```java
public class LocalFileStorage {                     // 无 interface
    public String store(byte[] bytes, String originalName) { ... }
}
// FileController.java:42
public FileController(LocalFileStorage storage) { ... }   // 依赖具体类
```

**问题本质**：这不是适配器，是"具体类 + 注释"。切对象存储时，调用方已编译期耦合。同时两个上传端点重复"校验 → 落盘 → 组 VO"，且**匿名通用口子比登录的证件口子校验更松**（通用口无类型/大小限制）。

**为什么该用模式**：适配器 + 策略（上传策略）。`Storage` 接口 + `LocalStorage` / `OssStorage` 实现 + `UploadPolicy` 枚举承载差异校验。

**重构方案**：
1. `common/storage/Storage` 接口：`String store(byte[], String)` / `byte[] load(String)` / `void delete(String)`；
2. `LocalFileStorage implements Storage`，`FileController` 改注入接口；
3. 建 `file/service/FileUploadService.upload(MultipartFile, UploadPolicy)`，`UploadPolicy`（`GENERAL` / `LICENSE`：maxSize + contentType + validator）；
4. 验收：`LicenseImageValidator` 的 2MB 限制与白名单对两个端点同时生效。

---

### R9【中】券"是否可用于该客户/该金额"两份实现，列表说可用、下单抵 0

**位置**：`coupon/service/CouponUseCase.java:50-63` vs `coupon/service/impl/CouponQueryServiceImpl.java:65-78`

```java
// CouponUseCase.java:50-53
if (coupon.isEnterpriseOnly() && !CUSTOMER_ENTERPRISE.equals(customerQueryService.typeOf(customerId))) { return 0L; }
// CouponQueryServiceImpl.java:65-78 又把同一套「企业券排除 → usableFor → scope 命中」写成 .filter 三段
```
另：`CUSTOMER_ENTERPRISE` 在 `CouponUseCase.java:30`、`CouponQueryServiceImpl.java:30` 各声明一次，而 `Coupon.CUSTOMER_ENTERPRISE`（`domain/Coupon.java:36`）已存在。

**问题本质**：两处入参粒度已经不同（`goodsCent` vs `List<CouponLine>`），判定逻辑靠人肉保持一致。这是"列表页显示可用、结算时抵 0 元"的根因。

**重构方案**：`Coupon.applicability(customerType, lines, now) → Eligibility{usable, eligibleCent, discount}`；两处只做 VO 映射；常量统一引用 `Coupon.CUSTOMER_ENTERPRISE`。

---

### R10【中】14 个 Controller 整段复制确认令牌校验（安全校验的复制粘贴）

**位置**：`refund/controller/admin/AdminRefundBatchDeleteController.java:39-44` 及另外 13 个文件（复核：`grep -rln "confirmTokenService.consume"` = **14**）

```java
String targetId = BatchIds.targetId(cmd.ids());
if (confirmToken == null || confirmToken.isBlank()
        || !confirmTokenService.consume(ConfirmAction.BATCH_DELETE.name(), targetId, confirmToken)) {
    throw new BizException(ErrorCode.CONFIRM_TOKEN_INVALID);
}
return R.ok(new BatchDeleteVO(deleteUseCase.delete(cmd.ids())));
```

**问题本质**：这是**安全校验**的复制粘贴。将来加"令牌绑定操作人""非 GET 强制二次确认"，14 处漏 1 处就是越权删除入口。`AdminStockTableBatchDeleteController.java:57-63` 已抽了私有 `execute(...)`，但仍是复制品。

**为什么该用模式**：横切关注点就该用切面——项目已有 `IdempotentInterceptor` 这个现成范式。

**重构方案**：`@Confirm(action = ConfirmAction.BATCH_DELETE)` 注解 + `ConfirmTokenInterceptor`（比 AOP 更合适，因为需要读 Header）；Controller 方法签名只保留业务 DTO；`BatchIds.targetId()` 由拦截器计算并塞进 request attribute。

---

### R11【中】10 个定时任务直连他模块 Mapper，ArchUnit 规则存在两个盲区

**位置**：`job/AutoConfirmJob.java:4-5,35`（order）、`ChatTimeoutJob.java:43`、`OverdueJob.java:28`（finance）、`StockWarningJob.java:25`、`ContractRemindJob.java:30`、`PointExpireJob.java:31`、`SlaRemindJob.java:25-26`、`PresenceReclaimJob.java:33`

**盲区证据**：`server/src/test/java/com/sent/sports/ArchitectureTest.java:80-110`

```java
ArchRule rule = noClasses()
        .that().resideInAPackage(self + "..")
        .and().resideInAnyPackage("..controller..", "..service..", "..event..")   // ← job 不在列
        .should().dependOnClassesThat()
        .resideInAnyPackage(otherModuleForbiddenPackages(module));               // ← 只有 mapper/domain/controller
```
`otherModuleForbiddenPackages` 返回 `mapper` / `domain` / `controller` 三类，**`repository` 不在其中**。于是 `order/service/CreateOrderUseCase.java:28`、`CloseOrderUseCase.java:18` 直接 `import com.sent.sports.stock.repository.StockRepository` 全部合法通过——铁律"跨模块只经 service 接口"只写在注释里（`CloseOrderUseCase.java:33-34` 甚至自称合规）。

**重构方案**：
1. `otherModuleForbiddenPackages` 增补 `base + "..repository.."`；
2. 规则作用域增补 `"..job.."`；
3. stock 暴露 `StockService`（`lock` / `release` / `releaseByOrderNo`），order 三个用例改依赖接口；
4. Job 一律「本模块 mapper 扫描 + 调目标模块 UseCase」（与 R1 合并实施）。

---

### R12【中】会员等级两套互不相容的模型 + 第三份字符串白名单

**位置**：`member/domain/MemberLevel.java:9-11`（0/2000/10000）、`member/domain/MemberTier.java:18-21`（0/1000/5000/20000）、`customer/service/impl/CustomerOperationServiceImpl.java:26`（`Set.of("NORMAL","LV1","LV2","LV3")`）

```java
// MemberTier.java:8 注释自认
* <p>与早期内存档位 MemberLevel（NORMAL/VIP1/VIP2，门槛 0/2000/10000）不是同一套，
```

**问题本质**：同一业务概念两套门槛表 + 第三份白名单；`LevelCalculator.java:12` 与 `MemberTier.calc:40` 是同一算法的两次实现。矛盾数据（成长值 16167 却是 LV1）就来自这里。

**重构方案**：删 `MemberLevel` + `LevelCalculator`（仅 `Member.java:36,50`、`AdminCustomerDtos.java:32` 引用），全链路用 `MemberTier`；`LEVEL_CODES` 改为 `Arrays.stream(MemberTier.values()).map(MemberTier::getCode).collect(...)`。

---

### R13【中】VO 位置参数爆炸（27 / 30 个参数），该用 Assembler + Builder

**位置**：`order/dto/OrderDtos.java:74-96`（`OrderVO` 27 字段）→ `order/service/impl/OrderQueryServiceImpl.java:128-148` 一次塞 27 个实参；`refund/dto/RefundDtos.java:97-108`（`AuditOrderVO` 30 参数）→ `refund/service/impl/RefundQueryServiceImpl.java:120-150`

```java
return new OrderDtos.OrderVO(
        o.getOrderNo(), o.getStatus(), o.getPayStatus(), null, o.getAfterSaleStatus(),
        o.getPayableCent(), MoneyUtil.toYuan(nonNull(o.getPayableCent())), ... 共 27 个);
```
第 4 个参数是 `null`（实为 `shipStatus`），`OrderVO` 已被迫写 3 个重载构造器兜底（`:100`、`:110`）。

**问题本质**：位置参数超过 5 个就退化成"哑数组"；加字段 = 全调用点编译失败，`null,null,null` 无法自解释。这不是"能不能跑"的问题，是可读性与变更成本。

**重构方案**：`OrderVOAssembler`（PO + payment + shipment → VO 的唯一装配点）；VO 改 `@Builder`（record 用 builder 辅助类）；或拆 `OrderBaseVO + OrderAmountVO + OrderLogisticsVO` 组合。

---

### R14【低中】PO↔领域转换 9 个仓储各写一份；券 PO 三处手工装配

**位置**：`order/repository/OrderRepositoryImpl.java:94-121`（`toPO` 26 次 set）、`:151-164`；`payment/repository/impl/DbPaymentRepository.java:74/87`；`refund/repository/impl/RefundRepositoryImpl.java:52/70`；`chat/repository/ChatSessionRepositoryImpl.java:117/127`；`CouponClaimUseCase.java:70-91`（17 次 set）、`CouponIssueUseCase.java:51-68`、`RegisterCouponGrantUseCase.java:44-60`

```java
// OrderRepositoryImpl.java:143 注释本身在提示风险
// 回读时必须带出分摊值：后续发货/收货刷新订单行，不能把券抵扣分摊冲成 0
item.setDiscountAllocCent(po.getDiscountAllocCent() == null ? 0L : po.getDiscountAllocCent());
```
`CouponAssembler.java:15` 只有 `toDomain`，**没有 `toPO`**——注释（:7-8）明令"不允许各写一份"，但反向映射被复制了 3 份，且三处对 `thresholdCent/claimKey/reason` 取值规则已各不相同。

**重构方案**：每模块一个 `XxxAssembler`（静态 `toDomain/toPO`，或 MapStruct）；`CouponAssembler.toPO(template, customerId, couponNo, source, expireAt, thresholdOverride)` 单一工厂；仓储只留 IO 与 SQL 兜底。

---

### R15【低中】`blank → null` 归一化 11 份、手机号正则 5 份、权限码 60+ 裸字符串

**位置**：
- 归一化：`CustomerQueryServiceImpl.java:36-38`、`CustomAdminQueryServiceImpl:35`、`OrderAdminServiceImpl:104,109`、`StockQueryServiceImpl:61`、`FinanceQueryServiceImpl:70,155`、`RefundQueryServiceImpl:71` 等 **11 处**
- 手机号正则 `^1[3-9]\d{9}$`：`SendSmsCodeUseCase.java:25`、`ResetPasswordUseCase.java:23`、`RegisterUseCase.java:17`、`LoginUseCase.java:39`、`EnterpriseRegisterUseCase.java:22`（**5 份**）
- 权限码：`@RequirePerm` **123 处**、30+ 不同字面量，无常量类；且券管理 6 个端点共用 `customer:grant`（`AdminCouponController.java:48,55,65,74,81,91`）——**语义错配：拥有"客户授级"权限的人可以自建并全量发券**

**重构方案**：
1. `common/util/Texts`（`nullIfBlank` / `upperOrNull`）+ `common/validation/Patterns.PHONE`；
2. `common/security/Perm` 常量类 + ArchUnit 断言"注解值必须存在于常量集合"；
3. 券管理独立 `coupon:template:manage`，并做一次权限矩阵回归（这是 RBAC 越权面的真实收缩）。

---

### R16【低】用正则解析 `spec_json`

**位置**：`stock/service/impl/StockQueryServiceImpl.java:113-138`

```java
private static final Pattern SPEC_VALUE = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"\\s*:\\s*(...)" );
```
而同项目 `order/service/CreateOrderUseCase.java:232` 对同类 JSON 用的是 `ObjectMapper.writeValueAsString`。

**问题本质**：JSON 是递归文法，正则只覆盖"扁平字符串值"子集；遇到嵌套/数组/数字会静默拼出半截字符串（注释自承曾出现 `XL", "color": "红` 的事故）。两套手法并存，后人不知道该跟哪个。

**重构方案**：`ObjectMapper.readValue(json, new TypeReference<LinkedHashMap<String,Object>>(){})` 后按值拼接，异常回落原串；与 `CreateOrderUseCase` 共用同一 mapper 配置。

---

## 2.2 前端：缺失与重复

### F1【高】`usePagination` 只封装状态不封装请求 → 25 处 `onSize`、13 处翻页拉全量

**位置**：`web-admin/src/composables/usePagination.ts:46-72`

```ts
export function usePagination(...) {
  const key = `${STORAGE_PREFIX}${route.path}`;        // :51
  watch([page, size], ([p, s]) => { window.sessionStorage.setItem(key, ...) })  // :58-68
}
```
它只返回 `page/size`，**没有 `total/rows/loading/load`**。于是每个列表页都要自己写：

```ts
// web-admin/src/views/system/log.vue:128（复核：全仓 onSize 共 25 处）
function onSize(value: number): void { size.value = value; page.value = 1; void load(); }
```

**同构副本（翻页拉全量导出，13 处）**：

```ts
// web-admin/src/views/customer/list.vue:126-137
for (let p = 1; p <= 50; p++) {
  const data = await unwrap(customerApi.list({ page: p, size: 100, ... }));
  all.push(...(data?.list ?? []));
  if (list.length < 100 || all.length >= Number(data?.total ?? 0)) break;
}
```
其余 12 处：`finance/receivable.vue:109`、`system/log.vue:79`、`customer/enterprise.vue:199`、`customer/enterprise-audit.vue:177`、`customer/coupon.vue:344`、`customer/coupon-template.vue:351`、`order/shipment.vue:188`、`report/stock-warning.vue:111`、`stock/inbound.vue:109`、`stock/loss.vue:111`、`payment-record.vue:250` 与 `:312`（**同一文件里还重复了一遍**）。

**用户端连 `usePagination` 都没有**：8 处裸写（`order/list.vue:181-182`、`custom/index.vue:208-209`、`message/index.vue:119-120`、`refund/list.vue:171-172`、`products/index.vue:208`、`home/index.vue:176`、`favorites/index.vue:99`、`footprints/index.vue:95`）。

**问题本质**：这是任务描述里提到的"useTable"本该解决的问题——**表格外观模式缺失**。`usePagination` 拿到了状态的钥匙，却没接管请求，导致"门面只做了一半"。

**重构方案**：
```ts
// web-admin/src/composables/useTable.ts
export function useTable<T, Q>(fetcher: (q: Q) => Promise<PageResult<T>>, opts?: { immediate?: boolean }) {
  // 内部复用 usePagination 做 page/size 持久化
  return { page, size, total, rows, loading, query, load, onPage, onSize, reset, fetchAll }
}
```
`fetchAll` 内置 `PAGE=100 / MAX_PAGES=50 / break 条件`，直接喂给 `SentTable :export-rows`。**一处改造，25 + 13 = 38 处收敛**。用户端把 `useTable` 放 `packages/shared`。

---

### F2【高】页面自建状态字典，绕过 `STATUS_LABELS` 策略表（7 处）

| 文件:行 | 自建函数 | 备注 |
|---|---|---|
| `web-admin/src/views/customer/coupon.vue:271` | `statusLabel` | **与 shared 同名**，最易误用 |
| `web-admin/src/views/customer/enterprise-audit.vue:140,146` | `statusLabel` + `statusTone` | `ACTIVE/NORMAL → 已通过` 两套语义混用 |
| `web-admin/src/views/product/customer-price.vue:401,408` | `stateText` + `stateTone` | PENDING/EXPIRED/ACTIVE 再实现一遍 |
| `web-user/src/views/coupon/index.vue:86` | `statusLabel` | |
| `web-user/src/components/InvoiceApplyDialog.vue:119` | `stateText` | |

```ts
// customer/coupon.vue:271-280
function statusLabel(status?: string): string {
  switch (status) { case 'USED': return '已使用'; case 'EXPIRED': return '已过期'; default: return '待使用'; }
}
```

**问题本质**：策略表建在 shared，但**没有强制消费机制**，页面可以随手造一个同名函数。`default: '待使用'` 这类兜底会把未知状态误标成正常态——比显示原码更危险。

**重构方案**：`enums.ts` 补 `COUPON_STATUS` / `ENTERPRISE_AUDIT_STATUS` / `PRICE_STATE` 三组 Record；`statusLabel(status, dict = STATUS_LABELS)` 支持字典参数；用 ESLint `no-restricted-syntax` 禁止页面内定义同名函数（或至少加注释说明为何不能复用）。

---

### F3【高】用户端订单详情：状态判断散成 4 个 computed + 多处 v-if

**位置**：`web-user/src/views/order/detail.vue:414-441`

```ts
const canInvoice = computed(() => { if (order.value?.status !== 'COMPLETED') return false; ... });
const canRefund  = computed(() => s === 'WAIT_SHIP' || s === 'WAIT_RECEIVE' || s === 'PART_SHIPPED' || s === 'PAID');
const canConfirm = computed(() => s === 'WAIT_RECEIVE' || s === 'PART_SHIPPED');
const canRebuy   = computed(() => s === 'COMPLETED' || s === 'CANCELLED' || s === 'CLOSED');
```
模板另有多段：`:58,73` `v-if="order.status === 'WAIT_PAY'"`、`:95` `!== 'COMPLETED'`；`order/list.vue:91/99/108/117` 与 `:169-175` 的 `TABS` 字面量数组重复同一套状态；`order/list.vue:265` 又把 `canInvoice` 抄了一遍。

**问题本质**：后端的"哪个状态能做什么"由状态机定义，前端却在 3 个文件里用字符串数组重新推导了一遍。**前后端两套权限判断**是"按钮能点但接口 40901"的经典来源（与 R2 同源）。

**重构方案**：建 `web-user/src/composables/useOrderActions.ts`，返回 `{canPay, canRefund, canConfirm, canRebuy, canDelete, canInvoice, primaryAction}`；`TABS` 移入 `constants/order.ts` 由 `OrderStatus` 生成。更进一步：后端在 `OrderVO` 里下发 `allowedActions: string[]`，前端只做渲染（**协议化优于复制规则**）。

---

### F4【中高】3 个超长 .vue（1694 / 1460 / 1026 行），职责数 5~9 个

| 文件 | 行数 | 承担职责 |
|---|---|---|
| `web-admin/src/views/chat/workbench.vue` | **1694** | WS 连接与重连、坐席在线上报、会话 Tab 与排队、服务小结（`:728 loadNote`/`:743 saveNote`）、历史服务与转写、顾客历史订单侧栏（`:684`）、订单详情弹窗（`:821`）、满意率统计（`:649`）、正在输入节流（`:641`） |
| `web-user/src/views/order/detail.vue` | **1460** | 状态机 + 倒计时 + 发票 + 购物车 + 物流 + 快照 |
| `web-user/src/views/chat/index.vue` | **1026** | 会话 + 订单卡片 + 评价卡 + 图片预览 |

**重构方案**：`workbench` → `useAgentPresence` / `useSessionTabs` / `useServiceNote` + `CustomerOrderSidebar.vue` / `OrderDetailDialog.vue` / `SatisfactionCard.vue`；`order/detail` → `useOrderCountdown` / `OrderActionBar.vue` / `OrderLogistics.vue` / `OrderInvoicePanel.vue`。**建议以 500 行为阈值加入 CI 警告**。

---

### F5【中】金额格式化 5 份 `yuan()` + 10 处内联除法

**位置**：`customer/coupon-template.vue:306`、`customer/coupon.vue:249`、`checkout/index.vue:347`、`coupon/center.vue:121`、`coupon/index.vue:73`（**5 份逐字相同**）

```ts
function yuan(cent) { const n = Number(cent ?? 0); return Number.isInteger(n/100) ? String(n/100) : (n/100).toFixed(2); }
```
内联：`pay/index.vue:60,64`（`(Number(order.customFeeCent) / 100).toFixed(2)`）、`coupon-template.vue:206`、`cart/index.vue:221/226`。

**问题本质**：`packages/shared/src/format.ts:11` 的 `fmtCent` 注释明写"页面禁止 `price/100`"，但**没有 lint 保护**，规则靠自觉。

**重构方案**：删 5 个 `yuan`，统一 `fmtCent`；`pay/index.vue` 改 `fmtMoney`；加 ESLint 规则禁止 `.vue` 内出现 `/ 100` 与 `toFixed(2)`（`format.ts` 白名单）。

---

### F6【中】业务规则写进视图：划线价 = 零售价 × 1.1

**位置**：`web-user/src/views/cart/index.vue:220-226`

```ts
return (Number(line.unitPriceCent) * 11 / 10 / 100).toFixed(2); // 划线价 = 零售价 +10%
```
**问题本质**：加价率是**业务参数**，写在前端意味着调价必须发版，且与后端取价引擎（`product/domain/pricing/*`）无任何关联——后端改策略，前端划线价不会变。

**重构方案**：后端在 `CatalogProductVO`/购物车行里下发 `strikePriceCent`；若必须前端算，移到 `constants/price.ts` 的 `MARKUP_RATE`。

---

### F7【中】localStorage 直接读写散落，无统一工具

**位置**：`web-user/src/api/custom.ts:85,93,104`（**api 模块内做持久化副作用**）、`web-user/src/layout/index.vue:205,216,221`（`JSON.parse` 的 try-catch 重复 3 次）

```ts
// api/custom.ts:85
localStorage.setItem(PENDING_DRAFT_KEY, JSON.stringify(draft));
```
**问题本质**：api 层应该只做请求；持久化是 UI 关注点。且三处 `JSON.parse` 各自 try-catch，解析失败行为不一致。

**重构方案**：`web-user/src/utils/localStore.ts`（`readJson<T>` / `writeJson` / `remove`，含 try-catch 与版本号）；草稿存取移到 `useCustomDraft` composable；api 层只留请求。全仓已无组件内 `import axios` / `new WebSocket`，这点做得很好，只需补这一个破口。

---

### F8【低中】同一接口两套定义 + 跨模块归属错位

**位置**：
- `web-admin/src/api/product.ts:132-137`（`catalog` / `catalogSkus`）↔ `web-user/src/api/product.ts:46-54`（`list` / `skus`）——**路径完全相同**（`/products`、`/products/{id}/skus`），VO 同为 `CatalogProductVO`
- `web-admin/src/api/order.ts:69` `refunds: '/refunds'`（退款查询写在 order 模块），而 `api/finance.ts:200-203` 又有 `refunds → '/admin/refunds'` 与 `refundDetail`；`views/order/list.vue` 实际调用的是 `orderApi.refunds`

**重构方案**：目录接口提到 `packages/shared/src/api/catalog.ts` 一份实现，两端 re-export；退款收口到 `api/refund.ts`（admin/user 各一份），`order.ts` 删除 `refunds`。

---

### F9【低】硬编码：支付渠道字面量、颜色魔法值

- `web-user/src/views/pay/index.vue:157,165` `channel: 'BALANCE'` —— 应取 `PayChannel` 枚举
- `web-admin/src/views/chat/audit.vue:336` `#12664f`；`chat/metrics.vue:237-240` `#178a68/#d46b08/#e34d59` —— 应走 CSS 变量
- 环境地址本身**规范**：全仓无 `http://localhost` 硬编码，统一 `VITE_API_BASE`（`packages/shared/src/index.ts:59`）

---

## 2.3 "假模式"清单（形式大于实质）

这一节单列，因为**它比缺失更危险**——模式的存在会让人误以为问题已解决。

| # | 假模式 | 声称 | 实际 | 判定 |
|---|---|---|---|---|
| 1 | `DomainEventPublisher` | 领域事件解耦 | 只打日志，0 订阅者，0 异步 | **必须修或删**（R4） |
| 2 | `@Idempotent` / `@RateLimit` | 接口幂等 / 限流 | 拦截器装配完整，业务零使用 | 删除注解或补使用（下单/支付回调最该加） |
| 3 | `LocalFileStorage` | 可替换存储 | 无接口，调用方依赖具体类 | 补 `Storage` 接口（R8） |
| 4 | `PayGateway` | 支付通道适配 | `create`/`verify` 零调用，只有 `refund` 在用 | 补验签（安全）；`create` 要么用要么删 |
| 5 | `OrderStateMachine` 注释 | "所有状态变更必须走此方法" | 4 处绕过 | 收紧可见性（R2） |
| 6 | `CustomFeeCalculator` | 定制费计费引擎 | 仅测试引用，生产用 `1000L` | 接回主链路（R6） |
| 7 | `MemberLevel` | 会员等级 | 与 `MemberTier` 并存且门槛不同 | 删除（R12） |
| 8 | `MailSender` | 邮件通道 | 只有 `LoggingMailSender`，**零注入点** | 接 SMTP 或删除 |
| 9 | `StockLockedEvent` | 库存锁定事件 | **从未被发布** | 删除 |

---

# 维度三 · 重构路线图与验收

## 3.1 分批建议

### P0（本周，修缺陷，不动架构）

| 项 | 动作 | 验收标准 |
|---|---|---|
| R1 | `AutoConfirmJob` 改调 `ConfirmReceiptUseCase` | 新增集成测试：自动确认后积分流水 + 应收台账各有 1 行 |
| R4-B | 若不做异步：删事件总线，`MemberRefundGatewayImpl` 直调 `PointFacade` | 退款回调后 `point_flow` 有回退行；`grep DomainEventPublisher` 为 0 |
| 支付验签 | `PayCallbackUseCase` 接 `payGateway.verify(callbackParams)` | 伪造回调（改金额/签名）返回失败；Mock 网关补验签单测 |
| R6 | 定制费走 `CustomFeeCalculator` | Cart 报价 == Order 落库金额（同一配置）；调价只改 yml |
| R15-权限 | 券管理拆 `coupon:template:manage` | 权限矩阵回归：仅 `customer:grant` 无法自建券模板 |

### P1（两周内，一致性，改动可控）

| 项 | 动作 |
|---|---|
| R2 / R3 | 收敛状态机：删白名单、`setStatus` 降 package-private、建 `RefundStateMachine` |
| R11 | ArchUnit 补 `..repository..` 与 `..job..`；stock 暴露 `StockService` |
| R5 | 退款 Saga + outbox，外部调用移出事务 |
| R10 | `@Confirm` + `ConfirmTokenInterceptor`，14 处收敛 |
| R7 / R9 | `CouponType` 枚举 + `Coupon.applicability` |
| F1 | 新建 `useTable`（含 `fetchAll`），收敛 38 处 |
| F2 / F3 | 状态字典补全 + `useOrderActions`（或后端下发 `allowedActions`） |

### P2（一个月内，可维护性）

| 项 | 动作 |
|---|---|
| R8 | `Storage` 接口 + `UploadPolicy` |
| R12 / R13 / R14 | 删 `MemberLevel`；VO Assembler + Builder；每模块 Assembler |
| R15 / R16 | `Texts` / `Patterns` / `Perm` 常量类；JSON 解析换 `ObjectMapper` |
| F4 / F5 / F7 / F8 | 拆 3 个超长 vue；统一 `fmtCent`；`localStore`；共享 catalog API |

## 3.2 门禁增强（ArchUnit 4 条 → 7 条）

现有 4 条（controller 不依赖 mapper / domain 纯 POJO / common 不依赖业务 / 跨模块只经 service）保持不变，**建议新增**：

```java
// 5) 跨模块禁用包补 repository
otherModuleForbiddenPackages → 追加 base + "..repository.."

// 6) job 包同样受跨模块约束
.and().resideInAnyPackage("..controller..", "..service..", "..event..", "..job..")

// 7) 状态变更只能走聚合
noClasses().that().resideOutsideOfPackages("..order.domain..", "..order.repository..")
    .should().callMethod(Order.class, "setStatus", OrderStatus.class);

// 8) 事件发布者必须有订阅者（可用反射扫描事件类是否被 @EventListener 引用）
//    —— 这条用 ArchUnit 不好写，建议改为单测：每个 *Event 类必须出现在某个 @EventListener 方法参数中
```

## 3.3 不建议重构的地方（避免过度设计）

审计的价值也包括"劝你别改"：

| 位置 | 为什么不改 |
|---|---|
| 全仓 6 处 `switch`（`RefundAuditUseCase` 除外） | 分支均 ≤4，其中 `OrderAdminServiceImpl.shipStatusOf` 的真问题是**重复**（R2 建议挂到枚举），不是 switch 本身 |
| 5 个 RefundGateway 实现只有 1~5 行 | **薄是端口适配器的正确形态**，不该合并；要改的是"编排"（R5），不是"转发" |
| `BatchDeleteExecutor` 用 `Function` 回调而非抽象类 | 组合优于继承，保持现状 |
| 仓储手写 `toPO/toDomain` | 除非引入 MapStruct 并全量替换，否则局部改造收益低于风险；优先级排在 R13 之后 |
| `PriceContext` 的 Builder | 11 参数场景，Builder 是正解，不要改回构造器 |
| 前端 `SentTable` 插槽化 | 已经是合适的模板方法，不要为了"模式感"改成 render props |

---

# 附录 A · 关键复核命令

```bash
# A.1 事件总线是否真的空转
grep -rn "@EventListener\|ApplicationListener\|@Async\|@EnableAsync" --include=*.java server/src/main/java | wc -l
# → 0

# A.2 幂等/限流注解是否被业务使用
grep -rn "@Idempotent\|@RateLimit" --include=*.java server/src/main/java | grep -v "common/anno\|common/interceptor"
# → 空

# A.3 定制计费引擎是否死代码
grep -rn "CustomFeeCalculator" server/src
# → 仅 test 出现

# A.4 文件存储是否有接口
ls server/src/main/java/com/sent/sports/common/storage/    # → LocalFileStorage.java（唯一）

# A.5 前端是否存在 useTable
grep -rn "useTable" web-admin/src web-user/src packages/shared/src   # → 空

# A.6 switch 总量（证明无巨型 switch）
grep -rn "switch (" --include=*.java server/src/main/java | wc -l   # → 6

# A.7 确认令牌校验复制份数
grep -rln "confirmTokenService.consume" --include=*.java server/src/main/java | wc -l   # → 14
```

# 附录 B · 证据索引（按文件）

| 文件 | 相关条目 |
|---|---|
| `product/domain/pricing/PricingService.java` | 1.1.1（亮点） |
| `product/domain/pricing/*Strategy.java` | 1.1.1（亮点） |
| `order/domain/OrderStateMachine.java` | 1.1.2（亮点）/ R2 |
| `customize/domain/CustomStateMachine.java` | 1.1.2 / R3（作为重构范本） |
| `common/anno/UseCaseAspect.java` | 1.1.3（亮点） |
| `common/mybatis/DataScopeSqlAppender.java` | 1.1.3（亮点） |
| `common/event/LoggingDomainEventPublisher.java` | 1.1.5 / R4 |
| `common/storage/LocalFileStorage.java` | 1.1.4 / R8 |
| `payment/gateway/PayGateway.java` | 1.1.4 / 2.3-4 |
| `job/AutoConfirmJob.java` | **R1（P0）** |
| `order/service/ConfirmReceiptUseCase.java` | **R1 / R2** |
| `order/service/ShipOrderUseCase.java` | R2 |
| `refund/service/RefundAuditUseCase.java` | **R3** |
| `order/service/CloseOrderUseCase.java` | **R5** |
| `cart/service/CartQuoteUseCase.java` | **R6** |
| `customize/domain/CustomFeeCalculator.java` | R6 |
| `coupon/service/CouponTemplateAdminUseCase.java` | R7 |
| `coupon/service/CouponUseCase.java` + `CouponQueryServiceImpl.java` | R9 |
| `refund/controller/admin/AdminRefundBatchDeleteController.java` | R10（14 份样板） |
| `server/src/test/java/com/sent/sports/ArchitectureTest.java` | R11（规则盲区） |
| `member/domain/MemberLevel.java` + `MemberTier.java` | R12 |
| `order/dto/OrderDtos.java` + `OrderQueryServiceImpl.java` | R13 |
| `packages/shared/src/http.ts` | 1.2.2（最高分） |
| `packages/shared/src/enums.ts` | 1.2.3 / F2 |
| `web-admin/src/composables/usePagination.ts` | 1.2.1 / **F1** |
| `web-admin/src/views/chat/workbench.vue` | F4 |
| `web-user/src/views/order/detail.vue` | F3 / F4 |

---

> **一句话收尾**：这个代码库的设计模式底子在中上水平（取价引擎、状态机表、数据权限改写、HTTP 内核都值得保留），真正拖后腿的是**"模式建了但不接线"**——事件总线、幂等注解、支付验签、定制计费引擎四处空转，其中三处直接对应可指认的业务缺陷。先把 P0 的 5 项修完，比再引入任何新框架都更有价值。
