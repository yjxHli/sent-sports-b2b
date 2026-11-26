<template>
  <!--
    根节点是真正的 <a>（2026-11-25 新标签页口径）：
    首页/列表页/搜索结果的卡片点开商品详情要新开 Tab，用原生锚点才能让鼠标中键、
    右键「在新窗口打开」也生效；`newTab` 为 false 的场景（领券中心、我的、团购专区）
    仍然 preventDefault + emit('click') 走 SPA 同页跳转。
  -->
  <a
    class="good-card"
    :href="detailHref"
    :target="newTab ? '_blank' : undefined"
    :rel="newTab ? 'noopener' : undefined"
    :data-testid="testid"
    @click="onClick"
  >
    <ProductThumb
      :src="product.mainImage"
      :category="product.categoryName"
      :name="product.name"
      :seed="product.productId"
    />
    <div class="good-card__body">
      <div class="good-card__title">{{ product.name }}</div>
      <div class="good-card__price">
        <span class="sym">¥</span>
        <span class="num">{{ priceParts.int }}</span>
        <span class="dec">.{{ priceParts.dec }}</span>
        <span v-if="showFrom && hasMultiSku" class="good-card__from">起</span>
      </div>
      <div class="good-card__tags">
        <span class="tag tag--ship">包邮</span>
        <span class="tag tag--custom">UV定制</span>
        <span class="good-card__sales">已售 {{ salesText }}</span>
      </div>
      <div v-if="shop" class="good-card__shop">{{ shop }}</div>
    </div>
  </a>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { fmtCent } from '@sent/shared';
import ProductThumb from '@/components/ProductThumb.vue';
import { productHref } from '@/utils/link';
import type { CatalogProductVO } from '@/api/product';

const props = withDefaults(
  defineProps<{
    product: CatalogProductVO;
    shop?: string;
    showFrom?: boolean;
    /** true = 点卡在新标签页打开商品详情（首页/列表页/搜索结果用） */
    newTab?: boolean;
    /** 便于 E2E 定位，透传到根锚点 */
    testid?: string;
  }>(),
  { newTab: false },
);
const emit = defineEmits<{ (e: 'click'): void }>();
const router = useRouter();

/** href 由 router 解析，自动带 base；不要手拼 `/product/` + id */
const detailHref = computed(() => productHref(router, props.product.productId));

function onClick(ev: MouseEvent): void {
  // 新标签页交给浏览器原生行为（含中键/右键菜单），不再插入 JS 跳转，
  // 否则会出现「新开一个页 + 原页也被导航走」的双跳。
  if (props.newTab) return;
  ev.preventDefault();
  emit('click');
}

/** 多规格商品在价格后加「起」（对齐电商列表的 ¥199 起 写法） */
const hasMultiSku = computed(() => (props.product.skus?.length ?? 0) > 1);

const priceCent = computed<number>(() => {
  const sku = props.product.skus?.find((s) => s.onShelf) ?? props.product.skus?.[0];
  return sku?.price.retailPriceCent ?? 0;
});

const priceParts = computed(() => {
  const text = fmtCent(priceCent.value);
  const [int, dec] = text.split('.');
  return { int, dec: dec ?? '00' };
});

const salesText = computed(() => {
  const n = Number(props.product.salesCount ?? 0);
  if (n >= 10000) return `${(n / 10000).toFixed(1)}万`;
  return n.toLocaleString('zh-CN');
});
</script>

<style scoped>
/* 根节点是 <a>：清掉链接默认的蓝色下划线，并保证在栅格里按块级卡片排版 */
.good-card {
  display: block;
  color: inherit;
  text-decoration: none;
}
.good-card__price .dec {
  font-size: 13px;
  font-weight: 700;
}
.good-card__from {
  font-size: 11px;
  font-weight: 400;
  color: var(--sent-text-2);
  margin-left: 3px;
}
</style>
