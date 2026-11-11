<template>
  <div class="site">
    <!-- 顶部窄条：右侧 登录/订单/消息/购物车 -->
    <div class="topbar">
      <div class="container topbar__inner">
        <span class="topbar__welcome">您好，欢迎来到森特体育商城！专业运动装备 · 企业团购 · 会员专享</span>
        <div class="topbar__links">
          <template v-if="auth.isLoggedIn">
            <router-link to="/mine" class="topbar__link">我的账户</router-link>
          </template>
          <template v-else>
            <router-link to="/login" class="topbar__link">请登录</router-link>
            <router-link to="/login" class="topbar__link">免费注册</router-link>
          </template>
          <span class="topbar__sep"></span>
          <router-link to="/orders" class="topbar__link">我的订单</router-link>
          <span class="topbar__sep"></span>
          <router-link to="/chat" class="topbar__link">消息</router-link>
          <span class="topbar__sep"></span>
          <router-link to="/cart" class="topbar__link topbar__cart">
            购物车<i v-if="cartCount > 0" class="cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</i>
          </router-link>
        </div>
      </div>
    </div>

    <!-- 搜索区：左 Logo / 中加粗搜索框 / 下方热搜词 -->
    <div class="searchbar">
      <div class="container searchbar__inner">
        <router-link to="/home" class="brand">
          <span class="brand__mark">S</span>
          <span class="brand__text">
            <b>森特体育</b>
            <i>SENT SPORTS</i>
          </span>
        </router-link>

        <div class="searchbox">
          <div class="searchbox__field">
            <input
              v-model="keyword"
              class="searchbox__input"
              placeholder="搜索商品 / 品牌 / 分类，如「篮球」「瑜伽垫」"
              @keyup.enter="onSearch"
            />
            <button class="searchbox__btn" @click="onSearch">搜 索</button>
          </div>
          <div class="searchbox__hot">
            <span>热搜：</span>
            <a v-for="h in HOT_WORDS" :key="h" @click="searchWord(h)">{{ h }}</a>
          </div>
        </div>

        <div class="searchbar__right">
          <router-link to="/cart" class="mini-cart">
            <span>🛒</span><em>购物车</em><b>{{ cartCount }}</b>
          </router-link>
        </div>
      </div>
    </div>

    <!-- 主导航 -->
    <nav class="mainnav">
      <div class="container mainnav__inner">
        <router-link
          v-for="m in NAV_ITEMS"
          :key="m.path"
          :to="m.path"
          class="mainnav__item"
          :class="{ active: isActive(m.path) }"
        >
          {{ m.title }}
        </router-link>
      </div>
    </nav>

    <main class="site-main">
      <router-view />
    </main>

    <footer class="site-footer">
      <div class="container">
        <div class="footer__cols">
          <div class="footer__col">
            <h4>购物指南</h4>
            <p>购物流程</p>
            <p>会员介绍</p>
            <p>常见问题</p>
          </div>
          <div class="footer__col">
            <h4>配送方式</h4>
            <p>顺丰速运</p>
            <p>中通 / 圆通</p>
            <p>企业专送</p>
          </div>
          <div class="footer__col">
            <h4>售后服务</h4>
            <p>退换货政策</p>
            <p>定制商品说明</p>
            <p>发票与对账</p>
          </div>
          <div class="footer__col">
            <h4>关于森特</h4>
            <p>品牌故事</p>
            <p>企业采购</p>
            <p>联系我们</p>
          </div>
        </div>
        <div class="footer__copy">© 2026 森特体育用品有限公司 · 专业运动装备 B2B 商城 · 演示环境</div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { useCartStore } from '@/store/modules/useCartStore';
import { MENU_ROUTES } from '@/router/modules/business';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const cart = useCartStore();

const keyword = ref('');

const HOT_WORDS = ['篮球', '瑜伽垫', '跑步鞋', '定制球衣', '哑铃', '泳镜'];

/** 主导航顺序（文案统一取 MENU_ROUTES，保持菜单单一真源） */
const NAV_ORDER = ['/home', '/products', '/orders', '/member', '/chat', '/mine'];
const NAV_ITEMS = NAV_ORDER.map((path) => ({
  path,
  title: MENU_ROUTES.find((m) => m.path === path)?.title ?? path,
}));

const cartCount = computed(() => cart.totalQty);

function isActive(path: string): boolean {
  if (path === '/home') return route.path === '/' || route.path === '/home';
  return route.path === path || route.path.startsWith(path + '/');
}

function onSearch(): void {
  const kw = keyword.value.trim();
  router.push({ path: '/products', query: kw ? { keyword: kw } : {} });
}

function searchWord(word: string): void {
  keyword.value = word;
  onSearch();
}

onMounted(() => {
  if (auth.isLoggedIn) {
    cart.load().catch(() => { /* 购物车拉取失败不阻塞浏览 */ });
  }
});
</script>
