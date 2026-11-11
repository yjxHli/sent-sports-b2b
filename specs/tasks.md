# 森特体育用品管理系统 · 任务分解清单（tasks.md）

| 项 | 内容 |
| --- | --- |
| 版本 | v1.0 |
| 上游 | `specs/spec.md` v1.2、`specs/plan.md` v1.3、`specs/agents.md` |
| 定位 | 面向 AI 代理的原子任务队列，可直接逐条执行 |
| 任务总数 | 315（Phase1 28 / Phase2 46 / Phase3 63 / Phase4 58 / Phase5 50 / Phase6 70） |
| 原则 | 一任务一文件、测试先行、依赖显式、可并行必标 `[P]` |

## 0. 路径简写与执行协议

### 0.1 路径简写

| 简写 | 实际路径 |
| --- | --- |
| `@` | `server/src/main/java/com/sent/sports/` |
| `@t` | `server/src/test/java/com/sent/sports/` |
| `@r` | `server/src/main/resources/` |
| `wa` | `web-admin/src/` |
| `wu` | `web-user/src/` |
| `sh` | `packages/shared/src/` |

### 0.2 TDD 铁律（Phase2/3/4 强制，例外见 0.2.1）

1. 每个单元先执行 **测试任务**（产出测试文件），运行并确认 **红**（编译通过、断言失败）。
2. 再执行 **实现任务**（产出实现文件），运行至 **绿**。
3. 禁止先写实现后补测试；禁止测试只断言"不抛异常"。
4. 提交时测试与实现可分两次 commit：`test(xxx): 红` → `feat(xxx): 绿`。

### 0.2.1 TDD 适用范围（例外情形）

| Phase | 是否强制测试先行 | 说明 |
| --- | --- | --- |
| P1 骨架 | 部分 | 骨架任务以「能启动、能构建」为验收，无法先写业务测试；测试性产出为 P1-19 架构测试与 P1-20 测试基类，须先落地 |
| P2 领域 | 强制 | 纯 Java，测试先写无任何障碍 |
| P3 用例 | 强制 | Mock 仓储，测试先写无任何障碍 |
| P4 接口 | 强制 | MockMvc + Mock Service，测试先写 |
| P5 基础设施 | 强制（形式不同） | 集成测试无法在 DDL 之前运行，故 P5-48～P5-50 须在 **P5-23 完成后立即以「红」落地**，随后随 P5-19～P5-22 适配器逐个转绿，**禁止做完实现再补测试** |
| P6 前端 | 不强制 | 以人工点通 + Playwright 收口（Z-01）替代单测 |

### 0.3 `[P]` 判定（三条同时满足才标记）

1. 依赖任务全部已完成；
2. 与同批任务**不共享任何产出文件**；
3. 不修改他人已完成的文件。

### 0.4 AI 执行协议

| 规则 | 说明 |
| --- | --- |
| 一次一任务 | 只处理看板上第一个未完成任务，完成后立即标记 |
| 禁止越界 | 不得修改本任务"产出文件"以外的文件；需要改时先新建任务 |
| 完成标准 | 验收栏的命令/断言全部通过，才算完成 |
| 阻塞处理 | 依赖未满足时禁止跳过，标记 `BLOCKED` 并说明 |
| 提交格式 | `feat(scope): 描述 [TASK-ID]` |
| 失败上限 | 同一任务连续失败 2 次，停止并输出诊断，不继续硬改 |

---

## 1. 阶段总览

| Phase | 目标 | 任务数 | 出口标准 |
| --- | --- | --- | --- |
| P1 Foundation & Skeleton | 骨架、配置、DI、日志、环境、前端初始化 | 28 | 后端可启动、两端可跑、CI 通过 |
| P2 Domain Model & Tests | 实体、值对象、状态机、领域服务 | 46 | 领域单测全绿，覆盖率 ≥ 80% |
| P3 Application Use Cases | 编排、事务边界、用例测试 | 63 | 用例测试全绿，业务规则可验证 |
| P4 API Contracts & WebAPI | DTO、校验、Controller、接口测试 | 58 | OpenAPI 可导出，MockMvc 全绿 |
| P5 Infrastructure | 建表、Mapper、仓储、认证、WS、定时 | 50 | 真实 DB 集成测试通过 |
| P6 Frontend | 双端路由、页面、交互、联调 | 70 | 9.4.7 + 全链路可点通 |

**里程碑映射**：P1→M0、P2/P3→M1–M2、P4/P5→M2–M6、P6→M7–M8（见 plan §12.1）。

---

## Phase 1 · Foundation & Skeleton（28）

> 不实现任何业务功能。只保证"能跑、能测、能部署"。骨架期以可运行性为验收，唯一的测试先行任务是 P1-19 架构测试。

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P1-01 | Maven 工程与依赖锁定 | `server/pom.xml` | — | `mvn -q compile` 通过；含 web/security/validation/mybatis-plus/mysql/redis/flyway/springdoc/jjwt/testcontainers/archunit |
| P1-02 | 启动类 | `@SentSportsApplication.java` | P1-01 | `mvn spring-boot:run` 启动无错 |
| P1-03 | 主配置 | `@r/application.yml` | P1-01 | 端口 8088、时区 `Asia/Shanghai`、上传 50MB `[P]` |
| P1-04 | 开发环境配置 | `@r/application-dev.yml` | P1-03 | 指向 `sent_dev` |
| P1-05 | 测试环境配置 | `@r/application-test.yml` | P1-03 | 指向 `sent_test` |
| P1-06 | 生产环境配置 | `@r/application-prod.yml` | P1-03 | 指向 `sent_prod`，关闭 swagger |
| P1-07 | 日志配置 | `@r/logback-spring.xml` | P1-03 | 控制台+滚动文件，pattern 含 `%X{traceId}` |
| P1-08 | 统一响应体 | `@common/web/R.java` | P1-02 | 单测断言 `ok/fail` 的 code、message、traceId |
| P1-09 | 错误码枚举 | `@common/error/ErrorCode.java` | — | 覆盖 plan §8.2 全部 19 个码 `[P]` |
| P1-10 | 业务异常 | `@common/error/BizException.java` | P1-09 | 携带 code/message/args |
| P1-11 | 全局异常处理 | `@common/web/GlobalExceptionHandler.java` | P1-08,P1-10 | 参数错→40001、未登录→40101、无权限→40301、未知→50001 |
| P1-12 | 分页模型 | `@common/web/PageResult.java` | P1-08 | 序列化含 `list/total/page/size` `[P]` |
| P1-13 | TraceId 过滤器 | `@common/web/TraceFilter.java` | P1-02 | 响应头与 MDC 均含 traceId `[P]` |
| P1-14 | Jackson 配置 | `@common/config/JacksonConfig.java` | P1-02 | Long→String、时间 ISO-8601、金额字段不丢精度 `[P]` |
| P1-15 | MyBatis-Plus 配置 | `@common/config/MybatisPlusConfig.java` | P1-01 | 分页插件 + 乐观锁插件生效 `[P]` |
| P1-16 | Web MVC 配置 | `@common/config/WebMvcConfig.java` | P1-02 | CORS 暴露 `X-New-Token,X-Token-Expire-In,X-Idempotent-Replay` `[P]` |
| P1-17 | OpenAPI 配置 | `@common/config/OpenApiConfig.java` | P1-02 | `/swagger-ui.html` 可访问 `[P]` |
| P1-18 | 环境守卫（R-03） | `@common/config/EnvironmentGuard.java` | P1-03,P1-04 | env 与库名后缀不符时启动失败并打印原因 |
| P1-19 | **[测试]** 分层架构测试 | `@t/ArchitectureTest.java` | P1-02 | 4 条规则通过：controller 不依赖 mapper、domain 无 Spring 注解、common 不依赖业务、跨模块只走 service |
| P1-20 | 单测基类 | `@t/support/BaseUnitTest.java` | P1-19 | 提供 Mockito 扩展与固定时钟 |
| P1-21 | 工程规范文件 | `.gitignore` `.editorconfig` `.gitattributes` | — | 忽略 target/node_modules/dist；UTF-8 + LF `[P]` |
| P1-22 | 管理端脚手架落地 | `web-admin/`（复制 pure-admin-thin v6.2.0） | — | `pnpm i && pnpm dev` 起在 5174，访问 `/admin/` 正常 |
| P1-23 | 用户端工程初始化 | `web-user/`（Vite+Vue3+TS+ElementPlus+Tailwind） | — | `pnpm dev` 起在 5173，空白首页可访问 `[P]` |
| P1-24 | 共享包初始化 | `sh/index.ts` + `packages/shared/package.json` | P1-23 | 两端可 `import { } from "@sent/shared"` |
| P1-25 | workspace 配置 | `pnpm-workspace.yaml` | P1-23,P1-24 | `pnpm i` 识别三包 |
| P1-26 | 容器编排骨架 | `docker/docker-compose.yml` | P1-03 | `docker compose config` 通过 `[P]` |
| P1-27 | Nginx 骨架 | `docker/nginx.conf` | P1-26 | `nginx -t` 通过；含 `/admin/`、`/api`、`/ws` 转发 |
| P1-28 | CI 流水线 | `.github/workflows/ci.yml` | P1-19,P1-22 | 含 lint、单测、**DDL 禁 DROP 扫描**、`${}` 扫描 |

---

## Phase 2 · Domain Model & Domain Tests（46）

> 先测试后实现。领域层纯 Java，无 Spring 注解、无数据库。

### 2.1 金额与取价（12）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P2-01 | **[测试]** 金额分摊 | `@t/common/util/MoneyUtilTest.java` | P1-20 | 覆盖：比例分摊、余数归属、单行、0 权重、合计恒等；**红** |
| P2-02 | 金额分摊实现 | `@common/util/MoneyUtil.java` | P2-01 | 上述测试转绿 |
| P2-03 | **[测试]** 取价引擎 | `@t/product/domain/PricingServiceTest.java` | P1-20 | 覆盖 A3/A23：一客一价终结、协议压阶梯、阶梯压会员、折上折需开关、低于底价抛错、低于成本置标记；**红** |
| P2-04 | 取价上下文 | `@product/domain/pricing/PriceContext.java` | P2-03 | 含 customerType/level/协议/阶梯/零售/底价/成本 `[P]` |
| P2-05 | 取价结果 | `@product/domain/pricing/PriceResult.java` | P2-03 | 含 unitPriceCent/source/ruleId/belowCost `[P]` |
| P2-06 | 取价策略接口 | `@product/domain/pricing/PriceStrategy.java` | P2-04,P2-05 | `order()` + `match()` + `price()` `[P]` |
| P2-07 | 一客一价策略 | `@product/domain/pricing/CustomerSkuPriceStrategy.java` | P2-06 | 有效期边界（当天有效）`[P]` |
| P2-08 | 协议价策略 | `@product/domain/pricing/AgreementPriceStrategy.java` | P2-06 | 未开 stacking 时不参与阶梯 `[P]` |
| P2-09 | 阶梯价策略 | `@product/domain/pricing/LadderPriceStrategy.java` | P2-06 | 区间边界 9/10/49/50 `[P]` |
| P2-10 | 会员价策略 | `@product/domain/pricing/MemberPriceStrategy.java` | P2-06 | 按等级折扣率 `[P]` |
| P2-11 | 零售价策略 | `@product/domain/pricing/RetailPriceStrategy.java` | P2-06 | 兜底恒命中 `[P]` |
| P2-12 | 取价编排 | `@product/domain/pricing/PricingService.java` | P2-07..P2-11 | P2-03 转绿；`source` 唯一 |

### 2.2 订单与发货（10）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P2-13 | **[测试]** 订单状态枚举 | `@t/order/domain/OrderStatusTest.java` | P1-20 | 7 个状态、中文名、是否终态；**红** |
| P2-14 | 订单状态枚举 | `@order/domain/OrderStatus.java` | P2-13 | 转绿 |
| P2-15 | **[测试]** 订单状态机 | `@t/order/domain/OrderStateMachineTest.java` | P2-13 | 覆盖 15.1 全部迁移；非法迁移抛 40901；含 `PART_SHIPPED` 往返；**红** |
| P2-16 | 订单状态机 | `@order/domain/OrderStateMachine.java` | P2-15 | 转绿；迁移表唯一来源 |
| P2-17 | **[测试]** 订单行发货量 | `@t/order/domain/OrderItemTest.java` | P1-20 | 超发被拒、多次累加、剩余量计算；**红** |
| P2-18 | 订单行实体 | `@order/domain/OrderItem.java` | P2-17 | 转绿 |
| P2-19 | **[测试]** 发货进度重算 | `@t/order/domain/ShipmentProgressTest.java` | P1-20 | 0→待发货、部分→部分发货、全发→待收货；**红** |
| P2-20 | 发货进度 | `@order/domain/ShipmentProgress.java` | P2-19 | 转绿 |
| P2-21 | **[测试]** 支付状态与订单联动 | `@t/order/domain/PayStatusTest.java` | P1-20 | 未支付/已支付/部分退款/全额退款迁移；**红** |
| P2-22 | 支付状态枚举 | `@order/domain/PayStatus.java` | P2-21 | 转绿 |

### 2.3 定制（6）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P2-23 | **[测试]** 定制状态 | `@t/customize/domain/CustomStatusTest.java` | P1-20 | 10 个状态含 `ESCALATED`；**红** |
| P2-24 | 定制状态枚举 | `@customize/domain/CustomStatus.java` | P2-23 | 转绿 |
| P2-25 | **[测试]** 定制状态机 | `@t/customize/domain/CustomStateMachineTest.java` | P2-23 | 覆盖 15.2；第 4 次驳回转 `ESCALATED`；未排产可取消；**红** |
| P2-26 | 定制状态机 | `@customize/domain/CustomStateMachine.java` | P2-25 | 转绿 |
| P2-27 | **[测试]** 定制计费 | `@t/customize/domain/CustomFeeCalculatorTest.java` | P1-20 | 面数×数量阶梯；无颜色维度；**红** |
| P2-28 | 定制计费 | `@customize/domain/CustomFeeCalculator.java` | P2-27 | 转绿 |

### 2.4 退款与积分（6）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P2-29 | **[测试]** 退款金额 | `@t/refund/domain/RefundCalculatorTest.java` | P2-01 | 行实付、已退扣减、超退抛 40940、运费规则、不补扣；**红** |
| P2-30 | 退款金额计算 | `@refund/domain/RefundCalculator.java` | P2-29 | 转绿 |
| P2-31 | **[测试]** 积分抵扣 | `@t/member/domain/PointDeductionTest.java` | P1-20 | 100:1、30% 上限、余额不足、负积分；**红** |
| P2-32 | 积分抵扣 | `@member/domain/PointDeduction.java` | P2-31 | 转绿 |
| P2-33 | **[测试]** 等级计算 | `@t/member/domain/LevelCalculatorTest.java` | P1-20 | 三档门槛 0/2000/10000；退款扣回后次日重算；**红** |
| P2-34 | 等级计算 | `@member/domain/LevelCalculator.java` | P2-33 | 转绿 |

### 2.5 库存与财务（6）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P2-35 | **[测试]** 库存口径 | `@t/stock/domain/StockItemTest.java` | P1-20 | 可售=实物−预占−待检−冻结；不足时不可锁；不为负；**红** |
| P2-36 | 库存实体 | `@stock/domain/StockItem.java` | P2-35 | 转绿 |
| P2-37 | **[测试]** 核销分配 | `@t/finance/domain/WriteOffAllocatorTest.java` | P1-20 | 一笔记多单、超额抛 40930、分次回款、顺序核销；**红** |
| P2-38 | 核销分配 | `@finance/domain/WriteOffAllocator.java` | P2-37 | 转绿 |
| P2-39 | **[测试]** 应收状态 | `@t/finance/domain/ReceivableTest.java` | P1-20 | 到期日、逾期判定、结清；**红** |
| P2-40 | 应收实体 | `@finance/domain/Receivable.java` | P2-39 | 转绿 |

### 2.6 客服与通用值对象（6）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P2-41 | **[测试]** 会话状态 | `@t/chat/domain/ChatSessionTest.java` | P1-20 | 待接入/进行中/已结束；同一客户仅一个进行中；**红** |
| P2-42 | 会话实体 | `@chat/domain/ChatSession.java` | P2-41 | 转绿 |
| P2-43 | **[测试]** 客服分配策略 | `@t/chat/domain/StaffAssignmentPolicyTest.java` | P1-20 | 取会话数最少、并列按工号、达上限跳过、无在线返回空；**红** |
| P2-44 | 分配策略 | `@chat/domain/StaffAssignmentPolicy.java` | P2-43 | 转绿 |
| P2-45 | **[测试]** 幂等键与确认短语 | `@t/common/domain/ValueObjectsTest.java` | P1-20 | 幂等键格式校验；7 个 action 对应短语精确匹配；**红** |
| P2-46 | 值对象 | `@common/domain/IdemKey.java` `@common/domain/ConfirmPhrase.java` | P2-45 | 转绿（两个文件同属一个值对象单元） |

---

## Phase 3 · Application Use Cases & Application Tests（63）

> 先测试后实现。用 Mock 隔离仓储与外部服务，验证编排与业务顺序。

### 3.1 基础设施抽象（3）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P3-01 | 用例测试基类 | `@t/support/BaseUseCaseTest.java` | P1-20 | 提供 Mock 仓库装配与固定事务模板 |
| P3-02 | 领域事件发布接口 | `@common/event/DomainEventPublisher.java` | P1-02 | 三个事件类型可编译 `[P]` |
| P3-03 | 用例事务注解 | `@common/anno/UseCase.java` + `UseCaseAspect.java` | P3-02 | 标注方法在事务内执行，异常回滚 `[P]` |

### 3.2 交易主链（14）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P3-04 | **[测试]** 结算试算 | `@t/cart/CartQuoteUseCaseTest.java` | P2-12 | 调用取价、运费、积分、应付；**红** |
| P3-05 | 结算试算用例 | `@cart/service/CartQuoteUseCase.java` | P3-04 | 转绿 |
| P3-06 | **[测试]** 创建订单 | `@t/order/CreateOrderUseCaseTest.java` | P2-12,P2-18 | 幂等键必填、预占失败整单回滚、价格快照落库、订单号生成；**红** |
| P3-07 | 创建订单用例 | `@order/service/CreateOrderUseCase.java` | P3-06 | 转绿；顺序：校验→取价→预占→落库 |
| P3-08 | **[测试]** 取消订单 | `@t/order/CancelOrderUseCaseTest.java` | P2-16 | 仅待付款可取消；释放预占；已支付抛 40901；**红** |
| P3-09 | 取消订单用例 | `@order/service/CancelOrderUseCase.java` | P3-08 | 转绿 |
| P3-10 | **[测试]** 发起支付 | `@t/payment/CreatePaymentUseCaseTest.java` | P2-16 | 生成流水、幂等、已支付不可重复发起；**红** |
| P3-11 | 发起支付用例 | `@payment/service/CreatePaymentUseCase.java` | P3-10 | 转绿 |
| P3-12 | **[测试]** 支付回调 | `@t/payment/PayCallbackUseCaseTest.java` | P3-11,P2-22 | **A2/A14**：重复回调只入账一次、金额不符拒绝、已取消订单生成退款单、不复活订单；**红** |
| P3-13 | 支付回调用例 | `@payment/service/PayCallbackUseCase.java` | P3-12 | 转绿；顺序同 plan §7.3 七步 |
| P3-14 | **[测试]** 发货 | `@t/order/ShipOrderUseCaseTest.java` | P2-20,P2-26 | 按行数量校验、定制未完工拒绝、超发拒绝、状态重算；**红** |
| P3-15 | 发货用例 | `@order/service/ShipOrderUseCase.java` | P3-14 | 转绿 |
| P3-16 | **[测试]** 确认收货 | `@t/order/ConfirmReceiptUseCaseTest.java` | P2-20 | 逐包裹确认、全部确认才完成、发成长值与积分；**红** |
| P3-17 | 确认收货用例 | `@order/service/ConfirmReceiptUseCase.java` | P3-16 | 转绿 |

### 3.3 退款与售后（6）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P3-18 | **[测试]** 申请退款 | `@t/refund/RefundApplyUseCaseTest.java` | P2-30 | 可退性判定（含定制已排产拒绝）、超退拒绝、凭证必填；**红** |
| P3-19 | 申请退款用例 | `@refund/service/RefundApplyUseCase.java` | P3-18 | 转绿 |
| P3-20 | **[测试]** 退款审核 | `@t/refund/RefundAuditUseCaseTest.java` | P3-19 | 通过触发回调、驳回必填原因、二次申诉限一次；**红** |
| P3-21 | 退款审核用例 | `@refund/service/RefundAuditUseCase.java` | P3-20 | 转绿 |
| P3-22 | **[测试]** 退款回调 | `@t/refund/RefundCallbackUseCaseTest.java` | P3-21,P2-32 | 更新支付状态、退积分优先、回补待检、释放额度、全退关单；**红** |
| P3-23 | 退款回调用例 | `@refund/service/RefundCallbackUseCase.java` | P3-22 | 转绿 |

### 3.4 定制（6）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P3-24 | **[测试]** 提交图稿 | `@t/customize/SubmitDraftUseCaseTest.java` | P2-26 | 版本号递增、必须有附件、超 50MB 拒绝、驳回后重传；**红** |
| P3-25 | 提交图稿用例 | `@customize/service/SubmitDraftUseCase.java` | P3-24 | 转绿 |
| P3-26 | **[测试]** 审核定制单 | `@t/customize/ReviewCustomUseCaseTest.java` | P3-25 | **A5/A6**：通过锁定版本、驳回原因必填、第 4 次转主管、超时计算 24h；**红** |
| P3-27 | 审核用例 | `@customize/service/ReviewCustomUseCase.java` | P3-26 | 转绿 |
| P3-28 | **[测试]** 排产完工 | `@t/customize/ProduceUseCaseTest.java` | P3-27 | **A7**：已排产不可退款、完工才可发货；**红** |
| P3-29 | 排产完工用例 | `@customize/service/ProduceUseCase.java` | P3-28 | 转绿 |

### 3.5 库存（6）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P3-30 | **[测试]** 手工入库 | `@t/stock/InboundUseCaseTest.java` | P2-36 | **A19**：数量>0、写流水、可售增加、触发到货提醒；**红** |
| P3-31 | 入库用例 | `@stock/service/InboundUseCase.java` | P3-30 | 转绿 |
| P3-32 | **[测试]** 报损 | `@t/stock/LossUseCaseTest.java` | P2-36 | 不得超实物、必须填原因、写流水；**红** |
| P3-33 | 报损用例 | `@stock/service/LossUseCase.java` | P3-32 | 转绿 |
| P3-34 | **[测试]** 盘点 | `@t/stock/StockCheckUseCaseTest.java` | P2-36 | 实盘差异计算、审批后调账、需确认令牌（由 P4 层校验）；**红** |
| P3-35 | 盘点用例 | `@stock/service/StockCheckUseCase.java` | P3-34 | 转绿 |

### 3.6 财务（8）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P3-36 | **[测试]** 登记回款 | `@t/finance/PaymentRecordUseCaseTest.java` | P2-38 | 金额>0、客户必填、流水号唯一；**红** |
| P3-37 | 登记回款用例 | `@finance/service/PaymentRecordUseCase.java` | P3-36 | 转绿 |
| P3-38 | **[测试]** 核销 | `@t/finance/WriteOffUseCaseTest.java` | P2-38,P2-40 | **A9/A18**：超额拒绝、一笔记多单、释放额度、结清状态；**红** |
| P3-39 | 核销用例 | `@finance/service/WriteOffUseCase.java` | P3-38 | 转绿 |
| P3-40 | **[测试]** 回款冲销 | `@t/finance/ReverseRecordUseCaseTest.java` | P3-39 | 冲销回退额度、已核销先解绑、留痕；**红** |
| P3-41 | 冲销用例 | `@finance/service/ReverseRecordUseCase.java` | P3-40 | 转绿 |
| P3-42 | **[测试]** 企业授信 | `@t/finance/CreditGrantUseCaseTest.java` | P2-40 | 额度与账期、有效期、超额下单拦截依据；**红** |
| P3-43 | 授信用例 | `@finance/service/CreditGrantUseCase.java` | P3-42 | 转绿 |

### 3.7 会员与客户（6）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P3-44 | **[测试]** 人工授级 | `@t/member/GrantLevelUseCaseTest.java` | P2-34 | 原因必填、有效期、到期回落；**红** |
| P3-45 | 授级用例 | `@member/service/GrantLevelUseCase.java` | P3-44 | 转绿 |
| P3-46 | **[测试]** 积分调整 | `@t/member/AdjustPointUseCaseTest.java` | P2-32 | 原因必填、负积分允许、写流水；**红** |
| P3-47 | 积分调整用例 | `@member/service/AdjustPointUseCase.java` | P3-46 | 转绿 |
| P3-48 | **[测试]** 客户改派 | `@t/customer/TransferCustomerUseCaseTest.java` | P1-20 | 原因必填、不改历史订单归属、写流转日志；**红** |
| P3-49 | 改派用例 | `@customer/service/TransferCustomerUseCase.java` | P3-48 | 转绿 |

### 3.8 客服（10）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P3-50 | **[测试]** 发起会话 | `@t/chat/CreateSessionUseCaseTest.java` | P2-42,P2-44 | **A17/A20/A22**：有客服则 ACTIVE、无则 WAITING、已有进行中则复用、达上限进排队；**红** |
| P3-51 | 发起会话用例 | `@chat/service/CreateSessionUseCase.java` | P3-50 | 转绿 |
| P3-52 | **[测试]** 发送消息 | `@t/chat/SendMessageUseCaseTest.java` | P3-51 | 幂等（同 clientMsgId 返回原 seq）、会话已结束拒绝、seq 递增；**红** |
| P3-53 | 发送消息用例 | `@chat/service/SendMessageUseCase.java` | P3-52 | 转绿 |
| P3-54 | **[测试]** 会话分配与转接 | `@t/chat/AssignSessionUseCaseTest.java` | P2-44 | 排队优先 VIP、转接需原因、原客服退出、接收方可见历史；**红** |
| P3-55 | 分配转接用例 | `@chat/service/AssignSessionUseCase.java` | P3-54 | 转绿 |
| P3-56 | **[测试]** 结束会话 | `@t/chat/CloseSessionUseCaseTest.java` | P3-51 | 主动关闭、30 分钟超时、释放名额、原因枚举；**红** |
| P3-57 | 结束会话用例 | `@chat/service/CloseSessionUseCase.java` | P3-56 | 转绿 |
| P3-58 | **[测试]** 在线状态切换 | `@t/chat/PresenceUseCaseTest.java` | P2-44 | ONLINE/BUSY/OFFLINE、离线释放会话、恢复重分配；**红** |
| P3-59 | 在线状态用例 | `@chat/service/PresenceUseCase.java` | P3-58 | 转绿 |

### 3.9 认证与会话（4）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P3-60 | **[测试]** 登录 | `@t/auth/LoginUseCaseTest.java` | P1-20 | 密码错 5 次锁定 15 分钟、双令牌签发、审计留痕；**红** |
| P3-61 | 登录用例 | `@auth/service/LoginUseCase.java` | P3-60 | 转绿 |
| P3-62 | **[测试]** 刷新与登出 | `@t/auth/RefreshTokenUseCaseTest.java` | P3-61 | **A12**：RT 一次性轮换、旧 RT 失效、`token_version` 失效、登出列入黑名单；**红** |
| P3-63 | 刷新登出用例 | `@auth/service/RefreshTokenUseCase.java` | P3-62 | 转绿 |

---

## Phase 4 · API Contracts & WebAPI（58）

> 先接口测试（MockMvc + Mock Service），后实现 Controller。DTO 与 Controller 分文件。

### 4.1 接口基础（4）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P4-01 | MockMvc 测试基类 | `@t/support/BaseApiTest.java` | P3-01 | 统一注入安全上下文与 JSON 断言工具 |
| P4-02 | 权限注解 | `@common/anno/RequirePerm.java` | P1-02 | 可标注方法/类 `[P]` |
| P4-03 | 字段脱敏注解 | `@common/anno/Sensitive.java` | P1-14 | 按角色置空字段 `[P]` |
| P4-04 | 分页查询对象 | `@common/web/PageQuery.java` | P1-12 | page/size/sort 归一化与上限 100 `[P]` |

### 4.2 用户端接口（9 模块 × 3 = 27）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P4-05 | **[测试]** 认证接口 | `@t/web/AuthApiTest.java` | P3-61,P3-63 | 登录返回双令牌、refresh 轮换、40102/40103 分流、登出清 token；**红** |
| P4-06 | 认证 DTO | `@web/dto/AuthDtos.java` | P4-05 | LoginCmd/TokenVO/RefreshCmd 校验注解齐全 |
| P4-07 | 认证控制器 | `@web/AuthController.java` | P4-06 | P4-05 转绿 |
| P4-08 | **[测试]** 商品浏览接口 | `@t/web/CatalogApiTest.java` | P2-12 | 列表筛选排序、详情按身份给价、缺货态、未登录仅零售价；**红** |
| P4-09 | 商品 DTO | `@web/dto/CatalogDtos.java` | P4-08 | ProductVO/SkuVO/PriceVO |
| P4-10 | 商品控制器 | `@web/CatalogController.java` | P4-09 | 转绿 |
| P4-11 | **[测试]** 购物车接口 | `@t/web/CartApiTest.java` | P3-05 | 加购去重、改量、试算金额一致；**红** |
| P4-12 | 购物车 DTO | `@web/dto/CartDtos.java` | P4-11 | CartItemVO/QuoteVO |
| P4-13 | 购物车控制器 | `@web/CartController.java` | P4-12 | 转绿 |
| P4-14 | **[测试]** 订单接口 | `@t/web/OrderApiTest.java` | P3-07,P3-09,P3-17 | 缺幂等头 40002、下单 200、取消 200、确认收货 200；**红** |
| P4-15 | 订单 DTO | `@web/dto/OrderDtos.java` | P4-14 | CreateOrderCmd/OrderVO/四维度状态字段 |
| P4-16 | 订单控制器 | `@web/OrderController.java` | P4-15 | 转绿 |
| P4-17 | **[测试]** 支付接口 | `@t/web/PaymentApiTest.java` | P3-11,P3-13 | 发起返回 txnNo、回调验签失败 400、重复回调仍 200；**红** |
| P4-18 | 支付 DTO | `@web/dto/PaymentDtos.java` | P4-17 | PayCmd/CallbackCmd/PayResultVO |
| P4-19 | 支付控制器 | `@web/PaymentController.java` | P4-18 | 转绿 |
| P4-20 | **[测试]** 退款接口 | `@t/web/RefundApiTest.java` | P3-19 | 超退 40940、状态跟踪、申诉一次；**红** |
| P4-21 | 退款 DTO | `@web/dto/RefundDtos.java` | P4-20 | ApplyRefundCmd/RefundVO |
| P4-22 | 退款控制器 | `@web/RefundController.java` | P4-21 | 转绿 |
| P4-23 | **[测试]** 定制接口 | `@t/web/CustomApiTest.java` | P3-25 | 上传图稿版本+1、非图稿格式 400、详情含驳回原因；**红** |
| P4-24 | 定制 DTO | `@web/dto/CustomDtos.java` | P4-23 | DraftVO/CustomVO |
| P4-25 | 定制控制器 | `@web/CustomController.java` | P4-24 | 转绿 |
| P4-26 | **[测试]** 会员与企业接口 | `@t/web/MemberApiTest.java` | P2-34 | 等级进度、积分明细、额度与应收、专属商品；**红** |
| P4-27 | 会员 DTO | `@web/dto/MemberDtos.java` | P4-26 | LevelVO/PointVO/CreditVO |
| P4-28 | 会员控制器 | `@web/MemberController.java` | P4-27 | 转绿 |
| P4-29 | **[测试]** 消息与客服 REST | `@t/web/ChatApiTest.java` | P3-51,P3-53 | 建会话、发消息、afterSeq 补拉、已读上报；**红** |
| P4-30 | 会话 DTO | `@web/dto/ChatDtos.java` | P4-29 | SessionVO/MessageVO/SendCmd |
| P4-31 | 会话控制器 | `@web/ChatController.java` | P4-30 | 转绿 |

> 认证实现类用例见 §3.9（P3-60～P3-63）；本节只测 HTTP 契约。

### 4.3 管理端接口（9 模块 × 3 = 27）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P4-32 | **[测试]** 商品管理接口 | `@t/admin/AdminProductApiTest.java` | P2-12 | CRUD、上下架、阶梯价、一客一价、无权限 403；**红** |
| P4-33 | 商品管理 DTO | `@web/admin/dto/AdminProductDtos.java` | P4-32 | ProductForm/SkuForm/LadderForm |
| P4-34 | 商品管理控制器 | `@web/admin/AdminProductController.java` | P4-33 | 转绿 |
| P4-35 | **[测试]** 库存管理接口 | `@t/admin/AdminStockApiTest.java` | P3-31,P3-33,P3-35 | 列表口径、入库、报损、盘点审批需确认令牌；**红** |
| P4-36 | 库存 DTO | `@web/admin/dto/AdminStockDtos.java` | P4-35 | InboundCmd/LossCmd/CheckCmd |
| P4-37 | 库存控制器 | `@web/admin/AdminStockController.java` | P4-36 | 转绿 |
| P4-38 | **[测试]** 订单与发货接口 | `@t/admin/AdminOrderApiTest.java` | P3-15,P3-13 | 多维筛选、代客下单、拆单发货、关单需令牌；**红** |
| P4-39 | 订单管理 DTO | `@web/admin/dto/AdminOrderDtos.java` | P4-38 | ShipCmd/CloseCmd/AdminOrderVO |
| P4-40 | 订单管理控制器 | `@web/admin/AdminOrderController.java` | P4-39 | 转绿 |
| P4-41 | **[测试]** 定制审核接口 | `@t/admin/AdminCustomApiTest.java` | P3-27,P3-29 | 待审列表、通过/驳回、排产完工、超时标记；**红** |
| P4-42 | 定制审核 DTO | `@web/admin/dto/AdminCustomDtos.java` | P4-41 | ReviewCmd/RejectCmd |
| P4-43 | 定制审核控制器 | `@web/admin/AdminCustomController.java` | P4-42 | 转绿 |
| P4-44 | **[测试]** 客户与会员管理接口 | `@t/admin/AdminCustomerApiTest.java` | P3-49,P3-45,P3-47 | 列表按数据范围、改派、授级、积分调整、企业审核；**红** |
| P4-45 | 客户管理 DTO | `@web/admin/dto/AdminCustomerDtos.java` | P4-44 | TransferCmd/GrantCmd/CreditCmd |
| P4-46 | 客户管理控制器 | `@web/admin/AdminCustomerController.java` | P4-45 | 转绿 |
| P4-47 | **[测试]** 财务接口 | `@t/admin/AdminFinanceApiTest.java` | P3-39,P3-41,P3-43 | 应收台账、登记回款、核销、冲销、授信、发票；**红** |
| P4-48 | 财务 DTO | `@web/admin/dto/AdminFinanceDtos.java` | P4-47 | WriteOffCmd/RecordCmd/InvoiceCmd |
| P4-49 | 财务控制器 | `@web/admin/AdminFinanceController.java` | P4-48 | 转绿 |
| P4-50 | **[测试]** 客服工作台接口 | `@t/admin/AdminChatApiTest.java` | P3-55,P3-57,P3-59 | 三态列表、接入、转接、关闭、状态切换、工作量；**红** |
| P4-51 | 客服管理 DTO | `@web/admin/dto/AdminChatDtos.java` | P4-50 | TransferCmd/CloseCmd/PresenceCmd |
| P4-52 | 客服控制器 | `@web/admin/AdminChatController.java` | P4-51 | 转绿 |
| P4-53 | **[测试]** 系统与报表接口 | `@t/admin/AdminSystemApiTest.java` | P1-19 | 员工/角色/参数/日志、三张报表字段与权限；**红** |
| P4-54 | 系统 DTO | `@web/admin/dto/AdminSystemDtos.java` | P4-53 | StaffForm/RolePermForm/ReportVO |
| P4-55 | 系统控制器 | `@web/admin/AdminSystemController.java` | P4-54 | 转绿 |
| P4-56 | **[测试]** 确认令牌接口 | `@t/admin/ConfirmTokenApiTest.java` | P2-46 | 短语错 40302、令牌绑定目标、5 分钟过期；**红** |
| P4-57 | 确认令牌 DTO | `@web/admin/dto/ConfirmDtos.java` | P4-56 | ConfirmCmd/TokenVO |
| P4-58 | 确认令牌控制器 | `@web/admin/AdminConfirmController.java` | P4-57 | 转绿 |

---

## Phase 5 · Infrastructure & Integration（50）

> 真实数据库、外部适配、认证落地、定时任务。可与 Phase 3 并行（DDL 依据 plan §6.2）。

### 5.1 数据库（9）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P5-01 | 基线迁移 | `@r/db/migration/V1__baseline.sql` | plan §6.2 | 仅建 schema 与公共字段约定，无 DROP `[P]` |
| P5-02 | 系统与权限表 | `V2__system.sql` | P5-01 | 6 表 + `token_version` 字段 |
| P5-03 | 客户与会员表 | `V3__customer.sql` | P5-01 | 9 表 `[P]` |
| P5-04 | 商品与定价表 | `V4__product.sql` | P5-01 | 6 表含 `allow_stacking` `[P]` |
| P5-05 | 库存表 | `V5__stock.sql` | P5-01 | 6 表 `[P]` |
| P5-06 | 订单与支付表 | `V6__order.sql` | P5-01 | 含 `orders.idem_key` **唯一索引** |
| P5-07 | 定制与退款表 | `V7__customize.sql` | P5-01 | `custom_order.order_item_id` 唯一 `[P]` |
| P5-08 | 财务表 | `V8__finance.sql` | P5-01 | `receivable.order_id` 唯一 `[P]` |
| P5-09 | 客服与通知表 | `V9__chat_notify.sql` | P5-01 | 9 表；**不建 job_lock** `[P]` |

### 5.2 持久化（14）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P5-10 | 客户与员工 Mapper | `@system/mapper/StaffMapper.java` | P5-02 | CRUD + 数据范围查询可用 |
| P5-11 | 商品 Mapper | `@product/mapper/ProductMapper.java` | P5-04 | 含 SKU、阶梯、协议价查询 `[P]` |
| P5-12 | 库存 Mapper（含原子扣减） | `@stock/mapper/StockMapper.java` | P5-05 | `lockStock` 条件更新 SQL 生效 `[P]` |
| P5-13 | 订单 Mapper | `@order/mapper/OrderMapper.java` | P5-06 | 含 `selectByIdemKey`、状态条件更新 |
| P5-14 | 支付 Mapper | `@payment/mapper/PaymentTxnMapper.java` | P5-06 | 唯一键冲突可捕获 `[P]` |
| P5-15 | 退款 Mapper | `@refund/mapper/RefundOrderMapper.java` | P5-07 | `[P]` |
| P5-16 | 定制 Mapper | `@customize/mapper/CustomOrderMapper.java` | P5-07 | 待审+超时查询 `[P]` |
| P5-17 | 财务 Mapper | `@finance/mapper/ReceivableMapper.java` | P5-08 | 逾期查询、核销锁行 `[P]` |
| P5-18 | 客服 Mapper | `@chat/mapper/ChatSessionMapper.java` | P5-09 | 待接入队列排序 `[P]` |
| P5-19 | 订单仓储实现 | `@order/repository/OrderRepositoryImpl.java` | P5-13 | 通过 P3-06 单测（切真实仓储） |
| P5-20 | 库存仓储实现 | `@stock/repository/StockRepositoryImpl.java` | P5-12 | 预占/释放/出库三方法可用 |
| P5-21 | 财务仓储实现 | `@finance/repository/FinanceRepositoryImpl.java` | P5-17 | `[P]` |
| P5-22 | 客服仓储实现 | `@chat/repository/ChatRepositoryImpl.java` | P5-18 | `[P]` |
| P5-23 | 集成测试基类 | `@t/support/BaseIntegrationTest.java` | P5-01 | Testcontainers 起 MySQL+Redis，Flyway 自动迁移 |

### 5.3 认证与横切（8）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P5-24 | 令牌服务 | `@auth/token/TokenService.java` | P3-61 | 签发/校验/续期，`ver` 校验生效 |
| P5-25 | 刷新令牌存储 | `@auth/token/RefreshTokenStore.java` | P5-24 | Redis 轮换、一次性、按设备 |
| P5-26 | 鉴权过滤器 | `@auth/filter/AuthFilter.java` | P5-24 | 剩余 <15min 下发 `X-New-Token`；40102/40103 分流 |
| P5-27 | 权限服务（缓存） | `@system/service/PermissionService.java` | P5-10 | `perm:{role}` TTL 60s，变更清缓存 |
| P5-28 | 数据范围拦截器 | `@common/mybatis/DataScopeInterceptor.java` | P5-27 | ALL/TEAM/SELF 三类 SQL 追加正确 |
| P5-29 | 幂等拦截器 | `@common/interceptor/IdempotentInterceptor.java` | P5-23 | SETNX + DONE 快照 + `X-Idempotent-Replay` |
| P5-30 | 限流拦截器 | `@common/interceptor/RateLimitInterceptor.java` | P1-15 | 滑动窗口、429 + `Retry-After` |
| P5-31 | 确认令牌服务 | `@system/service/ConfirmTokenService.java` | P2-46 | 短语校验、绑定目标、用后即焚 |

### 5.4 外部适配与实时（6）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P5-32 | 本地文件存储 | `@common/storage/LocalFileStorage.java` | P5-01 | 上传/下载/去重，路径按年月 |
| P5-33 | 模拟支付网关 | `@payment/gateway/MockPayGateway.java` | P5-14 | 签名/回调报文可复现 |
| P5-34 | WS 握手与注册表 | `@chat/ws/ChatWebSocketHandler.java` | P5-22 | 鉴权、心跳、断线移除 |
| P5-35 | WS 消息编解码 | `@chat/ws/WsEnvelope.java` | P5-34 | MSG/ACK/READ/QUEUE 四类 `[P]` |
| P5-36 | WS 广播器 | `@chat/ws/SessionBroadcaster.java` | P5-34 | 单实例内存 + 抽象接口 `[P]` |
| P5-37 | 邮件发送（桩） | `@notify/MailSender.java` | P1-03 | dev 打日志、prod 走 SMTP 配置 `[P]` |

### 5.5 定时任务（10）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P5-38 | 调度配置（单线程） | `@config/ScheduleConfig.java` | P1-02 | `poolSize=1`，注释标注分布扩展点 |
| P5-39 | 订单超时取消 | `@job/OrderTimeoutJob.java` | P5-19 | A1 通过；条件更新幂等 `[P]` |
| P5-40 | 自动确认收货 | `@job/AutoConfirmJob.java` | P5-19 | 15 天规则 `[P]` |
| P5-41 | 应收逾期标记 | `@job/OverdueJob.java` | P5-21 | 冻结账期 + 三级通知 `[P]` |
| P5-42 | 积分过期 | `@job/PointExpireJob.java` | P5-21 | 写 EXPIRE 流水 `[P]` |
| P5-43 | 合同到期提醒 | `@job/ContractRemindJob.java` | P5-17 | 30 天前提醒 `[P]` |
| P5-44 | 库存预警 | `@job/StockWarningJob.java` | P5-12 | 低于安全库存清单 `[P]` |
| P5-45 | 会话超时结束 | `@job/ChatTimeoutJob.java` | P5-22 | 30 分钟无消息关闭 `[P]` |
| P5-46 | 客服掉线回收 | `@job/PresenceReclaimJob.java` | P5-22 | 60s 心跳超时置离线 `[P]` |
| P5-47 | 审图/退款超时提醒 | `@job/SlaRemindJob.java` | P5-16 | 24h/48h 双提醒 `[P]` |

### 5.6 关键集成测试（3）

> 按 §0.2.1，这三项**不是**收尾补测：P5-23 完成后立即以「红」状态写好，随 P5-19～P5-22 逐个转绿。

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P5-48 | **[测试]** 下单支付集成测试 | `@t/integration/OrderFlowIT.java` | P5-19,P5-33 | 下单→支付→回调→发货→收货 全绿 |
| P5-49 | **[测试]** 并发抢单测试 | `@t/integration/StockConcurrencyIT.java` | P5-20 | 20 线程抢 1 件，成功=1，无负库存 |
| P5-50 | **[测试]** 幂等与迟到回调 | `@t/integration/IdempotentIT.java` | P5-29,P5-14 | 清空 Redis 后仍不重复下单；A2/A14 通过 |

---

## Phase 6 · Frontend UI & Interaction（70）

> 管理端基于 `pure-admin-thin` 改造（plan §9.4），用户端独立设计。

### 6.1 共享层（4）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P6-01 | 枚举常量（前后端同源） | `sh/enums.ts` | P4-01 | 5 组状态枚举与后端一致 `[P]` |
| P6-02 | 金额与格式化工具 | `sh/format.ts` | P1-24 | `fmtCent(12900) === "129.00"` `[P]` |
| P6-03 | 类型定义 | `sh/types.ts` | P6-01 | 覆盖主要 VO `[P]` |
| P6-04 | HTTP 封装（用户端） | `sh/http.ts` | P6-02 | 复用开源实现 + D1–D4：单飞刷新、40102/40103、防旧盖新 |

### 6.2 管理端基座改造（9）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P6-05 | 环境与代理 | `web-admin/.env.development` | P1-22 | 5174、base `/admin/`、h5 模式、`/api` 与 `/ws` 代理 |
| P6-06 | HTTP 层改造 D1–D5 | `wa/utils/http/index.ts` | P6-05 | 补 `X-New-Token`、40102/40301 分流、防旧盖新、白名单 |
| P6-07 | 登录态适配 | `wa/utils/auth.ts` | P6-06 | `expiresIn`→时间戳；双端 storage key 隔离 |
| P6-08 | 路由表（静态+权限） | `wa/router/modules/business.ts` | P4-02 | 全部业务路由 + `meta.perm` |
| P6-09 | 权限守卫 | `wa/router/index.ts` | P6-08 | 无 token 跳登录、无权限跳 403 |
| P6-10 | 权限 store 改造 | `wa/store/modules/permission.ts` | P6-09 | 按 `permissions[]` 过滤菜单，**不调** `/get-async-routes` |
| P6-11 | 品牌主题 | `wa/style/theme.scss` | P1-22 | 主色 #12664F、强调 #FF7A1A、Logo 替换 |
| P6-12 | 危险操作确认组件 | `wa/utils/confirm.tsx` | P6-06 | 输入短语→取 token→返回可提交状态 |
| P6-13 | 幂等键工具 | `wa/utils/idempotent.ts` | P6-06 | 进入页面生成一次、成功后清空 `[P]` |

### 6.3 管理端 API 模块（9）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P6-14 | 商品 API | `wa/api/product.ts` | P4-34 | 6 个方法 `[P]` |
| P6-15 | 库存 API | `wa/api/stock.ts` | P4-37 | `[P]` |
| P6-16 | 订单与发货 API | `wa/api/order.ts` | P4-40 | `[P]` |
| P6-17 | 定制审核 API | `wa/api/custom.ts` | P4-43 | `[P]` |
| P6-18 | 客户与会员 API | `wa/api/customer.ts` | P4-46 | `[P]` |
| P6-19 | 财务 API | `wa/api/finance.ts` | P4-49 | `[P]` |
| P6-20 | 客服 API | `wa/api/chat.ts` | P4-52 | `[P]` |
| P6-21 | 系统与报表 API | `wa/api/system.ts` | P4-55 | `[P]` |
| P6-22 | 认证 API | `wa/api/auth.ts` | P4-07 | `[P]` |

### 6.4 管理端页面（20 首批）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P6-23 | 登录页改造 | `wa/views/login/index.vue` | P6-07 | 双令牌写入、失败锁定提示、去模板文案 |
| P6-24 | 工作台 | `wa/views/dashboard/index.vue` | P6-21 | 六个指标卡 + 角色化待办 |
| P6-25 | 商品列表 | `wa/views/product/list.vue` | P6-14 | RePureTableBar + 筛选 + 批量上下架 |
| P6-26 | 商品编辑 | `wa/views/product/edit.vue` | P6-14 | 基础信息 + SKU 矩阵 + 阶梯价 |
| P6-27 | 分类管理 | `wa/views/product/category.vue` | P6-14 | 两级树 + 排序 `[P]` |
| P6-28 | 一客一价 | `wa/views/product/customer-price.vue` | P6-14 | 按客户+SKU 设价与有效期 `[P]` |
| P6-29 | 库存列表 | `wa/views/stock/list.vue` | P6-15 | 四口径列 + 预警标红 `[P]` |
| P6-30 | 手工入库 | `wa/views/stock/inbound.vue` | P6-15 | 数量+原因+流水回显 `[P]` |
| P6-31 | 报损 | `wa/views/stock/loss.vue` | P6-15 | 凭证上传 `[P]` |
| P6-32 | 盘点 | `wa/views/stock/check.vue` | P6-15 | 实盘录入 → 差异 → 审批（确认令牌） |
| P6-33 | 订单列表 | `wa/views/order/list.vue` | P6-16 | 四维度组合筛选 |
| P6-34 | 订单详情 | `wa/views/order/detail.vue` | P6-16 | 取价来源、包裹、定制、日志四区块 |
| P6-35 | 发货工作台 | `wa/views/order/shipment.vue` | P6-16 | 按行选量、生成包裹、多次发货 |
| P6-36 | 定制审核 | `wa/views/custom/review.vue` | P6-17 | 左图右单、`P`/`R` 快捷键、版本对比 |
| P6-37 | 定制列表 | `wa/views/custom/list.vue` | P6-17 | 超时标红 `[P]` |
| P6-38 | 客户列表 | `wa/views/customer/list.vue` | P6-18 | 数据范围生效 `[P]` |
| P6-39 | 客户详情 | `wa/views/customer/detail.vue` | P6-18 | 订单/售后/会话/应收四 Tab |
| P6-40 | 企业审核与授信 | `wa/views/customer/enterprise.vue` | P6-18 | 资质预览 + 授信（确认令牌） |
| P6-41 | 会员与积分 | `wa/views/member/index.vue` | P6-18 | 等级参数 + 授级 + 积分调整 |
| P6-42 | 客服工作台 | `wa/views/chat/workbench.vue` | P6-20 | 三栏、待接入排序、转接、状态切换 |

### 6.5 管理端页面（财务 + 系统 + 报表，12）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P6-43 | 退款审核 | `wa/views/finance/refund.vue` | P6-19 | 通过需确认令牌，驳回必填原因 |
| P6-44 | 应收台账 | `wa/views/finance/receivable.vue` | P6-19 | 逾期天数标红 `[P]` |
| P6-45 | 回款登记与核销 | `wa/views/finance/payment-record.vue` | P6-19 | 勾选订单核销、超额提示 |
| P6-46 | 发票管理 | `wa/views/finance/invoice.vue` | P6-19 | 上传 PDF、状态流转 `[P]` |
| P6-47 | 合同管理 | `wa/views/finance/contract.vue` | P6-19 | 附件上传 + 到期提醒 `[P]` |
| P6-48 | 员工管理 | `wa/views/system/staff.vue` | P6-21 | 增改停用重置密码 `[P]` |
| P6-49 | 角色权限矩阵 | `wa/views/system/role.vue` | P6-21 | 模块×操作×数据范围勾选 `[P]` |
| P6-50 | 业务参数 | `wa/views/system/config.vue` | P6-21 | 8 项时效参数可改 `[P]` |
| P6-51 | 操作日志 | `wa/views/system/log.vue` | P6-21 | 变更前后对比 `[P]` |
| P6-52 | 销售额统计 | `wa/views/report/sales.vue` | P6-21 | ECharts 趋势 + 汇总 |
| P6-53 | 销售业绩排行 | `wa/views/report/sales-rank.vue` | P6-21 | 排行表 + 下钻 `[P]` |
| P6-54 | 库存预警清单 | `wa/views/report/stock-warning.vue` | P6-21 | 缺口排序 + 入库快捷入口 `[P]` |

### 6.6 用户端（16）

| ID | 任务 | 产出文件 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P6-55 | 路由与布局 | `wu/router/index.ts` | P6-04 | 19 路由 + 登录守卫 |
| P6-56 | 商品 API | `wu/api/catalog.ts` | P4-10 | `[P]` |
| P6-57 | 交易 API | `wu/api/order.ts` | P4-16,P4-19 | `[P]` |
| P6-58 | 会员/企业 API | `wu/api/member.ts` | P4-28 | `[P]` |
| P6-59 | 客服 API + useChat | `wu/composables/useChat.ts` | P4-31,P5-34 | 连接/心跳/重发/seq 排序/补拉/已读 |
| P6-60 | 首页 | `wu/views/home/index.vue` | P6-56 | 按销量倒序、悬浮客服 |
| P6-61 | 商品列表 | `wu/views/product/list.vue` | P6-56 | 筛选排序分页 `[P]` |
| P6-62 | 商品详情 | `wu/views/product/detail.vue` | P6-56 | 规格联动、定制表单（无颜色）、缺货提醒 |
| P6-63 | 定制服务页 | `wu/views/custom/index.vue` | P6-56 | 面数×阶梯试算 `[P]` |
| P6-64 | 购物车 | `wu/views/cart/index.vue` | P6-57 | 实时试算只信后端 |
| P6-65 | 结算页 | `wu/views/checkout/index.vue` | P6-64 | 地址/发票/备注 + 幂等键生成 |
| P6-66 | 支付页 | `wu/views/pay/index.vue` | P6-57 | 倒计时 + 结果轮询三态 |
| P6-67 | 订单列表 | `wu/views/order/list.vue` | P6-57 | 状态 Tab、部分发货显示 `[P]` |
| P6-68 | 订单详情 | `wu/views/order/detail.vue` | P6-57 | 四维度状态 + 逐包裹确认 |
| P6-69 | 售后申请 | `wu/views/after-sale/apply.vue` | P6-57 | 行选择 + 可退额校验 `[P]` |
| P6-70 | 会员/企业/消息/发票/图稿/个人中心（5 页打包为 1 任务） | `wu/views/member/*` `enterprise/*` `messages/*` `invoices/*` `profile/*` | P6-58 | 各页基础展示与跳转可用 |

---

## 7. 依赖图与关键路径

```
P1-01 ──┬─ P1-02 ─ P1-08 ─ P1-11
        │        └ P1-14 ─ P4-03
        └─ P1-03 ─ P1-04 ─ P1-18

P1-20 ─┬─ P2-01 ─ P2-02 ──────────────── P2-29
       ├─ P2-03 ─ P2-12 ─ P3-04/P3-06 ── P3-07 ─ P3-12/P3-14 ─ P3-15
       ├─ P2-15 ─ P2-16 ─ P3-08
       └─ P2-25 ─ P2-26 ─ P3-26 ─ P3-28

P2-12 ─ P4-08 ─ P4-10
P1-20 ─ P3-60 ─ P3-61 ─ P3-62 ─ P3-63 ─ P4-05 ─ P4-07
P3-07 ─ P4-14 ─ P4-16 ─ P5-19 ─┬─ P5-39 ─ P5-48
                               └─ P5-13
P5-01 ─ P5-06 ─ P5-14 ─ P5-33 ─ P5-48

P6-04 ─ P6-55 ─ P6-60…P6-69
P6-05 ─ P6-06 ─ P6-12/13 ─ P6-23 ─ P6-24…P6-54
```

**关键路径（最长链，决定工期）**

`P1-01 → P1-02 → P1-20 → P2-03 → P2-12 → P3-06 → P3-07 → P3-12 → P4-14 → P4-16 → P5-19 → P5-48`
（骨架 → 取价 → 下单 → 支付回调 → 订单接口 → 真实仓储 → 全链路集成）

**三条可并行的独立支线**

| 支线 | 起点 | 说明 |
| --- | --- | --- |
| 定制线 | P2-23 | 与交易线无共享文件，可独立开发到 P5-16 |
| 客服线 | P2-41 | 需要 WS，但领域与用例可与交易线并行 |
| 财务线 | P2-37 | 依赖退款（P3-22），可晚一步启动 |

---

## 8. 并行泳道建议（4 人 / 4 Agent）

| 泳道 | 负责范围 | 起始任务 | 说明 |
| --- | --- | --- | --- |
| A · 交易主链 | P1、P2 金额与订单、P3 订单支付、P4 订单支付接口 | P1-01 | 关键路径，优先保障 |
| B · 领域支撑 | P2 库存/财务/会员、P3 库存财务、P5 持久化 | P1-20 | 依赖 A 的领域基类后启动 |
| C · 定制与客服 | P2 定制/客服、P3 定制/客服、P4 定制/客服、P5 WS | P1-20 | 与 A 无文件冲突 |
| D · 前端 | P1-22/23、P6 全部 | P1-23 | 用 OpenAPI mock 先行，P4-01 后可联调 |

**冲突提示**：A 与 B 都会碰 `common/`，约定 `common` 由 A 独占，B 需要新增时提任务给 A。

---

## 9. 收口任务（Phase 6 后追加，不计入 315）

| ID | 任务 | 依赖 | 验收 |
| --- | --- | --- | --- |
| Z-01 | 23 条验收场景脚本（Playwright） | 全部 | A1–A23 全绿 |
| Z-02 | 压测脚本与基线报告 | P5-49 | plan §11.3 场景 1/2/4 达标 |
| Z-03 | OpenAPI 导出与接口文档 | P4-58 | `openapi.json` 与 plan §8 一致 |
| Z-04 | README / 部署手册 / 环境变量清单 | P1-26 | 新人按文档可起环境 |
| Z-05 | 全量自检（见第 10 节） | 全部 | 34 项全过 |

---

## 10. 交付前自检清单（34 项）

### 工程红线（8）
- [ ] 迁移脚本无 `DROP TABLE` / `DROP DATABASE` / `TRUNCATE`
- [ ] Mapper 无 `${}` 拼接用户输入
- [ ] `app.env` 与库名后缀校验生效
- [ ] 7 个危险操作均需确认短语 + 令牌
- [ ] 金额字段全为 `_cent` 且无 `double`
- [ ] 无 `SELECT *`
- [ ] 事务内无外部 HTTP 调用
- [ ] 所有写接口标 `@RequirePerm`

### 交易正确性（10）
- [ ] A1 超时取消释放库存
- [ ] A2 迟到回调不复活订单且自动退款
- [ ] A3 阶梯优先于会员
- [ ] A14 重复回调只入账一次
- [ ] A15 不超卖
- [ ] A16 拆单发货状态正确
- [ ] A18 不允许预收款
- [ ] A23 协议价不叠阶梯
- [ ] 幂等：Redis 清空后仍不重复下单
- [ ] 退款金额分摊合计等于订单实付

### 定制与客服（6）
- [ ] A5/A6 审图驳回可重传且版本递增
- [ ] A7 已排产不可退
- [ ] 第 4 次驳回转主管
- [ ] A17 会话分配取最少者
- [ ] A20 达上限进排队
- [ ] A21 断线重连不丢不重

### 财务（4）
- [ ] A9 一笔记多单核销、额度释放
- [ ] A10 逾期冻结账期
- [ ] 冲销可回退额度
- [ ] 发票一张订单一张

### 权限与体验（6）
- [ ] A11 销售员只能看自己客户
- [ ] A12 仓管看不到手机号与成交价
- [ ] A13 未达等级访问专属商品被拦
- [ ] 40102 并发只刷新一次
- [ ] 页面无 `pure-admin` 字样
- [ ] `pnpm build` 部署 `/admin/` 刷新不 404

---

*本清单为执行层文档。需求变更时先改 `spec.md`，再改 `plan.md`，最后同步本文件的受影响任务。*
