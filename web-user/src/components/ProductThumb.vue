<template>
  <div class="thumb" :style="bgStyle">
    <img
      v-if="resolvedSrc && !failed"
      :src="resolvedSrc"
      class="thumb__img"
      alt=""
      @error="failed = true"
    />
    <div v-else class="thumb__ph">
      <span class="thumb__emoji">{{ emoji }}</span>
      <span class="thumb__label">{{ label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

const props = defineProps<{
  src?: string | null;
  category?: string | null;
  name?: string | null;
  /** 占位渐变种子，默认用 name */
  seed?: string | number | null;
}>();

const failed = ref(false);

/** 演示数据里的 demo.sent.test 是虚构域名，直接走占位，避免无谓的 404。 */
const resolvedSrc = computed(() => {
  const s = props.src;
  if (!s || /demo\.sent\.test/i.test(s)) return '';
  return s;
});

const EMOJI_MAP: Record<string, string> = {
  球类运动: '🏀',
  健身器材: '🏋️',
  瑜伽用品: '🧘',
  跑步装备: '👟',
  户外运动: '🏔️',
  团队运动: '🤾',
  游泳装备: '🏊',
  运动服饰: '👕',
};

const GRADIENTS = [
  ['#e7f1ec', '#cfe6dc'],
  ['#fff1e6', '#ffe0c7'],
  ['#e8eefc', '#d2ddf7'],
  ['#f3eafc', '#e3d4f7'],
  ['#e6f6f5', '#c9eae8'],
  ['#fdf0f0', '#fbdcdc'],
  ['#eef7e6', '#dbefc8'],
  ['#f7f3e6', '#ece1c4'],
];

const emoji = computed(() => {
  const cat = props.category || '';
  for (const key of Object.keys(EMOJI_MAP)) {
    if (cat.includes(key)) return EMOJI_MAP[key];
  }
  return '🛍️';
});

const label = computed(() => {
  const n = (props.name || '').trim();
  if (!n) return '森特体育';
  return n.slice(0, 8);
});

const hash = computed(() => {
  const text = String(props.seed ?? props.name ?? props.category ?? 'sent');
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) & 0x7fffffff;
  return h;
});

const bgStyle = computed(() => {
  const [a, b] = GRADIENTS[hash.value % GRADIENTS.length];
  const angle = 120 + (hash.value % 60);
  return { background: `linear-gradient(${angle}deg, ${a}, ${b})` };
});
</script>

<style scoped>
.thumb {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumb__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.thumb__ph {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 10px;
  text-align: center;
}
.thumb__emoji {
  font-size: 42px;
  line-height: 1;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.12));
}
.thumb__label {
  font-size: 12px;
  color: rgba(31, 35, 41, 0.55);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
