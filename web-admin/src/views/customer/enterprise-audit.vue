<template>
  <div>
    <h2 class="sent-page-title">企业审核</h2>
    <div class="sent-panel">
      <SentTable
        :select-all-rows="fetchAll"
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :selectable="deleteMode"
        row-key="customerId"
        exportable
        export-name="企业审核列表"
        :export-rows="fetchAll"
        :operation-width="240"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.customerId))"
            :summary="`已选择 ${selected.length} 家企业的审核记录`"
            :delete-fn="(ids, config) => unwrap(financeApi.batchDeleteEnterpriseAudits(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <el-input v-model="keyword" placeholder="企业名称 / 信用代码 / 手机号 / 客户号"
                    clearable style="width: 260px" @keyup.enter="onSearch" />
          <el-select v-model="status" placeholder="审核状态" clearable style="width: 130px">
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="ACTIVE" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </template>
        <template #col-status="{ row }">
          <StatusTag :status="row.status" :label="auditLabel(row.status)" />
        </template>
        <template #col-creditLimitCent="{ row }">
          {{ fmtMoney(row.creditLimitCent) }}
        </template>
        <template #operations="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="success" :disabled="row.status === 'ACTIVE'" @click="auditRow(row, true)">通过</el-button>
          <el-button link type="danger" :disabled="row.status === 'REJECTED'" @click="auditRow(row, false)">驳回</el-button>
        </template>
      </SentTable>
    </div>

    <el-dialog v-model="detailVisible" title="企业认证资料详情" width="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="客户号">{{ detail.customerId }}</el-descriptions-item>
          <el-descriptions-item label="客户编号">{{ detail.customerNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="企业名称">{{ detail.companyName }}</el-descriptions-item>
          <el-descriptions-item label="统一社会信用代码">{{ detail.creditCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="注册手机号">{{ detail.phone }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">{{ auditLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.submittedAtMillis ? fmtDateTime(detail.submittedAtMillis) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ detail.auditedAtMillis ? fmtDateTime(detail.auditedAtMillis) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="授信额度">{{ fmtMoney(detail.creditLimitCent) }}</el-descriptions-item>
          <el-descriptions-item label="已用额度">{{ fmtMoney(detail.creditUsedCent) }}</el-descriptions-item>
          <el-descriptions-item label="审核意见" :span="2">
            <span :class="detail.status === 'REJECTED' ? 'audit-reason' : ''">
              {{ detail.auditRemark || '—' }}
            </span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="license-block">
          <h4>营业执照</h4>
          <el-image
            v-if="detail.licenseFileId"
            :src="fileApi.url(detail.licenseFileId)"
            :preview-src-list="[fileApi.url(detail.licenseFileId)]"
            :preview-teleported="true"
            fit="contain"
            class="license-img"
            data-testid="enterprise-audit-license-img"
          />
          <span v-else class="sent-hint">未上传（历史档案或线下提交）</span>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail && detail.status !== 'ACTIVE'" type="success"
                   @click="auditRow(detail, true)">审核通过</el-button>
        <el-button v-if="detail && detail.status !== 'REJECTED'" type="danger"
                   @click="auditRow(detail, false)">审核驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 企业审核（2026-11-19）：独立审核台，列表直接看基本信息，
 * 详情弹窗看营业执照与全部提交内容；驳回原因必填并落库，企业端进度页可见。
 */
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { customerApi, financeApi, type EnterpriseAuditRow } from '@/api';
import { fileApi } from '@/api/file';
import { ENTERPRISE_STATUS_LABELS, fmtDateTime, fmtMoney, statusLabel } from '@sent/shared';
import { useTable } from '@/composables/useTable';
import { useBatchDelete } from '@/composables/useBatchDelete';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'customerId', label: '客户号', width: 100 },
  { prop: 'companyName', label: '企业名称', minWidth: 170 },
  { prop: 'creditCode', label: '统一社会信用代码', minWidth: 180 },
  { prop: 'phone', label: '注册手机号', width: 130 },
  { prop: 'status', label: '审核状态', width: 100, align: 'center' },
  { prop: 'auditRemark', label: '审核意见', minWidth: 180,
    formatter: (_r, v) => String(v ?? '—') },
  { prop: 'creditLimitCent', label: '授信额度', width: 120, align: 'right' },
];

const keyword = ref('');
const status = ref('');
const { rows, total, loading, page, size, load, onPage, onSize, onSearch, fetchAll } = useTable<
  EnterpriseAuditRow
>({
  fetch: (params) => unwrap(financeApi.enterpriseAudits({
    ...params,
    keyword: keyword.value || undefined,
    status: status.value || undefined,
  })),
  errorMessage: '企业审核列表加载失败',
});
const { deleteMode, selected, onDeleted } = useBatchDelete(load);
const auditing = ref(false);
const detailVisible = ref(false);
const detail = ref<EnterpriseAuditRow | null>(null);

/** 审核台的「通过/驳回」与客户档案页共用一份企业认证字典（ACTIVE=已认证） */
function auditLabel(value?: string | null): string {
  return statusLabel(value, ENTERPRISE_STATUS_LABELS);
}

function openDetail(row: EnterpriseAuditRow): void {
  detail.value = row;
  detailVisible.value = true;
}

async function auditRow(row: EnterpriseAuditRow, approved: boolean): Promise<void> {
  let remark = '资料齐全';
  if (approved) {
    try {
      await ElMessageBox.confirm(
        `确认通过「${row.companyName}」的企业认证？`, '企业认证审核', { type: 'warning' },
      );
    } catch { return; }
  } else {
    try {
      const answered = await ElMessageBox.prompt(
        `驳回「${row.companyName}」的企业认证，请填写具体原因（企业可见）：`,
        '驳回企业认证',
        {
          inputType: 'textarea',
          inputPlaceholder: '例如：营业执照模糊，请重新上传清晰照片',
          inputValidator: (value: string) => (value && value.trim() ? true : '驳回原因不能为空'),
        },
      );
      remark = String(answered.value || '').trim();
    } catch { return; }
  }
  auditing.value = true;
  try {
    await unwrap(customerApi.auditEnterprise(Number(row.customerId), approved, remark));
    ElMessage.success(approved ? '审核已通过' : '已驳回，原因已同步给企业');
    detailVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '审核失败');
  } finally {
    auditing.value = false;
  }
}
</script>

<style scoped>
.audit-reason { color: #c9383f; }
.license-block { margin-top: 20px; }
.license-block h4 { margin: 0 0 10px; font-size: 14px; color: #33463f; }
.license-img { width: 100%; max-height: 320px; border: 1px solid #e3e8e5; border-radius: 8px; }
</style>
