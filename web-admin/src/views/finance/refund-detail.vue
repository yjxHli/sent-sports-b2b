<template>
  <div>
    <h2 class="sent-page-title">退款审核详情</h2>

    <div class="sent-panel audit-head" v-loading="loading">
      <div class="audit-head__top">
        <div class="audit-head__title">
          <span class="audit-head__no">{{ refundNo }}</span>
          <StatusTag :status="detail?.status" />
          <el-tag v-if="detail?.type" size="small" effect="plain" type="info">{{ typeLabel }}</el-tag>
        </div>
        <div class="audit-head__ops">
          <el-button link type="primary" @click="router.push('/finance/refund')">返回列表</el-button>
          <el-button
            v-if="orderNo"
            link
            type="primary"
            @click="router.push(`/order/detail/${orderNo}`)"
          >
            订单详情
          </el-button>
        </div>
      </div>

      <div class="audit-head__stats">
        <div class="stat">
          <span class="stat__label">退款金额</span>
          <strong class="stat__value stat__value--amount">{{ fmtMoney(detail?.amountCent) }}</strong>
        </div>
        <div class="stat">
          <span class="stat__label">订单号</span>
          <strong class="stat__value">{{ orderNo || '--' }}</strong>
        </div>
        <div class="stat">
          <span class="stat__label">申请时间</span>
          <strong class="stat__value">{{ fmtDateTime(detail?.createdAt) }}</strong>
        </div>
        <div class="stat">
          <span class="stat__label">申诉次数</span>
          <strong class="stat__value">{{ detail?.appealCount ?? 0 }}</strong>
        </div>
        <div class="stat">
          <span class="stat__label">退款到账</span>
          <strong class="stat__value">{{ detail?.refundedAt ? fmtDateTime(detail.refundedAt) : '--' }}</strong>
        </div>
      </div>
    </div>

    <!-- 定制单审核通过但未排产：这一档允许退，但必须先跟生产确认是否已备料（2026-11-21） -->
    <el-alert
      v-if="detail?.needManualReview"
      class="audit-manual"
      type="warning"
      :closable="false"
      show-icon
      title="该退款涉及已过审但未排产的定制商品"
      data-testid="refund-need-manual-review"
    >
      定制图稿已审核通过、尚未进入生产。请先与生产确认是否已备料/打样，再决定通过或驳回。
    </el-alert>

    <div class="audit-grid">
      <div class="audit-col">
        <section class="sent-panel">
          <h3 class="section-title">退款申请</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="退款单号">{{ detail?.refundNo || '--' }}</el-descriptions-item>
            <el-descriptions-item label="退款类型">{{ typeLabel }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ applicantLabel }}</el-descriptions-item>
            <el-descriptions-item label="退款状态">
              <StatusTag :status="detail?.status" />
            </el-descriptions-item>
            <el-descriptions-item label="退款原因" :span="2">
              {{ detail?.reason || '--' }}
            </el-descriptions-item>
            <el-descriptions-item label="审核结论" :span="2">
              <span v-if="detail?.auditRemark">{{ detail.auditRemark }}</span>
              <span v-else class="muted">暂无审核备注</span>
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="sub-title">凭证图片（{{ proofImages.length }} 张）</h4>
          <div v-if="proofImages.length" class="proof-gallery">
            <el-image
              v-for="(fid, i) in proofImages"
              :key="fid"
              :src="fileApi.url(fid)"
              :preview-src-list="proofImages.map((f: string) => fileApi.url(f))"
              :initial-index="i"
              fit="cover"
              class="proof-gallery__item"
              preview-teleported
            />
          </div>
          <el-empty v-else description="用户未上传凭证图片" :image-size="56" />
        </section>

        <section class="sent-panel">
          <h3 class="section-title">订单基本信息</h3>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="订单号">{{ order?.orderNo || '--' }}</el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <StatusTag :status="order?.status" />
            </el-descriptions-item>
            <el-descriptions-item label="支付状态">
              <StatusTag :status="order?.payStatus" />
            </el-descriptions-item>
            <el-descriptions-item label="发货状态">
              <StatusTag :status="order?.shipStatus" />
            </el-descriptions-item>
            <el-descriptions-item label="售后状态">
              <StatusTag v-if="order?.afterSaleStatus" :status="order?.afterSaleStatus" />
              <span v-else class="muted">无</span>
            </el-descriptions-item>
            <el-descriptions-item label="下单时间">{{ fmtDateTime(order?.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="支付时间">{{ fmtDateTime(order?.paidAt) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ fmtDateTime(order?.completedAt) }}</el-descriptions-item>
            <el-descriptions-item label="订单备注">{{ order?.remark || '--' }}</el-descriptions-item>
            <template v-if="order?.address">
              <el-descriptions-item label="收货人">{{ order?.receiver || '--' }}</el-descriptions-item>
              <el-descriptions-item label="收货电话">{{ order?.receiverPhone || '--' }}</el-descriptions-item>
              <el-descriptions-item label="收货地址">{{ order?.address }}</el-descriptions-item>
            </template>
            <el-descriptions-item v-else label="收货信息" :span="3">
              <span class="muted">订单未记录收货信息（下单环节未填写地址）</span>
            </el-descriptions-item>
            <el-descriptions-item label="物流" :span="3">
              <span v-if="order?.trackingNo">
                {{ order.carrier || '承运商未登记' }} · {{ order.trackingNo }}
                <span class="muted">（{{ fmtDateTime(order.shippedAt) }} 发货）</span>
              </span>
              <span v-else class="muted">未发货或未登记物流单号</span>
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="sub-title">买家信息</h4>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="客户">{{ order?.customerName || '--' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ order?.customerPhone || '--' }}</el-descriptions-item>
            <el-descriptions-item label="客户编号">{{ order?.customerNo || '--' }}</el-descriptions-item>
            <el-descriptions-item label="客户类型">{{ customerTypeLabel }}</el-descriptions-item>
            <el-descriptions-item label="会员等级">{{ order?.customerLevel || '--' }}</el-descriptions-item>
            <el-descriptions-item label="客户 ID">{{ order?.customerId ?? '--' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="sent-panel">
          <h3 class="section-title">金额与商品明细</h3>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="货款">{{ fmtMoney(order?.goodsAmountCent) }}</el-descriptions-item>
            <el-descriptions-item label="运费">{{ fmtMoney(order?.freightCent) }}</el-descriptions-item>
            <el-descriptions-item label="积分抵扣">{{ fmtMoney(order?.pointDeductCent) }}</el-descriptions-item>
            <el-descriptions-item label="优惠">{{ fmtMoney(order?.discountCent) }}</el-descriptions-item>
            <el-descriptions-item label="应付金额">
              <strong>{{ fmtMoney(order?.payableCent) }}</strong>
            </el-descriptions-item>
            <el-descriptions-item label="实付金额">
              <strong>{{ fmtMoney(order?.paidCent) }}</strong>
            </el-descriptions-item>
          </el-descriptions>

          <el-table v-if="lines.length" :data="lines" border size="small" class="line-table">
            <el-table-column prop="skuName" label="商品 / SKU" min-width="180" />
            <el-table-column prop="spec" label="规格" width="130" />
            <el-table-column prop="qty" label="数量" width="80" align="center" />
            <el-table-column label="单价" width="110" align="right">
              <template #default="scope">{{ fmtMoney(scope.row.unitPriceCent) }}</template>
            </el-table-column>
            <el-table-column label="行实付" width="110" align="right">
              <template #default="scope">{{ fmtMoney(scope.row.linePaidCent) }}</template>
            </el-table-column>
            <el-table-column label="已发" width="80" align="center">
              <template #default="scope">{{ scope.row.shippedQty ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="定制状态" width="110" align="center">
              <template #default="scope"><StatusTag :status="scope.row.customStatus" /></template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="该订单暂无商品行" :image-size="56" />
        </section>
      </div>

      <aside class="audit-col audit-col--side">
        <section class="sent-panel audit-side">
          <h3 class="section-title">审核操作</h3>
          <el-alert
            :title="auditHint"
            :type="pending ? 'warning' : 'info'"
            :closable="false"
            show-icon
          />
          <div class="audit-actions">
            <el-button
              type="success"
              :disabled="!pending"
              :loading="submitting"
              @click="approve"
            >
              审核通过
            </el-button>
            <el-button
              type="danger"
              :disabled="!pending"
              :loading="submitting"
              @click="reject"
            >
              驳回申请
            </el-button>
            <el-button @click="load">刷新</el-button>
          </div>

          <h4 class="sub-title">审核要点</h4>
          <ul class="audit-tips">
            <li>退款金额不得高于订单实付 {{ fmtMoney(order?.paidCent) }}。</li>
            <li>凭证图片是唯一实物依据，看不清请驳回并说明需要补拍的角度。</li>
            <li>已发货订单需确认物流签收情况，未签收优先引导拒收。</li>
            <li>驳回后用户可申诉一次，原因会作为站内信正文发给客户。</li>
          </ul>

          <h4 class="sub-title">处理进度</h4>
          <el-timeline>
            <el-timeline-item
              v-for="node in timeline"
              :key="node.title"
              :timestamp="node.time"
              :type="node.type"
            >
              {{ node.title }}
            </el-timeline-item>
          </el-timeline>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 退款审核详情（独立审核页）：列表行点「详情」进入，路由 /finance/refund/:refundNo。
 *
 * 数据来自 `GET /api/admin/refunds/{refundNo}`（退款单 + 订单基本信息 + 客户 + 商品明细 + 物流一次返回），
 * 审核员在一屏内就能判断「该不该退」，不用再来回切订单详情页。
 *
 * 审核动作与列表页同一套口径：
 * - 通过 = 危险操作，必须 openConfirm('REFUND_APPROVE') 换一次性 X-Confirm-Token，提交前复查过期；
 * - 驳回 = 必须填写原因（会写进 refund_order.audit_remark 并作为站内信正文发给客户）。
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { financeApi } from '@/api';
import { fileApi } from '@/api/file';
import type { AuditOrderVO, RefundAuditDetailVO } from '@sent/shared';
import { RefundStatus, fmtDateTime, fmtMoney } from '@sent/shared';
import { openConfirm } from '@/utils/confirm';
import StatusTag from '@/components/StatusTag.vue';

const route = useRoute();
const router = useRouter();
const refundNo = String(route.params.refundNo ?? '');

const detail = ref<RefundAuditDetailVO | null>(null);
const loading = ref(false);
const submitting = ref(false);

const orderNo = computed(() => detail.value?.orderNo ?? detail.value?.order?.orderNo ?? '');
const order = computed<AuditOrderVO | null>(() => detail.value?.order ?? null);
const lines = computed(() => (Array.isArray(order.value?.lines) ? order.value!.lines : []));
const proofImages = computed<string[]>(() =>
  Array.isArray(detail.value?.images) ? (detail.value!.images as string[]) : [],
);
const pending = computed(() => detail.value?.status === RefundStatus.PENDING);

const TYPE_LABELS: Record<string, string> = {
  REFUND: '仅退款',
  ONLY_REFUND: '仅退款',
  RETURN_REFUND: '退货退款',
};
const typeLabel = computed(() => TYPE_LABELS[String(detail.value?.type ?? '')] ?? (detail.value?.type || '--'));
const applicantLabel = computed(() =>
  detail.value?.applicantType === 'STAFF' ? '客服代客申请' : '客户自助申请',
);
const customerTypeLabel = computed(() =>
  order.value?.customerType === 'ENTERPRISE' ? '企业客户' : '个人客户',
);
const auditHint = computed(() => {
  if (pending.value) return '待审核：核对凭证图片与订单实付后再操作，通过后立即发起退款。';
  if (detail.value?.status === RefundStatus.CLOSED) return '该申请已驳回关闭，用户可申诉一次。';
  if (detail.value?.status === RefundStatus.REFUNDED) return '该申请已通过，退款已发起。';
  if (detail.value?.status === RefundStatus.FAILED) return '退款失败：请到回款记录核对失败流水后重试。';
  return '退款处理中，等待支付渠道回调。';
});

/** 处理进度：申请 → 审核 → 退款到账（无审核记录表，节点由单据状态与时间派生）。 */
const timeline = computed(() => {
  const nodes: Array<{ title: string; time: string; type: 'primary' | 'success' | 'warning' | 'info' }> = [
    { title: '用户提交退款申请', time: fmtDateTime(detail.value?.createdAt), type: 'primary' },
  ];
  if (detail.value?.status === RefundStatus.PENDING) {
    nodes.push({ title: '等待财务审核', time: '进行中', type: 'warning' });
  } else if (detail.value?.status === RefundStatus.CLOSED) {
    nodes.push({ title: `审核驳回：${detail.value?.auditRemark || '无备注'}`, time: '已完成', type: 'info' });
  } else {
    nodes.push({ title: '审核通过', time: '已完成', type: 'success' });
    nodes.push({
      title: detail.value?.status === RefundStatus.REFUNDED ? '退款已到账' : '退款处理中',
      time: detail.value?.refundedAt ? fmtDateTime(detail.value.refundedAt) : '等待回调',
      type: detail.value?.status === RefundStatus.REFUNDED ? 'success' : 'warning',
    });
  }
  return nodes;
});

async function load(): Promise<void> {
  if (!refundNo) return;
  loading.value = true;
  try {
    detail.value = await unwrap(financeApi.refundAuditDetail(refundNo));
  } catch (error) {
    detail.value = null;
    ElMessage.error((error as Error)?.message || '退款详情加载失败');
  } finally {
    loading.value = false;
  }
}

async function approve(): Promise<void> {
  if (!pending.value || submitting.value) return;
  submitting.value = true;
  try {
    const grant = await openConfirm({
      action: 'REFUND_APPROVE',
      targetId: refundNo,
      summary: `退款单 ${refundNo} · 订单 ${orderNo.value} · 金额 ${fmtMoney(detail.value?.amountCent)}`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    await unwrap(financeApi.auditRefund(refundNo, { approved: true, remark: '审核通过' }, grant.token));
    ElMessage.success('退款审核已通过');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '退款审核提交失败');
  } finally {
    submitting.value = false;
  }
}

async function reject(): Promise<void> {
  if (!pending.value || submitting.value) return;
  let remark = '';
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因（会作为站内信正文发给客户）', '驳回退款申请', {
      inputType: 'textarea',
      inputPlaceholder: '如：凭证照片无法证明质量问题，请补拍吊牌与实物细节',
      inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空'),
      type: 'warning',
    });
    remark = String(value).trim();
  } catch {
    return;
  }
  submitting.value = true;
  try {
    await unwrap(financeApi.auditRefund(refundNo, { approved: false, remark }));
    ElMessage.success('已驳回，已通知客户');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '退款审核提交失败');
  } finally {
    submitting.value = false;
  }
}

onMounted(() => { void load(); });
</script>

<style scoped>
.audit-head { margin-bottom: 16px; }
.audit-head__top { display: flex; align-items: center; gap: 10px; }
.audit-head__title { display: flex; align-items: center; gap: 10px; }
.audit-head__no {
  font-size: 18px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--sent-ink);
  letter-spacing: 0;
}
.audit-head__ops { margin-left: auto; display: flex; gap: 4px; }
/* 规格行：一排关键值只靠竖向发丝线分隔，不做成 5 张小卡 */
.audit-head__stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  margin-top: var(--sp-4);
  border-top: 1px solid var(--sent-line);
  padding-top: var(--sp-3);
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px var(--sp-3) 4px 0;
  border-radius: 0;
  background: transparent;
}
.stat + .stat {
  padding-left: var(--sp-4);
  border-left: 1px solid var(--sent-line);
}
.stat__label { font-size: 12px; color: var(--sent-ink-3); }
.stat__value {
  font-size: 14px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--sent-ink);
  word-break: break-all;
}
.stat__value--amount { font-size: 20px; font-weight: 500; letter-spacing: -0.01em; }

.audit-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}
.audit-col { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.audit-col--side { position: sticky; top: 12px; }
.sub-title { margin: 18px 0 10px; font-size: 13px; color: #4b5f57; font-weight: 600; }
.muted { color: #99a1a8; }

.proof-gallery { display: flex; flex-wrap: wrap; gap: 10px; }
.proof-gallery__item {
  width: 104px;
  height: 104px;
  border-radius: 6px;
  border: 1px solid #e5e6eb;
  cursor: zoom-in;
}

.line-table { margin-top: 14px; }

.audit-actions { display: flex; gap: 10px; margin: 16px 0 4px; flex-wrap: wrap; }
.audit-tips {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.9;
  color: #5c6b66;
}
.audit-tips li { list-style: disc; }
.audit-side :deep(.el-timeline) { padding-left: 2px; }
.audit-side :deep(.el-timeline-item__timestamp) { font-size: 12px; }
.audit-manual { margin-bottom: 16px; }
</style>
