<template>
  <div class="container detail">
    <div v-if="loading" class="list-hint">加载中…</div>
    <el-empty v-else-if="!product" description="商品不存在或已下架" />

    <template v-else>
      <!-- 页面内品牌 + 迷你搜索（2026-11-25）：详情页已去掉整站搜索区，商城名挪到这里并回首页；
           右侧补一个轻量搜索（只要输入框 + 按钮），宽度约为购买栏的一半，和淘宝详情头同读法。 -->
      <header class="detail-brand" data-testid="detail-brand">
        <router-link to="/home" class="detail-brand__home" data-testid="detail-brand-home">
          <span class="detail-brand__mark" aria-hidden="true"></span>
          <span class="detail-brand__text">
            <span class="detail-brand__cn">森特</span>
            <span class="detail-brand__en">SENT SPORTS</span>
          </span>
        </router-link>
        <form class="detail-mini-search" data-testid="detail-mini-search" @submit.prevent="onMiniSearch">
          <input v-model="miniKeyword" type="search" placeholder="搜索商品 / 品牌" aria-label="搜索商品" />
          <button type="submit">搜索</button>
        </form>
      </header>

      <!-- 面包屑 -->
      <el-breadcrumb separator="/" class="detail-crumb">
        <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/products', query: product.categoryId ? { categoryId: String(product.categoryId) } : {} }">
          {{ product.categoryName || '全部商品' }}
        </el-breadcrumb-item>
        <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
      </el-breadcrumb>

      <!-- 两栏栅格：左 60%（主图 + 详情/评价）| 右 40%（购买决策栏，sticky）
           注意：`.gallery-rail` 是 v-if，图集 ≤1 张时该节点不渲染。`.gallery-block` 若仍按
           56px | 1fr 排，Grid 会把剩下的 [gallery] 塞进第一列，
           主图被压成 56px 小方块（2026-11-22 缺陷）。因此无 rail 时加修饰类收掉第一列轨道。 -->
      <div class="detail-main">
        <!-- 左列（2026-11-25）：「商品详情 / 用户评价」与主图同列摆放；
             右列购买栏 sticky，往下滑时一直跟着（淘宝式两栏） -->
        <div class="detail-left">
          <div class="gallery-block" :class="{ 'gallery-block--no-rail': galleryImages.length <= 1 }">
            <div v-if="galleryImages.length > 1" class="gallery-rail">
              <button
                v-for="(img, i) in galleryImages"
                :key="'rail-' + i"
                type="button"
                class="gallery-rail__item"
                :class="{ active: activeThumb === i }"
                @mouseenter="activeThumb = i"
                @focus="activeThumb = i"
                @click="activeThumb = i"
              >
                <ProductThumb
                  :src="img"
                  :category="product.categoryName"
                  :name="product.name"
                  :seed="product.productId + '-rail-' + i"
                />
              </button>
            </div>

            <div class="gallery">
              <div class="gallery__main">
                <ProductThumb
                  :src="activeImage"
                  :category="product.categoryName"
                  :name="product.name"
                  :seed="product.productId + '-' + activeThumb"
                />
              </div>
              <!-- 窄屏才出现的横排缩略图（宽屏用左侧竖排 rail） -->
              <div v-if="galleryImages.length > 1" class="gallery__thumbs">
                <div
                  v-for="(img, i) in galleryImages"
                  :key="'thumb-' + i"
                  class="gallery__thumb"
                  :class="{ active: activeThumb === i }"
                  @mouseenter="activeThumb = i"
                  @click="activeThumb = i"
                >
                  <ProductThumb
                    :src="img"
                    :category="product.categoryName"
                    :name="product.name"
                    :seed="product.productId + '-' + i"
                  />
                </div>
              </div>
            </div>
          </div>

        <!-- 内容切换：商品详情 / 用户评价 互斥（2026-11-22）
             改造前这里是锚点导航，四块内容始终全部渲染，滚到哪算哪——
             用户看到的「商品详情」和「用户评价」是缝在一起的。
             现在改成真切换：一次只显示一个面板，参数/服务承诺归入商品详情。 -->
        <nav class="detail-tabs">
          <button
            v-for="tab in DETAIL_TABS"
            :key="tab.id"
            type="button"
            class="detail-tabs__item"
            :class="{ active: activeTab === tab.id }"
            :data-testid="'detail-tab-' + tab.id"
            @click="switchTab(tab.id)"
          >
            {{ tab.label }}
          </button>
        </nav>

        <!-- 商品详情面板：描述 + 详情图 + 规格参数 + 服务承诺 -->
        <div v-show="activeTab === 'desc'" class="detail-panel" data-testid="detail-panel-desc">
          <section id="section-desc" class="detail-desc">
            <div class="detail-desc__head">商品详情</div>
            <div class="detail-desc__body">
              <p v-if="product.description" class="detail-desc__text">{{ product.description }}</p>
              <div v-if="detailImages.length" class="detail-desc__images">
                <img
                  v-for="(img, i) in detailImages"
                  :key="'detail-' + i"
                  class="detail-desc__img"
                  :src="resolveImageUrl(img)"
                  :alt="product.name + ' 详情图 ' + (i + 1)"
                  loading="lazy"
                />
              </div>
              <p v-if="!product.description && !detailImages.length" class="detail-desc__empty">暂无详情</p>
            </div>
          </section>

          <section v-if="params.length" id="section-params" class="detail-params">
            <div class="detail-params__head">规格参数</div>
            <div class="detail-params__grid">
              <div v-for="(p, i) in paramsTop" :key="'p-top-' + i" class="detail-params__card">
                <span class="detail-params__label">{{ p.label }}</span>
                <span class="detail-params__value">{{ p.value }}</span>
              </div>
            </div>
            <table v-if="paramsRest.length" class="detail-params__table">
              <tbody>
                <tr v-for="(p, i) in paramsRest" :key="'p-rest-' + i">
                  <th>{{ p.label }}</th>
                  <td>{{ p.value }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section v-if="serviceTags.length" id="section-service" class="detail-service">
            <div class="detail-service__head">服务承诺</div>
            <div class="detail-service__tags">
              <span v-for="(tag, i) in serviceTags" :key="'tag-' + i" class="detail-service__tag">
                {{ tag }}
              </span>
            </div>
          </section>
        </div>

        <!-- 用户评价面板（V55）：写入/读取都在组件内部，不影响上方购买逻辑 -->
        <div v-show="activeTab === 'review'" class="detail-panel" data-testid="detail-panel-review">
          <ProductReview :product-id="String(route.params.productId || '')" :sku-id="selectedSkuId || null" />
        </div>
        </div>

        <!-- 购买区（sticky） -->
        <aside class="buy">
          <h1 class="buy__title">{{ product.name }}</h1>
          <div class="buy__subtitle">
            {{ product.subtitle || (product.categoryName || '运动装备') + ' · 森特体育官方自营 · 支持企业采购与账期结算' }}
          </div>

          <!-- 秒杀横幅：无生效活动时整块 DOM 不存在 -->
          <div v-if="flashSale" class="flash-sale" data-testid="flash-sale-banner">
            <div class="flash-sale__badge">限时秒杀</div>
            <div class="flash-sale__body">
              <div class="flash-sale__pricebox">
                <span class="flash-sale__sym">¥</span>
                <b class="flash-sale__num" data-testid="flash-sale-price">{{ fmtCent(bannerPrice) }}</b>
                <span v-if="!skuFlashPrice" class="flash-sale__low">起</span>
                <span class="flash-sale__origin">¥{{ fmtCent(bannerOrigin) }}</span>
              </div>
              <div class="flash-sale__meta">
                <span class="flash-sale__title">{{ flashSale.title || '限时秒杀' }}</span>
                <span class="flash-sale__countdown" data-testid="flash-sale-countdown">{{ countdownText }}</span>
                <span class="flash-sale__end">{{ fmtDateTime(flashSale.endAt) }} 结束</span>
              </div>
            </div>
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
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { resolveImageUrl } from '@/utils/image';
import { openInNewTab } from '@/utils/link';
import { productApi, type CatalogProductVO, type CatalogSkuVO } from '@/api/product';
import { cartApi } from '@/api/cart';
import { fileApi } from '@/api/file';
import { favoriteApi } from '@/api/favorite';
import { footprintApi } from '@/api/footprint';
import { savePendingDraft, clearPendingDraft } from '@/api/custom';
import { useCartStore } from '@/store/modules/useCartStore';
import { fmtCent, fmtDateTime } from '@sent/shared';
import ProductThumb from '@/components/ProductThumb.vue';
import ProductReview from '@/components/ProductReview.vue';

const route = useRoute();
const router = useRouter();
const cart = useCartStore();

const product = ref<CatalogProductVO | null>(null);
const loading = ref(false);
const selectedSkuId = ref<string>('');
const qty = ref(1);
/** 详情页品牌行右侧迷你搜索：只在本地持有输入，提交后复用整站搜索的 /products?keyword= 口径 */
const miniKeyword = ref('');
const activeThumb = ref(0);
const uvCustom = ref(false);
const fileName = ref('');
const draftFileId = ref('');
const uploadingDraft = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

const currentSku = computed(() => product.value?.skus?.find((s) => s.skuId === selectedSkuId.value));

/**
 * 秒杀价按 SKU 归属（V54）：只有选中的规格真的参加了活动才有秒杀价。
 * 拿 SPU 级最低价顶替会让「活动只挂了 A 规格」时的 B 规格显示一个结算拿不到的低价。
 */
const skuFlashPrice = computed<number | null>(() => {
  const cent = currentSku.value?.flashPriceCent;
  return cent == null ? null : Number(cent);
});

const currentPrice = computed(() => {
  if (skuFlashPrice.value != null) return skuFlashPrice.value;
  return currentSku.value?.price.retailPriceCent ?? 0;
});
/**
 * 划线原价：秒杀生效时用该规格真实零售价（库里的值），
 * 无秒杀时保留原有「参考价 = 零售价 ×1.25」的展示口径，避免历史页面观感突变。
 */
const originPrice = computed(() => {
  if (skuFlashPrice.value != null) return currentSku.value?.price.retailPriceCent ?? 0;
  return Math.round(currentPrice.value * 1.25);
});
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

/* ---------------- 富内容（V54）：空数组就不渲染对应区块 ---------------- */

const galleryImages = computed(() => {
  const list = (product.value?.images ?? []).filter((s) => !!s && s.trim() !== '');
  if (list.length) return list;
  // 没有图集时退回主图单图：老商品只有 main_image
  return product.value?.mainImage ? [product.value.mainImage] : [];
});

const detailImages = computed(() =>
  (product.value?.detailImages ?? []).filter((s) => !!s && s.trim() !== ''));

const params = computed(() =>
  (product.value?.params ?? []).filter((p) => !!p && !!(p.label || p.value)));

/** 前 4 条放大成宫格（淘宝「核心参数」的读法），其余走表格。 */
const paramsTop = computed(() => params.value.slice(0, 4));
const paramsRest = computed(() => params.value.slice(4));

const serviceTags = computed(() =>
  (product.value?.serviceTags ?? []).filter((s) => !!s && s.trim() !== ''));

/** 选中规格的款式图优先；否则用图集当前张。 */
const activeImage = computed(() => {
  const skuImage = currentSku.value?.imageFileId;
  if (skuImage) return skuImage;
  const list = galleryImages.value;
  return list[activeThumb.value] ?? product.value?.mainImage ?? '';
});

/* ---------------- 秒杀横幅 + 倒计时 ---------------- */

const flashSale = computed(() => product.value?.flashSale ?? null);

/** 横幅价格：当前规格参加活动就用它的价，否则展示「低至 X」。 */
const bannerPrice = computed(() => {
  if (skuFlashPrice.value != null) return skuFlashPrice.value;
  return Number(flashSale.value?.flashPriceCent ?? 0);
});
const bannerOrigin = computed(() => {
  if (skuFlashPrice.value != null) return currentSku.value?.price.retailPriceCent ?? 0;
  return Number(flashSale.value?.originPriceCent ?? 0);
});

const nowMs = ref(Date.now());
let countdownTimer: number | null = null;

const remainingMs = computed(() => {
  const end = flashSale.value?.endAt;
  if (!end) return 0;
  const endMs = new Date(end).getTime();
  return Number.isFinite(endMs) ? Math.max(0, endMs - nowMs.value) : 0;
});

const countdownText = computed(() => {
  const total = Math.floor(remainingMs.value / 1000);
  const pad = (n: number) => n.toString().padStart(2, '0');
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
});

/**
 * 倒计时归零后重新拉详情：活动一结束，横幅必须消失、价格回落零售价。
 * 用一次性判断而不是每秒都请求，避免把后端打成筛子。
 */
function tickCountdown(): void {
  nowMs.value = Date.now();
  if (flashSale.value && remainingMs.value <= 0) {
    stopCountdown();
    void reloadDetail();
  }
}

function startCountdown(): void {
  stopCountdown();
  if (!flashSale.value) return;
  countdownTimer = window.setInterval(tickCountdown, 1000);
}

function stopCountdown(): void {
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

/* ---------------- 内容切换（商品详情 / 用户评价） ---------------- */

type DetailTab = 'desc' | 'review';

/** 只有两块内容分属两个面板；参数与服务承诺是商品详情的一部分，不单独占 tab */
const DETAIL_TABS: Array<{ id: DetailTab; label: string }> = [
  { id: 'desc', label: '商品详情' },
  { id: 'review', label: '用户评价' },
];

const activeTab = ref<DetailTab>('desc');

function switchTab(id: DetailTab): void {
  if (activeTab.value === id) return;
  activeTab.value = id;
  // 两个面板高度差很大：沿用旧的滚动位置会落在空白处。
  // 把 tab 条顶到视口顶部即可（它是 sticky，面板内容正好接在下面），
  // 不能滚到 top:0——那样用户点「用户评价」后看到的是上方的商品主图。
  document.querySelector('.detail-tabs')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/** 迷你搜索：与整站搜索同一口径（新开标签，空词回全部商品，不写死商品 id） */
function onMiniSearch(): void {
  const kw = miniKeyword.value.trim();
  openInNewTab(router, { path: '/products', query: kw ? { keyword: kw } : {} });
}

watch(uvCustom, (on) => {
  if (on && qty.value < 30) qty.value = 30;
});

/** 切规格时重置图集到主图，避免停留在上一个规格的款式图上。 */
watch(selectedSkuId, () => {
  activeThumb.value = 0;
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
    // 秒杀生效时按秒杀价展示；真实成交价仍由结算页取价引擎决定（企业协议价优先）
    priceCent: currentPrice.value,
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

/** 只刷新数据、不动收藏/足迹，供倒计时结束回落后复用。 */
async function reloadDetail(): Promise<void> {
  try {
    const p = await unwrap(productApi.detail(String(route.params.productId)));
    product.value = p;
    const stillThere = p.skus?.some((s) => s.skuId === selectedSkuId.value);
    if (!stillThere) {
      const first = p.skus?.find((s) => s.onShelf && s.stockQty > 0) ?? p.skus?.[0];
      selectedSkuId.value = first?.skuId ?? '';
    }
    nowMs.value = Date.now();
    startCountdown();
  } catch {
    /* 刷新失败保留旧数据，下次交互再重试 */
  }
}

onMounted(async () => {
  loading.value = true;
  try {
    const p = await unwrap(productApi.detail(String(route.params.productId)));
    product.value = p;
    const first = p.skus?.find((s) => s.onShelf && s.stockQty > 0) ?? p.skus?.[0];
    if (first) selectedSkuId.value = first.skuId;
    startCountdown();
  } catch {
    product.value = null;
  } finally {
    loading.value = false;
  }
  if (product.value) {
    await Promise.all([loadFavoriteStatus(), recordFootprint()]);
  }
});

onUnmounted(stopCountdown);

/**
 * 浏览器标签标题跟随商品名（2026-11-26 用户要求）：
 * 打开多个商品页时，标签上要能直接看出是哪一件商品，而不是一律「商品详情」。
 * 路由 afterEach 只负责写「商品详情」，这里拿到数据后覆盖。
 */
watch(() => product.value?.name, (name) => {
  if (name) document.title = `森特体育 · ${name}`;
}, { immediate: true });
</script>

<style scoped>
.detail {
  padding-top: 4px;
  /* 2026-11-25 再放大：版心与顶栏同宽（1408 @1440），右缘正好落在「帮助中心」那一列。
     1280 时右缘只到 1360，在它左边空出一截，看着像两块没拼起来的版式。 */
  max-width: calc(100% - 32px);
}

/* 页面内品牌行：左「森特 / SENT SPORTS」可点回首页，右迷你搜索（约为购买栏一半宽） */
.detail-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 2px 0 12px;
}
.detail-brand__home {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.detail-brand__home:hover .detail-brand__cn {
  color: var(--sent-accent);
}
.detail-brand__mark {
  width: 4px;
  height: 32px;
  border-radius: 2px;
  background: var(--sent-primary);
}
.detail-brand__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.1;
}
.detail-brand__cn {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 6px;
  color: var(--sent-primary);
}
.detail-brand__en {
  font-size: 11px;
  letter-spacing: 3.2px;
  color: var(--sent-muted);
  text-transform: uppercase;
}

/* 迷你搜索：宽度 = 版心 20%（购买栏 40% 的一半），只有输入框 + 搜索按钮 */
.detail-mini-search {
  display: flex;
  align-items: stretch;
  width: 20%;
  min-width: 200px;
  flex: 0 0 auto;
}
.detail-mini-search input {
  flex: 1 1 auto;
  min-width: 0;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--sent-border);
  border-right: none;
  border-radius: 6px 0 0 6px;
  background: #fff;
  color: var(--sent-text);
  font-size: 13px;
  outline: none;
}
.detail-mini-search input:focus {
  border-color: var(--sent-primary);
}
.detail-mini-search button {
  flex: 0 0 auto;
  min-width: 64px;
  border: none;
  border-radius: 0 6px 6px 0;
  background: var(--sent-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.detail-mini-search button:hover {
  filter: brightness(1.08);
}

.detail-crumb {
  margin-bottom: 16px;
  font-size: 13px;
}

/* 两栏：左 60%（主图 + 详情/评价同列）| 右 40%（购买栏，sticky 跟随） */
.detail-main {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: 24px;
  align-items: start;
}
.detail-left {
  min-width: 0;
}
/* 竖排缩略图 56px | 主图自适应：只作用于左列内部，不影响 60/40 的列宽比例 */
.gallery-block {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}
/* 图集 ≤1 张：rail 节点不渲染，收掉第一列，否则主图会被 Grid 塞进 56px 轨道 */
.gallery-block--no-rail {
  grid-template-columns: minmax(0, 1fr);
}

/* 竖排缩略图 */
.gallery-rail {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.gallery-rail__item {
  padding: 0;
  width: 56px;
  border: 2px solid transparent;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
  cursor: pointer;
  opacity: 0.85;
  transition: all 0.15s;
}
.gallery-rail__item:hover,
.gallery-rail__item.active {
  border-color: var(--sent-primary);
  opacity: 1;
}

/* 画廊 */
.gallery__main {
  background: #fff;
  border-radius: var(--sent-radius);
  overflow: hidden;
  border: 1px solid #eee;
  /* 主图跟随左列宽度（约 686px），不再写死 520 上限——详情页的主图就该是首屏主角 */
  max-width: 100%;
}
/* 主图自身是 1:1，宽度由父容器决定，避免 min-width 之外的二次放大 */
.gallery__main .thumb {
  max-width: 100%;
}
/* 宽屏用 rail，横排缩略图只在窄屏出现 */
.gallery__thumbs {
  display: none;
  gap: 10px;
  margin-top: 12px;
}
.gallery__thumb {
  flex: 1 1 0;
  min-width: 0;
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
  padding: 26px 28px;
  border: 1px solid #eef0f2;
  /* 吸顶跟随（2026-11-25）：详情页已无整站搜索区，顶部只需让开 16px；
     旧值 88px 是给 56px 搜索头留的，现在会把购买栏压到半屏以下。 */
  position: sticky;
  top: 16px;
  /* 与主图等高（2026-11-25 用户要求）：主图 ≈ (版心-24)*60% - 68 = 60vw - 102，
     1440 下 762px。用 flex 列把 CTA 推到底，避免卡片拉高后按钮下面留一大块空。 */
  display: flex;
  flex-direction: column;
  min-height: min(760px, calc(60vw - 102px));
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

/* 秒杀横幅：红底条，仅在活动生效期出现 */
.flash-sale {
  display: flex;
  align-items: stretch;
  gap: 0;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 12px;
  background: linear-gradient(90deg, #e5342b, #ff6a3d);
  color: #fff;
}
.flash-sale__badge {
  flex: none;
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  background: rgba(0, 0, 0, 0.16);
  writing-mode: vertical-rl;
}
.flash-sale__body {
  flex: 1;
  min-width: 0;
  padding: 10px 14px;
}
.flash-sale__pricebox {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.flash-sale__sym {
  font-size: 14px;
}
.flash-sale__num {
  font-size: 24px;
  line-height: 1;
  font-weight: 800;
}
.flash-sale__low {
  font-size: 12px;
  margin-right: 4px;
}
.flash-sale__origin {
  font-size: 12px;
  opacity: 0.8;
  text-decoration: line-through;
}
.flash-sale__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  margin-top: 4px;
  opacity: 0.95;
}
.flash-sale__title {
  font-weight: 600;
}
.flash-sale__countdown {
  font-variant-numeric: tabular-nums;
  background: rgba(0, 0, 0, 0.22);
  border-radius: 3px;
  padding: 0 6px;
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
  color: var(--sent-price);
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
/* Element Plus 的 .el-checkbox 默认 white-space: nowrap、固定 32px 高，
   定制说明是一整行长文案，窄屏购买栏（400px）会横向撑出虚线边框。
   这里放开换行并让勾选框与首行文字顶端对齐。 */
.custom-panel__check {
  display: flex;
  align-items: flex-start;
  height: auto;
  white-space: normal;
}
.custom-panel__check :deep(.el-checkbox__input) {
  margin-top: 3px;
}
.custom-panel__check :deep(.el-checkbox__label) {
  font-size: 13px;
  color: var(--sent-text-2);
  white-space: normal;
  line-height: 1.6;
  word-break: break-word;
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
  /* 卡片被 min-height 拉高后，CTA 贴在卡片底部（淘宝式），不悬在半空 */
  margin-top: auto;
  padding-top: 24px;
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

/* 锚点导航（V54）：sticky 在面包屑下方，滚动时始终可点 */
.detail-tabs {
  position: sticky;
  top: 0;
  z-index: 6;
  display: flex;
  gap: 6px;
  margin-top: 26px;
  padding: 8px 0;
  background: var(--sent-bg, #f6f7f8);
  border-bottom: 1px solid #eceef0;
}
.detail-tabs__item {
  border: none;
  background: transparent;
  padding: 8px 18px;
  font-size: 14px;
  color: var(--sent-text-2);
  border-radius: 6px;
  cursor: pointer;
}
.detail-tabs__item:hover {
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
}
.detail-tabs__item.active {
  color: #fff;
  background: var(--sent-primary);
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
.detail-desc__text {
  white-space: pre-wrap;
}
.detail-desc__images {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-top: 12px;
}
/* 详情长图按序贴满整宽，不加圆角间隙——长图之间有缝会显得断开 */
.detail-desc__img {
  width: 100%;
  height: auto;
  display: block;
}
.detail-desc__empty {
  color: var(--sent-muted);
  margin: 0;
}

/* 规格参数（V54）：前 4 条放大成宫格，其余走表格 */
.detail-params {
  margin-top: 20px;
  background: #fff;
  border-radius: var(--sent-radius);
  border: 1px solid #eee;
  padding: 18px 22px 22px;
}
.detail-params__head {
  font-size: 16px;
  font-weight: 600;
  color: var(--sent-text);
  margin-bottom: 14px;
}
.detail-params__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.detail-params__card {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.detail-params__label {
  font-size: 12px;
  color: var(--sent-muted);
}
.detail-params__value {
  font-size: 15px;
  color: var(--sent-text);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-params__table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 16px;
  font-size: 13px;
}
.detail-params__table th {
  width: 140px;
  text-align: left;
  font-weight: 400;
  color: var(--sent-muted);
  padding: 10px 12px;
  background: #fafbfb;
  border: 1px solid #f0f2f3;
}
.detail-params__table td {
  padding: 10px 12px;
  color: var(--sent-text-2);
  border: 1px solid #f0f2f3;
}

/* 服务承诺 */
.detail-service {
  margin-top: 20px;
  margin-bottom: 30px;
  background: #fff;
  border-radius: var(--sent-radius);
  border: 1px solid #eee;
  padding: 18px 22px;
}
.detail-service__head {
  font-size: 16px;
  font-weight: 600;
  color: var(--sent-text);
  margin-bottom: 12px;
}
.detail-service__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.detail-service__tag {
  font-size: 13px;
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
  border-radius: 14px;
  padding: 5px 14px;
}

/* 窄屏（≤1240）：60/40 保持不变，只收起竖排缩略图（改用主图下方横排）
   注意：这里不能改 `.detail-main` 的列定义，否则左右比例会跳变；
   要收的是左列内部的 `.gallery-block` 轨道。 */
@media (max-width: 1240px) {
  .gallery-block {
    grid-template-columns: minmax(0, 1fr);
  }
  .gallery-rail {
    display: none;
  }
  .gallery__thumbs {
    display: flex;
  }
  /* 缩略图轨道没了，主图宽度多出 68px：购买栏高度跟着重算，才能继续与主图齐平 */
  .buy {
    min-height: min(820px, calc(60vw - 34px));
  }
}

/* 单栏（≤960）：购买栏落到主图下方，sticky 会让它在窄屏遮住内容，直接关掉 */
@media (max-width: 960px) {
  .detail-main {
    grid-template-columns: minmax(0, 1fr);
  }
  .detail-brand {
    flex-wrap: wrap;
  }
  .buy {
    position: static;
    min-height: 0;
  }
  .detail-params__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
