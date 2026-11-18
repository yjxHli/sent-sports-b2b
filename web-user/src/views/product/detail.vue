<template>
  <div class="container detail">
    <div v-if="loading" class="list-hint">加载中…</div>
    <el-empty v-else-if="!product" description="商品不存在或已下架" />

    <template v-else>
      <!-- 面包屑 -->
      <el-breadcrumb separator="/" class="detail-crumb">
        <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/products', query: product.categoryId ? { categoryId: String(product.categoryId) } : {} }">
          {{ product.categoryName || '全部商品' }}
        </el-breadcrumb-item>
        <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="detail-main">
        <!-- 左：40% 大图 + 缩略图 -->
        <div class="gallery">
          <div class="gallery__main">
            <ProductThumb
              :src="product.mainImage"
              :category="product.categoryName"
              :name="product.name"
              :seed="product.productId + '-' + activeThumb"
            />
          </div>
          <div class="gallery__thumbs">
            <div
              v-for="i in 4"
              :key="i"
              class="gallery__thumb"
              :class="{ active: activeThumb === i - 1 }"
              @mouseenter="activeThumb = i - 1"
            >
              <ProductThumb
                :src="product.mainImage"
                :category="product.categoryName"
                :name="product.name"
                :seed="product.productId + '-' + (i - 1)"
              />
            </div>
          </div>
        </div>

        <!-- 右：60% 购买区（浅灰底） -->
        <div class="buy">
          <h1 class="buy__title">{{ product.name }}</h1>
          <div class="buy__subtitle">
            {{ product.categoryName || '运动装备' }} · 森特体育官方自营 · 支持企业采购与账期结算
          </div>

          <div class="buy__pricebox">
            <div class="buy__price">
              <span class="buy__sym">¥</span>
              <span class="buy__num">{{ priceParts.int }}</span>
              <span class="buy__dec">.{{ priceParts.dec }}</span>
            </div>
            <div class="buy__price-meta">
              <span class="buy__origin">原价 ¥{{ fmtCent(originPrice) }}</span>
              <span class="buy__save" v-if="originPrice > currentPrice">立省 ¥{{ fmtCent(originPrice - currentPrice) }}</span>
            </div>
          </div>

          <div class="buy__meta">
            <span>累计销量 <b>{{ salesText }}</b></span>
            <span class="buy__meta-sep"></span>
            <span>可售库存 <b data-testid="available-stock">{{ remainingStock }}</b></span>
            <span class="buy__meta-sep"></span>
            <span>发货地 <b>广东 · 深圳</b></span>
            <span class="buy__meta-sep"></span>
            <span>运费 <b>包邮</b></span>
          </div>

          <!-- 规格 -->
          <div class="buy__row">
            <div class="buy__label">规格</div>
            <div class="buy__value">
              <div v-if="product.skus && product.skus.length" class="spec-list">
                <button
                  v-for="s in product.skus"
                  :key="s.skuId"
                  class="spec-item"
                  :class="{
                    active: s.skuId === selectedSkuId,
                    disabled: !s.onShelf || s.stockQty <= 0,
                  }"
                  :disabled="!s.onShelf || s.stockQty <= 0"
                  @click="selectSku(s)"
                >
                  {{ s.spec }}
                </button>
              </div>
              <span v-else class="buy__empty">暂无可售规格</span>
            </div>
          </div>

          <!-- 数量 -->
          <div class="buy__row">
            <div class="buy__label">数量</div>
            <div class="buy__value buy__qty">
              <!--
                不设 max：输入超过库存时保留用户输入并给出「库存不足」提示 + 禁用购买，
                而不是被 el-input-number 静默钳制到库存值（那样用户看不到任何提示，
                会以为输入 8 就买了 7 件）。拦截统一交给 qtyExceedStock。
              -->
              <el-input-number
                v-model="qty"
                :min="uvCustom ? 30 : 1"
              />
              <!--
                可售数只在顶部信息区显示一次，这里不重复「库存 N 件」；
                只留红色的「仅剩 N 件」提示购买上限。
                不附「定制 30 件起订」：勾选框文案与库存不足提示里都有，挤在一起会折行。
                出现「库存不足」警示块时隐藏它——那块里已经写明可售 N 件，两处红字挨着太吵。
              -->
              <span v-if="!qtyExceedStock" class="buy__qty-hint">仅剩 {{ currentStock }} 件</span>
              <span v-if="qtyExceedStock" class="stock-alert">库存不足：当前规格可售 {{ currentStock }} 件{{ uvCustom ? '，不满足定制 30 件起订' : '' }}</span>
            </div>
          </div>

          <!-- UV 定制 -->
          <div class="custom-panel">
            <el-checkbox v-model="uvCustom" class="custom-panel__check">
              需要 <b>UV 定制</b>（企业队服 / 印 logo，定制费 +¥10/件，30 件起订）
            </el-checkbox>

            <div v-if="uvCustom" class="upload-area">
              <div v-if="!fileName" class="upload-drop" @click="triggerUpload">
                <div class="upload-drop__icon">＋</div>
                <div class="upload-drop__text">点击上传定制图稿</div>
                <div class="upload-drop__hint">支持 PNG / JPG / AI，单个不超过 20MB</div>
              </div>
              <div v-else class="upload-file">
                <img
                  v-if="draftFileId"
                  class="upload-file__thumb"
                  :src="fileApi.url(draftFileId)"
                  alt="定制图稿"
                />
                <span v-else class="upload-file__icon">🖼️</span>
                <span class="upload-file__name">{{ fileName }}</span>
                <span v-if="uploadingDraft" class="upload-file__hint">上传中…</span>
                <span class="upload-file__remove" @click="clearUpload">移除</span>
              </div>
              <input ref="fileInput" type="file" accept="image/*,.ai,.pdf" class="upload-input" @change="onFileChange" />
            </div>
          </div>

          <!-- 操作按钮（两个橙色）+ 收藏 -->
          <div class="buy__actions">
            <button class="buy-btn buy-btn--cart" :disabled="!currentSku || currentStock <= 0 || qtyExceedStock" @click="addToCart">加入购物车</button>
            <button class="buy-btn buy-btn--now" :disabled="!currentSku || currentStock <= 0 || qtyExceedStock" @click="buyNow">立即购买</button>
            <!-- 收藏：淘宝把心形放在「加入购物车」右侧，与加购并列而不是塞进工具栏 -->
            <button
              type="button"
              class="buy-fav"
              :class="{ 'buy-fav--on': favorited }"
              :title="favorited ? '取消收藏' : '收藏商品'"
              :disabled="favoriting"
              @click="toggleFavorite"
            >
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path
                  d="M10 17s-6.5-4.2-6.5-8.2A3.8 3.8 0 0 1 10 6.1a3.8 3.8 0 0 1 6.5 2.7C16.5 12.8 10 17 10 17Z"
                  :fill="favorited ? 'currentColor' : 'none'"
                  stroke="currentColor"
                  stroke-width="1.3"
                />
              </svg>
              <span>{{ favorited ? '已收藏' : '收藏' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 商品详情占位区 -->
      <section class="detail-desc">
        <div class="detail-desc__head">商品详情</div>
        <div class="detail-desc__body">
          <p>本商品由森特体育官方自营，适用于专业训练、赛事及企业团购场景，支持 UV 印花、烫印队徽等定制服务。</p>
          <p>规格：{{ product.name }} · 分类：{{ product.categoryName || '运动装备' }} · 品牌：森特 / SENT</p>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { productApi, type CatalogProductVO, type CatalogSkuVO } from '@/api/product';
import { cartApi } from '@/api/cart';
import { fileApi } from '@/api/file';
import { favoriteApi } from '@/api/favorite';
import { footprintApi } from '@/api/footprint';
import { savePendingDraft, clearPendingDraft } from '@/api/custom';
import { useCartStore } from '@/store/modules/useCartStore';
import { fmtCent } from '@sent/shared';
import ProductThumb from '@/components/ProductThumb.vue';

const route = useRoute();
const router = useRouter();
const cart = useCartStore();

const product = ref<CatalogProductVO | null>(null);
const loading = ref(false);
const selectedSkuId = ref<string>('');
const qty = ref(1);
const activeThumb = ref(0);
const uvCustom = ref(false);
const fileName = ref('');
const draftFileId = ref('');
const uploadingDraft = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

const currentSku = computed(() => product.value?.skus?.find((s) => s.skuId === selectedSkuId.value));
const currentPrice = computed(() => currentSku.value?.price.retailPriceCent ?? 0);
const originPrice = computed(() => Math.round(currentPrice.value * 1.25));
const currentStock = computed(() => currentSku.value?.stockQty ?? 0);
/**
 * 可售库存固定显示该 SKU 的真实可售数，不随已选数量递减。
 * 递减会让「可售库存 6 / 库存 7 件」两个数打架，用户误以为只能买 6 件；
 * 真实库存是多少就显示多少，输入超过它才提示库存不足。
 */
const remainingStock = computed(() => currentStock.value);
/** 数量超出可售库存，或定制起订 30 件但库存不足：拦截购买 */
const qtyExceedStock = computed(() => qty.value > currentStock.value || (uvCustom.value && currentStock.value < 30));

const priceParts = computed(() => {
  const [int, dec] = fmtCent(currentPrice.value).split('.');
  return { int, dec: dec ?? '00' };
});

const salesText = computed(() => {
  const n = Number(product.value?.salesCount ?? 0);
  if (n >= 10000) return `${(n / 10000).toFixed(1)}万`;
  return n.toLocaleString('zh-CN');
});

watch(uvCustom, (on) => {
  if (on && qty.value < 30) qty.value = 30;
});

function selectSku(s: CatalogSkuVO): void {
  if (!s.onShelf || s.stockQty <= 0) return;
  selectedSkuId.value = s.skuId;
}

function triggerUpload(): void {
  fileInput.value?.click();
}

/**
 * 选图即上传：图稿必须真正落到文件服务，否则下单后管理端只能看到一个假 fileId，
 * 审图台也就"看不到用户上传的图片"。上传成功后暂存本地，结算成功再补交到定制单。
 */
async function onFileChange(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('单个图稿不超过 20MB');
    clearUpload();
    return;
  }
  uploadingDraft.value = true;
  try {
    const res = await unwrap(fileApi.upload(file));
    if (!res?.fileId) throw new Error('图稿上传失败');
    draftFileId.value = res.fileId;
    fileName.value = res.fileName || file.name;
    savePendingDraft({ fileId: res.fileId, fileName: fileName.value, fileSize: Number(res.size ?? file.size) });
    ElMessage.success('图稿已上传，下单后自动提交审图');
  } catch (err) {
    ElMessage.error((err as Error).message || '图稿上传失败');
    clearUpload();
  } finally {
    uploadingDraft.value = false;
  }
}

function clearUpload(): void {
  fileName.value = '';
  draftFileId.value = '';
  clearPendingDraft();
  if (fileInput.value) fileInput.value.value = '';
}

async function addToCart(): Promise<void> {
  if (!currentSku.value) return;
  if (qtyExceedStock.value) {
    ElMessage.warning(`库存不足：当前规格可售 ${currentStock.value} 件${uvCustom.value ? '，定制需 30 件起订' : ''}`);
    return;
  }
  try {
    await cartApi.add(Number(currentSku.value.skuId), qty.value);
    await cart.load(true);
    ElMessage.success(uvCustom.value ? '已加入购物车，定制需求将在下单时确认' : '已加入购物车');
  } catch {
    ElMessage.error('加入购物车失败');
  }
}

async function buyNow(): Promise<void> {
  if (!product.value || !currentSku.value) return;
  if (qtyExceedStock.value) {
    ElMessage.warning(`库存不足：当前规格可售 ${currentStock.value} 件${uvCustom.value ? '，定制需 30 件起订' : ''}`);
    return;
  }
  await cart.setBuyNow({
    skuId: Number(currentSku.value.skuId),
    qty: qty.value,
    name: product.value.name,
    spec: currentSku.value.spec,
    priceCent: currentSku.value.price.retailPriceCent,
  });
  router.push('/checkout');
}

/**
 * 收藏状态 + 惰性上报足迹（2026-11-16）。
 *
 * 详情页是「收藏」和「足迹」两个行为共同的入口：
 * - 进入页面先 `GET /favorites/status` 取心形初始态（未登录时后端返回 false，不抛 401）；
 * - `POST /footprints/{productId}` 静默上报一次浏览，失败不提示（埋点不该打断选规格）。
 */
const favorited = ref(false);
const favoriting = ref(false);

async function loadFavoriteStatus(): Promise<void> {
  const pid = Number(route.params.productId);
  if (!Number.isFinite(pid) || pid <= 0) return;
  try {
    const res = await unwrap(favoriteApi.status(pid));
    favorited.value = Boolean(res?.favorited);
  } catch {
    favorited.value = false;
  }
}

async function recordFootprint(): Promise<void> {
  const pid = Number(route.params.productId);
  if (!Number.isFinite(pid) || pid <= 0) return;
  try {
    await unwrap(footprintApi.record(pid));
  } catch {
    /* 足迹是埋点，失败静默 */
  }
}

async function toggleFavorite(): Promise<void> {
  const pid = Number(route.params.productId);
  if (!Number.isFinite(pid) || pid <= 0) return;
  favoriting.value = true;
  const next = !favorited.value;
  try {
    if (next) {
      await unwrap(favoriteApi.add(pid));
      ElMessage.success('已收藏，可在顶部「收藏夹」查看');
    } else {
      await unwrap(favoriteApi.remove(pid));
      ElMessage.success('已取消收藏');
    }
    favorited.value = next;
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败');
  } finally {
    favoriting.value = false;
  }
}

onMounted(async () => {
  loading.value = true;
  try {
    const p = await unwrap(productApi.detail(String(route.params.productId)));
    product.value = p;
    const first = p.skus?.find((s) => s.onShelf && s.stockQty > 0) ?? p.skus?.[0];
    if (first) selectedSkuId.value = first.skuId;
  } catch {
    product.value = null;
  } finally {
    loading.value = false;
  }
  if (product.value) {
    await Promise.all([loadFavoriteStatus(), recordFootprint()]);
  }
});
</script>

<style scoped>
.detail {
  padding-top: 4px;
}
.detail-crumb {
  margin-bottom: 16px;
  font-size: 13px;
}

.detail-main {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
  gap: 28px;
  align-items: start;
}

/* 画廊 */
.gallery__main {
  background: #fff;
  border-radius: var(--sent-radius);
  overflow: hidden;
  border: 1px solid #eee;
}
.gallery__thumbs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 12px;
}
.gallery__thumb {
  border: 2px solid transparent;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  opacity: 0.85;
  transition: all 0.15s;
}
.gallery__thumb:hover,
.gallery__thumb.active {
  border-color: var(--sent-primary);
  opacity: 1;
}

/* 购买区 */
.buy {
  background: #f7f8f9;
  border-radius: var(--sent-radius);
  padding: 24px 26px;
  border: 1px solid #eef0f2;
}
.buy__title {
  font-size: 22px;
  line-height: 1.4;
  margin: 0 0 8px;
  color: var(--sent-text);
}
.buy__subtitle {
  font-size: 13px;
  color: var(--sent-muted);
  margin-bottom: 16px;
}

.buy__pricebox {
  background: linear-gradient(90deg, #fff3ea, #fff8f2);
  border: 1px solid #ffe2cc;
  border-radius: 6px;
  padding: 14px 18px;
  margin-bottom: 14px;
}
.buy__price {
  display: flex;
  align-items: baseline;
  color: var(--sent-accent);
  font-weight: 800;
}
.buy__sym {
  font-size: 18px;
  margin-right: 2px;
}
.buy__num {
  font-size: 36px;
  line-height: 1;
  letter-spacing: -1px;
}
.buy__dec {
  font-size: 18px;
}
.buy__price-meta {
  margin-top: 6px;
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--sent-muted);
}
.buy__origin {
  text-decoration: line-through;
}
.buy__save {
  color: var(--sent-accent);
}

.buy__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: var(--sent-muted);
  padding-bottom: 16px;
  border-bottom: 1px dashed #e5e7eb;
}
.buy__meta b {
  color: var(--sent-text);
  font-weight: 600;
}
.buy__meta-sep {
  width: 1px;
  height: 12px;
  background: #e0e3e7;
}

.buy__row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 0 0;
}
.buy__label {
  flex: 0 0 48px;
  color: var(--sent-muted);
  font-size: 13px;
  line-height: 34px;
}
.buy__value {
  flex: 1;
  min-width: 0;
}
.buy__empty {
  color: var(--sent-muted);
  font-size: 13px;
}

.spec-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.spec-item {
  border: 1px solid #dfe3e8;
  background: #fff;
  border-radius: 6px;
  padding: 7px 16px;
  font-size: 13px;
  color: var(--sent-text-2);
  cursor: pointer;
  transition: all 0.15s;
}
.spec-item:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}
.spec-item.active {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
  box-shadow: inset 0 0 0 1px var(--sent-primary);
}
.spec-item.disabled {
  color: #c2c7cf;
  background: #f4f5f6;
  cursor: not-allowed;
  text-decoration: line-through;
}

.buy__qty {
  display: flex;
  align-items: center;
  gap: 12px;
}
/* 「仅剩 N 件」红色强调购买上限；flex:none + nowrap 防止被右侧警示块挤到换行 */
.buy__qty-hint {
  flex: none;
  font-size: 12px;
  font-weight: 600;
  color: #c93644;
  white-space: nowrap;
}
.stock-alert {
  font-size: 12px;
  color: #c93644;
  background: #fdeaec;
  padding: 4px 10px;
  border-radius: 4px;
}

/* 定制面板 */
.custom-panel {
  margin-top: 18px;
  border: 1px dashed #ffd0a8;
  background: #fffaf5;
  border-radius: 8px;
  padding: 14px 16px;
}
.custom-panel__check :deep(.el-checkbox__label) {
  font-size: 13px;
  color: var(--sent-text-2);
}
.custom-panel__check b {
  color: var(--sent-accent);
}
.upload-area {
  margin-top: 12px;
}
.upload-drop {
  border: 1px dashed #ffbe8a;
  border-radius: 8px;
  background: #fff;
  padding: 22px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
}
.upload-drop:hover {
  border-color: var(--sent-accent);
  background: #fff6ee;
}
.upload-drop__icon {
  font-size: 26px;
  color: var(--sent-accent);
  line-height: 1;
}
.upload-drop__text {
  margin-top: 8px;
  font-size: 14px;
  color: var(--sent-text-2);
}
.upload-drop__hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sent-muted);
}
.upload-file {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border: 1px solid #ffe0c7;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 13px;
}
.upload-file__name {
  flex: 1;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.upload-file__thumb {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  object-fit: cover;
  flex: none;
  border: 1px solid #f0e2d6;
}
.upload-file__hint {
  font-size: 12px;
  color: var(--sent-muted);
}
.upload-file__remove {
  color: var(--sent-accent);
  cursor: pointer;
}
.upload-input {
  display: none;
}

/* 按钮 */
.buy__actions {
  display: flex;
  gap: 14px;
  margin-top: 24px;
}
.buy-btn {
  flex: 1;
  height: 52px;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
  transition: transform 0.12s, box-shadow 0.15s, background 0.15s;
}
.buy-btn--cart {
  background: #ff9a4d;
}
.buy-btn--cart:hover {
  background: #ff8a33;
}
.buy-btn--now {
  background: var(--sent-accent);
  box-shadow: 0 8px 18px rgba(255, 122, 26, 0.32);
}
.buy-btn--now:hover {
  background: var(--sent-accent-dark);
}
.buy-btn:hover {
  transform: translateY(-2px);
}
.buy-btn:disabled {
  background: #d6d9de;
  box-shadow: none;
  cursor: not-allowed;
  transform: none;
}

/* 收藏：与两个主按钮并排，宽度只占一小截，避免抢走「立即购买」的视觉重心 */
.buy-fav {
  flex: none;
  width: 96px;
  height: 52px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 1px solid var(--sent-border);
  border-radius: 8px;
  background: #fff;
  color: var(--sent-text-2);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.buy-fav svg {
  width: 17px;
  height: 17px;
}
.buy-fav:hover {
  border-color: #e34d59;
  color: #e34d59;
}
.buy-fav--on {
  border-color: #e34d59;
  color: #e34d59;
  background: #fff5f5;
}
.buy-fav:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

/* 详情描述 */
.detail-desc {
  margin-top: 28px;
  background: #fff;
  border-radius: var(--sent-radius);
  border: 1px solid #eee;
  overflow: hidden;
}
.detail-desc__head {
  padding: 14px 22px;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #f0f0f0;
  color: var(--sent-text);
}
.detail-desc__body {
  padding: 20px 22px;
  color: var(--sent-text-2);
  line-height: 1.9;
  font-size: 14px;
}
.detail-desc__body p {
  margin: 0 0 8px;
}

@media (max-width: 1240px) {
  .detail-main {
    gap: 20px;
  }
}
</style>
