<template>
  <div class="container my-custom">
    <div class="sent-bar">
      <strong>我的定制</strong>
      <span class="sent-bar__sub">点任意一条可查看图稿与审核记录</span>
    </div>

    <div class="sent-card">
      <el-input v-model="orderId" placeholder="输入定制单号直接查询，如 CU20261115001" @keyup.enter="query">
        <template #append>
          <el-button @click="query">查询</el-button>
        </template>
      </el-input>
    </div>

    <div class="sent-card">
      <div class="list-head">
        <strong>定制单列表</strong>
        <el-button link type="primary" :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-empty v-if="!loading && rows.length === 0" description="暂无定制单" :image-size="80" />
      <div
        v-for="row in rows"
        :key="row.customOrderId"
        class="custom-row"
        role="link"
        tabindex="0"
        @click="openDetail(row.customOrderId)"
        @keyup.enter="openDetail(row.customOrderId)"
      >
        <div class="row-main">
          <strong>{{ row.customOrderId }}</strong>
          <span class="row-right">
            <el-tag :type="tone(row.status)" size="small">{{ label(row.status) }}</el-tag>
            <span class="row-go">查看详情 ›</span>
          </span>
        </div>
        <div class="row-sub">
          <span>订单 {{ row.orderStatus || '--' }}</span>
          <span>图稿 v{{ row.version }}</span>
          <span>驳回 {{ row.rejectCount }}</span>
          <span v-if="Number(row.reviewDeadlineMillis) > 0">截止 {{ fmtDateTime(Number(row.reviewDeadlineMillis)) }}</span>
        </div>
      </div>

      <div v-if="total > size" class="pager">
        <el-button :disabled="page <= 1" @click="prevPage">上一页</el-button>
        <span>{{ page }} / {{ Math.ceil(total / size) }}</span>
        <el-button :disabled="page * size >= total" @click="nextPage">下一页</el-button>
      </div>
    </div>

    <p class="custom-tip">列表与详情均来自后端真实查询；详情页可查看每一版图稿、审核记录并重新上传。</p>
  </div>
</template>

<script setup lang="ts">
/**
 * 我的定制（列表）：每一行点进去到独立详情页 `/custom/:customOrderId`。
 * 详情（图稿版本 / 审核记录 / 重新上传）都在详情页做，列表只负责定位与状态概览。
 */
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { customApi, type MyCustomOrderVO } from '@/api/custom';
import { fmtDateTime, statusLabel, statusTone, type StatusTone } from '@sent/shared';

const router = useRouter();

const orderId = ref('');
const rows = ref<MyCustomOrderVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const loading = ref(false);

function label(s: string): string {
  return statusLabel(s);
}
function tone(s: string): StatusTone {
  return statusTone(s);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(customApi.my({ page: page.value, size }));
    rows.value = data?.list ?? [];
    total.value = Number(data?.total ?? 0);
  } catch {
    rows.value = [];
    total.value = 0;
    ElMessage.error('定制单列表加载失败');
  } finally {
    loading.value = false;
  }
}

/** 点一行 → 详情页 */
function openDetail(customOrderId: string): void {
  router.push(`/custom/${customOrderId}`);
}

/** 手输单号：合法就直接进详情页，非法给提示（不再原地展开） */
function query(): void {
  const id = orderId.value.trim();
  if (!id) {
    ElMessage.warning('请输入定制单号');
    return;
  }
  openDetail(id);
}

function prevPage(): void {
  if (page.value <= 1) return;
  page.value -= 1;
  void load();
}
function nextPage(): void {
  if (page.value * size >= total.value) return;
  page.value += 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.my-custom {
  padding-top: 14px;
}
.sent-bar__sub {
  font-size: 12px;
  color: var(--sent-muted);
  font-weight: 400;
}
.list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  padding-bottom: 8px;
}
.custom-row {
  border-top: 1px solid #f0f1f3;
  padding: 12px 10px;
  margin: 0 -10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.custom-row:hover {
  background: var(--sent-primary-soft);
}
.custom-row:focus-visible {
  outline: 2px solid var(--sent-primary);
  outline-offset: -2px;
}
.row-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.row-main strong {
  color: var(--sent-text);
}
.row-right {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.row-go {
  font-size: 12px;
  color: var(--sent-muted);
}
.custom-row:hover .row-go {
  color: var(--sent-primary);
}
.row-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
  padding-top: 12px;
}
.custom-tip {
  font-size: 12px;
  color: #b5b9c0;
  padding: 8px 4px;
  line-height: 1.5;
}
</style>
