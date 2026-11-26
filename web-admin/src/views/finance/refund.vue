<template>
  <div>
    <h2 class="sent-page-title">退款审核</h2>
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
        row-key="refundNo"
        exportable
        export-name="退款单"
        :operation-width="176"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.refundNo))"
            :summary="`已选择 ${selected.length} 笔退款单：${selected.map((r: any) => r.refundNo).join('、')}`"
            :delete-fn="(ids, config) => unwrap(financeApi.batchDeleteRefunds(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <el-input
            v-model="keyword"
            placeholder="退款单号 / 订单号"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
          <el-select v-model="status" placeholder="退款状态" clearable style="width: 150px">
            <el-option
              v-for="s in Object.values(RefundStatus)"
              :key="s"
              :label="statusLabel(s)"
              :value="s"
            />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </template>

        <template #col-status="{ row }"><StatusTag :status="row.status" /></template>

        <!-- 人工复核标记（V52）：定制单「审核通过未排产」，放行前须跟生产确认是否已备料 -->
        <template #col-needManualReview="{ row }">
          <el-tag v-if="row.needManualReview" type="warning" size="small" effect="dark">
            需人工复核
          </el-tag>
          <span v-else class="proof-empty">—</span>
        </template>

        <template #col-images="{ row }">
          <div v-if="row.images && row.images.length" class="proof-shots">
            <el-image
              v-for="(fid, i) in row.images.slice(0, 2)"
              :key="fid"
              :src="fileApi.url(fid)"
              :preview-src-list="row.images.map((f: string) => fileApi.url(f))"
              :initial-index="i"
              fit="cover"
              class="proof-shot"
              preview-teleported
            />
            <span class="proof-count">{{ row.images.length }} 张</span>
          </div>
          <span v-else class="proof-empty">未上传</span>
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            link
            type="success"
            :disabled="row.status !== RefundStatus.PENDING"
            @click="approve(row)"
          >
            通过
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status !== RefundStatus.PENDING"
            @click="reject(row)"
          >
            驳回
          </el-button>
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 退款审核（P6-43）：列表 / 审核均走管理端真实端点（/admin/refunds）；
 * 审核通过是危险操作，必须 openConfirm('REFUND_APPROVE') 换 X-Confirm-Token 并在提交前复查过期。
 */
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { financeApi } from '@/api';
import { fileApi } from '@/api/file';
import { RefundStatus, fmtDateTime, fmtMoney, statusLabel } from '@sent/shared';
import { openConfirm } from '@/utils/confirm';
import { useTable } from '@/composables/useTable';
import { useBatchDelete } from '@/composables/useBatchDelete';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'refundNo', label: '退款单号', minWidth: 164 },
  { prop: 'orderNo', label: '订单号', minWidth: 164 },
  { prop: 'status', label: '状态', width: 84, align: 'center' },
  { prop: 'amountCent', label: '退款金额', width: 108, align: 'right',
    formatter: (_r, v) => fmtMoney(v) },
  { prop: 'reason', label: '退款原因', minWidth: 96 },
  { prop: 'needManualReview', label: '人工复核', width: 76, align: 'center' },
  { prop: 'images', label: '凭证图片', width: 92, align: 'center' },
  { prop: 'createdAt', label: '申请时间', width: 150, formatter: (_r, v) => fmtDateTime(v) },
];

const router = useRouter();
const status = ref<string>('');
/** 退款单号 / 订单号模糊搜索（两者都匹配，客服拿到哪个号都能定位） */
const keyword = ref('');
const { rows, total, loading, page, size, load, onPage, onSize, onSearch, fetchAll } = useTable<any>({
  fetch: (params) => unwrap(financeApi.refunds({
    ...params,
    status: status.value || undefined,
    keyword: keyword.value.trim() || undefined,
  })),
  errorMessage: '退款单加载失败',
});
const { deleteMode, selected, onDeleted } = useBatchDelete(load);

/** 进入单笔独立审核页：订单信息、凭证图片、商品明细、审核动作都在那一页完成。 */
function openDetail(row: any): void {
  router.push(`/finance/refund/${String(row.refundNo)}`);
}

async function approve(row: any): Promise<void> {
  try {
    const grant = await openConfirm({
      action: 'REFUND_APPROVE',
      targetId: String(row.refundNo),
      summary: `退款单 ${row.refundNo} · 订单 ${row.orderNo} · 金额 ${fmtMoney(row.amountCent)}`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    await unwrap(financeApi.auditRefund(String(row.refundNo), { approved: true, remark: '审核通过' }, grant.token));
    ElMessage.success('退款审核已通过');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '退款审核提交失败');
  }
}

async function reject(row: any): Promise<void> {
  let remark = '';
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回退款申请', {
      inputValidator: (v: string) => (v && v.trim() ? true : '原因不能为空'),
      type: 'warning',
    });
    remark = String(value).trim();
  } catch {
    return;
  }
  try {
    await unwrap(financeApi.auditRefund(String(row.refundNo), { approved: false, remark }));
    ElMessage.success('已驳回');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '退款审核提交失败');
  }
}

</script>

<style scoped>
.proof-shots {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
}
.proof-shot {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 4px;
  border: 1px solid var(--sent-line-2);
  background: var(--sent-fill); /* 白底凭证图在纯白卡片上会"消失"，给一块浅底兜住 */
  object-fit: cover;
  cursor: zoom-in;
}
.proof-count {
  font-size: 13px;
  color: #86909c;
}
.proof-empty {
  font-size: 13px;
  color: #c9cdd4;
}
</style>
