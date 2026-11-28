# 森特体育用品管理系统 · 部署手册

> 适用版本：2026-11-09 基线（Phase 3~10 + 批 1/3/4 全量收口 + 商品规格持久化修复与字段通俗化命名）
> 配套文档：[项目 README](../README.md)
> 交付状态：**长期 backlog 仍冻结**；本手册描述当前代码的部署方式。商品编辑页字段已按「业务口语版 + 隐藏编号」完成通俗化改名，接口与数据库字段未变。

---

## 1. 部署形态与端口

```
                    ┌──────────────────────────────────────┐
   浏览器 ──80──▶   │ Nginx（docker/nginx.conf）            │
                    │  /admin/  → web-admin/dist 静态资源   │
                    │  /        → web-user/dist  静态资源   │
                    │  /api/    → sent-server:8088          │
                    │  /ws/     → sent-server:8088 (WS)     │
                    └───────────────┬──────────────────────┘
                                    │
                    ┌───────────────▼──────────────────────┐
                    │ Spring Boot 3.2.5 (sent-server.jar)   │
                    │ 18 个业务模块 + Flyway + Security      │
                    └───────┬───────────────────┬──────────┘
                            │                   │
                    ┌───────▼──────┐    ┌───────▼──────┐
                    │ MySQL 8.0    │    │ Redis 7      │
                    │ 3306         │    │ 6379（可选）  │
                    └──────────────┘    └──────────────┘
```

| 组件 | 开发端口 | 容器端口 | 说明 |
|---|---|---|---|
| 后端 API | 8088 | 8088 | 默认端口（避开 Steam 占用的 8080），可用 `SERVER_PORT` 覆盖；`/v3/api-docs` 仅非生产可用 |
| 管理端 Vite dev | 5174 | — | 生产改为 Nginx `/admin/` 静态托管 |
| 用户端 Vite dev | 5173 | — | 生产改为 Nginx `/` 静态托管 |
| MySQL | 3306 | 3306 | `sent_dev` / `sent_test` / `sent_prod` |
| Redis | 6379 | 6379 | 可选；未启动不影响主流程，健康检查已排除 |
| Nginx | — | 80 | 反向代理 + 两个 SPA |

---

## 2. 环境依赖

| 依赖 | 版本 | 必需 | 说明 |
|---|---|---|---|
| JDK | **17** | ✅ | 后端编译与运行（`java -jar`） |
| Maven | 3.9+ | ✅ | 已有离线依赖时可用 `mvn -o` |
| MySQL | **8.0** | ✅ | 必须 `utf8mb4` / `utf8mb4_unicode_ci`，时区 `Asia/Shanghai` |
| Redis | 7 | ⚠️ 可选 | 生产建议部署；不部署时应用可启动，`management.health.redis.enabled=false` |
| Node.js | **22** | ✅ | 仅构建前端时需要 |
| pnpm | 12 | ✅ | workspace（`allowBuilds: esbuild / vue-demi`） |
| Docker + Compose | 任意近期版本 | 容器方案必需 | 见 §6 |
| Nginx | 1.27（容器） | 容器方案必需 | 见 §6 |

---

## 3. 数据库准备

### 3.1 建库

```sql
CREATE DATABASE IF NOT EXISTS sent_prod
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 如需运行集成测试
CREATE DATABASE IF NOT EXISTS sent_test
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 3.2 迁移由 Flyway 自动执行

**不要手工执行迁移 SQL**。后端启动时会按 `classpath:db/migration` 顺序执行 V1~V21，
在 `flyway_schema_history` 留痕，共 **21 个版本 / 55 张业务表**。

两个与部署强相关的迁移：

| 版本 | 内容 | 部署注意 |
|---|---|---|
| `V20__refund_appeal_count.sql` | `refund_order.appeal_count INT NOT NULL DEFAULT 0` | 用户端售后申诉次数 |
| `V21__orders_paid_at_backfill.sql` | ① 把「已支付但 `paid_at` 为 NULL」的历史订单按 `created_at` 回填；② 加覆盖索引 `idx_order_paid_at(pay_status, paid_at)` | **销售趋势报表的数据前置**。历史库升级到本版时会自动执行；大表上 `ALTER TABLE ADD INDEX` 可能耗时，建议在低峰期部署并先做备份 |

> `V21` 是一次性数据修复：此前支付回调链路未写 `orders.paid_at`，导致真实支付订单该列恒 NULL。
> 本版代码已修复写入链路（`OrderPaymentServiceImpl.markPaid` 透传回调时间、`updateHeader` 用
> `COALESCE(#{paidAt}, paid_at)` 只覆盖非空值），迁移负责补齐存量数据。

### 3.3 dev 库的历史校验和例外

部分历史 `sent_dev` 库曾改动过 V5 迁移文件，Flyway 校验和不匹配会阻断启动。
`application-dev.yml` 已固化：

```yaml
spring:
  flyway:
    validate-on-migrate: false
```

**仅限本机开发库**；`test` / `prod` profile 保持严格校验，**不要**把该配置复制到生产。

### 3.4 种子账号说明

登录账号由 `AccountSeeder` 写入**进程内** `InMemoryUserAccountRepository`（`app.seed-accounts.enabled=true`）。
Flyway 迁移只写数据库，不会让种子账号对登录服务可见 —— 因此**不要**试图通过往数据库插账号来登录。

| 角色 | username | 密码 | aud |
|---|---|---|---|
| 管理员 | `admin` | `123456` | `admin` |
| 客户 | `13800138000` | `123456` | **`user`** |
| 客服 | `service` | `123456` | `admin` |
| 业务岗 | `sales_mgr` / `sales` / `finance` / `warehouse` | `123456` | `admin` |

> ⚠️ 这些是**演示账号、明文弱口令**。`seed-accounts.enabled` 与 `demo-data.enabled` 在
> `application-prod.yml` 中默认不开启，生产环境必须替换为真实账号体系并关闭演示开关。

---

## 4. 后端构建与启动（裸机 / 本机）

### 4.1 全量门禁（部署前必跑）

```bash
cd server
mvn -o clean verify
```

预期结果：

```
[INFO] Tests run: 615, Failures: 0, Errors: 0, Skipped: 0     # 单元
[INFO] Tests run: 38,  Failures: 0, Errors: 0, Skipped: 0     # 集成（真库）
[INFO] Tests run: 4,   Failures: 0, Errors: 0, Skipped: 0     # ArchUnit
[INFO] All coverage checks have been met.                      # JaCoCo BUNDLE >= 0.80
[INFO] BUILD SUCCESS
```

> 集成测试 `*IT` 需要可达的 MySQL（`sent_test`）。数据库不可达时由
> `@EnabledIf("com.sent.sports.support.ItDatabase#available")` 整类跳过。

### 4.2 启动

```bash
java -jar server/target/sent-server.jar \
  --spring.datasource.password=<DB_PASSWORD>      # 默认 --server.port=8088
```

生产环境用环境变量切换 profile（见 §7）：

```bash
APP_PROFILE=prod APP_ENV=prod DB_HOST=<host> DB_PASSWORD=<pwd> REDIS_HOST=<host> \
  java -jar server/target/sent-server.jar              # 默认 8088，如需改：SERVER_PORT=9090
```

### 4.3 两个高频启动故障

| 现象 | 原因 | 处理 |
|---|---|---|
| `mvn clean` 报 `Failed to delete target/sent-server.jar` | 正在运行的 JVM 锁住 jar 文件（Windows 文件锁） | **先停掉后端 java 进程**再执行 `mvn clean/package`，完成后重启 |
| `APPLICATION FAILED TO START: Port 8088 was already in use` | 本机有其他进程占用 8088 | 用 `netstat -ano \| grep :8088` 交叉确认归属（PowerShell `Get-NetTCPConnection` 可能返回陈旧缓存）；临时换端口设环境变量 `SERVER_PORT`，并同步改前端 `VITE_PROXY_API_TARGET` / `VITE_PROXY_WS_TARGET` |
| 请求 404 / 返回非本项目内容，但日志显示启动成功 | 8080 被 Steam 的 `steamwebhelper.exe` 抢占，前端代理打到 Steam 上 | 已通过默认端口改为 8088 规避；若见到老版本 8080 配置，按 §1 统一到 8088 |

---

## 5. 前端构建

```bash
# 安装 workspace 依赖（首次）
pnpm install

# 管理端
cd web-admin && npx vue-tsc -b && npx vite build      # 产物 web-admin/dist
# 用户端
cd web-user  && npx vue-tsc -b && npx vite build      # 产物 web-user/dist
```

两端 `vite.config.ts` 的代理目标支持环境变量覆盖，默认 `http://127.0.0.1:8088`：

| 变量 | 默认值 | 用途 |
|---|---|---|
| `VITE_PROXY_API_TARGET` | `http://127.0.0.1:8088` | `/api` 代理 |
| `VITE_PROXY_WS_TARGET` | `ws://127.0.0.1:8088` | `/ws` 代理 |

### 开发模式启动（本机）

pnpm 12 的 `pnpm dev` 可能卡在 `--verify-deps-before-run-install` 且无输出、端口不监听。
可靠做法是绕开 pnpm 脚本直接调用 Vite，且**先起一端确认 200 再起另一端**（两端首次并发会争抢 `.pnpm/esbuild` 目录）：

```bash
cd web-user  && node node_modules/vite/bin/vite.js --port 5173 --host   # 先起
cd web-admin && node node_modules/vite/bin/vite.js --port 5174 --host   # 后起
```

### 构建告警与沙箱限制（不影响产物）

- **主 chunk 约 1.13 MB**：Element Plus 全量引入导致 `Some chunks are larger than 500 kB` 警告，功能不受影响。
- **`SAFE_DELETE_BULK_CONFIRM_REQUIRED`**：Vite 清空 `dist/assets` 时，若旧 chunk 数超过批量删除阈值会失败（此时 `1790 modules transformed` 通常已成功，失败发生在 `prepareOutDir`）。绕过方式：

  ```bash
  mv dist dist_prev_$(date +%s)     # 改名而非删除
  npx vite build
  ```

---

## 6. 容器化部署（Docker Compose）

编排文件：`docker/docker-compose.yml`（4 个服务：`mysql` / `redis` / `server` / `nginx`）。

```bash
cd docker
DB_PASSWORD='<强密码>' docker compose up -d --build
```

Compose 会：
- 启动 MySQL 8.0（`sent_prod`）与 Redis 7，两者带 healthcheck；
- 用 `server/Dockerfile` 多阶段构建后端 jar，`server` 服务等待 DB/Redis 健康后才启动；
- Nginx 挂载 `web-admin/dist` → `/usr/share/nginx/html/admin`、`web-user/dist` → `/usr/share/nginx/html/user`，
  并反代 `/api/` 与 `/ws/` 到 `server:8088`。

**部署顺序**：先执行 §5 生成两端 `dist`，再 `docker compose up`，否则 Nginx 挂载目录为空。

### 容器方案的 3 个已知注意点（冻结版现状，未改动）

1. **`server/Dockerfile` 的 `ENTRYPOINT`** 写成
   `["java","-jar","-Dspring.profiles.active=prod","app.jar"]`，JVM 参数放在 `-jar` 之后不会生效；
   实际依赖 compose 的环境变量 `SPRING_PROFILES_ACTIVE: prod` 兜底。若脱离 compose 单独
   `docker run`，务必显式传 `-e SPRING_PROFILES_ACTIVE=prod`。
2. **`docker/nginx.conf`** 中透传刷新令牌响应头使用
   `add_header X-New-Token $sent_header_x_new_token always;`。Nginx 原生上游响应头变量为
   `$upstream_http_x_new_token`，`$sent_header_*` 不是内置变量，`nginx -t` 可能报 unknown variable。
   上线前请执行 `nginx -t` 校验；若报错，改为 `$upstream_http_x_new_token` /
   `$upstream_http_x_token_expire_in`（或用 `proxy_pass_header`）。
3. **`docker/` 目录下没有 `Dockerfile` 以外的镜像构建脚本**，compose 的 `build.context: ./server`
   依赖仓库中已存在的 `server/Dockerfile` 与 `server/mvnw`（构建时 `./mvnw` 不存在会 fallback 到系统 `mvn`）。

> 以上三项属于冻结范围内的既有配置，本手册如实标注，不代表已修复。

---

## 7. 配置项清单

### 7.1 Profile 差异

| 项 | dev | test | prod |
|---|---|---|---|
| 数据库 | `sent_dev` | `sent_test` | `sent_prod` |
| 密码默认值 | `123456` | `123456` | 由 `DB_PASSWORD` 注入 |
| Flyway `validate-on-migrate` | **false**（历史库例外） | 严格 | 严格 |
| Swagger `/v3/api-docs` | 开 | 开 | **关** |
| 种子账号 | 开 | 开（供 IT 登录） | 不开 |
| 演示数据生成器 | 开 | **关** | 不开 |
| SQL 日志 | stdout | slf4j | 关闭 |
| Actuator health 详情 | `when-authorized` | 同 dev | **`never`** |
| 日志级别 | info | info | `root: info` / `com.sent.sports: warn` |

激活方式：`APP_PROFILE` 环境变量（默认 `dev`），或 `--spring.profiles.active=prod`。

### 7.2 关键环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `APP_PROFILE` / `APP_ENV` | `dev` | profile 与环境标识 |
| `DB_HOST` | `mysql`（prod） / `127.0.0.1` | MySQL 主机 |
| `DB_USERNAME` | `root` | 数据库用户 |
| `DB_PASSWORD` | 空（prod 必填，缺失即启动失败） / `123456`（dev/test 演示假口令） | **生产必须显式设置为强密码** |
| `REDIS_PASSWORD` | 空 | Redis 鉴权口令，本地默认无口令 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB` | `127.0.0.1` / `6379` / `0` | Redis 连接 |
| `FLYWAY_ENABLED` | `true` | 是否自动迁移 |
| `JWT_SECRET` | 启动时随机生成（dev/test）；prod 必填、无默认值 | **生产必须注入 ≥32 字节随机密钥** |
| `PAY_MOCK_SECRET` | 启动时随机生成（dev/test）；prod 必填、无默认值 | 模拟支付回调签名密钥，生产必须注入 |
| `APP_CORS_ORIGINS` | 本机 5173 / 5174 dev server | 部署时改为真实前端域名，禁止通配符 |
| `JWT_ACCESS_TTL_MS` | `7200000` | 访问令牌 2 小时 |
| `JWT_REFRESH_TTL_MS` | `604800000` | 刷新令牌 7 天 |
| `JWT_RENEW_THRESHOLD_MS` | `900000` | 续期阈值 15 分钟 |
| `UPLOAD_BASE_DIR` | `/data/upload` | 本地文件存储根目录 |
| `SERVER_PORT` | `8088` | 后端监听端口（容器内外一致；8080 会被 Steam 的 `steamwebhelper.exe` 抢占） |
| `VITE_PROXY_API_TARGET` / `VITE_PROXY_WS_TARGET` | `http://127.0.0.1:8088` / `ws://127.0.0.1:8088` | 前端 dev 代理 |

### 7.3 上线前必改的安全项

- [ ] 注入 `JWT_SECRET`（≥32 字节随机值）与 `PAY_MOCK_SECRET`；prod 缺失即启动失败
- [ ] 设置强 `DB_PASSWORD`，限制数据库端口仅内网可达
- [ ] 关闭 `app.seed-accounts.enabled` 与 `app.demo-data.enabled`
- [ ] 确认 `springdoc.api-docs.enabled=false`、Swagger 不可公网访问
- [ ] 确认 Actuator 仅暴露 `health,info,metrics` 且 `show-details: never`
- [ ] 上传目录 `UPLOAD_BASE_DIR` 挂载持久卷并做好权限控制
- [ ] Nginx 配置 `nginx -t` 通过（见 §6 注意点 2）

---

## 8. 健康检查与冒烟验证

### 8.1 后端

```bash
# 接口文档（dev/test；prod 预期 404）
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8088/v3/api-docs

# Actuator
curl -s http://127.0.0.1:8088/actuator/health
# {"status":"UP"}
```

启动日志关键行：

```
Migrating schema `sent_dev` to version "21 - orders paid at backfill"
Successfully applied 1 migration to schema `sent_dev`, now at version v21
Tomcat started on port 8088 (http) with context path ''
Started SentSportsApplication in ~16 seconds
```

### 8.2 双端登录（经 Vite 代理或 Nginx）

```bash
# 用户端（注意 aud=user，username 是手机号）
curl -s -X POST http://127.0.0.1:5173/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"13800138000","password":"123456","aud":"user","deviceId":"smoke"}'
# {"code":0,"message":"ok","data":{"accessToken":"...","refreshToken":"..."}}

# 管理端
curl -s -X POST http://127.0.0.1:5174/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456","aud":"admin","deviceId":"smoke"}'
# {"code":0,...}
```

### 8.3 关键业务端点

| 端点 | 预期 |
|---|---|
| `GET /api/refunds?page=1&size=10`（用户令牌） | 真分页数据；`status` 筛选生效；他人单详情 40401、无令牌 40101 |
| `GET /api/admin/reports/sales-trend?days=30`（管理员令牌） | 30 个连续日点 + 汇总；`days` 支持 7/30/90，收敛 1~90 |
| `GET /api/admin/logs`、`/api/admin/payment-records` | 真日志 / 真回款分页 |
| `POST /api/admin/invoices` | 开票创建 + 状态化 404 文案 |
| `GET /ws/chat?token=` | 客服双身份 WebSocket |

### 8.4 销售趋势数据对账（部署后建议执行一次）

报表口径：以 `orders.paid_at` 为时间基准、`SUM(paid_cent)` 聚合、只计
`pay_status IN ('PAID','PART_REFUNDED')`，全额退款 `FULL_REFUNDED` 整单剔除。

```sql
-- 与接口返回的 totalAmountCent / totalOrderCount 应完全一致
SELECT COUNT(*) AS orders, SUM(paid_cent) AS amount_cent
FROM orders
WHERE pay_status IN ('PAID','PART_REFUNDED')
  AND paid_at >= '<fromDate> 00:00:00'
  AND paid_at <  DATE_ADD('<toDate>', INTERVAL 1 DAY);

-- 确认为 0（V21 回填是否生效）
SELECT COUNT(*) FROM orders
WHERE paid_at IS NULL
  AND pay_status IN ('PAID','PART_REFUNDED','FULL_REFUNDED');

-- 确认索引存在
SHOW INDEX FROM orders WHERE Key_name = 'idx_order_paid_at';

-- 确认迁移版本
SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1;  -- 21
```

冻结版验收时对账示例（`sent_dev` 演示数据）：某时点近 30 天接口返回 **994 单 / 299,270,037 分**，
独立 SQL 查询返回完全相同的值；另有 49 笔 `FULL_REFUNDED`（13,123,017 分）确认未计入。
演示库含定时任务与验证用支付回调，绝对数值会随时间变化 —— **验收标准是「接口与 SQL 对账一致」，
不是某个固定数字**。生产库请以自己的数据执行上述对账。

---

## 9. 备份与恢复

```bash
# 全库备份（含结构 + 数据 + flyway_schema_history）
mysqldump -h127.0.0.1 -P3306 -uroot -p<密码> \
  --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 sent_prod > sent_prod_$(date +%Y%m%d).sql

# 恢复
mysql -h127.0.0.1 -P3306 -uroot -p<密码> sent_prod < sent_prod_YYYYMMDD.sql
```

要点：
- **必须一起备份 `flyway_schema_history`**，否则恢复后 Flyway 会重复执行迁移导致失败。
- 上传目录 `UPLOAD_BASE_DIR` 不在数据库内，需单独备份。
- 升级到含 V21 的版本前先备份：V21 含 `UPDATE` 回填与 `ALTER TABLE ADD INDEX`，大表耗时且不可逆（索引可删，数据回填不可自动回滚）。

---

## 10. 故障排查速查

| 现象 | 排查方向 |
|---|---|
| 启动报 `Port 8088 was already in use` | `netstat -ano \| grep :8088`，注意 `Get-NetTCPConnection` 陈旧缓存；换端口用 `SERVER_PORT` |
| 端口被 Steam 占用（8080 老配置） | 本机 `steamwebhelper.exe` 常驻 127.0.0.1:8080；本项目默认已改 8088，若前端代理仍指 8080 请查 `.env.development` |
| `mvn clean/package` 删不掉 jar | 停掉运行中的 java 进程 |
| Flyway 校验和不匹配 | 仅 dev 库允许 `validate-on-migrate=false`；其它环境应从干净库重新迁移，勿改历史迁移文件 |
| 集成测试全类跳过 | MySQL 不可达或 `sent_test` 未创建；`@EnabledIf(ItDatabase#available)` 会跳过而非失败 |
| 登录返回 40401 | 用户端 `username` 必须是手机号 `13800138000`，且 `aud=user`；`aud=admin` 走另一套账号 |
| 前端 `pnpm dev` 卡死无输出 | 用 `node node_modules/vite/bin/vite.js --port XXXX --host`；两端不要并发首次启动 |
| `vite build` 报批量删除保护 | `mv dist dist_prev_<ts>` 后重建 |
| Nginx 反代后刷新令牌失效 | 检查 `X-New-Token` 透传配置（§6 注意点 2），执行 `nginx -t` |
| 销售趋势全 0 / 金额偏小 | 确认 V21 已执行、`paid_at` 非 NULL、索引存在；确认订单 `pay_status` 是 `PAID`/`PART_REFUNDED` 而非 `UNPAID` |
| WebSocket 连不上 | Nginx `/ws/` 需 `Upgrade` 头与 `proxy_http_version 1.1`；客服会话需先接待为 ACTIVE |

---

## 11. 冻结声明

按交付约定，本版本之后**不再进行长期 backlog 功能开发**。以下内容属于已知保留项，不在本次部署范围内：

- `/api/admin/reports/sales`、`/reports/sales-rank` 仍为单条快照桩（保留以保证既有契约兼容）；
- 强制全端下线、企业认证审核未实现；
- `ContractRemindJob` / `PointExpireJob` 仅只读统计；
- 前端主 chunk 体积（Element Plus 全量引入）未做分包优化；
- §6 列出的 3 个容器配置注意点未修改。

本版另修复了管理端商品编辑页规格矩阵保存后不生效的问题：新增 / 删除 / 改价 / 改库存均真落库，并新增 `ProductAdminCrudIT` 覆盖「重新查详情」场景。
同轮按用户确认的「业务口语版 + 隐藏编号」方案完成字段通俗化：面板与列名改为「商品规格 / 规格编码 / 规格名称 / 售价 / 最低折后价 / 可用库存 / 批量折扣」，按钮改为「新增规格 / 删除该规格 / 新增折扣档 / 保存折扣 / 清空折扣」，并隐藏系统编号列（`skuId` 仍随表单提交）；商品列表列名同步为「规格数 / 已配折扣规格」。接口路径、DTO 字段与数据库结构均未变化，无迁移。
如需继续演进，请以本手册与 README 记录的门禁基线
（615 单元 + 38 集成 + ArchUnit 4/4 + JaCoCo ≥0.80）为起点重新立项。
