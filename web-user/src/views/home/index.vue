<template>
  <div class="container home">
    <div class="home-hero">
      <!-- 左侧浮动分类导航 -->
      <aside class="cat-nav">
        <div class="cat-nav__title">全部商品分类</div>
        <ul class="cat-nav__list">
          <li
            v-for="c in categories"
            :key="c.categoryId"
            class="cat-nav__item"
            @click="goCategory(c.categoryId)"
          >
            <span>{{ c.name }}</span>
            <span class="cat-nav__arrow">›</span>
          </li>
          <li v-if="categories.length === 0" class="cat-nav__empty">暂无分类</li>
        </ul>
        <div class="cat-nav__all" @click="goProducts">查看全部商品 ›</div>
      </aside>

      <!-- 右侧主区域 -->
      <div class="home-main">
        <el-carousel height="380px" :interval="4500" arrow="hover" class="home-carousel">
          <el-carousel-item v-for="b in banners" :key="b.key">
            <div class="banner" :style="{ background: b.bg }">
              <div class="banner__inner">
                <div class="banner__tag">{{ b.tag }}</div>
                <h2 class="banner__title">{{ b.title }}</h2>
                <p class="banner__sub">{{ b.sub }}</p>
                <button class="banner__btn" @click="goProducts">立即选购</button>
              </div>
              <div class="banner__emoji">{{ b.emoji }}</div>
            </div>
          </el-carousel-item>
        </el-carousel>

        <!-- 精选好物 -->
        <section class="home-section">
          <div class="section-head">
            <h3>热销推荐</h3>
            <a class="section-more" @click="goProducts">查看全部 ›</a>
          </div>
          <div v-if="loading" class="list-hint">加载中…</div>
          <el-empty v-else-if="products.length === 0" description="暂无商品" />
          <div v-else class="goods-grid">
            <GoodsCard
              v-for="p in products"
              :key="p.productId"
              :product="p"
              @click="goDetail(p.productId)"
            />
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { unwrap } from '@/utils/http';
import { productApi, type CategoryVO, type CatalogProductVO } from '@/api/product';
import GoodsCard from '@/components/GoodsCard.vue';

const router = useRouter();
const categories = ref<CategoryVO[]>([]);
const products = ref<CatalogProductVO[]>([]);
const loading = ref(false);

const banners = [
  {
    key: 'b1',
    tag: '企业团购季',
    title: '专业运动装备 · 一站配齐',
    sub: '球类 / 健身 / 瑜伽 / 户外，支持 UV 定制与账期结算',
    bg: 'linear-gradient(120deg, #12664f 0%, #1c8a6b 55%, #2fae86 100%)',
    emoji: '🏀',
  },
  {
    key: 'b2',
    tag: '新品上市',
    title: '缓震科技 · 跑出每一步',
    sub: '全新一代跑步装备，会员下单立享专属价',
    bg: 'linear-gradient(120deg, #ff7a1a 0%, #ff9a4d 60%, #ffb877 100%)',
    emoji: '👟',
  },
  {
    key: 'b3',
    tag: '定制服务',
    title: '来图定制 · 打造专属队服',
    sub: 'UV 印花 / 烫印队徽，30 件起订，最快 7 天发货',
    bg: 'linear-gradient(120deg, #1f3a5f 0%, #2b5285 60%, #3f6fa8 100%)',
    emoji: '👕',
  },
];

function goCategory(id: string | number): void {
  router.push({ path: '/products', query: { categoryId: String(id) } });
}
function goProducts(): void {
  router.push('/products');
}
function goDetail(id: string): void {
  router.push(`/product/${id}`);
}

onMounted(async () => {
  try {
    categories.value = (await unwrap(productApi.categories())) ?? [];
  } catch {
    categories.value = [];
  }
  loading.value = true;
  try {
    const res = await unwrap(productApi.list({ page: 1, size: 8 }));
    products.value = res.list ?? [];
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.home-hero {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
  align-items: start;
}

/* 分类导航：吸顶浮动在版心左侧 */
.cat-nav {
  position: sticky;
  top: 60px;
  background: #fff;
  border-radius: var(--sent-radius);
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding-bottom: 6px;
}
.cat-nav__title {
  background: var(--sent-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  padding: 13px 18px;
  letter-spacing: 1px;
}
.cat-nav__list {
  list-style: none;
  margin: 0;
  padding: 6px 0;
}
.cat-nav__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 18px;
  font-size: 14px;
  color: var(--sent-text-2);
  cursor: pointer;
  transition: all 0.15s;
}
.cat-nav__item:hover {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  padding-left: 22px;
}
.cat-nav__arrow {
  color: #c6cbd3;
}
.cat-nav__item:hover .cat-nav__arrow {
  color: var(--sent-primary);
}
.cat-nav__empty {
  padding: 12px 18px;
  color: var(--sent-muted);
  font-size: 13px;
}
.cat-nav__all {
  margin: 6px 14px 0;
  padding: 9px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-primary);
  border-top: 1px dashed #eef0f2;
  cursor: pointer;
}
.cat-nav__all:hover {
  color: var(--sent-accent);
}

.home-main {
  min-width: 0;
}

/* 轮播 */
.home-carousel {
  border-radius: var(--sent-radius);
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}
.banner {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 56px;
  color: #fff;
  position: relative;
}
.banner__inner {
  max-width: 520px;
}
.banner__tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 13px;
  letter-spacing: 1px;
  margin-bottom: 16px;
}
.banner__title {
  font-size: 34px;
  font-weight: 800;
  margin: 0 0 12px;
  letter-spacing: 1px;
}
.banner__sub {
  font-size: 15px;
  opacity: 0.9;
  margin: 0 0 26px;
  line-height: 1.6;
}
.banner__btn {
  border: none;
  cursor: pointer;
  background: #fff;
  color: var(--sent-primary);
  font-size: 15px;
  font-weight: 700;
  padding: 12px 34px;
  border-radius: 24px;
  transition: transform 0.15s, box-shadow 0.15s;
}
.banner__btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.18);
}
.banner__emoji {
  font-size: 150px;
  line-height: 1;
  opacity: 0.9;
  filter: drop-shadow(0 12px 24px rgba(0, 0, 0, 0.25));
}

.home-section {
  margin-top: 22px;
}
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}
.section-head h3 {
  font-size: 20px;
  margin: 0;
  color: var(--sent-text);
  position: relative;
  padding-left: 14px;
}
.section-head h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 3px;
  bottom: 3px;
  width: 4px;
  border-radius: 2px;
  background: var(--sent-accent);
}
.section-more {
  font-size: 13px;
  color: var(--sent-muted);
  cursor: pointer;
}
.section-more:hover {
  color: var(--sent-accent);
}

@media (max-width: 1240px) {
  .home-hero {
    grid-template-columns: 190px 1fr;
  }
}
</style>
