<template>
  <div class="fav-page">
    <!-- 顶部条：标题 + 数量 + 去逛逛 -->
    <div class="fav-bar">
      <h1 class="fav-bar__title">我的收藏</h1>
      <span class="fav-bar__count">共 {{ total }} 件</span>
      <router-link to="/products" class="fav-bar__more">继续逛逛 ›</router-link>
    </div>

    <!-- 筛选：全部 / 在售 / 已失效（下架商品保留，让用户自己决定删不删） -->
    <div class="fav-tabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        type="button"
        class="fav-tab"
        :class="{ 'fav-tab--on': active === t.key }"
        @click="selectTab(t.key)"
      >
        {{ t.label }}<i v-if="t.key === ''" class="fav-tab__num">{{ total }}</i>
      </button>
    </div>

    <div v-if="loading && list.length === 0" class="fav-hint">加载中…</div>

    <!-- 空态：区分「一件没收藏」和「筛选后为空」，后者别引导用户去逛商品 -->
    <div v-else-if="shown.length === 0" class="fav-empty">
      <div class="fav-empty__icon">☆</div>
      <p>{{ active === '' ? '还没有收藏任何商品' : '当前筛选下没有商品' }}</p>
      <router-link v-if="active === ''" to="/products" class="fav-empty__btn">去挑几件喜欢的</router-link>
    </div>

    <div v-else class="fav-grid">
      <div v-for="f in shown" :key="f.productId" class="fav-card" :class="{ 'fav-card--off': !f.onShelf }">
        <button type="button" class="fav-card__heart" title="取消收藏" @click.stop="unfavorite(f)">
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="M10 17s-6.5-4.2-6.5-8.2A3.8 3.8 0 0 1 10 6.1a3.8 3.8 0 0 1 6.5 2.7C16.5 12.8 10 17 10 17Z"
                  fill="currentColor" />
          </svg>
        </button>

        <div class="fav-card__thumb" @click="goDetail(f)">
          <ProductThumb :src="f.mainImage" :name="f.productName" :seed="f.productId" />
          <span v-if="!f.onShelf" class="fav-card__mask">已失效</span>
        </div>

        <div class="fav-card__body" @click="goDetail(f)">
          <div class="fav-card__name">{{ f.productName }}</div>
          <div class="fav-card__price">
            <span class="sym">¥</span>{{ money(f) }}
          </div>
          <div class="fav-card__meta">
            <span>{{ timeText(f.favoritedAt) }}</span>
            <span v-if="Number(f.priceCent) > 0" class="fav-card__tag">包邮</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="list.length < total" class="fav-more">
      <button type="button" class="fav-more__btn" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 我的收藏（收藏夹，2026-11-16）。
 *
 * 版式对齐淘宝「我的收藏」：顶部条 + 在售/失效筛选 + 五列卡片网格。
 * 数据全部走 `GET /api/favorites`：后端已带上商品标题、主图、最低在售价、收藏时间，
 * 前端不再逐条查商品（收藏夹常见几十件，N+1 会把接口拖成几秒）。
 *
 * 下架商品不静默消失：`onShelf=false` 的卡片灰显 + 「已失效」遮罩，心形按钮仍可点掉，
 * 否则用户会以为「收藏自己丢了」。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtCent, fmtDate } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { favoriteApi, type FavoriteVO } from '@/api/favorite';
import ProductThumb from '@/components/ProductThumb.vue';

const router = useRouter();

const PAGE_SIZE = 20;

const TABS = [
  { key: '', label: '全部' },
  { key: 'ON', label: '在售' },
  { key: 'OFF', label: '已失效' },
] as const;

const list = ref<FavoriteVO[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const active = ref<'' | 'ON' | 'OFF'>('');

/** 前端筛选：收藏夹量级小（一次 20 条、翻页累加），比再发一次带状态的请求更跟手 */
const shown = computed(() => {
  if (active.value === 'ON') return list.value.filter((f) => f.onShelf);
  if (active.value === 'OFF') return list.value.filter((f) => !f.onShelf);
  return list.value;
});

function money(f: FavoriteVO): string {
  const yuan = f.priceYuan;
  if (yuan) return String(yuan);
  return fmtCent(Number(f.priceCent ?? 0));
}

function timeText(ms: number | string | null | undefined): string {
  const n = Number(ms ?? 0);
  return n > 0 ? `${fmtDate(n)} 收藏` : '刚刚收藏';
}

function goDetail(f: FavoriteVO): void {
  if (!f.onShelf) {
    ElMessage.warning('该商品已下架，无法查看详情');
    return;
  }
  router.push(`/product/${f.productId}`);
}

/** 取消收藏：先落本地再调接口，失败回滚（避免整页重载导致滚动位置丢失） */
async function unfavorite(f: FavoriteVO): Promise<void> {
  const index = list.value.findIndex((i) => i.productId === f.productId);
  if (index < 0) return;
  const backup = list.value[index];
  list.value.splice(index, 1);
  total.value = Math.max(0, total.value - 1);
  try {
    await unwrap(favoriteApi.remove(f.productId));
    ElMessage.success('已取消收藏');
  } catch (e) {
    list.value.splice(index, 0, backup);
    total.value += 1;
    ElMessage.error((e as Error).message || '取消收藏失败');
  }
}

function selectTab(key: '' | 'ON' | 'OFF'): void {
  active.value = key;
}

async function fetchPage(target: number): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(favoriteApi.list({ page: target, size: PAGE_SIZE }));
    const rows = res?.list ?? [];
    list.value = target === 1 ? rows : [...list.value, ...rows];
    total.value = Number(res?.total ?? 0);
    page.value = target;
  } catch (e) {
    if (target === 1) {
      list.value = [];
      total.value = 0;
    }
    ElMessage.error((e as Error).message || '加载收藏失败');
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
.fav-page {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: 14px 0 36px;
}

/* ---------- 顶部条 ---------- */
.fav-bar {
  display: flex;
  align-items: baseline;
  gap: 12px;
  background: #fff;
  border-radius: 10px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.fav-bar__title {
  margin: 0;
  font-size: 18px;
  color: var(--sent-text);
}
.fav-bar__count {
  font-size: 13px;
  color: var(--sent-muted);
}
.fav-bar__more {
  margin-left: auto;
  font-size: 13px;
  color: var(--sent-primary);
}
.fav-bar__more:hover {
  color: var(--sent-accent);
}

/* ---------- 筛选 ---------- */
.fav-tabs {
  display: flex;
  gap: 6px;
  margin: 12px 0;
}
.fav-tab {
  border: 1px solid transparent;
  background: #fff;
  border-radius: 16px;
  padding: 6px 16px;
  font-size: 13px;
  color: var(--sent-text-2);
  cursor: pointer;
  transition: all 0.15s;
}
.fav-tab:hover {
  color: var(--sent-primary);
}
.fav-tab--on {
  color: var(--sent-primary);
  border-color: var(--sent-primary);
  font-weight: 700;
}
.fav-tab__num {
  font-style: normal;
  margin-left: 4px;
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 网格 ---------- */
.fav-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}
.fav-card {
  position: relative;
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid transparent;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.fav-card:hover {
  border-color: var(--sent-primary);
  box-shadow: 0 8px 20px rgba(18, 102, 79, 0.12);
  transform: translateY(-2px);
}
.fav-card--off {
  opacity: 0.82;
}
.fav-card__thumb {
  position: relative;
  height: 148px;
  cursor: pointer;
}
.fav-card__thumb :deep(.thumb) {
  height: 148px;
  border-radius: 10px 10px 0 0;
}
.fav-card__mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.42);
  color: #fff;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.fav-card__body {
  padding: 10px 12px 12px;
  cursor: pointer;
}
.fav-card__name {
  font-size: 13px;
  line-height: 1.5;
  color: var(--sent-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 39px;
}
.fav-card__price {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 700;
  color: var(--sent-accent-dark);
}
.fav-card__price .sym {
  font-size: 12px;
  margin-right: 1px;
}
.fav-card__meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--sent-muted);
}
.fav-card__tag {
  border: 1px solid #f0d0b6;
  color: var(--sent-accent-dark);
  border-radius: 3px;
  padding: 0 3px;
  font-size: 10px;
  flex: none;
  white-space: nowrap;
}

/* 心形：默认淡出，hover 才明显，避免遮挡商品图 */
.fav-card__heart {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: #e34d59;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0.9;
  transition: transform 0.15s, opacity 0.15s;
}
.fav-card__heart svg {
  width: 16px;
  height: 16px;
}
.fav-card__heart:hover {
  transform: scale(1.1);
  opacity: 1;
}

/* ---------- 空态 / 提示 ---------- */
.fav-hint {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.fav-empty {
  background: #fff;
  border-radius: 10px;
  padding: 56px 0;
  text-align: center;
  color: var(--sent-muted);
  font-size: 13px;
}
.fav-empty__icon {
  font-size: 40px;
  opacity: 0.35;
  margin-bottom: 10px;
}
.fav-empty__btn {
  display: inline-block;
  margin-top: 14px;
  background: var(--sent-accent);
  color: #fff;
  border-radius: 16px;
  padding: 7px 20px;
  font-size: 13px;
}
.fav-empty__btn:hover {
  background: var(--sent-accent-dark);
}

.fav-more {
  text-align: center;
  padding: 22px 0 0;
}
.fav-more__btn {
  border: 1px solid var(--sent-border);
  background: #fff;
  border-radius: 16px;
  padding: 7px 26px;
  font-size: 13px;
  color: var(--sent-text-2);
  cursor: pointer;
}
.fav-more__btn:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}

@media (max-width: 1080px) {
  .fav-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
