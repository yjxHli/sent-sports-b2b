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
      <div class="sent-form-row">
        <strong>报损流水</strong>
        <el-button size="small" @click="loadFlows">刷新</el-button>
      </div>
      <el-table :data="flows" border size="small" v-loading="flowLoading">
        <el-table-column prop="bizNo" label="业务单号" width="180" />
        <el-table-column prop="skuId" label="SKU ID" width="150" />
        <el-table-column prop="qty" label="数量" width="100" align="center" />
        <el-table-column prop="reason" label="原因" min-width="180" />
        <el-table-column prop="createdAt" label="时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无报损流水" :image-size="60" /></template>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 报损（P6-31）：数量 + 原因 + 凭证暂存 + 流水回显 */
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, type UploadFile } from 'element-plus';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { useIdempotencyKey } from '@/utils/idempotent';
import { stockApi, type InboundVO } from '@/api';
import type { StockFlowVO } from '@sent/shared';

const idem = useIdempotencyKey('STOCK_LOSS');
const form = reactive({ skuId: 0, qty: 1, reason: '', remark: '', proofName: '' });
const saving = ref(false);
const result = ref<InboundVO | null>(null);
const flows = ref<StockFlowVO[]>([]);
const flowLoading = ref(false);

function onFileChange(file: UploadFile): void {
  form.proofName = file.name;
}
function onFileRemove(): void {
  form.proofName = '';
}

async function loadFlows(): Promise<void> {
  flowLoading.value = true;
  try {
    const data = await unwrap(stockApi.flows({ page: 1, size: 20, bizType: 'LOSS' }));
    flows.value = data?.list ?? [];
  } catch {
    flows.value = [];
  } finally {
    flowLoading.value = false;
  }
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

onMounted(() => { void loadFlows(); });
</script>
