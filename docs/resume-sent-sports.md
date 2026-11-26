# 简历项目素材 · 森特体育用品管理系统（sent-sports-b2b）

> 用途：简历项目经历、面试自述、GitHub README 摘要。
> 口径：全部数字来自当前仓库实测（2026-11-23），不含推测。
> 定位建议：个人全栈项目 / 毕业设计级工程实践，不要包装成公司生产系统（支付为 Mock 网关，未上公网）。

---

## 〇、项目标题怎么写（SDD + AI 协作方向）

标题公式：**项目名（业务定位）｜开发范式｜技术栈标签**

### 按投递方向选

**A. 投 Java 后端（最稳，推荐）**
> **森特体育 B2B 电商中台**｜规格驱动开发（SDD）+ AI 协作｜Java 17 · Spring Boot 3 · MyBatis-Plus · MySQL

**B. 投全栈 / 前端偏重**
> **森特体育 B2B 电商中台（SDD 规格驱动 · AI 协作全栈实践）**｜Spring Boot 3 · Vue 3 · TypeScript · MySQL

**C. 投 AI 应用 / 研发效能 / AI 辅助研发岗**
> **基于 SDD 的 AI 协作研发实践 —— 森特体育 B2B 电商中台**｜315 个原子任务 · 6 阶段流水线 · 机器可判定工程红线

**D. 一页简历的紧凑单行（版面紧张时用）**
> 森特体育 B2B 电商中台｜SDD 规格驱动 + AI 协作开发｜Spring Boot 3 / Vue 3 / MySQL（个人全栈）

### 标题下的第一句定位（把「谁做了什么」说清楚，避免被读成「AI 代写」）

> 以 SDD（Spec-Driven Development）组织的 AI 协作项目：**我负责需求规格、技术方案与工程红线，AI 代理按原子任务生成代码，构建门禁负责验收** —— 全链路可回溯到 spec 条款。

### 配套的两条 SDD bullet（放进项目经历，含真实数字）

- **规格驱动开发（SDD）落地**：按 `spec.md`（需求规格 + 21 项定稿决策 + 验收标准，1027 行，v0.1→v1.2 共 7 版收敛）→ `plan.md`（技术方案 / 接口 / 算法 / 追踪矩阵，1872 行）→ `tasks.md`（**315 个原子任务**、6 个 Phase、显式依赖与 `[P]` 并行标记）→ `agents.md`（工程红线）四层组织交付；每个任务一文件、测试先行，实现与 spec 条款双向可追溯，需求变更只改上游文档、不靠口头传递。
- **约束 AI 输出的质量门禁**：把「禁止 `DROP TABLE/TRUNCATE`、SQL 只许 `#{}` 参数化、启动校验 dev/test/prod 库名、TDD 先红后绿」写成**机器可判定**的红线，落到 CI 扫描、ArchUnit 4 条架构规则与 JaCoCo 80% 覆盖率门禁上；AI 生成的代码不合规直接构建失败，从「靠人 review」变成「靠流水线拦截」。

### 面试必答题：这项目是不是 AI 写的？

> **AI 是执行器，不是决策者。** 需求 21 项决策、技术方案、工程红线、验收标准都由我定；AI 按原子任务产出代码，我逐条评审并用单测/集成/E2E 验收。SDD 的意义正是把「我要什么」写到可验收的程度 —— 否则 AI 只会稳定产出「看起来对」的代码。

---

## 一、项目定位（简历标题下的两行简介，任选其一）

**版本 A（一句话，适合简历顶部）**
> 面向体育用品批发场景的 B2B 电商中台：Spring Boot 3 模块化单体 + 三端 Vue3（商城 / 管理后台 / 独立客服工作台），覆盖商品定价、库存、交易、支付退款、营销券、会员积分、定制审核、财务开票、实时客服全链路。

**版本 B（带技术亮点，适合项目描述第一段）**
> 独立设计并实现一套 B2B 电商与经营管理中台，后端以「模块化单体 + 领域分层」组织 20 个业务模块，用状态机、策略链、适配器等模式承载订单/定价/存储等核心复杂度；前端用 pnpm workspace 统一三端（商城 5173 / 管理端 5174 / 客服台 5175）与共享 HTTP 内核；配套 ArchUnit 架构守护、Testcontainers 真库集成测试、Playwright 全链路 E2E 与 GitHub Actions CI。

---

## 二、技术栈（可直接作为简历「技术栈」一栏）

| 层次 | 技术 |
|---|---|
| 语言 / 运行时 | Java 17、TypeScript 5、Node.js 22 |
| 后端框架 | Spring Boot 3.2.5、Spring Security、Spring WebSocket、Spring AOP、Actuator |
| 持久层 | MyBatis-Plus 3.5.7、MySQL 8.0、Flyway（56 个版本化迁移）、HikariCP |
| 认证鉴权 | JWT（jjwt 0.12.5）双令牌、RBAC 权限点、DataScope 数据权限、接口限流、幂等拦截 |
| 缓存 / 消息 | Redis（可选适配位）、WebSocket 长连接、进程内端口抽象（幂等 / 令牌 / 限流 / 在线状态） |
| 文档 / 报表 | springdoc-openapi 2.3.0（Swagger UI）、Apache PDFBox 3（发票 PDF 现场渲染）、Hutool |
| 前端 | Vue 3.4、Vite 5、Pinia、Vue Router 4、Element Plus 2.7、Tailwind CSS 3 |
| 前端工程 | pnpm workspace monorepo、`packages/shared` 共享 HTTP 内核 / TokenStorage / 类型 / 格式化、vue-tsc 类型检查 |
| 测试 | JUnit 5、Mockito、AssertJ、ArchUnit 4 条架构规则、JaCoCo、Testcontainers（MySQL）、Playwright 1.65 |
| 部署 | Docker Compose（MySQL + Redis + 后端 + Nginx）、Nginx 静态托管与 `/api`、`/ws/chat` 反向代理 |

---

## 三、工具链（简历「开发工具」一栏）

- **构建 / 依赖**：Maven（多阶段：单测 → 集成测试 → 覆盖率校验）、pnpm workspace、Vite
- **代码质量**：ArchUnit（架构守护）、JaCoCo（覆盖率硬门禁）、vue-tsc（类型检查）
- **测试工具**：JUnit 5 + Mockito + AssertJ、Testcontainers（拉真 MySQL 容器）、Playwright（E2E + 失败录屏/报告）
- **CI/CD**：GitHub Actions 三 Job —— 后端 `mvn test`、三端 `pnpm build` + 纯函数单测、Guardrails（禁止 Flyway 出现 `DROP TABLE/DATABASE`、扫描配置残留 `${}` 占位符）
- **接口 / 联调**：Swagger UI（springdoc）、Actuator 健康检查、浏览器 DevTools
- **部署 / 运维**：Docker Compose、Nginx、环境变量外置（prod fail-closed 启动校验）
- **协作 / 版本**：Git（`main` + 特性分支）、Conventional 风格提交、`docs/` 设计审计报告留档

---

## 四、项目经历正文（推荐版，6 条主推 bullet，可直接粘贴）

> 格式：**做了什么 + 用了什么 + 量化结果**。每条都能在代码里指到文件，面试经得起追问。

1. **架构设计与边界治理**：以模块化单体组织 20 个业务模块（auth / customer / product / stock / cart / order / payment / refund / customize / coupon / finance / chat / notify / report / file / job / system / review / web + common 基座），跨模块调用统一走 `service` 接口 + Facade 依赖倒置；用 **ArchUnit 4 条规则**在构建期拦截依赖腐化（Controller 不得依赖 Mapper、domain 必须纯 POJO、common 不得依赖业务、跨模块只允许经 service），违反即构建失败。

2. **交易主链路与状态机建模**：实现「浏览 → 取价 → 加购 → 结算 → 下单 → 支付 → 发货 → 收货 → 售后 → 开票 → 应收核销」完整闭环；把订单拆成**主流程 / 支付 / 定制 / 售后四个正交状态维度**，用 4 个**表驱动状态机**（Order / Pay / Refund / Custom，纯 Java 无 Spring 依赖、可单测穷举）约束迁移，非法迁移统一 `40901 STATE_CONFLICT`，从编译层面杜绝「已取消订单被支付回调复活」「重复发货扣两次库存」。

3. **库存防超卖与全链路幂等**：放弃分布式锁，用**条件 UPDATE 原子预占**（`physical - locked - pending - frozen >= qty`，影响 0 行即失败）+ `GREATEST` 安全释放 + 出库双条件校验；以数据库**唯一索引**作为防重唯一事实来源（订单 `uk_order_idem`、支付 `uk_txn_idem`、库存流水 `uk_flow_biz`、领券 `uk_coupon_claim`、积分流水 `uk_point_source`），并实现请求级 `Idempotency-Key`（`PROCESSING`→40902、`DONE`→快照回放 + `X-Idempotent-Replay`）；用 **Testcontainers 真 MySQL** 写 20 线程抢最后 1 件的并发集成测试，断言成功下单恒为 1、库存永不为负、订单无重复行。

4. **B2B 定价与营销体系**：设计**六级价格策略链**（一客一价 > 合同/协议价 > 秒杀 > 阶梯价 > 会员价 > 零售价），统一在 `PriceContext` 装配、命中即返回不取低，命中来源与规则 ID 落 `order_item` 实现**逐行可举证**；实现**四维优惠券模型**（券型 × 优惠形式 × 适用范围 × 领取对象）+ 按订单行逐行抵扣 + 会员日门禁 + 领券幂等 + 模板快照；打通**积分抵扣闭环**（上限 = min(积分余额, 应付 × 30%)，支付实扣、整单退款退回、流水幂等）。

5. **实时客服与内容风控**：基于 WebSocket 自建**六帧协议**（MSG / ACK / READ / QUEUE / ASSIGNED / TYPING），实现坐席忙闲与并发位、等待队列自动补位、僵尸在线回收、会话超时关闭（经用例层而非裸 UPDATE，保证坐席负载一致）；用 `(session_id, seq)` 与 `(session_id, client_msg_id)` 双唯一键保证断线重连不产生重复气泡；接入 **DFA 前缀树敏感词过滤**，在 REST 与 WS 两处同口径拦截并回传原文让用户自主修改，词库支持后台增删改、保存即生效。

6. **工程质量与自动化门禁**：坚持 TDD「先红后绿」，沉淀 **1362 条单元测试 + 89 条集成测试（24 个 `*IT`，Testcontainers 真库）+ 87 个 Playwright spec（159 条 E2E 用例，全量 155 条通过）**；JaCoCo 对 `domain` 与 `common/util` 设 **80% 行覆盖率硬门禁**（不达标构建失败，禁止下调）；GitHub Actions 三 Job 覆盖后端测试、三端类型检查与构建、DDL 破坏性语句与配置占位符扫描；Docker Compose 一键拉起 MySQL + Redis + 后端 + Nginx。

---

## 五、备选 bullet（按岗位方向挑 2~3 条替换或补充）

- **认证鉴权**：JWT 双令牌（Access 2h / Refresh 7d 一次性轮换）+ 剩余 15min 响应头 `X-New-Token` 滑动续期 + 前端**单飞刷新**（并发 401 只触发一次刷新、原请求自动重放一次）+ `token_version` 使改密后旧令牌全线失效；配套角色-权限点矩阵、二次确认令牌（`X-Confirm-Token`，用后即焚）、接口限流、登录失败锁定。
- **数据权限**：实现 `@DataScope` 注解 + MyBatis 拦截器对聚合 SQL 做别名改写，按角色自动收敛为「本人 / 本团队 / 全部」三级数据域，销售只能看自己、主管看团队、财务与超管看全量。
- **文件服务**：设计 SHA-256 **内容寻址**文件服务（`年/月/摘要前2位/完整摘要.ext`），同内容同路径、重复上传磁盘零增长且无需加锁（幂等写）；`assertInsideBase()` 防目录穿越、扩展名白名单、`Clock` 可注入便于单测；`Storage` 接口 + `LocalFileStorage` 适配器，换 OSS/S3 只替换一个实现，REST 与 WebSocket 共用同一 `fileId` 语义。
- **定制业务**：图稿多版本上传与审核（`uk_review_once` 防重复审核）、生产流转、定制费单一计费引擎（`CustomFeeFacade`，yml 调价、购物车试算与下单共用）；退款按状态分级 —— 待提交/待审核/已驳回可自助退，已通过需人工复核，生产中/完工/已发货硬拦截，规则收敛在状态机内不走旁路。
- **商品评价**：评价资格由订单侧 `findCompletedOrderNoForReview` 判定（review 模块不直读订单表），**先按 SKU 判、失败再按商品级兜底**避免「买 A 规格评 B 规格」被误杀；唯一键防重 + 昵称头像脱敏 + 好评/差评独立聚合（差评单独 `SUM(CASE WHEN rating<=2)`，不用总数减好评）；后台治理支持隐藏/恢复并落操作日志（before/after + 原因），权限点 `system:review:manage` 精确匹配。
- **财务**：应收台账与核销、合同到期提醒、发票申请与 PDF 现场渲染（PDFBox 内嵌中文字体子集）、可退额度溢出校验（`REFUND_OVERFLOW` 40940）。
- **定时任务**：10 个调度任务（超时取消、自动确认收货、积分过期、合同提醒、应收逾期、客服 SLA、库存预警、WS 心跳、在线状态回收、会话超时），全部走用例层保证副作用一致。
- **前端工程化**：`packages/shared` 沉淀 HTTP 内核（TokenStorage 单一真源、401 单飞刷新与重放、错误码分流、金额/时间格式化），三端复用；管理端抽象 `SentTable` / `StatusTag` / `openConfirm` / `usePagination` / `createIdempotencyScope` 等复用层，设计令牌集中在 `theme.css` 禁止硬编码色值；图表全部 CSS + SVG 手绘，无重型图表库。

---

## 六、可量化指标（简历「项目成果」或面试报数用）

| 指标 | 数值 |
|---|---|
| 后端 Java 源文件 | 740 个 |
| 业务模块 | 20 个业务域 + common 基座 |
| REST 接口 | 219 个端点 + 1 个 WebSocket 端点 |
| 数据库 | 56 个 Flyway 迁移 / 66 张表 / 49 个唯一索引 |
| 前端 | 92 个 `.vue` + 86 个 `.ts`，三端合计 |
| 单元测试 | 1362 条（214 个测试类） |
| 集成测试 | 89 条（24 个 `*IT`，Testcontainers 真 MySQL） |
| E2E | 87 个 spec / 159 条用例，全量 155 通过（单 worker 约 15 分钟） |
| 架构规则 | ArchUnit 4 条，违反即构建失败 |
| 覆盖率门禁 | `domain` + `common/util` 行覆盖率 ≥ 80%，`haltOnFailure` |
| 定时任务 | 10 个 |
| 权限点 | 120+ 个 `@RequirePerm` 注解点 |
| CI | 3 个 Job（后端测试 / 三端构建 / 守护扫描） |

---

## 七、精简版（一页简历用，控制在 8~10 行）

> **森特体育 B2B 电商中台** ｜ SDD 规格驱动 + AI 协作开发 ｜ 个人全栈项目 ｜ Java 17 · Spring Boot 3 · MyBatis-Plus · MySQL · Vue 3 · TypeScript
>
> - 以模块化单体拆分 20 个业务域（商品/库存/交易/支付退款/营销券/会员/定制/客服/财务/报表），ArchUnit 4 条规则守护分层边界，Flyway 56 个迁移管理 66 张表、219 个 REST 端点。
> - 订单用四维状态机（主流程/支付/定制/售后）表驱动约束流转；库存用条件 UPDATE 原子预占 + 唯一索引幂等，Testcontainers 20 线程并发测试验证不超卖、不重复下单。
> - 设计六级价格策略链与四维优惠券模型，支持逐行抵扣、会员日门禁、积分 30% 上限抵扣闭环，价格命中来源落库可追溯。
> - 自建 WebSocket 六帧协议客服（排队补位、超时回收、断线重连不重复）+ DFA 敏感词双端拦截；JWT 双令牌滑动续期 + 单飞刷新 + 三级数据权限。
> - 工程质量：TDD 先红后绿，1362 单测 / 89 集成（真库）/ 155 E2E 全绿，JaCoCo 80% 硬门禁，GitHub Actions + Docker Compose 一键交付。

---

## 八、2 分钟面试自述（口语版，按这个顺序讲不会乱）

1. **是什么**：一套 B2B 体育用品批发中台，客户在商城端下单，员工在管理端把货、钱、票、券、售后逐笔落地，客服有独立工作台。开发上用 SDD 规格驱动 + AI 协作：我定规格与红线，AI 按 315 个原子任务产出代码，门禁负责验收。
2. **怎么分层**：后端模块化单体，20 个业务包，跨模块只走 service 接口，用 ArchUnit 在 CI 里卡住；前端 pnpm workspace 三端共享一个 HTTP 内核。
3. **最难的三件事**：
   - 超卖与重复下单 —— 不用分布式锁，把判断压进一条 UPDATE，把防重压进唯一索引，再用真库并发测试证明；
   - 价格与优惠的优先级 —— 策略链 + 上下文统一装配，命中来源落库，客户质疑时能逐行举证；
   - 状态一致性 —— 订单四个维度各自状态机，客服关会话、退款、自动收货都必须走用例层，不允许裸改状态。
4. **怎么保证质量**：先写失败用例再写实现；域层覆盖率 80% 是硬门禁；E2E 覆盖主链路，缺陷也先补用例再修。
5. **诚实收尾**：支付是 Mock 网关、令牌等有状态组件是可替换的进程内实现，接口已抽象好，换 Redis / 真实支付通道业务代码零改动。

---

## 九、写简历时的避坑提醒

1. **别写「高并发生产系统」**：这是本地原型、未上公网。写「高并发场景设计 + 真库并发测试验证」既准确又不掉分。
2. **支付要写明 Mock**：写「Mock 支付网关 + 回调验签 + 幂等入账」，比含糊写「接入支付」更经得起追问。
3. **不要堆砌模式名词**：状态机、策略、适配器、Facade 都能指到具体文件，写 3 个讲透 > 写 8 个说不清。
4. **数字用实测口径**：本文件的数字与仓库一致，改代码后记得同步（单测/集成/E2E 条数、迁移版本）。
5. **简历上只留 4~6 条 bullet**：其余放「备选池」留作面试展开，简历不是功能清单。
6. **AI 协作别写成「AI 生成」**：用「规格驱动 + AI 协作 / AI 辅助编码」，并在同段落强调你负责规格、方案、红线与验收 —— 否则面试官第一反应是「这不是你写的」。SDD 的四份文档就是你主导权的最好证据。
7. **项目名不要放「Demo / 练习」**：写业务定位（B2B 电商中台）比写「练手项目」更有分量；真实性靠「Mock 支付、未上公网」这类边界说明来守，而不是靠自贬。
