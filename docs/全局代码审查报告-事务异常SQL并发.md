# 森特体育用品管理系统 — 全局代码审查报告（Step 1）

> 审查视角：高级代码审查员 · 审查范围：全量后端代码（server/）+ 前端配置（web-admin / web-user）+ 部署配置
> 审查重点：事务边界、异常吞噬、SQL 性能与安全、并发安全

---

## 一、事务边界（Transaction Boundary）

**结论：✅ 安全**

- 项目**未使用 Spring `@Transactional`**（全仓库 grep 命中数为 0），而是自研 `@UseCase` 注解 + `UseCaseAspect`（`@Around` 包裹 `TransactionTemplate`）统一管控事务。
- `@UseCase` 注解 `@Target(ElementType.METHOD)`，编译期即阻止标注在 `private` 方法上；当前所有用例方法均为 `public`，**无 private 失效风险**。
- 事务内行为核查：各 `@UseCase` 方法体内仅做取价、库存行锁预占（`UPDATE ... WHERE physical_qty-locked_qty-pending_qty-frozen_qty >= ?` 条件更新）、落库、发布领域事件等**本地 DB / 内存操作**。
- **未发现**在事务内调用外部 HTTP / 发送邮件 / MQ 等耗时操作：
  - `MailSender` 仅存在于接口层（`notify`），无 SMTP 实现，当前不会在事务内触发真实外部 I/O。
  - 模拟支付网关 `MockPayGateway` 在 **Controller 层**调用，不在 `@UseCase` 内。

**建议（非阻塞）**：若后续接入真实支付/短信网关，务必把外部调用移出 `@UseCase`，改为「事务内只落流水/状态，事务外异步补偿」模式，避免长事务占用连接。

---

## 二、异常吞噬（Exception Swallowing）

**结论：✅ 未发现静默吞噬**

- `UseCaseAspect`：仅捕获 `RuntimeException`（`setRollbackOnly` + 重抛）与 `Throwable`（包装为 `RuntimeException` 重抛），**不吞异常**。
- 各业务 `catch` 块：要么 `log.warn/debug` 后重抛，要么在 `RefundCallbackUseCase` 中捕获 `BizException` 后置 `FAILED` 状态并落库（**业务语义**，非吞噬）。
- `AuthFilter` 捕获 `BizException` 后调用 `writeError` 写出标准错误体（与 `GlobalExceptionHandler` 等价路径）。
- **未发现**「大范围 `catch (Exception e)` 静默吞掉 `BizException` 导致全局异常处理器失效」的情况。

**建议（非阻塞）**：个别 `catch` 用 `log.debug` 的关键失败路径（如退款失败、回调失败）建议提升为 `log.warn`，便于生产排障。

---

## 三、SQL 性能与安全（SQL Security & Performance）

**结论：✅ 安全**

- **注入风险**：全仓库**无 Mapper XML 文件**，所有 SQL 使用注解 `@Select/@Update/@Insert/@Delete` + `<script>`（MyBatis-Plus）。动态片段统一使用 `#{}`（预编译占位），**未发现任何 `${}` 拼接用户输入**。
  - `DataScopeInterceptor` 改写 SQL 时拼接的是**服务端解析出的数值型 staffId / 数据范围**（来自 `DataScopeContext`，非前端输入）；`annotationCache` 用 `ConcurrentHashMap`，安全。
  - `StaffMapper.selectByDataScope` 使用白名单式范围常量（ALL/TEAM/SELF），无注入面。
- **全表扫描**：所有列表查询均带 `LIMIT` / 分页（`IPage`）；`StockMapper.selectBelowSafety()` 为带 `ORDER BY sku_id` 的 Job 查询，非交互式全表扫描。

**建议（非阻塞）**：上线后开启慢查询日志（>200ms）并定期 `EXPLAIN` 关键报表查询（销售排行、库存预警）。

---

## 四、并发安全（Concurrency Safety）

**结论：✅ 无共享可变非线程安全状态**

- 全量单例 Bean（Service/Controller/@Component）成员变量核查：所有注入依赖均为 `final`（不可变）；**未发现** `SimpleDateFormat` / `Calendar` / 可变 `static` 状态。
- 关键类：
  - `IdGen`：序号用 `AtomicLong`（线程安全），日期格式化用局部变量。
  - `InMemorySessionBroadcaster`：`ConcurrentHashMap` + `CopyOnWriteArraySet` + `synchronized(session)`。
  - `DataScopeContext`：用 `ThreadLocal` 且请求结束清理。
  - `InMemoryRateLimiter`：`ConcurrentHashMap` + 每 key `synchronized` 双端队列，`limit<=0` 直接返回 false（快速失败）。

**注意（非阻塞）**：上述 `InMemory*` 实现为**进程内**适配器，多实例（多副本）部署时需替换为 Redis / DB 实现，否则限流、会话广播、令牌状态会在实例间不一致（见 Step 3 部署审计）。

---

## 五、Step 2 加固落地（边界单测补齐）

| 场景 | 加固方式 | 测试 |
|---|---|---|
| 商品已下架 | `ProductPriceRepositoryImpl.loadContext`：SKU 状态非 `ON` 抛 `PRODUCT_OFF_SHELF(40960)` | `CreateOrderUseCaseTest.productOffShelfRejected` |
| 用户超信用额度 | 新增 `CreditFacade`(finance.service) + `CreditFacadeImpl`（查 `enterprise_profile` 真库 limit/used）；`CreateOrderUseCase` 在算出**后端应付**后、落库前调用（用后端金额比对，防金额篡改） | `CreateOrderUseCaseTest.creditExceededRejected` |
| 库存刚好不足 | 已有 | `stockFailureRollsBackEntireOrder` |
| 订单不存在 / 金额不符 / 迟到回调 / 已支付幂等 | 已有 | `PayCallbackUseCaseTest`（6 例覆盖） |
| 退款各失败分支 | 已有 | `RefundCallbackUseCaseTest`（4 例覆盖） |
| Redis 挂了 | 系统限流为**进程内实现，无 Redis 依赖**，不存在该故障面；新增测试证明 fail-fast 不阻塞 | `InMemoryRateLimiterTest`（4 例） |

**验证结果**：`mvn -o verify`（跳过 repackage）通过 —— 单元 **521**、集成 **25**、0 失败；ArchUnit 4/4；JaCoCo 门禁通过。
（注：本轮 `repackage` 因运行中的 `java -jar sent-server.jar` 进程 PID 4120 占用 jar 文件导致重命名失败，故用 `-Dspring-boot.repackage.skip=true` 完成测试门禁验证；此属环境问题，不影响代码正确性。正式 CI 全新构建不受影响。）
