# web-admin 设计重构 · 施工记录（2026-11-21）

配套审计：`docs/design-audit-web-admin-2026-11-21.md`
方向：朴素 / 精确 / 器材感 —— 单色深绿、暖绿灰中性、单据式发丝线、等宽数字。

---

## 0. 收敛结果（同一套扫描口径）

| 指标 | 改前 | 改后 |
|---|---|---|
| 文字颜色 | 36 种 | 24 种 |
| 背景色 | 22 种 | 10 种 |
| 字号 | 15 种 | 11 种 |
| `font-weight: 700` | 7 处 | 0 处 |
| 装饰性渐变 | 1 处（登录页三段绿） | 0 处 |
| `transition: all` | 1 处 | 0 处 |
| `.section-title` 定义 | 11 份副本 | 1 份全局 |
| 自定义 CSS 变量 | 引用 5 个 / 定义 0 个 | 定义 1 套（23 个 `--sent-*` + Element 映射） |

剩下的 24 种文字色里，绝大部分是**语义色**（danger/warn/ok 的深浅）和 6 个尚未逐个清扫的页面（`stock/*`、`system/*`、`product/*` 的局部规则），不是新的漂移。

---

## 1. 令牌层 `src/style/theme.css`（重写，58 → 约 430 行）

- 中性 ramp 一条：`--sent-bg #f6f7f4` / `--sent-surface #fff` / `--sent-fill #eef0eb` / `--sent-line rgba(27,42,36,.10)` / `--sent-ink #1b2a24` / `--sent-ink-2 #4e5f57` / `--sent-ink-3 #6f7e76`。
- 品牌绿保留 `#12664f`，新增 `hover #0e5240`；橙色从 `#ff7a1a`（白底对比度只有 2.9:1，不能做文字）改成 `--sent-accent #a2500f`，只做文字/待办。
- 字号 5 档（12/13/14/20/28 + 40 展示）、字重 3 档（400/500/600）、圆角 3 档（6/10/12，tag 4，禁 pill）、间距 4px 栅格。
- 补齐 Element 变量映射：`--el-text-color-*`、`--el-border-color*`、`--el-fill-color*`、`--el-border-radius-*`、语义色 light-9。
- 组件覆盖：表格（去竖线/表头透明/行高 40/数字 tabular）、卡片（去投影）、分页（紧凑）、tag（去边框 4px）、输入框（hairline）、弹窗（圆角 12 + 唯一投影）、描述列表（只留横向发丝线）、树/步骤条/提示条/表单项。
- **修掉一个自己引入的回归**：表头底色改透明后，右侧固定列会透出下层文字（客户列表"创建时间"被"操作"压住），已给固定列补不透明表面 + 内侧发丝线。
- 全局类：`.sent-page-title`（20/600）、`.sent-panel`（白面 + 发丝线 + 12px + 20 padding）、`.section-title`（沿用原类名，E2E 有 `h3.section-title` 断言）、`.section-title--spaced`、`.sent-hint`、`.sent-num`。
- 空状态：`.el-empty__image { display: none }`，全站去掉 Element 默认 3D 插画（`order-search` 依赖 `.el-empty` 存在，元素本身保留）。

## 2. 布局层 `src/layout/index.vue`

- 侧栏 224 → 240；删掉「20×20 半透明白方块 + 一个汉字」的占位图标（`台/列/类/价/单…`），改纯文本菜单，标签对齐成一列。
- 行高 34 / 行间距 2 / 圆角 6；分组标题 12px、62% 白、上方 16px 下方 4px（比它自己的条目更远）。
- 当前路由所属分组**默认展开**（`:default-openeds` 由 `route.meta.group` 推导，`:key` 绑定组名，换组才重挂）。
- 选中态：白 12% 底 + 白字 500，不再用橙色文字（橙色退出导航）。
- 顶栏 56px：`森特体育 › 分组 › 当前页`，全部 12px 弱字 —— 顶栏不再是"标题"，页面 `<h2>` 成为一屏唯一主标题（`getByRole('heading')` 断言因此仍唯一命中）。
- 右侧：姓名 13px + 1px 竖分隔 + 「退出登录」弱色文字，hover 变 danger。

## 3. 组件层

| 组件 | 改动 |
|---|---|
| `SentTable.vue` | 去掉 `border` + `stripe`；操作列改左对齐；刷新/列显隐/导出 改 `text` 按钮（`.sent-table__tool`，弱色，hover 品牌绿）；`.sent-table-pager` / `.sent-table-filters` 类名保留（E2E 依赖 17 + 2 次） |
| `StatusTag.vue` | `el-tag` → 「6px 色点 + 12px 灰字」；空值不再渲染标签框，改一个弱色破折号（已获授权的一行 `v-if`） |
| `BatchDeleteButton.vue` | 常态从"红色描边按钮"降为弱色文字入口，hover 才变 danger；testid 与 @click 未动 |
| `utils/confirm.ts` | `ElMessageBox` 挂 `customClass: 'sent-confirm'`：去黄色感叹号图标、确认键改 danger 实心、圆角 12 |

## 4. 页面外壳

- **工作台**：6 张等宽 `el-card` → 1 块计分板（6 格，1px 发丝线分隔）；只有"今日销售额"用 28px，其余 20px；数字全部 tabular-nums、右列对齐；待办行高 40，「前往处理」默认 `opacity:0`，悬停才出现（原来重复 5 次）。
- **销售报表**：3 张卡 → 计分板；柱图补 3 条发丝网格 + 明确基线 + 峰值标注；**修掉日期标签被 `overflow:hidden` 裁掉的 bug**（一直没显示过）；峰值柱拿唯一强调色，其余退到中性阶；排行 bar 不再挤掉金额。
- **分类管理**：左侧竖条统计卡 → 发丝板；chip 从 14px 药丸 → 6px 圆角 + 发丝描边；`transition: all` → 具名过渡；`#f6faf8` 等浅底并入 `--sent-fill`。
- **违禁词库**：四类标签从"四色药丸（含薰衣草紫 `#f0ecff` / `#6d4bd1`）"→ 色点 + 灰字；`sw-stat` 去掉三色数字。
- **会员 / 客服指标 / 退款详情**：统计块统一成同一块计分板（CSS-only，不动模板）。
- **登录页**：三段绿渐变 → 单色 `#0f4738` + 4% 白场边线纹理；卡片 380px、左对齐标题、发丝描边。
- **403 / 404**：76px/700 大数字 → 40px/500 弱色，居中整块 → 左对齐单列。

## 5. 列宽与表格密度（超出"纯 class"但属展示配置，已改）

客户列表与退款列表的列宽总和超过容器，Element 会按比例压缩列，导致表头互相压字、时间列只剩"2026-11…"。已按 1110px 重新配平（客户 1016、退款 1096）；回款登记页两张内嵌表也重配（左 412 / 右 578，原来溢出到面板外，"本次核销"输入框和"状态"列被切）。

另外两个由 E2E 与截图暴露的表格问题：

- **分页器溢出到隔壁列**：`.el-pagination` 在窄面板里宽 404px、容器只有 364px，`下一页` 按钮有 11px 落在右列盒子下面 —— `finance-contract-refund` 用例点不动它（这是本轮唯一的红）。修法是全局 `flex-wrap: wrap; max-width: 100%`，与 `.sent-table-pager` 里既有的同类修复同口径。
- **单号被劈成两行**：Element 的 `.cell` 默认 `word-break: break-all`，会把 `SO20261121924482` 从中间断开，行高 40 装不下。改成单行 + 省略号，行高恒定。

---

## 6. 验证

- `npx vue-tsc -b` 通过；`npm run build` 通过（仅 Element 全量引入的 chunk 体积告警，改造前就有）。
- **E2E 全量 135 passed（12.4 分钟）**，三端 5173/5174/5175 + 8088 在线。
- 视觉：`e2e/_audit-shots/`（改前）↔ `e2e/_audit-shots-after/`（改后），同视口 1440×900，含登录/工作台/订单/分类/退款/销售/客户/回款/会员 9 屏。

## 7. 审计中发现的真实缺陷（超出本次红线，未改，需另开工单）

1. **订单列表接口只返回 4 个字段**：`GET /api/admin/orders` 实测返回 `orderNo / status / payableCent / payableYuan`，没有 `payStatus / goodsAmountCent / createdAt`。所以管理端订单列表的「支付状态」「货款」「下单时间」三列**永远是 `-` / `0.00` / 空**（库里 `pay_status` 是有值的）。
2. **订单金额格式不统一**（`¥24.36` 与 `0.00` 并存）：根因同上，`goodsAmountCent` 缺失时走了 `fmtCent(undefined)`。属于 `@sent/shared` 的 formatter 口径，需单独工单。
3. **其余页面列宽未做全量体检**：退款/客户/回款已修，发票、合同、定制审核等宽表仍可能压缩列。

## 8. 明确没做

- 多页签（按你的决定，坚决不加）。
- 侧栏图标集（A 方案：纯文本，不改路由 `icon` 字段）。
- 其余 20 余个页面里零散的硬编码色（约 24 种文字色里的非语义部分）：下一批可以按「令牌替换 + 删局部副本」继续扫，改动面大但零逻辑风险。
