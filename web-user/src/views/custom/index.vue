<template>
  <div>
    <div class="sent-bar"><strong>我的定制</strong></div>

    <div class="sent-card">
      <el-input v-model="orderId" placeholder="输入定制单号直接查询" @keyup.enter="query">
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
        :class="{ active: custom?.customOrderId === row.customOrderId }"
        @click="select(row)"
      >
        <div class="row-main">
          <strong>{{ row.customOrderId }}</strong>
          <el-tag :type="tone(row.status)" size="small">{{ label(row.status) }}</el-tag>
        </div>
        <div class="row-sub">
          <span>订单 {{ row.orderStatus || '--' }}</span>
          <span>图稿 v{{ row.version }}</span>
          <span>驳回 {{ row.rejectCount }}</span>
          <span v-if="row.reviewDeadlineMillis > 0">截止 {{ fmtDateTime(row.reviewDeadlineMillis) }}</span>
        </div>
      </div>

      <div v-if="total > size" class="pager">
        <el-button :disabled="page <= 1" @click="prevPage">上一页</el-button>
        <span>{{ page }} / {{ Math.ceil(total / size) }}</span>
        <el-button :disabled="page * size >= total" @click="nextPage">下一页</el-button>
      </div>
    </div>

    <div v-if="custom" class="sent-card">
      <div class="list-head"><strong>{{ custom.customOrderId }}</strong></div>
      <div class="row"><span>状态</span><el-tag :type="tone(custom.status)" size="small">{{ label(custom.status) }}</el-tag></div>
      <div class="row"><span>图稿版本</span><span>v{{ custom.version }}</span></div>
      <div class="row"><span>驳回次数</span><span>{{ custom.rejectCount }}</span></div>
      <div v-if="custom.rejectReason" class="row"><span>驳回原因</span><span>{{ custom.rejectReason }}</span></div>
      <div class="row"><span>审核截止</span><span>{{ fmtDateTime(custom.reviewDeadlineMillis) }}</span></div>

      <el-button
        class="up-btn"
        type="primary"
        :loading="uploading"
        @click="upload"
      >上传图稿</el-button>
    </div>

    <p class="custom-tip">列表与详情均来自后端真实查询；图稿上传仅提交文件元数据（演示生成占位 fileId）。</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { customApi, type CustomVO, type MyCustomOrderVO } from '@/api/custom';
import { fmtDateTime, statusLabel, statusTone, type StatusTone } from '@sent/shared';

const orderId = ref('');
const custom = ref<CustomVO | null>(null);
const rows = ref<MyCustomOrderVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const loading = ref(false);
const uploading = ref(false);

function label(s: string): string { return statusLabel(s); }
function tone(s: string): StatusTone { return statusTone(s); }

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(customApi.my({ page: page.value, size }));
    rows.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } catch {
    rows.value = [];
    total.value = 0;
    ElMessage.error('定制单列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function select(row: MyCustomOrderVO): Promise<void> {
  orderId.value = row.customOrderId;
  await query();
}

async function query(): Promise<void> {
  const id = orderId.value.trim();
  if (!id) {
    ElMessage.warning('请输入定制单号');
    return;
  }
  loading.value = true;
  try {
    custom.value = await unwrap(customApi.detail(id));
  } catch {
    custom.value = null;
    ElMessage.error('未找到该定制单');
  } finally {
    loading.value = false;
  }
}

async function upload(): Promise<void> {
  if (!custom.value) return;
  uploading.value = true;
  try {
    const res = await unwrap(customApi.uploadDraft(custom.value.customOrderId, {
      fileId: `f_${Date.now()}`, fileName: 'draft.png', fileSize: 1024,
    }));
    custom.value = {
      ...custom.value,
      version: res.version,
      status: res.status,
      reviewDeadlineMillis: res.reviewDeadlineMillis,
    };
    ElMessage.success('图稿已上传');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '上传失败');
  } finally {
    uploading.value = false;
  }
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

onMounted(() => { void load(); });
</script>

<style scoped>
.list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  padding-bottom: 8px;
}
.custom-row {
  border-top: 1px solid #f0f1f3;
  padding: 10px 0;
  cursor: pointer;
}
.custom-row.active { color: #c4382f; }
.row-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.row-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #555;
  padding: 5px 0;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
  padding-top: 10px;
}
.up-btn {
  width: 100%;
  margin-top: 12px;
}
.custom-tip {
  font-size: 12px;
  color: #b5b9c0;
  padding: 8px 16px;
  line-height: 1.5;
}
</style>
