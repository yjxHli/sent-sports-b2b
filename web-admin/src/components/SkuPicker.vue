<template>
  <div class="sku-picker" :data-sku-id="displayText(modelValue)">
    <el-autocomplete
      v-model="text"
      :fetch-suggestions="fetchSuggestions"
      :disabled="disabled"
      :placeholder="placeholder"
      :debounce="250"
      clearable
      class="sku-picker__input"
      @update:model-value="onInput"
    >
      <template #default="{ item }">
        <div class="sku-picker__option">
          <span class="sku-picker__option-id">SKU {{ item.skuId }}</span>
          <span class="sku-picker__option-name">{{ item.label }}</span>
          <span v-if="item.spec || item.skuCode" class="sku-picker__option-spec">
            {{ item.spec || item.skuCode }}
          </span>
        </div>
      </template>
    </el-autocomplete>
    <span class="sent-hint sku-picker__hint">
      可直接输入 SKU ID，也可输入商品名 / 规格后从列表中选择
    </span>
  </div>
</template>

<script setup lang="ts">
/**
 * 库存单据共用的 SKU 选件（2026-11-27）。
 * 自由输入数字 ID 与远程下拉选择都收敛为同一个 number modelValue；
 * 候选来源只有现成读接口：数字走库存台账精确查，文本走商品目录。
 * 不做存在性业务校验——那是后端入库 / 报损 / 盘点用例的职责。
 */
import { ref, watch } from 'vue';
import { unwrap } from '@/utils/http';
import { productApi } from '@/api/product';
import { stockApi } from '@/api/stock';
import type { StockVO } from '@sent/shared';

interface SkuSuggestion {
  /** el-autocomplete 的 valueKey，选中后回填输入框 */
  value: string;
  skuId: number;
  label: string;
  spec?: string;
  skuCode?: string;
}

const props = withDefaults(defineProps<{
  modelValue?: number | string | null;
  disabled?: boolean;
  placeholder?: string;
}>(), {
  modelValue: null,
  disabled: false,
  placeholder: '输入 SKU ID 或搜索商品名 / 规格',
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void;
}>();

/** 页面的 0 是「未选」占位，不能显示成一个真的 SKU */
function displayText(value: number | string | null | undefined): string {
  if (value == null || value === '' || Number(value) === 0) return '';
  return String(value);
}

const text = ref(displayText(props.modelValue));
/** 记录自己刚 emit 出去的值，避免父组件回写时把正在输入的搜索词冲掉 */
let lastEmitted: number | null = Number(props.modelValue) || null;
let requestSeq = 0;

watch(() => props.modelValue, (value) => {
  const next = Number(value) || null;
  if (next === lastEmitted || (next !== null && !Number.isFinite(next))) return;
  lastEmitted = next;
  text.value = displayText(next);
});

function onInput(value: string): void {
  text.value = value;
  const trimmed = value.trim();
  const normalized = /^\d+$/.test(trimmed) && Number(trimmed) > 0 ? Number(trimmed) : null;
  if (normalized !== lastEmitted) {
    lastEmitted = normalized;
    emit('update:modelValue', normalized);
  }
}

async function fetchSuggestions(keyword: string): Promise<SkuSuggestion[]> {
  const key = keyword.trim();
  const seq = ++requestSeq;
  try {
    let suggestions: SkuSuggestion[] = [];
    if (/^\d+$/.test(key)) {
      // 数字：精确查库存台账，能命中已建台账的 SKU（含下架商品）
      const data = await unwrap(stockApi.list({ page: 1, size: 20, skuId: Number(key) }));
      suggestions = (data?.list ?? []).map((row: StockVO) => ({
        value: String(row.skuId),
        skuId: Number(row.skuId),
        label: String(row.skuName ?? `SKU ${row.skuId}`),
      }));
    } else {
      // 文本：公开目录列表接口带 SKU 明细，沿用券/秒杀页的选规格口径
      const data = await unwrap(productApi.catalog({
        page: 1,
        size: 20,
        keyword: key || undefined,
      }));
      suggestions = (data?.list ?? []).flatMap((product) =>
        (product.skus ?? []).map((sku) => ({
          value: String(sku.skuId),
          skuId: Number(sku.skuId),
          label: product.name,
          spec: sku.spec,
          skuCode: sku.skuCode,
        })),
      ).slice(0, 40);
    }
    return seq === requestSeq ? suggestions : [];
  } catch {
    // 候选加载失败不阻断手输，后端提交时仍会校验 SKU
    return [];
  }
}
</script>

<style scoped>
.sku-picker {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 6px;
}

.sku-picker__input {
  width: 100%;
}

.sku-picker__hint {
  line-height: 1.4;
}

.sku-picker__option {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.sku-picker__option-id {
  flex: none;
  color: var(--sent-primary);
  font-variant-numeric: tabular-nums;
}

.sku-picker__option-name {
  min-width: 0;
  overflow: hidden;
  color: var(--sent-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sku-picker__option-spec {
  flex: none;
  color: var(--sent-ink-3);
}
</style>
