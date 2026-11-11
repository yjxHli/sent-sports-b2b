# agents.md · 工程红线

> 本文件是开发与 AI 代码生成的**强制约束**。与 `spec.md`（要做什么）和 `plan.md`（怎么做）冲突时，以本文件为准。
> 原文件第 4 条被截断（"危险操作必须添加二次确认机"），已补全并落地为可执行规则，详见第 4 节。

## 需求：database

### R-01 禁止生成 DROP TABLE / DROP DATABASE

- 迁移脚本中**禁止**出现 `DROP TABLE`、`DROP DATABASE`、`TRUNCATE`。
- 需要下线字段时，用"标记废弃 + 停止读写 + 后续人工归档"，不做物理删除。
- 需要下线表时，先改名加 `_deprecated` 前缀并观察一个发布周期，再人工处理。
- CI 扫描 `db/migration/*.sql`，命中上述语句直接构建失败。

### R-02 所有 SQL 必须使用参数化查询

- MyBatis 只允许 `#{}`，**禁止 `${}` 拼接用户输入**。
- 动态排序、动态列名必须走白名单枚举，不接受前端传入的字段名。
- 禁止字符串拼接 SQL；禁止 `Statement`，统一 `PreparedStatement`（ORM 已保证）。
- CI 扫描 Mapper XML 中的 `${`，除框架变量（`${ew.customSqlSegment}` 等）外一律报错。

### R-03 操作前必须验证环境（生产 / 测试 / 开发）

- 应用启动时校验 `app.env` 与数据源库名后缀一致（`sent_dev` / `sent_test` / `sent_prod`），不一致直接终止启动。
- 所有写操作日志必须带 `env` 标记，便于事后区分数据来源。
- 生产环境**禁止**通过接口执行数据修复；只允许运维脚本 + 双人复核。
- 危险脚本执行前必须打印目标环境并等待人工确认，不允许"一键跑全环境"。

### R-04 危险操作必须添加二次确认机制

**确认方式**：前端弹窗要求**手动输入确认短语**（禁用粘贴），后端校验一次性 `ConfirmToken`。

| 操作 | 确认短语 | action |
| --- | --- | --- |
| 关闭订单（触发退款） | `确认关闭` | `ORDER_CLOSE` |
| 回款核销 | `确认核销` | `PAYMENT_WRITE_OFF` |
| 回款冲销 | `确认冲销` | `PAYMENT_REVERSE` |
| 企业授信/调额 | `确认授信` | `ENTERPRISE_CREDIT` |
| 盘点审批（调整库存） | `确认调整` | `STOCK_CHECK_APPROVE` |
| 退款打款 | `确认退款` | `REFUND_APPROVE` |
| 管理员强制下线 | `确认下线` | `STAFF_LOGOUT_ALL` |

**令牌规则**

1. 换取接口：`POST /api/admin/confirm-token`，入参 `action` + `targetId` + `confirmText`。
2. 短语与 `action` 不匹配 → `40302`，不签发令牌。
3. Redis 存储 `confirm:{staffId}:{action}:{targetId}`，TTL 300 秒。
4. 业务请求带 `X-Confirm-Token`；校验通过后**立即删除**（用后即焚）。
5. **令牌与目标绑定**：订单 A 的令牌不能用于订单 B。
6. **缺失即拒绝**：未带令牌返回 `40302`，不允许降级为普通确认。

**审计**：所有二次确认操作写 `sys_operation_log`，记录操作人、目标、影响摘要、确认时间。

## 附：本文件未覆盖但同样强制的事项

以下条目来自 `plan.md`，与上述红线同级，不得违反：

- 金额一律以「分」（`BIGINT`）存储与计算，禁止浮点。
- 禁止 `SELECT *`；禁止循环内查库（N+1）。
- 事务内不得调用外部 HTTP，改为事务提交后发事件。
- 所有写接口必须标注 `@RequirePerm`，未标注的由 CI 拦截。
- 所有接口必须透传 `traceId`。
- 标注 `@Idempotent` 的接口必须校验 `Idempotency-Key`，缺失返回 `40002`，不允许降级。
