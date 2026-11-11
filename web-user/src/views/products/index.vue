<template>
  <div class="container products">
    <div class="products__head">
      <h2>{{ pageTitle }}</h2>
      <span class="products__count">共 {{ total }} 件商品</span>
    </div>

    <!-- 横向筛选栏 -->
    <div class="filter-bar">
      <div class="filter-bar__tabs">
        <button
          class="filter-tab"
          :class="{ active: sortBy === 'default' }"
          @click="setSort('default')"
        >
          综合
        </button>
        <button
          class="filter-tab"
          :class="{ active: sortBy === 'sales' }"
          @click="setSort('sales')"
        >
          销量
        </button>
        <button
          class="filter-tab"
          :class="{ active: sortBy === 'price' }"
          @click="togglePrice"
        >
          价格
          <span class="filter-tab__arrow" :class="{ on: sortBy === 'price' }">
            {{ priceAsc ? '↑' : '↓' }}
          </span>
        </button>
      </div>

      <div class="filter-bar__search">
        <el-input
          v-model="keyword"
          placeholder="在结果中搜索"
          size="default"
          clearable
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #append>
            <el-button @click="reload">搜索</el-button>
          </template>
        </el-input>
      </div>
    </div>

    <div v-if="loading" class="list-hint">加载中…</div>
    <el-empty v-else-if="products.length === 0" description="暂无相关商品" />

    <template v-else>
      <div class="goods-grid">
        <GoodsCard
          v-for="p in sortedProducts"
          :key="p.productId"
          :product="p"
          @click="goDetail(p.productId)"
        />
      </div>

      <el-pagination
        v-if="total > size"
        class="list-page"
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPage"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { unwrap } from '@/utils/http';
import { productApi, type CategoryVO, type CatalogProductVO } from '@/api/product';
import GoodsCard from '@/components/GoodsCard.vue';

const route = useRoute();
const router = useRouter();

const keyword = ref<string>((route.query.keyword as string) || '');
const categoryId = ref<string | undefined>((route.query.categoryId as string) || undefined);
const categories = ref<CategoryVO[]>([]);
const products = ref<CatalogProductVO[]>([]);
const loading = ref(false);
const page = ref(1);
const size = ref(12);
const total = ref(0);

type SortKey = 'default' | 'sales' | 'price';
const sortBy = ref<SortKey>('default');
const priceAsc = ref(true);

const pageTitle = computed(() => {
  if (!categoryId.value) return keyword.value ? `搜索「${keyword.value}」` : '全部商品';
  const cat = categories.value.find((c) => String(c.categoryId) === String(categoryId.value));
  return cat ? cat.name : '全部商品';
});

/** 当前页客户端排序（后端暂未开放排序参数） */
const sortedProducts = computed<CatalogProductVO[]>(() => {
  const list = [...products.value];
  if (sortBy.value === 'sales') {
    return list.sort((a, b) => Number(b.salesCount ?? 0) - Number(a.salesCount ?? 0));
  }
  if (sortBy.value === 'price') {
    return list.sort((a, b) => (minPrice(a) - minPrice(b)) * (priceAsc.value ? 1 : -1));
  }
  return list;
});

function minPrice(p: CatalogProductVO): number {
  const sku = p.skus?.find((s) => s.onShelf) ?? p.skus?.[0];
  return sku?.price.retailPriceCent ?? 0;
}

function goDetail(id: string): void {
  router.push(`/product/${id}`);
}

function setSort(key: SortKey): void {
  sortBy.value = key;
}

function togglePrice(): void {
  if (sortBy.value === 'price') {
    priceAsc.value = !priceAsc.value;
  } else {
    sortBy.value = 'price';
    priceAsc.value = true;
  }
}

async function reload(): Promise<void> {
  page.value = 1;
  await load();
}

async function onPage(p: number): Promise<void> {
  page.value = p;
  await load();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(productApi.list({
      keyword: keyword.value || undefined,
      categoryId: categoryId.value ? Number(categoryId.value) : null,
      page: page.value,
      size: size.value,
    }));
    products.value = res.list ?? [];
    total.value = res.total ?? 0;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  try {
    categories.value = (await unwrap(productApi.categories())) ?? [];
  } catch {
    categories.value = [];
  }
  await load();
});
</script>

<style scoped>
.products {
  padding-top: 4px;
}
.products__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}
.products__head h2 {
  font-size: 22px;
  margin: 0;
  color: var(--sent-text);
}
.products__count {
  font-size: 13px;
  color: var(--sent-muted);
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 10px 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.filter-bar__tabs {
  display: flex;
  align-items: center;
  gap: 6px;
}
.filter-tab {
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: var(--sent-text-2);
  padding: 7px 18px;
  border-radius: 6px;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.filter-tab:hover {
  color: var(--sent-primary);
}
.filter-tab.active {
  background: var(--sent-primary);
  color: #fff;
}
.filter-tab__arrow {
  font-size: 12px;
  opacity: 0.55;
}
.filter-tab.active .filter-tab__arrow {
  opacity: 1;
}
.filter-bar__search {
  width: 280px;
}

.list-page {
  justify-content: center;
  padding: 28px 0 8px;
}
</style>
