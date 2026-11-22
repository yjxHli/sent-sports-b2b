# web-admin 全局设计审计（去 AI 化）

> 范围：`src/layout/`、`src/components/`、`src/style/`，以及被它们统辖的 35 个页面外壳。
> 方法：读代码取精确值 + 起服务在 1440×900 真实渲染 7 屏截图（登录 / 工作台 / 订单列表 / 分类 / 退款 / 销售报表 / 客户列表）。
> 结论只针对"看起来像 AI 随手生成"，不动业务、不动交互、不动 testid。

---

## 0. 一句话结论

这不是"紫色渐变"那种一眼假的 slop，而是更麻烦的一类：**元件全用 Element Plus 默认脸 + 令牌系统是假的 + 同一个页面名在一屏里说三遍**。代码里没有任何一处渐变滥用、没有 emoji、没有 pulse 动画，所以它不会被第一眼抓出来 —— 但它的每一处都在说"没人做过设计决定"。

判定：**中度 slop**。需要动令牌层和外壳层，不是打补丁能解决的。

---

## 1. 硬数据（40 个文件扫描结果）

| 指标 | 现状 | 应有 | 判定 |
|---|---|---|---|
| 文字颜色 | **36 种** | 3 种（主/次/弱） | 5 倍超标 |
| 背景色 | **22 种**（其中 12 种是近似白） | 3 种（页/面/沉） | 4 倍超标 |
| 字号 | **15 种**（10~76px，12px 用了 35 次） | 5 档 | 3 倍超标 |
| 字重 | 700 用了 7 次、600 用了 19 次 | 500/600 两档 | 重字当层级用 |
| 自定义 CSS 变量 | 引用 5 个名字，**定义了 0 个** | 全定义 | 令牌系统是虚构的 |
| `.section-title` | 在 11 个页面各抄一份，3 种 margin | 1 个全局类 | 已开始漂移 |
| 筛选控件宽度 | 13 种内联 `style="width:..."`，220px 用了 15 次 | 1 个栅格类 | 随手值 |
| 渐变 | 1 处（登录页 135° 三段绿） | 0 | 唯一一处也是最像 AI 的一屏 |
| 阴影 | 登录卡 `0 18px 48px`、el-card hover、1 处 0 1px | 只给浮层 | 卡片在"浮" |

---

## 2. 审计清单

### 2.1 布局 / 外壳（最高优先，一屏就被看到）

| # | 位置 | 现象 | 改法 |
|---|---|---|---|
| L1 | `layout/index.vue:86-96` | 侧栏图标是 **20×20 半透明白方块里塞一个中文字**（`台/列/类/价/单/发…`）。这是本项目最"AI 拼贴"的一处：它不是图标，是拿汉字当图标占位。且 `审` 同时出现在"定制审核"和"企业审核"，`价`在"一客一价"。 | 删掉方块与单字，改纯文本菜单（或统一图标集，见待拍板 1） |
| L2 | `layout/index.vue:5-17` | 7 个分组全部默认折叠，进任何二级页要 **两次点击**；当前页在折叠组里时侧栏看不到选中态（截图：订单列表页展开后才知道自己在哪）。 | 保留分组，但把当前路由所属分组默认展开（`default-openeds` 由 route 推导） |
| L3 | `layout/index.vue:84` | 选中态靠 **橙色文字** `#ffb77a`，橙色是品牌强调色，被降级成了导航高亮。 | 选中态 = 10% 白底 + 600 白字 + 2px 左侧亮条；橙色退出导航 |
| L4 | `layout/index.vue:22,104` | 顶栏 `sent-crumb` 是 **15px/600 的当前页名**，与内容区 `<h2>` 同权重、垂直相距 30px —— 一屏里同一个名字出现两遍，读者不知道该看哪个。 | 顶栏改真面包屑：`分组（12px 弱）› 当前页（12px 次）`，不再是标题；`<h2>` 独占页面标题 |
| L5 | `layout/index.vue:3` | 侧栏 224px、`el-menu` 行高随 Element 默认（约 50px），与"系统类后台该有的密度"不符。 | 240px / 行高 34 / 行间距 2 / 一级项左内距 12 |
| L6 | 全站 | **没有多页签**，也没有分组级面包屑。32 个页面固定集合，靠反复展开侧栏导航。 | 明确不加多页签（见待拍板 2），面包屑补上分组层 |
| L7 | `layout/index.vue:101` | 顶栏白底 + 内容区 `#f4f7f5`，但 `el-card` 边框是 Element 冷灰 `#e4e7ed`，暖绿背景里嵌冷灰线。 | 统一发丝线 `rgb(27 42 36 / 10%)` |

### 2.2 样式变量层（`style/theme.css`，58 行）

| # | 位置 | 现象 | 改法 |
|---|---|---|---|
| T1 | 全站 5 处 | 引用 `--sent-text` / `--sent-text-2` / `--sent-text-muted` / `--sent-muted` / `--sent-primary-soft`，**theme.css 里一个都没定义**，全部走 fallback `#303133 / #606266 / #909399 / #8a94a6`（Element 冷灰）。5 套命名 = 5 个人各写了一遍。 | 定义 3 个真变量 `--sent-ink / --sent-ink-2 / --sent-ink-3`，删 5 个假名 |
| T2 | `theme.css:37-58` | 只有 5 个全局类（title/panel/form-row/hint），`.sent-panel` 是 `padding:18px`（非 4 倍数）+ 8px 圆角 + **无边框无阴影**，白块直接压在浅底上，边界靠颜色差。 | 面板 = 白面 + 发丝线 + 12px 圆角 + `padding:20px` |
| T3 | `theme.css:18` | 只有 `--el-border-radius-base:6px`，`small/large/round` 全走 Element 默认（4/8/999），所以按钮 6px、输入框 6px、tag 直接变药丸。 | 补齐 6/6/8/4 四档，tag 不再 pill |
| T4 | `theme.css` | 没有任何 `el-table / el-card / el-pagination / el-message-box` 覆盖 → 表格、卡片、分页、弹窗**全是最原始的 Element 默认脸**。 | 逐组件覆盖（见 2.3） |

### 2.3 公共组件

| # | 位置 | 现象 | 改法 |
|---|---|---|---|
| C1 | `components/SentTable.vue:31-38` | `border` + `stripe` 同时开：**每个单元格都有竖线 + 隔行变色 + 表头浅灰底**，读起来像 Excel 截图而不是数据表。 | 关 border/stripe，改发丝横线 + 表头透明 + 行 hover `#F6F7F4` + 行高 40 |
| C2 | 同上 | 金额列 `align:right` 已设，但**没有 tabular-nums**，`¥24.36` 与 `0.00` 两种格式并存（`order/list.vue:98-101`，一个 `fmtMoney` 一个 `fmtCent`）。 | 数字列统一 `font-variant-numeric: tabular-nums`；格式统一留作独立工单（属 shared formatter，不在本次红线内） |
| C3 | `SentTable.vue:9-27` | 工具栏右侧固定 4 个白底 small 按钮（刷新 / 列显隐 / 导出 CSV），与左侧筛选同视觉重量，一屏 6~7 个按钮没有一个主次。 | 三个工具按钮降为 ghost 图标级（`.sent-table-tools`），主按钮只留"查询" |
| C4 | `SentTable.vue:67-71` | 空状态是 `el-empty` 默认灰色 3D 小人插画（分类页右栏占据 700×300 一大片空白）。 | 隐藏默认插画，改一句话 + 12px 弱字（走已有 `#empty` 插槽 + CSS） |
| C5 | `components/StatusTag.vue:2` | `el-tag effect="light"` 全站通吃：状态是**淡底 + 深字 + 细边框的小色块**，一行 18 个，视觉噪音大于信息。空状态时 `statusLabel` 返回 `-`，于是渲染出一个**空 tag 灰框**（订单列表"支付状态"列整排可见）。 | tag 去边框、圆角 4、字号 12、`padding:0 6px`；改成"6px 色点 + 文本"更静（见待拍板 3）。空值应不渲染 tag —— 需一行 `v-if`，**超出本次红线**，请你决定是否放行 |
| C6 | `components/BatchDeleteButton.vue` | 常态就是一个**红色实心边框的「删除」按钮**，在筛选行最左侧（客户列表、订单列表可见），比"查询"还抢眼。危险操作占据了全页最高频、最靠前的位置。 | 改 ghost 红、字号 12、靠右（`order` 或 `margin-left:auto`），保留 testid 与 @click 不动 |
| C7 | `utils/confirm.ts:92-99` | 危险确认走 `ElMessageBox` 默认脸：黄色感叹号圆标 + 16px 标题 + 4px 圆角 + 蓝色主按钮。**"危险"用黄色感叹号、确认键是品牌蓝绿**，语义与颜色打架。 | 挂 `customClass: 'sent-confirm'`：去 type 图标、圆角 12、发丝线、确认键改 danger 实心、取消键 ghost |
| C8 | `dashboard/index.vue:6` | `el-card shadow="hover"` 6 张：鼠标划过整卡浮起 —— 卡片是容器不是可点项，hover 阴影在撒谎。 | 去 shadow，改 hover 边框加深（或不动，纯静态） |

### 2.4 排版与信息层级

| # | 位置 | 现象 | 改法 |
|---|---|---|---|
| P1 | `dashboard/index.vue:4-12` | **6 张完全等宽同构的卡片**（2×3），每张：13px 灰标签 + 28px/700 绿数字 + 12px 更浅灰提示。六个数字一样重，"今日销售额 ¥11,610.92" 和"待接入会话 2"同等重要。 | 拆成 1 主（跨 2 列，32px/500）+ 5 次（20px），数字右对齐 tabular-nums |
| P2 | 全站 | `.metric-value` 一律 `color:#12664f` **粗体绿**。品牌绿同时是"主色""数字色""链接色""选中色"，一个颜色承担 4 个语义，于是哪个都不突出。 | 数字用 `--sent-ink`（近黑），绿色只留给可点与选中；橙色只给"要你动手的那件事" |
| P3 | `report/sales.vue:187-222` | 手写柱图：30 根柱、无 Y 轴、无网格线、无基线标注；26 根柱因量级差被压成 <8px 的残桩；**日期标签 `position:absolute; bottom:-20px` 被面板裁掉，根本看不见**。 | 加 3 条发丝网格 + 最大值标注 + 柱宽对齐 4px + 修 `bottom` 裁剪；给最高那一天一个强调色（橙色，唯一解） |
| P4 | `report/sales.vue:223-224` | 排行表内嵌 bar + 金额，第一行"未分配"bar 撑满导致金额被右边缘裁掉（截图可见 `¥` 后截断）。 | bar 容器 `min-width:0` + 金额固定右列 |
| P5 | `product/category.vue:299-317` | 三张统计卡用 `border-left: 3px` 做色条 —— **直角竖条压在 8px 圆角卡上**，左上/左下露尖角；"100" 是橙色（无含义）。 | 去色条，改"标签在上、数字在下"，橙色还给有含义的数字 |
| P6 | `finance/refund.vue`（截图） | 9 列溢出，固定右列盖住"申请时间"（只剩"申"），操作列 3 个链接挤在 160px 里换行成"问"。 | 收窄固定列、次要列 `minWidth` 下调、操作列 168px 且左对齐；列优先级清单另附 |
| P7 | `customer/list.vue:38` | 界面里出现实现注释：**"数据范围（ALL/TEAM/SELF）由后端 SQL 改写决定，前端不做二次过滤"**。这是给开发者看的，不是给运营看的。 | 删掉（属文案，非 class —— 需你放行） |

### 2.5 颜色 / 组件色彩

| # | 位置 | 现象 | 改法 |
|---|---|---|---|
| K1 | `system/sensitive-words.vue:334-337` | 违禁词分类第四个标是 **薰衣草紫** `#f0ecff` / `#6d4bd1` —— 绿牌产品里唯一的紫色，且是 pill。 | 四类改"色点 + 文本"：红/琥珀/灰/深绿，无底色无药丸 |
| K2 | 同上 `:323` | 同类标还用了冷蓝灰 `#eef2f7 / #4b5b6f`。 | 并入中性 ramp |
| K3 | `views/chat/metrics.vue:335,337,351` | 弱字用 `#8a94a6`（冷蓝灰），同页别处用 `#6b7a74`（暖绿灰）→ 一屏两种灰底子。 | 统一 `--sent-ink-3` |
| K4 | `login/index.vue:127` | `linear-gradient(135deg,#0f4738,#12664f,#1c8a68)` 三段绿 + `0 18px 48px` 黑影 + 12px 圆角 → 这是全站唯一一处渐变，也是最"AI 落地页"的一屏。 | 单色 `#0F4738` 底 + 场地边线纹理（纯 CSS 重复线性渐变细线，非彩色渐变），卡片改发丝线 + 轻阴影 |
| K5 | `error/404.vue:19-21` | `76px/700` 绿色大数字 + 居中三行。 | 40px/500，左对齐，单按钮 |
| K6 | `product/category.vue:385` | `transition: all 0.15s ease`（会连带动画 width/padding）。 | `transition: background-color, border-color, color` |

---

## 3. 设计方向（5 行）

- **Product**：森特体育给企业客户做体育用品批量采购 —— 报价、定制、发货、应收，一件事一张单据。
- **Character**：朴素、精确、有器材感。不用 clean/modern/minimal 这类词，它们等于"没决定"。
- **World**：器材仓库和场地边线 —— 纸箱标签、计分板、白线。中性色偏暖绿灰（纸 + 器材箱），**不是冷蓝灰**（这正是现在 36 种灰里混进来的杂色）。
- **Moments**：只给两处颜色 —— 工作台"今日销售额"那一个数字，和销售趋势图里最高的那一天。其余全是静默的墨色与发丝线。
- **Signature**：**单据感 = 发丝线分区 + 等宽数字**。所有数字 `tabular-nums`，所有分区靠 1px `rgb(27 42 36 / 10%)` 而不是白卡片堆叠 + 阴影。只此一个细节，全站一致。

品牌绿 `#12664F` 保留（真实品牌色，不是 slop），但**从"到处都在用"退回到"只在可点、选中、主按钮上出现"**。橙色 `#FF7A1A` 从"导航高亮 + 统计数字"退回到"待办 / 逾期 / 唯一需要你动手的那件事"。

---

## 4. 令牌（批准后写进 `style/theme.css`）

```
中性 ramp（暖绿灰）
  --sent-bg        #F6F7F4   页面底
  --sent-surface   #FFFFFF   面
  --sent-fill      #EEF0EB   沉/表头/hover
  --sent-line      rgb(27 42 36 / 10%)    发丝
  --sent-line-2    rgb(27 42 36 / 18%)    强线
  --sent-ink       #1B2A24   主（正文、数字）
  --sent-ink-2     #4E5F57   次（标签、表头）
  --sent-ink-3     #6F7E76   弱（注释、meta）

品牌与语义
  --sent-primary   #12664F   hover #0E5240   soft #E7F0EC
  --sent-accent    #A2500F   （橙只做文字/待办，不做大色块）
  --sent-danger    #B4322A   soft #FBEEEC
  --sent-warn      #9A6410   soft #FBF3E4
  --sent-ok        #186B4E   soft #E8F2EE

字号（5 档 + 1 展示）
  12 meta / 13 label / 14 body / 20 title / 28 figure / 40 display
字重  400 / 500 / 600（删掉全部 700）

间距  4 8 12 16 20 24 32 40    页边 24 / 面板 20 / 卡片间 16
圆角  control 6 / card 10 / panel 12 / tag 4（禁 pill）
阴影  仅浮层：0 8px 24px rgb(27 42 36 / 12%)
动效  transition: color, background-color, border-color, opacity  .15s ease-out（唯一曲线）
```

---

## 5. 施工顺序（4 批，每批独立可验证）

### 批次 1 · 令牌层（`style/theme.css`）
1. 写入上表全部变量；删 5 个假变量名。
2. Element 变量覆盖：radius 四档、font-family、`--el-text-color-*`、`--el-border-color*`、`--el-fill-color*`。
3. 全局类：`.sent-page-title`（20/500）、`.sent-panel`（发丝 + 12px + 20px）、`.sent-section-title`（14/500）、`.sent-meta`（12 弱/等宽）、`.sent-figure`（tabular）、`.sent-toolbar`。
4. 组件覆盖：`el-table`（去竖线/表头透明/行高 40/hover）、`el-card`（去阴影）、`el-pagination`（紧凑）、`el-tag`（去边框）、`el-button`（去 700 字重）、`el-message-box`（圆角 12）。

### 批次 2 · 布局层（`layout/index.vue`）
侧栏 240 / 行高 34 / 分组默认展开当前组 / 选中态换白底 + 左亮条 / 删中文方块图标；顶栏 56px 改真面包屑 + 用户区分隔发丝。**`<h2>` 保持唯一 heading（E2E 依赖 `getByRole('heading')`）。**

### 批次 3 · 组件层（`SentTable` / `StatusTag` / `BatchDeleteButton` / 确认弹窗 / 空状态）
表格去 border+stripe、数字列 tabular、工具按钮 ghost 化、空状态去插画、确认弹窗 danger 化。

### 批次 4 · 页面外壳收口
11 处重复 `.section-title` 归并全局类；统计卡改 1 主 + N 次（只换外层 `div` 包裹，不动 `v-for` 数据）；登录页/404 收口；`transition: all` 收口。

**每批验证**：`cd web-admin && npx vue-tsc -b && vite build` → 我用已有脚本重拍 7 屏对比 → `cd e2e && npx playwright test`（三端 5173/5174/5175 需在跑，当前 5174 + 8088 已在运行）。

---

## 6. 红线对照（我会怎么守住）

| 你的红线 | 我的做法 |
|---|---|
| 只改 class / `<style>` / 无关业务的外层包裹 div | 所有改动限定在这三类；统计卡的 `el-row/el-col` → `div.metric-grid` 属"外层包裹 div" |
| 禁改 `data-testid` | 一个都不动，删除按钮等仍靠 testid 定位 |
| 禁改 `v-model` / `@click` | 一个都不动 |
| 禁改业务变量 | 不动 `props / emit / computed / api 调用 / 状态判断` |
| 额外自加 | **`.sent-panel`（4 次）、`.sent-table-pager`（17 次）、`.sent-table-filters`（2 次）、`.sent-page-title`（1 次）四个类名被 E2E 依赖，只增不减不改名**；`.el-table__row` / `.el-message-box` / `.el-pagination` 的 DOM 结构不做替换，只做样式覆盖 |

---

## 7. 需要你拍板（4 项）

1. **侧栏图标**：A 纯文本菜单（推荐，最静、零新增依赖）／B 用已装的 `@element-plus/icons-vue` 统一图标（需改 `router/modules/business.ts` 的 `icon` 字段，**超出本次红线**）。
2. **多页签**：A 不加（推荐：32 页固定集合 + 菜单分组已够；多页签恰是 vue-element-admin 模板的标志器官，加了更像模板）／B 加（属新特性，需单独走 TDD 与状态设计）。
3. **状态标签**：A 色点 + 文本（最静）／B 去边框的浅底 tag（保守）。
4. **登录页渐变**：A 去掉渐变改单色 + 边线纹理（推荐）／B 保留品牌渐变。

另有两处**超出纯 class 红线**、需要你单独放行的小改：
- `StatusTag` 空值不渲染 tag（一行 `v-if`）；
- 客户列表删掉"由后端 SQL 改写决定"那句实现注释（文案）。

---

*审查方法：unslop by Damir Sokolovsky (@heydamir), CC BY 4.0。*
