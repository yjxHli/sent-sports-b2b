<template>
  <div class="fp-page">
    <div class="fp-bar">
      <h1 class="fp-bar__title">我的足迹</h1>
      <span class="fp-bar__count">近一个月浏览 {{ total }} 件商品</span>
      <button
        v-if="total > 0"
        type="button"
        class="fp-bar__clear"
        @click="clearAll"
      >清空足迹</button>
      <router-link to="/products" class="fp-bar__more">继续逛逛 ›</router-link>
    </div>

    <div v-if="loading && list.length === 0" class="fp-hint">加载中…</div>

    <div v-else-if="list.length === 0" class="fp-empty">
      <div class="fp-empty__icon">👣</div>
      <p>最近还没有浏览记录</p>
      <router-link to="/products" class="fp-empty__btn">去逛逛</router-link>
    </div>

    <!-- 2026-11-28 用户要求：改成「几号看了什么」的时间轴，只保留一个月（后端同样只回近 30 天） -->
    <div v-else class="fp-timeline">
      <section v-for="group in dayGroups" :key="group.key" class="fp-day" data-testid="footprint-day">
        <header class="fp-day__head">
          <span class="fp-day__date">{{ group.dateLabel }}</span>
          <span class="fp-day__week">{{ group.weekLabel }}</span>
          <span class="fp-day__count">{{ group.items.length }} 件</span>
        </header>

        <ul class="fp-day__items">
          <li v-for="f in group.items" :key="f.productId" class="fp-line" data-testid="footprint-item">
            <span class="fp-line__time">{{ timeOf(f) }}</span>
            <span class="fp-line__dot" aria-hidden="true" />

            <button
              type="button"
              class="fp-line__thumb"
              :class="{ 'fp-line__thumb--off': !f.onShelf }"
              :title="f.onShelf ? `查看「${f.productName}」` : '商品已失效'"
              @click="goDetail(f)"
            >
              <ProductThumb :src="f.mainImage" :name="f.productName" :seed="f.productId" />
            </button>

            <div class="fp-line__info">
              <button type="button" class="fp-line__name" @click="goDetail(f)">
                {{ f.productName }}
              </button>
              <span class="fp-line__meta">
                <b class="fp-line__price">¥{{ money(f) }}</b>
                <span class="fp-line__sep">·</span>
                <span>{{ viewText(f) }}</span>
                <span v-if="!f.onShelf" class="fp-line__off">已失效</span>
              </span>
            </div>

            <button
              type="button"
              class="fp-line__heart"
              :class="{ 'fp-line__heart--on': f.favorited }"
              :title="f.favorited ? '取消收藏' : '加入收藏'"
              @click.stop="toggleFavorite(f)"
            >
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M10 17s-6.5-4.2-6.5-8.2A3.8 3.8 0 0 1 10 6.1a3.8 3.8 0 0 1 6.5 2.7C16.5 12.8 10 17 10 17Z"
                      :fill="f.favorited ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.2" />
              </svg>
            </button>
          </li>
        </ul>
      </section>

      <div v-if="list.length < total" class="fp-more">
        <button type="button" class="fp-more__btn" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 我的足迹（2026-11-16；2026-11-28 改为时间轴）。
 *
 * 与收藏夹的区别：足迹是「被动记录」的浏览历史，所以这页回答的是「几号看了什么」——
 * 按天分组，每行左边是时间（HH:mm），右边是商品，行尾可以就地补收藏。
 *
 * 只保留一个月：后端读接口按 `last_viewed_at >= now - 30 天` 过滤，
 * 并由 `FootprintCleanupJob` 每天物理删除过期记录；前端不再做时间过滤，
 * 免得「页面藏了但库里还在」和「库里删了页面还算着」两套口径打架。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtCent } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { footprintApi, type FootprintVO } from '@/api/footprint';
import { favoriteApi } from '@/api/favorite';
import ProductThumb from '@/components/ProductThumb.vue';
import { openInNewTab } from '@/utils/link';

const router = useRouter();

const PAGE_SIZE = 20;

const list = ref<FootprintVO[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function money(f: FootprintVO): string {
  return f.priceYuan ? String(f.priceYuan) : fmtCent(Number(f.priceCent ?? 0));
}

function viewText(f: FootprintVO): string {
  const times = Number(f.viewCount ?? 1);
  return times > 1 ? `浏览 ${times} 次` : '浏览过';
}

/** 一行右侧的时间：HH:mm（同一天看过多件时，按时间排下来才像「时间轴」） */
function timeOf(f: FootprintVO): string {
  const t = Number(f.lastViewedAt ?? 0);
  if (!t) return '--:--';
  const d = new Date(t);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/**
 * 按「天」分组：组头是「今天 / 昨天 / 11-26 + 周三」，组内保持后端倒序。
 * 用本地日期串做 key（不用时间戳除法），避免跨时区/夏令时把两天并成一天。
 */
const dayGroups = computed(() => {
  const buckets: { key: string; dateLabel: string; weekLabel: string; items: FootprintVO[] }[] = [];
  const todayKey = dateKey(Date.now());
  const yesterdayKey = dateKey(Date.now() - 24 * 60 * 60 * 1000);
  for (const item of list.value) {
    const t = Number(item.lastViewedAt ?? 0) || Date.now();
    const key = dateKey(t);
    let bucket = buckets.find((b) => b.key === key);
    if (!bucket) {
      const d = new Date(t);
      bucket = {
        key,
        dateLabel: key === todayKey ? '今天' : key === yesterdayKey ? '昨天' : `${d.getMonth() + 1}-${d.getDate()}`,
        weekLabel: WEEK[d.getDay()] ?? '',
        items: [],
      };
      buckets.push(bucket);
    }
    bucket.items.push(item);
  }
  return buckets;
});

function dateKey(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
}

function goDetail(f: FootprintVO): void {
  if (!f.onShelf) {
    ElMessage.warning('该商品已下架，无法查看详情');
    return;
  }
  openInNewTab(router, { path: `/product/${f.productId}` });
}

/** 就地收藏 / 取消收藏：先改本地状态，失败回滚 */
async function toggleFavorite(f: FootprintVO): Promise<void> {
  const next = !f.favorited;
  f.favorited = next;
  try {
    if (next) await unwrap(favoriteApi.add(f.productId));
    else await unwrap(favoriteApi.remove(f.productId));
    ElMessage.success(next ? '已加入收藏' : '已取消收藏');
  } catch (e) {
    f.favorited = !next;
    ElMessage.error((e as Error).message || '操作失败');
  }
}

async function clearAll(): Promise<void> {
  try {
    await ElMessageBox.confirm('清空后浏览记录不可恢复，确定清空？', '清空足迹', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    const deleted = await unwrap(footprintApi.clear());
    ElMessage.success(`已清空 ${Number(deleted ?? 0)} 条足迹`);
    list.value = [];
    total.value = 0;
    page.value = 1;
  } catch (e) {
    ElMessage.error((e as Error).message || '清空失败');
  }
}

async function fetchPage(target: number): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(footprintApi.list({ page: target, size: PAGE_SIZE }));
    const rows = res?.list ?? [];
    list.value = target === 1 ? rows : [...list.value, ...rows];
    total.value = Number(res?.total ?? 0);
    page.value = target;
  } catch (e) {
    if (target === 1) {
      list.value = [];
      total.value = 0;
    }
    ElMessage.error((e as Error).message || '加载足迹失败');
  } finally {
    loading.value = false;
  }
}

async function loadMore(): Promise<void> {
  if (loading.value) return;
  await fetchPage(page.value + 1);
}

onMounted(() => {
  void fetchPage(1);
});
</script>

<style scoped>
.fp-page {
  width: 100%;
}

.fp-bar {
  display: flex;
  align-items: baseline;
  gap: 12px;
  background: #fff;
  border-radius: 8px;
  padding: 6px 0 14px;
}
.fp-bar__title {
  margin: 0;
  font-size: 18px;
  color: var(--sent-text);
}
.fp-bar__count {
  font-size: 13px;
  color: var(--sent-muted);
}
.fp-bar__clear {
  margin-left: auto;
  border: 1px solid var(--sent-border);
  background: #fff;
  border-radius: 14px;
  padding: 4px 12px;
  font-size: 12px;
  color: var(--sent-muted);
  cursor: pointer;
}
.fp-bar__clear:hover {
  border-color: #e34d59;
  color: #e34d59;
}
.fp-bar__more {
  font-size: 13px;
  color: var(--sent-primary);
}
.fp-bar__more:hover {
  color: var(--sent-accent);
}

/* ---------- 时间轴 ---------- */
.fp-timeline {
  display: flex;
  flex-direction: column;
}
.fp-day {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 0 10px;
  padding-bottom: 8px;
}
.fp-day__head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: 10px;
  text-align: right;
}
.fp-day__date {
  font-size: 15px;
  font-weight: 700;
  color: var(--sent-text);
  font-variant-numeric: tabular-nums;
}
.fp-day__week {
  font-size: 12px;
  color: var(--sent-muted);
}
.fp-day__count {
  font-size: 11px;
  color: #b3b9c2;
}

/* 竖线画在商品列左侧，按天连续，视觉上是一条不断的时间线 */
.fp-day__items {
  position: relative;
  list-style: none;
  margin: 0;
  padding: 0 0 0 20px;
  border-left: 1px solid var(--sent-border);
}
.fp-line {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
}
.fp-line + .fp-line {
  border-top: 1px dashed #f2f3f5;
}
.fp-line__time {
  flex: none;
  width: 42px;
  font-size: 12px;
  color: var(--sent-muted);
  font-variant-numeric: tabular-nums;
}
/* 时间轴节点：压在竖线上 */
.fp-line__dot {
  position: absolute;
  left: -25px;
  top: 50%;
  transform: translateY(-50%);
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--sent-primary);
}
.fp-line:first-child .fp-line__dot {
  border-color: var(--sent-accent);
}
.fp-line__thumb {
  flex: none;
  width: 64px;
  height: 64px;
  padding: 0;
  border: none;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f6f7;
  cursor: pointer;
}
.fp-line__thumb :deep(.thumb) {
  width: 64px;
  height: 64px;
  border-radius: 8px;
}
.fp-line__thumb--off {
  opacity: 0.6;
}
.fp-line__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.fp-line__name {
  border: none;
  background: transparent;
  padding: 0;
  text-align: left;
  font-size: 14px;
  line-height: 1.4;
  color: var(--sent-text);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.fp-line__name:hover {
  color: var(--sent-primary);
}
.fp-line__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--sent-muted);
}
.fp-line__price {
  font-size: 15px;
  font-weight: 700;
  color: var(--sent-price);
}
.fp-line__sep {
  color: #d7dbe0;
}
.fp-line__off {
  color: #e34d59;
}
.fp-line__heart {
  flex: none;
  width: 32px;
  height: 32px;
  border: 1px solid var(--sent-border);
  border-radius: 50%;
  background: #fff;
  color: #b9bfc4;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.fp-line__heart svg {
  width: 16px;
  height: 16px;
}
.fp-line__heart:hover {
  border-color: #e34d59;
  color: #e34d59;
}
.fp-line__heart--on {
  border-color: #f7d3d7;
  color: #e34d59;
}

.fp-hint {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.fp-empty {
  background: #fff;
  border-radius: 8px;
  padding: 56px 0;
  text-align: center;
  color: var(--sent-muted);
  font-size: 13px;
}
.fp-empty__icon {
  font-size: 40px;
  opacity: 0.35;
  margin-bottom: 10px;
}
.fp-empty__btn {
  display: inline-block;
  margin-top: 14px;
  background: var(--sent-accent);
  color: #fff;
  border-radius: 16px;
  padding: 7px 20px;
  font-size: 13px;
}
.fp-empty__btn:hover {
  background: var(--sent-accent-dark);
}

.fp-more {
  text-align: center;
  padding: 18px 0 0 86px;
}
.fp-more__btn {
  border: 1px solid var(--sent-border);
  background: #fff;
  border-radius: 16px;
  padding: 7px 26px;
  font-size: 13px;
  color: var(--sent-text-2);
  cursor: pointer;
}
.fp-more__btn:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}

/* 窄屏：日期列收窄、时间与竖线一起隐藏，保证商品名有足够宽度 */
@media (max-width: 720px) {
  .fp-day {
    grid-template-columns: minmax(0, 1fr);
  }
  .fp-day__head {
    flex-direction: row;
    align-items: baseline;
    gap: 8px;
    padding: 8px 0 4px;
    text-align: left;
  }
  .fp-day__items {
    padding-left: 0;
    border-left: none;
  }
  .fp-line__time,
  .fp-line__dot {
    display: none;
  }
  .fp-more {
    padding-left: 0;
  }
}
</style>
