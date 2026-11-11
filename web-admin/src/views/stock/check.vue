<template>
  <div>
    <h2 class="sent-page-title">库存盘点</h2>
    <div class="sent-panel">
      <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 24px">
        <el-step title="创建盘点单" />
        <el-step title="录入实盘数" />
        <el-step title="差异确认与审批" />
      </el-steps>

      <el-form :model="form" label-width="110px" style="max-width: 560px">
        <el-form-item label="SKU ID">
          <el-input-number v-model="form.skuId" :min="1" :disabled="step > 0" style="width: 220px" />
        </el-form-item>
        <el-form-item label="账面数量">
          <span>{{ bookQty }}</span>
        </el-form-item>
        <el-form-item label="实盘数量">
          <el-input-number v-model="form.actualQty" :min="0" :disabled="step < 1" style="width: 220px" />
        </el-form-item>
        <el-form-item v-if="diffQty !== null" label="差异">
          <span :class="{ 'check-diff': diffQty !== 0 }">
            {{ diffQty > 0 ? '+' : '' }}{{ diffQty }}
          </span>
        </el-form-item>
      </el-form>

      <div class="sent-form-row">
        <el-button v-if="step === 0" type="primary" :loading="busy" @click="createCheck">
          创建盘点单
        </el-button>
        <el-button v-if="step === 1" type="primary" :loading="busy" @click="count">
          录入实盘数
        </el-button>
        <el-button v-if="step === 2" type="danger" :loading="busy" @click="approve">
          确认调整并审批
        </el-button>
        <el-button @click="reset">重新开始</el-button>
      </div>
      <p class="sent-hint">
        审批需输入「确认调整」换取一次性令牌；令牌过期必须重新确认，不允许静默重试。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 盘点（P6-32）：建单 → 实盘 → 差异 → 确认令牌审批 */
import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { openConfirm } from '@/utils/confirm';
import { stockApi } from '@/api';

const route = useRoute();
const form = reactive({ skuId: 0, actualQty: 0 });
const step = ref(0);
const busy = ref(false);
const checkNo = ref('');
const bookQty = ref(0);
const diffQty = ref<number | null>(null);

async function createCheck(): Promise<void> {
  if (!form.skuId) {
    ElMessage.warning('请填写 SKU ID');
    return;
  }
  busy.value = true;
  try {
    const vo = await unwrap(stockApi.createCheck(form.skuId));
    checkNo.value = vo.checkNo;
    bookQty.value = vo.bookQty;
    step.value = 1;
    ElMessage.success(`盘点单 ${vo.checkNo} 已创建`);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '创建盘点单失败');
  } finally {
    busy.value = false;
  }
}

async function count(): Promise<void> {
  busy.value = true;
  try {
    const vo = await unwrap(stockApi.count(checkNo.value, form.actualQty));
    diffQty.value = vo.diffQty;
    step.value = 2;
    ElMessage.info(vo.diffQty === 0 ? '账实相符' : `存在差异 ${vo.diffQty}`);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '实盘数录入失败');
  } finally {
    busy.value = false;
  }
}

async function approve(): Promise<void> {
  try {
    const grant = await openConfirm({
      action: 'STOCK_CHECK_APPROVE',
      targetId: checkNo.value,
      summary: `盘点单 ${checkNo.value} · 账面 ${bookQty.value} · 实盘 ${form.actualQty} · 差异 ${diffQty.value}`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    busy.value = true;
    const vo = await unwrap(stockApi.approve(checkNo.value, grant.token));
    ElMessage.success(`审批完成，调整后实物 ${vo.afterPhysicalQty}`);
    reset();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '审批失败或已取消');
  } finally {
    busy.value = false;
  }
}

function reset(): void {
  step.value = 0;
  checkNo.value = '';
  bookQty.value = 0;
  diffQty.value = null;
  form.actualQty = 0;
}

onMounted(() => {
  const skuId = Number(route.query.skuId);
  if (Number.isFinite(skuId) && skuId > 0) form.skuId = skuId;
});
</script>

<style scoped>
.check-diff { color: #d03050; font-weight: 700; }
</style>
