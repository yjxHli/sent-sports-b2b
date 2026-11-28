<template>
  <div class="inv-page">
    <section class="inv-card">
      <header class="inv-head">
        <div class="inv-tabs" role="tablist" aria-label="发票分类">
          <button
            v-for="t in TABS"
            :key="t.key"
            type="button"
            class="inv-tab"
            :class="{ 'inv-tab--on': active === t.key }"
            role="tab"
            :aria-selected="active === t.key"
            :data-testid="`invoice-tab-${t.key}`"
            @click="switchTab(t.key)"
          >
            {{ t.label }}<i v-if="counts[t.key] > 0" class="inv-tab__num">{{ counts[t.key] }}</i>
          </button>
        </div>

        <!-- 未申请页签是订单列表，没有发票号可搜，隐藏搜索框 -->
        <div v-if="active !== 'pending'" class="inv-search">
          <input
            v-model="keyword"
            class="inv-search__input"
            data-testid="invoice-search"
            placeholder="输入订单号 / 发票号"
            @keyup.enter="applyFilter"
          />
          <button class="inv-search__btn" type="button" data-testid="invoice-search-btn" @click="applyFilter">搜索</button>
        </div>
      </header>

      <div v-if="loading" class="inv-state">加载中…</div>

      <!-- 未申请：可开票订单 -->
      <template v-else-if="active === 'pending'">
        <div v-if="pendingRows.length === 0" class="inv-state" data-testid="invoice-empty">
          <div class="inv-state__icon">🧾</div>
          <p>没有待开票的订单</p>
          <span>订单完成后即可在这里申请开票</span>
        </div>
        <div v-else class="inv-table-wrap">
          <table class="inv-table">
            <thead>
              <tr>
                <th class="col-info">订单信息</th>
                <th class="col-money">开票金额</th>
                <th class="col-date">完成时间</th>
                <th class="col-action">发票操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in pendingRows" :key="row.orderNo || index" data-testid="pending-row">
                <td class="col-info">
                  <div class="inv-order">
                    订单号：<button class="inv-order__link" @click="goOrder(row.orderNo)">{{ row.orderNo }}</button>
                  </div>
                  <div class="inv-goods">{{ row.firstTitle || '订单商品' }}</div>
                </td>
                <td class="col-money"><b class="inv-money">¥{{ row.amountYuan || '0.00' }}</b></td>
                <td class="col-date">{{ fmtDate(row.completedAtMillis) }}</td>
                <td class="col-action">
                  <button class="inv-link" data-testid="invoice-apply" @click="openApply(row)">申请开票</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <!-- 已开具 / 申请中 -->
      <template v-else>
        <div v-if="rows.length === 0" class="inv-state" data-testid="invoice-empty">
          <div class="inv-state__icon">🧾</div>
          <p>{{ active === 'issued' ? '还没有已开具的发票' : '没有申请中的发票' }}</p>
          <span>{{ active === 'issued' ? '开票完成后可在这里查看与下载' : '在「未申请」里选择订单申请开票' }}</span>
        </div>
        <div v-else class="inv-table-wrap">
          <table class="inv-table">
            <thead>
              <tr>
                <th class="col-info">发票信息</th>
                <th class="col-money">开票金额</th>
                <th class="col-title">发票抬头</th>
                <th class="col-date">{{ active === 'issued' ? '开票日期' : '申请日期' }}</th>
                <th class="col-status">开票进度</th>
                <th class="col-action">发票操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in rows" :key="row.invoiceId || row.orderNo || index" data-testid="invoice-row">
                <td class="col-info">
                  <div class="inv-order">
                    订单号：<button class="inv-order__link" @click="goOrder(row.orderNo)">{{ row.orderNo }}</button>
                  </div>
                  <div v-if="row.invoiceNo" class="inv-no">发票号：{{ row.invoiceNo }}</div>
                  <div v-else class="inv-no inv-no--muted">发票开具后生成发票号</div>
                </td>
                <td class="col-money"><b class="inv-money">¥{{ row.amountYuan || '0.00' }}</b></td>
                <td class="col-title">
                  <div class="inv-title">{{ row.title || '—' }}</div>
                  <div class="inv-type">{{ typeText(row.type) }}</div>
                </td>
                <td class="col-date">{{ fmtDate(row.status === 'ISSUED' ? row.issuedAtMillis : row.createdAtMillis) }}</td>
                <td class="col-status">
                  <!-- 2026-11-28 用户要求：下载按钮挪到「开票进度」下面，跟状态标签同一列 -->
                  <div class="inv-status">
                    <span class="inv-badge" :class="statusClass(row.status)">{{ statusText(row.status) }}</span>
                    <button
                      v-if="row.status === 'ISSUED'"
                      class="inv-link inv-status__download"
                      data-testid="invoice-download"
                      @click="download(row)"
                    >下载到本地</button>
                  </div>
                </td>
                <td class="col-action">
                  <!-- 操作竖排：查看发票 / 查看订单（用户要求垂直排放，横向两个按钮挤在一起点不准） -->
                  <div class="inv-ops">
                    <button
                      v-if="row.status === 'ISSUED'"
                      class="inv-link"
                      data-testid="invoice-preview"
                      @click="preview(row)"
                    >查看发票</button>
                    <button
                      v-else-if="row.status === 'REJECTED' || row.status === 'EXCEPTION'"
                      class="inv-link"
                      @click="openApplyOf(row)"
                    >重新申请</button>
                    <button class="inv-link" @click="goOrder(row.orderNo)">查看订单</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <footer class="inv-foot">
        <span class="inv-tip">
          票面金额按订单实付开具，一个订单只能开一张发票；发票由财务审核后开具，可在本页下载电子发票。
        </span>
        <el-pagination
          v-if="total > 0"
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          class="inv-page-ctl"
          @current-change="onPageChange"
        />
      </footer>
    </section>

    <InvoiceApplyDialog
      v-model="applyVisible"
      :order-no="applyOrderNo"
      :payable-cent="applyPayableCent"
      @updated="onApplied"
    />
  </div>
</template>

<script setup lang="ts">
import { fmtDate } from '@sent/shared';
/**
 * 我的发票（2026-11-27，参考淘宝发票页）。
 *
 * 三个页签：
 * - 已开具：`GET /api/invoices?status=ISSUED`，可查看 / 下载 PDF；
 * - 申请中：`status=OPEN`（待开 + 驳回 + 异常），驳回 / 异常可原单重新申请；
 * - 未申请：`GET /api/invoices/pending`，已完成且仍可开票、还没有有效申请的订单。
 *
 * 金额一律用后端返回的 `amountYuan`（联查订单应付），前端不自己算；
 * PDF 走 blob 请求（带令牌），再交给浏览器预览 / 下载——直接把接口地址挂到 <a> 上不会带令牌。
 */
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { invoiceApi, type InvoiceRowVO, type PendingInvoiceVO } from '@/api/invoice';
import InvoiceApplyDialog from '@/components/InvoiceApplyDialog.vue';

type TabKey = 'issued' | 'open' | 'pending';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'issued', label: '已开具发票' },
  { key: 'open', label: '申请中发票' },
  { key: 'pending', label: '未申请' },
];

const router = useRouter();

const active = ref<TabKey>('issued');
const rows = ref<InvoiceRowVO[]>([]);
const pendingRows = ref<PendingInvoiceVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = 10;
const keyword = ref('');
/** 已生效的搜索词：点搜索 / 回车才更新，避免边输边发请求 */
const appliedKeyword = ref('');
const loading = ref(false);
const counts = reactive<Record<TabKey, number>>({ issued: 0, open: 0, pending: 0 });

const applyVisible = ref(false);
const applyOrderNo = ref('');
const applyPayableCent = ref<number | null>(null);

function statusText(status?: string | null): string {
  switch (status) {
    case 'ISSUED': return '已开具';
    case 'APPLIED': return '待开具';
    case 'REJECTED': return '已驳回';
    case 'EXCEPTION': return '开票异常';
    default: return '处理中';
  }
}

function statusClass(status?: string | null): string {
  if (status === 'ISSUED') return 'inv-badge--done';
  if (status === 'REJECTED' || status === 'EXCEPTION') return 'inv-badge--bad';
  return 'inv-badge--wait';
}

function typeText(type?: string | null): string {
  return type === 'SPECIAL' ? '增值税专用发票' : '增值税普通发票';
}

async function loadCounts(): Promise<void> {
  const [issued, open, pending] = await Promise.all([
    unwrap(invoiceApi.mine({ page: 1, size: 1, status: 'ISSUED' })).catch(() => null),
    unwrap(invoiceApi.mine({ page: 1, size: 1, status: 'OPEN' })).catch(() => null),
    unwrap(invoiceApi.pending({ page: 1, size: 1 })).catch(() => null),
  ]);
  counts.issued = Number(issued?.total ?? 0);
  counts.open = Number(open?.total ?? 0);
  counts.pending = Number(pending?.total ?? 0);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    if (active.value === 'pending') {
      const result = await unwrap(invoiceApi.pending({ page: page.value, size }));
      pendingRows.value = result?.list ?? [];
      total.value = Number(result?.total ?? 0);
    } else {
      const result = await unwrap(invoiceApi.mine({
        page: page.value,
        size,
        status: active.value === 'issued' ? 'ISSUED' : 'OPEN',
        keyword: appliedKeyword.value || undefined,
      }));
      rows.value = result?.list ?? [];
      total.value = Number(result?.total ?? 0);
    }
  } catch (e) {
    rows.value = [];
    pendingRows.value = [];
    total.value = 0;
    ElMessage.error((e as Error).message || '发票列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function refresh(): Promise<void> {
  await Promise.all([loadCounts(), load()]);
}

function switchTab(tab: TabKey): void {
  if (active.value === tab) return;
  active.value = tab;
  page.value = 1;
  keyword.value = '';
  appliedKeyword.value = '';
  void load();
}

function applyFilter(): void {
  appliedKeyword.value = keyword.value.trim();
  page.value = 1;
  void load();
}

function onPageChange(next: number): void {
  page.value = next;
  void load();
}

function goOrder(orderNo?: string | null): void {
  if (!orderNo) return;
  router.push(`/order/${orderNo}`);
}

function openApply(row: PendingInvoiceVO): void {
  applyOrderNo.value = row.orderNo ?? '';
  applyPayableCent.value = Number(row.amountCent ?? 0);
  applyVisible.value = true;
}

/** 申请中页签里驳回 / 异常的单子允许按原订单重新申请（后端只拦 APPLIED / ISSUED）。 */
function openApplyOf(row: InvoiceRowVO): void {
  applyOrderNo.value = row.orderNo ?? '';
  applyPayableCent.value = Number(row.amountCent ?? 0);
  applyVisible.value = true;
}

function onApplied(): void {
  void refresh();
}

async function preview(row: InvoiceRowVO): Promise<void> {
  const id = Number(row.invoiceId ?? 0);
  if (!id) return;
  try {
    const blob = await invoiceApi.pdfBlob(id);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener');
    // 新标签页读完再释放，立即 revoke 会让预览空白
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (e) {
    ElMessage.error((e as Error).message || '发票预览失败');
  }
}

async function download(row: InvoiceRowVO): Promise<void> {
  const id = Number(row.invoiceId ?? 0);
  if (!id) return;
  try {
    await invoiceApi.downloadPdf(id, row.invoiceNo);
  } catch (e) {
    ElMessage.error((e as Error).message || '发票下载失败');
  }
}

onMounted(() => {
  void refresh();
});
</script>

<style scoped>
.inv-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.inv-card {
  /* 2026-11-27：与个人中心统一白底、统一 8px 圆角，不再用阴影做「卡片感」 */
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px 12px;
}

/* ---------- 页签 + 搜索 ---------- */
.inv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--sent-border);
  padding-bottom: 12px;
}
.inv-tabs {
  display: flex;
  align-items: center;
  gap: 26px;
}
.inv-tab {
  position: relative;
  border: none;
  background: transparent;
  padding: 6px 0 10px;
  cursor: pointer;
  font-size: 15px;
  color: var(--sent-text-2);
  font-weight: 500;
}
.inv-tab:hover {
  color: var(--sent-primary);
}
.inv-tab--on {
  color: var(--sent-text);
  font-weight: 700;
}
.inv-tab--on::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  border-radius: 2px;
  background: var(--sent-accent);
}
.inv-tab__num {
  font-style: normal;
  font-size: 11px;
  color: var(--sent-accent-dark);
  background: var(--sent-accent-soft);
  border-radius: 9px;
  padding: 1px 6px;
  margin-left: 4px;
}

.inv-search {
  display: flex;
  align-items: center;
  height: 32px;
  border: 1px solid var(--sent-border);
  /* 与顶部搜索、商品详情页迷你搜索保持同一套 6px 直角圆角 */
  border-radius: 6px;
  overflow: hidden;
  width: 280px;
  flex: none;
}
.inv-search:focus-within {
  border-color: var(--sent-primary);
}
.inv-search__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  outline: none;
  background: transparent;
  padding: 0 12px;
  font-size: 12.5px;
  color: var(--sent-text);
}
.inv-search__btn {
  flex: none;
  height: 100%;
  border: none;
  /* 与顶部搜索、商品详情页迷你搜索的按钮同一套：深绿底 + 白字 */
  background: var(--sent-primary);
  color: #fff;
  padding: 0 14px;
  font-size: 13px;
  cursor: pointer;
}
.inv-search__btn:hover {
  filter: brightness(1.08);
}

/* ---------- 表格 ---------- */
.inv-table-wrap {
  overflow-x: auto;
}
/* 列宽预算（2026-11-27）：个人中心内容区被侧栏挤窄（1280 视口下约 990px），
   原先把「发票类型」单列一行，七列最小宽 1010 让最后一列被裁；现在类型并到抬头第二行，
   六列合计约 806px，1280 下不横向滚动，1080 下也只是轻微滚动而不是把按钮切掉。 */
.inv-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 720px;
}
.inv-table th {
  text-align: left;
  font-weight: 500;
  font-size: 12px;
  color: var(--sent-muted);
  background: #fafbfc;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f1f3;
  white-space: nowrap;
}
.inv-table td {
  padding: 14px 12px;
  border-bottom: 1px solid #f4f5f7;
  vertical-align: middle;
  color: var(--sent-text-2);
}
.inv-table tr:last-child td {
  border-bottom: none;
}
.inv-table tbody tr:hover td {
  background: #fcfdfc;
}
.col-info { width: 210px; }
.col-money { width: 96px; }
.col-title { width: 160px; }
.col-date { width: 100px; white-space: nowrap; }
/* 开票进度列现在要同时放状态标签 + 下载链接，宽一点 */
.col-status { width: 104px; }
.col-action { width: 96px; white-space: nowrap; text-align: right; }
.inv-table th.col-action {
  text-align: right;
}

/* 开票进度：标签在上、下载在下（竖直居中，右列按钮跟着同一基线） */
.inv-status {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.inv-status__download {
  margin-right: 0;
}
/* 操作：查看发票 / 查看订单 竖排 */
.inv-ops {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}
.inv-ops .inv-link {
  margin-right: 0;
}

.inv-order {
  font-size: 12.5px;
  color: var(--sent-text);
  font-weight: 500;
}
/* 订单号本身可点进订单详情（2026-11-27）：把原来占一列的「订单详情」按钮收进订单号，
   操作列只剩 1~2 个动作，窄屏不会再被裁切。 */
.inv-order__link {
  border: none;
  background: transparent;
  padding: 0;
  font: inherit;
  color: inherit;
  cursor: pointer;
}
.inv-order__link:hover {
  color: var(--sent-primary);
  text-decoration: underline;
}
.inv-no {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sent-muted);
}
.inv-no--muted {
  opacity: 0.75;
}
/* 抬头 + 类型（2026-11-27 合并成一格，给操作列腾宽度） */
.inv-title {
  color: var(--sent-text);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.inv-type {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sent-muted);
  white-space: nowrap;
}
.inv-goods {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--sent-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360px;
}
.inv-money {
  color: var(--sent-accent-dark);
  font-size: 15px;
  font-weight: 700;
}

.inv-badge {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
}
.inv-badge--done {
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
}
.inv-badge--wait {
  color: #d46b08;
  background: #fff7e6;
}
.inv-badge--bad {
  color: #d9363e;
  background: #fff1f0;
}

.inv-link {
  border: none;
  background: transparent;
  padding: 0;
  margin-right: 12px;
  font-size: 12.5px;
  color: var(--sent-primary);
  cursor: pointer;
}
.inv-link:hover {
  text-decoration: underline;
}
.inv-link--muted {
  color: var(--sent-muted);
}

/* ---------- 空态 / 底部 ---------- */
.inv-state {
  padding: 56px 0;
  text-align: center;
  color: var(--sent-muted);
  font-size: 13px;
}
.inv-state__icon {
  font-size: 34px;
  opacity: 0.4;
  margin-bottom: 8px;
}
.inv-state p {
  margin: 0 0 4px;
  color: var(--sent-text-2);
  font-size: 14px;
}
.inv-state span {
  font-size: 12px;
}

.inv-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 14px;
  flex-wrap: wrap;
}
.inv-tip {
  font-size: 12px;
  color: var(--sent-muted);
  line-height: 1.6;
  max-width: 560px;
}
.inv-page-ctl {
  margin-left: auto;
}

@media (max-width: 720px) {
  .inv-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .inv-search {
    width: 100%;
  }
  .inv-tabs {
    gap: 16px;
  }
}
</style>
