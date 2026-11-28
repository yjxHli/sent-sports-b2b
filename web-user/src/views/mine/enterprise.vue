<template>
  <div class="ent-page">
    <div v-if="loading" class="ent-state">加载中…</div>

    <!-- 个人账户 / 审核中 / 已驳回：统一先过资质关 -->
    <section v-else-if="!isActive" class="ent-gate" data-testid="enterprise-gate">
      <div class="ent-gate__icon">{{ gateIcon }}</div>
      <h3 class="ent-gate__title">{{ gateTitle }}</h3>
      <p class="ent-gate__desc">{{ gateDesc }}</p>

      <p v-if="data?.status === 'REJECTED' && data?.auditRemark" class="ent-gate__reason">
        驳回原因：{{ data.auditRemark }}
      </p>

      <ul v-if="!data || data.status === 'NONE'" class="ent-gate__perks">
        <li><b>企业价</b><span>合同价 / 企业专享折扣</span></li>
        <li><b>授信账期</b><span>先采后付，账期内结算</span></li>
        <li><b>合同与对公</b><span>年度合同、对公转账</span></li>
      </ul>

      <div class="ent-gate__actions">
        <button
          v-if="data?.status !== 'PENDING'"
          type="button"
          class="ent-btn ent-btn--primary"
          data-testid="enterprise-apply"
          @click="goApply"
        >去申请企业认证</button>
        <router-link
          v-else
          to="/enterprise/audit-progress"
          class="ent-btn ent-btn--ghost"
          data-testid="enterprise-progress"
        >查看审核进度</router-link>
        <router-link to="/products" class="ent-btn ent-btn--ghost">先去逛逛</router-link>
      </div>

      <p class="ent-gate__hint">
        企业认证需提交营业执照、企业名称与统一社会信用代码，审核通过后即可使用企业采购、授信、先采后付与合同。
      </p>
    </section>

    <template v-else>
      <!-- ========== 我的合同（对齐京东采购工作台的「筛选条 + 表格 + 分页」） ========== -->
      <template v-if="tool === 'contracts'">
        <header class="ent-head">
          <div class="ent-head__main">
            <h2 class="ent-head__title">我的合同</h2>
            <span class="ent-head__hint">仅展示已签署生效的采购合同，下单时按合同价自动结算</span>
          </div>
          <button type="button" class="ent-btn ent-btn--outline" @click="go('/products')">
            查看专属采购工作台
          </button>
        </header>

        <section class="ent-filter" data-testid="contract-filter">
          <div class="ent-filter__row">
            <label class="ent-field ent-field--wide">
              <span class="ent-field__label">合同有效期</span>
              <span class="ent-range">
                <input v-model="filters.expireFrom" type="date" class="ent-input" aria-label="开始日期" />
                <i class="ent-range__sep">—</i>
                <input v-model="filters.expireTo" type="date" class="ent-input" aria-label="结束日期" />
              </span>
            </label>
            <label class="ent-field">
              <span class="ent-field__label">合同状态</span>
              <select v-model="filters.status" class="ent-input" aria-label="合同状态">
                <option value="">全部状态</option>
                <option value="ACTIVE">生效中</option>
                <option value="EXPIRED">已到期</option>
                <option value="TERMINATED">已终止</option>
              </select>
            </label>
            <label class="ent-field">
              <span class="ent-field__label">合同编号</span>
              <input
                v-model="filters.contractNo"
                class="ent-input"
                placeholder="请输入合同编号"
                @keyup.enter="applyFilters"
              />
            </label>
            <div class="ent-filter__actions">
              <button type="button" class="ent-btn ent-btn--ghost" @click="resetFilters">重置</button>
              <button type="button" class="ent-btn ent-btn--primary" data-testid="contract-search" @click="applyFilters">查询</button>
            </div>
          </div>
        </section>

        <section class="ent-table-card">
          <!-- 空态也保留表头：看起来才像工作台的表格，而不是没搭完 -->
          <div class="ent-table-wrap">
            <table class="ent-table ent-table--contract">
              <thead>
                <tr>
                  <th class="col-no">合同编号</th>
                  <th class="col-money">合同金额</th>
                  <th class="col-rate">折扣率</th>
                  <th class="col-date">签订日期</th>
                  <th class="col-date">到期日期</th>
                  <th class="col-status">状态</th>
                </tr>
              </thead>
              <tbody v-if="pagedContracts.length > 0">
                <tr
                  v-for="(c, index) in pagedContracts"
                  :key="c.contractNo || index"
                  data-testid="enterprise-contract-row"
                >
                  <td class="col-no">{{ c.contractNo || '—' }}</td>
                  <td class="col-money"><b class="ent-money">¥{{ c.amountYuan || fmtCent(Number(c.amountCent ?? 0)) }}</b></td>
                  <td class="col-rate">{{ contractDiscount(c.discountRate) }}</td>
                  <td class="col-date">{{ c.signDate || '—' }}</td>
                  <td class="col-date">{{ c.expireDate || '—' }}</td>
                  <td class="col-status">
                    <span class="ent-status" :class="contractClass(c.status)">{{ contractText(c.status) }}</span>
                  </td>
                </tr>
              </tbody>
              <tbody v-else>
                <tr class="ent-empty-row">
                  <td colspan="6">
                    <div class="ent-empty">
                      <p>没有数据</p>
                      <span>{{ contracts.length === 0 ? '合同签订后会同步到这里，采购时自动享受合同价' : '换个筛选条件试试' }}</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <footer v-if="filteredContracts.length > 0" class="ent-pager">
            <span class="ent-pager__total">共 {{ filteredContracts.length }} 份合同</span>
            <button
              type="button"
              class="ent-pager__btn"
              :disabled="page <= 1"
              @click="page -= 1"
            >上一页</button>
            <b class="ent-pager__now">{{ page }}</b>
            <span class="ent-pager__all">/ {{ pageCount }}</span>
            <button
              type="button"
              class="ent-pager__btn"
              :disabled="page >= pageCount"
              @click="page += 1"
            >下一页</button>
          </footer>
        </section>
      </template>

      <!-- ========== 我的授信 ========== -->
      <template v-else-if="tool === 'credit'">
        <header class="ent-head">
          <div class="ent-head__main">
            <h2 class="ent-head__title">我的授信</h2>
            <span class="ent-head__hint">授信用于先采后付下单，订单形成应收后占用额度，回款核销后自动释放</span>
          </div>
          <button type="button" class="ent-btn ent-btn--outline" @click="go('/mine/enterprise/pay-later')">
            去先采后付
          </button>
        </header>

        <section class="ent-stats">
          <div class="ent-stat">
            <span>授信总额</span>
            <b>¥{{ fmtCent(Number(data?.creditLimitCent ?? 0)) }}</b>
          </div>
          <div class="ent-stat">
            <span>已用额度</span>
            <b>¥{{ fmtCent(Number(data?.creditUsedCent ?? 0)) }}</b>
          </div>
          <div class="ent-stat ent-stat--accent">
            <span>可用额度</span>
            <b>¥{{ fmtCent(Number(data?.creditAvailableCent ?? 0)) }}</b>
          </div>
        </section>

        <section class="ent-table-card">
          <h3 class="ent-card-title">企业信息</h3>
          <div class="ent-table-wrap">
            <table class="ent-table ent-table--kv">
              <tbody>
                <tr>
                  <th>企业名称</th><td>{{ data?.companyName || '—' }}</td>
                  <th>认证状态</th>
                  <td><span class="ent-status ent-status--ok">已认证</span></td>
                </tr>
                <tr>
                  <th>统一社会信用代码</th><td>{{ data?.creditCode || '—' }}</td>
                  <th>账期</th><td>{{ data?.paymentTermDays || 0 }} 天</td>
                </tr>
                <tr>
                  <th>授信到期</th><td>{{ fmtDate(data?.creditExpireAtMillis) }}</td>
                  <th>可用额度</th>
                  <td><b class="ent-money">¥{{ fmtCent(Number(data?.creditAvailableCent ?? 0)) }}</b></td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="ent-note">
            可用额度为 0 时请先完成回款；额度上限由平台按企业资质核定，如需调整请联系客户经理。
          </p>
        </section>
      </template>

      <!-- ========== 先采后付 ========== -->
      <template v-else-if="tool === 'pay-later'">
        <header class="ent-head">
          <div class="ent-head__main">
            <h2 class="ent-head__title">先采后付</h2>
            <span class="ent-head__hint">下单占用授信、账期内回款，适合有固定采购周期的企业客户</span>
          </div>
          <button type="button" class="ent-btn ent-btn--outline" @click="go('/mine/enterprise/credit')">
            查看授信明细
          </button>
        </header>

        <section class="ent-credit-hero">
          <div class="ent-credit-hero__label">当前可用额度</div>
          <div class="ent-credit-hero__num">¥{{ fmtCent(Number(data?.creditAvailableCent ?? 0)) }}</div>
          <div class="ent-credit-hero__sub">
            账期 {{ data?.paymentTermDays || 0 }} 天 · 授信到期 {{ fmtDate(data?.creditExpireAtMillis) }}
          </div>
        </section>

        <section class="ent-table-card">
          <h3 class="ent-card-title">结算规则</h3>
          <div class="ent-table-wrap">
            <table class="ent-table ent-table--kv">
              <tbody>
                <tr>
                  <th>授信总额</th><td>¥{{ fmtCent(Number(data?.creditLimitCent ?? 0)) }}</td>
                  <th>已用额度</th><td>¥{{ fmtCent(Number(data?.creditUsedCent ?? 0)) }}</td>
                </tr>
                <tr>
                  <th>账期天数</th><td>{{ data?.paymentTermDays || 0 }} 天</td>
                  <th>授信到期</th><td>{{ fmtDate(data?.creditExpireAtMillis) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <ol class="ent-steps">
            <li><b>1</b><span>下单时选择先采后付，占用授信额度</span></li>
            <li><b>2</b><span>订单完成形成应收，按账期结算</span></li>
            <li><b>3</b><span>回款核销后额度自动释放</span></li>
          </ol>

          <div class="ent-actions">
            <router-link to="/products" class="ent-btn ent-btn--primary">去下单</router-link>
          </div>
        </section>
      </template>

      <!-- ========== 企业采购（默认） ========== -->
      <template v-else>
        <header class="ent-head">
          <div class="ent-head__main">
            <h2 class="ent-head__title">企业采购</h2>
            <span class="ent-head__hint">
              {{ data?.companyName || '您的企业' }} 已通过企业认证，采购时自动按合同价 / 企业价结算
            </span>
          </div>
          <button type="button" class="ent-btn ent-btn--outline" @click="go('/products')">
            进入商品列表
          </button>
        </header>

        <section class="ent-tiles">
          <button class="ent-tile" type="button" @click="go('/mine/enterprise/credit')">
            <span class="ent-tile__icon">💳</span>
            <b>我的授信</b>
            <small>可用 ¥{{ fmtCent(Number(data?.creditAvailableCent ?? 0)) }}</small>
          </button>
          <button class="ent-tile" type="button" @click="go('/mine/enterprise/pay-later')">
            <span class="ent-tile__icon">⏳</span>
            <b>先采后付</b>
            <small>账期 {{ data?.paymentTermDays || 0 }} 天</small>
          </button>
          <button class="ent-tile" type="button" @click="go('/mine/enterprise/contracts')">
            <span class="ent-tile__icon">📄</span>
            <b>我的合同</b>
            <small>{{ contracts.length }} 份合同</small>
          </button>
        </section>

        <!-- 2026-11-28：首页用户卡底部的企业采购卡挪到这里，点击新开团购专区 -->
        <section class="ent-groupbuy">
          <div>
            <b>企业采购</b>
            <small>专票 · 账期 · 阶梯价</small>
          </div>
          <router-link to="/group-buy" target="_blank" rel="noopener" class="ent-btn ent-btn--primary">
            查看团购专区
          </router-link>
        </section>

        <section class="ent-table-card">
          <h3 class="ent-card-title">企业身份</h3>
          <div class="ent-table-wrap">
            <table class="ent-table ent-table--kv">
              <tbody>
                <tr>
                  <th>企业名称</th><td>{{ data?.companyName || '—' }}</td>
                  <th>统一社会信用代码</th><td>{{ data?.creditCode || '—' }}</td>
                </tr>
                <tr>
                  <th>账期</th><td>{{ data?.paymentTermDays || 0 }} 天</td>
                  <th>认证状态</th>
                  <td><span class="ent-status ent-status--ok">已认证</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 企业工具（个人中心，2026-11-27；2026-11-28 按京东采购工作台重构）。
 *
 * 四个入口共用一页，靠路由 meta.tool 区分：企业采购 / 我的授信 / 先采后付 / 我的合同。
 * 数据来自 `GET /api/me/enterprise`：个人客户返回 `status=NONE`，
 * 页面展示「资质不够 + 去申请企业认证」，跳转企业认证注册页提交营业执照等材料。
 *
 * 版式口径（对齐京东企业采购工作台）：页头（标题 + 说明 + 右侧描边按钮）
 * → 筛选条（条件 + 重置 / 查询）→ 表格（浅灰表头、无竖线）→ 底部计数与翻页。
 * 合同筛选与分页都在本地做：`/api/me/enterprise` 一次把合同全量带回来（企业合同量级个位数），
 * 不为筛选单独加后端参数，避免「翻页靠后端、筛选靠前端」的两套口径打架。
 *
 * 审核中（PENDING）不给申请入口，只引导查看进度；驳回（REJECTED）展示原因并允许重新提交。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtCent, fmtDate } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { enterpriseApi, type EnterpriseContractVO, type EnterpriseOverviewVO } from '@/api/enterprise';

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const data = ref<EnterpriseOverviewVO | null>(null);

const tool = computed(() => (route.meta.tool as string | undefined) || 'procurement');
const isActive = computed(() => data.value?.status === 'ACTIVE');
const contracts = computed<EnterpriseContractVO[]>(() => data.value?.contracts ?? []);

/* ---------- 合同筛选 + 本地分页 ---------- */
const PAGE_SIZE = 10;
const filters = reactive({ contractNo: '', status: '', expireFrom: '', expireTo: '' });
const applied = reactive({ contractNo: '', status: '', expireFrom: '', expireTo: '' });
const page = ref(1);

/** 合同状态缺省按「生效中」算（后端未回状态时视作正常在生效） */
function statusOf(c: EnterpriseContractVO): string {
  return (c.status || 'ACTIVE').toUpperCase();
}

const filteredContracts = computed(() =>
  contracts.value.filter((c) => {
    if (applied.contractNo && !String(c.contractNo ?? '').toLowerCase()
      .includes(applied.contractNo.toLowerCase())) return false;
    if (applied.status && statusOf(c) !== applied.status) return false;
    // 日期是 'YYYY-MM-DD' 字符串，字典序即时间序，直接比较不需要 new Date
    const expire = c.expireDate ?? '';
    if (applied.expireFrom && expire && expire < applied.expireFrom) return false;
    if (applied.expireTo && expire && expire > applied.expireTo) return false;
    return true;
  }),
);

const pageCount = computed(() => Math.max(1, Math.ceil(filteredContracts.value.length / PAGE_SIZE)));
const pagedContracts = computed(() =>
  filteredContracts.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
);

/** 筛选条件变化时回到第 1 页，否则会停在一个已经不存在的页码上看到空表 */
watch(filteredContracts, () => {
  if (page.value > pageCount.value) page.value = pageCount.value;
});

function applyFilters(): void {
  applied.contractNo = filters.contractNo.trim();
  applied.status = filters.status;
  applied.expireFrom = filters.expireFrom;
  applied.expireTo = filters.expireTo;
  page.value = 1;
}

function resetFilters(): void {
  filters.contractNo = '';
  filters.status = '';
  filters.expireFrom = '';
  filters.expireTo = '';
  applyFilters();
}

const gateIcon = computed(() => {
  if (data.value?.status === 'PENDING') return '⏳';
  if (data.value?.status === 'REJECTED') return '⚠️';
  return '🔒';
});
const gateTitle = computed(() => {
  if (data.value?.status === 'PENDING') return '企业认证审核中';
  if (data.value?.status === 'REJECTED') return '企业认证未通过';
  return '该功能需要企业资质';
});
const gateDesc = computed(() => {
  if (data.value?.status === 'PENDING') {
    return '资料已提交，管理端审核通过后即可使用企业采购、授信、先采后付与合同。';
  }
  if (data.value?.status === 'REJECTED') {
    return '企业认证未通过，请按驳回原因补充材料后重新提交。';
  }
  return '当前账号是个人账户，企业采购、我的授信、先采后付与我的合同仅对企业认证客户开放。';
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    data.value = await unwrap(enterpriseApi.overview());
  } catch (e) {
    data.value = null;
    ElMessage.error((e as Error).message || '企业信息加载失败');
  } finally {
    loading.value = false;
  }
}

function goApply(): void {
  router.push('/enterprise/register');
}

function go(path: string): void {
  router.push(path);
}

function contractText(status?: string | null): string {
  switch (status) {
    case 'EXPIRED': return '已到期';
    case 'TERMINATED': return '已终止';
    default: return '生效中';
  }
}

function contractClass(status?: string | null): string {
  if (status === 'EXPIRED' || status === 'TERMINATED') return 'ent-status--bad';
  return 'ent-status--ok';
}

function contractDiscount(rate?: number | null): string {
  const value = Number(rate ?? 100);
  if (!value || value >= 100) return '无折扣';
  return `${(value / 10).toFixed(1)} 折`;
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.ent-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ent-state {
  background: #fff;
  border-radius: 8px;
  padding: 48px 0;
  text-align: center;
  color: var(--sent-muted);
  font-size: 13px;
}

/* ---------- 页头（标题 + 说明 + 右侧描边按钮） ---------- */
.ent-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
}
.ent-head__main {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}
.ent-head__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--sent-text);
}
.ent-head__hint {
  font-size: 12.5px;
  color: var(--sent-muted);
  line-height: 1.6;
}

/* ---------- 资质关 ---------- */
.ent-gate {
  background: #fff;
  border-radius: 8px;
  padding: 48px 28px 40px;
  text-align: center;
}
.ent-gate__icon {
  font-size: 42px;
  line-height: 1;
  margin-bottom: 12px;
}
.ent-gate__title {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--sent-text);
}
.ent-gate__desc {
  margin: 0 auto 6px;
  max-width: 560px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--sent-text-2);
}
.ent-gate__reason {
  display: inline-block;
  margin: 8px 0 0;
  padding: 6px 12px;
  border-radius: 6px;
  background: #fff1f0;
  color: #d9363e;
  font-size: 12.5px;
}
.ent-gate__perks {
  list-style: none;
  padding: 0;
  margin: 22px auto 4px;
  max-width: 720px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.ent-gate__perks li {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #fafbfc;
  text-align: left;
}
.ent-gate__perks b {
  font-size: 13px;
  color: var(--sent-text);
}
.ent-gate__perks span {
  font-size: 12px;
  color: var(--sent-muted);
}
.ent-gate__actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin: 20px 0 14px;
  flex-wrap: wrap;
}
.ent-gate__hint {
  margin: 0;
  font-size: 12px;
  color: var(--sent-muted);
}

/* 按钮：主操作沿用站点强调橙，次操作描边 */
.ent-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 18px;
  border-radius: 4px;
  font-size: 13px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.ent-btn--primary {
  background: var(--sent-accent);
  border-color: var(--sent-accent);
  color: #fff;
}
.ent-btn--primary:hover {
  background: var(--sent-accent-dark);
  border-color: var(--sent-accent-dark);
}
.ent-btn--ghost {
  background: #fff;
  border-color: var(--sent-border);
  color: var(--sent-text-2);
}
.ent-btn--ghost:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}
/* 页头右侧那个「查看专属采购工作台」：橙色描边、透明底（对齐参考图） */
.ent-btn--outline {
  background: #fff;
  border-color: #ffb27a;
  color: var(--sent-accent-dark);
}
.ent-btn--outline:hover {
  background: var(--sent-accent-soft);
  border-color: var(--sent-accent);
}

/* ---------- 筛选条 ---------- */
.ent-filter {
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px;
}
.ent-filter__row {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 12px 18px;
  align-items: end;
}
.ent-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.ent-field__label {
  font-size: 12.5px;
  color: var(--sent-text-2);
  font-weight: 500;
}
.ent-input {
  height: 32px;
  width: 100%;
  min-width: 0;
  padding: 0 10px;
  border: 1px solid var(--sent-border);
  border-radius: 4px;
  background: #fff;
  font-size: 13px;
  color: var(--sent-text);
  outline: none;
}
.ent-input:focus {
  border-color: var(--sent-primary);
}
.ent-input::placeholder {
  color: #b8bfc9;
}
.ent-range {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ent-range__sep {
  font-style: normal;
  color: var(--sent-muted);
  font-size: 12px;
}
.ent-filter__actions {
  display: flex;
  gap: 10px;
}

/* ---------- 表格卡 ---------- */
.ent-table-card {
  background: #fff;
  border-radius: 8px;
  padding: 4px 18px 16px;
}
.ent-card-title {
  margin: 16px 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--sent-text);
}
.ent-table-wrap {
  overflow-x: auto;
}
.ent-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 640px;
}
.ent-table--contract {
  /* 固定列宽：编号列吸收剩余宽度，金额/折扣率/日期不会被挤成一坨 */
  table-layout: fixed;
}
.ent-table th {
  text-align: left;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--sent-muted);
  background: #fafbfc;
  padding: 11px 12px;
  border-bottom: 1px solid #f0f1f3;
  white-space: nowrap;
}
.ent-table td {
  padding: 14px 12px;
  border-bottom: 1px solid #f4f5f7;
  color: var(--sent-text-2);
  vertical-align: middle;
}
.ent-table tbody tr:last-child td {
  border-bottom: none;
}
.ent-table tbody tr:hover td {
  background: #fcfdfc;
}
.col-no { min-width: 160px; }
.col-money { width: 120px; }
.col-rate { width: 90px; }
.col-date { width: 110px; white-space: nowrap; }
.col-status { width: 90px; }
.ent-money {
  color: var(--sent-accent-dark);
  font-weight: 700;
}

/* 键值表（企业信息 / 结算规则）：无表头，左侧是字段名 */
.ent-table--kv th {
  width: 130px;
  background: transparent;
  color: var(--sent-muted);
  font-weight: 400;
  border-bottom: 1px dashed #f0f1f3;
}
.ent-table--kv td {
  border-bottom: 1px dashed #f0f1f3;
  color: var(--sent-text);
  word-break: break-all;
}
.ent-table--kv tbody tr:hover td {
  background: transparent;
}

.ent-status {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 8px;
  font-size: 12px;
  white-space: nowrap;
}
.ent-status--ok {
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
}
.ent-status--bad {
  color: #d9363e;
  background: #fff1f0;
}

/* ---------- 分页 ---------- */
.ent-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding-top: 16px;
  font-size: 12.5px;
  color: var(--sent-muted);
}
.ent-pager__total {
  margin-right: auto;
}
.ent-pager__btn {
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--sent-border);
  border-radius: 4px;
  background: #fff;
  color: var(--sent-text-2);
  font-size: 12.5px;
  cursor: pointer;
}
.ent-pager__btn:hover:not(:disabled) {
  border-color: var(--sent-accent);
  color: var(--sent-accent-dark);
}
.ent-pager__btn:disabled {
  color: #c8ccd2;
  cursor: not-allowed;
}
.ent-pager__now {
  min-width: 26px;
  height: 26px;
  border-radius: 4px;
  background: var(--sent-accent);
  color: #fff;
  font-size: 12.5px;
  line-height: 26px;
  text-align: center;
}

/* ---------- 授信概览 ---------- */
.ent-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.ent-stat {
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ent-stat span {
  font-size: 12.5px;
  color: var(--sent-muted);
}
.ent-stat b {
  font-size: 24px;
  color: var(--sent-text);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.ent-stat--accent {
  background: var(--sent-primary-soft);
}
.ent-stat--accent b {
  color: var(--sent-primary);
}

.ent-note {
  margin: 14px 0 2px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--sent-muted);
}

/* ---------- 先采后付 ---------- */
.ent-credit-hero {
  padding: 22px 24px;
  border-radius: 8px;
  background: linear-gradient(120deg, #12664f 0%, #1a8a6a 100%);
  color: #fff;
}
.ent-credit-hero__label {
  font-size: 12px;
  opacity: 0.85;
}
.ent-credit-hero__num {
  margin: 6px 0 4px;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.ent-credit-hero__sub {
  font-size: 12px;
  opacity: 0.88;
}
.ent-steps {
  list-style: none;
  padding: 0;
  margin: 18px 0 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.ent-steps li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: #fafbfc;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 13px;
  color: var(--sent-text-2);
  line-height: 1.6;
}
.ent-steps b {
  flex: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-size: 12px;
}
.ent-actions {
  display: flex;
  gap: 12px;
  margin-top: 18px;
  flex-wrap: wrap;
}

/* ---------- 企业采购入口 ---------- */
.ent-groupbuy {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  background: var(--sent-primary-soft);
  border-radius: 8px;
  padding: 12px 16px;
}
.ent-groupbuy div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ent-groupbuy b {
  font-size: 14px;
  color: var(--sent-text);
}
.ent-groupbuy small {
  font-size: 12px;
  color: var(--sent-text-2);
}
.ent-tiles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.ent-tile {
  background: #fff;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 18px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all 0.15s;
}
.ent-tile:hover {
  border-color: #ffd3b3;
  background: #fffaf5;
}
.ent-tile__icon {
  font-size: 20px;
}
.ent-tile b {
  font-size: 14px;
  color: var(--sent-text);
}
.ent-tile small {
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 空态 ---------- */
.ent-empty {
  padding: 52px 0 40px;
  text-align: center;
  color: var(--sent-muted);
  font-size: 13px;
}
.ent-table tbody tr.ent-empty-row:hover td {
  background: transparent;
}
.ent-table .ent-empty-row td {
  border-bottom: none;
}
.ent-empty p {
  margin: 0 0 4px;
  color: var(--sent-text-2);
  font-size: 14px;
}
.ent-empty span {
  font-size: 12px;
}

@media (max-width: 980px) {
  .ent-filter__row {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
  .ent-filter__actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}
@media (max-width: 820px) {
  .ent-stats,
  .ent-tiles,
  .ent-steps,
  .ent-gate__perks {
    grid-template-columns: minmax(0, 1fr);
  }
  .ent-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
