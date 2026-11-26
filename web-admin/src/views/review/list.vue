<template>
  <div>
    <h2 class="sent-page-title">评价管理</h2>

    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="商品评价"
        :operation-width="136"
        data-testid="admin-review-list"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-input
            v-model="query.productId"
            data-testid="admin-review-filter-product"
            placeholder="商品 ID"
            clearable
            style="width: 130px"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-select
            v-model="query.rating"
            data-testid="admin-review-filter-rating"
            placeholder="全部星级"
            clearable
            style="width: 120px"
            @change="onSearch"
            @clear="query.rating = undefined"
          >
            <el-option v-for="star in [5, 4, 3, 2, 1]" :key="star" :label="`${star} 星`" :value="star" />
          </el-select>
          <el-select
            v-model="query.status"
            data-testid="admin-review-filter-status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
            @change="onSearch"
            @clear="query.status = undefined"
          >
            <el-option label="正常" value="NORMAL" />
            <el-option label="已隐藏" value="HIDDEN" />
          </el-select>
          <el-button type="primary" data-testid="admin-review-query" @click="onSearch">查询</el-button>
          <span class="sent-hint">隐藏后用户端立即不可见，好评率按可见评价重算；评价本身不删除，可随时恢复</span>
        </template>

        <template #col-createdAt="{ row }">
          <span class="rv-time">{{ fmtDateTime(Number(row.createdAt)) }}</span>
        </template>

        <template #col-productTitle="{ row }">
          <div class="rv-product">
            <span class="rv-product__title">{{ row.productTitle || '（商品已删除）' }}</span>
            <span class="rv-product__id">#{{ row.productId }}</span>
          </div>
        </template>

        <template #col-userLabel="{ row }">
          <span class="rv-user">{{ row.userLabel }}</span>
        </template>

        <template #col-rating="{ row }">
          <el-rate :model-value="Number(row.rating)" disabled size="small" />
        </template>

        <template #col-content="{ row }">
          <span class="rv-content" :title="row.content">{{ row.content }}</span>
        </template>

        <template #col-images="{ row }">
          <div v-if="row.images?.length" class="rv-imgs">
            <el-image
              v-for="(img, i) in row.images.slice(0, 2)"
              :key="row.reviewId + '-thumb-' + i"
              class="rv-img"
              :src="fileApi.url(img)"
              :preview-src-list="row.images.map((f: string) => fileApi.url(f))"
              :initial-index="i"
              fit="cover"
              preview-teleported
              data-testid="admin-review-img"
            />
            <span v-if="row.images.length > 2" class="rv-imgs__more">+{{ row.images.length - 2 }}</span>
          </div>
          <span v-else class="sent-hint">—</span>
        </template>

        <template #col-status="{ row }">
          <span data-testid="admin-review-status">
            <StatusTag
              :label="row.status === 'HIDDEN' ? '已隐藏' : '正常'"
              :tone="row.status === 'HIDDEN' ? 'danger' : 'success'"
            />
          </span>
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" data-testid="admin-review-detail-btn" @click="openDetail(row)">
            查看详情
          </el-button>
          <el-popconfirm
            :title="row.status === 'HIDDEN' ? '恢复这条评价的展示？用户端将立即可见。' : '隐藏后用户端立即不可见，确认隐藏？'"
            width="260"
            @confirm="toggle(row)"
          >
            <template #reference>
              <el-button
                link
                :type="row.status === 'HIDDEN' ? 'success' : 'warning'"
                :data-testid="row.status === 'HIDDEN' ? 'admin-review-show-btn' : 'admin-review-hide-btn'"
              >
                {{ row.status === 'HIDDEN' ? '显示' : '隐藏' }}
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </SentTable>
    </div>

    <!-- 详情：完整正文 + 九宫格（点击放大） -->
    <el-dialog v-model="detailVisible" title="评价详情" width="640px" align-center>
      <div v-if="detail" class="rv-detail">
        <div class="rv-detail__meta">
          <span class="rv-detail__product">{{ detail.productTitle || '（商品已删除）' }} #{{ detail.productId }}</span>
          <span class="rv-detail__sub">{{ detail.userLabel }} · {{ fmtDateTime(Number(detail.createdAt)) }}</span>
        </div>
        <el-rate :model-value="Number(detail.rating)" disabled />
        <p class="rv-detail__text">{{ detail.content }}</p>
        <div v-if="detail.images?.length" class="rv-detail__imgs">
          <el-image
            v-for="(img, i) in detail.images"
            :key="detail.reviewId + '-full-' + i"
            class="rv-detail__img"
            :src="fileApi.url(img)"
            :preview-src-list="detail.images.map((f: string) => fileApi.url(f))"
            :initial-index="i"
            fit="cover"
            preview-teleported
            data-testid="admin-review-img"
          />
        </div>
        <p v-else class="sent-hint">这条评价没有上传图片</p>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 评价管理（V56）：运营下架违规评价的治理页。
 *
 * 三条页面口径：
 * - 列表筛选全部下推后端（商品 ID / 星级 / 状态），不在当前页 filter——
 *   评价上量后前端过滤会「明明有却看不到」；
 * - 状态列与操作列都用 `status` 派生，隐藏/显示共用一个切换端点，避免两条路径口径漂移；
 * - 详情弹窗复用 `preview-src-list`，与用户端看图体验一致。
 */
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { reviewAdminApi, type AdminReviewVO } from '@/api';
import { fileApi } from '@/api/file';
import { useTable } from '@/composables/useTable';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'createdAt', label: '评价时间', width: 136 },
  { prop: 'productTitle', label: '商品信息', minWidth: 120 },
  { prop: 'userLabel', label: '用户', width: 72 },
  { prop: 'rating', label: '评分', width: 104, align: 'center' },
  // 摘要固定宽度而不是 minWidth：让「图片/状态」在 1280 宽屏内也能露出来，不必横向滚动
  { prop: 'content', label: '评价内容', width: 140 },
  { prop: 'images', label: '图片', width: 66, align: 'center' },
  { prop: 'status', label: '状态', width: 78, align: 'center' },
];

const query = reactive<{ productId: string; rating: number | undefined; status: string | undefined }>({
  productId: '', rating: undefined, status: undefined,
});

const { rows, total, loading, page, size, load, onPage, onSize, onSearch } = useTable<AdminReviewVO>({
  fetch: (params) => unwrap(reviewAdminApi.list({
    ...params,
    // 空串归一 undefined：否则后端 Long 绑定会 400
    productId: query.productId.trim() || undefined,
    rating: typeof query.rating === 'number' ? query.rating : undefined,
    status: query.status || undefined,
  })),
  paginationKey: 'admin-review',
  errorMessage: '评价列表加载失败',
});

const detailVisible = ref(false);
const detail = ref<AdminReviewVO | null>(null);

function openDetail(row: AdminReviewVO): void {
  detail.value = row;
  detailVisible.value = true;
}

async function toggle(row: AdminReviewVO): Promise<void> {
  const next = row.status === 'HIDDEN' ? 'NORMAL' : 'HIDDEN';
  try {
    await unwrap(reviewAdminApi.updateStatus(row.reviewId, { status: next }));
    ElMessage.success(next === 'HIDDEN' ? '已隐藏，用户端不再展示' : '已恢复展示');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '状态修改失败');
  }
}
</script>

<style scoped>
.rv-time {
  font-size: 12px;
  color: var(--sent-ink-2);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.rv-product {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.rv-product__title {
  font-size: 13px;
  color: var(--sent-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rv-product__id {
  font-size: 12px;
  color: var(--sent-ink-3);
  font-variant-numeric: tabular-nums;
}

.rv-user {
  font-size: 13px;
  color: var(--sent-ink-2);
}

/* 摘要单行省略：两行截断会顶破单元格宽度（表格 show-overflow-tooltip 也跟着失效），
   全文在「查看详情」弹窗里看 */
.rv-content {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--sent-ink-2);
}

.rv-imgs {
  display: flex;
  align-items: center;
  gap: 6px;
}
.rv-img {
  width: 34px;
  height: 34px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--sent-fill, #eef0eb);
  cursor: zoom-in;
}
.rv-imgs__more {
  font-size: 12px;
  color: var(--sent-ink-3);
}

.rv-detail__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}
.rv-detail__product {
  font-size: 14px;
  font-weight: 500;
  color: var(--sent-ink);
}
.rv-detail__sub {
  font-size: 12px;
  color: var(--sent-ink-3);
}
.rv-detail__text {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--sent-ink-2);
  white-space: pre-wrap;
  word-break: break-word;
}
.rv-detail__imgs {
  display: grid;
  grid-template-columns: repeat(3, 96px);
  gap: 8px;
  margin-top: 12px;
}
.rv-detail__img {
  width: 96px;
  height: 96px;
  border-radius: var(--sent-radius);
  overflow: hidden;
  background: var(--sent-fill, #eef0eb);
  cursor: zoom-in;
}
</style>
