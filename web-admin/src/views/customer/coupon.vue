<template>
  <div>
    <h2 class="sent-page-title">优惠券管理</h2>

    <div class="sent-panel">
      <div class="sent-form-row">
        <strong>发放优惠券</strong>
        <span class="sent-hint">
          券由「优惠券创建」建好，这里只负责发给客户；发放后客户会收到站内信提醒
        </span>
      </div>

      <el-form :model="form" label-width="110px" style="max-width: 680px">
        <el-form-item label="券面额">
          <el-select
            v-model="form.templateId"
            filterable
            placeholder="请选择券面额"
            data-testid="coupon-template"
            style="width: 360px"
          >
            <el-option
              v-for="t in templates"
              :key="t.templateId"
              :label="templateLabel(t)"
              :value="Number(t.templateId)"
            >
              <span style="float: left">{{ templateLabel(t) }}</span>
              <span
                v-if="!templateLabel(t).includes(typeText(t.couponType))"
                style="float: right; color: #909399; font-size: 12px"
              >{{ typeText(t.couponType) }}</span>
            </el-option>
          </el-select>
          <span v-if="selectedTemplate?.customerScope === 'ENTERPRISE'" class="sent-hint">
            企业专属券：全员发放时只发给企业客户
          </span>
        </el-form-item>

        <el-form-item label="使用门槛">
          <el-input-number
            v-model="thresholdYuan"
            :min="0"
            :max="99999"
            :precision="2"
            :step="1"
            data-testid="coupon-threshold"
            style="width: 180px"
          />
          <span class="sent-hint">元；留空沿用模板门槛，填 0 表示无门槛</span>
        </el-form-item>

        <el-form-item label="发放范围">
          <el-radio-group v-model="form.scope" data-testid="coupon-scope">
            <el-radio value="ALL">全部客户</el-radio>
            <el-radio value="SPECIFIED">指定客户</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.scope === 'SPECIFIED'" label="选择客户">
          <el-select
            v-model="form.customerIds"
            multiple
            filterable
            remote
            clearable
            :remote-method="searchCustomers"
            :loading="customerLoading"
            placeholder="输入姓名或手机号搜索"
            data-testid="coupon-customers"
            style="width: 360px"
          >
            <el-option
              v-for="c in customerOptions"
              :key="String(c.customerId)"
              :label="`${c.name}${c.phone ? ` · ${c.phone}` : ''}`"
              :value="Number(c.customerId)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="发放场景">
          <el-radio-group v-model="form.scene" data-testid="coupon-scene">
            <el-radio value="ACTIVITY">平台活动</el-radio>
            <el-radio value="AFTER_SALE">售后补券</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="发放原因">
          <el-input
            v-model="form.reason"
            maxlength="80"
            :placeholder="reasonPlaceholder"
            data-testid="coupon-reason"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="saving"
            data-testid="coupon-submit"
            @click="submit"
          >确认发放</el-button>
          <span class="sent-hint" style="margin-left: 12px">幂等键：{{ idem.current() || '提交时生成' }}</span>
        </el-form-item>
      </el-form>
    </div>

    <div class="sent-panel">
      <SentTable
        :select-all-rows="fetchAllIssues"
        :columns="issueColumns"
        :rows="issues"
        :total="issueTotal"
        :page="page"
        :size="size"
        :loading="loading"
        :selectable="deleteMode"
        row-key="couponId"
        exportable
        export-name="优惠券发放记录"
        :export-rows="fetchAllIssues"
        :operation-width="60"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="loadIssues"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.couponId))"
            :summary="`已选择 ${selected.length} 条发放记录：${selected.map((r: any) => r.couponNo).join('、')}`"
            :delete-fn="(ids, config) => unwrap(couponApi.batchDeleteIssues(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <strong class="panel-title">最近发放记录</strong>
          <el-input
            v-model="issueKeyword"
            placeholder="券号 / 券名称 / 客户 ID"
            clearable
            style="width: 220px"
            data-testid="coupon-issue-keyword"
            @keyup.enter="onIssueSearch"
          />
          <el-button type="primary" @click="onIssueSearch">查询</el-button>
        </template>
        <template #col-name="{ row }">
          <span>{{ row.name }}</span>
          <span class="issue-type">{{ typeText(row.couponType) }}</span>
        </template>
        <template #col-amountCent="{ row }">
          {{ row.discountType === 'PERCENT' ? rateText(row.discountRate) : `¥${fmtCent(row.amountCent)}` }}
        </template>
        <template #col-thresholdCent="{ row }">{{ thresholdText(row.thresholdCent) }}</template>
        <template #col-status="{ row }">
          <StatusTag
            :status="row.status"
            :label="statusLabel(row.status)"
            :tone="row.status === 'UNUSED' ? 'success' : 'info'"
          />
        </template>
        <template #col-source="{ row }">{{ sourceText(row.source) }}</template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 管理端「客户与会员 → 优惠券管理」（2026-11-18）。
 *
 * 只做「发放」与「发放记录」：券本身在「优惠券创建」里建（可限定 SPU/SKU），
 * 发放下拉直接读券模板接口，建好的券立刻可选。
 *
 * 发放记录对齐发票管理：分页 + 批量删除 + 导出全量（不是当前页）。
 * 删除只把 customer_coupon.deleted 置 1：券从列表与客户「我的优惠券」消失，
 * 已使用券对应的订单折扣额不改（那是历史事实）。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtCent, fmtDateTime, statusLabel } from '@sent/shared';
import type { CustomerVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { useIdempotencyKey } from '@/utils/idempotent';
import { useTable } from '@/composables/useTable';
import { useBatchDelete } from '@/composables/useBatchDelete';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import type { TableColumn } from '@/components/types';
import {
  couponApi,
  type CouponIssuePageVO,
  type CouponTemplateVO,
} from '@/api/coupon';
import { customerApi } from '@/api/customer';

const route = useRoute();
const idem = useIdempotencyKey('COUPON_ISSUE');
const templates = ref<CouponTemplateVO[]>([]);
const saving = ref(false);
const customerOptions = ref<CustomerVO[]>([]);
const customerLoading = ref(false);
const issueKeyword = ref('');
const { rows: issues, total: issueTotal, loading, page, size,
  load: loadIssues, onPage, onSize, onSearch: onIssueSearch,
  fetchAll: fetchAllIssues } = useTable<CouponIssuePageVO>({
  fetch: (params) => unwrap(couponApi.issuesPage({
    ...params,
    keyword: issueKeyword.value.trim() || undefined,
  })),
  errorMessage: '发放记录加载失败',
});
const { deleteMode, selected, onDeleted } = useBatchDelete(loadIssues);

/** 列宽按 1440 视口内一屏看全（合计 ≈1050px），否则「发放时间」会被挤出可视区 */
const issueColumns: TableColumn[] = [
  { prop: 'couponNo', label: '券号', width: 200 },
  { prop: 'name', label: '券名称', minWidth: 150 },
  { prop: 'amountCent', label: '面额 / 折扣', width: 105, align: 'right' },
  { prop: 'thresholdCent', label: '门槛', width: 90, align: 'right' },
  { prop: 'customerName', label: '客户', width: 100 },
  { prop: 'status', label: '状态', width: 78, align: 'center' },
  { prop: 'source', label: '场景', width: 85 },
  { prop: 'reason', label: '发放原因', minWidth: 90 },
  { prop: 'createdAtMillis', label: '发放时间', width: 150,
    formatter: (_row, v) => fmtDateTime(Number(v)) },
];

const form = reactive({
  templateId: 0 as number,
  scope: 'ALL' as 'ALL' | 'SPECIFIED',
  customerIds: [] as number[],
  scene: 'ACTIVITY' as 'ACTIVITY' | 'AFTER_SALE',
  reason: '',
});
/**
 * 门槛在界面上按「元」录入，提交时 ×100 转分。
 * undefined = 留空沿用模板，0 = 无门槛；不要用 0 兼任「没填」，否则运营发不出无门槛券。
 */
const thresholdYuan = ref<number | undefined>(undefined);

const reasonPlaceholder = computed(() => form.scene === 'AFTER_SALE'
  ? '默认：客服售后补偿' : '可不填，使用默认活动话术');

/** 当前选中的券模板：用于展示企业专属提示与确认弹窗里的券型描述 */
const selectedTemplate = computed<CouponTemplateVO | undefined>(() =>
  templates.value.find((t) => Number(t.templateId) === form.templateId));

function rateText(rate: number | string): string {
  const zhe = Number(rate ?? 100) / 10;
  return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`;
}
function typeText(couponType?: string): string {
  switch (couponType) {
    case 'NO_THRESHOLD':
      return '无门槛券';
    case 'FULL_REDUCTION':
      return '满减券';
    case 'CATEGORY':
      return '品类券';
    case 'ENTERPRISE':
      return '企业专属券';
    default:
      return '优惠券';
  }
}
function sourceText(source?: string): string {
  if (source === 'REGISTER') return '注册礼';
  if (source === 'AFTER_SALE') return '售后补券';
  if (source === 'CLAIM') return '领券中心';
  return '平台活动';
}
/** 90 → 9折；88 → 8.8折 */
function thresholdText(cent: number | string): string {
  const n = Number(cent ?? 0);
  return n > 0 ? `满 ${fmtCent(n)} 元` : '无门槛';
}

/**
 * 券模板名称本身已带面额（如「5元券」），不再额外拼 ¥ 前缀，
 * 避免下拉出现「¥5 · 5元券」这种金额重复；只有名称不含面额时才补金额。
 */
function templateLabel(t: CouponTemplateVO): string {
  const name = t.name || '优惠券';
  // 折扣券按「9折 / 8.8折」展示，金额券按面额；名称已含金额时不重复拼
  if (t.discountType === 'PERCENT') return name;
  // 券名里的面额是「5元券」这种整数写法，比对时用去掉 .00 的紧凑形式，展示仍走 fmtCent
  const bare = fmtCent(t.amountCent).replace(/\.00$/, '');
  return name.includes(`${bare}元`) || name.includes(`¥${bare}`)
    ? name
    : `¥${fmtCent(t.amountCent)} · ${name}`;
}
async function loadTemplates(): Promise<void> {
  try {
    templates.value = (await unwrap(couponApi.templates())) ?? [];
    // 从「优惠券创建」页点「去发放」会带 templateId 过来，直接预选
    const preset = Number(route.query.templateId ?? 0);
    if (preset > 0 && templates.value.some((t) => Number(t.templateId) === preset)) {
      form.templateId = preset;
    } else if (!form.templateId && templates.value.length) {
      form.templateId = Number(templates.value[0].templateId);
    }
  } catch {
    templates.value = [];
  }
}


async function searchCustomers(keyword: string): Promise<void> {
  customerLoading.value = true;
  try {
    const data = await unwrap(customerApi.list({
      page: 1,
      size: 20,
      keyword: keyword.trim() || undefined,
    }));
    customerOptions.value = data?.list ?? [];
  } catch {
    customerOptions.value = [];
  } finally {
    customerLoading.value = false;
  }
}

async function submit(): Promise<void> {
  if (!form.templateId) {
    ElMessage.warning('请选择券面额');
    return;
  }
  if (form.scope === 'SPECIFIED' && form.customerIds.length === 0) {
    ElMessage.warning('请至少选择一位客户');
    return;
  }
  const template = selectedTemplate.value;
  const amountText = !template
    ? '该券'
    : template.discountType === 'PERCENT'
      ? `${rateText(template.discountRate)}（${typeText(template.couponType)}）`
      : `¥${fmtCent(template.amountCent)}（${typeText(template.couponType)}）`;
  const targetText = form.scope === 'ALL'
    ? (template?.customerScope === 'ENTERPRISE' ? '全部企业客户' : '全部客户')
    : `${form.customerIds.length} 位指定客户`;
  try {
    await ElMessageBox.confirm(
      `将向${targetText}发放 ${amountText} 优惠券，确认继续？`,
      '确认发放优惠券',
      { type: 'warning', confirmButtonText: '确认发放', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }

  idem.getOrCreate();
  saving.value = true;
  try {
    const count = await unwrap(couponApi.issue({
      templateId: form.templateId,
      customerIds: form.scope === 'ALL' ? [] : form.customerIds,
      thresholdCent: thresholdYuan.value === undefined
        ? null : Math.round(Number(thresholdYuan.value) * 100),
      reason: form.reason.trim() || undefined,
      scene: form.scene,
    }, { headers: idem.headers() }));
    idem.clear();
    ElMessage.success(`已发放 ${Number(count ?? 0)} 张优惠券`);
    form.reason = '';
    page.value = 1;
    await loadIssues();
  } catch (e) {
    ElMessage.error((e as Error).message || '发放失败');
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadTemplates(), loadIssues()]);
  await searchCustomers('');
});
</script>

<style scoped>
.panel-title { font-size: 14px; color: var(--sent-text, #303133); }
.issue-type {
  margin-left: 8px;
  font-size: 12px;
  color: var(--sent-muted, #909399);
}
</style>
