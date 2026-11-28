<template>
  <div class="rv-page" data-testid="mine-reviews-page">
    <!-- 顶部用户条：头像 + 昵称 + 四项统计（2026-11-28 按参考图重排） -->
    <section class="rv-hero">
      <div class="rv-hero__who">
        <div class="rv-hero__avatar">{{ avatarChar }}</div>
        <div class="rv-hero__name">{{ nickname }}</div>
      </div>
      <div class="rv-hero__stats">
        <div
          v-for="s in stats"
          :key="s.key"
          class="rv-stat"
          :data-testid="`review-stat-${s.key}`"
        >
          <b class="rv-stat__num">{{ s.value }}</b>
          <span class="rv-stat__label">{{ s.label }}</span>
        </div>
      </div>
    </section>

    <!-- 页签 + 排序 -->
    <div class="rv-bar">
      <div class="rv-tabs" role="tablist" aria-label="评价分类">
        <button
          v-for="t in TABS"
          :key="t.key"
          type="button"
          class="rv-tab"
          :class="{ 'rv-tab--on': tab === t.key }"
          role="tab"
          :aria-selected="tab === t.key"
          :data-testid="`review-tab-${t.key}`"
          @click="switchTab(t.key)"
        >
          {{ t.label }}
          <i v-if="t.key === 'pending' && pendingTotal > 0" class="rv-tab__num">{{ pendingTotal }}</i>
        </button>
      </div>

      <!-- 排序：待评价是「按完成时间倒序」的固定口径，没有排序意义，只在已评价页签出现 -->
      <div v-if="tab === 'written'" class="rv-sort" role="group" aria-label="排序方式">
        <button
          v-for="s in SORTS"
          :key="s.key"
          type="button"
          class="rv-sort__item"
          :class="{ 'rv-sort__item--on': sort === s.key }"
          :data-testid="`review-sort-${s.key}`"
          @click="setSort(s.key)"
        >{{ s.label }}</button>
      </div>
    </div>

    <div v-if="loading" class="rv-hint">加载中…</div>

    <!-- ========== 待评价 ========== -->
    <template v-else-if="tab === 'pending'">
      <div v-if="pendingList.length === 0" class="rv-empty" data-testid="reviews-empty">
        <div class="rv-empty__icon">✎</div>
        <p>暂无待评价的商品</p>
        <p class="rv-empty__sub">确认收货后就能在这里评价买到的东西</p>
        <router-link to="/orders" class="rv-empty__btn">查看我的订单</router-link>
      </div>
      <ul v-else class="rv-pending">
        <li
          v-for="item in pendingList"
          :key="`${item.orderNo}-${item.productId}`"
          class="rv-pending__row"
          data-testid="pending-review-item"
        >
          <button type="button" class="rv-pending__thumb" @click="goProduct(item.productId)">
            <ProductThumb :src="item.mainImage" :name="item.productTitle" :seed="item.productId" />
          </button>
          <div class="rv-pending__body">
            <button type="button" class="rv-pending__name" @click="goProduct(item.productId)">
              {{ item.productTitle || '商品已下架' }}
            </button>
            <div class="rv-pending__meta">
              <span>订单号 {{ item.orderNo }}</span>
              <span>完成时间 {{ fmtDate(item.completedAt) }}</span>
              <span>数量 {{ Number(item.qty) }}</span>
            </div>
          </div>
          <div class="rv-pending__price">¥{{ item.paidYuan || '0.00' }}</div>
          <button
            type="button"
            class="rv-btn rv-btn--primary"
            data-testid="pending-review-go"
            @click="goReview(item.productId)"
          >去评价</button>
        </li>
      </ul>

      <div v-if="pendingList.length < pendingTotal" class="rv-more">
        <button type="button" class="rv-more__btn" :disabled="loading" @click="loadMorePending">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </template>

    <!-- ========== 已评价 ========== -->
    <template v-else>
      <div v-if="list.length === 0" class="rv-empty" data-testid="reviews-empty">
        <div class="rv-empty__icon">✎</div>
        <p>还没有写过评价</p>
        <p class="rv-empty__sub">确认收货后就能评价买到的东西</p>
        <router-link to="/orders" class="rv-empty__btn">查看我的订单</router-link>
      </div>
      <ul v-else class="rv-list">
        <li
          v-for="item in list"
          :key="item.reviewId"
          class="rv-item"
          data-testid="review-item"
        >
          <button type="button" class="rv-item__thumb" @click="goProduct(item.productId)">
            <ProductThumb :src="item.mainImage" :name="item.productTitle" :seed="item.productId" />
          </button>

          <div class="rv-item__body">
            <div class="rv-item__head">
              <button type="button" class="rv-item__name" @click="goProduct(item.productId)">
                {{ item.productTitle || '商品已下架' }}
              </button>
              <span v-if="item.status !== 'NORMAL'" class="rv-item__hidden">已隐藏</span>
              <span v-else-if="item.anonymous" class="rv-item__anon">匿名</span>
            </div>

            <div class="rv-item__stars" :aria-label="`${item.rating} 星`">
              <span
                v-for="i in 5"
                :key="i"
                class="rv-item__star"
                :class="{ 'rv-item__star--on': i <= item.rating }"
              >★</span>
            </div>

            <p class="rv-item__content">{{ item.content }}</p>

            <div v-if="item.images?.length" class="rv-item__images">
              <button
                v-for="fid in item.images"
                :key="fid"
                type="button"
                class="rv-item__image"
                @click="preview(fid)"
              >
                <img :src="fileApi.url(fid)" alt="评价图" />
              </button>
            </div>

            <div class="rv-item__foot">
              <span class="rv-item__time">{{ fmtDate(item.createdAt) }}</span>
              <span class="rv-item__metric">浏览 {{ count(item.viewCount) }}</span>
              <span class="rv-item__metric">获赞 {{ count(item.likeCount) }}</span>
              <!-- 删除自己的评价（2026-11-28 用户要求）：删完可重新评价 -->
              <button
                type="button"
                class="rv-item__del"
                data-testid="review-delete"
                @click="removeReview(item)"
              >删除</button>
            </div>
          </div>
        </li>
      </ul>

      <div v-if="list.length < total" class="rv-more">
        <button type="button" class="rv-more__btn" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 评价中心（2026-11-28 第二次改版，对齐京东「我的评价」）。
 *
 * 版式：用户条（头像 + 昵称 + 已写评论/浏览数/获赞数/待评价四项）
 * → 左侧页签「待评价 / 已评价」+ 右侧「综合 / 最新 / 最早」排序 → 列表。
 *
 * 两套数据各走各的接口，口径不混：
 * - 待评价 `GET /api/me/reviews/pending`：已完成订单里尚未评价的商品，
 *   与详情页「去评价」的资格同源（同一条 order_no + product_id 判定）；
 * - 已评价 `GET /api/me/reviews?sort=`：我写过的评价，被运营隐藏的也留下并标记。
 *
 * 删除是物理删除（后端放掉唯一索引），所以删完可以重新评价同一订单商品；
 * 删除按钮只出现在自己写的评价上——数据本来就只查本人的。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtDate, type UserMeVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import {
  reviewApi,
  type MyPendingReviewVO,
  type MyReviewVO,
  type ReviewSort,
} from '@/api/review';
import { fileApi } from '@/api/file';
import { profileApi } from '@/api/profile';
import { openInNewTab } from '@/utils/link';
import ProductThumb from '@/components/ProductThumb.vue';

type TabKey = 'pending' | 'written';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'pending', label: '待评价' },
  { key: 'written', label: '已评价' },
];

const SORTS: { key: ReviewSort; label: string }[] = [
  { key: 'comprehensive', label: '综合' },
  { key: 'latest', label: '最新' },
  { key: 'earliest', label: '最早' },
];

const router = useRouter();

const PAGE_SIZE = 10;

const tab = ref<TabKey>('pending');
const sort = ref<ReviewSort>('comprehensive');
const loading = ref(false);

const list = ref<MyReviewVO[]>([]);
const total = ref(0);
const page = ref(1);
const summary = ref({ reviewCount: 0, viewCount: 0, likeCount: 0 });

const pendingList = ref<MyPendingReviewVO[]>([]);
const pendingTotal = ref(0);
const pendingPage = ref(1);

const profile = ref<UserMeVO | null>(null);
const nickname = computed(() => profile.value?.nickname || '森特会员');
const avatarChar = computed(() => nickname.value.slice(0, 1));

/** 千分位：浏览数可能上千，直接堆数字看不清 */
function count(value: number | string | null | undefined): string {
  return Number(value ?? 0).toLocaleString('zh-CN');
}

const stats = computed(() => [
  { key: 'written', label: '已写评论', value: count(summary.value.reviewCount) },
  { key: 'views', label: '浏览数', value: count(summary.value.viewCount) },
  { key: 'likes', label: '获赞数', value: count(summary.value.likeCount) },
  { key: 'pending', label: '待评价', value: count(pendingTotal.value) },
]);

function goProduct(productId: string): void {
  if (!productId) return;
  router.push(`/product/${productId}`);
}

/** 去评价：商品详情页才有评价表单，新标签页打开（不打断评价中心的浏览） */
function goReview(productId: string): void {
  if (!productId) return;
  openInNewTab(router, { path: `/product/${productId}` });
}

/** 评价图预览：与详情页评价区同款（受浏览器拦截时降级成提示） */
function preview(fileId: string): void {
  const url = fileApi.url(fileId);
  if (!url) return;
  ElMessageBox.alert(`<img src="${url}" style="max-width:100%;border-radius:6px" alt="评价图" />`,
    '评价图片', { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' }).catch(() => { /* 关闭 */ });
}

async function fetchWritten(target: number): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(reviewApi.mine({ page: target, size: PAGE_SIZE, sort: sort.value }));
    const rows = res?.list ?? [];
    list.value = target === 1 ? rows : [...list.value, ...rows];
    total.value = Number(res?.total ?? 0);
    page.value = target;
    summary.value = {
      reviewCount: Number(res?.summary?.reviewCount ?? 0),
      viewCount: Number(res?.summary?.viewCount ?? 0),
      likeCount: Number(res?.summary?.likeCount ?? 0),
    };
  } catch (e) {
    if (target === 1) {
      list.value = [];
      total.value = 0;
      summary.value = { reviewCount: 0, viewCount: 0, likeCount: 0 };
    }
    ElMessage.error((e as Error).message || '加载评价失败');
  } finally {
    loading.value = false;
  }
}

async function fetchPending(target: number): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(reviewApi.pending({ page: target, size: PAGE_SIZE }));
    const rows = res?.list ?? [];
    pendingList.value = target === 1 ? rows : [...pendingList.value, ...rows];
    pendingTotal.value = Number(res?.total ?? 0);
    pendingPage.value = target;
  } catch (e) {
    if (target === 1) {
      pendingList.value = [];
      pendingTotal.value = 0;
    }
    ElMessage.error((e as Error).message || '加载待评价失败');
  } finally {
    loading.value = false;
  }
}

async function loadMore(): Promise<void> {
  if (loading.value) return;
  await fetchWritten(page.value + 1);
}

async function loadMorePending(): Promise<void> {
  if (loading.value) return;
  await fetchPending(pendingPage.value + 1);
}

function switchTab(next: TabKey): void {
  if (tab.value === next) return;
  tab.value = next;
  if (next === 'pending') {
    void fetchPending(1);
  } else {
    void fetchWritten(1);
  }
}

/** 换排序要回第一页重取：排序在服务端做，不能只重排已加载的几条 */
function setSort(next: ReviewSort): void {
  if (sort.value === next) return;
  sort.value = next;
  void fetchWritten(1);
}

async function removeReview(item: MyReviewVO): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后这条评价将不再展示，且可以重新评价该商品。', '删除评价', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return; // 用户取消
  }
  try {
    await unwrap(reviewApi.remove(item.reviewId));
    ElMessage.success('评价已删除');
    await Promise.all([fetchWritten(1), fetchPending(1)]);
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败');
  }
}

onMounted(async () => {
  // 两个页签的数据一次取全：页签角标要显示待评价条数，切页签时直接有数据，不用等第二次请求
  await Promise.all([
    fetchPending(1),
    fetchWritten(1),
    (async () => {
      try {
        profile.value = await unwrap(profileApi.me());
      } catch {
        profile.value = null;
      }
    })(),
  ]);
});
</script>

<style scoped>
.rv-page {
  /* 外壳（layout/mine.vue）已控制内容区宽度，这里不再自建版心 */
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---------- 用户条 ---------- */
.rv-hero {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #fafbfc;
  border-radius: 8px;
  padding: 18px 24px;
  flex-wrap: wrap;
}
.rv-hero__who {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.rv-hero__avatar {
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: 50%;
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rv-hero__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--sent-text);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rv-hero__stats {
  display: flex;
  align-items: center;
  gap: 44px;
  margin-left: auto;
  flex-wrap: wrap;
}
.rv-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 64px;
}
.rv-stat__num {
  font-size: 20px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--sent-text);
  font-variant-numeric: tabular-nums;
}
.rv-stat__label {
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 页签 + 排序 ---------- */
.rv-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--sent-border);
  padding-bottom: 10px;
}
.rv-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rv-tab {
  position: relative;
  border: 1px solid transparent;
  background: transparent;
  padding: 5px 14px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  color: var(--sent-text-2);
}
.rv-tab:hover {
  color: var(--sent-primary);
}
.rv-tab--on {
  color: var(--sent-accent-dark);
  border-color: #ffc9a3;
  background: #fff7f2;
  font-weight: 600;
}
.rv-tab__num {
  font-style: normal;
  font-size: 11px;
  color: var(--sent-accent-dark);
  background: var(--sent-accent-soft);
  border-radius: 9px;
  padding: 1px 6px;
  margin-left: 4px;
}
.rv-sort {
  display: flex;
  align-items: center;
  gap: 20px;
}
.rv-sort__item {
  border: none;
  background: transparent;
  padding: 2px 0;
  font-size: 13px;
  color: var(--sent-muted);
  cursor: pointer;
}
.rv-sort__item:hover {
  color: var(--sent-text);
}
.rv-sort__item--on {
  color: var(--sent-accent-dark);
  font-weight: 600;
}

/* ---------- 待评价行 ---------- */
.rv-pending {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.rv-pending__row {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) 100px 84px;
  align-items: center;
  gap: 14px;
  padding: 16px 4px;
  border-bottom: 1px solid var(--sent-border);
}
.rv-pending__row:last-child {
  border-bottom: none;
}
.rv-pending__thumb {
  width: 76px;
  height: 76px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}
.rv-pending__thumb :deep(.thumb) {
  width: 76px;
  height: 76px;
  border-radius: 8px;
}
.rv-pending__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.rv-pending__name {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 14px;
  color: var(--sent-text);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rv-pending__name:hover {
  color: var(--sent-primary);
}
.rv-pending__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: var(--sent-muted);
}
.rv-pending__price {
  font-size: 15px;
  font-weight: 700;
  color: var(--sent-price);
  text-align: right;
}
.rv-btn {
  height: 30px;
  padding: 0 16px;
  border-radius: 4px;
  border: 1px solid var(--sent-border);
  background: #fff;
  color: var(--sent-text-2);
  font-size: 13px;
  cursor: pointer;
}
.rv-btn--primary {
  background: var(--sent-accent);
  border-color: var(--sent-accent);
  color: #fff;
}
.rv-btn--primary:hover {
  background: var(--sent-accent-dark);
  border-color: var(--sent-accent-dark);
}

/* ---------- 已评价列表 ---------- */
.rv-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.rv-item {
  display: flex;
  gap: 14px;
  padding: 16px 4px;
  border-bottom: 1px solid var(--sent-border);
}
.rv-item:last-child {
  border-bottom: none;
}
.rv-item__thumb {
  flex: none;
  width: 76px;
  height: 76px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}
.rv-item__thumb :deep(.thumb) {
  width: 76px;
  height: 76px;
  border-radius: 8px;
}
.rv-item__body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.rv-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.rv-item__name {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 14px;
  color: var(--sent-text);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.rv-item__name:hover {
  color: var(--sent-primary);
}
.rv-item__hidden,
.rv-item__anon {
  flex: none;
  font-size: 11px;
  border-radius: 3px;
  padding: 1px 6px;
  line-height: 16px;
}
.rv-item__hidden {
  color: #e34d59;
  background: #fdecee;
}
.rv-item__anon {
  color: var(--sent-muted);
  background: #f3f4f6;
}

.rv-item__stars {
  display: flex;
  gap: 1px;
  color: #dfe3e8;
  font-size: 13px;
  line-height: 1;
}
.rv-item__star--on {
  color: #ff9f1a;
}

.rv-item__content {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--sent-text-2);
  word-break: break-word;
}

.rv-item__images {
  display: flex;
  gap: 8px;
}
.rv-item__image {
  width: 64px;
  height: 64px;
  padding: 0;
  border: none;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f6f7;
  cursor: zoom-in;
}
.rv-item__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.rv-item__foot {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--sent-muted);
}
.rv-item__metric {
  white-space: nowrap;
}
.rv-item__del {
  margin-left: auto;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12.5px;
  color: var(--sent-muted);
  cursor: pointer;
}
.rv-item__del:hover {
  color: #e34d59;
}

/* ---------- 空态 / 加载 / 更多 ---------- */
.rv-hint {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.rv-empty {
  padding: 60px 0;
  text-align: center;
  color: var(--sent-muted);
  font-size: 13px;
}
.rv-empty__icon {
  font-size: 34px;
  opacity: 0.35;
  margin-bottom: 8px;
}
.rv-empty p {
  margin: 0;
}
.rv-empty__sub {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.85;
}
.rv-empty__btn {
  display: inline-block;
  margin-top: 14px;
  border: 1px solid var(--sent-border);
  border-radius: 16px;
  padding: 6px 20px;
  font-size: 13px;
  color: var(--sent-text-2);
  background: #fff;
}
.rv-empty__btn:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}

.rv-more {
  text-align: center;
  padding: 6px 0 10px;
}
.rv-more__btn {
  border: 1px solid var(--sent-border);
  background: #fff;
  border-radius: 16px;
  padding: 7px 26px;
  font-size: 13px;
  color: var(--sent-text-2);
  cursor: pointer;
}
.rv-more__btn:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}

@media (max-width: 820px) {
  .rv-hero__stats {
    gap: 24px;
    margin-left: 0;
  }
  .rv-pending__row {
    grid-template-columns: 64px minmax(0, 1fr) 84px;
  }
  .rv-pending__price {
    display: none;
  }
}
</style>
