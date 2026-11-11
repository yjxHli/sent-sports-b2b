<template>
  <div>
    <div class="sent-bar">
      <el-button text @click="goBack">‹ 返回</el-button>
      <strong>售后详情</strong>
    </div>

    <div v-if="loading" class="detail-hint">加载中…</div>
    <el-empty v-else-if="!detail" description="售后单不存在" />

    <template v-else>
      <div class="sent-card">
        <div class="row"><span>退款单号</span><span>{{ detail.refundNo }}</span></div>
        <div class="row"><span>订单号</span><span>{{ detail.orderNo }}</span></div>
        <div class="row">
          <span>状态</span>
          <el-tag :type="tone(detail.status)" size="small">{{ label(detail.status) }}</el-tag>
        </div>
        <div class="row"><span>类型</span><span>{{ typeLabel(detail.type) }}</span></div>
        <div class="row"><span>申请人</span><span>{{ applicantLabel(detail.applicantType) }}</span></div>
      </div>

      <div class="sent-card">
        <div class="sent-title">金额</div>
        <div class="row"><span>退款金额</span><span class="sent-price">¥{{ detail.amountYuan || fmtCent(detail.amountCent) }}</span></div>
        <div class="row"><span>申诉次数</span><span>{{ detail.appealCount ?? 0 }}</span></div>
      </div>

      <div class="sent-card">
        <div class="sent-title">明细</div>
        <div class="row"><span>退款原因</span><span class="row__reason">{{ detail.reason || '—' }}</span></div>
        <div class="row"><span>申请时间</span><span>{{ fmtDateTime(detail.createdAtMillis) }}</span></div>
        <div class="row" v-if="detail.refundedAtMillis">
          <span>退款完成</span><span>{{ fmtDateTime(detail.refundedAtMillis) }}</span>
        </div>
      </div>

      <div class="detail-ops" v-if="detail.status === 'REFUNDED' || detail.status === 'CLOSED' || detail.status === 'FAILED'">
        <el-button @click="goList">查看我的售后</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { unwrap } from '@/utils/http';
import { fmtCent, fmtDateTime, statusLabel, statusTone, type StatusTone } from '@sent/shared';
import { refundApi, type RefundDetailVO } from '@/api/refund';

const route = useRoute();
const router = useRouter();

const detail = ref<RefundDetailVO | null>(null);
const loading = ref(false);

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
function typeLabel(t: string | null | undefined): string {
  switch (t) {
    case 'REFUND': return '仅退款';
    case 'ONLY_REFUND': return '迟到支付自动退款';
    case 'RETURN': return '退货退款';
    default: return t || '-';
  }
}
function applicantLabel(a: string | null | undefined): string {
  switch (a) {
    case 'CUSTOMER': return '用户申请';
    case 'STAFF': return '系统/客服';
    default: return a || '-';
  }
}
function goBack(): void { router.back(); }
function goList(): void { router.push('/refunds'); }

async function load(): Promise<void> {
  loading.value = true;
  try {
    detail.value = await unwrap(refundApi.detail(String(route.params.refundNo)));
  } catch {
    detail.value = null;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.detail-hint {
  text-align: center;
  padding: 40px 0;
  color: #b5b9c0;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  font-size: 13px;
  color: #555;
  padding: 4px 0;
}
.row__reason {
  max-width: 60%;
  text-align: right;
  color: #666;
}
.detail-ops {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
}
</style>
