# sent-sports-b2b · 森特体育用品 B2B 管理系统

> **状态：本地开发 / 教学演示用的初版原型。** 本项目**尚未部署到公网服务器**，仓库内全部为虚构演示数据，请勿直接用于生产环境或处理任何真实业务数据。

一个面向体育用品批发场景的 B2B 业务系统原型：企业客户认证与授信、商品与阶梯价 / 一客一价、购物车与订单、模拟支付与退款、定制图稿审核、库存与发货、应收核销与发票、会员积分、客服实时会话，以及配套的管理端与商城端。

---

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.2.5、MyBatis-Plus、Flyway、Spring Security、Spring WebSocket |
| 数据库 / 缓存 | MySQL 8.0（必需）、Redis（可选，未启用不影响主流程） |
| 前端 | Vue 3、Vite、TypeScript、Pinia、Element Plus（管理端 / 商城端双端） |
| 共享 | `packages/shared`：HTTP 内核、令牌存储、类型与格式化工具 |
| 测试 | JUnit 5 + Mockito + ArchUnit + JaCoCo、Failsafe 真库集成测试、Playwright E2E |
| 其它 | Apache PDFBox 3（发票 PDF 现场渲染）、Docker Compose 编排骨架 |

后端按业务域拆分为 `auth / system / customer / member / product / stock / cart / order / payment / refund / customize / finance / chat / notify / report / job` 等模块，通过 ArchUnit 约束分层与跨模块访问。

---

## 目录结构

```
sent-sports-b2b/
├── server/                 # Spring Boot 后端
│   ├── src/main/java/      # 业务模块、装配、配置
│   ├── src/main/resources/ # application*.yml、Flyway 迁移 V1~V21
│   └── src/test/           # 单元测试 / 集成测试
├── web-admin/              # 管理端（Vite dev :5174，base /admin/）
├── web-user/               # 商城端（Vite dev :5173，base /）
├── packages/shared/        # 双端共享的 HTTP 内核与类型
├── e2e/                    # Playwright 端到端用例
├── docker/                 # docker-compose.yml + nginx.conf（可选）
├── docs/                   # 部署与上线检查清单
├── specs/                  # 设计与任务文档（历史快照，以代码为准）
├── .env.example            # 环境变量模板（仅占位值，可安全提交）
└── .gitignore              # 构建产物 / 本地数据 / 密钥屏蔽规则
```

---

## 本地快速开始

### 前置条件

- JDK 17、Maven 3.9+
- Node.js 22、pnpm
- 本机 MySQL 8.0（字符集 `utf8mb4` / `utf8mb4_unicode_ci`，时区 `Asia/Shanghai`）
- Redis 可选；本项目默认不依赖独立 Redis 实例

### 1. 建库

```sql
CREATE DATABASE IF NOT EXISTS sent_dev  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS sent_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，把 DB_PASSWORD 改成你本机的 MySQL 密码；.env 已被 .gitignore 忽略
```

也可以直接在启动命令里注入，无需创建 `.env`。**仓库中不存在任何真实口令**。

### 3. 启动后端

```bash
cd sent-sports-b2b/server
export DB_USERNAME=root DB_PASSWORD='<你的本机数据库密码>'
mvn -o clean verify                       # 全量门禁；只跑本地可改用 mvn -o -DskipTests package
java -jar target/sent-server.jar          # 默认监听 8088，可用 SERVER_PORT 覆盖
```

健康检查：

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8088/v3/api-docs   # 200
curl -s http://127.0.0.1:8088/actuator/health                                 # {"status":"UP"}
```

> 后端默认端口为 **8088**（避开常被其它软件占用的 8080）。如需改端口，设 `SERVER_PORT`，并同步修改两端 `.env.development` 中的 `VITE_PROXY_API_TARGET` / `VITE_PROXY_WS_TARGET`。

### 4. 启动前端

建议先起一端、确认可访问后再起另一端（首次并发安装依赖可能争抢 pnpm 缓存）：

```bash
cd sent-sports-b2b
pnpm install

cd web-user  && pnpm exec vite --port 5173 --host
cd web-admin && pnpm exec vite --port 5174 --host
```

| 端 | 地址 |
|---|---|
| 商城端 | `http://localhost:5173/` |
| 管理端 | `http://localhost:5174/admin/` |

前端开发态通过 Vite 同源代理访问 `/api` 与 `/ws`；生产构建使用相对路径，由 Nginx 反向代理，不存在写死公网地址的问题。

---

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `APP_PROFILE` / `APP_ENV` | `dev` | 运行 profile；`prod` 会关闭 Swagger 并收紧 Actuator |
| `SERVER_PORT` | `8088` | 后端监听端口 |
| `DB_HOST` / `DB_USERNAME` / `DB_PASSWORD` | 本机演示值 | 数据库连接。`prod` profile 下**无默认值**，缺失即启动失败 |
| `JWT_SECRET` | 启动时随机生成 | Access Token 签名密钥；`prod` 下必须注入，长度建议 ≥32 字节 |
| `PAY_MOCK_SECRET` | 启动时随机生成 | 模拟支付回调签名密钥；`prod` 下必须注入 |
| `REDIS_PASSWORD` | 空 | Redis 鉴权口令，本地默认不启用 |
| `APP_CORS_ORIGINS` | 本机两个 dev server | 允许的跨域来源，逗号分隔；生产必须改成真实前端域名 |
| `UPLOAD_BASE_DIR` | `/data/upload` | 上传文件根目录 |
| `INVOICE_*` | 虚构演示值 | 发票销售方信息，见下方「演示数据声明」 |
| `VITE_PROXY_API_TARGET` / `VITE_PROXY_WS_TARGET` | `http://127.0.0.1:8088` / `ws://127.0.0.1:8088` | 仅开发态代理使用，不会编入生产包 |

完整清单见 `.env.example`。

---

## 演示账号（仅 dev / test profile）

| 角色 | 登录名 | 密码 | `aud` |
|---|---|---|---|
| 超级管理员 | `admin` | `123456` | `admin` |
| 客户（手机号即登录名） | `13800138000` | `123456` | `user` |
| 客服 / 销售主管 / 销售 / 财务 / 仓管 | `service` `sales_mgr` `sales` `finance` `warehouse` | `123456` | `admin` |

登录请求体：`{ "username": "...", "password": "...", "aud": "admin|user", "deviceId": "..." }`。

> 这些账号由 `AccountSeeder` 在 **dev / test** profile 注入进程内账号仓储，仅用于本地演示；`prod` profile 不加载种子账号，且强制要求注入密钥与数据库凭证。口令目前是明文占位实现，上线前必须替换为 BCrypt 等哈希方案。

---

## 演示数据声明

- 所有客户姓名、员工姓名、手机号、邮箱、企业名称、统一社会信用代码、银行账号、收货地址、发票与物流单号**均为虚构**，不对应任何真实自然人、机构或票据。
- 手机号统一使用 `1380000900x` / `1390000910x` / `13800138000` 等示例号段；邮箱使用保留域 `.test` 与 `example.com`。
- 统一社会信用代码、发票代码、银行账号均含 `XXXXXXXX` / `DEMO-` / 全零等占位特征，不可用于真实开票或结算。
- 发票 PDF 的销售方名称、税号、地址电话、开户行为演示占位值，**禁止用于真实开票**；如需替换，通过 `INVOICE_SELLER_*` 环境变量注入合法主体信息。
- 商品图片使用 `picsum.photos` 占位图源；支付通道为可复现的 Mock 网关，不连接任何真实支付机构。
- 仓库中**不含**真实身份证号、真实企业票据或真实个人隐私数据。种子数据位于 `server/src/main/resources/db/migration/V11`~`V15` 与 `DemoDataGenerator`。

---

## 开源安全说明

本仓库在首次公开前已完成以下处理：

1. **凭证外置**：数据库口令、JWT 密钥、支付回调密钥、Redis 口令、发票主体信息全部通过环境变量注入；`application.yml` / `application-dev.yml` / `pom.xml` 中不含任何真实凭证。
2. **生产 fail-closed**：`application-prod.yml` 对 `DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET` / `PAY_MOCK_SECRET` 不设默认值，未注入时应用拒绝启动；`docker-compose.yml` 同样强制要求 `DB_PASSWORD`。
3. **无可伪造的默认密钥**：本地未设置 `JWT_SECRET` / `PAY_MOCK_SECRET` 时由框架在启动时生成随机值，因此公开仓库中的默认值无法被用来伪造令牌（副作用：本地重启后旧令牌失效）。
4. **前端无第三方密钥**：`web-admin/.env.development` 与 `web-user/.env.development` 只含本地代理地址、路由与令牌前缀，不含任何 API Key；这些文件需要随仓库提交以便开箱即用。
5. **本地产物屏蔽**：`.gitignore` 覆盖 `target/`、`node_modules/`、`dist/`、`.idea/`、`.vscode/`、`*.log`、本地 MySQL 数据目录（含 TLS 私钥）、Playwright 报告与录屏、本机会话记忆等。
6. **路径清理**：源码与文档不含开发者本机绝对路径、个人邮箱或机器名；所有示例命令使用相对路径与 `sent-sports-b2b/` 目录名。

**提交前建议再自检一次**（确保没有敏感文件被暂存）：

```bash
git status --ignored
git ls-files | grep -Ei '(^|/)(target|node_modules|dist|logs)/|\.log$|\.pem$|\.key$|\.env$|mysql[-_]data|playwright-report|test-results'
```

如要真正部署到公网，至少还需完成：口令哈希（BCrypt）、接入真实支付通道并校验回调签名、细粒度权限回收、全站 HTTPS、密钥轮换与审计、关闭演示数据生成器与种子账号、Nginx 配置校验。

---

## 测试

```bash
# 后端门禁：单元测试 + 集成测试 + ArchUnit 4 条 + JaCoCo 覆盖率
cd sent-sports-b2b/server && mvn -o clean verify

# 双端类型检查与构建
cd sent-sports-b2b/web-admin && pnpm exec vue-tsc -b && pnpm exec vite build
cd sent-sports-b2b/web-user  && pnpm exec vue-tsc -b && pnpm exec vite build

# 端到端（需先启动三端服务）
cd sent-sports-b2b/e2e && npx playwright test
```

集成测试需要本机可访问的 `sent_test` 库；数据库不可达时相关测试类会整类跳过而不会误报失败。

---

## 已知限制（初版）

- 报表中的销售趋势 / 销售排行仍为演示快照实现；部分定时任务只做只读统计。
- 管理端 Element Plus 为全量引入，主 chunk 偏大（约 1.1 MB）。
- 权限模型在演示数据中统一授予全部权限点，生产需按业务域回收。
- 更多细节见 `docs/DEPLOYMENT.md` 与 `specs/`（规格文档为历史快照，实现以代码为准）。

---

## 许可证

本项目代码采用 [MIT License](./LICENSE)。第三方依赖各自保留原许可证；管理端基座参考了 `pure-admin-thin`（MIT），发票渲染使用 Apache PDFBox（Apache-2.0）。若你修改了许可证或版权主体，请同步更新 `LICENSE` 与本节说明。
