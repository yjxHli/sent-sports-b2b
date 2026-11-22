<template>
  <div class="img-field">
    <div class="img-field__grid">
      <div v-for="(fileId, index) in modelValue" :key="fileId + index" class="img-field__item">
        <img :src="fileApi.url(fileId)" alt="" class="img-field__img" />
        <span v-if="showPrimary && index === 0" class="img-field__badge">主图</span>
        <div class="img-field__ops">
          <button type="button" title="上移" :disabled="index === 0" @click="move(index, -1)">↑</button>
          <button type="button" title="下移" :disabled="index === modelValue.length - 1" @click="move(index, 1)">↓</button>
          <button type="button" title="删除" class="img-field__del" @click="remove(index)">×</button>
        </div>
      </div>

      <label v-if="modelValue.length < max" class="img-field__add" :class="{ 'is-busy': uploading }">
        <input type="file" accept="image/*" :disabled="uploading" @change="onPick" />
        <span class="img-field__add-icon">{{ uploading ? '…' : '+' }}</span>
        <span class="img-field__add-text">{{ uploading ? '上传中' : '上传图片' }}</span>
      </label>
    </div>
    <p class="img-field__hint">
      已上传 {{ modelValue.length }} / {{ max }} 张
      <template v-if="showPrimary && modelValue.length">，第一张为主图（可用 ↑ 调整）</template>
    </p>
  </div>
</template>

<script setup lang="ts">
/**
 * 图片列表字段（V54）：商品图集与详情长图共用。
 *
 * 上传即写文件服务拿 fileId，存的是相对路径而不是完整 URL——域名换了不用洗数据。
 * 排序不用拖拽库（KISS）：↑/↓ 足够，还省一个依赖。
 */
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fileApi } from '@/api/file';

const props = withDefaults(defineProps<{
  modelValue: string[];
  max?: number;
  showPrimary?: boolean;
}>(), {
  max: 8,
  showPrimary: false,
});

const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>();

const uploading = ref(false);

async function onPick(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片');
    input.value = '';
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('单张图片不超过 5MB');
    input.value = '';
    return;
  }
  uploading.value = true;
  try {
    const res = await fileApi.upload(file);
    const fileId = res.data?.fileId;
    if (!fileId) throw new Error('上传未返回 fileId');
    emit('update:modelValue', [...props.modelValue, fileId]);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '图片上传失败');
  } finally {
    uploading.value = false;
    input.value = '';
  }
}

function remove(index: number): void {
  const next = [...props.modelValue];
  next.splice(index, 1);
  emit('update:modelValue', next);
}

function move(index: number, delta: number): void {
  const target = index + delta;
  if (target < 0 || target >= props.modelValue.length) return;
  const next = [...props.modelValue];
  const [item] = next.splice(index, 1);
  next.splice(target, 0, item);
  emit('update:modelValue', next);
}
</script>

<style scoped>
.img-field__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.img-field__item {
  position: relative;
  width: 96px;
  height: 96px;
  border: 1px solid var(--sent-line-2);
  border-radius: 6px;
  overflow: hidden;
  background: var(--sent-fill);
}
.img-field__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.img-field__badge {
  position: absolute;
  left: 0;
  top: 0;
  padding: 1px 6px;
  font-size: 11px;
  color: #fff;
  background: var(--sent-primary);
  border-bottom-right-radius: 6px;
}
.img-field__ops {
  position: absolute;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 2px;
  padding: 2px;
  background: rgba(27, 42, 36, 0.55);
  border-top-left-radius: 6px;
}
.img-field__ops button {
  width: 20px;
  height: 18px;
  border: none;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--sent-ink);
  font-size: 11px;
  line-height: 1;
  cursor: pointer;
}
.img-field__ops button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.img-field__del {
  color: var(--sent-danger) !important;
}
.img-field__add {
  width: 96px;
  height: 96px;
  border: 1px dashed var(--sent-line-2);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  color: var(--sent-ink-3);
  background: var(--sent-surface);
}
.img-field__add:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}
.img-field__add.is-busy {
  cursor: progress;
}
.img-field__add input {
  display: none;
}
.img-field__add-icon {
  font-size: 20px;
  line-height: 1;
}
.img-field__add-text {
  font-size: 11px;
}
.img-field__hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--sent-ink-3);
}
</style>
