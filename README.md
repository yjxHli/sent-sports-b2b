<div align="center">

# 森特体育 · sent-sports-b2b

**面向体育用品批发场景的 B2B 电商中台 —— 模块化单体 + 双端 Vue3**

[![Build](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square)](#-测试与质量)
[![Coverage](https://img.shields.io/badge/coverage-80%25%2B-brightgreen?style=flat-square)](#-测试与质量)
[![Unit Tests](https://img.shields.io/badge/unit_tests-1233_passed-success?style=flat-square)](#-测试与质量)
[![Integration](https://img.shields.io/badge/integration-66_(Testcontainers)-blue?style=flat-square)](#-测试与质量)
[![E2E](https://img.shields.io/badge/E2E-123_passed-success?style=flat-square)](#-测试与质量)
[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](#-技术栈)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?style=flat-square&logo=springboot)](#-技术栈)
[![Vue](https://img.shields.io/badge/Vue-3.4-42b883?style=flat-square&logo=vuedotjs)](#-技术栈)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql)](#-技术栈)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](./LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen?style=flat-square)](#-贡献指南)

[业务全景](#-业务全景) · [核心亮点](#-核心技术亮点) · [快速启动](#-快速启动指南) · [测试与质量](#-测试与质量) · [已知限制](#-已知限制与-roadmap)

</div>

> [!WARNING]
> **本项目为本地开发 / 教学演示用的工程原型，未部署到公网。** 仓库内全部为虚构演示数据，支付通道为可复现的 Mock 网关，**请勿直接用于生产环境或处理真实业务数据**。上线前必读 [生产化待办](#-已知限制与-roadmap)。

---

## 📖 项目简介

森特体育用品管理系统是一个**模块化单体（Modular Monolith）**架构的 B2B 电商与经营管理中台：企业客户认证与授信、验证码登录与找回密码、阶梯价 / 一客一价、购物车与下单、模拟支付与退款、定制图稿多版本审核、库存与部分发货、应收核销与发票 PDF、**四类券型的优惠券营销体系（含会员日门禁）**、会员积分抵扣闭环、收藏与足迹、站内信、带图实时客服与质检、违禁词风控、批量软删除，以及配套的管理后台与商城前台。

后端按业务域拆成 **20 个模块包**（`auth / customer / member / product / stock / cart / order / payment / refund / customize / coupon / finance / chat / notify / report / file / job / system / common / web`），跨模块只允许经由对方 `service` 接口调用 —— 这条边界由 **ArchUnit 规则**在构建期强制守住，不必等到运行期才发现依赖腐化。

| 维度 | 规模 |
|---|---|
| 后端源码 | 701 个 Java 文件 / 20 个业务模块 / 203 个 REST 端点 / 1 个 WebSocket 端点 |
| 数据模型 | 50 个 Flyway 迁移（V1 → V50）/ 63 张表 / 46 个具名唯一索引 |
| 前端 | 双端 84 个 `.vue` + 68 个 `.ts`，商城 **24 个业务路由**、管理端 **37 个业务路由** |
| 测试 | 1233 单元测试 + 66 集成测试（19 个 `*IT`）+ 123 条 Playwright E2E 用例（70 个 spec） |

---

## 🧭 业务全景

一句话：**客户在商城端下单，员工在管理端把货、钱、票、券、售后一笔笔落地。**

```
┌─ ① 客户端层 ──────────────────────────────────────────────
│  商城端  web-user  :5173        管理端  web-admin  :5174 /admin/
│  首页 · 分类/搜索 · 商品详情 · 领券中心(会员日) · 购物车 · 结算 · 订单 · 售后
│  收藏夹 · 足迹 · 会员中心 · 我的券 · 站内信 · 在线客服(WS) · 团购专区
│  登录引导/注册 · 验证码登录 · 忘记密码 · 企业自助注册/审核进度
│  工作台 · 商品 · 库存 · 订单 · 财务 · 定制审核 · 客服审查/指标 · 券管理 · 报表 · 系统（含违禁词库）
│  客服工作台  web-cs  :5175（独立工程，仅 chat:serve 账号可入；管理端不再承载接待）
└──────────────────────────────────────────────────────────
                        │  /api/**   +   /ws/chat
                        ▼
┌─ ② 接入层 ────────────────────────────────────────────────
│  AuthFilter（双 Token）· DataScope（数据权限）· RateLimit · 幂等拦截 · 二次确认 · 短信验证码
└──────────────────────────────────────────────────────────
                        ▼
┌─ ③ 业务域（模块化单体，跨模块只走 service 接口）─────────────
│  auth → product(一客一价) → stock(条件更新) → cart → order(四维状态机)
│       → coupon(四类券型/逐行抵扣) → payment(Mock 回调) → refund
│       → finance(应收 / 发票 PDF)
│  customize(图稿多版本) · member(积分等级) · notify(站内信) · chat(WS 六帧 + 质检)
│  横切：file(内容寻址) · job(10 个定时任务) · report(报表) · common
└──────────────────────────────────────────────────────────
                        ▼
      MySQL 8.0（63 表 / Flyway V1→V50） + 本地内容寻址文件库
```

**一条主链路的完整生命周期**：

```
浏览 → 领券 → 加购 → 结算(地址必填 + 券抵扣) → 下单(原子预占库存, 幂等键去重)
   → 支付(Mock 回调) → 发货(部分/全部, 记库存流水) → 收货 / 超时自动确认
   → 开票(PDF 现场渲染) → 售后申请 → 退款审核(读地址快照) → 应收核销
   → 会员积分入账 → 到期自动过期
```

---

## 🛠 技术栈

| 层 | 技术选型 |
|---|---|
| 语言 / 运行时 | Java 17、Node.js 22、pnpm 12（workspace monorepo） |
| 后端框架 | Spring Boot 3.2.5、Spring Security、Spring WebSocket、Spring AOP、Actuator |
| 持久层 | MyBatis-Plus 3.5.7（**条件更新**支撑库存原子扣减）、Flyway 10、MySQL 8.0 |
| 认证 | jjwt 0.12.5（HS256 双令牌：Access + Refresh） |
| 文档 | springdoc-openapi 2.3.0（dev 开 `/swagger-ui`，prod 关闭） |
| 前端 | Vue 3.4 + Vite 5 + TypeScript 5 + Pinia + Vue Router + Element Plus 2.7 |
| 样式 | 商城端 Tailwind 3 + CSS 变量版式约束；管理端 Element Plus 变量体系 |
| 共享层 | `packages/shared` —— HTTP 内核、TokenStorage、错误码分流、金额/时间格式化 |
| 报表 / 文档 | Apache PDFBox 3.0.3（发票 PDF 现场渲染） |
| 测试 | JUnit 5 + Mockito + AssertJ + **ArchUnit 4 条** + JaCoCo + **Testcontainers(MySQL)** + Playwright |

> **Redis 为可选项** —— 当前令牌索引、幂等存储、限流、确认令牌、在线状态全部为进程内实现，端口已抽象（`IdempotencyStore` / `RefreshTokenStore` / `ConfirmTokenStore` / `RateLimiter`），多实例部署时替换为 Redis 适配器即可，业务代码零改动。

---

## 💎 核心技术亮点

### 1️⃣ 高并发库存防超卖 + 分布式防重

**问题**：20 个人同时抢最后 1 件货，读-改-写两步走必然超卖；用户手抖双击提交，会生成两张订单。

**方案**：把「判断」和「扣减」压进**同一条 SQL**，用数据库的行锁语义代替应用层锁；用**唯一索引**代替分布式锁做去重。

```java
// 原子预占：可用量（physical - locked - pending - frozen）足够才扣，返回 0 行即失败
@Update("UPDATE sku_stock SET locked_qty = locked_qty + #{qty}, version = version + 1 " +
        "WHERE sku_id = #{skuId} " +
        "AND physical_qty - locked_qty - pending_qty - frozen_qty >= #{qty}")
int lockStock(@Param("skuId") long skuId, @Param("qty") int qty);
```

| 场景 | 手段 | 关键约束 |
|---|---|---|
| 下单预占 | 条件 `UPDATE`，影响 0 行 → 抛 `STOCK_NOT_ENOUGH` | 整单在一个事务内回滚，不留半截预占 |
| 释放预占 | `locked_qty = GREATEST(locked_qty - qty, 0)` | 脏数据也不会把锁定值扣成负数 |
| 出库实扣 | `WHERE locked_qty >= qty AND physical_qty >= qty` | 双条件，缺一不扣 |
| 下单防重 | `orders.uk_order_idem(idem_key)` | 重复键 → 复用首次订单，不再扣库存 |
| 支付防重 | `payment_txn.uk_txn_idem(idem_key)` | 回调重放不重复入账 |
| 库存流水 | `stock_flow.uk_flow_biz(biz_type, biz_no, sku_id)` | 同一业务单号同一 SKU 只记一条 |
| 领券防重 | `customer_coupon.uk_coupon_claim(customer_id, claim_key)` | 索引必须带 `customer_id`，一人一模板一张 |
| 请求级幂等 | `Idempotency-Key` 头 + `@Idempotent` | `PROCESSING`→40902；`DONE`→回放快照 + `X-Idempotent-Replay: true` |

**落地证据**（集成测试，真 MySQL）：

- `StockConcurrencyIT` —— 20 线程抢最后 1 件：成功下单**恒为 1**、库存**永不为负**、订单表**无重复行**。
- `IdempotentIT` —— 同一 `idem_key` 提交两次：订单数 = 1，且**重放不吃掉可用库存**（`locked_qty` 仍为 1）。

> 设计取舍：**不引入分布式锁**。锁会带来持有者崩溃、锁超时、跨库事务一致性等一连串问题；而库存本来就是数据库里的一行数据，`UPDATE ... WHERE` 天然串行化。防重同理 —— 唯一索引是唯一无法被绕过的事实来源。

---

### 2️⃣ 通用文件微服务：内容寻址 + SHA-256 去重 + 全链路贯通

**问题**：图稿只存在前端随手生成的假 `fileId` 里，磁盘上没有文件 —— 管理端看不到图，客服发不了图。

**方案**：一个独立 `file` 模块，**REST + WebSocket 两侧共用同一套 `fileId` 语义**。

```http
POST /api/files              # 需登录，multipart 字段名 file（≤50MB）→ { fileId, url, fileName, size }
GET  /api/files/{*fileId}    # 匿名可读（<img> 带不了 Authorization），Cache-Control: 7d
```

**内容寻址落盘**：文件名即内容摘要，路径 `年/月/摘要前2位/完整SHA-256.ext`。

```java
String digest   = sha256Hex(content);                         // 内容指纹
String relative = "2026/11/ab/ab3f...(64位).png";             // 年/月/前缀/摘要
if (Files.exists(target)) return relative;                    // 同内容 → 同一路径（幂等写入）
Files.write(target, content, CREATE_NEW, WRITE);              // 绝非「覆盖」，无锁即无竞争
```

| 特性 | 实现 |
|---|---|
| 去重 | 同字节内容 → 同 SHA-256 → 同路径，**重复上传磁盘零增长**，且无需加锁（幂等写） |
| 路径安全 | `assertInsideBase()` 校验最终路径仍在根目录内，杜绝 `../../etc/passwd` 穿越 |
| 扩展名白名单 | 仅保留 `[a-z0-9]{1,10}`，奇怪字符直接丢弃 |
| 时间可注入 | `Clock` 注入，让「按年月分目录」在单测里可被固定 |
| 迁移成本 | 换对象存储只需替换 `LocalFileStorage` 一个适配器，调用方相对路径语义不变 |

**全链路贯通**（库里只存 `fileId` 字符串，二进制**绝不进 JSON 列**）：

```
客服发图   聊天消息 msg_type=IMAGE，content = fileId  →  前端拼 /api/files/{fileId} 渲染
定制图稿   custom_draft.file_id（多版本，v1/v2/v3 可回溯）
退款凭证   refund_order.images_json = ["2026/11/..","2026/11/.."]（多图数组）
商品主图   product 图片数组，统一走 ImageJsonCodec 编解码
```

> `ChatMsgType` 还有一个 `ORDER` 类型：消息体里存订单摘要 JSON（订单号/状态/应付金额），双方渲染成**订单卡片**，点开跳订单详情 —— 订单本体仍只在 `orders` 表里，聊天记录里不会留着过期快照。

> **敏感词风控（2026-11-19）**：聊天消息在 `SendMessageUseCase`（REST 兜底）与
> `ChatWebSocketHandler`（MSG 帧）**两处同口径**拦截 —— 命中即 `BizException(40003)`，
> 不落库、不推送、不消耗会话序号。客户端收到 `code=40003` 的 ACK 后撤回乐观气泡，
> **把原文还回输入框**让用户自己改（不做 `**` 打码，否则用户不知道哪几个字犯了规）。
> 匹配用 `common/util/SensitiveWordFilter`：把词库编译成前缀树（DFA）后整棵替换，
> 复杂度 O(文本长度 × 最长词长)，与词库规模无关；词库**只收完整词组**（不收「操」这种单字），
> 避免「操作」「操场」被误杀。昵称/登录名同受管控（注册、改资料、建员工账号 → `400`，
> 文案「昵称/用户名包含违禁词，请修改后重试」）。`TEXT` 之外的消息（`IMAGE` 的 fileId、
> `ORDER` 的 JSON）不做文本匹配，避免误杀正常业务消息。
>
> **词库在管理端维护（V46）**：`系统管理 → 违禁词库`（`/system/sensitive-words`）支持按
> 词/备注搜索、按分类（辱骂脏话 / 广告导流 / 违法违禁 / 其他）与启停筛选、增删改与启停开关。
> 词表落 `sys_sensitive_word`（唯一键 `word`，物理删除），**每次写操作后重建前缀树**，
> 保存即生效、不需要重启；启动时由 `SensitiveWordBootstrap` 用 DB 启用词整体覆盖 classpath 兜底词库
> （`sensitive_words.txt` 仅作为首次建库的种子与单测样本）。权限点 `system:sensitive:manage`。
> 因为 `SensitiveWordFilter` 用 `volatile` 持有不可变词树，换词是**原子替换引用**，
> 读线程不会读到「删了一半」的中间态。

---

### 3️⃣ 电商级复杂业务流转

#### 3.1 订单四维状态机

订单不是「一个状态字段」，而是**四个互相正交的维度**，各自独立演进：

| 维度 | 字段 | 取值 |
|---|---|---|
| 主流程 | `status` | `WAIT_PAY` `WAIT_SHIP` `PART_SHIPPED` `WAIT_RECEIVE` `COMPLETED` `CANCELLED` `CLOSED` |
| 支付 | `pay_status` | `UNPAID` `PAYING` `PAID` `PART_REFUNDED` `FULL_REFUNDED` |
| 定制 | `custom_status` | `NONE` `WAIT_DRAFT` `WAIT_REVIEW` `REJECTED` `APPROVED` `IN_PRODUCTION` `FINISHED` `SHIPPED` `CANCELLED` `ESCALATED` |
| 售后 | `after_sale_status` | `NULL` / `REFUNDED`（退款完成回写；退款单另有 `refund_order` 独立状态机） |

```java
// 纯 Java 迁移表，无 Spring 依赖，可被单测穷举；非法迁移统一 40901 STATE_CONFLICT
put(WAIT_PAY,    PAY,               WAIT_SHIP);
put(WAIT_SHIP,   SHIP_PARTIAL,      PART_SHIPPED);
put(PART_SHIPPED, SHIP_CONTINUE,    PART_SHIPPED);   // 分批发货可反复发生
put(CANCELLED,   LATE_PAY_CALLBACK, CANCELLED);      // 迟到的支付回调不得复活订单
```

关键收益：**状态判断只允许写在状态机里**。页面和接口想读状态只能调用它，于是「已取消订单被支付回调复活」「重复发货把库存扣两次」这类经典事故在编译期就没法写出来。

#### 3.2 双 Token 滑动续期

```
登录  →  Access Token(2h, JWT: sub/aud/jti/ver)  +  Refresh Token(7d, 一次性轮换)
请求  →  剩余 < 15min：响应头下发 X-New-Token（复用同一 jti，仅顺延 exp）
40102 →  前端单飞刷新（同一时刻只有 1 个 refresh 在飞）→ 原请求自动重放一次
40103 →  刷新失败 / 已登出 → 清令牌 + 跳登录（带回跳地址）
改密  →  token_version + 1，旧 Access 立即全线失效
```

- **单飞锁**：`refreshSingleFlight()` 让并发 401 只触发一次刷新，避免「刷新风暴」把自己打进限流。
- **用后即焚**：`RefreshTokenStore.rotate()` 原子完成「校验旧 RT → 拉黑 → 写新 RT」，同一 RT 并发刷新只有一个能成功。
- **预判续期**：本地已知将过期时先换新，不必等 401 往返。

#### 3.3 高保真电商订单版式 + 历史地址快照

**页面**：单接口 `GET /api/orders/{orderNo}` 一次取全，前端不做拼装（避免 N+1 与状态错位）。

```
┌ 四步进度条：拍下商品 → 买家付款 → 卖家发货 → 交易成功
│            （done / in 双态 + 半段连线着色：只有「本步与上一步都亮」才染色）
├ 状态 Hero：主标题 + 人话提示（按状态给「正在备货」「已签收」等）
├ 收货地址条：省市区 + 详细地址 + 收货人 + 手机号脱敏
├ 操作区：去支付 / 取消 / 再次购买 / 加入购物车 / 查看物流 / 确认收货 / 申请退款 / 申请开票 / 删除 / 打印 / 联系客服
├ 商品表格：缩略图 · 标题 · 规格 · 服务标签 · 单价 · 数量 · 小计 · 行内「再加入购物车」「申请售后」
├ 物流时间轴：承运商 · 运单号 · 发货时间（未发货给状态化空态，不写死「暂无物流」）
└ 侧栏：付款详情（商品额/运费/优惠/积分抵扣/实付） · 订单信息 · 交易快照 · 订单服务（电子发票）
```

**地址快照**：下单瞬间把地址**固化**进 `orders.address_snapshot_json`，只存业务字段，**不存 `addressId` 也不存 `isDefault`**。

```json
{ "receiver": "张**", "phone": "138****8000",
  "province": "江苏省", "city": "南京市", "district": "鼓楼区", "detail": "演示路 1 号 3 栋 501" }
```

| 规则 | 后果（都发生在真实业务里） |
|---|---|
| `addressId` 必填，校验在**取价 / 预占之前** | 缺地址直接 `PARAM_INVALID`「请先选择收货地址」，不会先扣库存再失败 |
| 非本人地址 → `NOT_FOUND` | 不泄露他人地址是否存在 |
| 下单后改地址簿 / 删地址 | 历史订单收货信息**不受影响** |
| 退款审核读快照 | `AddressSnapshotCodec` 「能读多少读多少」，单字段脏只丢该字段，绝不让整页 500 |

#### 3.4 带图实时客服（WebSocket 六帧协议）

`/ws/chat` 只允许六种帧，非法 `type` 直接 `PARAM_INVALID`，坏帧不会污染会话：

| 帧 | 方向 | 语义 |
|---|---|---|
| `MSG` | 双向 | 文本 / 图片（`content`=fileId）/ 订单卡片（`content`=摘要 JSON） |
| `ACK` | 服务端 | 落库成功回执，带 `seq` |
| `READ` | 双向 | 已读回执 |
| `QUEUE` | 服务端 | 排队中（无在线客服时的兜底） |
| `ASSIGNED` | 服务端 | 坐席分配完成 |
| `TYPING` | 双向 | 正在输入 |

防重与可恢复性：`chat_message` 上有 `uk_msg_seq(session_id, seq)` 与 `uk_msg_client(session_id, client_msg_id)` —— **断线重连后重发不会产生重复气泡**；工作台必须 `reportPresence()` 才会被派单，`PresenceReclaimJob` 回收僵尸在线状态。

**会话生命周期**：客户 `CHAT_IDLE_MINUTES`（默认 10）分钟无消息即由 `ChatTimeoutJob` 扫描并**经 `CloseSessionUseCase` 关闭**（关会话 + 坐席负载 -1 + 队列补位），绝不直接 `UPDATE status='CLOSED'`，否则 `agent_presence.active_sessions` 只增不减，几轮后坐席被判满、新客户只能排队。工作台另配会话**质检 / 审计 / 满意度回访 / 绩效指标**页。

#### 3.5 取价策略链 + 可举证

「一客一价、企业协议价、阶梯价、会员价、零售价」谁优先，是 B2B 最容易扯皮的地方。这里做成**严格优先级策略链**，命中即返回、不取低：

```
① 一客一价(谈判结果)  →  ② 企业协议价  →  ③ 阶梯价(按数量区间)  →  ④ 会员等级价  →  ⑤ 零售价
      order()=1              =2                =3                    =4            =5
```

命中的规则会连同 `price_source` / `price_rule_id` 一起写进 `order_item` —— 客户来问「这单为什么是这个价」，管理端能逐行举证，而不是口头解释。

#### 3.6 优惠券：四类券型 + 逐行抵扣 + 领券幂等

券不是「一张折扣码」，而是**券型 × 优惠形式 × 适用范围 × 领取对象**四组正交属性：

| 维度 | 取值 | 说明 |
|---|---|---|
| 券型 `coupon_type` | `NO_THRESHOLD` / `FULL_REDUCTION` / `CATEGORY` / `ENTERPRISE` | 无门槛 / 满减 / 品类券 / 企业专享 |
| 优惠形式 `discount_type` | `AMOUNT` / `PERCENT` | 减固定金额 / 按比例；`discount_rate` 是**支付比例**，90 = 9 折 |
| 适用范围 `scope_type` | `ALL` / `SPU` / `SKU` + `scope_ids` | 全场 / 指定商品 / 指定规格 |
| 领取对象 `customer_scope` | `ALL` / `ENTERPRISE` | 所有人 / 仅企业认证客户 |

```java
// 跨模块契约：传「订单行」而不是总金额，品类券只对命中行生效（定制费并入行金额参与门槛）
long discountFor(long couponId, long customerId, List<CouponLine> lines);

// 领域内：先按范围筛行，再算抵扣 —— 折扣券按支付比例，金额券按面额封顶，均不超过可抵扣额
public boolean matchesSku(long skuId, Long spuId) { /* ALL / SPU / SKU 三种范围 */ }

public long discountFor(long eligibleCent) {
    long eligible = Math.max(0L, eligibleCent);
    if (DISCOUNT_PERCENT.equals(discountType)) {
        return Math.min(eligible, Math.floorDiv(eligible * (100L - discountRate), 100L));
    }
    return Math.min(amountCent, eligible);
}
```

- **一源转换**：PO → 领域只走 `CouponAssembler.toDomain`，用旧构造器会把品类券/企业券**静默降级成全场券**，可用券、抵扣、领券中心一起错。
- **可用性判定要带商品**：`GET /api/coupons/usable?skuIds=...`，否则品类券永远「看起来可用」。
- **用户券是模板快照**：改模板不影响已领取的券，历史订单不因运营改价而错乱。
- **管理端两条入口**：`/customer/coupon-template` 建券（可限定 SPU/SKU，商品从公开目录搜索）+ `/customer/coupon` 发放与核销记录，两处都带分页、批量软删除与全量导出。
- **会员日门禁**：会员日 = **每周三、周四**，仅会员日可从领券中心领券；门禁在原子扣库存**之前**，非会员日领券直接拒绝并在前端显示下次会员日倒计时。有效期唯一真源是 `MemberDayPolicy.claimValidDays`（¥5 无门槛 3 天 / 其他无门槛 7 天 / 满减 15 天 / 其余沿用模板），前端 `shared/format` 用同口径，不在 UI 复制规则。

#### 3.7 积分抵扣闭环：试算 / 实扣 / 退回 / 幂等

```
结算试算 checkout-preview → 展示可抵扣上限（积分余额 与 应付额 30% 双重约束）
   → 下单只记抵扣额（不动余额）→ 支付成功真实扣减（余额「刚好」减少抵扣额，多扣少扣都算 bug）
   → 整单退款退回积分 → 积分流水按「客户+来源类型+来源单号」唯一键去重（V47）
```

- **上限口径**：积分抵扣 ≤ min(可用积分余额, 订单应付额 × 30%)，30% 上限由 `PointFacade → PointDeduction` 唯一给出；结算页展示、下单落库、订单详情三处金额逐字一致。
- **幂等**：积分流水落 `uk_point_source(customer_id, source_type, source_id)`（V47），由 `INSERT IGNORE` 承担幂等，支付回调重放 / 退款重试不会重复入账或重复退分；手工调整流水不带来源（NULL 不冲突）。
- **闭环修复**：自动确认收货、超时关闭、整单退款三条链路都走用例层，确保积分与成长值**发得出也退得回**（E2E `point-deduct-ui-flow` / `RefundPointsIT` / `RefundReturnPointsIT` 锁死）。

#### 3.8 短信验证码登录 / 找回密码：按用途隔离的一次性码

```
发送短信 → SmsCodeStore 键 = scope:phone（LOGIN / RESET 互不覆盖、互不通用）
   → verify() 只校验不消费（找回密码第一步）→ verifyAndConsume() 校验并删除（登录/重置）
```

- 登录码与找回密码码**按用途隔离**：同一手机号同时请求两种码，互不覆盖、互不通用。
- 验证码**一次性消费**，60s 倒计时防连点；dev 环境复用 `app.sms.expose-demo-code` 回显到控制台，无需真实短信通道。
- 未注册手机号统一返回 `PHONE_NOT_REGISTERED`，登录页据此跳注册引导并回填手机号；企业账号即便验证码正确仍需审核通过，避免绕过企业门禁。

---

## 📚 业务功能清单

| 域 | 已交付能力 |
|---|---|
| 认证与权限 | 双令牌登录 / 刷新 / 登出、验证码登录 / 找回密码（按用途隔离的一次性码）、登录引导与自助注册、`token_version` 强制下线、角色-权限点矩阵、员工登录名（与工号分离）、数据权限（`DataScope`）、二次确认令牌（`X-Confirm-Token`，用后即焚）、接口限流 |
| 客户 | 企业认证自助注册与审核、审核进度查询、授信额度与占用、客户档案、连续登录失败锁定、收货地址簿（默认地址唯一） |
| 商品 | SPU/SKU、分类、上下架、**五级取价策略链**（一客一价 / 协议 / 阶梯 / 会员 / 零售）、收藏、足迹、搜索与筛选 |
| 库存 | 预占/释放/出库、入库单、报损单、盘点单、库存流水、安全库存预警 |
| 交易 | 购物车、结算取价（策略链 + 券抵扣 + 积分抵扣 + 运费）、下单幂等、部分发货、确认收货、超时取消、自动确认收货 |
| 营销 | **优惠券四类券型**、领券中心（会员日门禁 + 倒计时）、结算逐行抵扣、发放记录、券模板管理（限定 SPU/SKU）、注册自动发券、批量软删除与全量导出 |
| 支付退款 | Mock 支付网关与回调验签、部分/整单退款、退款审核（含凭证图片与申诉次数）、**退款单状态机**（审核 / 申诉 / 退款成败）、可退额度溢出校验（`REFUND_OVERFLOW` 40940） |
| 定制 | 图稿多版本上传与审核（`uk_review_once` 防重复审核）、生产流转、定制费用（10 元/件，单一计费引擎 `CustomFeeFacade`，yml 调价）并入订单 |
| 财务 | 应收台账与核销、合同与到期提醒、付款记录、发票申请与 **PDF 现场渲染** |
| 会员 | 积分获取/扣减/过期、结算积分抵扣（30% 上限 + 支付实扣 + 退款退回 + 流水幂等）、等级与有效期、会员中心聚合页 |
| 客服 | 会话创建/分配/转接/关闭、排队补位、在线状态、带图对话、订单卡片、SLA 提醒、会话质检与审计、满意度评价、坐席绩效指标、空闲超时自动结束、**客户标签**（四大类 24 项，仅客服与后台可见，可取消）、**客服发券**（单张 ≤ 20 元，到账即发站内信） |
| 通知 | 站内信（未读红点单一真源）、业务事件通知 |
| 报表 | 经营看板、销售趋势、销售排行、库存预警 |
| 运维 | 10 个定时任务（超时取消 / 自动收货 / 积分过期 / 合同提醒 / 应收逾期 / SLA / 库存预警 / WS 心跳 / 在线回收 / 会话超时）、操作日志、批量软删除（订单 / 退款 / 发票 / 合同 / 定制 / 库存 / 券） |
| 系统 | 员工与角色、**系统配置中心**（9 项业务参数：中文名 / 说明 / 单位 / 类型校验，按分组呈现）、操作日志、接口与权限点配置、**违禁词库管理**（分类 / 启停 / 即时生效，DFA 前缀树）、**客户标签目录维护**（新增 / 改名 / 启停 / 删除，被引用时拒绝删除） |

---

## 📂 目录结构

```
sent-sports-b2b/
├── server/                          # Spring Boot 后端（模块化单体）
│   ├── src/main/java/com/sent/sports/
│   │   ├── auth/ customer/ member/ product/ stock/ cart/ order/
│   │   ├── payment/ refund/ customize/ coupon/ finance/ chat/ notify/
│   │   ├── report/ file/ job/ system/ web/          # 业务模块（20 个包）
│   │   └── common/                  # 领域基座：error/idempotent/ratelimit/storage/interceptor
│   ├── src/main/resources/
│   │   ├── application{,-dev,-test,-prod}.yml
│   │   └── db/migration/            # Flyway V1 → V50（63 张表）
│   └── src/test/java/               # 单元测试 + integration/*IT（Testcontainers，19 个）
├── web-admin/                       # 管理端 Vue3（dev :5174，base /admin/）
├── web-cs/                          # 独立客服工作台 Vue3（dev :5175，base /，Token 前缀 cs-token）
├── web-user/                        # 商城端 Vue3（dev :5173，base /）
├── packages/shared/                 # 双端共享 HTTP 内核 / TokenStorage / 类型 / 格式化
├── e2e/                             # Playwright 用例（70 个 spec / 123 条用例）
├── docker/                          # docker-compose.yml + nginx.conf
├── docs/                            # DEPLOYMENT.md、prod-deploy-checklist.md、设计模式审计报告
├── specs/                           # 设计与任务文档（历史快照，实现以代码为准）
├── .env.example                     # 环境变量模板（仅占位值，可安全提交）
└── .github/workflows/ci.yml         # CI：后端单测 + 双端构建
```

---

## 🚀 快速启动指南

### 路线 A：Docker Compose（推荐，一键起四件套）

`docker/docker-compose.yml` 编排 **MySQL 8.0 + Redis 7 + 后端 + Nginx**，Nginx 同时托管两端静态产物并反向代理 `/api`、`/ws/chat`。

```bash
# 0) 前置：Docker / Docker Compose v2、Node 22 + pnpm（构建前端用）
git clone <repo-url> && cd sent-sports-b2b
pnpm install

# 1) 先构建两端静态产物（Nginx 直接托管 dist 目录）
pnpm -C web-admin build
pnpm -C web-user  build

# 2) 注入数据库口令（compose 强制要求，缺失即报错，绝无弱默认值）
cd docker
echo "DB_PASSWORD=$(openssl rand -hex 16)" > .env
docker compose up -d --build

# 3) 健康检查
curl -s http://127.0.0.1:8088/actuator/health      # {"status":"UP"}
```

| 入口 | 地址 |
|---|---|
| 商城端 | `http://localhost/` |
| 管理端 | `http://localhost/admin/` |
| 后端 API | `http://localhost:8088/api/**`（经 Nginx 反代对外） |

> 后端固定 **8088**（8080 常被 Steam 等软件抢占）。首次启动由 Flyway 自动建表（V1 → V50）；`APP_ENV=prod` 下未注入 `JWT_SECRET` / `PAY_MOCK_SECRET` 时会**拒绝启动**，这是刻意的 fail-closed 设计。

### 路线 B：本地开发（改代码用）

<details>
<summary><b>展开：JDK 17 + 本机 MySQL 8.0，双端热更新</b></summary>

**前置**：JDK 17、Maven 3.9+、Node.js 22、pnpm、本机 MySQL 8.0（`utf8mb4` / `Asia/Shanghai`）。

```sql
CREATE DATABASE IF NOT EXISTS sent_dev  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS sent_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
# 在仓库根目录
cp .env.example .env                       # .env 已被 .gitignore 忽略

# 后端 :8088
cd server
export DB_USERNAME=root DB_PASSWORD='<你的本机密码>'
mvn clean verify                           # 全量门禁（单测 + IT + ArchUnit + JaCoCo）
java -jar target/sent-server.jar --spring.datasource.password="$DB_PASSWORD"

# 前端（回仓库根目录，两条命令开两个终端）
cd ..
cd web-user  && ./node_modules/.bin/vite --port 5173 --host
cd web-admin && ./node_modules/.bin/vite --port 5174 --host
cd web-cs    && ./node_modules/.bin/vite --port 5175 --host   # 客服工作台（需 chat:serve 账号，如 service/123456）
```

健康检查：

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8088/v3/api-docs   # 200
curl -s http://127.0.0.1:8088/actuator/health                                # {"status":"UP"}
```

- Swagger UI（仅 dev）：`http://127.0.0.1:8088/swagger-ui/index.html`
- 端口被系统排除段占用（Windows 报 `listen EACCES` 且 `netstat` 无占用）→ 管理员 PowerShell 执行 `net stop winnat && net start winnat`
- 改了 `packages/shared` 源码需要重启 Vite 时加 `--force`（依赖预构建缓存）
- **`mvn clean` 会删掉 `target/sent-server.jar`**：跑完门禁要重新 `package` 并重启 :8088，否则 E2E 全线假红

</details>

---

## ⚙️ 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `APP_PROFILE` / `APP_ENV` | `dev` | 运行 profile；`prod` 关闭 Swagger、收紧 Actuator、禁用演示数据 |
| `SERVER_PORT` | `8088` | 后端监听端口 |
| `DB_HOST` / `DB_USERNAME` / `DB_PASSWORD` | 本机演示值 | 数据库连接；**`prod` 下无默认值，缺失即启动失败** |
| `JWT_SECRET` | 启动时随机生成 | Access Token 签名密钥；`prod` 下必须注入（建议 ≥32 字节） |
| `JWT_ACCESS_TTL_MS` / `JWT_REFRESH_TTL_MS` | `7200000` / `604800000` | Access 2h / Refresh 7d |
| `JWT_RENEW_THRESHOLD_MS` | `900000` | 剩余不足 15min 触发 `X-New-Token` 滑动续期 |
| `PAY_MOCK_SECRET` | 启动时随机生成 | Mock 支付回调签名密钥；`prod` 下必须注入 |
| `CHAT_IDLE_MINUTES` | `10` | 客服会话空闲超时（分钟），客户无消息即自动结束 —— **唯一真源** |
| `APP_CORS_ORIGINS` | 本机两个 dev server | 跨域白名单，逗号分隔；生产必须改成真实域名 |
| `UPLOAD_BASE_DIR` | `./data/upload` | 文件服务根目录（内容寻址落盘位置） |
| `APP_SENSITIVE_WORDS_FILE` | `sensitive_words.txt` | 违禁词库 classpath 文件；词库缺失即启动失败（风控静默失效比启动失败更危险） |
| `CUSTOM_FEE_SINGLE_CENT` | `1000` | UV 定制服务费单价（分/件，单面）；购物车试算与下单共用同一引擎，调价只改这里 |
| `app.sms.expose-demo-code` | `false`（dev 为 `true`） | 验证码回显开关，仅 dev 开启；prod 恒 false，验证码绝不回显 |
| `REDIS_*` | 空 | Redis 可选；本地不启用不影响主流程 |
| `JOB_*_MS` | `60000` | 各定时任务间隔（详见 `application.yml`） |
| `INVOICE_*` | 虚构演示值 | 发票销售方信息，**禁止用于真实开票** |
| `VITE_PROXY_API_TARGET` / `VITE_PROXY_WS_TARGET` | `http://127.0.0.1:8088` | 仅开发态代理，不编入生产包 |

完整清单见 [`.env.example`](./.env.example)。

---

## 👤 演示账号

> 由 `AccountSeeder` 在 **dev / test** profile 注入进程内账号仓储；`prod` 不加载种子账号。

| 角色 | 登录名 | 密码 | `aud` |
|---|---|---|---|
| 超级管理员 | `admin` | `123456` | `admin` |
| 客户（手机号即登录名） | `13800138000` | `123456` | `user` |
| 客服 / 销售主管 / 销售 / 财务 / 仓管 | `service` `sales_mgr` `sales` `finance` `warehouse` | `123456` | `admin` |

登录请求体：`{ "username": "...", "password": "...", "aud": "admin|user", "deviceId": "..." }` —— 用户端必须 `aud=user`，否则返回 `50001`。

验证码登录（仅商城客户）：`POST /api/auth/sms-code`（获取码）→ `POST /api/auth/login/by-code`（`{phone, code, deviceId}`）；找回密码走 `POST /api/auth/reset/sms-code` → `/reset/verify-code`（只校验不消费）→ `/reset/password`（消费）。dev 下验证码回显在后端控制台与 `/auth/sms-code` 响应里，prod 不回显。

> 口令目前是**明文占位实现**，上线前必须替换为 BCrypt。

---

## 🧪 测试与质量

```
    ┌─ Backend   ─  mvn -o clean verify          →  单测 + IT + ArchUnit + JaCoCo
    ├─ Frontend  ─  vue-tsc -b && vite build     →  两端 0 error
    └─ E2E       ─  npx playwright test          →  123 passed
```

| 层级 | 数量 | 说明 |
|---|---:|---|
| 单元测试（Surefire） | **1233** | JUnit 5 + Mockito + AssertJ，0 失败 0 跳过 |
| 集成测试（Failsafe） | **66** | 19 个 `*IT`，Testcontainers 拉起真 MySQL |
| 架构约束（ArchUnit） | **4** | 分层与依赖边界，违反即构建失败 |
| E2E（Playwright） | **123** | 70 个 spec，全绿，单 worker 稳定串行（约 10 分钟） |
| 行覆盖率（JaCoCo） | **81.2%** | 域层 + `common/util` 口径 **94.2%** |

```bash
# 后端全量门禁：单测 + IT(Testcontainers) + ArchUnit + JaCoCo check
cd server && mvn -o clean verify       # 本地仓库已预热可加 -o 离线跑

# 双端类型检查与生产构建
pnpm -C web-admin build        # vue-tsc -b && vite build
pnpm -C web-user  build

# 端到端（需先起 5173 / 5174 / 8088 三端）
cd e2e && npx playwright test --output runs/$(date +%Y%m%d-%H%M)
```

**质量门禁规则**

- **ArchUnit 4 条铁律**：`controller` 不得依赖 `mapper`；`domain` 必须是纯 POJO（不依赖 Spring）；`common` 不得依赖任何业务模块；跨模块只允许经由对方 `service` 接口（可带 DTO）。
- **JaCoCo `haltOnFailure=true`**：`*/domain/**` 与 `common/util/**` 行覆盖率低于 80% 直接构建失败，**禁止下调阈值**。
- **集成测试用真库**：Testcontainers 优先拉 `mysql:8.0`，Docker 不可用时回落本机 `sent_test`；数据库完全不可达时相关测试类整类跳过，不误报失败。
- **缺陷先红后绿**：业务缺陷、链路问题同样先补一条会失败的 E2E 用例，再改代码。
- **E2E 不写死演示数据**：演示库会随用例漂移（某 SKU 可能刚好被买空），用例一律现场挑选「在架且库存足够」的商品，必要时用 `E2E_PRODUCT_ID` 显式覆盖。

---

## 🔐 开源安全与演示数据

**演示数据声明**

- 所有客户/员工姓名、手机号、邮箱、企业名称、统一社会信用代码、银行账号、收货地址、发票与物流单号**均为虚构**，不对应任何真实自然人、机构或票据。
- 手机号使用 `1380000900x` / `1390000910x` / `13800138000` 等示例号段；邮箱使用保留域 `.test` / `example.com`。
- 统一社会信用代码、发票代码、银行账号均含 `XXXXXXXX` / `DEMO-` / 全零等占位特征，**不可用于真实开票或结算**；发票销售方信息可经 `INVOICE_SELLER_*` 覆盖。
- 商品图片使用占位图源；支付通道为可复现的 Mock 网关，不连接任何真实支付机构。
- 种子数据位于 `server/src/main/resources/db/migration/V11`~`V15`、`V29`、`V42`~`V50` 与 `seed/DemoDataGenerator`。

**已完成的加固**

1. **凭证外置**：数据库口令、JWT 密钥、支付回调密钥、Redis 口令、发票主体全部走环境变量，`application*.yml` / `pom.xml` 中不含真实凭证。
2. **生产 fail-closed**：`prod` profile 对 `DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET` / `PAY_MOCK_SECRET` 不设默认值；`docker-compose.yml` 用 `${DB_PASSWORD:?...}` 强制要求注入。
3. **无可伪造的默认密钥**：本地未设置时由框架启动时随机生成，公开仓库里的默认值无法用来伪造令牌。
4. **前端无第三方密钥**：两端 `.env.development` 只含本地代理地址、路由与令牌前缀（需随仓库提交以便开箱即用）。
5. **本地产物屏蔽**：`.gitignore` 覆盖 `target/`、`node_modules/`、`dist/`、`*.log`、本地 MySQL 数据目录、Playwright 报告与录屏。

提交前建议自检：

```bash
git status --ignored
git ls-files | grep -Ei '(^|/)(target|node_modules|dist|logs)/|\.log$|\.pem$|\.key$|\.env$|mysql[-_]data|playwright-report|test-results'
```

---

## 🚧 已知限制与 Roadmap

| 类别 | 现状 | 生产化动作 |
|---|---|---|
| 口令存储 | 明文占位 | 替换 BCrypt / Argon2 |
| 有状态组件 | 令牌索引、幂等、限流、确认令牌、在线状态为**进程内实现**（单实例语义） | 换 Redis 适配器（端口已抽象，业务零改动） |
| 文件存储 | 本地磁盘 `LocalFileStorage` | 换对象存储（OSS/S3），保留 `fileId` 语义 |
| 支付 | Mock 网关 | 接入真实通道 + 回调验签 + 对账 |
| 优惠券 | 单券抵扣、无叠加规则引擎 | 叠加/互斥策略、自动发券、预算与核销对账 |
| 权限 | 演示数据全量授权 | 按角色/数据域回收权限点 |
| 报表 | 部分指标为演示快照 | 改为真实聚合或离线数仓 |
| 前端体积 | 管理端 Element Plus 全量引入（主 chunk ≈1.1 MB） | 按需引入 / 路由级分包 |
| 部署 | 单机 Compose | 多实例 + 健康探针 + 灰度 + 全站 HTTPS + 密钥轮换 |

**Roadmap**：`logout-all`（全端下线）· 企业认证双向核验 · 库存批次与效期 · 券叠加与自动发券 · 移动端（H5/小程序）· OpenAPI 对外开放。

更多部署细节见 [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md) 与 [`docs/prod-deploy-checklist.md`](./docs/prod-deploy-checklist.md)；`specs/` 为设计阶段历史快照，**实现以代码为准**。

---

## 🤝 贡献指南

1. Fork → 建分支（`feat/xxx`、`fix/xxx`、`docs/xxx`）。
2. 提交前必须过门禁：`mvn -o clean verify` + 双端 `build` + `npx playwright test`。
3. 遵循既有边界：新增跨模块调用必须走 `service` 接口，否则 ArchUnit 会拦下你的 PR。
4. 新增表结构只能**追加** Flyway 迁移（当前最新 `V50`，下一个是 `V51__xxx.sql`），不得修改历史迁移（会破坏 `sent_test` 校验和）。
5. Commit 信息用中文或英文均可，但需说明「改了什么 + 为什么」。

---

## 📄 License

本项目代码采用 [MIT License](./LICENSE)。第三方依赖各自保留原许可证：管理端基座参考 `pure-admin-thin`（MIT），发票渲染使用 Apache PDFBox（Apache-2.0），其余见各自 `pom.xml` / `package.json`。

<div align="center">

**如果这个项目对你有帮助，点个 ⭐ Star 是最实在的支持。**

</div>
