# 森特体育用品管理系统 — 生产部署配置审计与必填环境变量清单（Step 3）

> 目标环境：阿里云公网部署 · 审查文件：`application-prod.yml`、`application.yml`、`logback-spring.xml`、`docker/nginx.conf`、`web-*/vite.config.ts`、`web-*/.env.*`

---

## 一、数据库连接池（HikariCP）

**现状**：`application.yml` / `application-prod.yml` **均未显式配置** `spring.datasource.hikari.*`，使用 Spring Boot 默认 `maximum-pool-size = 10`。

**风险**：公网并发订单/报表查询下，10 连接易成瓶颈，请求排队甚至超时。

**建议（写入 `application-prod.yml`）**：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20          # 4c8g 单机约 20~30；多实例另行评估
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000         # 须小于云数据库/中间件空闲断开时间
      pool-name: sent-hikari
```

---

## 二、日志级别与敏感数据

**级别**：`application-prod.yml` 设 `root: info`、`com.sent.sports: warn` —— **合理，不会打印 DEBUG/TRACE**。

**敏感数据核查**：
- `logback-spring.xml` 的 `LOG_PATTERN` 含 `%X{traceId}`，**未**打印密码 / Token / 身份证等敏感字段。
- `GlobalExceptionHandler` 仅记录 `biz error code=xxx msg=...`（业务消息，无请求体原文）。
- `AuthFilter` 写错误体时**不回显令牌原文**；`MockPayGateway` 回调日志仅打 `code/orderNo`，安全。

**建议（非阻塞）**：上线后再 grep 一次确认无 `log.info(... password/token/secret ...)`；生产 `LOG_DIR` 通过 `${LOG_DIR}` 指向挂载盘（已具备 `maxHistory=30` / `totalSizeCap=2GB`）。

---

## 三、前端 baseURL（是否写死 localhost:8080）

**结论：✅ 未写死，安全**

- `web-admin` / `web-user` **均无 `.env.production` 文件**，仅有 `.env.development`。
- 前端运行期 `baseURL = VITE_API_BASE || '/api'`，默认 **`/api`（相对路径）**；`VITE_PROXY_API_TARGET` 仅被 **Vite dev server 代理**使用，**不会编入生产包**。
- 生产由 Nginx 反向代理 `/api/` → 后端，因此**不存在写死 `localhost:<后端端口>` 的问题**（开发默认端口现为 8088）。

**缺口（建议补齐）**：缺少 `.env.production` 意味着生产构建依赖代码内默认值（`/admin/`、`/`、`sent_admin`/`sent_user`、`admin`/`user`）。当前值正确，但建议显式创建两份 `.env.production` 固化，避免误改 `.env.development` 引发歧义：
```bash
# web-admin/.env.production
VITE_API_BASE=/api
VITE_PUBLIC_PATH=/admin/
VITE_TOKEN_STORAGE_PREFIX=sent_admin
VITE_AUD=admin
VITE_ROUTER_HISTORY=h5

# web-user/.env.production
VITE_API_BASE=/api
VITE_PUBLIC_PATH=/
VITE_TOKEN_STORAGE_PREFIX=sent_user
VITE_AUD=user
VITE_ROUTER_HISTORY=h5
```
> 注意：Nginx 已按 `/admin/` 与 `/` 分别托管两个静态站点，`VITE_PUBLIC_PATH` 必须与之一致。

---

## 四、Nginx 配置（docker/nginx.conf）审计

**现状**：反向代理 `/api/`、`/ws/`（WebSocket upgrade）、两端静态托管；`client_max_body_size 50m`（与后端上传上限一致）；透传 `X-Forwarded-*` 与自定义刷新头；`/ws/` 已设 `proxy_read/send_timeout 3600s`。

**⚠️ 公网（阿里云）缺口**：
1. **仅 80 端口，无 HTTPS/TLS**：公网必须启用 443 + 证书 + `HSTS`，并把 80 301 到 443。当前无 TLS，令牌与支付回调明文暴露。
2. **缺少安全响应头**：建议加 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Content-Security-Policy`、`Referrer-Policy`。
3. **缺少 gzip**：静态资源建议 `gzip on;` 降低首屏体积。
4. **缺少限流兜底**：建议 Nginx 层 `limit_req_zone` + `limit_req` 作为第一道防刷（应用层已有 `RateLimitInterceptor`，但 Nginx 更前置）。
5. 普通 `/api/` 建议显式设 `proxy_read_timeout 60s`。

**建议**：将 `docker/nginx.conf` 升级为生产模板（含 443 + 安全头 + gzip + limit_req），证书路径参数化。

---

## 五、必填环境变量清单（运维部署核对）

应用通过 `${VAR:default}` 读取。以下为生产**必填 / 强烈建议覆盖**项（默认值仅适用于本地，生产必须显式设置）：

| 变量 | 作用 | 生产要求 | 默认值（勿用于生产） |
|---|---|---|---|
| `APP_PROFILE` | 激活 profile | `prod` | `dev` |
| `APP_ENV` | 环境标识（守卫校验） | `prod`（须与库名 `sent_prod` 一致） | `dev` |
| `DB_HOST` | MySQL 地址 | 生产 RDS 内网地址 | `mysql` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 | 独立低权限账号，**强密码** | **无默认值，缺失即启动失败** |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB` | Redis（当前限流/会话为进程内，预留） | 多实例必配 | `127.0.0.1:6379` |
| `JWT_SECRET` | JWT 签名密钥 | **≥32 字节随机密钥** | **无默认值（prod 必填）**；dev/test 启动时随机生成 |
| `PAY_MOCK_SECRET` | 模拟支付回调签名密钥 | 独立随机值 | **无默认值（prod 必填）** |
| `JWT_ACCESS_TTL_MS` / `JWT_REFRESH_TTL_MS` | 令牌有效期 | 按安全策略（如 2h / 7d） | `7200000 / 604800000` |
| `UPLOAD_BASE_DIR` | 文件上传根目录 | 挂载盘路径（如 `/data/upload`） | `/data/upload` |
| `LOG_DIR` | 日志目录 | 挂载盘（如 `/data/logs`） | `logs` |
| `FLYWAY_ENABLED` | 是否执行迁移 | `true`（首启后建议复核） | `true` |
| `JOB_*_MS`（10 项） | 各定时任务间隔 | 按业务节奏，默认 60000ms | `60000` |
| `PRESENCE_HEARTBEAT_TIMEOUT_MS` / `WS_IDLE_TIMEOUT_MS` | 客服心跳 / WS 空闲 | 60s / 300s | `60000 / 300000` |

**⚠️ 上线前置项**：`application-prod.yml` 对 `DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET` / `PAY_MOCK_SECRET` 均不设默认值，未注入时应用拒绝启动；dev/test 的 `123456` 只是本机演示假口令，绝不能带到任何联网环境。`APP_PROFILE`/`APP_ENV` 必须与库名（`sent_prod`）一致，否则 `EnvironmentGuard` 会拒绝启动（已有守卫）。

**附加提醒**：部署前请停止占用 8088 端口的进程（本轮验证曾发现 PID 4120 仍在 `java -jar sent-server.jar`，导致 `repackage` 重命名失败；当时端口为 8080，现已统一为 8088）。正式发布由 CI 全新构建，不受此影响。
