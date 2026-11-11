<template>
  <div class="good-card" @click="$emit('click')">
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
      </div>
      <div class="good-card__tags">
        <span class="tag tag--ship">包邮</span>
        <span class="tag tag--custom">UV定制</span>
        <span class="good-card__sales">已售 {{ salesText }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { fmtCent } from '@sent/shared';
import ProductThumb from '@/components/ProductThumb.vue';
import type { CatalogProductVO } from '@/api/product';

const props = defineProps<{ product: CatalogProductVO }>();
defineEmits<{ (e: 'click'): void }>();

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
.good-card__price .dec {
  font-size: 13px;
  font-weight: 700;
}
</style>
