<template>
  <div>
    <div class="sent-bar"><strong>我的售后</strong></div>

    <div class="refund-filter">
      <span
        v-for="f in filters"
        :key="f.value"
        class="refund-filter__item"
        :class="{ active: active === f.value }"
        @click="select(f.value)"
      >{{ f.label }}</span>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="暂无售后记录" />

    <div
      v-for="r in list"
      :key="r.refundNo"
      class="sent-card refund-card"
      @click="goDetail(r.refundNo)"
    >
      <div class="refund-card__head">
        <span class="refund-card__no">{{ r.refundNo }}</span>
        <el-tag :type="tone(r.status)" size="small">{{ label(r.status) }}</el-tag>
      </div>
      <div class="refund-card__sub">订单号 {{ r.orderNo }}</div>
      <div class="refund-card__foot">
        <span class="sent-price">退款 ¥{{ r.amountYuan || fmtCent(r.amountCent) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { fmtCent, statusLabel, statusTone, type StatusTone } from '@sent/shared';
import { refundApi, type RefundVO } from '@/api/refund';

const router = useRouter();
const list = ref<RefundVO[]>([]);
const loading = ref(false);
const active = ref<string>('');

const filters = [
  { label: '全部', value: '' },
  { label: '待审核', value: 'PENDING' },
  { label: '退款中', value: 'REFUNDING' },
  { label: '已退款', value: 'REFUNDED' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '退款失败', value: 'FAILED' },
];

// 退款状态文案 / 配色（FAILED 在共享映射里是「支付失败」，此处按售后语义覆盖为「退款失败」）
const REFUND_LABELS: Record<string, string> = {
  PENDING: '待审核', REFUNDING: '退款中', REFUNDED: '已退款', CLOSED: '已关闭', FAILED: '退款失败',
};
const REFUND_TONES: Record<string, StatusTone> = {
  PENDING: 'warning', REFUNDING: 'warning', REFUNDED: 'success', CLOSED: 'info', FAILED: 'danger',
};

function label(s: string | null | undefined): string {
  return (s && REFUND_LABELS[s]) || statusLabel(s) || '-';
}
function tone(s: string | null | undefined): StatusTone {
  return (s && REFUND_TONES[s]) || statusTone(s);
}
function goDetail(no: string): void {
  router.push(`/refund/${no}`);
}

async function select(value: string): Promise<void> {
  active.value = value;
  await load();
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const query = active.value
      ? { page: 1, size: 50, status: active.value }
      : { page: 1, size: 50 };
    const res = await unwrap(refundApi.list(query));
    list.value = res.list ?? [];
  } catch (e) {
    ElMessage.error((e as Error).message || '加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.refund-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px 0;
}
.refund-filter__item {
  font-size: 12px;
  color: #606266;
  padding: 3px 10px;
  border-radius: 14px;
  background: #f4f4f5;
  cursor: pointer;
}
.refund-filter__item.active {
  color: #fff;
  background: #12664f;
}
.refund-card {
  cursor: pointer;
}
.refund-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.refund-card__no {
  font-size: 13px;
  color: #555;
}
.refund-card__sub {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.refund-card__foot {
  font-size: 14px;
}
</style>
