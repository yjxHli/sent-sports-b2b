<template>
  <div class="fp-page">
    <div class="fp-bar">
      <h1 class="fp-bar__title">我的足迹</h1>
      <span class="fp-bar__count">共 {{ total }} 件商品</span>
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

    <template v-else>
      <!-- 按浏览日期分组，和淘宝「今天 / 昨天 / 更早」一致 -->
      <section v-for="group in groups" :key="group.label" class="fp-group">
        <header class="fp-group__head">
          <h3>{{ group.label }}</h3>
          <span>{{ group.items.length }} 件</span>
        </header>
        <div class="goods-grid">
          <div v-for="f in group.items" :key="f.productId" class="fp-card" :class="{ 'fp-card--off': !f.onShelf }">
            <button
              type="button"
              class="fp-card__heart"
              :class="{ 'fp-card__heart--on': f.favorited }"
              :title="f.favorited ? '取消收藏' : '加入收藏'"
              @click.stop="toggleFavorite(f)"
            >
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M10 17s-6.5-4.2-6.5-8.2A3.8 3.8 0 0 1 10 6.1a3.8 3.8 0 0 1 6.5 2.7C16.5 12.8 10 17 10 17Z"
                      :fill="f.favorited ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.2" />
              </svg>
            </button>

            <div class="fp-card__thumb" @click="goDetail(f)">
              <ProductThumb :src="f.mainImage" :name="f.productName" :seed="f.productId" />
              <span v-if="!f.onShelf" class="fp-card__mask">已失效</span>
            </div>
            <div class="fp-card__body" @click="goDetail(f)">
              <div class="fp-card__name">{{ f.productName }}</div>
              <div class="fp-card__price"><span class="sym">¥</span>{{ money(f) }}</div>
              <div class="fp-card__meta">
                <span>{{ viewText(f) }}</span>
                <span v-if="f.favorited" class="fp-card__tag">已收藏</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div v-if="list.length < total" class="fp-more">
        <button type="button" class="fp-more__btn" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 我的足迹（2026-11-16）。
 *
 * 与收藏夹的区别：足迹是「被动记录」的浏览历史，所以重点展示「什么时候看的、看了几次」，
 * 并按日期分组（今天 / 昨天 / 更早），和淘宝一致。
 *
 * 卡片右上角的心形可以直接收藏/取消收藏——足迹里看到想买却漏收藏的商品，就地补上，
 * 不必先点进详情页。`favorited` 由后端一次批量查回，不是逐条请求。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtCent, fmtDate } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { footprintApi, type FootprintVO } from '@/api/footprint';
import { favoriteApi } from '@/api/favorite';
import ProductThumb from '@/components/ProductThumb.vue';

const router = useRouter();

const PAGE_SIZE = 20;

const list = ref<FootprintVO[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);

function money(f: FootprintVO): string {
  return f.priceYuan ? String(f.priceYuan) : fmtCent(Number(f.priceCent ?? 0));
}

function viewText(f: FootprintVO): string {
  const times = Number(f.viewCount ?? 1);
  return times > 1 ? `浏览 ${times} 次` : '浏览过';
}

/** 日期分组：先按毫秒时间戳的「天」聚合，保持后端返回的倒序（组内不再重排） */
const groups = computed(() => {
  const today = startOfDay(Date.now());
  const yesterday = today - 24 * 60 * 60 * 1000;
  const buckets: { label: string; items: FootprintVO[] }[] = [];
  const push = (label: string, item: FootprintVO): void => {
    const found = buckets.find((b) => b.label === label);
    if (found) found.items.push(item);
    else buckets.push({ label, items: [item] });
  };
  for (const item of list.value) {
    const t = Number(item.lastViewedAt ?? 0);
    if (t >= today) push('今天', item);
    else if (t >= yesterday) push('昨天', item);
    else push(t > 0 ? fmtDate(t) : '更早', item);
  }
  return buckets;
});

function startOfDay(ms: number): number {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function goDetail(f: FootprintVO): void {
  if (!f.onShelf) {
    ElMessage.warning('该商品已下架，无法查看详情');
    return;
  }
  router.push(`/product/${f.productId}`);
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
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: 14px 0 36px;
}

.fp-bar {
  display: flex;
  align-items: baseline;
  gap: 12px;
  background: #fff;
  border-radius: 10px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
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

.fp-group {
  margin-top: 16px;
}
.fp-group__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
  padding-left: 2px;
}
.fp-group__head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--sent-text);
}
.fp-group__head span {
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 网格：统一走全局 .goods-grid（auto-fill，1440 版心 → 6 列） ---------- */
.fp-card {
  position: relative;
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid transparent;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.fp-card:hover {
  border-color: var(--sent-primary);
  box-shadow: 0 8px 20px rgba(18, 102, 79, 0.12);
  transform: translateY(-2px);
}
.fp-card--off {
  opacity: 0.82;
}
.fp-card__thumb {
  position: relative;
  height: 148px;
  cursor: pointer;
}
.fp-card__thumb :deep(.thumb) {
  height: 148px;
  border-radius: 10px 10px 0 0;
}
.fp-card__mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.42);
  color: #fff;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.fp-card__body {
  padding: 10px 12px 12px;
  cursor: pointer;
}
.fp-card__name {
  font-size: 13px;
  line-height: 1.5;
  color: var(--sent-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 39px;
}
.fp-card__price {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 700;
  color: var(--sent-accent-dark);
}
.fp-card__price .sym {
  font-size: 12px;
  margin-right: 1px;
}
.fp-card__meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--sent-muted);
}
.fp-card__tag {
  color: var(--sent-primary);
  flex: none;
  white-space: nowrap;
}

.fp-card__heart {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: #b9bfc4;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.15s, color 0.15s;
}
.fp-card__heart svg {
  width: 16px;
  height: 16px;
}
.fp-card__heart--on {
  color: #e34d59;
}
.fp-card__heart:hover {
  transform: scale(1.1);
}

.fp-hint {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.fp-empty {
  background: #fff;
  border-radius: 10px;
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
  padding: 22px 0 0;
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

/* 窄屏列数由全局 .goods-grid 的 auto-fill 自动降列，原 1080px 三列断点已冗余，删除 */
</style>
