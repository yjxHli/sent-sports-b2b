<template>
  <div class="site">
    <!-- 顶部窄条：右侧 账户/订单/会员中心/在线客服/消息/购物车 -->
    <div class="topbar">
      <div class="container topbar__inner">
        <span class="topbar__welcome">您好，欢迎来到森特体育商城！专业运动装备 · 企业团购 · 会员专享</span>
        <div class="topbar__links">
          <!-- 首页放最前（淘宝顶栏就是「淘宝网首页」开头），未登录也能回首页 -->
          <router-link to="/home" class="topbar__link topbar__home">首页</router-link>
          <span class="topbar__sep"></span>
          <template v-if="auth.isLoggedIn">
            <router-link to="/mine" class="topbar__link">我的账户</router-link>
          </template>
          <template v-else>
            <router-link to="/login" class="topbar__link">请登录</router-link>
            <router-link to="/login" class="topbar__link">免费注册</router-link>
          </template>
          <span class="topbar__sep"></span>
          <router-link to="/favorites" class="topbar__link topbar__fav">收藏夹</router-link>
          <span class="topbar__sep"></span>
          <router-link to="/orders" class="topbar__link">我的订单</router-link>
          <span class="topbar__sep"></span>
          <router-link to="/member" class="topbar__link topbar__member">会员中心</router-link>
          <span class="topbar__sep"></span>
          <router-link to="/chat" class="topbar__link topbar__chat">在线客服</router-link>
          <span class="topbar__sep"></span>
          <router-link to="/messages" class="topbar__link topbar__msg">
            消息<i v-if="msg.unreadCount > 0" class="msg-badge">{{ msg.unreadCount > 99 ? '99+' : msg.unreadCount }}</i>
          </router-link>
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
      </div>
    </div>

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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { useCartStore } from '@/store/modules/useCartStore';
import { useMessageStore } from '@/store/modules/useMessageStore';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const cart = useCartStore();
const msg = useMessageStore();

const keyword = ref('');

const HOT_WORDS = ['篮球', '瑜伽垫', '跑步鞋', '定制球衣', '哑铃', '泳镜'];

const cartCount = computed(() => cart.totalQty);

/**
 * 站内信未读数（顶栏红点）。
 * 真源是 {@link useMessageStore}：消息页标记已读/全部已读/删除后都会刷新同一个 ref，
 * 顶栏红点随点击实时变化，不必等 60s 轮询或路由切换。
 */
async function loadUnread(): Promise<void> {
  if (!auth.isLoggedIn) {
    msg.reset();
    return;
  }
  await msg.refresh();
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
    void loadUnread();
    startUnreadPoll();
  }
});

/**
 * 未读红点轮询：站内信是「别人触发」的（客服审核驳回退款、订单发货……），
 * 只在进入页面时拉一次的话，用户停在订单详情页就永远看不到驳回通知。
 * 60s 一次、页面不可见时跳过，成本可忽略。
 */
const UNREAD_POLL_MS = 60_000;
let unreadTimer: number | undefined;

function startUnreadPoll(): void {
  stopUnreadPoll();
  unreadTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') void loadUnread();
  }, UNREAD_POLL_MS);
}

function stopUnreadPoll(): void {
  if (unreadTimer !== undefined) {
    window.clearInterval(unreadTimer);
    unreadTimer = undefined;
  }
}

onBeforeUnmount(stopUnreadPoll);

/** 切回标签页时立刻补一次，避免「刚切回来还是旧数字」 */
function onVisible(): void {
  if (document.visibilityState === 'visible' && auth.isLoggedIn) void loadUnread();
}
window.addEventListener('visibilitychange', onVisible);
onBeforeUnmount(() => window.removeEventListener('visibilitychange', onVisible));

/** 从消息中心返回时红点要跟着变 */
watch(() => route.fullPath, (path) => {
  if (path.startsWith('/messages')) void loadUnread();
});
</script>
