<template>
  <div class="container group-buy">
    <!-- 头部：团购定位 + 三个关键数字 -->
    <header class="gb-hero">
      <div class="gb-hero__main">
        <span class="gb-hero__tag">企业采购 · 团购专区</span>
        <h1 class="gb-hero__title">批量采购，越买越省</h1>
        <p class="gb-hero__desc">
          运动装备 / 赛事器材 / 定制队服一站配齐，30 件起订。
          阶梯报价、账期结算、增值税专用发票全流程支持。
        </p>
        <div class="gb-hero__actions">
          <button class="gb-hero__btn" @click="goChat">💬 联系客户经理</button>
          <router-link to="/chat" class="gb-hero__btn gb-hero__btn--ghost">定制专属方案</router-link>
        </div>
      </div>
      <ul class="gb-hero__stats">
        <li><b>30 件</b><span>起订量</span></li>
        <li><b>最高 8 折</b><span>阶梯价</span></li>
        <li><b>60 天</b><span>账期结算</span></li>
      </ul>
    </header>

    <!-- 阶梯报价 -->
    <section class="gb-block">
      <div class="gb-block__head">
        <h3>阶梯报价</h3>
        <span class="gb-block__sub">下单数量越多单价越低，最终价格以合同为准</span>
      </div>
      <div class="gb-tiers">
        <div v-for="t in TIERS" :key="t.qty" class="gb-tier" :class="{ 'gb-tier--best': t.best }">
          <span v-if="t.best" class="gb-tier__flag">最受欢迎</span>
          <div class="gb-tier__qty">{{ t.qty }}</div>
          <div class="gb-tier__off">{{ t.off }}</div>
          <div class="gb-tier__note">{{ t.note }}</div>
        </div>
      </div>
    </section>

    <!-- 企业权益 -->
    <section class="gb-block">
      <div class="gb-block__head">
        <h3>企业权益</h3>
        <span class="gb-block__sub">下单即享，无需额外申请</span>
      </div>
      <ul class="gb-rights">
        <li v-for="r in RIGHTS" :key="r.title" class="gb-right">
          <span class="gb-right__icon">{{ r.icon }}</span>
          <div class="gb-right__text">
            <b>{{ r.title }}</b>
            <p>{{ r.desc }}</p>
          </div>
        </li>
      </ul>
    </section>

    <!-- 批量采购商品 -->
    <section class="gb-block">
      <div class="gb-block__head">
        <h3>批量采购热销</h3>
        <router-link to="/products" class="gb-block__more">查看全部商品 ›</router-link>
      </div>
      <div v-if="loading" class="list-hint">加载中…</div>
      <el-empty v-else-if="products.length === 0" description="暂无商品" />
      <div v-else class="goods-grid">
        <GoodsCard
          v-for="p in products"
          :key="p.productId"
          :product="p"
          shop="森特体育自营店"
          @click="goDetail(p.productId)"
        />
      </div>
    </section>

    <!-- 底部 CTA -->
    <section class="gb-cta">
      <div class="gb-cta__text">
        <b>需要报价单或批量选型建议？</b>
        <p>把清单发给客户经理，2 小时内出阶梯报价与货期。</p>
      </div>
      <div class="gb-cta__actions">
        <button class="gb-cta__btn" @click="goChat">在线咨询</button>
        <router-link to="/products" class="gb-cta__btn gb-cta__btn--ghost">继续选购</router-link>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 团购专区（企业采购）。
 *
 * 背景：首页「查看团购专区」原来落到 `/products?keyword=训练`，点进去看到的全是训练器材，
 * 与「团购/企业采购」完全不对应。这里给它一个真正的落点：
 * 阶梯报价 + 企业权益 + 批量采购商品，数据仍走真实商品接口，不做假数据。
 */
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { unwrap } from '@/utils/http';
import { productApi, type CatalogProductVO } from '@/api/product';
import GoodsCard from '@/components/GoodsCard.vue';

const router = useRouter();

/** 阶梯档位：文案是采购政策的明示，不参与计算 */
const TIERS = [
  { qty: '30 - 99 件', off: '9.5 折', note: '适合部门 / 小队采购', best: false },
  { qty: '100 - 299 件', off: '9 折', note: '适合学校 / 俱乐部', best: false },
  { qty: '300 - 999 件', off: '8.5 折', note: '适合赛事 / 健身房', best: true },
  { qty: '1000 件以上', off: '8 折', note: '适合连锁 / 集团采购', best: false },
];

const RIGHTS = [
  { icon: '🧾', title: '增值税专用发票', desc: '下单即可开具，支持月结汇总开票' },
  { icon: '📅', title: '账期结算', desc: '资质审核通过后最长 60 天账期' },
  { icon: '📊', title: '阶梯报价单', desc: '按清单出正式报价，可加盖公章' },
  { icon: '🤝', title: '专属客户经理', desc: '1 对 1 跟进选型、打样与交付' },
];

const products = ref<CatalogProductVO[]>([]);
const loading = ref(false);

const BATCH_SIZE = 10;

async function loadProducts(): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(productApi.list({ page: 1, size: BATCH_SIZE }));
    products.value = res.list ?? [];
  } catch {
    products.value = [];
  } finally {
    loading.value = false;
  }
}

function goChat(): void {
  router.push('/chat');
}

function goDetail(id: string): void {
  router.push(`/product/${id}`);
}

onMounted(loadProducts);
</script>

<style scoped>
.group-buy {
  padding-top: 14px;
}

/* ---------- 头部 ---------- */
.gb-hero {
  display: flex;
  align-items: center;
  gap: 24px;
  background: linear-gradient(120deg, #0d4d3b 0%, #12664f 52%, #1c8a6b 100%);
  border-radius: var(--sent-radius);
  padding: 26px 28px;
  color: #fff;
}
.gb-hero__main {
  flex: 1;
  min-width: 0;
}
.gb-hero__tag {
  display: inline-block;
  font-size: 12px;
  letter-spacing: 1px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 12px;
  padding: 3px 12px;
}
.gb-hero__title {
  margin: 10px 0 8px;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 1px;
}
.gb-hero__desc {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.7;
  opacity: 0.92;
  max-width: 560px;
}
.gb-hero__actions {
  display: flex;
  gap: 10px;
}
.gb-hero__btn {
  border: none;
  cursor: pointer;
  background: #fff;
  color: var(--sent-primary);
  font-size: 13px;
  font-weight: 700;
  border-radius: 20px;
  padding: 9px 20px;
  transition: box-shadow 0.15s;
}
.gb-hero__btn:hover {
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.18);
}
.gb-hero__btn--ghost {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.6);
  color: #fff;
}
.gb-hero__stats {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  gap: 28px;
  flex: none;
}
.gb-hero__stats li {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.gb-hero__stats b {
  font-size: 20px;
  font-weight: 800;
}
.gb-hero__stats span {
  font-size: 12px;
  opacity: 0.85;
}

/* ---------- 区块通用 ---------- */
.gb-block {
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 18px;
  margin-top: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.gb-block__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}
.gb-block__head h3 {
  margin: 0;
  font-size: 17px;
  color: var(--sent-text);
  position: relative;
  padding-left: 12px;
}
.gb-block__head h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 3px;
  bottom: 3px;
  width: 4px;
  border-radius: 2px;
  background: var(--sent-accent);
}
.gb-block__sub {
  font-size: 12px;
  color: var(--sent-muted);
}
.gb-block__more {
  margin-left: auto;
  font-size: 13px;
  color: var(--sent-muted);
}
.gb-block__more:hover {
  color: var(--sent-accent);
}

/* ---------- 阶梯价 ---------- */
.gb-tiers {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.gb-tier {
  position: relative;
  border: 1px solid #eef0f2;
  border-radius: 10px;
  padding: 16px;
  text-align: center;
  background: #fafbfc;
  transition: border-color 0.15s, transform 0.15s;
}
.gb-tier:hover {
  border-color: var(--sent-primary);
  transform: translateY(-2px);
}
.gb-tier--best {
  background: var(--sent-primary-soft);
  border-color: var(--sent-primary);
}
.gb-tier__flag {
  position: absolute;
  top: -9px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  color: #fff;
  background: var(--sent-accent);
  border-radius: 9px;
  padding: 1px 10px;
  white-space: nowrap;
}
.gb-tier__qty {
  font-size: 13px;
  color: var(--sent-text-2);
}
.gb-tier__off {
  margin: 8px 0 6px;
  font-size: 22px;
  font-weight: 800;
  color: var(--sent-accent);
}
.gb-tier__note {
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 企业权益 ---------- */
.gb-rights {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.gb-right {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  border: 1px solid #f2f3f5;
  border-radius: 10px;
  padding: 14px;
}
.gb-right__icon {
  font-size: 22px;
  line-height: 1;
  flex: none;
}
.gb-right__text b {
  font-size: 13px;
  color: var(--sent-text);
}
.gb-right__text p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--sent-muted);
}

/* ---------- 商品网格：列数由全局 .goods-grid 自适应（1440 版心 → 6 列） ---------- */
.list-hint {
  padding: 18px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}

/* ---------- 底部 CTA ---------- */
.gb-cta {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 20px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 18px 22px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border-left: 4px solid var(--sent-accent);
}
.gb-cta__text b {
  font-size: 15px;
  color: var(--sent-text);
}
.gb-cta__text p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--sent-muted);
}
.gb-cta__actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
  flex: none;
}
.gb-cta__btn {
  border: none;
  cursor: pointer;
  background: var(--sent-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border-radius: 18px;
  padding: 8px 22px;
}
.gb-cta__btn:hover {
  background: var(--sent-primary-dark);
}
.gb-cta__btn--ghost {
  background: #fff;
  color: var(--sent-primary);
  border: 1px solid var(--sent-primary);
}

@media (max-width: 1240px) {
  .gb-hero {
    flex-direction: column;
    align-items: flex-start;
  }
  .gb-tiers,
  .gb-rights {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
