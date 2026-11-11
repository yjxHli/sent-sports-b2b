<template>
  <div>
    <h2 class="sent-page-title">手工入库</h2>
    <div class="sent-panel">
      <el-form :model="form" label-width="100px" style="max-width: 560px">
        <el-form-item label="SKU ID">
          <el-input-number v-model="form.skuId" :min="1" style="width: 220px" />
        </el-form-item>
        <el-form-item label="入库数量">
          <el-input-number v-model="form.qty" :min="1" style="width: 220px" />
        </el-form-item>
        <el-form-item label="入库原因">
          <el-input v-model="form.reason" placeholder="如：采购到货 / 退货入库" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">提交入库</el-button>
          <span class="sent-hint" style="margin-left: 12px">
            幂等键：{{ idem.current() || '提交时生成' }}
          </span>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="result"
        type="success"
        :closable="false"
        :title="`入库成功 · 实物 ${result.afterPhysicalQty} · 可用 ${result.afterAvailableQty}`"
        style="margin-top: 12px"
      />
    </div>

    <div class="sent-panel">
      <div class="sent-form-row">
        <strong>入库流水</strong>
        <el-button size="small" @click="loadFlows">刷新</el-button>
      </div>
      <el-table :data="flows" border size="small" v-loading="flowLoading">
        <el-table-column prop="flowId" label="流水 ID" width="150" />
        <el-table-column prop="bizNo" label="业务单号" width="180" />
        <el-table-column prop="skuId" label="SKU ID" width="150" />
        <el-table-column prop="qty" label="数量" width="100" align="center" />
        <el-table-column prop="reason" label="原因" min-width="160" />
        <el-table-column prop="createdAt" label="时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无入库流水" :image-size="60" /></template>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 手工入库（P6-30）：数量 + 原因 + 流水回显；幂等键页面内复用、成功后清空 */
import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { useIdempotencyKey } from '@/utils/idempotent';
import { stockApi, type InboundVO } from '@/api';
import type { StockFlowVO } from '@sent/shared';

const route = useRoute();
const idem = useIdempotencyKey('STOCK_INBOUND');
const form = reactive({ skuId: 0, qty: 1, reason: '', remark: '' });
const saving = ref(false);
const result = ref<InboundVO | null>(null);
const flows = ref<StockFlowVO[]>([]);
const flowLoading = ref(false);

async function loadFlows(): Promise<void> {
  flowLoading.value = true;
  try {
    const data = await unwrap(stockApi.flows({ page: 1, size: 20, bizType: 'INBOUND' }));
    flows.value = data?.list ?? [];
  } catch {
    flows.value = [];
  } finally {
    flowLoading.value = false;
  }
}

async function submit(): Promise<void> {
  if (!form.skuId || !form.qty || !form.reason.trim()) {
    ElMessage.warning('请填写 SKU、数量与入库原因');
    return;
  }
  saving.value = true;
  try {
    idem.getOrCreate();
    result.value = await unwrap(stockApi.inbound(
      { skuId: form.skuId, qty: form.qty, reason: form.reason, remark: form.remark },
      { headers: idem.headers() },
    ));
    idem.clear();
    ElMessage.success('入库成功');
    await loadFlows();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '入库失败');
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  const skuId = Number(route.query.skuId);
  if (Number.isFinite(skuId) && skuId > 0) form.skuId = skuId;
  void loadFlows();
});
</script>
