# 商品详情页淘宝化改造 · 实施方案

> 版本：2026-11-21（rev2，新增秒杀价机制） · 范围：用户端商品详情页重构 + 管理端商品编辑能力补齐 + **秒杀活动价** + 全量 E2E
> 依据：天猫商品详情页（detail.tmall.com，ASZUNE 22W 充电器）版式与信息架构
> 需求补充（2026-11-21 23:54）：秒杀价必须由管理端发布活动才出现，平时不存在；因此秒杀机制本身也属本次范围。

---

## 0. 一句话结论

三件事：

1. **把沉睡字段接起来**——`product.images_json / detail_html / params_json`、`sku.spec_json` 早在 V4 就有了，但管理端从未维护、用户端从未渲染（详情页那 4 张缩略图是同一个 `mainImage` 换 seed 生成的渐变占位）。补 4 个缺失列即可打通。
2. **用户端按淘宝三列版式重排**，类名只增不改以保 145 条 E2E。
3. **秒杀价做成真实价格来源**——新建活动表 + `FLASH_SALE` 策略，接进 `PricingService` 引擎。因为 `CartQuoteUseCase` 与 `CreateOrderUseCase` 都走 `PricingServiceFacade` → `ProductPriceRepository.loadContext()`，秒杀价在**购物车预览与下单金额上同时生效**，不是只改前端展示的假功能。

评价/问大家本次不做（项目无评价子系统），用真实空态，禁止假数据填充。

---

## 1. 现状盘点（代码事实，非推测）

### 1.1 数据模型

`V4__product.sql` 已有：

| 表 | 字段 | 现状 |
|---|---|---|
| `product` | `main_image` | 有值，但种子数据是 `demo.sent.test` 假域名 → 前端 `ProductThumb` 降级为渐变占位 |
| `product` | `images_json` | 列存在，**无人写入** |
| `product` | `detail_html` | 被 `ProductForm.description` 当纯文本存，用户端未渲染 |
| `product` | `params_json` | 列存在，**无人写入** |
| `product` | `brand` / `can_customize` | `can_customize` 硬编码写 0，`brand` 从不写 |
| `sku` | `spec_json` | 有值，`formatSpec()` 展平成「黑 / M」 |
| `sku` | `barcode` / `weight_g` / `floor_price_cent` | 从不写（`floor_price_cent` 因此多为 0） |

### 1.2 管理端

`web-admin/src/views/product/edit.vue`（322 行）只有：名称 / 分类 / 描述（单行 textarea）/ SKU 表格（编码、名称、售价、最低折后价、库存）/ 批量折扣。
**无图片、无参数、无详情图、无品牌、无服务承诺、无任何促销活动。**

### 1.3 用户端

`web-user/src/views/product/detail.vue`（776 行）：两列 grid（2fr 图 : 3fr 购买区），无缩略图列；缩略图 `v-for i in 4` 全用同一个 `mainImage`；「商品详情」区块是**写死的两段话**；无参数、无服务承诺、无 tab 锚点、无推荐。

### 1.4 定价链路（秒杀要接进来的地方）

```
cart.CartQuoteUseCase ─┐
order.CreateOrderUseCase ─┴→ PricingServiceFacade.price(PriceRequest)
                                    ↓
                          ProductPriceRepositoryImpl.loadContext(skuId)
                                    ↓ 装配零售/底价/成本/阶梯
                          PriceContext → PricingService.calc(ctx)
                                    ↓ 按 order 升序，命中即返回（不取低）
                          PriceResult(unitPriceCent, PriceSource, ruleId, belowCost)
                                    ↓ finalize：低于底价抛 BELOW_FLOOR_PRICE(40920)
                          LinePrice → 写 order_item.price_source / price_rule_id
```

现有策略优先级：`CUSTOMER_SKU(1) → AGREEMENT(2) → LADDER(3) → MEMBER(4) → RETAIL(5)`，另有 `STACKED` 折上折特判。`order_item.price_source` 是 `VARCHAR(32)`，加枚举值无需迁移改列。

**关键结论：`loadContext()` 是唯一装配点，在这里注入秒杀价，预览与下单会自动一致。**

### 1.5 E2E 强耦合点（改造红线）

| 依赖 | 使用方 |
|---|---|
| `.spec-item` + 规格文案 | `pick-product.ts`，13 个下单类 spec |
| `.buy__meta` 内「可售库存」、`[data-testid="available-stock"]` | `product-stock-qty.spec.ts` |
| `.buy__qty input`、文案「仅剩 N 件」 | 同上 |
| 按钮文案「加入购物车」「立即购买」、`.buy-fav` | 结算链路 / `favorites-footprints` |
| 详情页价格在 10~40 元 | `price-repricing.spec.ts`（**秒杀演示数据会打破它，见 §9.3**） |

`pick-product.ts` 铁律：E2E 不写死商品/SKU，现场通过 `/products` 挑有货规格。

---

## 2. 范围与取舍

### P0（本次必做）

**A. 商品富内容**
1. 管理端新增/编辑商品支持：主图 + 多图集（≤8，可设主图、可删、上移下移）、详情长图（≤20，可排序）、商品参数（动态键值对）、服务承诺标签、副标题、品牌、款式图。
2. 后端 4 个新列（V54）、DTO 扩展（保留旧构造器）、读写联动、JSON 容错、清空语义正确。
3. 用户端详情页三列版式 + 缩略图切换 + tab 锚点 + 参数区 + 详情区 + 真实空态。

**B. 秒杀活动价**
4. 新建 `flash_sale_activity` + `flash_sale_item` 两张表，管理端活动管理页（列表、新建、编辑、发布、下线、删除）。
5. 新增 `PriceSource.FLASH_SALE` + `FlashSalePriceStrategy`，接入 `PriceContext` 与 `loadContext()`；秒杀价在**结算预览与下单**中生效。
6. 用户端详情页：有生效活动 → 秒杀横幅（红底 + 秒杀价 + 划线原价 + 倒计时）；无活动 → **整块不渲染**（用户明确要求"平时是没有的"）。
7. 结算页价格明细标注取价来源；命中协议价/一客一价时提示"已按企业协议价结算"，避免与详情页秒杀价冲突。

### P1（可选，有余量再做）

- 「同分类推荐 / 看了又看」横滑卡片（复用 `/products?categoryId=`）；
- SKU 选中后主图联动切换款式图；
- 商品列表 / 首页卡片「秒杀」角标；
- 详情页按当前客户身份取价（`CatalogController` 注入 `CurrentUser` → 返回 `effectivePrice/source`），彻底消除展示与结算的价差。

### P2（明确不做，写清理由）

| 不做 | 理由 |
|---|---|
| **商品评价 / 晒单 / 标签云 / 问大家** | 需 `product_review` 表 + 订单完成触发入口 + 图片晒单 + 管理端审核 + 敏感词过审，是独立中型模块。详情页显示真实空态「暂无评价」，禁止假数据。 |
| **秒杀活动限量配额（quota + 已抢进度条）** | 需要原子扣减（`UPDATE ... WHERE sold + n <= quota`）、超卖回滚、与 `sku_stock` 双约束叠加，是并发新坑。v1 秒杀**只控价 + 控时间窗**，库存仍受 SKU 可售库存约束。 |
| **秒杀定时任务自动开关活动** | 生效判断直接用时间窗 SQL（`now BETWEEN start_at AND end_at`），不需要 job 改状态；管理端「已结束」是计算值。少一个定时任务就少一类线上事故。 |
| **颜色 × 尺码多维规格矩阵** | 现有 `sku` 一行一 SKU、`spec_json` 扁平键值，已满足单层款式（单头 / 套装+1米）。矩阵需规格维表 + 笛卡尔积生成器。 |
| **顶部导航条 / 店铺头** | 用户明确不实现；用户端 `layout` 已有 topbar 与搜索栏，不动。 |
| **底部吸底双 CTA** | 按钮已在购买栏内且被 E2E 定位，加吸底条收益低、回归风险高。 |

---

## 3. 数据模型（V54，只新增）

> 铁律：Flyway 只能新增迁移，改完同步 `MapperIT` 版本断言。当前最新 V53 → 新建 V54。

```sql
-- V54 商品详情富内容 + 秒杀活动价

ALTER TABLE product
    ADD COLUMN subtitle            VARCHAR(255) NULL COMMENT '卖点副标题' AFTER title,
    ADD COLUMN detail_images_json  JSON         NULL COMMENT '详情长图 fileId 数组' AFTER images_json,
    ADD COLUMN service_tags_json   JSON         NULL COMMENT '服务承诺标签数组' AFTER params_json;

ALTER TABLE sku
    ADD COLUMN image_file_id VARCHAR(512) NULL COMMENT '款式图 fileId' AFTER spec_json;

CREATE TABLE flash_sale_activity (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    activity_no VARCHAR(32)  NOT NULL,
    title       VARCHAR(128) NOT NULL,
    start_at    DATETIME(3)  NOT NULL,
    end_at      DATETIME(3)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/OFFLINE',
    remark      VARCHAR(255) NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_no (activity_no),
    KEY idx_flash_status_time (status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动';

CREATE TABLE flash_sale_item (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    activity_id      BIGINT UNSIGNED NOT NULL,
    sku_id           BIGINT UNSIGNED NOT NULL,
    flash_price_cent BIGINT       NOT NULL,
    sort             INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by       BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_sku (activity_id, sku_id),
    KEY idx_flash_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动商品';
```

### JSON 约定（统一）

```jsonc
// product.images_json —— 图集，第一个即主图
["2026/11/ab/a1b2.png", "2026/11/ab/c3d4.png"]
// product.detail_images_json —— 详情长图，按数组顺序渲染
["2026/11/ab/d1.png", "2026/11/ab/d2.png"]
// product.params_json —— 参数键值对，前 4 条在用户端放大成宫格
[{"label": "充电功率", "value": "22W"}, {"label": "接口", "value": "USB-C"}]
// product.service_tags_json
["15天价保", "7天无理由", "假一赔四", "极速退款"]
```

**存储口径**：新上传一律存 **fileId（相对路径）**，渲染过 `fileApi.url(fileId)`；`http(s)://` 开头原样使用；`demo.sent.test` 视为无图。

### 活动状态机

```
DRAFT ──publish──> ACTIVE ──下线──> OFFLINE
  ↑                   │                │
  └──────编辑──────────┘                └──publish──> ACTIVE
```

**生效判定 = `status='ACTIVE' AND #{now} BETWEEN start_at AND end_at`**，两个条件同时满足才算数：
- 草稿即使到了时间也不生效（运营可先建后发）；
- 到 `end_at` 后自然失效，不需要 job 改状态；
- 运营手动下线立刻失效。

`now` 由 `Clock` 注入传入查询参数（不用 MySQL `NOW()`），保证单测可控制时间。

---

## 4. 后端 · 商品富内容

### 4.1 DTO 扩展（必须保留旧构造器）

`ProductForm`、`SkuForm`、`CatalogDtos.ProductVO` 都是 record，**直接加字段会让 12 处 `new ProductForm(...)` 编译失败**（`ProductAdminServiceImplTest`、`ProductAdminCrudIT`）。新字段追加在末尾 + 保留旧签名构造器转调。

```java
public record ProductForm(
        @NotBlank String name, @NotNull Long categoryId,
        String description, List<@Valid SkuForm> skus,
        String subtitle, String brand, Boolean canCustomize, String mainImage,
        List<String> images, List<String> detailImages,
        List<ParamItem> params, List<String> serviceTags) {

    /** 旧签名：单测与 IT 在用 */
    public ProductForm(String name, Long categoryId, String description, List<SkuForm> skus) {
        this(name, categoryId, description, skus, null, null, null, null, null, null, null, null);
    }
}

public record ParamItem(@NotBlank String label, String value) { }
```

`SkuForm` 追加 `String imageFileId`，保留 5 参 / 6 参旧构造器。
`CatalogDtos.ProductVO` 追加 `subtitle / brand / images / detailImages / params / serviceTags / flashSale`，旧构造器转 null。

### 4.2 校验规则（铁律四）

| 字段 | 规则 |
|---|---|
| `images` | ≤ 8；元素非空、长度 ≤ 512；去重 |
| `detailImages` | ≤ 20 |
| `params` | ≤ 20；`label` 非空且 ≤ 32 字符；`value` ≤ 128 |
| `serviceTags` | ≤ 6；单条 ≤ 16 |
| `subtitle` / `mainImage` | ≤ 255 / ≤ 512 |

越界 → `BizException(ErrorCode.PARAM_INVALID, "原因")`，由 `GlobalExceptionHandler` 统一出 `R<T>`。

### 4.3 写入（关键坑：`updateById` 跳过 null）

- **清空图片/参数**必须写 `"[]"` 而非 `null`，否则删除静默失效；
- `toJson()` 对 `null` 和空列表统一返回 `"[]"`（表示清空）。

### 4.4 读取（容错，不得 500）

```java
private static <T> List<T> parseList(ObjectMapper om, String json, TypeReference<List<T>> type) {
    if (json == null || json.isBlank()) return List.of();
    try { return om.readValue(json, type); }
    catch (Exception e) { return List.of(); }   // 脏 JSON 降级空，详情页仍可打开
}
```

`CatalogMapper` 三条 SPU 查询补列：`p.subtitle, p.brand, p.images_json AS imagesJson, p.detail_images_json AS detailImagesJson, p.params_json AS paramsJson, p.service_tags_json AS serviceTagsJson`；SKU 查询补 `s.image_file_id AS imageFileId`。联表列必须带表前缀（`id` 撞名会 50001）。

### 4.5 服务层

`ProductAdminServiceImpl.create/update` 补 8 个 set；`replaceSkus` 补 `imageFileId`。
`ProductQueryServiceImpl.toProductVo` 组装 6 个新字段；`main_image` 为空且 `images` 非空时取 `images[0]`。

---

## 5. 后端 · 秒杀价定价链路（本次核心）

### 5.1 领域层（纯 POJO，ArchUnit 允许）

```java
// PriceSource 新增
FLASH_SALE,   // 秒杀活动价（公开活动，不覆盖合同价）

// PriceContext 新增两个字段 + builder 方法
private Long flashSalePriceCent;   // 当前生效秒杀价（分），可空
private String flashSaleRuleId;    // 如 "FLASH#12"
```

```java
/** 秒杀价：公开限时活动价。order=0 最先尝试，但企业协议/一客一价优先（合同价优先）。 */
public class FlashSalePriceStrategy implements PriceStrategy {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public boolean match(PriceContext ctx) {
        return ctx.getFlashSalePriceCent() != null
                && ctx.getCustomerSkuPriceCent() == null   // 一客一价终结，不覆盖
                && !ctx.agreementValid();                  // 企业协议有效时不覆盖
    }

    @Override
    public PriceResult price(PriceContext ctx) {
        return new PriceResult(ctx.getFlashSalePriceCent(),
                PriceSource.FLASH_SALE, ctx.getFlashSaleRuleId(), false);
    }
}
```

**为什么 order=0 而不动其他策略**：不需要改 `LadderPriceStrategy` 等既有 order 值（避免破坏现有单测），只需新增一个更小 order 的策略，且 `match()` 里显式排除合同价，语义一眼可见：**合同价 > 秒杀价 > 阶梯 > 会员 > 零售**。

`PricingService` 构造函数里把 `new FlashSalePriceStrategy()` 加到策略列表首位（列表本就会按 order 排序，加在哪都行，放首位可读性最好）。

### 5.2 仓储层（唯一装配点）

`ProductPriceRepositoryImpl.loadContext()` 追加一次查询，注入 `PriceContext`：

```java
// ProductMapper 新增（now 由 Clock 传入，便于单测控时）
@Select("SELECT i.flash_price_cent AS priceCent, i.activity_id AS activityId "
      + "FROM flash_sale_item i JOIN flash_sale_activity a ON a.id = i.activity_id "
      + "WHERE i.sku_id = #{skuId} AND a.deleted = 0 AND a.status = 'ACTIVE' "
      + "  AND #{now} >= a.start_at AND #{now} < a.end_at "
      + "ORDER BY i.flash_price_cent ASC LIMIT 1")
FlashSaleRow selectActiveFlashSale(@Param("skuId") long skuId, @Param("now") LocalDateTime now);
```

同一个 `skuId` 若历史上有重复生效活动，取**最低价**兜底（`ORDER BY ... ASC LIMIT 1`），并在管理端保存时禁止时间窗重叠（§6.3）。

注入后，`PricingServiceFacade.price()` **无需改动**——它只是把 `loadContext` 的结果搬进 ctx，秒杀价自动参与。

### 5.3 与底价的关系（不动既有领域规则）

`PricingService.finalize` 会对低于 `floor_price_cent` 的结果抛 `BELOW_FLOOR_PRICE(40920)`。秒杀价是运营主动设的低价，不该被兜底规则拦死，但也不该绕过防倒挂。

**做法：发布活动时校验 `flash_price_cent >= sku.floor_price_cent`**（`floor_price_cent = 0` 视为未设底价，不拦）。校验不通过 → 拒绝发布并返回明确原因。这样领域规则一行不改，13 条定价单测不受影响。

### 5.4 详情页展示口径（避免"看到秒杀价、结算却是协议价"）

`/products/{id}` 返回 `flashSale` 快照（匿名口径，即 `PERSON` 客户）：

```jsonc
"flashSale": {
  "activityId": "12", "title": "限时秒杀",
  "flashPriceCent": 1920, "flashPriceYuan": "19.20",
  "originPriceCent": 3500, "originPriceYuan": "35.00",
  "startAt": "2026-11-21T00:00:00", "endAt": "2026-11-22T00:00:00",
  "remainingMillis": 5321000
}
```

- 无生效活动 → `flashSale: null`，用户端不渲染任何秒杀 UI；
- 有活动 → 红底横幅 + 倒计时，到期前端重新拉详情（横幅自动消失）；
- **企业客户一致性**：企业客户命中协议价时，结算页按协议价成交。因此结算页价格明细区必须显示取价来源（`price_source`），命中 `AGREEMENT` / `CUSTOMER_SKU` 时提示「已按企业协议价结算 ¥X」。这是 P0 必做项，否则构成价格误导。

### 5.5 管理端权限

新增权限码 `product:flash-sale`（写操作），复用 `@RequirePerm` 注解 + 权限种子迁移的既有做法（参照 `product:update`），给 SUPER_ADMIN 与运营角色授权。只读列表可用 `product:read`。

---

## 6. 管理端改动

### 6.1 商品编辑页 6 面板（`views/product/edit.vue`）

保持 `sent-panel` / `sent-page-title` 令牌，禁写硬编码色值。

1. **基础信息**：名称、副标题、分类、品牌、UV 定制开关。
2. **商品图集**：`el-upload` 手动上传 → `fileApi.upload()` 拿 `fileId` → 存 `form.images`。主图 = `images[0]`（角标标注），支持「设为主图」/删除/上移下移，上限 8 张。**不引拖拽库**（KISS）。
3. **商品参数**：可增删键值对表格（label / value / 删除 / 新增参数），≤20 条，顶部提示"前 4 条会在用户端放大展示"。
4. **详情介绍**：详情长图上传（≤20，可排序）+ 纯文本 `textarea`（rows=6，存 `detail_html`）。**不引富文本编辑器**：管理端依赖里没有 wangEditor/TinyMCE，淘宝详情本身就是长图拼接；同时避开 `v-html` 的 XSS 面。
5. **服务承诺**：`el-select multiple filterable allow-create`，预置「15天价保 / 7天无理由 / 假一赔四 / 极速退款 / 包邮 / 顺丰发货」，≤6 个。
6. **商品规格**：现有表格加「款式图」列（缩略图 + 上传），其余不动。库存经 `skuStockInitService` 端口，不得绕过。

回显走 `/products/{id}`；`Long` 被 `JacksonConfig` 序列化成字符串，一律 `Number()`。

### 6.2 秒杀活动管理页（新增 `views/product/flash-sale.vue`）

路由 `/product/flash-sale`，标题「秒杀活动」，group「商品」，`perm: 'product:flash-sale'`。

- **列表**：活动编号、标题、时间窗、状态（草稿 / 进行中 / 已结束 / 已下线，**进行中/已结束按时间窗实时计算**）、商品数、操作（编辑 / 发布 / 下线 / 删除）。
- **编辑抽屉**：
  - 标题、起止时间（`el-date-picker` `type="datetimerange"`，必须 `end > start`）；
  - 商品明细表格：`SKU 选择器`（参照 `customer-price.vue` 的 SKU 选择实现，展示 商品名 / 规格 / 零售价）+ 秒杀价（元，精度 2）+ 删除；
  - 实时校验并**在表格里就地标红**：`秒杀价 < 底价` → 「低于底价」；`秒杀价 >= 零售价` → 「秒杀价应低于零售价」；
  - 底部「保存草稿」/「保存并发布」。
- **删除**：软删活动（`deleted=1`），明细保留可追溯。

### 6.3 保存时校验清单

| 校验 | 失败返回 |
|---|---|
| `end_at > start_at` | `PARAM_INVALID`「结束时间必须晚于开始时间」 |
| 至少一个商品明细 | `PARAM_INVALID`「至少选择一个商品」 |
| `flash_price_cent > 0` | `PARAM_INVALID`「秒杀价必须大于 0」 |
| `flash_price_cent >= sku.floor_price_cent`（floor>0 时） | `PARAM_INVALID`「秒杀价低于该规格底价」 |
| `flash_price_cent < sku.retail_price_cent` | `PARAM_INVALID`「秒杀价应低于零售价」 |
| 同一 SKU 与其他 ACTIVE 活动时间窗不重叠 | `PARAM_INVALID`「该规格已参与重叠时段的秒杀活动」 |
| 发布时活动商品 SKU 均为 `ON` 状态 | `PARAM_INVALID`「含已下架规格，无法发布」 |

时间窗重叠判断在事务内用 SQL 校验（`EXISTS` + 排除自身），不依赖唯一索引（区间重叠无法用普通唯一键表达）。

---

## 7. 用户端改动（`views/product/detail.vue`）

### 7.1 版式（1440 容器）

```
.container
├─ el-breadcrumb                                    面包屑（保留）
├─ .detail-main  grid: 56px  minmax(0,1fr)  420px
│  ├─ .gallery-rail   竖排缩略图（≤8，选中红描边；无图不渲染）
│  ├─ .gallery        主图
│  └─ .buy            购买决策栏（sticky top: 88px）
│     ├─ .buy__title / .buy__subtitle
│     ├─ .flash-sale  秒杀横幅（无活动整块不渲染）   ← 新增
│     ├─ .buy__pricebox（秒杀时划线原价 + 秒杀价）
│     ├─ .buy__meta   销量 / 可售库存 / 发货地 / 运费
│     ├─ .buy__row    规格 / 数量 / UV 定制
│     └─ .buy__actions 加购 / 立即购买 / 收藏
├─ .detail-tabs       sticky 锚点：商品详情 / 规格参数 / 服务说明
├─ .detail-desc       详情长图（按序 <img>）+ 纯文本
├─ .detail-params     前 4 条放大成宫格 + 其余键值表
└─ .detail-service    服务承诺标签
```

断点 `max-width: 1240px` → 两列（隐藏 `.gallery-rail`，缩略图横排到主图下方）。

### 7.2 秒杀横幅（新增）

- 有 `flashSale` 才渲染：红底条 + 「限时秒杀」角标 + 秒杀价 + 划线原价 + `HH:mm:ss` 倒计时 + 「活动结束时间」；
- 倒计时 `setInterval` 每秒刷新，归零后重新请求详情（横幅自动消失，价格回落零售价）；
- 无活动时 `.flash-sale` **整个 DOM 不存在**（不是隐藏），避免 E2E 与样式误判；
- 价格区同步：秒杀生效时主价格显示秒杀价、下方划线原价 + 「省 ¥X」。

### 7.3 必须保留（E2E 红线）

`.spec-item`（含 `.active` / `.disabled`）· `.buy__meta` 中「可售库存」+ `[data-testid="available-stock"]` · `.buy__qty input` · 文案「仅剩 N 件」「库存不足：当前规格可售 N 件」 · `.buy-btn--cart` / `.buy-btn--now` / `.buy-fav` 及文案「加入购物车」「立即购买」「收藏」 · UV 定制面板全部行为（30 件起订、+¥10/件、图稿上传） · `selectSku` / `qtyExceedStock` / `remainingStock` 计算**一行不动**。

### 7.4 空态规则（禁止造假）

参数为空 → `.detail-params` 不渲染；详情图空 → 只渲染文本；文本也空 → 「暂无详情」；服务承诺空 → 不渲染；无秒杀 → 不渲染。

### 7.5 图片解析复用

新增 `web-user/src/utils/image.ts`：

```ts
export function resolveImageUrl(src?: string | null): string {
  const s = (src ?? '').trim();
  if (!s) return '';
  if (/^https?:\/\//i.test(s)) return s;
  if (/demo\.sent\.test/i.test(s)) return '';
  return `/api/files/${s}`;
}
```

`ProductThumb` 内部改用它，保留现有 `failed` 降级与渐变占位（向后兼容全部列表页）。

---

## 8. TDD 计划（先红后绿）

### 8.1 商品富内容单测

| 用例 | 断言 |
|---|---|
| `create_persistsRichContent` | 4 个 JSON 列落库为合法数组 |
| `update_clearsImagesWhenEmptyList` | 空数组 → `"[]"`（覆盖 `updateById` 跳过 null） |
| `update_rejectsTooManyImages` | 9 张 → `BizException`，`resolvedMessage()` 含「最多」 |
| `update_rejectsBlankParamLabel` | 空 label → `BizException` |
| `detail_parsesRichContent` | JSON → VO 列表正确 |
| `detail_toleratesBrokenJson` | 存 `{bad json` → 空列表，**不抛异常** |
| `detail_fallsBackToFirstImage` | `main_image` 空、`images` 非空 → 取第一张 |
| `ProductVO` 契约测试 | 旧构造器仍可用 |

### 8.2 秒杀单测（重点，纯领域可直接测）

| 用例 | 断言 |
|---|---|
| `FlashSalePriceStrategyTest#matchesWhenFlashPricePresent` | 有秒杀价、无合同价 → `match=true`，`source=FLASH_SALE`，`ruleId` 带活动 id |
| `#skipsWhenCustomerSkuPricePresent` | 一客一价存在 → 不命中，最终 `CUSTOMER_SKU` |
| `#skipsWhenAgreementValid` | 企业 + 协议有效 → 不命中，最终 `AGREEMENT`（**合同价优先**） |
| `PricingServiceFlashTest#flashBeatsLadderAndMember` | 秒杀 < 阶梯/会员 → 命中 `FLASH_SALE`（优先级 0） |
| `#flashBelowFloorThrows` | 秒杀价 < 底价 → `BELOW_FLOOR_PRICE`（证明发布校验确有必要） |
| `#flashBelowCostMarked` | 低于成本但不低于底价 → `belowCost=true`，不抛 |
| `PricingServiceFacadeImplTest#injectsFlashFromRepository` | 仓储返回秒杀行 → `LinePrice.sourceCode=FLASH_SALE` |
| `ProductPriceRepositoryImplTest` | `now` 在窗外 → 不返回秒杀；`status=DRAFT` → 不返回 |

> 时间相关断言一律注入固定 `Clock`（`Clock.fixed(Instant, ZoneId)`），**不用 `Thread.sleep`**。

### 8.3 秒杀管理端单测

保存校验 6 条各一例（§6.3 表格逐条），重点：时间窗重叠、秒杀价 ≥ 零售价、秒杀价 < 底价、含下架 SKU 不许发布。

### 8.4 集成测试

`ProductAdminCrudIT` 扩展：create → 查库校验 4 个 JSON 列 + `sku.image_file_id`；update 清空 → 确认 `"[]"`。
新增 `FlashSaleIT`：建活动 → 发布 → 查 `/products/{id}` 有 `flashSale` → 改 `end_at` 到过去 → 详情 `flashSale=null` → 下单金额回落到零售价（**这条最关键：证明秒杀真的进了订单**）。
`MapperIT` 同步 V54 版本断言。

### 8.5 架构守护

`mvn -o verify` 内跑 ArchUnit 4 条。秒杀模块放 `product` 包内（flashsale 子包），复用 `PricingServiceFacade` 契约，不新增跨模块调用。

### 8.6 新增 E2E

**`e2e/product-detail-rich.spec.ts`**（不写死商品/SKU，用 `pick-product.ts`）：
1. 三列结构 `.gallery-rail` / `.gallery` / `.buy` 同时可见（无图商品则 `.gallery-rail` 不存在且主图可见）；
2. 点第 2 张缩略图 → 主图 `src` 变化；
3. 点 tab「规格参数」→ 参数区进入视口；
4. 参数区若有数据：宫格 ≤4、键值表行数 = 参数条数；
5. 详情区 `<img>` 数量 = `detailImages.length`，无图时不出现空容器；
6. 不出现伪造评价内容。

**`e2e/flash-sale.spec.ts`**（自建自清，**最关键的 E2E**）：
1. 用 admin token 调 API 建活动（挑一个可售 SKU，秒杀价 = 零售价 − 1 元）→ 发布；
2. 打开详情页 → `.flash-sale` 可见、价格 = 秒杀价、倒计时 > 0、划线原价 = 零售价；
3. 加购 → 进结算 → **结算明细单价 = 秒杀价**（证明价格穿透到结算）；
4. 通过 API 把 `end_at` 改为过去（或下线）→ 刷新详情 → `.flash-sale` **不存在**、价格回落零售价；
5. `afterAll` 删除活动并校验库内无残留生效活动（`deleted=1` 或 `end_at` 已过）；
6. 断言库内**没有**其他生效秒杀活动（避免污染后续用例）。

### 8.7 全量回归重点

`product-stock-qty` · `product-pagination` · `price-repricing` · `search-filters` · 13 条下单链路（`checkout-*` / `coupon` / `custom-draft-image` / `pay-later-countdown`）· `favorites-footprints` · `e2e-core-flow` · `full-flow`。

---

## 9. 执行步骤与门禁

| 步骤 | 内容 | 自检 |
|---|---|---|
| **S0** | 本文档评审（范围与 §12 待确认） | — |
| **S1** | V54 迁移 + PO 加字段 + 商品富内容单测（红→绿） | `mvn -o -pl server test -Dtest=ProductAdminServiceImplTest` |
| **S2** | DTO 扩展 + Service 读写 + Mapper SQL + IT | `mvn -o -pl server verify -Dtest=ProductAdminCrudIT,MapperIT` |
| **S3** | 秒杀领域层：`PriceSource`/`PriceContext`/`Strategy` 单测（红→绿） | `mvn -o -pl server test -Dtest=FlashSalePriceStrategyTest,PricingServiceFlashTest` |
| **S4** | 秒杀仓储 + 管理端服务 + `FlashSaleIT`（含下单金额断言） | `mvn -o -pl server verify -Dtest=FlashSaleIT` |
| **S5** | 管理端：商品编辑 6 面板 + 秒杀活动页 | `pnpm --dir web-admin build` + 手点新增/编辑/发布/下线/秒杀校验 |
| **S6** | 用户端：详情页重构 + 秒杀横幅 + 结算页来源提示 | `pnpm --dir web-user build` |
| **S7** | 新增 2 个 spec，单跑 | `npx playwright test product-detail-rich flash-sale product-stock-qty` |
| **S8** | 全量门禁 | 见下 |

### S8 门禁

```bash
# 1) 先杀 8088（mvn clean 会删 jar 且占用导致失败）
cd "E:/Sent Sports Goods Management System" && mvn -o clean verify
#    期望 SUCCESS；单测 ≥1285；集成 ≥70；JaCoCo domain+common/util ≥80%
# 2) 重启 jar（上传盘 ./data/upload 依赖 cwd）
cd server && java -jar target/sent-server.jar --spring.datasource.password=123456
# 3) 三端 build
pnpm --dir web-admin build && pnpm --dir web-user build && pnpm --dir web-cs build
# 4) 全量 E2E（三端必须监听，5175 不可缺）
cd e2e && npx playwright test
#    基线 145 passed / 0 failed；期望 ≥145 + 新增条数，失败数为 0
```

---

## 10. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 改版导致 13 条下单 E2E 红 | 高 | 类名/文案只增不改；`selectSku`/`qtyExceedStock` 一行不动；S6 后先单跑 `product-stock-qty` |
| **秒杀价未穿透到下单金额**（只改展示） | 高 | 秒杀必须经 `loadContext()` + `PriceStrategy`；`FlashSaleIT` 断言**订单行单价**，E2E 断言结算页单价 |
| 秒杀 E2E 遗留生效活动，打破 `price-repricing` 的 10~40 元口径 | 高 | spec 内 `afterAll` 必清；用例结束断言库内无生效活动 |
| 企业客户看到秒杀价、结算走协议价 → 客诉 | 高 | 结算页显示取价来源，命中 `AGREEMENT`/`CUSTOMER_SKU` 时提示「已按企业协议价结算」 |
| 秒杀价低于底价 → 下单抛 40920 | 中 | 发布时校验 `>= floor_price_cent`（floor=0 不拦），不豁免领域 `finalize` |
| 同一 SKU 时间窗重叠 → 取价不确定 | 中 | 保存时事务内 `EXISTS` 校验；查询侧 `ORDER BY price ASC LIMIT 1` 兜底 |
| `updateById` 跳 null → 删图无效 | 中 | 空列表写 `"[]"`，单测锁死"删图后重新加载应消失" |
| record 加字段编译炸 12 处 | 中 | 保留旧签名构造器转调 |
| 脏 JSON 致详情页 500 | 中 | 读取统一 try-catch 降级空列表 |
| `detail_html` XSS | 中 | 不引富文本、不用 `v-html`，纯文本 `pre-wrap` |
| 种子数据无真图 → 页面全渐变块 | 中 | 本次不造图；E2E 断言结构而非图片真实性 |
| 1440 三列拥挤 | 低 | 1240 断点降两列 |
| JaCoCo 覆盖率被新分支拉低 | 中 | 每个校验分支配单测（尤其秒杀 6 条校验） |

---

## 11. 交付物

- `server/src/main/resources/db/migration/V54__product_detail_rich_and_flash_sale.sql`
- 后端商品：`AdminProductDtos` / `CatalogDtos` / `ProductAdminServiceImpl` / `ProductQueryServiceImpl` / `CatalogMapper`
- 后端秒杀：`domain/pricing/FlashSalePriceStrategy` / `PriceContext` / `PriceSource` / `ProductPriceRepositoryImpl` / `ProductMapper` / `FlashSaleAdminService` / `FlashSaleAdminController` / `FlashSaleDtos`
- 管理端：`views/product/edit.vue`、`views/product/flash-sale.vue`、`api/product.ts`、`router/modules/business.ts`
- 用户端：`views/product/detail.vue`、`utils/image.ts`、`components/ProductThumb.vue`、结算页来源提示
- 测试：`ProductAdminServiceImplTest`、`ProductQueryServiceImplTest`、`FlashSalePriceStrategyTest`、`PricingServiceFlashTest`、`FlashSaleAdminServiceImplTest`、`ProductAdminCrudIT`、`FlashSaleIT`、`MapperIT`、`e2e/product-detail-rich.spec.ts`、`e2e/flash-sale.spec.ts`

---

## 12. 待确认（评审时请拍板）

1. **评价模块**确认本次不做（P2）？做的话需 `product_review` 表 + 订单完成触发 + 管理端审核 + 晒单图，量级大幅上升。
2. **款式**只需单层（一组 SKU + 款式图），还是必须「颜色 × 尺码」矩阵？
3. 详情介绍用「长图 + 纯文本」（建议），还是必须上富文本编辑器（需加依赖 + XSS 白名单清洗）？
4. **秒杀与合同价的优先级**：建议「一客一价 / 企业协议价 > 秒杀价 > 阶梯 > 会员 > 零售」。若希望秒杀对企业客户也生效（覆盖协议价），需改 `match()` 条件并接受可能低于协议价。
5. **秒杀是否需要限量配额**（含"已抢 X%"进度条）？v1 建议不做，只控价 + 控时间窗，库存仍受 SKU 可售约束。
