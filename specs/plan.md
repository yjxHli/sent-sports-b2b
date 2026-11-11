# 森特体育用品管理系统 · 技术方案与实施计划（plan.md）

| 项目 | 内容 |
| --- | --- |
| 文档名称 | 森特体育用品管理系统 技术方案与实施计划 |
| 版本 | v1.3 |
| 上游依据 | `specs/spec.md` v1.2（需求定稿）、`specs/agents.md`（工程红线）、pure-admin-thin v6.2.0（MIT，上游开源仓库） |
| 定位 | 可直接用于任务拆分、接口设计、页面设计、测试设计与代码生成 |
| 读者 | 后端、前端、测试、实施；以及后续参与代码生成的 AI 代理 |
| 约束 | 本文所有设计必须能回溯到 spec 的具体条款；不得引入 spec 未定义的能力 |

### 如何使用本文档

| 你的角色 | 先读 | 再读 |
| --- | --- | --- |
| 后端开发 | 第 3、6、7 章 | 第 8 章接口、第 12 章任务 |
| 前端开发 | 第 9 章 | 第 8 章接口、第 7.9 权限 |
| 测试 | 第 11 章 | 第 13 章追踪矩阵 |
| AI 代理生成代码 | 第 5 章结构、第 7 章算法、第 8 章接口 | 第 10 章红线（强制） |

### 版本记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0 | 2026-11-02 | 首版；对齐 spec v1.1（含实时客服） |
| v1.1 | 2026-11-02 | 补充认证与令牌续期（双 Token + 滑动续期 + 无感替换）、幂等防刷（Redis SETNX + DB 唯一索引兜底）、危险操作二次确认（输入确认短语 + ConfirmToken）；修正 agents.md 截断 |
| v1.2 | 2026-11-02 | 按评审意见收敛与加固：取消 `job_lock` 改为单机串行调度（表 55→54）、库存增加三层并发防护与压测指标、取价改为严格优先级并加防倒挂（同步修订 spec 6.1.2）、令牌续期改为复用开源 axios 封装、幂等明确唯一索引 DDL 为必建项 |
| v1.3 | 2026-11-02 | 新增 ADR-13 与 9.4 节：管理端以 `pure-admin-thin` v6.2.0（MIT）为基座改造，明确"采用/改造/重写/移除"四类清单与 D1–D10 差异点；技术栈版本对齐模板基线 |

---

## 1. 架构决策记录（ADR）

> 每条决策给出「选择 / 理由 / 被否方案 / 影响面」。若需变更，只改对应 ADR 并同步标注的影响面，不必重写全文。

### ADR-01 采用模块化单体，不拆微服务

- **选择**：单一 Spring Boot 应用，按业务域分包（product / order / stock / finance / chat ...），模块间只通过 Service 接口与领域事件交互。
- **理由**：订单、库存、退款、应收之间存在强事务一致性要求（如"退款成功 → 回补库存 → 扣回积分 → 释放额度"必须同事务或可补偿）；6 角色 RBAC 与报表需要跨域联表；团队规模不足以承担分布式事务与运维成本。
- **被否**：微服务（拆 6 个服务）——分布式事务、链路追踪、部署复杂度全部上升，收益为零。
- **影响面**：第 4 章包结构、第 7 章事务边界、第 12 章部署。
- **演进预留**：`chat` 模块与其他模块无数据库级联，未来可独立拆出（WebSocket 是唯一有状态部分）。

### ADR-02 后端选 Java 17 + Spring Boot 3

- **选择**：Spring Boot 3.2 + Spring Security + MyBatis-Plus + MySQL 8.0 + Redis 7。
- **理由**：本项目的核心难度在"状态机 + 事务 + 并发扣减 + RBAC 数据权限"，Spring 生态在这四件事上最成熟、示例最多、代码生成最稳定；MyBatis-Plus 的条件更新（`UPDATE ... WHERE 可用 >= ?`）正好用于库存原子扣减。
- **被否**：NestJS（TS 全栈更统一，但复杂事务与并发控制示例少）、FastAPI（同样问题，且财务精度处理需额外小心）。
- **影响面**：第 2 章技术栈、第 7.2 库存实现。
- **切换成本**：仅第 3–8 章，需求与表结构不变。

### ADR-03 金额一律用 BIGINT 存「分」

- **选择**：所有金额字段命名以 `_cent` 结尾，类型 `BIGINT`；Java 用 `Long`；前端仅展示时除以 100。
- **理由**：spec 第 11 章明确"金额以分为最小单位，禁止浮点误差"。`DECIMAL` 虽可用，但 Java 侧仍易被 `double` 污染；整数分从根上杜绝。
- **被否**：`DECIMAL(12,2)` + `BigDecimal`（每处运算都要设 scale 与舍入模式，漏一处就出偏差）。
- **影响面**：第 6 章全部表、第 7.1 取价、7.6 退款分摊、第 8 章 DTO。
- **强制**：禁止在任何 DTO 中出现 `double`/`float` 金额字段；CI 加静态检查。

### ADR-04 主数据状态用「字符串枚举码」，不用数字

- **选择**：`status VARCHAR(32)`，取值如 `WAIT_PAY`、`PART_SHIPPED`。
- **理由**：spec 定义了 4 个并行状态维度，数字码在日志、排障、SQL 直查时不可读；字符串码自解释，且新增状态不需要重新分配数字。
- **被否**：`TINYINT`（省 3 字节，代价是所有人查库都要对照字典）。
- **影响面**：第 6 章所有状态字段。

### ADR-05 实时客服走原生 WebSocket + 自定义 JSON 信封，不用 STOMP

- **选择**：`/ws/chat` 端点，`TextWebSocketHandler`，消息体为自定义信封（含 `type`/`seq`/`clientMsgId`）。
- **理由**：spec 6.10.4 要求"不丢不重、保持顺序、断线补拉、发送状态可感知"，这些需要应用层自己控制序号与 ACK。STOMP 的心跳与订阅语义帮不上忙，反而多一层协议要解释。
- **被否**：STOMP over SockJS（兼容性好但协议重）、轮询（达不到 1 秒送达）。
- **影响面**：第 7.8 章协议、第 9 章前端 `useChat` 组合式函数。

### ADR-06 库存预占用「条件更新 + 有限重试」，分布式锁只作为可选开关

- **选择**：主链路用一条条件更新 `UPDATE sku_stock SET locked_qty = locked_qty + :qty WHERE sku_id = :id AND (physical_qty - locked_qty - pending_qty - frozen_qty) >= :qty`，按影响行数判断；对"锁等待超时/死锁"做**最多 2 次退避重试**（20ms、50ms）；对极热点 SKU 提供 **Redis 按 SKU 加锁**的可选开关（默认关闭）。
- **理由**：单条 SQL 已保证"校验+扣减"原子，正常并发下不会超卖。但 1000 请求抢 1 件时，失败请求会在 InnoDB 行锁上排队，拖长响应。分三层应对：① 条件不满足立即失败（不排队等锁）；② 死锁类异常才重试，且限次退避；③ 真出现热点（如同一秒数百次同 SKU 下单）时，用 Redis 锁把并发挡在 DB 之前，把"1000 个请求等行锁"变成"999 个请求快速失败"。
- **被否**：全程 Redis 预扣（缓存与 DB 双写不一致，补偿复杂）；全程 `SELECT FOR UPDATE`（吞吐更低）。
- **纠偏说明**：Redis 锁**不提升吞吐**，只是把排队位置从 DB 挪到 Redis 并缩短单请求占用时间；因此默认不开，仅在压测证明存在热点时开启。
- **影响面**：第 7.2 章、第 11.2 节并发与压测、14.1 风险表。
- **验收指标**：JMeter 500 并发抢同一 SKU 最后 1 件 → 成功恒为 1、`physical_qty` 不为负、无重复订单；混合场景 500 并发下单 → QPS ≥ 300、P95 < 500ms、错误率 < 0.1%（4C8G 单机基线，见 11.3）。

### ADR-07 权限 = RBAC + 数据范围，字段脱敏单独做

- **选择**：`role.data_scope`（`ALL`/`TEAM`/`SELF`）控制"能看到谁的数据"；字段级可见性用注解 + DTO 投影控制（如仓管看不到成交价）。
- **理由**：spec 3.2 与第 10.9 节的区别是"范围"与"字段"两类问题，混在一个模型里会失控。SQL 层加范围条件，序列化层做字段裁剪，各自可测。
- **被否**：纯 RBAC（无法表达"销售员只看自己客户"）、纯数据权限表（配置爆炸）。
- **影响面**：第 7.9 章、第 9 章路由守卫。

### ADR-08 定时任务用「单实例 @Scheduled 串行 + 条件更新保证幂等」，不建 job_lock

- **选择**：Spring `@Scheduled` 默认单线程调度器，同一时刻只有一个任务在执行，天然不会并发；任务体内部用条件更新（`WHERE status='WAIT_PAY'`）保证"即便被重复触发也只生效一次"。代码中保留 `DistributedLock` 接口与注释，标注"单机版无需，扩容时接 ShedLock/Redis 实现"。
- **理由**：单机部署下引入 `job_lock` 表只是多一张表、多一次写、多一处可能出错，收益为零。真正的幂等保障来自业务条件更新，而不是"谁抢到了锁"。
- **被否**：`job_lock` 表（原始方案，单机冗余）；Redis 分布式锁（同上）；MQ 延迟队列（对 10 个定时任务过重）。
- **代价与规避**：单线程调度意味着"一个慢任务会顶住后面的任务"。因此约定：① 单个任务处理上限 500 条/次，超出留到下一轮；② 任务体内禁止慢查询与外部 HTTP；③ 关键任务（订单超时、自动收货）单独指定 `scheduling.pool.size` 可调，必要时改为双线程池。
- **演进预留**：`DistributedLock` 接口已抽出，扩容到多实例时只需换成 `ShedLock` 实现（`@SchedulerLock` 注解），业务代码不动。
- **影响面**：第 7.10 章、第 6.2 节（不再建 `job_lock` 表）。

### ADR-09 图稿与发票文件走本地对象目录，接口层抽象

- **选择**：定义 `FileStorage` 接口，一期实现 `LocalFileStorage`（`/data/upload/{yyyyMM}/{uuid}.{ext}`），对外只暴露 `/api/files/{id}` 下载。
- **理由**：一期单机部署，无需对象存储；抽象后换 OSS/S3 只改一个实现类。
- **影响面**：第 6 章 `file_object` 表、第 8 章上传接口。

### ADR-10 双前端两个独立工程，共享类型与组件库

- **选择**：`web-user`（商城）与 `web-admin`（后台）为两个 Vite 工程，抽 `packages/shared`（类型定义、金额格式化、请求封装）。
- **理由**：两端设计语言与权限模型差异大，强行合并会导致路由与布局互相污染；共享层只放无 UI 的逻辑。
- **影响面**：第 4 章目录、第 9 章页面。

### ADR-11 认证采用「短期 Access Token + 长期 Refresh Token + 滑动续期」

- **选择**：Access Token（JWT，2 小时）+ Refresh Token（不透明串，7 天，Redis 存储）；每次请求若 Access 剩余有效期 < 15 分钟，由响应头 `X-New-Token` 下发新 Access，前端无感替换；Access 过期时用 Refresh 换新，Refresh 一次性轮换。
- **理由**：单 Token 要么频繁踢人（TTL 短），要么无法吊销（TTL 长）。双 Token 把「高频校验」与「长期信任」分开：短 Token 泄露窗口小，长 Token 可随时在 Redis 删除。滑动续期让活跃用户在操作期间永不掉线，避免「填完表单点提交时提示登录过期」这种最伤体验的场景。
- **被否**：纯 Session（双端 + WebSocket 场景下跨端同步麻烦，且不利于后续多端）；纯 JWT 长期有效（无法吊销，权限变更不生效）。
- **影响面**：第 7.11 章、第 8.1/8.3 章、第 9.3 章 axios 拦截器、第 11.2 节测试。
- **术语修正**：题目里说的"双 Token 刷新"更准确的叫法是 **滑动续期（Sliding Renewal）+ 双令牌兜底**：滑动续期解决"活跃用户不被打断"，双令牌解决"过期后还能续"。两者叠加才能覆盖"用户开着页面 3 小时后才提交"的场景。

### ADR-12 幂等采用「Redis SETNX 前置 + 数据库唯一索引兜底」双层

- **选择**：自定义 `@Idempotent` 注解 + 拦截器，以 `Idempotency-Key` 为键在 Redis `SETNX`；同时落库的业务表加唯一索引（如 `orders.idem_key`）作为第二道防线。
- **理由**：只用 Redis 时，Redis 重启/主从切换会丢失键，形成重复下单；只用唯一索引时，并发请求都会打到数据库并抛异常，体验差且噪音大。两层叠加：Redis 拦住 99% 的重复点击并快速返回，DB 保证"即便 Redis 挂了也绝不出现两笔订单"。
- **被否**：仅前端按钮置灰（刷新页面即失效）、仅分布式锁（锁的是"执行中"，不解决"重试已成功的请求"）。
- **影响面**：第 7.12 章、第 6.2 节 `orders` 表、第 8.1 章、第 11.2 节测试。

### ADR-13 管理端以 `pure-admin-thin`（MIT）为基座改造，用户端自建

- **选择**：管理端 fork `pure-admin-thin` v6.2.0 作为脚手架（保留布局、表格栏、弹窗、权限指令、构建配置），业务页面全部自写；用户端不套模板，独立设计。
- **理由**：管理端 30% 的工作是布局、页签、表格分页、弹窗、暗色主题这类"每个后台都一样"的重复劳动，模板已经打磨多年且是 MIT（可商用）。而商城端需要完全不同的视觉与信息结构，套后台模板会做出"后台味的商城"。
- **被否**：两端都用模板（商城体验受损）；两端都自建（管理端多花 3–5 天做布局）；用全量 `vue-pure-admin`（含大量用不上的组件与演示页）。
- **注意**：模板的 HTTP 层、权限模型与我们的设计有 5 处差异（D1–D5），必须改造而非照搬，细则见 9.4.4。
- **影响面**：第 9.4 章、第 4.1 章目录、第 12 章 T-29、技术栈版本锁定（以模板 engines 为准）。

---

## 2. 技术栈（版本锁定）

| 层 | 技术 | 版本 | 用途 |
| --- | --- | --- | --- |
| 语言 | Java | 17 (LTS) | 后端 |
| 框架 | Spring Boot | 3.2.x | Web / 事务 / 定时 |
| 安全 | Spring Security + JWT (jjwt 0.12) | — | 认证与鉴权 |
| ORM | MyBatis-Plus | 3.5.x | CRUD + 条件更新 |
| 数据库 | MySQL | 8.0 | 主存储，`utf8mb4_general_ci` |
| 迁移 | Flyway | 10.x | 版本化 DDL，**禁止 DROP** |
| 缓存 | Redis | 7.x | 会话状态、在线状态、序号生成、分布式锁 |
| 实时 | Spring WebSocket | 内置 | 客服会话 |
| 参数校验 | Jakarta Validation | 内置 | DTO 校验 |
| 文档 | springdoc-openapi | 2.x | 接口文档 |
| 测试 | JUnit 5 + Testcontainers + MockMvc | — | 单测与集成 |
| 前端 | Vue 3 + TypeScript + Vite | 3.5 / 5.9 / 7.1 | 双端 |
| 前端状态 | Pinia | 3.x | 会话、购物车、权限 |
| 前端 UI | Element Plus | 2.11.x | 管理端为主，商城端定制主题 |
| 前端样式 | Tailwind CSS | 4.x | 管理端沿用模板的原子类体系 |
| 管理端基座 | pure-admin-thin | v6.2.0（MIT） | 仅管理端，改造方案见 9.4 |
| 图表 | ECharts | 6.x | 销售额统计图 |
| 包管理 | pnpm | 9.x | 前端 monorepo |
| Node | Node.js | ≥ 20.19 | 与模板 engines 一致 |
| 构建 | Maven 3.9 | — | 后端 |
| 部署 | Docker Compose | — | mysql / redis / app / nginx |

---

## 3. 部署与运行拓扑

```
                    ┌──────────────────────────────┐
  浏览器 ──HTTPS──▶ │  Nginx                        │
  (用户端/管理端)    │  /        → web-user (静态)   │
                    │  /admin   → web-admin (静态)  │
                    │  /api     → app:8088          │
                    │  /ws      → app:8088 (Upgrade)│
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │  app (Spring Boot, 单实例)    │
                    │  REST + WebSocket + Scheduler │
                    └───────┬──────────────┬───────┘
                            │              │
                    ┌───────▼──────┐  ┌────▼──────┐  ┌──────────────┐
                    │ MySQL 8.0    │  │ Redis 7   │  │ /data/upload │
                    │ 业务数据      │  │ 状态/序号  │  │ 图稿与发票   │
                    └──────────────┘  └───────────┘  └──────────────┘
```

**关键约定**

- 时区全链路 `Asia/Shanghai`；数据库连接串显式 `serverTimezone=Asia/Shanghai`。
- 文件上传上限 50MB（对齐 spec 6.5.3）；Nginx `client_max_body_size 60m`。
- WebSocket 需 Nginx 透传 `Upgrade`/`Connection` 头，`proxy_read_timeout 3600s`。
- 一期单实例，因此 WebSocket 会话表放在进程内存 `ConcurrentHashMap<Long, WebSocketSession>`；Redis 仅存"在线状态"与"会话序号"，为多实例预留。

**环境标识（落实 agents.md 第 3 条）**

| 环境 | `app.env` | 数据库 | 危险操作 |
| --- | --- | --- | --- |
| dev | `dev` | `sent_dev` | 允许，需日志 |
| test | `test` | `sent_test` | 允许，需日志 |
| prod | `prod` | `sent_prod` | 需二次确认令牌 |

启动时校验：`app.env` 与 `spring.datasource.url` 中的库名后缀必须匹配，不匹配直接启动失败并打印明确错误。

---

## 4. 工程结构

### 4.1 仓库布局

```
sent-sports/
├── specs/                       # 本文档与 spec.md
├── server/                      # 后端
│   ├── pom.xml
│   └── src/main/java/com/sent/sports/
│       ├── SentSportsApplication.java
│       ├── common/              # 通用能力（不含业务）
│       ├── auth/                # 登录、令牌
│       ├── system/              # 员工、角色、权限、参数、日志
│       ├── customer/            # 客户、企业档案、地址、归属
│       ├── member/              # 等级、成长值、积分
│       ├── product/             # SPU、SKU、分类、定价
│       ├── stock/               # 库存、入库、报损、盘点
│       ├── cart/                # 购物车
│       ├── order/               # 订单、包裹、发货
│       ├── payment/             # 支付流水、回调
│       ├── refund/              # 退款与售后
│       ├── customize/           # 定制单、图稿、审核
│       ├── finance/             # 应收、回款核销、发票、合同
│       ├── chat/                # 实时客服（WS + 会话）
│       ├── notify/              # 站内信、邮件
│       ├── report/              # 三张报表
│       └── job/                 # 定时任务
│   └── src/main/resources/
│       ├── application.yml
│       ├── db/migration/        # Flyway V1__init.sql ...
│       └── mapper/              # MyBatis XML（复杂查询）
├── web-user/                    # 用户端（Vue3 + Vite，独立设计）
├── web-admin/                   # 管理端（基于 pure-admin-thin 改造，见 9.4）
├── packages/shared/             # 共享类型与工具
├── docker/                      # compose + nginx.conf
└── docs/                        # 生成的接口文档、测试报告
```

### 4.2 单模块内部分层（强制统一）

以 `order` 为例，其余模块同构：

```
order/
├── controller/       OrderController, AdminOrderController
├── service/          OrderService(接口), OrderQueryService
├── service/impl/     OrderServiceImpl
├── domain/           Order, OrderItem, OrderStatus(enum), OrderStateMachine
├── mapper/           OrderMapper, OrderItemMapper
├── dto/              CreateOrderCmd, OrderVO, AdminOrderVO
├── event/            OrderPaidEvent, OrderShippedEvent
└── job/              仅 job 模块引用
```

**依赖规则（CI 用 ArchUnit 校验）**

1. `controller` 只能依赖 `service`，不得直接依赖 `mapper`。
2. 跨模块调用只能走对方 `service` 接口，不得直接注入对方 `mapper`。
3. `domain` 不依赖 Spring 注解（纯 POJO + 状态机），保证可单测。
4. `common` 不得依赖任何业务模块。

### 4.3 common 包内容（先建，避免后续重复造）

| 类 | 职责 |
| --- | --- |
| `R<T>` | 统一响应 `{code, message, data, traceId}` |
| `BizException` + `ErrorCode` | 业务异常与错误码枚举 |
| `GlobalExceptionHandler` | 统一异常转响应，禁止把堆栈返回前端 |
| `PageResult<T>` | 分页响应 |
| `MoneyUtil` | 分↔元格式化、按比例分摊（含余数处理） |
| `IdGen` | 业务单号生成（`SO`/`RF`/`CU`/`PR` 前缀 + 日期 + 序列） |
| `CurrentUser` + `@CurrentUserArg` | 当前登录人注入 |
| `@RequirePerm("order:ship")` | 权限注解 |
| `@DataScope` | 数据范围注解（切面注入 SQL 条件） |
| `@Sensitive(hideFor={"WAREHOUSE"})` | 字段脱敏注解 |
| `Idempotent` + `IdempotentInterceptor` | 幂等注解与拦截器（Redis SETNX + 结果快照 + DB 唯一索引兜底），见 7.12 |
| `RateLimit` + `RateLimitInterceptor` | 滑动窗口限流，键 `rate:{principalId}:{path}` |
| `TokenService` | 双令牌签发/校验/续期/吊销，见 7.11 |
| `AuthFilter` | 解析 Token → 注入 `CurrentUser` → 剩余 <15min 时下发 `X-New-Token` |
| `ConfirmToken` | 危险操作二次确认令牌（短语校验 + 绑定目标 + 用后即焚），见 10.1 |
| `TraceFilter` | traceId 生成与透传 |

---

## 5. 模块划分与职责边界

| 模块 | 职责 | 不负责 | 覆盖 spec |
| --- | --- | --- | --- |
| `auth` | 登录、JWT 签发、密码、锁定策略 | 权限判定（在 system） | 3.1, 11 |
| `system` | 员工、角色、权限码、参数、操作日志 | 业务数据 | 3.2, 6.11, 8.11 |
| `customer` | 客户、企业档案、地址、归属与改派 | 等级计算（在 member） | 3.3, 6.8.1, 8.6 |
| `member` | 等级、成长值、积分 | 折扣计算（在 product 定价） | 6.3, 6.4, 8.7 |
| `product` | SPU/SKU、分类、零售价/会员价/协议价/阶梯价、一客一价、上下架、可见等级 | 库存数量 | 6.1, 6.3.1, 7.2, 7.3, 8.2 |
| `stock` | 库存台账、预占、入库、报损、盘点、流水、预警 | 采购（不做） | 6.2, 8.3, 9.3 |
| `cart` | 购物车、试算 | 下单落库 | 7.4 |
| `order` | 订单、状态机、包裹、拆单发货 | 支付结果写入（在 payment） | 6.6, 7.5, 7.6, 8.4 |
| `payment` | 支付单、模拟支付、回调、幂等 | 订单状态决策（调用 order） | 6.6.3 |
| `refund` | 售后单、退款金额分摊、退款回调 | 回款核销 | 6.7, 7.7 |
| `customize` | 定制单、图稿版本、审核流、排产 | 商品定价 | 6.5, 8.5 |
| `finance` | 应收台账、回款登记、核销、发票、合同 | 退款审核（在 refund，财务仅审批） | 6.8, 6.9, 8.9 |
| `chat` | WebSocket、会话、消息、在线状态、排队、转接 | 客服绩效报表（不做） | 6.10, 8.8, 7.10 |
| `notify` | 站内信、邮件模板与发送 | 业务判断 | 7.10 |
| `report` | 三张报表 | 其他统计 | 9.1–9.3 |

**模块间调用白名单**（防止循环依赖）

| 调用方 | 可调用 | 说明 |
| --- | --- | --- |
| order | stock, product, member, customer, customize, notify | 下单校验与落库 |
| payment | order, notify | 回调驱动订单 |
| refund | order, stock, member, finance, notify | 退款回补与额度释放 |
| customize | notify, product | 审图与工期 |
| chat | customer, order, notify | 会话上下文 |
| report | 各模块只读 Service | 不允许反向写 |

---

## 6. 数据模型

### 6.1 建模原则

1. **订单行是事实，商品是引用**：订单行保存标题、规格、单价、取价来源的快照，商品改价不影响历史（spec 6.1.3）。
2. **状态与流水分离**：任何"数量"或"金额"的变动都写一条流水（`stock_flow`、`payment_txn`、`write_off`），台账可重算。
3. **软删除只用于主数据**（商品、客户、员工），订单类数据永不删除（spec 11）。
4. **不加物理外键**，用索引 + 应用层保证；便于后续分库与数据修复。
5. 所有表带 `created_at DATETIME(3)`、`updated_at DATETIME(3)`、`created_by BIGINT`。

### 6.2 表清单（54 张）

#### 系统与权限（6）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `sys_staff` | id, staff_no, name, phone, email, password_hash, role_id, leader_id, status, last_login_at, fail_count, locked_until, **token_version** | uk(staff_no), uk(phone) |
| `sys_role` | id, code, name, data_scope(ALL/TEAM/SELF), is_builtin | uk(code) |
| `sys_permission` | id, code, name, module, type(MENU/ACTION) | uk(code) |
| `sys_role_permission` | role_id, permission_id | pk(role_id, permission_id) |
| `sys_config` | config_key, config_value, remark | uk(config_key) |
| `sys_operation_log` | id, staff_id, module, action, target_type, target_id, before_json, after_json, reason, ip, trace_id | idx(staff_id, created_at), idx(target_type, target_id) |

#### 客户与会员（9）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `customer` | id, customer_no, phone, password_hash, nickname, email, type(PERSON/ENTERPRISE), level_code, growth_value, points_balance, owner_staff_id, status, **token_version** | uk(phone), idx(owner_staff_id) |
| `enterprise_profile` | customer_id(PK), company_name, license_no, license_url, invoice_title, tax_no, credit_limit_cent, credit_used_cent, payment_term_days, credit_expire_at, status(PENDING/APPROVED/REJECTED) | pk(customer_id) |
| `customer_address` | id, customer_id, receiver, phone, province, city, district, detail, is_default | idx(customer_id) |
| `customer_owner_log` | id, customer_id, from_staff_id, to_staff_id, reason, operator_id | idx(customer_id) |
| `member_level` | code(PK), name, growth_threshold, discount_rate, point_rate, priority | pk(code) |
| `growth_log` | id, customer_id, change_value, balance_after, source_type(ORDER/REFUND/MANUAL), source_id | idx(customer_id, created_at) |
| `point_log` | id, customer_id, change_value, balance_after, type(EARN/USE/EXPIRE/CLAWBACK), source_type, source_id, expire_at | idx(customer_id, created_at) |
| `customer_follow_log` | id, customer_id, staff_id, content | idx(customer_id) |
| `arrival_subscription` | id, customer_id, sku_id, notified | uk(customer_id, sku_id) |

#### 商品与定价（6）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `product` | id, product_no, title, category_id, brand, main_image, images_json, detail_html, params_json, can_customize, min_visible_level, status(DRAFT/ON/OFF/SOLD_OUT), published_at, sales_count | uk(product_no), idx(status, published_at), idx(category_id) |
| `product_category` | id, parent_id, name, sort, icon | idx(parent_id) |
| `sku` | id, spu_id, sku_no, barcode, spec_json, retail_price_cent, floor_price_cent, cost_price_cent, weight_g, status | uk(sku_no), idx(spu_id) |
| `ladder_price` | id, sku_id, min_qty, max_qty, price_cent | idx(sku_id, min_qty) |
| `customer_sku_price` | id, customer_id, sku_id, price_cent, valid_from, valid_to, operator_id | uk(customer_id, sku_id), idx(valid_to) |
| `agreement_price` | id, customer_id, discount_rate, valid_from, valid_to, contract_id, **allow_stacking**(默认 0) | idx(customer_id) |

#### 库存（6）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `sku_stock` | sku_id(PK), location_code, physical_qty, locked_qty, pending_qty, frozen_qty, safety_qty, version | pk(sku_id) |
| `stock_flow` | id, sku_id, biz_type(LOCK/RELEASE/SHIP/INBOUND/LOSS/CHECK/REFUND_IN), change_qty, before_qty, after_qty, biz_no, operator_id, remark | idx(sku_id, created_at) |
| `stock_inbound` | id, inbound_no, sku_id, qty, reason_type(INIT/REPLENISH/RETURN), remark, operator_id | uk(inbound_no) |
| `stock_loss` | id, loss_no, sku_id, qty, reason, images_json, operator_id | uk(loss_no) |
| `stock_check` | id, check_no, status(DRAFT/COUNTED/APPROVED), operator_id, approved_by | uk(check_no) |
| `stock_check_item` | id, check_id, sku_id, book_qty, actual_qty, diff_qty | uk(check_id, sku_id) |

#### 购物车与订单（6）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `cart_item` | id, customer_id, sku_id, qty, customize_flag, custom_config_json, selected, price_snapshot_cent | uk(customer_id, sku_id, customize_flag) |
| `orders` | id, order_no, customer_id, customer_type, owner_staff_id, status, pay_status, custom_status, after_sale_status, goods_amount_cent, custom_fee_cent, discount_cent, freight_cent, point_deduct_cent, payable_cent, paid_cent, received_cent, address_snapshot_json, invoice_flag, remark, expire_at, paid_at, completed_at, version, **idem_key** | uk(order_no), **uk(idem_key)**, idx(customer_id, created_at), idx(status, expire_at), idx(owner_staff_id), idx(pay_status) |
| `order_item` | id, order_id, sku_id, spu_title_snap, spec_snap, qty, unit_price_cent, price_source, price_rule_id, amount_cent, discount_alloc_cent, shipped_qty, custom_flag, custom_fee_cent | idx(order_id), idx(sku_id) |
| `shipment` | id, shipment_no, order_id, carrier, tracking_no, shipped_at, status, operator_id | uk(shipment_no), idx(order_id) |
| `shipment_item` | id, shipment_id, order_item_id, qty | idx(shipment_id), idx(order_item_id) |
| `order_status_log` | id, order_id, from_status, to_status, dimension, operator_id, remark | idx(order_id, created_at) |

#### 支付与退款（5）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `payment_txn` | id, txn_no, order_no, amount_cent, channel, status(CREATED/SUCCESS/FAILED), request_at, callback_at, raw_payload | **uk(txn_no)** ← 幂等基石 |
| `refund_order` | id, refund_no, order_id, order_item_id, type(ONLY_REFUND/RETURN_REFUND), amount_cent, point_refund, status, reason_code, remark, images_json, applicant_type, auditor_id, audit_remark, refunded_at | uk(refund_no), idx(order_id), idx(status) |
| `refund_txn` | id, refund_no, txn_no, amount_cent, status, callback_at, raw_payload | uk(txn_no) |
| `invoice_request` | id, order_id, customer_id, title, tax_no, type(NORMAL/SPECIAL), email, status(APPLIED/ISSUED/VOID), invoice_no, invoice_url, issued_at | idx(order_id) |
| `contract` | id, customer_id, contract_no, sign_date, expire_date, discount_rate, file_url | idx(customer_id), idx(expire_date) |

#### 定制（4）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `custom_order` | id, custom_no, order_item_id, customer_id, service_type(ATTACHED/STANDALONE), print_side(SINGLE/DOUBLE/FULL), qty, fee_cent, status, current_version, reject_count, review_deadline, reviewer_id, production_start_at, finished_at, expect_ship_date, remark | **uk(order_item_id)**, idx(status, review_deadline) |
| `custom_draft` | id, custom_order_id, version, file_id, file_name, file_size, uploaded_at | uk(custom_order_id, version) |
| `custom_review_log` | id, custom_order_id, draft_version, result(PASS/REJECT), reason_code, remark, reviewer_id | idx(custom_order_id) |
| `custom_change_request` | id, custom_order_id, reason, extra_fee_cent, new_expect_date, status | idx(custom_order_id) |

#### 财务（3）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `receivable` | id, order_id, customer_id, amount_cent, received_cent, due_date, status(NORMAL/OVERDUE/CLOSED) | **uk(order_id)**, idx(customer_id, status), idx(due_date) |
| `payment_record` | id, record_no, customer_id, amount_cent, received_date, bank_serial, remark, status(NORMAL/REVERSED), reversed_reason, operator_id | uk(record_no), idx(customer_id) |
| `write_off` | id, payment_record_id, receivable_id, amount_cent | idx(payment_record_id), idx(receivable_id) |

#### 客服（6）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `chat_session` | id, customer_id, staff_id, status(WAITING/ACTIVE/CLOSED), source_type(PRODUCT/ORDER/NONE), source_id, priority, started_at, ended_at, last_msg_at, close_reason | idx(customer_id, status), idx(staff_id, status), idx(status, created_at) |
| `chat_message` | id, session_id, seq, sender_type(CUSTOMER/STAFF/SYSTEM), sender_id, msg_type(TEXT/IMAGE/PRODUCT_CARD/ORDER_CARD), content, payload_json, client_msg_id | **uk(session_id, seq)**, **uk(session_id, client_msg_id)**, idx(session_id, id) |
| `chat_read_cursor` | session_id, side(CUSTOMER/STAFF), last_read_seq | pk(session_id, side) |
| `staff_presence` | staff_id(PK), status(ONLINE/BUSY/OFFLINE), max_sessions, active_sessions, last_heartbeat_at | pk(staff_id) |
| `chat_quick_reply` | id, staff_id, scope(PERSONAL/PUBLIC), content, sort | idx(scope, staff_id) |
| `chat_session_tag` | session_id, tag | pk(session_id, tag) |

#### 通知与基础设施（3）

| 表 | 关键字段 | 索引/约束 |
| --- | --- | --- |
| `notification` | id, customer_id, type, title, content, biz_type, biz_id, read_flag | idx(customer_id, read_flag, created_at) |
| `notify_template` | code(PK), channel, title_tpl, content_tpl | pk(code) |
| `file_object` | id, biz_type(DRAFT/INVOICE/LOSS/RETURN), file_name, path, size, sha256 | uk(sha256, size) |

> `job_lock` 已按 ADR-08 取消：单机单线程调度无需互斥表。

### 6.3 枚举字典（全系统唯一来源，前后端共用）

| 枚举 | 取值 |
| --- | --- |
| `OrderStatus` | `WAIT_PAY` `WAIT_SHIP` `PART_SHIPPED` `WAIT_RECEIVE` `COMPLETED` `CANCELLED` `CLOSED` |
| `PayStatus` | `UNPAID` `PAYING` `PAID` `PART_REFUNDED` `REFUNDED` |
| `CustomStatus` | `NONE` `WAIT_DRAFT` `WAIT_REVIEW` `REJECTED` `APPROVED` `IN_PRODUCTION` `FINISHED` `SHIPPED` `CANCELLED` `ESCALATED` |
| `AfterSaleStatus` | `NONE` `APPLYING` `RETURNING` `REFUNDED` `REJECTED` |
| `RefundStatus` | `APPLIED` `CS_REVIEW` `FINANCE_REVIEW` `REFUNDING` `REFUNDED` `REJECTED` |
| `ChatStatus` | `WAITING` `ACTIVE` `CLOSED` |
| `StaffStatus` | `ONLINE` `BUSY` `OFFLINE` |
| `ReceivableStatus` | `NORMAL` `OVERDUE` `CLOSED` |
| `PriceSource` | `CUSTOMER_SKU` `AGREEMENT` `LADDER` `MEMBER` `RETAIL` |
| `StockBizType` | `LOCK` `RELEASE` `SHIP` `INBOUND` `LOSS` `CHECK` `REFUND_IN` |

> 前后端枚举必须由同一份 JSON 生成（`packages/shared/src/enums`），禁止手工复制，避免"前端以为能退、后端判不可退"。

---

## 7. 关键业务规则的实现方案（对齐 spec 第 6 章）

### 7.1 取价引擎（spec 6.1，**严格优先级 + 防倒挂**）

**实现**：`PricingService.calc(customerId, skuId, qty, orderCtx)`

```
1. 加载上下文：customer(type/level)、企业协议（含是否允许折上折）、SKU 零售价/底价/成本价、阶梯价、一客一价
2. 严格优先级，命中即返回（不比较、不取低）：
   if 命中一客一价（有效期内）            -> return 单价, CUSTOMER_SKU      // 终止
   if 企业客户 且 协议有效               -> return retail * 协议折扣率, AGREEMENT   // 终止，不叠阶梯
   if 命中阶梯区间                      -> return 阶梯价, LADDER           // 终止
   if 等级折扣率 > 0                    -> return retail * 等级折扣率, MEMBER    // 终止
   return retail, RETAIL
3. 例外：客户档案显式开启"折上折"（allowStacking=true）
   -> 协议价 × 阶梯折扣率，且必须通过 4 的双重校验
4. 防倒挂双重校验（任何取价结果都要过）：
   a) final < floor_price_cent        -> 抛 PRICE_BELOW_FLOOR(40920)，禁止下单
   b) final < cost_price_cent         -> 允许成交，但写一条 PRICE_BELOW_COST 告警，进管理端待复核
5. 返回 PriceResult{unitPriceCent, source, ruleId, hitDetail, belowCost}
```

**为什么不能"取低"**（这是本次修订的核心）

| 场景 | 取低的结果 | 严格优先级的结果 |
| --- | --- | --- |
| 企业协议 5 折，买 10 箱触发阶梯 4.5 折 | 4.5 折成交，可能低于成本，**亏本单** | 5 折成交，协议价就是最终价 |
| VIP2 95 折，买 50 个触发阶梯 92 折 | 92 折 | 92 折（阶梯优先于会员，结论相同但规则统一） |
| 协议 5 折 + 阶梯 6 折 | 5 折 | 5 折（结论相同，但不再需要"比大小"这段易错逻辑） |

**规则要点**

1. **命中的第一个就是成交价**，后面的价格类型不再参与计算。这样价格只有一个来源，`price_source` 永远唯一。
2. **协议价吸收阶梯**：企业客户一旦有协议价，阶梯价对该客户自动失效。若确实要给"量大再优惠"，应直接在合同里约定更低折扣，而不是在系统里叠加两套折扣。
3. **折上折需显式开启**，默认关闭，且在客户档案上可见、可审计；开启后仍受底价与成本价双重校验。
4. **阶梯优先于会员**：避免"VIP 大单比普通客户还贵"的观感问题（普通客户买 50 个享 92 折，VIP 反而 95 折）。
5. **一客一价永远终结计算**，它是谈判结果，不再参与任何规则。

**金额计算顺序（必须固定，否则各处算不一致）**

```
行金额 = 单价 × 数量
商品总额 = Σ 行金额
优惠额 = 零售基准 − 商品总额（仅展示用）
+
定制服务费 = Σ 服务费（不参与折扣）
- 积分抵扣 = min(积分/100, 商品总额 × 30%)
+ 运费（默认满 199 元包邮，否则 12 元；可配）
= 应付
```

**分摊算法**（退款与优惠展示都依赖）

`MoneyUtil.allocate(total, weights[])`：按权重比例分配，余数用"最大余数法"补给权重最大者，保证 `Σ 分摊 = total`，不允许出现 1 分钱对不上。

**落库**：`order_item.price_source`、`price_rule_id`、`discount_alloc_cent` 全量写入，管理端订单详情直接读这三个字段展示取价依据（spec 6.1.3 要求可举证）。

**自测要点**：阶梯边界（qty=9/10/49/50）、**协议价命中后阶梯必须失效**、**VIP 大单走阶梯而非会员价**、折上折开启后被底价拦截、一客一价过期当天仍有效、低于成本价产生告警但不阻塞下单。

---

### 7.2 库存预占与并发（spec 6.2）

**核心 SQL（单条原子操作）**

```sql
UPDATE sku_stock
SET locked_qty = locked_qty + #{qty}, updated_at = NOW(3)
WHERE sku_id = #{skuId}
  AND (physical_qty - locked_qty - pending_qty - frozen_qty) >= #{qty};
```

- 影响行数 = 1 → 预占成功；= 0 → 抛 `STOCK_NOT_ENOUGH(40910)`，整单回滚。
- **多行订单**：按 `sku_id` 升序依次预占，任一行失败立即抛异常回滚（已占用的由事务回滚自动释放），避免死锁。

#### 7.2.1 三层并发防护

| 层 | 手段 | 解决的问题 | 代价 |
| --- | --- | --- | --- |
| L1 快失败 | 条件不满足时 `UPDATE` 直接返回 0，**不排队等锁** | 库存本来就没了，没必要进锁队列 | 无 |
| L2 有限重试 | 仅对 `DeadlockLoserDataAccessException` / `LockWaitTimeoutException` 重试 **2 次**，退避 20ms、50ms | 死锁与瞬时锁冲突是"偶发可恢复"，重试能救回 | 无（正常路径不触发） |
| L3 热点隔离（可选，默认关） | 按 `skuId` 加 Redis 锁 `lock:stock:{skuId}`，`tryLock(0, 3s)`，拿不到立即失败 | 1000 请求抢 1 件时，把排队从 InnoDB 挪到 Redis，缩短单请求占用 | 多一次网络往返；**不提升吞吐**，只降低 DB 压力与响应抖动 |

```java
// L2 重试实现（Spring Retry 或手写循环均可）
@Retryable(retryFor = {DeadlockLoserDataAccessException.class, LockWaitTimeoutException.class},
           maxAttempts = 3, backoff = @Backoff(delay = 20, multiplier = 2, maxDelay = 50))
public void lockStock(Long skuId, int qty) { ... }
```

```java
// L3 热点开关（配置 app.stock.hotspot-lock-enabled，默认 false）
if (hotspotLockEnabled) {
    RLock lock = redisson.getLock("lock:stock:" + skuId);
    if (!lock.tryLock(0, 3, TimeUnit.SECONDS)) throw new BizException(STOCK_BUSY);
    try { return doLockStock(skuId, qty); } finally { lock.unlock(); }
}
```

**什么时候该开 L3**：压测显示同一 SKU 的 `LockWaitTimeout` 占比 > 1%，或 P99 明显高于 P95（长尾来自锁排队）。**不要默认开启**——它会让"正常并发"也承担一次 Redis 往返。

#### 7.2.2 压测指标（写入验收）

| 场景 | 并发 | 目标 | 判定 |
| --- | --- | --- | --- |
| 抢最后 1 件 | 500 线程同 SKU | 成功恒为 1，`physical_qty` 不为负，无重复订单 | 硬性 |
| 混合下单（10 个 SKU，库存充足） | 500 线程 | QPS ≥ 300，P95 < 500ms，错误率 < 0.1% | 硬性 |
| 抢最后 1 件（开启 L3 热点锁） | 500 线程 | 失败响应 P95 < 100ms（快失败生效） | 参考 |
| 单 SKU 长稳压测 | 200 线程 × 5 分钟 | 无死锁、无连接池耗尽 | 硬性 |

基线环境：4C8G 单机、MySQL 8 本地实例、连接池 20、JVM 堆 2G。压测脚本随代码入库（`/perf/order-create.jmx`），每次改动库存逻辑必须回归。

**预占记录**：`stock_flow` 写 `LOCK` 流水，`biz_no = order_no`，用于超时释放与对账。

**状态流转**

| 事件 | locked | physical | 说明 |
| --- | --- | --- | --- |
| 下单 | +qty | — | 可售减少 |
| 支付成功 | 不变 | — | 仅标记订单已支付，预占不再超时 |
| 发货 | −qty | −qty | 两个字段同时减，可售不变 |
| 超时取消 | −qty | — | 可售恢复 |
| 退款（未发货） | −qty | — | 可售恢复 |
| 退款（已退货质检合格） | — | +qty | pending → physical |

**可售口径统一**：所有对外接口只暴露 `availableQty = physical − locked − pending − frozen`，`StockVO` 里不放 `physical_qty`，从源头防止前端算错。

**验证方式**：单元级"20 线程抢 1 件"（11.2 `StockTest#lastOne`）+ 500 并发 JMeter（11.3），两者都通过才算"防超卖"达标。

---

### 7.3 支付回调：幂等 + 迟到处理（spec 6.6.3，验收 A2/A14）

**接口**：`POST /api/payment/mock/callback`（同样路径提供给未来真实渠道），Header `X-Sign`（HMAC-SHA256，密钥来自配置）。

**处理流程（顺序不可调换）**

| 步 | 动作 | 失败处理 |
| --- | --- | --- |
| 1 | 验签 | 返回 `SIGN_INVALID`，不落库 |
| 2 | `INSERT payment_txn(txn_no, ...)` 唯一键冲突 → 已处理过，**直接返回 SUCCESS** | 幂等保证只入账一次 |
| 3 | 校验 `amount == order.payable_cent` | 不一致 → 记 `MISMATCH` 告警日志，返回 FAIL，人工介入 |
| 4 | `SELECT ... FOR UPDATE` 锁订单行 | — |
| 5 | 若订单 `CANCELLED` → 生成 `refund_order`（`ONLY_REFUND`，原因 `PAID_AFTER_CANCEL`），置 `REFUNDING`，调模拟退款，返回 SUCCESS | 满足 A2：订单不复活 |
| 6 | 若 `WAIT_PAY` → 置 `WAIT_SHIP` + `pay_status=PAID`，写 `order_status_log`，发 `OrderPaidEvent` | — |
| 7 | 已在 `PAID` 状态 → 幂等返回 SUCCESS | — |

**为什么第 2 步先插流水再改订单**：唯一索引是幂等的天然屏障；先改状态后插流水，一旦并发就会重复入账。

**前端配合**：支付页不判定成功，只轮询 `GET /api/orders/{orderNo}/pay-result`，服务端返回 `PAID/FAILED/PENDING` 三态（spec 6.6.3）。

---

### 7.4 拆单发货与状态重算（spec 6.6.5，验收 A16）

**数据结构**：`shipment` + `shipment_item`（记录"本次这个订单行发了多少"），`order_item.shipped_qty` 累加。

**发货校验**

```
1. 订单状态 ∈ {WAIT_SHIP, PART_SHIPPED}
2. 每个明细：qty <= order_item.qty - order_item.shipped_qty
3. 若该订单行有定制单：custom_order.status ∈ {FINISHED} 才允许发（APPROVED/IN_PRODUCTION 不可发）
4. 扣库存：physical -= qty, locked -= qty（条件更新，防超发）
```

**状态重算（集中在一个方法，禁止各处各自判断）**

```java
void recalcShipStatus(Long orderId) {
    int total = Σ order_item.qty;
    int shipped = Σ order_item.shipped_qty;
    if (shipped == 0)            status = WAIT_SHIP;
    else if (shipped < total)    status = PART_SHIPPED;
    else                         status = WAIT_RECEIVE;
}
```

**收货**：`shipment.status` 逐个置 `RECEIVED`；当所有包裹均为 `RECEIVED` → 订单 `COMPLETED`，写完成时间、发成长值与积分（同一事务）。

**自动确认**：定时任务扫 `WAIT_RECEIVE` 且最后包裹 `shipped_at + 15 天 < now`，批量置完成（spec 6.6.5）。

---

### 7.5 定制审核状态机（spec 6.5，验收 A5/A6/A7）

**状态机实现**：`CustomStateMachine`（纯 Java，无 Spring 依赖），维护 `Map<from, Set<to>>`，所有变更走 `transit(customOrder, action, operator)`，非法迁移抛 `STATE_CONFLICT(40901)`。

| 动作 | from → to | 前置校验 | 后置动作 |
| --- | --- | --- | --- |
| `submitDraft` | `WAIT_DRAFT`/`REJECTED` → `WAIT_REVIEW` | 版本号 +1，文件已上传，必须有附件 | 写 `custom_draft`，设 `review_deadline = now + 24h` |
| `pass` | `WAIT_REVIEW` → `APPROVED` | 审核人具备 `custom:review` | 锁定版本，写 `custom_review_log`，通知客户 |
| `reject` | `WAIT_REVIEW` → `REJECTED` | `reason_code` 必填 | `reject_count + 1`，写日志，通知客户 |
| `resubmit` | `REJECTED` → `WAIT_REVIEW` | `reject_count >= 3` → 转 `ESCALATED` 而非 `WAIT_REVIEW` | 主管可见待办 |
| `startProduction` | `APPROVED` → `IN_PRODUCTION` | 订单已支付 | 记 `production_start_at` |
| `finish` | `IN_PRODUCTION` → `FINISHED` | — | 记 `finished_at` |
| `cancel` | `WAIT_DRAFT`/`WAIT_REVIEW`/`REJECTED` → `CANCELLED` | 未排产 | 触发全额退款 |
| `ship` | `FINISHED` → `SHIPPED` | 随包裹发货 | 由发货流程回调 |

**与订单的联动**：订单行 `custom_flag=true` 时，订单详情页的"可发货"判断必须读定制单状态；`custom_status` 是订单上的冗余字段，由定制单变更事件同步（便于列表筛选，spec 6.6.2）。

**图稿版本**：`custom_draft` 不 update，只 insert；`custom_order.current_version` 指向最新。旧版本查询走 `version` 降序，天然"旧版本只读不可删"。

---

### 7.6 退款金额与分摊（spec 6.7.2）

**可退额计算**

```
行可退上限 = 行实付 = 行金额 − 行优惠分摊
已退额   = Σ 该行历史已退款
本次可退 = 行可退上限 − 已退额
```

**整单退**：`Σ 行实付 + 运费（未发货时）`。
**部分退**：仅按行，不退运费；且**不补扣运费**（spec 6.7.2 定稿）。

**积分抵扣的处理**：订单上 `point_deduct_cent` 按行金额比例分摊到行；退款时按"先退积分、不足折现"处理：

```
应退积分 = 该行分摊的抵扣分 × 100
若 point_log 显示已过期 → 按 应退积分/100 元 计入现金退款
```

**状态与库存联动（同一事务）**

```
财务审核通过
  → refund_order.status = REFUNDING
  → 调模拟退款回调
  → REFUNDED
  → 订单 pay_status: 全退=REFUNDED, 部分=PART_REFUNDED
  → 标品：pending_qty += qty（等质检），定制件：不回补
  → 扣回成长值 + 积分
  → 释放信用额度（企业客户）
  → 全部退完 → 订单 CLOSED
```

**风控点**：`refund_order.amount_cent` 必须 ≤ 可退上限，服务端二次校验（前端传什么都不可信）。

---

### 7.7 回款核销（spec 6.8.4，验收 A9/A18）

**核销算法（事务内）**

```
输入：paymentRecordId, List<(receivableId, amount)>
1. 锁 payment_record（FOR UPDATE），校验 status = NORMAL
2. Σ amount <= payment_record.amount_cent − 该笔已核销额   → 否则 40930 超额核销
3. 逐条锁 receivable：
   amount <= receivable.amount_cent − receivable.received_cent  → 否则 40930
4. 插 write_off、累加 received_cent
5. received_cent >= amount_cent → receivable.status = CLOSED
6. 企业客户 credit_used_cent -= 本次核销额（不低于 0）
7. 若订单 pay_status 仍为 UNPAID 且有账期 → 置 PAID（账期单以核销视为收款）
8. 写 order_status_log、发通知
```

**不允许预收款**：`Σ amount` 必须等于本次核销总额，多余金额没有去处（spec 6.8.4 明确砍掉预收款），接口直接拒绝而不是"挂账"。

**逾期**：定时任务每日 00:10 扫 `receivable where status=NORMAL and due_date < today` → `OVERDUE`，同时把客户 `credit_frozen=true`，并生成三条通知（销售员/财务/主管按天数）。

---

### 7.8 实时客服协议（spec 6.10，验收 A17/A20/A21/A22）

#### 连接与鉴权

- 端点 `ws://host/ws/chat?token=<JWT>`；握手时从 token 解析 `customerId` 或 `staffId`（两侧共用端点，按角色分流）。
- 长连接跨过 Access 有效期（2 小时）时的重认证流程见 **7.11.5**，不可"连上就不管"。
- 心跳：客户端每 25s 发 `PING`，服务端回 `PONG`；60s 未收到 → 服务端关闭，客户端指数退避重连（1s/2s/4s/8s，上限 10s）。

#### 消息信封（统一 JSON）

```json
{
  "type": "MSG | ACK | READ | PRESENCE | QUEUE | SESSION | SYSTEM | ERROR",
  "sessionId": 1024,
  "seq": 57,
  "clientMsgId": "c-8f3a...",
  "msgType": "TEXT | IMAGE | PRODUCT_CARD | ORDER_CARD",
  "content": "这个球能印队徽吗",
  "payload": { "skuId": 12, "title": "...", "priceCent": 12900 },
  "ts": 1762000000000
}
```

#### 发送链路（保证不丢不重、有序）

| 步 | 动作 |
| --- | --- |
| 1 | 客户端生成 `clientMsgId`（UUID），本地以"发送中"展示，进入待确认队列 |
| 2 | 服务端收到 `MSG`：先按 `uk(session_id, client_msg_id)` 插入，冲突则视为重发，回原 `seq` |
| 3 | 分配 `seq = Redis INCR chat:seq:{sessionId}`，落库 `chat_message` |
| 4 | 回 `ACK{clientMsgId, seq, ts}` → 客户端把气泡改为"已送达" |
| 5 | 推送给对端 `MSG{seq, ...}` |
| 6 | 3s 内未收到 `ACK` → 客户端重发（同 `clientMsgId`，服务端幂等） |

**顺序保证**：接收端按 `seq` 排序渲染，不依赖到达顺序；`seq` 连续性用于补拉（缺号即漏消息）。

**断线重连**：重连成功后调 `GET /api/chat/sessions/{id}/messages?afterSeq={lastSeq}` 补拉，服务端返回 `seq > lastSeq` 全部消息；客户端合并去重后渲染，并提示"已重新连接"（验收 A21）。

**已读**：客户端滚动到底部发 `READ{lastReadSeq}` → 更新 `chat_read_cursor` → 推给对端更新"已读"标记（仅客服侧展示客户是否已读，spec 6.10.3）。

#### 分配与排队（验收 A20）

```sql
-- 找一个可接待的客服（原子：选中即占位）
SELECT staff_id FROM staff_presence
WHERE status = 'ONLINE' AND active_sessions < max_sessions
ORDER BY active_sessions ASC, staff_id ASC
LIMIT 1 FOR UPDATE;
```

- 占位成功后 `active_sessions + 1`，`chat_session.status = ACTIVE`。
- 无可用客服 → `WAITING`，推 `QUEUE{position, estimatedWaitSec}`；客户发消息照常落库（`session WAITING` 也允许发，符合 A22）。
- 触发重分配的三个时机：客服置 `ONLINE`、客服结束会话、定时器每 10s 补偿。
- 优先级：`priority` 字段，VIP/企业客户 = 1，普通 = 0；排序 `ORDER BY priority DESC, created_at ASC`。
- 客服掉线：`last_heartbeat_at` 超 60s → 置 `OFFLINE` 并释放 `active_sessions`（会话保留，10 分钟后仍未恢复的会话转回 `WAITING` 重新分配，spec 10.8 第 55 条）。

#### 会话结束

- 客服/客户主动结束 → `CLOSED` + `close_reason`。
- 定时任务每 5 分钟扫 `ACTIVE and last_msg_at < now - 30min` → `CLOSED`，写系统消息（spec 6.10.5）。
- 结束后客户再发起 → **新建会话**，不复用旧会话（spec 6.10.2）。

#### 并发上限

单实例内存持有 `sessionId → WebSocketSession`，另用 `Redis Set` 记录"该会话在线端"，便于多实例广播（一期可只做单实例，但接口留 `broadcast(sessionId, msg)` 抽象）。

---

### 7.9 权限与数据范围（spec 3.2、15.5、第 10.9 节）

#### 权限码规范

`{module}:{action}`，如 `order:read` `order:ship` `custom:review` `finance:writeoff` `system:role` `chat:serve`。

#### 数据范围注入

| `data_scope` | SQL 追加条件 | 适用角色 |
| --- | --- | --- |
| `ALL` | 无条件 | 超管、财务 |
| `TEAM` | `owner_staff_id IN (自己 + 直接下属)` | 销售主管 |
| `SELF` | `owner_staff_id = 自己` | 销售员、客服（客户维度） |

实现：`@DataScope(alias="o", column="owner_staff_id")` 切面在 Mapper 执行前改写 SQL（MyBatis 拦截器），**导出与列表走同一拦截器**，避免"列表看不到但能导出"（spec 11 权限条）。

#### 字段脱敏

| 场景 | 处理 |
| --- | --- |
| 仓管看订单 | `address` 保留收货信息；`customer.phone` 中间四位掩码；不返回 `unit_price_cent`/`amount_cent` |
| 销售员导出客户 | 手机号掩码 `138****1234` |
| 客服看订单 | 返回 `amount_cent`，不返回 `payment_txn.txn_no` |

实现：`@Sensitive(hideFor = {"WAREHOUSE"})` 标在 VO 字段上，Jackson 序列化时按当前角色置 `null`。

#### 前端

- 登录返回 `permissions[]` 与 `dataScope`；路由守卫按 `meta.perm` 过滤，菜单由后端下发的权限树生成（不写死）。
- **前端隐藏只是体验，后端必须再校验**（每个 Controller 方法必须标 `@RequirePerm`，CI 扫描未标注的写接口）。

---

### 7.10 定时任务清单（spec 11 时效表）

| 任务 | 周期 | 逻辑 | 幂等保障 |
| --- | --- | --- | --- |
| 订单超时取消 | 60s | `WAIT_PAY and expire_at < now` → `CANCELLED`，释放预占 | `UPDATE ... WHERE status='WAIT_PAY'` |
| 自动确认收货 | 10min | 最后包裹发出 15 天 → `COMPLETED` | 同上 |
| 应收逾期标记 | 每日 00:10 | `NORMAL and due_date < today` → `OVERDUE`，冻结账期 | 条件更新 |
| 积分过期 | 每日 01:00 | `expire_at < today and 余额>0` → 写 `EXPIRE` 流水 | 以流水存在性判重 |
| 合同到期提醒 | 每日 09:00 | `expire_date - 30d` → 通知销售员 | 通知表去重 |
| 库存预警 | 每小时 | `available < safety_qty` → 生成清单（报表实时算，不落表） | 只读计算 |
| 会话超时结束 | 5min | `ACTIVE and last_msg_at < now-30min` → `CLOSED` | 条件更新 |
| 客服掉线回收 | 60s | `last_heartbeat_at < now-60s` → `OFFLINE` | 条件更新 |
| 审图超时提醒 | 10min | `WAIT_REVIEW and review_deadline < now` → 通知客服与主管（spec 6.5.4） | 通知表按 `biz_id+type` 去重 |
| 退款审核超时提醒 | 30min | `FINANCE_REVIEW and applied_at < now-48h` → 通知财务与主管（spec 6.7.3） | 同上 |

**调度机制（ADR-08 落地，单机版）**

```java
@Configuration
@EnableScheduling
public class ScheduleConfig {
    // 单机部署：@Scheduled 默认使用单线程调度器（poolSize=1），任务天然串行，不会并发
    // 扩容到多实例时，在此注入 ShedLock 的 LockProvider，并给任务加 @SchedulerLock 即可
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);                       // 保持串行，避免同一批数据被两个任务同时改
        s.setThreadNamePrefix("sent-job-");
        s.setErrorHandler(new LoggingErrorHandler());
        return s;
    }
}
```

| 约定 | 说明 |
| --- | --- |
| 幂等来源 | **业务条件更新**（`WHERE status='WAIT_PAY'`），不是锁；即便重复触发也只生效一次 |
| 单次处理量 | 每个任务每轮最多 500 条，剩余留到下一轮，避免长事务 |
| 任务体内禁止 | 慢查询、外部 HTTP 调用、全表扫描 |
| 异常策略 | 单条任务失败记日志继续，不因一条数据中断整批 |
| 分布式扩展点 | `DistributedLock` 接口 + `@SchedulerLock` 注解已预留；多实例时换实现，业务代码零改动 |

> **不再建 `job_lock` 表**。单机场景下它只是一张永远只有一行的表，既增加写库次数，也让人误以为有并发保护而放松条件更新——真正的幂等必须写在 SQL 里。

### 7.11 认证与令牌续期（ADR-11 落地）

#### 7.11.1 两种令牌的分工

| 令牌 | 形态 | 有效期 | 存放 | 用途 |
| --- | --- | --- | --- | --- |
| Access Token | JWT（HS256） | 2 小时 | 前端内存/localStorage | 每次请求携带，服务端无状态校验 |
| Refresh Token | 32 字节随机串（不透明） | 7 天 | Redis：`rt:{aud}:{userId}:{deviceId}` → `jti` | 仅用于换新 Access，一次性轮换 |

**Access Token 载荷**

```json
{
  "sub": "10086",
  "aud": "user",           // user | admin，两端令牌互不通用
  "role": "SALES",
  "jti": "9f2c...",        // 唯一标识，用于吊销
  "ver": 3,                // 对应 token_version，改密/强制下线时 +1
  "dev": "web-6b1e",       // 设备标识，多端登录时区分
  "iat": 1762000000,
  "exp": 1762007200
}
```

> **关键设计**：Access 里只放 `role` 编码，**不放权限清单**。权限判定每次从 Redis 读（`perm:{roleCode}`，TTL 60s）。否则管理员改了角色权限，用户要等 2 小时才生效。

#### 7.11.2 滑动续期（无感替换）

服务端在鉴权过滤器中执行：

```java
long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
if (remaining > 0 && remaining < RENEW_THRESHOLD_MS) {     // 15 分钟
    String newToken = tokenService.issueAccess(principal);  // 同 jti 链、ver 不变、exp 顺延
    response.setHeader("X-New-Token", newToken);
    response.setHeader("X-Token-Expire-In", "7200");
}
```

- 阈值 15 分钟可由 `app.auth.renew-threshold-minutes` 配置。
- **只续 Access，不动 Refresh**，避免每个请求都写 Redis。
- CORS 必须 `Access-Control-Expose-Headers: X-New-Token,X-Token-Expire-In,X-Idempotent-Replay`，否则浏览器读不到。

#### 7.11.3 过期后的续期（双令牌兜底）

| 场景 | 服务端 | 前端 |
| --- | --- | --- |
| Access 未过期 | 正常返回，可能附带 `X-New-Token` | 有则**静默替换**，无则忽略 |
| Access 过期 | 返回 `401` + `code=40102`（TOKEN_EXPIRED） | 暂停当前请求 → 用 Refresh 换新 → 重放原请求一次 |
| Refresh 也失效 | 返回 `401` + `code=40103`（REFRESH_INVALID） | 清本地 Token → 跳登录页，登录后回跳原页面 |
| 任意接口返回 40102 | 不区分接口，统一处理 | **单飞队列**：并发 5 个请求同时 401，只发 1 次 refresh，其余排队等结果 |

##### 落地方式：**不要手写，直接复用开源封装**

这套"单飞刷新 + 队列重放 + 防重复跳转"的逻辑，业界模板已经打磨过很多轮，手写极容易出现 Promise 永不 resolve（导致所有请求挂起）或刷新风暴。**直接拿现成的改**：

| 参考项目 | 关键文件（以对应 tag 为准） | 可直接复用 | 本项目需改造 |
| --- | --- | --- | --- |
| **vue-pure-admin** | `src/utils/http/index.ts`（`PureHttp` 类） | 请求/响应拦截、Token 存取、refresh 后重放队列、错误码分流、白名单 | Token 头名、40102/40103 分支、`X-New-Token` 静默替换 |
| **vben-admin** | `src/utils/http/axios/index.ts` + `checkStatus.ts`（`VAxios`） | 并发刷新队列（`queues`）、请求重试、重复请求取消、loading 管理 | 同上；另需去掉其自带的加密/签名逻辑 |
| `axios-retry` | — | 纯网络错误重试 | 需与单飞队列隔离，避免"刷新失败又触发重试"双循环 |

**必须自己补的 4 点（模板里没有）**

| 编号 | 差异点 | 做法 |
| --- | --- | --- |
| D1 | 双端令牌隔离 | `web-user` 与 `web-admin` 各自 baseURL、storage key、`aud`，禁止共用一份 token |
| D2 | 提前续期 | 模板普遍只有"过期后刷新"，需补 `X-New-Token` 的静默替换 |
| D3 | 错误码分流 | `40102` 走刷新，`40103` 直接登出，不能一律当"未登录" |
| D4 | 防旧盖新 | 只接受 `exp` 更大的新 Token（模板通常无条件覆盖） |

**接入方式**：把模板的 `http/index.ts` 复制到 `packages/shared/http.ts`，按 D1–D4 改造，两端共用。**不要**在两端各写一份，否则修 bug 要改两次。

**参考实现的核心逻辑（仅供理解，不要照着手写）**

```ts
// 单飞：并发 401 只发一次刷新
let refreshing: Promise<string> | null = null;

if (res.headers['x-new-token']) {
  // D4：只接受 exp 更大的，防并发旧盖新
  if (expOf(res.headers['x-new-token']) > expOf(getToken())) setToken(res.headers['x-new-token']);
}
if (res.data.code === 40102) {
  refreshing ??= refreshApi().finally(() => { refreshing = null; });
  const token = await refreshing;
  return http.request({ ...res.config, headers: { ...res.config.headers, Authorization: `Bearer ${token}` } });
}
```

**为什么必须"防旧盖新"**：页面并发 10 个请求，可能几乎同时各自带回一个新 Token。若按响应到达顺序覆盖，先发出的（exp 较小）可能后到，把有效的新 Token 覆盖成旧的，导致下一次请求立刻过期。

**验收**：用 Playwright 跑 11.2 的 `AuthTest` 前端用例（并发 401、断网重连、刷新失败跳登录），不允许"看着像能用"就合并。

> 抄开源代码时注意：锁定对应版本 tag、保留 LICENSE 声明、只搬需要的文件，不要整包引入（vue-pure-admin 全量引入会带进一堆用不上的组件与主题）。

#### 7.11.4 Refresh Token 轮换与失效

| 动作 | 处理 |
| --- | --- |
| 刷新成功 | 删除旧 Refresh 键，签发新 Refresh（**一次性使用，防重放**） |
| 登出 | 删该设备 Refresh 键 + 当前 `jti` 进黑名单（TTL = 剩余有效期） |
| 改密码 / 管理员强制下线 | `customer.token_version++` 或 `sys_staff.token_version++`，所有旧 Token 因 `ver` 不匹配立即失效 |
| 多端登录 | 同一账号每端一个 `deviceId`，互不影响；"退出所有设备"清空该用户所有 `rt:*` 键并 `token_version++` |
| 角色变更 | 清 `perm:{roleCode}` 缓存，权限 60 秒内生效，无需重新登录 |

#### 7.11.5 WebSocket 长连接的令牌处理

客服会话可能持续数小时，必然跨过 Access 的 2 小时有效期：

1. 连接建立时校验 Token，并把 `exp` 记在连接上下文。
2. 距 `exp` 还有 5 分钟时，服务端推送 `SYSTEM {action:"REAUTH_REQUIRED"}`。
3. 客户端用 Refresh 换新 Access → **断开重连**（`/ws/chat?token=<新>`），并带 `lastSeq` 补拉，消息不丢。
4. 到 `exp` 仍未重连 → 服务端 `close(4001, "TOKEN_EXPIRED")`，客户端按退避策略重连（回落到登录页前的最后一次尝试）。

> 不要让长连接"免检"。连接建立后永不校验 Token，等于开了一个永久后门。

#### 7.11.6 安全细则

- 全站 HTTPS；Token 只经 Header 传输，禁止放 URL 参数（会进 Nginx 日志）。
- 生产建议 Access 存内存、Refresh 存 `HttpOnly + Secure + SameSite=Lax` Cookie；一期用 localStorage 简化，需在 README 标注 XSS 风险与清洗要求。
- 校验时钟偏差容忍 60s；`nbf`/`exp` 都要校验。
- 登录失败 5 次锁定 15 分钟（spec 11），失败计数按"账号 + IP"双维度，避免撞库绕过。

### 7.12 幂等与防刷（ADR-12 落地）

#### 7.12.1 注解定义

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    String scene();                        // 业务场景：ORDER_CREATE / PAY_CREATE / REFUND_APPLY
    int ttlSeconds() default 600;          // 键存活时间，覆盖"用户可能重试"的窗口
    boolean replayResult() default true;   // 重复请求是否直接返回首次结果
    String message() default "请求正在处理，请勿重复提交";
}
```

> 题目中写作 `@//idempotent/` 似为占位符，本方案统一命名为 **`@Idempotent`**，与 `Idempotency-Key` 请求头配套。

#### 7.12.2 键规则

```
Redis Key = idem:{scene}:{principalId}:{headerKey}
示例      = idem:ORDER_CREATE:10086:8f3a1c7e-...
```

- `headerKey` 取自请求头 `Idempotency-Key`；标注了 `@Idempotent` 的接口**缺失该头直接 400**，不允许降级为"不幂等"。
- 前端生成规则见 7.12.5；服务端只校验格式（UUID/长度 8–64），不解析语义。

#### 7.12.3 执行流程

| 步 | 动作 | 结果 |
| --- | --- | --- |
| 1 | 校验头存在与格式 | 缺失 → `40002` |
| 2 | `SET key PROCESSING NX EX ttl` | 失败（键已存在）→ 进入第 5 步 |
| 3 | 执行业务方法 | 成功 → 第 4 步；抛业务异常 → 删键后抛出 |
| 4 | 写结果快照 `SET key DONE:{响应JSON} EX ttl` | 返回原响应 |
| 5 | 键值为 `PROCESSING` | 返回 `409` + `40902`「请求处理中，请勿重复提交」 |
| 6 | 键值为 `DONE` 且 `replayResult=true` | 直接返回缓存响应，附响应头 `X-Idempotent-Replay: true` |

**失败语义（很重要，决定用户能不能重试）**

| 异常类型 | 是否删键 | 理由 |
| --- | --- | --- |
| 参数校验失败（40001） | 删 | 用户改完参数应能立即重试 |
| 业务校验失败（40910 库存不足等） | 删 | 补货后应能重试 |
| 系统异常 / 超时（50001） | **保留** | 不确定副作用是否已发生，保留键让重试走"重放"而不是再下一单 |

#### 7.12.4 数据库兜底（**必须建，不是可选**）

**DDL 必须出现在 V1 迁移里，且是不可协商项**：

```sql
ALTER TABLE orders ADD COLUMN idem_key VARCHAR(64) NULL COMMENT '幂等键，来自 Idempotency-Key';
CREATE UNIQUE INDEX uk_orders_idem_key ON orders (idem_key);
```

> 允许 `NULL` 是因为历史数据（种子/导入）没有幂等键；MySQL 唯一索引对多个 `NULL` 不冲突，正好满足。

**捕获与重放**

```java
try {
    orderMapper.insert(order);
} catch (DuplicateKeyException e) {
    // Redis 挂了、键被清理、或用户刷新重试：以数据库为准
    Order exist = orderMapper.selectByIdemKey(cmd.getIdemKey());
    if (exist != null) {
        return OrderVO.of(exist);   // 幂等返回同一单，HTTP 200
    }
    throw e;                        // 不是幂等冲突（如 order_no 撞号），照常抛出
}
```

**为什么这层不能省**

| 情况 | 只有 Redis | Redis + 唯一索引 |
| --- | --- | --- |
| 用户双击提交 | Redis 拦住 | Redis 拦住 |
| Redis 主从切换丢键 | **重复下单** | DB 拦住，返回原单 |
| Redis 被运维清空 | **重复下单** | DB 拦住 |
| 服务重启后键丢失 | **重复下单** | DB 拦住 |

生产事故里"Redis 丢了"不是假设，是必然会发生一次。**任何以 Redis 为唯一防线的幂等设计都不合格。**

**同一原则的其他落点**

| 场景 | 数据库唯一键 |
| --- | --- |
| 订单创建 | `orders.idem_key` |
| 支付回调 | `payment_txn.txn_no` |
| 退款回调 | `refund_txn.txn_no` |
| 定制单与订单行 | `custom_order.order_item_id` |
| 应账单与订单 | `receivable.order_id` |

**测试要求**：`IdemTest#redisFlush` 必须真的 `FLUSHDB` 后再提交，验证 DB 拦截生效——不能只 mock。

#### 7.12.5 前端生成规则（写错就白做）

| 时机 | 生成 | 复用 |
| --- | --- | --- |
| 进入结算页 / 打开退款申请弹窗 | 生成 1 个 UUID，存入组件状态或 `sessionStorage` | 整个提交流程复用同一个 |
| 点击提交（第 1 次） | 发送该 UUID | — |
| 网络超时后重试 | **复用原 UUID** | 服务端凭它重放结果 |
| 提交成功 / 明确失败并离开页面 | 清空 | 下次进入重新生成 |

**反例**：在 `onClick` 里 `uuid()`，每点一次换一个键，幂等形同虚设。这条必须写进前端 Code Review 清单。

#### 7.12.6 幂等与防刷的分工

两者常被混为一谈，本项目分开实现：

| 能力 | 解决的问题 | 实现 | 作用范围 |
| --- | --- | --- | --- |
| 幂等 | 同一操作**重复提交**只生效一次 | `@Idempotent` + 唯一索引 | 下单、支付、退款、回款核销 |
| 防刷 | 短时间**高频请求** | `@RateLimit(window, max)`，Redis 滑动窗口，键为 `rate:{userId}:{path}` | 登录、验证码、下单、会话发起 |

| 接口 | 限制 |
| --- | --- |
| `POST /api/auth/login` | 5 次/分钟/账号，5 次/分钟/IP |
| `POST /api/auth/sms-code` | 1 次/60 秒/手机号，10 次/天/手机号 |
| `POST /api/orders` | 10 次/分钟/用户 |
| `POST /api/chat/sessions` | 3 次/分钟/用户 |
| 其余写接口 | 60 次/分钟/用户（默认） |

超限返回 `429` + `42901`，并在响应头给 `Retry-After`。Nginx 层再加一道 `limit_req` 作为兜底（防止应用层被打穿）。

---

## 8. 接口设计

### 8.1 通用约定

| 项 | 约定 |
| --- | --- |
| 前缀 | 用户端 `/api/**`；管理端 `/api/admin/**`；WebSocket `/ws/chat` |
| 认证 | `Authorization: Bearer <AccessToken>`；用户端与管理端令牌不通用（`aud` 不同） |
| 令牌续期 | 响应头 `X-New-Token` 下发新 Access（剩余 <15 分钟时），前端静默替换；过期用 `POST /api/auth/refresh` 换新，失败跳登录 |
| 响应 | `{ code: 0, message: "ok", data: {...}, traceId: "..." }`，`code=0` 为成功 |
| 分页 | 入参 `page`(1起) `size`(默认20, 上限100) `sort`；出参 `{list, total, page, size}` |
| 时间 | ISO-8601 `2026-11-02T22:30:00+08:00` |
| 金额 | 一律 `*_cent` 整数；**响应中同时给 `*Yuan` 字符串**供直接展示，避免前端各自除 100 |
| 幂等 | 下单/支付/退款/核销必须带 `Idempotency-Key`（UUID），服务端 `SETNX` + 唯一索引双层（见 7.12）；重放响应带 `X-Idempotent-Replay: true` |
| 防刷 | 登录、验证码、下单、发起会话分别限流，超限 `429` + `Retry-After` |
| 危险操作 | 前端弹窗要求输入确认短语 → `POST /api/admin/confirm-token` 换取一次性令牌（Redis 5 分钟、绑定动作与目标）→ 请求头 `X-Confirm-Token`（见第 10 章 R-04） |

### 8.2 错误码

| 码 | 含义 | HTTP |
| --- | --- | --- |
| 0 | 成功 | 200 |
| 40001 | 参数校验失败 | 400 |
| 40002 | 缺少 `Idempotency-Key` | 400 |
| 40101 | 未登录（无令牌） | 401 |
| 40102 | 令牌已过期（触发刷新流程） | 401 |
| 40103 | 刷新令牌无效/已被使用（需重新登录） | 401 |
| 40301 | 无权限 | 403 |
| 40302 | 确认令牌缺失/失效/与操作不匹配 | 403 |
| 40401 | 资源不存在 | 404 |
| 40901 | 状态冲突（当前状态不允许该操作） | 409 |
| 40902 | 重复请求正在处理中 | 409 |
| 40910 | 库存不足 | 409 |
| 40920 | 低于底价 | 409 |
| 40930 | 核销金额超额 | 409 |
| 40940 | 超出可退金额 | 409 |
| 40950 | 信用额度不足 | 409 |
| 42201 | 业务校验失败（含自定义文案） | 422 |
| 42901 | 操作过频 | 429 |
| 50001 | 系统异常 | 500 |

### 8.3 认证与账号

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 手机号注册（发送验证码 → 校验 → 建号并分配销售员），返回双令牌 | 公开 |
| POST | `/api/auth/login` | 登录，返回 `accessToken`（2h）+ `refreshToken`（7d）+ `expiresIn` | 公开 |
| POST | `/api/auth/refresh` | 用 `refreshToken` 换新 Access，并轮换 Refresh（旧 Refresh 立即失效） | 公开（凭 RT） |
| POST | `/api/auth/sms-code` | 发送验证码（60s 限流） | 公开 |
| POST | `/api/auth/logout` | 退出：删本设备 Refresh + 当前 `jti` 入黑名单 | 登录 |
| POST | `/api/auth/logout-all` | 退出所有设备：清该用户全部 Refresh + `token_version++` | 登录 |
| GET | `/api/me` | 当前用户资料 + 等级 + 积分 | 登录 |
| PUT | `/api/me` | 修改资料 | 登录 |
| PUT | `/api/me/password` | 修改密码 | 登录 |
| GET/POST/PUT/DELETE | `/api/me/addresses[/{id}]` | 地址簿 | 登录 |
| POST | `/api/me/enterprise/apply` | 企业认证申请（上传执照） | 登录 |
| GET | `/api/me/enterprise` | 企业档案与额度 | 企业客户 |

### 8.4 商品与购物车

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/categories` | 分类树 | 公开 |
| GET | `/api/products` | 列表（分类/筛选/排序/分页） | 公开 |
| GET | `/api/products/{id}` | 详情（价格按当前身份返回，未登录给零售价） | 公开 |
| GET | `/api/products/{id}/skus` | SKU 与可售库存 | 公开 |
| POST | `/api/products/{id}/arrival-subscribe` | 到货提醒 | 登录 |
| GET | `/api/custom-services` | 定制服务商品与计价规则（面数×阶梯） | 公开 |
| POST | `/api/custom-services/quote` | 定制试算（面数、数量 → 服务费） | 登录 |
| GET | `/api/cart` | 购物车（含实时试算） | 登录 |
| POST | `/api/cart/items` | 加购 | 登录 |
| PUT | `/api/cart/items/{id}` | 改数量/勾选 | 登录 |
| DELETE | `/api/cart/items/{id}` | 删除 | 登录 |
| POST | `/api/cart/checkout-preview` | 结算试算（优惠/运费/积分/应付） | 登录 |

### 8.5 订单与支付

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/orders` | 创建订单（幂等，锁库存，价格快照） | 登录 |
| GET | `/api/orders` | 我的订单（按主状态/时间筛选） | 登录 |
| GET | `/api/orders/{orderNo}` | 订单详情（四维度状态 + 包裹 + 定制） | 登录 |
| POST | `/api/orders/{orderNo}/cancel` | 取消（仅待付款） | 登录 |
| POST | `/api/orders/{orderNo}/confirm` | 确认收货（按包裹） | 登录 |
| POST | `/api/payments` | 发起模拟支付，返回 `payUrl/txnNo` | 登录 |
| GET | `/api/orders/{orderNo}/pay-result` | 支付结果轮询（PAID/FAILED/PENDING） | 登录 |
| POST | `/api/payment/mock/callback` | 模拟第三方回调（验签 + 幂等） | 签名 |
| POST | `/api/payment/mock/refund-callback` | 模拟退款回调 | 签名 |

### 8.6 退款售后

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/refunds` | 申请退款（行级、金额校验） | 登录 |
| GET | `/api/refunds` | 我的售后单 | 登录 |
| GET | `/api/refunds/{refundNo}` | 售后详情与进度 | 登录 |
| POST | `/api/refunds/{refundNo}/appeal` | 申诉（一次） | 登录 |

### 8.7 定制

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/custom-orders/{id}/drafts` | 上传图稿（生成新版本） | 登录 |
| GET | `/api/custom-orders/{id}` | 定制单详情（版本、审核结论、驳回原因） | 登录 |
| GET | `/api/me/drafts` | 我的图稿库 | 登录 |

### 8.8 会员、企业、发票、消息

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/member/profile` | 等级、成长值、距下一级 | 登录 |
| GET | `/api/member/points` | 积分余额与明细 | 登录 |
| GET | `/api/member/exclusive-products` | 专属商品 | 登录 |
| GET | `/api/enterprise/receivables` | 我的应收账单 | 企业客户 |
| GET | `/api/enterprise/credit` | 额度与账期 | 企业客户 |
| POST | `/api/invoices` | 申请开票 | 登录 |
| GET | `/api/invoices` | 我的发票 | 登录 |
| GET | `/api/notifications` | 站内信列表 | 登录 |
| POST | `/api/notifications/read` | 标记已读 | 登录 |

### 8.9 在线客服（REST + WS）

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| WS | `/ws/chat?token=` | 建立长连接 | 登录 |
| POST | `/api/chat/sessions` | 发起会话（带 `sourceType/sourceId`），返回会话或排队信息 | 登录 |
| GET | `/api/chat/sessions/current` | 当前进行中会话 | 登录 |
| GET | `/api/chat/sessions/{id}/messages` | 历史消息（`afterSeq` 补拉） | 登录 |
| POST | `/api/chat/sessions/{id}/close` | 客户结束会话 | 登录 |
| POST | `/api/chat/sessions/{id}/read` | 上报已读游标 | 登录 |
| GET | `/api/chat/sessions` | 历史会话列表 | 登录 |

### 8.10 管理端 · 商品与库存

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET/POST/PUT | `/api/admin/products[/{id}]` | 商品 CRUD | `product:write` |
| POST | `/api/admin/products/{id}/publish` | 上/下架（批量） | `product:publish` |
| GET/POST/PUT/DELETE | `/api/admin/skus[/{id}]` | SKU 管理 | `product:write` |
| PUT | `/api/admin/skus/{id}/ladder-prices` | 阶梯价批量配置 | `product:price` |
| GET/POST/DELETE | `/api/admin/customer-prices` | 一客一价 | `product:price` |
| GET | `/api/admin/stock` | 库存列表（含可售/预占/待检） | `stock:read` |
| GET | `/api/admin/stock/flows` | 库存流水 | `stock:read` |
| POST | `/api/admin/stock/inbound` | 手工入库 | `stock:write` |
| POST | `/api/admin/stock/loss` | 报损 | `stock:loss` |
| POST | `/api/admin/stock/checks` | 创建盘点单 | `stock:check` |
| PUT | `/api/admin/stock/checks/{id}/count` | 录入实盘 | `stock:check` |
| POST | `/api/admin/stock/checks/{id}/approve` | 审批并调整库存（**需确认令牌**） | `stock:check` |

### 8.11 管理端 · 订单、发货、定制、退款

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/admin/orders` | 多维筛选列表 | `order:read` |
| GET | `/api/admin/orders/{id}` | 详情（含取价来源、支付流水、包裹） | `order:read` |
| POST | `/api/admin/orders` | 代客下单 | `order:write` |
| POST | `/api/admin/orders/{id}/price-adjust` | 改价（低于底价需超管令牌） | `order:adjust` |
| POST | `/api/admin/orders/{id}/close` | 关闭订单（**需确认令牌**） | `order:close` |
| POST | `/api/admin/shipments` | 创建包裹（选行+数量） | `order:ship` |
| POST | `/api/admin/shipments/{id}/confirm` | 确认发货 | `order:ship` |
| GET | `/api/admin/custom-orders` | 定制单列表（按状态/超时） | `custom:read` |
| GET | `/api/admin/custom-orders/{id}` | 审核详情（图稿 + 订单上下文） | `custom:read` |
| POST | `/api/admin/custom-orders/{id}/pass` | 审核通过 | `custom:review` |
| POST | `/api/admin/custom-orders/{id}/reject` | 驳回（原因必填） | `custom:review` |
| POST | `/api/admin/custom-orders/{id}/production` | 排产 | `custom:produce` |
| POST | `/api/admin/custom-orders/{id}/finish` | 完工 | `custom:produce` |
| GET | `/api/admin/refunds` | 退款待审列表 | `refund:read` |
| POST | `/api/admin/refunds/{id}/approve` | 审核通过（**需确认令牌**） | `refund:audit` |
| POST | `/api/admin/refunds/{id}/reject` | 驳回 | `refund:audit` |

### 8.12 管理端 · 客户、会员、财务、客服、系统、报表

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/admin/customers` | 客户列表（数据范围生效） | `customer:read` |
| GET | `/api/admin/customers/{id}` | 客户详情（订单/售后/会话/应收） | `customer:read` |
| POST | `/api/admin/customers/{id}/transfer` | 改派（原因必填） | `customer:transfer` |
| POST | `/api/admin/customers/batch-transfer` | 批量改派 | `customer:transfer` |
| POST | `/api/admin/enterprise/{id}/audit` | 企业认证审核 | `customer:audit` |
| POST | `/api/admin/enterprise/{id}/credit` | 授信/调额（**需确认令牌**） | `finance:credit` |
| PUT | `/api/admin/member-levels` | 等级参数 | `member:config` |
| POST | `/api/admin/customers/{id}/level` | 人工授予/降级（原因必填） | `member:grant` |
| POST | `/api/admin/customers/{id}/points` | 积分调整 | `member:points` |
| GET | `/api/admin/receivables` | 应收台账（含逾期天数） | `finance:read` |
| POST | `/api/admin/payment-records` | 登记回款 | `finance:payment` |
| POST | `/api/admin/payment-records/{id}/write-off` | 核销（**需确认令牌**） | `finance:writeoff` |
| POST | `/api/admin/payment-records/{id}/reverse` | 冲销（**需确认令牌**） | `finance:reverse` |
| GET | `/api/admin/invoices` | 待开票 / 已开票 | `finance:read` |
| POST | `/api/admin/invoices/{id}/issue` | 标记已开票并上传 PDF | `finance:invoice` |
| GET/POST | `/api/admin/contracts` | 合同列表/上传 | `finance:contract` |
| GET | `/api/admin/chat/sessions` | 会话列表（待接入/进行中/已结束） | `chat:serve` |
| POST | `/api/admin/chat/presence` | 切换在线/忙碌/离线 | `chat:serve` |
| POST | `/api/admin/chat/sessions/{id}/accept` | 接入会话 | `chat:serve` |
| POST | `/api/admin/chat/sessions/{id}/transfer` | 转接（原因必填） | `chat:serve` |
| POST | `/api/admin/chat/sessions/{id}/close` | 结束会话（选原因） | `chat:serve` |
| GET | `/api/admin/staff` | 员工列表 | `system:staff` |
| POST | `/api/admin/staff` | 新增员工 | `system:staff` |
| PUT | `/api/admin/roles/{id}/permissions` | 权限矩阵 | `system:role` |
| GET/PUT | `/api/admin/configs` | 业务参数 | `system:config` |
| GET | `/api/admin/logs` | 操作日志 | `system:log` |
| POST | `/api/admin/confirm-token` | 换取危险操作确认令牌（入参 `action`+`targetId`+`confirmText`），返回 `token`+`expireAt` | 登录 |
| GET | `/api/admin/reports/sales` | 销售额统计图（按日/周/月，可按品类） | `report:sales` |
| GET | `/api/admin/reports/sales-rank` | 销售业绩排行 | `report:sales` |
| GET | `/api/admin/reports/stock-warning` | 库存预警清单 | `report:stock` |

---

## 9. 前端设计

### 9.1 用户端路由（`web-user`）

| 路由 | 页面 | 关键要素 | 鉴权 |
| --- | --- | --- | --- |
| `/` | 首页 | Banner、分类、**按销量倒序**商品流、悬浮客服 | 公开 |
| `/products` | 列表 | 筛选/排序/分页、会员价角标 | 公开 |
| `/products/:id` | 详情 | 规格联动、库存三态、定制表单（**无颜色**）、咨询入口 | 公开 |
| `/custom` | 定制服务 | 面数×数量计价说明、独立下单入口 | 公开 |
| `/cart` | 购物车 | 实时试算、失效商品清理 | 登录 |
| `/checkout` | 结算 | 地址、发票、备注、费用明细 | 登录 |
| `/pay/:orderNo` | 支付 | 倒计时、模拟支付、结果轮询三态 | 登录 |
| `/orders` | 订单列表 | 状态 Tab、包裹进度"已发 2/3" | 登录 |
| `/orders/:orderNo` | 订单详情 | 四维度状态、时间轴、包裹逐条确认、图稿版本 | 登录 |
| `/after-sale/:orderNo` | 申请退款 | 行选择、可退金额、凭证 | 登录 |
| `/member` | 会员中心 | 成长值进度、权益、积分明细 | 登录 |
| `/enterprise` | 企业专区 | 额度、账期、应收、销售员 | 企业客户 |
| `/chat` | 客服会话 | 消息流、发送状态、排队提示、断线重连横幅 | 登录 |
| `/messages` | 站内信 | 已读/未读、跳转 | 登录 |
| `/invoices` | 我的发票 | 申请与下载 | 登录 |
| `/drafts` | 我的图稿 | 版本列表、下载 | 登录 |
| `/profile` | 个人中心 | 资料、地址、密码 | 登录 |
| `/login` `/register` | 登录/注册 | 验证码倒计时、失败锁定提示 | 公开 |

### 9.2 管理端路由（`web-admin`）

| 分组 | 路由 | 页面 | 权限码 |
| --- | --- | --- | --- |
| 概览 | `/dashboard` | 工作台（角色化待办） | — |
| 商品 | `/products` `/products/edit/:id` | 列表 / 编辑（含 SKU 与阶梯价） | `product:*` |
| 商品 | `/categories` | 分类管理 | `product:write` |
| 商品 | `/prices/customer` | 一客一价 | `product:price` |
| 库存 | `/stock` `/stock/inbound` `/stock/loss` `/stock/check` | 库存/入库/报损/盘点 | `stock:*` |
| 交易 | `/orders` `/orders/:id` | 订单列表 / 详情 | `order:read` |
| 交易 | `/shipments` | 发货工作台（按行拆单） | `order:ship` |
| 定制 | `/custom` `/custom/:id` | 定制单 / 审核工作台（左右分栏） | `custom:*` |
| 客户 | `/customers` `/customers/:id` | 客户列表 / 详情 | `customer:read` |
| 客户 | `/enterprise/audit` | 企业认证与授信 | `customer:audit` |
| 会员 | `/members/levels` `/members/points` | 等级参数 / 积分 | `member:*` |
| 客服 | `/chat/workbench` | 客服工作台（会话三栏） | `chat:serve` |
| 财务 | `/finance/receivables` | 应收台账 | `finance:read` |
| 财务 | `/finance/payment-records` | 回款登记与核销 | `finance:payment` |
| 财务 | `/finance/refunds` | 退款审核 | `refund:audit` |
| 财务 | `/finance/invoices` `/finance/contracts` | 发票 / 合同 | `finance:invoice` |
| 报表 | `/reports/sales` `/reports/sales-rank` `/reports/stock-warning` | 三张报表 | `report:*` |
| 系统 | `/system/staff` `/system/roles` `/system/configs` `/system/logs` | 账号/权限/参数/日志 | `system:*` |

### 9.3 关键前端实现要点

| 主题 | 实现 |
| --- | --- |
| 权限 | 登录后拉 `/api/me` 得 `permissions[]`；**静态路由表 + `meta.perm` 过滤**生成菜单（不采用后端下发组件路径，理由见 9.4.4） |
| 金额 | `packages/shared/format.ts` 提供 `fmtCent(c)`；**页面禁止自行 `/100`** |
| 试算 | 购物车与结算的价格**只信后端** `checkout-preview`，前端不本地累加（避免与取价规则分叉） |
| 支付 | 支付页只轮询 `pay-result`，不做乐观跳转 |
| 客服 | `useChat()` 组合式函数封装：连接、心跳、重发队列、`seq` 排序、补拉、已读上报；断线时顶部横幅"连接中断，正在重连" |
| 定制 | 审核工作台左图稿（`<img>` + 缩放组件）、右订单信息，键盘快捷键 `P` 通过 / `R` 驳回 |
| 表格 | 列表统一 `useTable()`（分页、筛选、导出、空态、加载态），导出走后端 `?export=1` 同权限 |
| 令牌 | 请求拦截器统一附加 `Authorization`；响应拦截器读 `X-New-Token` **只接受 exp 更大者**（防并发旧盖新）；`40102` 走单飞刷新队列后重放原请求一次，`40103` 清令牌跳登录并回跳 |
| 幂等 | `Idempotency-Key` 在**进入结算页时生成一次**，存组件状态；超时重试复用，成功后清空；禁止在 `onClick` 里生成 |
| 危险操作 | 弹窗展示影响摘要（如"将关单并全额退款 ¥1,280.00"），要求**手动输入确认短语**（关闭订单→"确认关闭"，核销→"确认核销"，授信→"确认授信"）→ 后端校验短语正确才发 `confirm-token`（5 分钟、绑定动作与目标、用后即焚）→ 随请求头提交 |

### 9.4 管理端：基于 `pure-admin-thin` 的改造方案

> **适用范围**：仅管理端。用户端（商城）**不使用**该模板——后台的侧边栏 + 密集表格 + 页签布局套到商城里，会做出"后台味的商城"，视觉与转化都受损。

#### 9.4.1 参考基线

| 项 | 内容 |
| --- | --- |
| 项目 | `pure-admin-thin` v6.2.0（vue-pure-admin 的精简版） |
| 许可证 | MIT（可商用，需保留版权声明） |
| 核心依赖 | Vue 3.5.22 / Vite 7.1.12 / Element Plus 2.11.5 / Pinia 3.0.3 / Tailwind 4 / ECharts 6 |
| 生态包 | `@pureadmin/table` 3.3、`@pureadmin/utils` 2.6、`@pureadmin/descriptions` 1.2 |
| 目录 | `src/{api,assets,components,config,directives,layout,plugins,router,store,style,utils,views}` |
| 已具备 | 折叠侧边栏 + 顶栏 + **多页签**、暗色模式、`RePureTableBar` 表格栏、`ReDialog`、按钮级权限指令 `v-auth`/`v-perms`、`PureHttp` 封装、路由层级拍平工具 |
| 默认配置 | 端口 8848、路由 `hash` 模式、`server.proxy` **为空**（需自行补） |

**它能省掉的工作**：布局骨架、页签与缓存、表格栏与分页联动、弹窗封装、图标体系、暗色主题——这些约占管理端 30% 的重复劳动。

#### 9.4.2 采用 / 改造 / 重写 / 移除

**A. 直接采用（保留原样或仅改配置）**

| 模块 | 文件/目录 | 说明 |
| --- | --- | --- |
| 布局 | `src/layout/**`（11 个子组件） | 侧边栏/顶栏/页签/设置面板/内容区，直接可用 |
| 表格栏 | `@pureadmin/table` + `RePureTableBar` | 分页、列显隐、刷新、密度，全部业务列表页复用 |
| 弹窗 | `ReDialog` | 审核、核销、确认框统一走它 |
| 权限指令 | `directives/auth`、`directives/perms`、`RePerms` | 按钮级 `<el-button v-auth="'order:ship'">` |
| 图标 | `unplugin-icons` + `ReIcon` | 菜单与按钮图标 |
| 路由工具 | `src/router/utils.ts` 的 `buildHierarchyTree` / `formatFlatteningRoutes` | 静态路由层级处理 |
| 打包配置 | `build/**`、`vite.config.ts` 骨架 | 分包、压缩、别名 |

**B. 必须改造（不改就跑不通或不合规）**

| 编号 | 差异点 | 模板现状 | 本项目要求 |
| --- | --- | --- | --- |
| D1 | 登录响应字段 | 期望 `{ accessToken, refreshToken, expires }`（时间戳） | 后端返回 `expiresIn`（秒），需在 `setToken` 前换算为时间戳 |
| D2 | 提前续期 | **无**：只在请求前判断本地 `expires` 到期后刷新 | 增加响应头 `X-New-Token` 静默替换（7.11.2） |
| D3 | 错误码分流 | **无**：未按业务码区分 | `40102` 走刷新重放，`40103` 清令牌跳登录 |
| D4 | 防旧盖新 | **无**：直接覆盖 token | 只接受 `exp` 更大的新 Token（7.11.3 D4） |
| D5 | 刷新白名单 | `["/refresh-token", "/login"]` | 改为 `["/auth/refresh", "/auth/login", "/auth/sms-code"]` |
| D6 | 部署路径 | `base: "/"`、`hash` 模式 | `base: "/admin/"` + `h5` 模式，Nginx `try_files` 兜底 |
| D7 | 权限来源 | 调 `/get-async-routes` **由后端下发组件路径** | 改为**静态路由表 + `meta.perm` 过滤**（理由见 9.4.4） |
| D8 | 幂等与确认令牌 | **无** | 请求拦截器补 `Idempotency-Key` 透传；危险操作补 `X-Confirm-Token` |
| D9 | 主题品牌 | 模板默认蓝 | 换森特品牌色（建议主色 `#12664F` 墨绿 + 强调 `#FF7A1A` 橙） |
| D10 | 演示内容 | 欢迎页、权限演示页、作者信息 | 全部删除，替换为工作台与真实菜单 |

**C. 必须重写（业务页面，一行都不复用）**

9.2 节列出的 32 个路由对应页面全部自写，包括：工作台、商品编辑、库存盘点、订单详情、发货工作台、定制审核、客户详情、回款核销、客服工作台、三张报表。

**D. 移除（模板自带但本项目不需要）**

| 项 | 处理 |
| --- | --- |
| `mock/`、`vite-plugin-fake-server` | 联调前可用于接口打桩，联调完成后关闭并从构建中移除 |
| `views/welcome`、`views/permission/**` | 删除（前者换成工作台，后者是权限演示） |
| `VITE_CDN`（`vite-plugin-cdn-import`） | 关闭，内网部署走本地打包 |
| 模板作者水印、README 演示链接、示例数据 | 清除 |
| `localforage` / `sortablejs` / `pinyin-pro` | 先保留（布局搜索与缓存依赖），若后续未使用则在收口时裁剪 |

#### 9.4.3 工程结构（标注来源）

```
web-admin/
├── build/                       # 来自模板（保留）
├── mock/                        # 来自模板（联调后删）
├── src/
│   ├── api/                     # 【重写】按后端模块组织，与 plan 第 8 章一一对应
│   │   ├── order.ts  stock.ts  custom.ts  finance.ts  chat.ts ...
│   ├── components/              # 来自模板（ReDialog/ReIcon/RePureTableBar…）
│   ├── config/                  # 来自模板（改品牌与网关地址）
│   ├── directives/              # 来自模板（auth/perms 保留）
│   ├── layout/                  # 来自模板（仅改 Logo 与菜单宽度）
│   ├── router/
│   │   ├── index.ts             # 来自模板，改 base 与守卫
│   │   └── modules/             # 【重写】按业务域拆：product/order/finance/chat/system
│   ├── store/modules/           # 来自模板，改名（pure-user → sent-user）并改字段
│   ├── utils/http/index.ts      # 来自模板，按 D1–D5 改造（见 9.4.4）
│   ├── utils/confirm.ts         # 【新增】危险操作确认短语 + confirm-token
│   ├── utils/idempotent.ts      # 【新增】Idempotency-Key 生成与复用
│   └── views/                   # 【重写】32 个业务页
└── .env.development             # 改端口 5174、base /admin/、补 proxy
```

#### 9.4.4 五处代码级改造（关键）

**① HTTP 层**：模板的 `PureHttp` 已有"队列 + 单飞"骨架，但缺三件事，按下面改：

| 模板行为 | 问题 | 改法 |
| --- | --- | --- |
| 请求前判断 `expires` 已过期 → 刷新 | 只覆盖"已知过期"，不覆盖"服务端提前判定失效"（改密、踢下线） | 保留预判，**增加响应侧 40102 分支** |
| 响应直接 `return response.data` | 读不到自定义响应头 | 在成功分支先取 `response.headers["x-new-token"]` 并按 D4 比较 |
| `whiteList` 仅两个 | 刷新接口本身可能触发循环 | 补 `/auth/refresh`、`/auth/sms-code` |

**② 权限模型：不采用后端下发组件路径**

模板的 `/get-async-routes` 会让后端返回 `component: "views/xxx/index.vue"` 之类的字符串，再 `import.meta.glob` 动态匹配。本项目**不采用**，理由：

1. 组件路径拼错只会在运行时白屏，编译期抓不到；
2. 后端一旦被攻破，可下发任意组件路径，扩大攻击面；
3. 32 个页面是固定集合，没有"运营动态加页面"的需求，动态路由纯属多余复杂度。

**改法**：`router/modules/*.ts` 静态声明全部路由，`meta.perm` 标注权限码；登录后拉 `/api/me` 得 `permissions[]`，在 `permission` store 里过滤生成菜单。守卫逻辑：

```ts
router.beforeEach((to, _from, next) => {
  if (whiteList.includes(to.path)) return next();
  if (!getToken()) return next({ path: "/login", query: { redirect: to.fullPath } });
  if (to.meta?.perm && !hasPerm(to.meta.perm as string)) return next({ path: "/error/403" });
  next();
});
```

**③ 登录响应字段适配**（D1）

```ts
// 模板期望 expires 为时间戳；后端给 expiresIn 秒
const { accessToken, refreshToken, expiresIn } = res.data;
setToken({ accessToken, refreshToken, expires: Date.now() + expiresIn * 1000 });
```

**④ 部署路径与代理**（D6）

| 配置 | 值 |
| --- | --- |
| `.env.development` | `VITE_PORT = 5174`、`VITE_PUBLIC_PATH = /admin/`、`VITE_ROUTER_HISTORY = "h5"` |
| `server.proxy` | `/api` → `http://localhost:8088`；`/ws` → `ws://localhost:8088`（`ws: true`） |
| Nginx | `location /admin/ { try_files $uri $uri/ /admin/index.html; }`，否则刷新子路由 404 |

**⑤ 品牌与文案**（D9/D10）

- 改 `src/style` 的 `--el-color-primary` 与 `--pure-*` 变量，替换 Logo 与 favicon。
- 删除"pure-admin"字样、作者链接、演示路由名（`/permission/page` 等）。
- 页签文案统一为业务名，不出现英文 key。

#### 9.4.5 组件复用要点

| 场景 | 用法 |
| --- | --- |
| 列表页 | `RePureTableBar` + `@pureadmin/table` 的 `useTable`，列定义用 `columns` 数组，操作列固定右侧 |
| 表单弹窗 | `addDialog`（ReDialog）统一宽高与底部按钮，提交按钮带 loading 与幂等键 |
| 按钮权限 | `v-auth="'order:ship'"`；后端仍会二次校验（前端只做体验） |
| 危险操作 | `openConfirm({ phrase: "确认关闭", action: "ORDER_CLOSE", targetId, summary })`，内部完成：展示摘要 → 校验短语 → 取 token → 返回可提交状态 |
| 状态展示 | 统一 `StatusTag` 组件，颜色映射订单主状态/支付状态/定制状态/售后状态四套枚举 |
| 金额展示 | `fmtCent()` 来自 `packages/shared`，禁止页面 `{{ price / 100 }}` |

#### 9.4.6 许可证与"不照搬"的边界

MIT 允许修改与商用，但**"参考"与"照搬"的区别必须落到可检查的动作上**：

| 必须做 | 说明 |
| --- | --- |
| 保留 `LICENSE` 与原版权声明 | 放在 `web-admin/LICENSE`，README 注明"管理端基座基于 pure-admin-thin (MIT) 改造" |
| 业务页面 100% 自写 | 不复用其 `views/*` 任何页面代码 |
| 主题与品牌替换 | 主色、Logo、字体、间距按森特视觉重设，不做"换 Logo 的 pure-admin" |
| 删除演示与作者信息 | 侧边栏、登录页、页脚不得出现模板作者与演示入口 |
| 不整包引入 `vue-pure-admin` 全量版 | 全量版含大量用不上的组件与演示页，体积与维护成本都翻倍；用 thin 版按需取 |

#### 9.4.7 脚手架验收清单

| # | 验收项 | 判定 |
| --- | --- | --- |
| 1 | `pnpm i && pnpm dev` 后访问 `http://localhost:5174/admin/` 正常 | 必须 |
| 2 | 登录成功写入双令牌，刷新页面仍保持登录 | 必须 |
| 3 | 并发 5 个请求触发 40102，实际只发 1 次 refresh，5 个请求均成功 | 必须 |
| 4 | 关闭标签页重开（Cookie 失效）后回到登录页，不出现空白页 | 必须 |
| 5 | 销售员登录看不到"系统设置"菜单；直接输 URL 访问被拦到 403 | 必须 |
| 6 | 列表页分页、筛选、列显隐、导出可用 | 必须 |
| 7 | 关闭订单弹窗必须输入"确认关闭"才可提交，令牌过期提示重新确认 | 必须 |
| 8 | `pnpm build` 产物部署到 `/admin/`，刷新任意子路由不 404 | 必须 |
| 9 | 页面无"pure-admin"字样与作者链接 | 必须 |

---

## 10. 工程红线（落实 `agents.md`）

> `agents.md` 已补全为 4 条可执行规则（原第 4 条截断，已于 v1.1 修复）。第 4 条的落地细则见 10.1。

| 编号 | 红线 | 落地手段 | 校验点 |
| --- | --- | --- | --- |
| R-01 | **禁止 DROP TABLE / DROP DATABASE** | Flyway 迁移只允许 `CREATE`/`ALTER ADD`/`ALTER MODIFY`/索引变更；删除列用"标记废弃 + 停止使用"，不物理删 | CI 正则扫 `db/migration/*.sql`，命中 `DROP\s+(TABLE\|DATABASE)` 直接失败；代码中禁止 `@TableLogic` 之外的物理删除 |
| R-02 | **所有 SQL 必须参数化** | MyBatis 只允许 `#{}`；动态排序/列名走白名单枚举；禁止 `${}` 拼接用户输入 | CI 扫 Mapper XML 的 `${`，除白名单（`${ew.customSqlSegment}` 等框架变量）外报错；Code Review 必查 |
| R-03 | **操作前验证环境** | 启动校验 `app.env` 与库名后缀一致；所有写操作日志带 `env`；生产环境禁止通过接口执行数据修复 | `EnvironmentGuard` 在 `@PostConstruct` 校验，不匹配抛异常终止启动 |
| R-04 | **危险操作二次确认** | 前端二次确认弹窗 + 后端一次性令牌 `X-Confirm-Token`（5 分钟、绑定操作类型与目标 ID、用后即焚） | 危险接口（关闭订单、核销、冲销、授信调额、盘点审批、退款打款）强制校验，缺失返回 `40302` |

**补充约束（本方案追加，不违背 agents.md）**

- 禁止 `SELECT *`：查询必须显式列名（VO 专用 SQL 除外）。
- 禁止在循环内查库（N+1）：列表接口用批量查询或 `IN`。
- 事务只包 DB 操作，**不得在事务内调外部 HTTP**（支付/邮件），改为事务提交后发事件。
- 所有接口必须带 `traceId` 并贯穿日志。

### 10.1 危险操作二次确认的实施细则（R-04 展开）

**为什么不能只弹一个"确定/取消"**：用户对确认框已经麻木，连续点击会形成肌肉记忆。要求**手动输入确认短语**，才能把"误触"变成"需要三个动作才能完成"（打开弹窗 → 阅读影响 → 输入文字），误操作率显著下降。

**确认短语表**

| 操作 | 确认短语 | 令牌 `action` |
| --- | --- | --- |
| 关闭订单（触发退款） | `确认关闭` | `ORDER_CLOSE` |
| 回款核销 | `确认核销` | `PAYMENT_WRITE_OFF` |
| 回款冲销 | `确认冲销` | `PAYMENT_REVERSE` |
| 企业授信/调额 | `确认授信` | `ENTERPRISE_CREDIT` |
| 盘点审批（调整库存） | `确认调整` | `STOCK_CHECK_APPROVE` |
| 退款打款 | `确认退款` | `REFUND_APPROVE` |
| 管理员强制下线 | `确认下线` | `STAFF_LOGOUT_ALL` |

**令牌换取流程**

```
1. 前端弹窗 -> 展示影响摘要（订单号/金额/将发生的动作）-> 输入确认短语
2. POST /api/admin/confirm-token  { action, targetId, confirmText }
3. 后端校验：confirmText 与 action 对应的短语完全一致，否则 40302
4. 生成 token = UUID，存 Redis：
   key   = confirm:{staffId}:{action}:{targetId}
   value = token
   TTL   = 300s
5. 返回 { token, expireAt }
6. 前端提交业务请求，Header: X-Confirm-Token
7. 业务接口校验：键存在 && 值相等 && action/targetId 匹配 -> DEL 后放行（用后即焚）
```

**三条硬规则**

1. **令牌与目标绑定**：`ORDER_CLOSE:123` 的令牌不能用于关闭订单 456，也不能用于核销。
2. **用后即焚**：校验通过立刻删除，同一令牌无法重放第二次。
3. **缺失即拒绝**：未带令牌返回 `40302`，**不允许"没有令牌就降级为普通确认"**，否则这条红线形同虚设。

**前端交互要求**

- 确认短语输入框禁止粘贴（`onpaste="return false"`），必须手敲。
- 令牌过期（5 分钟）后提交 → 提示"确认已超时，请重新确认"，重新走流程，不允许静默重试。
- 影响摘要必须来自服务端预览接口（如 `GET /api/admin/orders/{id}/close-preview`），前端不得自行拼接金额。


---

## 11. 测试设计

### 11.1 分层

| 层 | 范围 | 工具 | 目标 |
| --- | --- | --- | --- |
| 单元 | 取价引擎、金额分摊、状态机、核销算法 | JUnit 5 | 行覆盖 ≥ 80%，核心分支 100% |
| 并发 | 库存预占、支付回调幂等 | JUnit + 线程池 + Testcontainers(MySQL) | 无超卖、无重复入账 |
| 集成 | Controller → Service → DB | MockMvc + Testcontainers | 主链路全通 |
| 端到端 | 23 条验收场景 | 手工 + Playwright 脚本 | 与 spec 第 13 章逐条对齐 |

### 11.2 必测用例（对齐 spec 验收标准）

| 用例 | 场景 | 断言 |
| --- | --- | --- |
| `PriceTest#ladderBoundary` | qty=9/10/49/50 | 命中正确区间价 |
| `PriceTest#agreementBeatsLadder` | 企业协议 5 折 + 阶梯 4.5 折 | **按协议价 5 折成交**，`price_source=AGREEMENT`，阶梯不生效（防倒挂，对应 A23） |
| `PriceTest#ladderBeatsMember` | VIP2（95 折）买 50 个，阶梯 92 折 | **按阶梯 92 折**成交，`price_source=LADDER`（对应 A3） |
| `PriceTest#noStackingByDefault` | 企业协议 + 阶梯，未开 `allow_stacking` | 结果等于协议价，不等于两者相乘 |
| `PriceTest#stackingWhenEnabled` | 开启 `allow_stacking` 且结果低于底价 | 抛 40920，禁止成交 |
| `PriceTest#belowCost` | 成交价低于成本价但高于底价 | 允许成交 + 生成 `PRICE_BELOW_COST` 告警 |
| `PriceTest#belowFloor` | 一客一价低于底价 | 抛 40920，订单不生成 |
| `PriceTest#memberExpired` | 人工等级到期当天 | 仍按原等级取价 |
| `PriceTest#oneCustomerTerminal` | 一客一价 + 同时有协议与阶梯 | 只用一客一价，`price_source=CUSTOMER_SKU` |
| `StockTest#lastOne` | 20 线程抢 1 件 | 成功=1，`physical` 不为负（对应 A15） |
| `StockTest#timeoutRelease` | 下单不支付 30 分钟 | `locked` 归零，可售恢复（A1） |
| `PayTest#idempotent` | 同 `txnNo` 回调 3 次 | 只入账一次，流水 1 条（A14） |
| `PayTest#lateCallback` | 已取消订单收到回调 | 订单仍 `CANCELLED`，生成退款单（A2） |
| `PayTest#amountMismatch` | 回调金额不符 | 拒绝入账 + 告警记录 |
| `ShipTest#partial` | 一单含标品+定制件先发标品 | 状态 `PART_SHIPPED`，包裹 1 个（A16） |
| `ShipTest#customNotApproved` | 定制未过审就发货 | 抛 40901 |
| `CustomTest#rejectFlow` | 驳回 3 次后第 4 次 | 转 `ESCALATED`，客服不可再审 |
| `RefundTest#amountCap` | 申请超过可退额 | 抛 40940 |
| `RefundTest#pointReturn` | 积分抵扣订单退款 | 优先退积分，过期折现 |
| `WriteOffTest#over` | 核销额 > 订单未回款 | 抛 40930，不产生预收款（A18） |
| `WriteOffTest#multi` | 一笔回款核销 3 单 | 3 单 `received_cent` 更新，额度释放（A9） |
| `PermissionTest#scope` | 销售员查他人订单 | 列表为空，详情 403（A11） |
| `PermissionTest#mask` | 仓管查订单 | 无手机号与成交价（A12） |
| `ChatTest#assign` | 10 会话打满后再来客户 | 进入排队，不分配给该客服（A20） |
| `ChatTest#reconnect` | 断线 30s 重连 | 补拉不漏不重、顺序正确（A21） |
| `ChatTest#allOffline` | 全员离线发起会话 | 留言落库为 `WAITING`，客服上线收到（A22） |
| `ChatTest#seqUnique` | 同 `clientMsgId` 重发 3 次 | 仅 1 条消息，`seq` 一致 |
| `AuthTest#slidingRenew` | Access 剩余 10 分钟时发请求 | 响应头带 `X-New-Token`，新 Token exp 更晚 |
| `AuthTest#noRenew` | Access 剩余 90 分钟时发请求 | 无 `X-New-Token`，避免无意义续期 |
| `AuthTest#refreshRotate` | 用同一 Refresh 刷新两次 | 第二次返回 40103，旧 RT 已失效 |
| `AuthTest#refreshSingleFlight` | 并发 5 个请求遇 40102 | 只发 1 次 refresh，5 个请求均成功重放 |
| `AuthTest#tokenVersion` | 改密后用旧 Token 请求 | 40101，`ver` 不匹配全部失效 |
| `AuthTest#roleChange` | 改角色权限后在 60s 内请求 | 新权限生效（不依赖 Token 过期） |
| `AuthTest#wsReauth` | 连接存活跨过 Access 过期 | 收到 `REAUTH_REQUIRED`，重连后补拉不丢消息 |
| `IdemTest#duplicate` | 同一 `Idempotency-Key` 提交 2 次 | 第二次返回同一订单号 + `X-Idempotent-Replay: true`，库中仅 1 条 |
| `IdemTest#concurrent` | 20 线程同 key 同时下单 | 成功 1 笔，其余 409 或重放，无脏数据 |
| `IdemTest#redisFlush` | 清空 Redis 后用同 key 再下单 | DB 唯一索引拦截，返回已有订单而非报错 |
| `IdemTest#bizFail` | 库存不足失败后用同 key 重试 | 键已删除，可正常重试（不被误判重复） |
| `IdemTest#missingKey` | 下单未带 `Idempotency-Key` | 40002 |
| `ConfirmTest#phrase` | 关闭订单但短语输错 | 40302，不签发令牌 |
| `ConfirmTest#replay` | 同一确认令牌用两次 | 第二次 40302（用后即焚） |
| `ConfirmTest#mismatch` | 用订单 A 的令牌关闭订单 B | 40302（绑定不匹配） |
| `RateLimitTest#login` | 1 分钟内第 6 次登录 | 429 + `Retry-After` |

### 11.3 性能测试（防超卖与热点验证）

| 项 | 内容 |
| --- | --- |
| 工具 | JMeter 5.6（脚本入库 `/perf/order-create.jmx`） |
| 环境 | 4C8G 单机、MySQL 8 本地、Redis 本地、连接池 20、JVM `-Xmx2g` |
| 场景 1 | 500 线程抢同一 SKU 最后 1 件 → **成功数 = 1**，`physical_qty >= 0`，订单表无重复 |
| 场景 2 | 500 线程混合下单（10 个 SKU，库存充足）→ QPS ≥ 300、P95 < 500ms、错误率 < 0.1% |
| 场景 3 | 开启 `hotspot-lock-enabled` 重跑场景 1 → 失败响应 P95 < 100ms（验证"快失败"） |
| 场景 4 | 200 线程 × 5 分钟稳压 → 无死锁、无连接池耗尽、`LockWaitTimeout` 占比 < 1% |
| 回归要求 | 每次改动 `StockService` 或库存 SQL，必须重跑场景 1 与 4 并归档报告 |
| 通过标准 | 场景 1/2/4 全绿；场景 3 仅作参考，不达标不放宽场景 1 |

> 压测数据用独立 schema（`sent_perf`），**禁止**在 dev 库上跑，避免污染业务数据（对应 agents.md R-03 环境校验）。

### 11.4 测试数据与环境

- Testcontainers 起 MySQL 8 + Redis 7，每个测试类独立 schema，Flyway 自动迁移。
- 固定种子数据：3 个等级、6 个角色、12 个权限组、5 个 SPU / 20 个 SKU、2 个人客户 + 1 个企业客户（含额度）。
- 支付回调测试使用固定密钥，构造签名工具类。

---

## 12. 里程碑与任务拆分

### 12.1 里程碑

| 里程碑 | 内容 | 出口标准 |
| --- | --- | --- |
| M0 地基 | 工程脚手架、Flyway、common、鉴权、权限骨架 | 能登录、能校验权限、CI 通过 |
| M1 主数据 | 员工/角色、商品/SKU、分类、定价（含三层价） | 管理端能建商品，用户端能看到正确价格 |
| M2 交易主链 | 购物车、下单、支付回调、订单状态机 | A1/A2/A14/A15 通过 |
| M3 履约 | 库存预占与流水、手工入库/报损/盘点、拆单发货 | A16/A19 通过 |
| M4 定制 | 定制单、图稿版本、审核流、排产 | A5/A6/A7 通过 |
| M5 售后与财务 | 退款、应收、回款核销、发票、合同 | A8/A9/A18 通过 |
| M6 会员 | 等级、成长值、积分、专属商品 | A3/A13 通过 |
| M7 实时客服 | WebSocket、排队、分配、转接、断线补拉 | A17/A20/A21/A22 通过 |
| M8 报表与收口 | 三张报表、操作日志、参数配置、危险操作令牌 | 全量验收通过 |

### 12.2 任务清单（可直接派工）

| ID | 任务 | 依赖 | 产出 | 验收 |
| --- | --- | --- | --- | --- |
| T-01 | 后端工程初始化（pom、分层、common、异常、traceId） | — | 可启动骨架 | `GET /api/ping` 返回 traceId |
| T-02 | Flyway V1 建表（54 张）+ 种子数据 | T-01 | 迁移脚本 | `flyway migrate` 成功，无 DROP |
| T-03 | 鉴权（注册/登录/JWT/锁定/双端令牌隔离/Refresh 轮换） | T-01 | auth 模块 | 登录失败 5 次锁定 15 分钟；RT 一次性轮换 |
| T-03b | 令牌滑动续期 + WebSocket 重认证（7.11） | T-03 | 过滤器、`X-New-Token` | `AuthTest` 全绿 |
| T-04 | RBAC + 数据范围 + 字段脱敏 | T-03 | system 模块、`@DataScope` | 销售员只能看自己客户 |
| T-05 | 商品与 SKU CRUD、分类、上下架、可见等级 | T-04 | product 模块 | 管理端建品、用户端可见性正确 |
| T-06 | 取价引擎（5 级严格优先级 + 防倒挂 + 底价） | T-05 | `PricingService` | 单价测试全绿 |
| T-07 | 购物车与结算试算 | T-06 | cart 模块 | 试算金额与下单一致 |
| T-08 | 下单（幂等、快照、预占） | T-07 | order 创建链路 | A15 并发测试通过 |
| T-09 | 模拟支付 + 回调（验签/幂等/迟到） | T-08 | payment 模块 | A2/A14 通过 |
| T-10 | 订单状态机 + 查询 + 超时取消任务 | T-09 | order 查询、job | A1 通过 |
| T-11 | 库存台账、流水、手工入库、报损 | T-02 | stock 模块 | A19 通过 |
| T-12 | 盘点（录入/差异/审批） | T-11 | stock 盘点 | 审批需确认令牌 |
| T-13 | 拆单发货、包裹、收货、自动确认 | T-10,T-11 | shipment | A16 通过 |
| T-14 | 定制单、图稿版本、审核流、排产 | T-10 | customize 模块 | A5/A6/A7 通过 |
| T-15 | 退款（可退校验/分摊/回调/回补） | T-13,T-14 | refund 模块 | A8 通过 |
| T-16 | 应收台账、回款登记、核销、冲销 | T-15 | finance 模块 | A9/A18 通过 |
| T-17 | 发票、合同 | T-16 | finance 扩展 | 上传与下载可用 |
| T-18 | 会员等级、成长值、积分、专属商品 | T-10 | member 模块 | A3/A13 通过 |
| T-19 | 企业认证、授信、额度占用/释放 | T-16 | customer 企业域 | 超额下单被拦截（A4） |
| T-20 | 站内信 + 邮件 + 模板 | T-01 | notify 模块 | 关键节点可收到 |
| T-21 | WebSocket 通道 + 消息模型（seq/ack/补拉） | T-03 | chat WS 层 | A21 通过 |
| T-22 | 分配、排队、在线状态、转接、超时结束 | T-21 | chat 业务层 | A17/A20/A22 通过 |
| T-23 | 三张报表 | T-13,T-16 | report 模块 | 数据与台账一致 |
| T-24 | 操作日志、参数配置、确认令牌（含短语校验与用后即焚） | T-04 | system 扩展 | `ConfirmTest` 全绿 |
| T-24b | `@Idempotent` 注解 + 拦截器 + 限流（7.12） | T-02 | common 组件 | `IdemTest`/`RateLimitTest` 全绿 |
| T-25 | 用户端脚手架 + 布局 + 权限守卫 | T-01 | web-user | 路由可跑通 |
| T-26 | 用户端商品/购物车/结算/支付页 | T-07,T-09 | 页面 | 可完成一次下单 |
| T-27 | 用户端订单/售后/会员/企业/发票页 | T-10,T-15,T-18 | 页面 | 状态展示正确 |
| T-28 | 用户端客服聊天（useChat） | T-21 | 页面 | 断线重连可用 |
| T-29 | 管理端脚手架（基于 pure-admin-thin 改造：布局保留、HTTP 补 D1–D5、静态路由+权限、品牌替换） | T-04, T-03b | web-admin | 9.4.7 九项验收全过 |
| T-30 | 管理端商品/库存/订单/发货页 | T-05,T-11,T-13 | 页面 | 可完成发货 |
| T-31 | 管理端定制审核工作台 | T-14 | 页面 | 快捷键与驳回可用 |
| T-32 | 管理端客户/会员/财务页 | T-16,T-18,T-19 | 页面 | 核销与授信可用 |
| T-33 | 管理端客服工作台 | T-22 | 页面 | 三栏、转接、状态切换 |
| T-34 | 管理端报表页（ECharts） | T-23 | 页面 | 三张图/表 |
| T-35 | Docker Compose、Nginx、环境校验 | T-01 | 部署包 | 一键起环境 |
| T-36 | 集成测试 + 23 条验收脚本 | 全部 | 测试报告 | 全绿 |
| T-37 | 接口文档导出、README、运维手册 | T-36 | docs | 可交接 |
| T-38 | 压测脚本与基线报告（抢单 / 混合 / 稳压） | T-13 | `/perf/*.jmx` + 报告 | 11.3 场景 1/2/4 达标，零超卖 |
| T-39 | 用户端 axios 封装接入（基于开源实现改 D1–D4，放 `packages/shared`；管理端用自身 PureHttp，见 T-29） | T-25 | `packages/shared/http.ts` | 并发 401 只刷一次；无死锁挂起 |

### 12.3 关键路径

```
T-01 → T-02 → T-04 → T-05 → T-06 → T-07 → T-08 → T-09 → T-10
                                              ↓
                                   T-11 → T-13 → T-15 → T-16
                                              ↓
                                   T-14 → T-31
T-21 → T-22 → T-28 / T-33   （可与 M5 并行）
```

**建议并行**：M1/M2 完成后，M4（定制）与 M7（客服）可由两人并行，互不依赖；前端可在 T-05 后介入，用 mock（OpenAPI 生成）。

**必须前置的两件事**（否则后期返工面很大）：

- `T-03 → T-03b`（令牌续期）必须在**任何页面联调之前**完成，否则登录态问题会散落在每个页面的 bug 里。
- `T-24b`（幂等与限流）必须在 `T-08`（下单）**之前**完成，否则下单压测会造出重复订单，清理数据比写代码更费时。

---

## 13. 追踪矩阵（spec → 实现）

| spec 条款 | 模块 | 关键表 | 接口 | 页面 | 测试 |
| --- | --- | --- | --- | --- | --- |
| 3.1 用户角色 | auth/customer | customer, enterprise_profile | `/auth/*`, `/me/enterprise` | 登录、企业专区 | R-03 |
| 3.2 六角色 | system | sys_staff, sys_role | `/admin/staff`, `/admin/roles` | 系统设置 | PermissionTest |
| 3.3 客户归属 | customer | customer_owner_log | `/admin/customers/{id}/transfer` | 客户详情 | — |
| 6.1 取价 | product | ladder_price, customer_sku_price, agreement_price | `/cart/checkout-preview` | 详情/结算 | PriceTest |
| 6.2 库存 | stock | sku_stock, stock_flow | `/admin/stock/*` | 库存 | StockTest |
| 6.3 会员等级 | member | member_level, growth_log | `/member/profile` | 会员中心 | A3 |
| 6.4 积分 | member | point_log | `/member/points` | 会员中心 | RefundTest#pointReturn |
| 6.5 定制 | customize | custom_order, custom_draft, custom_review_log | `/admin/custom-orders/*` | 审核工作台 | CustomTest |
| 6.6 订单 | order/payment | orders, order_item, payment_txn | `/orders`, `/payments` | 订单/支付 | PayTest, ShipTest |
| 6.7 退款 | refund | refund_order, refund_txn | `/refunds`, `/admin/refunds` | 售后/退款审核 | RefundTest |
| 6.8 账期 | finance | receivable, payment_record, write_off | `/admin/payment-records/*` | 应收/回款 | WriteOffTest |
| 6.9 发票合同 | finance | invoice_request, contract | `/invoices`, `/admin/invoices` | 我的发票/财务 | — |
| 6.10 客服 | chat | chat_session, chat_message, staff_presence | `/ws/chat`, `/chat/*` | 会话/工作台 | ChatTest |
| 6.11 审批留痕 | system | sys_operation_log | 各审批接口 | 操作日志 | — |
| 7.x 用户端 | web-user | — | `/api/**` | 19 页 | E2E |
| 8.x 管理端 | web-admin | — | `/api/admin/**` | 32 页 | E2E |
| 9.4 管理端基座 | web-admin（pure-admin-thin 改造） | — | — | 布局/表格/弹窗/权限指令 | 9.4.7 九项 |
| 9.1 销售额图 | report | orders | `/admin/reports/sales` | 报表 | — |
| 9.2 业绩排行 | report | orders, customer | `/admin/reports/sales-rank` | 报表 | — |
| 9.3 库存预警 | report/stock | sku_stock | `/admin/reports/stock-warning` | 报表 | — |
| 11 认证时效（无 spec 条款，工程要求） | auth | customer.token_version, sys_staff.token_version | `/auth/refresh`, `X-New-Token` | 全局拦截器 | AuthTest |
| 11 幂等（spec 6.6.3 延伸） | common/order | orders.idem_key | 全部写接口 | 结算/支付页 | IdemTest |
| 6.11 审批留痕 / R-04 | system | sys_operation_log | `/admin/confirm-token` | 危险操作弹窗 | ConfirmTest |
| 10.x 边缘场景 | 全模块 | — | — | — | 第 11.2 节用例 |
| 11 非功能 | 全模块 | — | — | — | 并发/权限/幂等测试 |
| 13 验收 A1–A23 | 全模块 | — | — | — | 11.2 用例逐条 |

---

## 14. 风险与开放问题

### 14.1 技术风险

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| WebSocket 单实例内存会话 | 横向扩容后消息不互通 | 接口层留 `broadcast` 抽象；一期单实例；扩容时切 Redis Pub/Sub |
| 热点 SKU 并发下单 | 行锁排队、P99 拉长 | L1 快失败 + L2 有限重试 + L3 Redis 热点锁开关；指标见 11.3 |
| 状态机四维度漂移 | 各页面对"能不能发货/能不能退"判断不一致 | 判断逻辑只允许写在 `OrderStateMachine` / `CustomStateMachine`，页面读接口返回的 `allowedActions[]` |
| 金额分摊误差 | 退款少退或多退 1 分 | 统一 `MoneyUtil.allocate`，单测覆盖余数场景 |
| 定制图稿体积 | 磁盘与备份压力 | 单文件 50MB 上限 + 24 个月归档策略；`file_object.sha256` 去重 |
| 模板被"照搬" | 带来 5 处与需求不符的实现（无 X-New-Token、动态路由、hash 模式等），后期返工 | 9.4.2 的 D1–D10 差异清单 + 9.4.7 九项验收，逐条勾掉才算接入完成 |
| 定时任务重跑 | 重复退款/重复释放 | 全部用条件更新（`WHERE status=...`），禁止"先查后改"；单机串行调度见 ADR-08 |

### 14.2 开放问题（需业务方确认，暂按默认实现）

| 编号 | 问题 | 当前默认 | 影响 |
| --- | --- | --- | --- |
| O-01 | 运费规则（满额包邮阈值与运费金额） | 满 199 元包邮，否则 12 元 | 结算页、退款金额 |
| O-02 | 短信验证码服务商 | 一期用固定验证码 `123456` 打桩，接口预留 | 注册登录 |
| O-03 | 邮件服务商 | 一期写库 + 控制台输出，接口预留 | 通知 |
| O-04 | 定制标准工期天数 | 5 个工作日 | 预计发货日 |
| O-05 | 客服工作时间 | 9:00–18:00 | 排队与提示文案 |
| O-06 | 图稿归档后是否可下载 | 可下载（只读） | 我的图稿 |
| O-07 | 是否需要多语言 | 不做 | — |

### 14.3 `agents.md` 补全情况

原文件 4 条规则且第 4 条末尾截断（"危险操作必须添加二次确认机"），现已补全并回写 `specs/agents.md`：

| 原规则 | 处理 |
| --- | --- |
| 禁止 DROP TABLE / DROP DATABASE | 保留，补充"删除列用标记废弃"的替代做法 |
| 所有 SQL 必须参数化 | 保留，补充"禁 `${}`、动态列名走白名单" |
| 操作前必须验证环境 | 保留，补充"启动时校验 env 与库名后缀" |
| 危险操作必须添加二次确认机制 | **补全**：确认为"输入确认短语 + ConfirmToken（Redis 5 分钟、绑定目标、用后即焚）"，细则见 10.1 |

仍待业务方决定的两项：

1. 是否允许线上数据修复接口（本方案默认**不允许**，只允许运维脚本 + 环境校验 + 双人复核）。
2. 备份与回滚要求（建议：每次迁移前自动逻辑备份，保留 7 天；回滚只允许用备份恢复，不允许写反向 DDL）。

---

*本文档为技术方案与实施计划，所有设计均可回溯至 `spec.md` 条款。需求变更时，先改 spec，再改本文件对应章节与追踪矩阵。*
