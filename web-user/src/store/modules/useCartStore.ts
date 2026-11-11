/**
 * 购物车 Store（P6-55）。
 *
 * 职责：缓存购物车项、勾选态、合计；并持有「立即购买」临时项（buyNow）供结算页优先使用。
 * 后端购物车为桩，列表项暂无 selected/skuName 回填时以本地兜底；结算时按选中项或 buyNow 构造下单参数。
 */
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { cartApi, type CartItemVO } from '@/api/cart';

export interface BuyNowItem {
  skuId: number;
  qty: number;
  name: string;
  spec: string;
  priceCent: number;
}

export const useCartStore = defineStore('sent-user-cart', () => {
  const items = ref<CartItemVO[]>([]);
  const selectedIds = ref<number[]>([]);
  const loaded = ref(false);
  const buyNow = ref<BuyNowItem | null>(null);

  const totalQty = computed(() => items.value.reduce((s, i) => s + i.qty, 0));

  const selectedItems = computed(() =>
    items.value.filter((i) => selectedIds.value.includes(i.itemId)),
  );

  async function load(force = false): Promise<void> {
    if (loaded.value && !force) return;
    try {
      const res = await cartApi.list();
      items.value = res.data ?? [];
      // 默认全选
      selectedIds.value = items.value.filter((i) => i.selected).map((i) => i.itemId);
      if (selectedIds.value.length === 0 && items.value.length > 0) {
        selectedIds.value = items.value.map((i) => i.itemId);
      }
    } finally {
      loaded.value = true;
    }
  }

  function toggleSelect(itemId: number): void {
    const idx = selectedIds.value.indexOf(itemId);
    if (idx >= 0) selectedIds.value.splice(idx, 1);
    else selectedIds.value.push(itemId);
  }

  function selectAll(flag: boolean): void {
    selectedIds.value = flag ? items.value.map((i) => i.itemId) : [];
  }

  async function remove(itemId: number): Promise<void> {
    await cartApi.remove(itemId);
    items.value = items.value.filter((i) => i.itemId !== itemId);
    selectedIds.value = selectedIds.value.filter((id) => id !== itemId);
  }

  async function setBuyNow(item: BuyNowItem | null): Promise<void> {
    buyNow.value = item;
  }

  function clearBuyNow(): void {
    buyNow.value = null;
  }

  return {
    items, selectedIds, loaded, buyNow,
    totalQty, selectedItems,
    load, toggleSelect, selectAll, remove, setBuyNow, clearBuyNow,
  };
});
