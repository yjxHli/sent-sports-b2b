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
      <!-- 左 2fr：主趋势，仍是柱状图（用户明确要求），带 Y 轴刻度与网格 -->
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
              <div class="trend-bars">
                <div
                  v-for="p in trend.points"
                  :key="p.date"
                  class="trend-col"
                  :title="`${p.date}　销售额 ¥${p.amountYuan}　订单 ${p.orderCount} 笔`"
                >
                  <div
                    class="trend-bar"
                    :class="{ 'trend-bar--peak': Number(p.amountCent) === maxTrendAmount }"
                    :style="{ height: barHeight(p.amountCent) }"
                  />
                </div>
              </div>
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

    <!-- 底部全宽：Top 5 大客户采购排行（按 customer_id 聚合，数据范围同样随账号下推） -->
    <div class="sent-panel">
      <div class="sent-panel__head">
        <h3 class="section-title">Top 5 大客户采购排行</h3>
        <span class="sent-panel__meta">
          区间内 {{ customerCount }} 位客户有成交 · 累计 {{ fmtMoney(customerTotalCent) }}
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
  </div>
</template>

<script setup lang="ts">
/**
 * 业务报表：筛选条 → KPI 卡行 → 左柱状趋势 / 右热销商品 Top 5 → 全宽大客户 Top 5。
 *
 * 2026-11-21 第三次调整（用户反馈）：环形占比图在长尾数据下「其他」占 76.7%，
 * 既难看也没有信息量 —— 物理删除，改成带进度条的 Top 5 排行；原下方的商品排行表
 * 一并收进右栏（只留 Top 5），腾出的整行放大客户采购排行。
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
  type HotProductItemVO,
  type HotProductVO,
  type SalesTrendVO,
  type TopCustomerItemVO,
  type TopCustomerVO,
} from '@/api';
import { fmtMoney } from '@sent/shared';

const DEFAULT_DAYS = 30;
/** 排行条数：后端 limit 收敛 1~50，这里只要 Top 5 */
const TOP_LIMIT = 5;

const days = ref(DEFAULT_DAYS);
const loading = ref(false);
const trend = ref<SalesTrendVO | null>(null);
const hot = ref<HotProductVO | null>(null);
const customers = ref<TopCustomerVO | null>(null);

/** 三个请求各自失败给各自文案，任一失败不影响其余卡片渲染 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const [trendRes, hotRes, customerRes] = await Promise.allSettled([
      unwrap(systemApi.salesTrend(days.value)),
      unwrap(systemApi.hotProducts(days.value, TOP_LIMIT)),
      unwrap(systemApi.topCustomers(days.value, TOP_LIMIT)),
    ]);
    trend.value = trendRes.status === 'fulfilled' ? trendRes.value : null;
    hot.value = hotRes.status === 'fulfilled' ? hotRes.value : null;
    customers.value = customerRes.status === 'fulfilled' ? customerRes.value : null;
    if (trendRes.status === 'rejected') ElMessage.error('销售趋势加载失败');
    if (hotRes.status === 'rejected') ElMessage.error('热销商品加载失败');
    if (customerRes.status === 'rejected') ElMessage.error('大客户排行加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(load);

const maxTrendAmount = computed(() =>
  Math.max(1, ...(trend.value?.points ?? []).map((p) => Number(p.amountCent ?? 0))),
);

/** 峰值日：金额最高的那天（并列取最早），用于「峰值日销售额」卡的口径行 */
const peakDay = computed(() => {
  const points = trend.value?.points ?? [];
  let best: { date: string; amountCent: number | string } | null = null;
  for (const p of points) {
    if (!best || Number(p.amountCent) > Number(best.amountCent)) best = p;
  }
  return best && Number(best.amountCent) > 0 ? best.date.slice(5) : '区间内无成交';
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

/** 手机号脱敏：138****8000（保留前 3 后 4，长度不足时原样返回） */
function maskPhone(phone: string): string {
  const text = (phone ?? '').trim();
  if (text.length < 8) return text || '—';
  return `${text.slice(0, 3)}****${text.slice(-4)}`;
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

function barHeight(amountCent: number | string): string {
  const ratio = Number(amountCent ?? 0) / maxTrendAmount.value;
  return `${Math.max(2, Math.round(ratio * 100))}%`;
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

/* ---------- 柱状趋势：左刻度列 + 网格 + 柱 + 日期带 ---------- */
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
.trend-bars {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  gap: 2px;
}
.trend-col {
  flex: 1 1 0;
  min-width: 1px;
  height: 100%;
  display: flex;
  align-items: flex-end;
  cursor: default;
}
.trend-bar {
  width: 100%;
  min-height: 2px;
  border-radius: 2px 2px 0 0;
  background: var(--sent-chart-1);
  opacity: 0.78;
  transition: opacity var(--sent-ease);
}
.trend-col:hover .trend-bar {
  opacity: 1;
}
/* 只有最高的那一天拿强调色，其余全部退到品牌绿 */
.trend-bar--peak,
.trend-col:hover .trend-bar--peak {
  background: var(--sent-accent);
  opacity: 1;
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

/* 窄屏：图表上下堆叠，KPI 三列 */
@media (max-width: 1279px) {
  .kpi-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 1180px) {
  .chart-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
