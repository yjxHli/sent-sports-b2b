<template>
  <div>
    <div class="sent-bar">
      <strong>购物车</strong>
      <el-button text type="primary" :disabled="items.length === 0" @click="goProducts">去逛逛</el-button>
    </div>

    <el-empty v-if="items.length === 0" description="购物车是空的" />

    <template v-else>
      <div v-for="it in items" :key="it.itemId" class="sent-card cart-item">
        <el-checkbox :model-value="selectedIds.includes(it.itemId)" @change="() => onToggle(it.itemId)" />
        <div class="cart-item__body">
          <div class="cart-item__name">{{ it.skuName || ('SKU ' + it.skuId) }}</div>
          <div class="cart-item__foot">
            <el-input-number
              :model-value="it.qty"
              :min="1"
              :max="99"
              size="small"
              @change="(v: number | undefined) => onQty(it.itemId, v ?? 1)"
            />
            <el-button text type="danger" size="small" @click="onRemove(it.itemId)">删除</el-button>
          </div>
        </div>
      </div>

      <div v-if="quote" class="sent-card quote">
        <div class="quote-line">
          <span>商品金额</span><span>¥{{ quote.goodsAmountYuan }}</span>
        </div>
        <div class="quote-line">
          <span>运费</span><span>¥{{ quote.freightYuan }}</span>
        </div>
        <div class="quote-line quote-line--total">
          <span>应付</span><span class="sent-price">¥{{ quote.payableYuan }}</span>
        </div>
      </div>
    </template>

    <div v-if="items.length > 0" class="cart-foot">
      <el-checkbox :model-value="allSelected" @change="onSelectAll">全选</el-checkbox>
      <div class="cart-foot__pay">
        <span>合计：</span>
        <span class="sent-price">{{ payableText }}</span>
        <el-button type="primary" :disabled="selectedItems.length === 0" @click="goCheckout">
          结算 ({{ selectedItems.length }})
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { unwrap } from '@/utils/http';
import { cartApi, type QuoteVO } from '@/api/cart';
import { useCartStore } from '@/store/modules/useCartStore';

const router = useRouter();
const cart = useCartStore();
const quote = ref<QuoteVO | null>(null);

const items = computed(() => cart.items);
const selectedIds = computed(() => cart.selectedIds);
const selectedItems = computed(() => cart.selectedItems);
const allSelected = computed(() => items.value.length > 0 && selectedIds.value.length === items.value.length);
const payableText = computed(() => (quote.value ? `¥${quote.value.payableYuan}` : '¥0.00'));

function onToggle(id: number): void {
  cart.toggleSelect(id);
}
function onSelectAll(): void {
  cart.selectAll(!allSelected.value);
}

async function onQty(id: number, qty: number): Promise<void> {
  await cartApi.update(id, qty, selectedIds.value.includes(id));
  const it = cart.items.find((i) => i.itemId === id);
  if (it) it.qty = qty;
  await refreshQuote();
}

async function onRemove(id: number): Promise<void> {
  await cart.remove(id);
  await refreshQuote();
}

function goProducts(): void {
  router.push('/products');
}
function goCheckout(): void {
  router.push('/checkout');
}

async function refreshQuote(): Promise<void> {
  if (selectedItems.value.length === 0) {
    quote.value = null;
    return;
  }
  try {
    quote.value = await unwrap(cartApi.checkoutPreview({
      availablePoints: 0,
      freightFreeThresholdCent: 0,
      freightFeeCent: 0,
      items: selectedItems.value.map((i) => ({
        skuId: i.skuId,
        qty: i.qty,
        customerTypeCode: 'PERSON',
        memberDiscountRate: 100,
        agreementDiscountRate: 100,
        allowStacking: false,
        ladderTiers: [],
      })),
    }));
  } catch {
    quote.value = null;
  }
}

watch(selectedIds, refreshQuote);

onMounted(async () => {
  await cart.load();
  await refreshQuote();
});
</script>

<style scoped>
.cart-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cart-item__body {
  flex: 1;
}
.cart-item__name {
  font-size: 14px;
  margin-bottom: 8px;
}
.cart-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.quote-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #555;
  padding: 3px 0;
}
.quote-line--total {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
  border-top: 1px dashed #eee;
  margin-top: 4px;
  padding-top: 8px;
}
.cart-foot {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 56px;
  width: 100%;
  max-width: 480px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-top: 1px solid #ececec;
  padding: 0 12px;
  z-index: 19;
}
.cart-foot__pay {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
</style>
