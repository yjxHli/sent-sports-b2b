<template>
  <div>
    <h2 class="sent-page-title">库存报损</h2>
    <div class="sent-panel">
      <el-form :model="form" label-width="100px" style="max-width: 560px">
        <el-form-item label="SKU ID">
          <el-input-number v-model="form.skuId" :min="1" style="width: 220px" />
        </el-form-item>
        <el-form-item label="报损数量">
          <el-input-number v-model="form.qty" :min="1" style="width: 220px" />
        </el-form-item>
        <el-form-item label="报损原因">
          <el-input v-model="form.reason" placeholder="如：破损 / 过期 / 丢失" />
        </el-form-item>
        <el-form-item label="凭证">
          <el-upload :auto-upload="false" :limit="1" :on-change="onFileChange" :on-remove="onFileRemove">
            <el-button size="small">选择凭证文件</el-button>
            <template #tip>
              <div class="sent-hint">支持图片 / PDF；上传端点待后端补齐，当前仅暂存文件名并随备注提交</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">提交报损</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="result"
        type="warning"
        :closable="false"
        :title="`报损成功 · 实物 ${result.afterPhysicalQty} · 可用 ${result.afterAvailableQty}`"
        style="margin-top: 12px"
      />
    </div>

    <div class="sent-panel">
      <SentTable
        :columns="flowColumns"
        :rows="flows"
        :total="flowTotal"
        :page="flowPage"
        :size="flowSize"
        :loading="flowLoading"
        :selectable="deleteMode"
        row-key="flowId"
        exportable
        export-name="报损流水"
        :export-rows="exportAllFlows"
        @selection-change="selected = $event"
        @update:page="onFlowPage"
        @update:size="onFlowSize"
        @refresh="loadFlows"
      >
        <template #toolbar><strong>报损流水</strong></template>
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.flowId))"
            :summary="`已选择 ${selected.length} 条报损流水`"
            :delete-fn="(ids, config) => unwrap(stockApi.batchDeleteFlows(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #col-createdAt="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 报损（P6-31）：数量 + 原因 + 凭证暂存 + 流水回显 */
import { reactive, ref } from 'vue';
import { ElMessage, type UploadFile } from 'element-plus';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { useIdempotencyKey } from '@/utils/idempotent';
import { stockApi, type InboundVO } from '@/api';
import type { StockFlowVO } from '@sent/shared';
import SentTable from '@/components/SentTable.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import { useBatchDelete } from '@/composables/useBatchDelete';
import { useTable } from '@/composables/useTable';
import type { TableColumn } from '@/components/types';

const flowColumns: TableColumn[] = [
  { prop: 'bizNo', label: '业务单号', width: 180 },
  { prop: 'skuId', label: 'SKU ID', width: 140 },
  { prop: 'qty', label: '数量', width: 100, align: 'center' },
  { prop: 'reason', label: '原因', minWidth: 180 },
  { prop: 'createdAt', label: '时间', width: 170 },
];

const idem = useIdempotencyKey('STOCK_LOSS');
const form = reactive({ skuId: 0, qty: 1, reason: '', remark: '', proofName: '' });
const saving = ref(false);
const result = ref<InboundVO | null>(null);
const { rows: flows, total: flowTotal, loading: flowLoading,
  page: flowPage, size: flowSize, load: loadFlows,
  onPage: onFlowPage, onSize: onFlowSize, fetchAll: exportAllFlows } = useTable<StockFlowVO>({
  fetch: (params) => unwrap(stockApi.flows({ ...params, bizType: 'LOSS' })),
  errorMessage: '',
});
const { deleteMode, selected, onDeleted } = useBatchDelete(loadFlows);

function onFileChange(file: UploadFile): void {
  form.proofName = file.name;
}
function onFileRemove(): void {
  form.proofName = '';
}

async function submit(): Promise<void> {
  if (!form.skuId || !form.qty || !form.reason.trim()) {
    ElMessage.warning('请填写 SKU、数量与报损原因');
    return;
  }
  saving.value = true;
  try {
    const remark = [form.remark, form.proofName ? `凭证：${form.proofName}` : '']
      .filter(Boolean).join('；');
    idem.getOrCreate();
    result.value = await unwrap(stockApi.loss(
      { skuId: form.skuId, qty: form.qty, reason: form.reason, remark },
      { headers: idem.headers() },
    ));
    idem.clear();
    ElMessage.success('报损成功');
    await loadFlows();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '报损失败');
  } finally {
    saving.value = false;
  }
}
</script>
