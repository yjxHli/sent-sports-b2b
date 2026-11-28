<template>
  <div>
    <div class="sent-page-head">
      <div class="sent-page-head__main">
        <h2 class="sent-page-title">业务报表</h2>
        <p class="sent-page-desc">
          以支付时间 paid_at 为基准统计成交，全额退款整单剔除，不计入业绩。
        </p>
      </div>
    </div>

    <!-- 筛选条：本系统只有「统计区间」一个真实维度，其余维度（地区 / 渠道）没有数据源，
         不做点了没反应的假下拉；区间切换会同时重算 KPI、趋势与两张排行。 -->
    <div class="report-filter">
      <div class="report-filter__item">
        <span class="report-filter__label">统计区间</span>
        <el-radio-group :model-value="days" size="small" @change="selectDays">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
      </div>
      <div class="report-filter__item">
        <span class="report-filter__label">数据范围</span>
        <span class="report-filter__value sent-num">
          {{ trend ? `${trend.fromDate} ~ ${trend.toDate}` : '--' }}
        </span>
      </div>
      <el-button link class="report-filter__reset" @click="resetFilter">重置筛选</el-button>
      <span class="report-filter__note">筛选联动全部 KPI 与图表</span>
    </div>

    <!-- KPI 卡：顶部 2px 色条按指标分色，数字是主体，口径写在下面一行小字 -->
    <div class="kpi-row">
      <div v-for="(k, i) in kpis" :key="k.key" class="kpi-card" :class="`kpi-card--${i + 1}`">
        <span class="kpi-card__label">{{ k.label }}</span>
        <strong class="kpi-card__value sent-num" :title="k.value">
          {{ k.value }}<span v-if="k.unit" class="kpi-card__unit">{{ k.unit }}</span>
        </strong>
        <span class="kpi-card__hint">{{ k.hint }}</span>
      </div>
    </div>

    <div class="chart-row">
      <!-- 左 2fr：主趋势，SVG 手绘折线图（不引入 ECharts），带 Y 轴刻度与网格 -->
      <div class="sent-panel chart-panel chart-panel--main">
        <div class="sent-panel__head">
          <h3 class="section-title">销售趋势（按日）</h3>
          <span v-if="trend && trend.points.length" class="sent-panel__meta">
            峰值 {{ fmtMoney(maxTrendAmount) }}
          </span>
        </div>

        <div v-if="trend && trend.points.length" class="trend">
          <div class="trend-axis" aria-hidden="true">
            <span v-for="(t, i) in yTicks" :key="i" class="trend-axis__tick">{{ t }}</span>
          </div>
          <div class="trend-plot">
            <div class="trend-canvas">
              <div class="trend-grid" aria-hidden="true">
                <span v-for="i in 4" :key="i" class="trend-grid__line" />
              </div>
              <svg
                class="trend-svg"
                :viewBox="`0 0 ${LINE_W} ${LINE_H}`"
                preserveAspectRatio="none"
                role="img"
                aria-label="每日销售额折线趋势"
              >
                <polygon class="trend-area" :points="areaPoints" />
                <polyline
                  class="trend-line"
                  :points="linePoints"
                  vector-effect="non-scaling-stroke"
                />
              </svg>
              <div class="trend-dots">
                <span
                  v-for="(p, i) in trend.points"
                  :key="p.date"
                  class="trend-dot"
                  :class="{ 'trend-dot--peak': i === peakIndex }"
                  :style="{ left: dotLeft(i), top: dotTop(p) }"
                />
              </div>
              <div
                v-if="hoverPoint"
                class="trend-tooltip"
                :style="{ left: hoverLeft, top: hoverTop }"
                role="status"
              >
                <span class="trend-tooltip__date sent-num">{{ hoverPoint.date }}</span>
                <span class="trend-tooltip__amount sent-num">销售额 ¥{{ hoverPoint.amountYuan }}</span>
                <span class="trend-tooltip__orders sent-num">订单 {{ hoverPoint.orderCount }} 笔</span>
              </div>
              <div
                class="trend-hover-layer"
                @mousemove="onTrendHover"
                @mouseleave="hoverIndex = null"
              />
            </div>
            <div class="trend-labels" aria-hidden="true">
              <span v-for="(p, i) in trend.points" :key="p.date" class="trend-labels__item">
                {{ showLabel(i) ? p.date.slice(5) : '' }}
              </span>
            </div>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="暂无趋势数据" :image-size="60" />

        <p class="sent-hint">
          口径：以 orders.paid_at 为时间基准，按 SUM(paid_cent) 聚合；仅统计 PAID / PART_REFUNDED，
          全额退款（FULL_REFUNDED）订单整单剔除，不计入销售业绩。无成交日期补零。
        </p>
      </div>

      <!-- 右 1fr：热销商品 Top 5（原环形占比卡已按用户要求删除，改用带进度条的排行榜） -->
      <div class="sent-panel chart-panel chart-panel--side">
        <div class="sent-panel__head">
          <h3 class="section-title">热销商品 Top 5</h3>
          <span class="sent-panel__meta">前 5 名占 {{ topSharePct }}%</span>
        </div>

        <ol v-if="hotTop.length" class="hot-rank">
          <li
            v-for="(item, i) in hotTop"
            :key="item.skuId"
            class="hot-rank__item"
            :title="`${nameOf(item)}　成交 ${fmtMoney(item.amountCent)}　销量 ${item.qty} 件`"
          >
            <span
              class="hot-rank__fill"
              :class="{ 'hot-rank__fill--first': i === 0 }"
              :style="{ width: hotBarWidth(item) }"
              aria-hidden="true"
            />
            <span class="hot-rank__dot" :class="{ 'hot-rank__dot--first': i === 0 }" aria-hidden="true" />
            <span class="hot-rank__main">
              <span class="hot-rank__name">{{ nameOf(item) }}</span>
              <span class="hot-rank__meta">销量：{{ item.qty }} 件 · {{ item.orderCount }} 单</span>
            </span>
            <span class="hot-rank__figure">
              <span class="hot-rank__amount sent-num">{{ fmtMoney(item.amountCent) }}</span>
              <span class="hot-rank__pct sent-num">{{ item.sharePct }}%</span>
            </span>
          </li>
        </ol>
        <el-empty v-else-if="!loading" description="暂无成交数据" :image-size="60" />

        <p class="sent-hint">
          口径：按订单行（order_item）聚合近 {{ days }} 天已支付订单，按 sku_id 汇总销量与成交金额；
          进度条按第 1 名金额归一，右侧百分比为占区间商品成交总额的比重。
        </p>
      </div>
    </div>

    <!-- 第二行：大客户 Top 5 / 履约风险预警 / 客户采购结构，三卡并排 -->
    <div class="report-row report-row--three">
    <!-- 左：Top 5 大客户采购排行（按 customer_id 聚合，数据范围同样随账号下推） -->
    <div class="sent-panel report-top-customer">
      <div class="sent-panel__head">
        <h3 class="section-title">Top 5 大客户采购排行</h3>
        <span class="sent-panel__meta">
          {{ customerCount }} 位客户 · 累计 {{ fmtMoney(customerTotalCent) }}
        </span>
      </div>

      <ol v-if="topCustomerItems.length" class="hot-rank hot-rank--wide">
        <li
          v-for="(item, i) in topCustomerItems"
          :key="item.customerId"
          class="hot-rank__item"
          :title="`${item.customerName}　成交 ${fmtMoney(item.amountCent)}　${item.orderCount} 笔订单`"
        >
          <span
            class="hot-rank__fill"
            :class="{ 'hot-rank__fill--first': i === 0 }"
            :style="{ width: customerBarWidth(item) }"
            aria-hidden="true"
          />
          <span class="hot-rank__dot" :class="{ 'hot-rank__dot--first': i === 0 }" aria-hidden="true" />
          <span class="hot-rank__main">
            <span class="hot-rank__name">{{ item.customerName }}</span>
            <span class="hot-rank__meta">
              {{ maskPhone(item.phone) }} · {{ item.orderCount }} 笔订单
            </span>
          </span>
          <span class="hot-rank__figure">
            <span class="hot-rank__amount sent-num">{{ fmtMoney(item.amountCent) }}</span>
            <span class="hot-rank__pct sent-num">{{ item.sharePct }}%</span>
          </span>
        </li>
      </ol>
      <el-empty v-else-if="!loading" description="区间内暂无客户成交" :image-size="60" />

      <p class="sent-hint">
        口径：按 customer_id 聚合近 {{ days }} 天已支付订单实付金额，企业客户显示企业名称、个人客户显示昵称；
        数据范围随当前账号下推（主管看团队、财务与超管看全部）。
      </p>
    </div>

    <!-- 第二行中：履约风险预警（复用 dashboard 接口，当前时点快照，不随统计区间变化） -->
    <div class="sent-panel report-risk">
      <div class="sent-panel__head">
        <h3 class="section-title">履约风险预警</h3>
        <span class="sent-panel__meta">实时快照</span>
      </div>
      <ul class="report-risk__list">
        <li v-for="item in riskItems" :key="item.key" class="report-risk__item">
          <div class="report-risk__row">
            <span class="report-risk__dot" :class="`report-risk__dot--${item.tone}`" aria-hidden="true" />
            <span class="report-risk__label">{{ item.label }}</span>
            <strong class="report-risk__value sent-num">{{ item.value }}</strong>
            <span class="report-risk__unit">{{ item.unit }}</span>
            <span class="report-risk__level" :class="`report-risk__level--${item.tone}`">{{ item.level }}</span>
          </div>
          <span class="report-risk__track" aria-hidden="true">
            <span
              class="report-risk__bar"
              :class="`report-risk__bar--${item.tone}`"
              :style="{ width: item.width }"
            />
          </span>
        </li>
      </ul>
      <p class="sent-hint">口径：当前时点待办数量，不随上方统计区间变化；横条按本卡最大值归一。</p>
    </div>

    <!-- 第二行右：客户采购结构（企业 / 个人 / 未分类环形占比） -->
    <div class="sent-panel report-customer-mix">
      <div class="sent-panel__head">
        <h3 class="section-title">客户采购结构</h3>
        <span class="sent-panel__meta">企业占比 {{ enterpriseShare }}%</span>
      </div>
      <div class="mix-body">
        <div class="mix-donut" :style="mixDonutStyle" role="img" aria-label="客户采购结构环形图">
          <span class="mix-donut__center">
            <strong class="mix-donut__pct sent-num">{{ enterpriseShare }}%</strong>
            <span class="mix-donut__label">企业客户</span>
          </span>
        </div>
        <ul class="mix-legend">
          <li v-for="(item, i) in mixItems" :key="item.type" class="report-customer-mix__item">
            <div class="mix-legend__head">
              <span class="mix-legend__dot" :class="`mix-legend__dot--${i + 1}`" aria-hidden="true" />
              <span class="mix-legend__label">{{ item.label }}</span>
              <span class="mix-legend__pct sent-num">{{ item.sharePct }}%</span>
            </div>
            <div class="mix-legend__detail">
              <span class="report-customer-mix__meta">
                {{ item.customerCount }} 位客户 · {{ item.orderCount }} 单
              </span>
              <span class="mix-legend__value sent-num">{{ fmtMoney(item.amountCent) }}</span>
            </div>
          </li>
        </ul>
      </div>
      <p class="sent-hint">口径：按 orders.customer_type 聚合近 {{ days }} 天实付金额，空值归「未分类」。</p>
    </div>
    </div>

    <!-- 第三行：商品销售明细 / 复购新老客结构，两卡并排 -->
    <div class="report-row report-row--two">
    <!-- 左：商品销售明细（含毛利与可用库存，取前 10） -->
    <div class="sent-panel report-product-detail">
      <div class="sent-panel__head">
        <h3 class="section-title">商品销售明细</h3>
        <span class="sent-panel__meta">
          区间内 {{ productView.itemCount }} 个 SKU · 毛利率 {{ productView.grossMarginPct }}%
        </span>
      </div>
      <el-table :data="productRows" size="small" stripe class="detail-table" v-loading="loading">
        <!-- 商品列是唯一 min-width 弹性列，会把剩余宽度全吃掉，把「销量」及右侧挤到最右。
             压缩 # 与三个数值列的富余宽度（数值本身只需 ~88px），把宽度还给商品列，
             「销量」起的四列整体左移，同时最长的商品名 + 规格仍能一行放下不被省略。 -->
        <el-table-column label="#" type="index" width="34" align="center" />
        <el-table-column label="商品 / 规格" min-width="168" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="detail-name">{{ row.skuName }}</span>
            <span class="detail-spec">{{ [fmtSpec(row.spec), sourceLabel(row.priceSources)].filter(Boolean).join(' · ') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="销量" prop="qty" width="64" align="right" />
        <el-table-column label="成交额" width="100" align="right">
          <template #default="{ row }"><span class="sent-num">{{ fmtMoney(row.amountCent) }}</span></template>
        </el-table-column>
        <el-table-column label="毛利 / 率" width="102" align="right">
          <template #default="{ row }">
            <span class="detail-profit sent-num">{{ fmtMoney(row.profitCent) }}</span>
            <span class="detail-margin sent-num">{{ row.grossMarginPct }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="库存" prop="stockQty" width="58" align="right" />
      </el-table>
      <p class="sent-hint">
        口径：按 sku_id 聚合近 {{ days }} 天已支付订单行；毛利 = 成交额 − 销量 × SKU 当前成本价
        （历史成本不追溯），可用库存 = 物理 − 锁定 − 待发。
      </p>
    </div>

    <!-- 第三行右：复购 / 新老客结构 -->
    <div class="sent-panel report-repeat">
      <div class="sent-panel__head">
        <h3 class="section-title">复购 / 新老客结构</h3>
        <span class="sent-panel__meta">区间内 {{ repeatView.totalCustomerCount }} 位客户成交</span>
      </div>
      <div class="repeat-figure">
        <strong class="repeat-figure__value sent-num">{{ repeatView.repeatRatePct }}%</strong>
        <span class="repeat-figure__label">复购率</span>
      </div>
      <div class="repeat-bar" role="img" aria-label="新老客金额结构">
        <span class="repeat-bar__repeat" :style="{ width: repeatAmountShare + '%' }" />
      </div>
      <ul class="repeat-legend">
        <li>
          <span class="repeat-legend__dot repeat-legend__dot--new" />
          <span>新客 {{ repeatView.newCustomerCount }} 人</span>
          <strong class="sent-num">{{ fmtMoney(repeatView.newAmountCent) }}</strong>
        </li>
        <li>
          <span class="repeat-legend__dot repeat-legend__dot--repeat" />
          <span>复购客 {{ repeatView.repeatCustomerCount }} 人</span>
          <strong class="sent-num">{{ fmtMoney(repeatView.repeatAmountCent) }}</strong>
        </li>
      </ul>

      <div class="repeat-buckets">
        <span class="repeat-buckets__title">复购客历史订单频次</span>
        <ul class="repeat-buckets__list">
          <li v-for="bucket in repeatBuckets" :key="bucket.label" class="report-repeat__bucket">
            <span class="report-repeat__bucket-track" aria-hidden="true">
              <span
                class="report-repeat__bucket-bar"
                :style="{ height: bucketHeight(bucket) }"
              />
            </span>
            <span class="report-repeat__bucket-value sent-num">{{ bucket.customerCount }} 人</span>
            <span class="report-repeat__bucket-foot">
              <span class="report-repeat__bucket-label">{{ bucket.label }}</span>
              <span class="report-repeat__bucket-pct sent-num">{{ bucket.sharePct }}%</span>
            </span>
          </li>
        </ul>
      </div>

      <p class="sent-hint">
        口径：区间前已有成交记为复购客，否则为新客；人均 {{ repeatView.avgOrderCount }} 单。
      </p>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 业务报表：筛选条 → KPI 卡行 → 左折线趋势 / 右热销商品 Top 5 → 全宽大客户 Top 5。
 *
 * 2026-11-21 第三次调整（用户反馈）：环形占比图在长尾数据下「其他」占 76.7%，
 * 既难看也没有信息量 —— 物理删除，改成带进度条的 Top 5 排行；原下方的商品排行表
 * 一并收进右栏（只留 Top 5），腾出的整行放大客户采购排行。
 *
 * 2026-11-23 第四次调整：第一行保持「左趋势 / 右热销 Top 5」，但销售趋势由柱状图
 * 改为 SVG 手绘折线图；折线用归一化 viewBox + non-scaling-stroke 保证任意宽度描边一致，
 * 数据点圆用 HTML 绝对定位，避免 preserveAspectRatio="none" 把圆拉成椭圆。
 *
 * B2B 自助下单场景下「谁卖的」没有意义（大量订单无归属销售），
 * 排行统一改为「卖什么 / 谁在买」；数据范围由后端 @DataScope 下推。
 *
 * 图表全部用 CSS + SVG 手绘，项目未引入 echarts（本次也未新增任何图表库依赖）。
 */
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import {
  systemApi,
  type CustomerMixVO,
  type DashboardSummaryVO,
  type HotProductItemVO,
  type HotProductVO,
  type ProductDetailVO,
  type RepeatCustomerVO,
  type SalesTrendPointVO,
  type SalesTrendVO,
  type TopCustomerItemVO,
  type TopCustomerVO,
} from '@/api';
import { fmtMoney, maskPhone } from '@sent/shared';

const DEFAULT_DAYS = 30;
/** 排行条数：后端 limit 收敛 1~50，这里只要 Top 5 */
const TOP_LIMIT = 5;
/** 折线虚拟坐标系：viewBox 固定，靠 preserveAspectRatio="none" 铺满容器宽度 */
const LINE_W = 1000;
const LINE_H = 300;

const days = ref(DEFAULT_DAYS);
const loading = ref(false);
const trend = ref<SalesTrendVO | null>(null);
const hot = ref<HotProductVO | null>(null);
const customers = ref<TopCustomerVO | null>(null);
const mix = ref<CustomerMixVO | null>(null);
const productDetail = ref<ProductDetailVO | null>(null);
const repeat = ref<RepeatCustomerVO | null>(null);
const dashboard = ref<DashboardSummaryVO | null>(null);

/** 各请求独立失败独立提示，任一失败不影响其余卡片渲染 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const [trendRes, hotRes, customerRes, mixRes, detailRes, repeatRes] = await Promise.allSettled([
      unwrap(systemApi.salesTrend(days.value)),
      unwrap(systemApi.hotProducts(days.value, TOP_LIMIT)),
      unwrap(systemApi.topCustomers(days.value, TOP_LIMIT)),
      unwrap(systemApi.customerMix(days.value)),
      unwrap(systemApi.productDetails(days.value, 10)),
      unwrap(systemApi.repeatCustomers(days.value)),
    ]);
    trend.value = trendRes.status === 'fulfilled' ? trendRes.value : null;
    hot.value = hotRes.status === 'fulfilled' ? hotRes.value : null;
    customers.value = customerRes.status === 'fulfilled' ? customerRes.value : null;
    mix.value = mixRes.status === 'fulfilled' ? mixRes.value : null;
    productDetail.value = detailRes.status === 'fulfilled' ? detailRes.value : null;
    repeat.value = repeatRes.status === 'fulfilled' ? repeatRes.value : null;
    if (trendRes.status === 'rejected') ElMessage.error('销售趋势加载失败');
    if (hotRes.status === 'rejected') ElMessage.error('热销商品加载失败');
    if (customerRes.status === 'rejected') ElMessage.error('大客户排行加载失败');
    if (mixRes.status === 'rejected') ElMessage.error('客户采购结构加载失败');
    if (detailRes.status === 'rejected') ElMessage.error('商品销售明细加载失败');
    if (repeatRes.status === 'rejected') ElMessage.error('复购结构加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
  // 履约预警是当前时点快照，一次取齐即可，不随统计区间重查
  unwrap(systemApi.dashboard())
    .then((vo) => { dashboard.value = vo; })
    .catch(() => ElMessage.error('履约预警加载失败'));
});

const maxTrendAmount = computed(() =>
  Math.max(1, ...(trend.value?.points ?? []).map((p) => Number(p.amountCent ?? 0))),
);

/** 峰值索引：金额最高那天（并列取最早），供折线强调点与 KPI 口径行共用 */
const peakIndex = computed(() => {
  const points = trend.value?.points ?? [];
  let idx = -1;
  let best = -1;
  for (let i = 0; i < points.length; i++) {
    const amount = Number(points[i].amountCent ?? 0);
    if (amount > best) {
      best = amount;
      idx = i;
    }
  }
  return best > 0 ? idx : -1;
});

/** 峰值日：金额最高的那天，用于「峰值日销售额」卡的口径行 */
const peakDay = computed(() => {
  const points = trend.value?.points ?? [];
  const idx = peakIndex.value;
  return idx >= 0 ? points[idx].date.slice(5) : '区间内无成交';
});

/** 五张 KPI 卡：只用现有接口字段，毛利类指标库里没有成本数据，不做 */
const kpis = computed(() => {
  const t = trend.value;
  const dailyAvgCent = t && t.days > 0 ? Math.round(Number(t.totalAmountCent) / t.days) : 0;
  return [
    {
      key: 'amount',
      label: '总销售额',
      value: t ? fmtMoney(t.totalAmountCent) : '--',
      hint: '实付合计 · 剔除退款',
    },
    {
      key: 'orders',
      label: '成交订单',
      value: t ? String(t.totalOrderCount) : '--',
      unit: '笔',
      hint: '按支付时间去重',
    },
    {
      key: 'avg',
      label: '客单价',
      value: t ? `¥${t.avgOrderYuan}` : '--',
      hint: '总销售额 ÷ 成交订单',
    },
    {
      key: 'daily',
      label: '日均销售额',
      value: t ? fmtMoney(dailyAvgCent) : '--',
      hint: `总销售额 ÷ ${t?.days ?? 0} 天`,
    },
    {
      key: 'peak',
      label: '峰值日销售额',
      value: t ? fmtMoney(maxTrendAmount.value) : '--',
      hint: peakDay.value,
    },
  ];
});

/** 热销商品 Top 5（后端已按金额降序、limit=5） */
const hotTop = computed<HotProductItemVO[]>(() => hot.value?.items ?? []);
/** 大客户 Top 5 */
const topCustomerItems = computed<TopCustomerItemVO[]>(() => customers.value?.items ?? []);
const customerCount = computed(() => customers.value?.customerCount ?? 0);
const customerTotalCent = computed(() => Number(customers.value?.totalAmountCent ?? 0));

/** 前 5 名合计占比：分母是区间全量，长尾业务下这个数通常不高，写出来比让人猜好 */
const topSharePct = computed(() =>
  hotTop.value.reduce((acc, item) => acc + Number(item.sharePct ?? 0), 0).toFixed(1),
);

/* ---------- 拓展报表：履约预警 / 客户结构 / 商品明细 / 复购结构 ---------- */

/** 履约预警：全部来自 dashboard 真库聚合，0 是真实值；横条按本卡最大值归一，并给严重度徽标 */
const riskItems = computed(() => {
  const d = dashboard.value;
  const LEVEL: Record<string, string> = { high: '紧急', mid: '关注', low: '监控' };
  const raw = [
    { key: 'ship', label: '待发货订单', num: d?.pendingShipCount, unit: '笔', tone: 'high' },
    { key: 'custom', label: '待审定制图稿', num: d?.pendingCustomReviewCount, unit: '项', tone: 'high' },
    { key: 'refund', label: '待审退款申请', num: d?.pendingRefundCount, unit: '项', tone: 'mid' },
    { key: 'receivable', label: '逾期应收跟进', num: d?.overdueReceivableCount, unit: '笔', tone: 'mid' },
    { key: 'stock', label: '库存预警 SKU', num: d?.warningSkuCount, unit: '个', tone: 'low' },
    { key: 'chat', label: '待接入会话', num: d?.pendingChatCount, unit: '个', tone: 'low' },
  ];
  const max = Math.max(1, ...raw.map((it) => Number(it.num ?? 0)));
  return raw.map((it) => ({
    ...it,
    value: d ? String(it.num ?? 0) : '--',
    level: LEVEL[it.tone],
    width: `${Math.max(2, Math.round((Number(it.num ?? 0) / max) * 100))}%`,
  }));
});

/** 三类固定齐全：接口即使缺项也补齐，环形图不会只剩一角 */
const mixItems = computed<CustomerMixVO['items']>(() => {
  const typeOrder = ['ENTERPRISE', 'PERSON', 'UNKNOWN'] as const;
  const fallback: CustomerMixVO['items'] = typeOrder.map((type) => ({
    type, label: type === 'ENTERPRISE' ? '企业客户' : type === 'PERSON' ? '个人客户' : '未分类',
    customerCount: 0, orderCount: 0, amountCent: 0, amountYuan: '0.00', sharePct: '0.0',
  }));
  return fallback.map((base) => mix.value?.items.find((it) => it.type === base.type) ?? base);
});
const enterpriseShare = computed(() => mixItems.value[0]?.sharePct ?? '0.0');
/** CSS conic-gradient：企业 / 个人 / 未分类三段，色值只取主题令牌 */
const mixDonutStyle = computed(() => {
  const [a, b] = mixItems.value;
  const p1 = Number(a?.sharePct ?? 0);
  const p2 = Number(b?.sharePct ?? 0);
  const e1 = Math.min(100, Math.max(0, p1));
  const e2 = Math.min(100, Math.max(0, e1 + p2));
  return {
    background: `conic-gradient(var(--sent-chart-1) 0% ${e1}%, `
      + `var(--sent-chart-2) ${e1}% ${e2}%, var(--sent-chart-6) ${e2}% 100%)`,
  };
});

const productRows = computed(() => productDetail.value?.items ?? []);
/** 模板展示用的非空兜底：接口未回时不让模板访问 null */
const productView = computed(() => productDetail.value ?? {
  itemCount: 0, grossMarginPct: '0.0',
} as Pick<ProductDetailVO, 'itemCount' | 'grossMarginPct'>);
const repeatView = computed(() => repeat.value ?? {
  totalCustomerCount: 0, newCustomerCount: 0, repeatCustomerCount: 0,
  repeatRatePct: '0.0', newAmountCent: 0, repeatAmountCent: 0, avgOrderCount: '0.0',
} as Pick<RepeatCustomerVO, 'totalCustomerCount' | 'newCustomerCount' | 'repeatCustomerCount'
  | 'repeatRatePct' | 'newAmountCent' | 'repeatAmountCent' | 'avgOrderCount'>);
/** 价格来源码转中文标签，未识别的原样显示（后端可能新增来源类型） */
function sourceLabel(sources: string): string {
  const dict: Record<string, string> = {
    RETAIL: '零售价', AGREEMENT: '合同价', CUSTOMER_SKU: '一客一价',
    TIER: '阶梯价', MEMBER: '会员价', FLASH_SALE: '秒杀价',
  };
  return (sources ?? '').split(',').filter(Boolean).map((s) => dict[s] ?? s).join(' / ') || '—';
}

const repeatAmountShare = computed(() => {
  const vo = repeat.value;
  if (!vo) return 0;
  const total = Number(vo.newAmountCent) + Number(vo.repeatAmountCent);
  return total > 0 ? Math.round(Number(vo.repeatAmountCent) / total * 100) : 0;
});

/** 复购频次三档：接口未回时给空档，保证卡片结构稳定 */
const repeatBuckets = computed(() => {
  const fallback = ['2 单', '3–5 单', '6 单以上'].map((label) => ({
    label, customerCount: 0, amountCent: 0, amountYuan: '0.00', sharePct: '0.0',
  }));
  return repeat.value?.frequencyBuckets?.length ? repeat.value.frequencyBuckets : fallback;
});
/**
 * 竖柱高度按最大档归一（0.5% 与 75.1% 相差 150 倍，直接用占比会得到 0px 柱），
 * 最小 8% 保证零档也看得见一小截。
 */
function bucketHeight(bucket: { sharePct: string }): string {
  const max = Math.max(...repeatBuckets.value.map((b) => Number(b.sharePct ?? 0)), 1);
  const ratio = Number(bucket.sharePct ?? 0) / max;
  return `${Math.max(8, Math.round(ratio * 100))}%`;
}

/** 进度条按第 1 名归一（否则 5.9% 的条几乎看不见），最小 8% 保证可见 */
function ratioWidth(amountCent: number | string, topCent: number | string): string {
  const top = Number(topCent ?? 0);
  if (top <= 0) return '0%';
  const ratio = Number(amountCent ?? 0) / top;
  return `${Math.max(8, Math.round(ratio * 100))}%`;
}
function hotBarWidth(item: HotProductItemVO): string {
  return ratioWidth(item.amountCent, hotTop.value[0]?.amountCent ?? 0);
}
function customerBarWidth(item: TopCustomerItemVO): string {
  return ratioWidth(item.amountCent, topCustomerItems.value[0]?.amountCent ?? 0);
}

/** 商品名：名称 +（有规格时）规格，过长由 CSS 省略号截断 */
function nameOf(item: HotProductItemVO): string {
  const spec = fmtSpec(item.spec);
  return spec ? `${item.skuName} · ${spec}` : item.skuName;
}

/**
 * 规格快照是 JSON（如 {"color": "经典黑金"}），直接渲染像一串代码；
 * 展示层拆成值序列，解析失败就原样返回。
 */
function fmtSpec(spec: string): string {
  const text = (spec ?? '').trim();
  if (!text) return '';
  if (!text.startsWith('{') && !text.startsWith('[')) return text;
  try {
    const parsed: unknown = JSON.parse(text);
    if (Array.isArray(parsed)) return parsed.map(String).join(' / ');
    return Object.values(parsed as Record<string, unknown>)
      .filter((value) => value !== null && value !== undefined && value !== '')
      .map(String)
      .join(' / ');
  } catch {
    return text;
  }
}

/** Y 轴 4 档刻度（从高到低），金额超过 1 万用「万」，否则取整到元 */
const yTicks = computed(() => {
  const max = maxTrendAmount.value;
  return [1, 2 / 3, 1 / 3, 0].map((ratio) => fmtAxis(max * ratio));
});

function fmtAxis(cent: number): string {
  if (cent <= 0) return '0';
  const yuan = cent / 100;
  if (yuan >= 10000) {
    const wan = yuan / 10000;
    return `${wan >= 10 ? Math.round(wan) : wan.toFixed(1)}万`;
  }
  return `¥${Math.round(yuan)}`;
}

/** 折线点 X：按点序等分虚拟宽度，单点时落在中线 */
function pointX(i: number): number {
  const n = trend.value?.points.length ?? 0;
  return n <= 1 ? LINE_W / 2 : (i / (n - 1)) * LINE_W;
}

/** 折线点 Y：按峰值归一，底部为 0，顶部为峰值 */
function pointY(p: SalesTrendPointVO): number {
  const ratio = Number(p.amountCent ?? 0) / maxTrendAmount.value;
  return LINE_H - ratio * LINE_H;
}

/** SVG 折线点串：E2E 断言点数 = 统计天数 */
const linePoints = computed(() =>
  (trend.value?.points ?? [])
    .map((p, i) => `${pointX(i)},${pointY(p)}`)
    .join(' '),
);

/** 面积填充：从基线闭合到折线，给折线一个视觉底 */
const areaPoints = computed(() => `0,${LINE_H} ${linePoints.value} ${LINE_W},${LINE_H}`);

/** 数据点圆用 HTML 绝对定位，百分比换算回容器尺寸，避免被 SVG 拉伸成椭圆 */
function dotLeft(i: number): string {
  return `${(pointX(i) / LINE_W) * 100}%`;
}
function dotTop(p: SalesTrendPointVO): string {
  return `${(pointY(p) / LINE_H) * 100}%`;
}

/* ---------- 折线 hover tooltip：鼠标所在横向位置映射到最近的日期点 ---------- */
const hoverIndex = ref<number | null>(null);
const hoverPoint = computed<SalesTrendPointVO | null>(() => {
  const i = hoverIndex.value;
  return i === null ? null : trend.value?.points[i] ?? null;
});
/** 气泡贴着数据点上方，左右 clamp 在画布内，避免贴边被裁 */
const hoverLeft = computed(() => {
  const i = hoverIndex.value ?? 0;
  const pct = (pointX(i) / LINE_W) * 100;
  return `clamp(0px, ${pct}% - 60px, calc(100% - 120px))`;
});
const hoverTop = computed(() => {
  const point = hoverPoint.value;
  if (!point) return '0px';
  const pct = (pointY(point) / LINE_H) * 100;
  return `max(2px, calc(${pct}% - 68px))`;
});
function onTrendHover(event: MouseEvent): void {
  const points = trend.value?.points ?? [];
  if (!points.length) return;
  const target = event.currentTarget as HTMLElement;
  const rect = target.getBoundingClientRect();
  const ratio = rect.width > 0 ? (event.clientX - rect.left) / rect.width : 0;
  const index = Math.round(Math.min(1, Math.max(0, ratio)) * (points.length - 1));
  hoverIndex.value = index;
}

/** 日期标签按间隔抽样，避免 30/90 根柱子下方文字挤成一团。 */
function showLabel(i: number): boolean {
  const step = days.value >= 90 ? 15 : days.value >= 30 ? 5 : 1;
  return i % step === 0 || i === (trend.value?.points.length ?? 0) - 1;
}

function selectDays(value: number | string | boolean): void {
  days.value = Number(value);
  void load();
}

function resetFilter(): void {
  days.value = DEFAULT_DAYS;
  void load();
}
</script>

<style scoped>
/* ---------- 筛选条：与卡片同层的浅面，标签弱、值强 ---------- */
.report-filter {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  padding: 12px 16px;
  margin-bottom: var(--sp-4);
  background: var(--sent-surface);
  border: 1px solid var(--sent-line);
  border-radius: var(--r-card);
  box-shadow: var(--sent-shadow-card);
}
.report-filter__item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.report-filter__label {
  font-size: 13px;
  color: var(--sent-ink-3);
}
.report-filter__value {
  font-size: 13px;
  color: var(--sent-ink-2);
}
.report-filter__reset {
  font-size: 13px;
  color: var(--sent-primary);
}
.report-filter__note {
  margin-left: auto;
  font-size: 12px;
  color: var(--sent-ink-3);
}

/* ---------- KPI 卡行 ---------- */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}
.kpi-card {
  position: relative;
  overflow: hidden;
  padding: 16px 16px 14px;
  background: var(--sent-surface);
  border: 1px solid var(--sent-line);
  border-radius: var(--r-card);
  box-shadow: var(--sent-shadow-card);
}
/* 2px 顶条：只做指标辨识，不参与文字信息 */
.kpi-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  background: var(--tone, var(--sent-chart-1));
}
.kpi-card--1 { --tone: var(--sent-chart-1); }
.kpi-card--2 { --tone: var(--sent-chart-2); }
.kpi-card--3 { --tone: var(--sent-chart-3); }
.kpi-card--4 { --tone: var(--sent-chart-4); }
.kpi-card--5 { --tone: var(--sent-chart-5); }

.kpi-card__label {
  display: block;
  font-size: 13px;
  color: var(--sent-ink-3);
}
.kpi-card__value {
  display: block;
  margin-top: 8px;
  /* 28px 在 1440 视口刚好放得下 ¥219,716.49；1280 视口五卡变窄，
     在 20（--fs-title）与 28（--fs-figure）两档之间平滑收，避免省略号。 */
  font-size: clamp(var(--fs-title), 1.95vw, var(--fs-figure));
  line-height: 1.15;
  font-weight: var(--fw-strong);
  letter-spacing: -0.02em;
  color: var(--sent-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kpi-card__unit {
  margin-left: 3px;
  font-size: 13px;
  font-weight: var(--fw-normal);
  color: var(--sent-ink-3);
}
.kpi-card__hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--sent-ink-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- 图表行：左 2fr 趋势 / 右 1fr 热销 Top 5，两栏由 grid 拉等高 ---------- */
.chart-row {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: var(--sp-4);
  margin-bottom: var(--sp-4);
  align-items: stretch;
}
.chart-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* ---------- 行内卡片：行距唯一真源 ----------
   .sent-panel 自带 margin-bottom:16，行容器再带一层 16 → 卡间实际 32px，
   且卡片 margin 参与 grid 行高计算，把每行多撑 16px。
   行内卡片 margin 归零，行距只留行容器的 16px；内距 20 → 16。 */
.chart-row > .sent-panel,
.report-row > .sent-panel {
  margin-bottom: 0;
  padding: var(--sp-4);
}
/* 标题区原为 margin 16 + padding 12，与卡片内距叠加后标题到内容 28px：压到 22px */
.chart-row > .sent-panel > .sent-panel__head,
.report-row > .sent-panel > .sent-panel__head {
  margin-bottom: var(--sp-3);
  padding-bottom: 10px;
}

/* ---------- 拓展报表：第二行三卡 / 第三行两卡 ---------- */
.report-row {
  display: grid;
  gap: var(--sp-4);
  margin-bottom: var(--sp-4);
  align-items: stretch;
}
.report-row--three {
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.95fr) minmax(0, 1fr);
}
.report-row--two {
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
}
.report-top-customer,
.report-risk,
.report-customer-mix,
.report-product-detail,
.report-repeat {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* 大客户从全宽挪进窄栏：行高压紧、手机号与订单数保留一行 ellipsis */
.report-top-customer .sent-panel__head {
  /* 标题不折 → 副标题自动掉到第二行当副标题用，避免「采购排 / 行」断字 */
  flex-wrap: wrap;
  row-gap: 2px;
}
.report-top-customer .section-title {
  white-space: nowrap;
}
.report-top-customer .sent-panel__meta {
  font-size: 12px;
}
.report-top-customer .hot-rank__item {
  min-height: 44px;
  padding: 5px 10px;
  gap: 8px;
}
.report-top-customer .hot-rank__meta {
  font-size: 11px;
}
.report-top-customer .hot-rank__figure {
  gap: 4px;
}
.report-top-customer .hot-rank__pct {
  min-width: 34px;
}

/* 履约风险预警 */
.report-risk__list {
  list-style: none;
  margin: 0;
  padding: 2px 0;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.report-risk__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 52px;
  padding: 7px 8px;
  border-radius: 8px;
  justify-content: center;
}
.report-risk__row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.report-risk__track {
  display: block;
  height: 5px;
  border-radius: 3px;
  background: var(--sent-primary-soft);
  overflow: hidden;
}
.report-risk__bar {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: var(--sent-chart-1);
  transition: width 0.35s ease-out;
}
.report-risk__bar--high { background: var(--sent-chart-5); }
.report-risk__bar--mid { background: var(--sent-chart-3); }
.report-risk__bar--low { background: var(--sent-chart-1); }
.report-risk__level {
  flex: none;
  /* 徽标统一贴右：标签定宽 + 数值紧跟，中间不再出现随标签长度漂移的空隙 */
  margin-left: auto;
  padding: 1px 6px;
  border-radius: 10px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--sent-ink-2);
  background: var(--sent-primary-soft);
}
.report-risk__level--high {
  color: var(--sent-chart-5);
  background: var(--sent-accent-soft);
}
.report-risk__level--mid {
  color: var(--sent-chart-3);
  background: var(--sent-accent-soft);
}
.report-risk__item:nth-child(odd) {
  background: var(--sent-primary-soft);
}
.report-risk__dot {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--sent-chart-6);
}
.report-risk__dot--high { background: var(--sent-chart-5); }
.report-risk__dot--mid { background: var(--sent-chart-3); }
.report-risk__dot--low { background: var(--sent-chart-1); }
.report-risk__label {
  /* 定宽标签列：数值落在同一条竖线上，标签与数值之间只剩 8px 间距 */
  flex: 0 0 88px;
  min-width: 0;
  font-size: 12px;
  color: var(--sent-ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.report-risk__value {
  font-size: 14px;
  font-weight: var(--fw-medium);
  color: var(--sent-ink);
}
.report-risk__unit {
  font-size: 11px;
  color: var(--sent-ink-3);
}

/* 客户采购结构：CSS conic-gradient 手绘环形 + 图例 */
.mix-body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 原来 center：400px 容器里 315px 内容 → 上下各空 42px。
     改为顶部对齐 + 让图例吃掉剩余高度，空白变成图例内的均匀呼吸。 */
  justify-content: flex-start;
  gap: 12px;
  min-height: 150px;
}
.mix-donut {
  flex: none;
  position: relative;
  width: 160px;
  height: 160px;
  border-radius: 50%;
}
.mix-donut::after {
  content: '';
  position: absolute;
  inset: 26px;
  border-radius: 50%;
  background: var(--sent-surface);
}
.mix-donut__center {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.mix-donut__pct {
  font-size: 18px;
  font-weight: var(--fw-strong);
  color: var(--sent-ink);
}
.mix-donut__label {
  font-size: 11px;
  color: var(--sent-ink-3);
}
.mix-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  width: 100%;
  min-width: 0;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}
.report-customer-mix__item {
  display: flex;
  flex-direction: column;
  justify-content: center;
  /* 三项均分图例剩余高度，避免整卡内容挤在上半截 */
  flex: 1 1 0;
  gap: 3px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--sent-line);
}
.report-customer-mix__item:last-child {
  border-bottom: none;
}
.mix-legend__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mix-legend__detail {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding-left: 16px;
}
.report-customer-mix__meta {
  font-size: 11px;
  color: var(--sent-ink-3);
}
.mix-legend__dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: var(--sent-chart-1);
}
.mix-legend__dot--2 { background: var(--sent-chart-2); }
.mix-legend__dot--3 { background: var(--sent-chart-6); }
.mix-legend__label {
  font-size: 12px;
  color: var(--sent-ink-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.mix-legend__value {
  font-size: 12px;
  color: var(--sent-ink);
}
.mix-legend__pct {
  min-width: 38px;
  text-align: right;
  font-size: 12px;
  color: var(--sent-ink-3);
}

/* 商品销售明细 */
.detail-table {
  flex: 1 1 auto;
  min-height: 0;
}
/* 行高 48 → 40：全局 `.el-table td.el-table__cell{height:44}` 是唯一真源，
   本页要再紧一档必须同时覆盖 height（只压 padding 会被 height 顶住）。
   双行内容本身 31px，配 3px 上下内距刚好 40。 */
.detail-table :deep(td.el-table__cell) {
  height: 40px;
  padding: 3px 0;
}
/* 宽屏给表格右侧留 20px 呼吸：库存列不再贴死卡片右内距，
   「销量」起的四列随表宽整体左移（窄屏保持满宽，避免商品名被截太狠）。 */
@media (min-width: 1400px) {
  .detail-table {
    width: calc(100% - 20px);
  }
}
.detail-name {
  color: var(--sent-ink);
  font-size: 12px;
}
.detail-spec {
  margin-left: 6px;
  color: var(--sent-ink-3);
  font-size: 11px;
}
.detail-profit,
.detail-margin {
  display: block;
  line-height: 1.35;
}
.detail-profit {
  color: var(--sent-ink);
  font-size: 12px;
}
.detail-margin {
  color: var(--sent-ink-3);
  font-size: 11px;
}

/* 复购 / 新老客结构 */
.repeat-figure {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 0 6px;
}
.repeat-figure__value {
  font-size: 30px;
  font-weight: var(--fw-strong);
  color: var(--sent-primary);
}
.repeat-figure__label {
  font-size: 12px;
  color: var(--sent-ink-3);
}
.repeat-bar {
  display: flex;
  height: 10px;
  border-radius: 5px;
  overflow: hidden;
  background: var(--sent-chart-2);
}
.repeat-bar__repeat {
  display: block;
  height: 100%;
  background: var(--sent-chart-1);
  transition: width 0.35s ease-out;
}
.repeat-legend {
  list-style: none;
  margin: 14px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.repeat-legend li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--sent-ink-2);
}
.repeat-legend strong {
  margin-left: auto;
  font-size: 12px;
  color: var(--sent-ink);
}
.repeat-legend__dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}
.repeat-legend__dot--new { background: var(--sent-chart-2); }
.repeat-legend__dot--repeat { background: var(--sent-chart-1); }

/* 复购频次分布：三列竖柱（分布图语义，且 flex:1 自适应卡片剩余高度，不留离散空档） */
.repeat-buckets {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--sent-line);
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.repeat-buckets__title {
  font-size: 12px;
  color: var(--sent-ink-3);
}
.repeat-buckets__list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-height: 0;
}
.report-repeat__bucket {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  min-height: 0;
  padding: 0 2px;
}
.report-repeat__bucket-track {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  /* 柱身只占列宽 46%：三列满宽会变成三块色块，收细才像分布图 */
  width: 46%;
  flex: 1 1 auto;
  min-height: 0;
  border-radius: 6px 6px 0 0;
  background: none;
  overflow: hidden;
}
.report-repeat__bucket-bar {
  display: block;
  width: 100%;
  border-radius: 6px 6px 0 0;
  background: var(--sent-chart-1);
  transition: height 0.35s ease-out;
}
.report-repeat__bucket-value {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sent-ink);
}
.report-repeat__bucket-foot {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 11px;
  white-space: nowrap;
}
.report-repeat__bucket-pct {
  font-size: 11px;
  color: var(--sent-ink-3);
}
.report-repeat__bucket-label {
  font-size: 12px;
  color: var(--sent-ink-2);
  white-space: nowrap;
}

/* ---------- 折线趋势：左刻度列 + 网格 + SVG 折线 + HTML 数据点 + 日期带 ---------- */
/* 高度交给 flex：与右栏等高，趋势区吃掉剩余空间 */
.trend {
  display: flex;
  gap: 14px;
  flex: 1 1 auto;
  min-height: 220px;
}
.trend-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  flex: none;
  width: 54px;
  /* 与柱区底部对齐：下方 20px 是日期标签带 */
  margin-bottom: 20px;
  text-align: right;
}
.trend-axis__tick {
  font-size: 11px;
  line-height: 1;
  color: var(--sent-ink-3);
  font-variant-numeric: tabular-nums;
  transform: translateY(50%);
}
.trend-axis__tick:first-child {
  transform: translateY(0);
}
.trend-axis__tick:last-child {
  transform: translateY(100%);
}
.trend-plot {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
}
.trend-canvas {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
}
.trend-grid {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}
.trend-grid__line {
  height: 1px;
  background: var(--sent-line);
}
.trend-grid__line:last-child {
  background: var(--sent-line-2);
}
/* SVG 铺满画布：viewBox 固定 + non-scaling-stroke 保证任意宽度描边粗细一致 */
.trend-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  overflow: visible;
}
.trend-area {
  fill: var(--sent-primary);
  opacity: 0.08;
  stroke: none;
}
.trend-line {
  fill: none;
  stroke: var(--sent-primary);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
}
/* 数据点圆用 HTML 绝对定位：SVG 被拉伸时圆不会被压成椭圆 */
.trend-dots {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
}
.trend-dot {
  position: absolute;
  width: 6px;
  height: 6px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: var(--sent-primary);
  pointer-events: auto;
  cursor: default;
  transition: transform var(--sent-ease);
}
.trend-dot:hover {
  transform: translate(-50%, -50%) scale(1.4);
}
/* 峰值点：强调橙 + 更大半径，其余退到品牌绿 */
.trend-dot--peak {
  width: 10px;
  height: 10px;
  background: var(--sent-accent);
  z-index: 1;
}
/* 透明捕获层：盖在折线与圆点之上统一接鼠标事件，避免点到某个圆才出提示 */
.trend-hover-layer {
  position: absolute;
  inset: 0;
  z-index: 4;
  cursor: crosshair;
}
.trend-tooltip {
  position: absolute;
  z-index: 5;
  min-width: 120px;
  padding: 6px 10px;
  border: 1px solid var(--sent-line);
  border-radius: 8px;
  background: var(--sent-surface);
  box-shadow: var(--sent-shadow-card);
  display: flex;
  flex-direction: column;
  gap: 2px;
  pointer-events: none;
}
.trend-tooltip__date {
  font-size: 11px;
  color: var(--sent-ink-3);
}
.trend-tooltip__amount {
  font-size: 13px;
  font-weight: var(--fw-medium);
  color: var(--sent-ink);
}
.trend-tooltip__orders {
  font-size: 11px;
  color: var(--sent-ink-2);
}
.trend-labels {
  display: flex;
  gap: 2px;
  height: 20px;
  padding-top: 4px;
  flex: none;
}
.trend-labels__item {
  flex: 1 1 0;
  min-width: 0;
  font-size: 11px;
  line-height: 1;
  color: var(--sent-ink-3);
  text-align: center;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* ---------- 排行榜（热销商品 / 大客户共用）：底层进度条 + 圆点 + 名称 + 金额 ---------- */
.hot-rank {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 auto;
  justify-content: center;
}
.hot-rank__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 6px 12px;
  border-radius: 8px;
  overflow: hidden;
}
.hot-rank--wide .hot-rank__item {
  min-height: 56px;
}
/* 极淡的进度条垫在整行最底层：第 1 名橙、其余墨绿，宽度按第 1 名归一 */
.hot-rank__fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 8px;
  background: var(--sent-primary-soft);
  z-index: 0;
  transition: width 0.35s ease-out;
}
.hot-rank__fill--first {
  background: var(--sent-accent-soft);
}
.hot-rank__dot {
  position: relative;
  z-index: 1;
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--sent-primary);
}
.hot-rank__dot--first {
  background: var(--sent-accent);
}
.hot-rank__main {
  position: relative;
  z-index: 1;
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.hot-rank__name {
  font-size: 13px;
  color: var(--sent-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-rank__meta {
  font-size: 12px;
  color: var(--sent-ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-rank__figure {
  position: relative;
  z-index: 1;
  flex: none;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.hot-rank__amount {
  font-size: 13px;
  font-weight: var(--fw-medium);
  color: var(--sent-ink);
  font-variant-numeric: tabular-nums;
}
.hot-rank__pct {
  min-width: 40px;
  text-align: right;
  font-size: 12px;
  color: var(--sent-ink-3);
  font-variant-numeric: tabular-nums;
}

/* 口径说明：<p> 默认 1em 上下外边距（13px）+ 卡片内距，正文下方会多出 33px 空白。
   信息一条不删，只把字号与行距压一档、去掉下外边距。 */
.sent-hint {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.55;
}

/* 窄屏：图表上下堆叠，KPI 三列 */
@media (max-width: 1279px) {
  .kpi-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .report-row--three {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 1180px) {
  .chart-row,
  .report-row--three,
  .report-row--two {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
