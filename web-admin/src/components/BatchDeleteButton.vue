<template>
  <!--
    两态按钮（2026-11-17 用户二次要求）：
    - 常态只有一个「删除」，列表不显示复选框列，首位就是业务主键（发票 ID / 退款单号…）；
    - 点「删除」进入删除态，复选框列才出现，此时才允许勾选 → 二次确认 → 提交。
    这样列表平时和原来长得一样，不会因为多了勾选列把业务主键挤到第二位。
  -->
  <template v-if="!active">
    <el-button
      class="batch-delete-trigger"
      type="danger"
      text
      size="small"
      data-testid="batch-delete-btn"
      @click="enter"
    >
      删除
    </el-button>
  </template>
  <template v-else>
    <span class="batch-delete-tip" data-testid="batch-delete-tip">已选 {{ ids.length }} 条</span>
    <el-button size="small" data-testid="batch-delete-cancel" @click="exit">取消</el-button>
    <el-button
      type="danger"
      size="small"
      :loading="busy"
      :disabled="ids.length === 0"
      data-testid="batch-delete-confirm"
      @click="onDelete"
    >
      删除
    </el-button>
  </template>
</template>

<script setup lang="ts">
/**
 * 管理端六列表通用的批量删除按钮（C2）。
 *
 * 危险操作链路：点「删除」进入删除态 → 勾选行 → 精确输入「删除」→ 换一次性令牌 → 调业务端点 → 用后即焚。
 * targetId 必须与后端 BatchIds.targetId 完全同口径（trim → 去空 → 去重 → 字典序升序），
 * 否则换到的是 A 令牌、消费用的是 B 目标，会误报 40302。
 */
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { openConfirm } from '@/utils/confirm';

const props = defineProps<{
  /** 当前勾选行的业务主键（单号 / 发票号 / SKU ID 等） */
  ids: string[];
  /** 弹窗摘要：由列表页拼好，例如「退款单 RF1、RF2」 */
  summary?: string;
  /** 实际提交删除的请求函数；config 里带 X-Confirm-Token */
  deleteFn: (ids: string[], config: { headers: Record<string, string> }) => Promise<unknown>;
}>();

const emit = defineEmits<{
  (e: 'done'): void;
}>();

/** 是否处于删除态：由列表页 v-model:active 双向绑定，同时决定表格是否渲染复选框列 */
const active = defineModel<boolean>('active', { default: false });

const busy = ref(false);

function enter(): void {
  active.value = true;
}

function exit(): void {
  active.value = false;
}

function normalizeIds(): string[] {
  return [...new Set(props.ids.map((id) => String(id ?? '').trim()).filter(Boolean))].sort();
}

async function onDelete(): Promise<void> {
  const ids = normalizeIds();
  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的记录');
    return;
  }
  let grant;
  try {
    grant = await openConfirm({
      action: 'BATCH_DELETE',
      targetId: ids.join(','),
      title: '批量删除确认',
      summary: props.summary ?? `已选择 ${ids.length} 条记录：${ids.join('、')}`,
    });
  } catch {
    // 用户取消 / 短语错误：不提交、不提示第二次
    return;
  }
  if (grant.isExpired()) {
    ElMessage.warning('确认已超时，请重新确认');
    return;
  }
  busy.value = true;
  try {
    await props.deleteFn(ids, { headers: grant.headers });
    ElMessage.success(`已删除 ${ids.length} 条记录`);
    active.value = false;
    emit('done');
  } catch {
    ElMessage.error('删除失败，请稍后重试');
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
/* 危险动作常态下是红色文字入口，不抢「查询」的位置，但必须一眼能看见：
   原来用 --sent-ink-3 和「刷新/列显隐」混成一片灰，用户反馈顶部按钮看不清。 */
.batch-delete-trigger {
  padding: 0 8px;
  font-weight: var(--fw-medium);
  color: var(--sent-danger);
}
.batch-delete-trigger:hover {
  color: var(--sent-danger);
  background: var(--sent-danger-soft);
}
.batch-delete-tip {
  align-self: center;
  font-size: 12px;
  color: var(--sent-ink-3);
}
</style>
