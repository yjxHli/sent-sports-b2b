<template>
  <!-- 顶部窄条：右侧 账户/订单/会员中心/在线客服/消息/购物车
       2026-11-27 从 layout/index.vue 抽出：个人中心外壳（layout/mine.vue）也要这一条，
       两套版式共用一份标记，避免「站点版改了、个人中心版忘改」。
       样式真源是全局 style.css 的 `.topbar` 段，本组件不写 `<style>`。 -->
  <div class="topbar">
    <div class="container topbar__inner">
      <span class="topbar__welcome">您好，欢迎来到森特体育商城！专业运动装备 · 企业团购 · 会员专享</span>
      <div class="topbar__links">
        <!-- 首页放最前（淘宝顶栏就是「淘宝网首页」开头），未登录也能回首页 -->
        <router-link to="/home" class="topbar__link topbar__home">首页</router-link>
        <span class="topbar__sep"></span>
        <template v-if="auth.isLoggedIn">
          <div class="topbar__account" data-testid="topbar-account">
            <router-link to="/mine" target="_blank" rel="noopener" class="topbar__link topbar__account-trigger" data-testid="topbar-account-name">
              {{ accountName }}<i class="topbar__account-arrow">▾</i>
            </router-link>
            <div class="topbar__account-menu">
              <router-link to="/mine" target="_blank" rel="noopener" class="topbar__account-item" data-testid="topbar-account-link">个人账户</router-link>
              <button type="button" class="topbar__account-item" data-testid="topbar-logout" @click="onLogout">
                退出登录
              </button>
            </div>
          </div>
        </template>
        <template v-else>
          <router-link to="/login" class="topbar__link">请登录</router-link>
          <router-link to="/login" class="topbar__link">免费注册</router-link>
        </template>
        <span class="topbar__sep"></span>
        <div class="topbar__orders">
          <router-link to="/orders" target="_blank" rel="noopener" class="topbar__link">我的订单<i class="topbar__orders-arrow">▾</i></router-link>
          <div class="topbar__orders-menu">
            <router-link class="topbar__orders-item" data-nav="goods-orders" to="/orders" target="_blank" rel="noopener">商品订单</router-link>
            <router-link class="topbar__orders-item" data-nav="custom-orders" to="/custom" target="_blank" rel="noopener">定制订单</router-link>
          </div>
        </div>
        <span class="topbar__sep"></span>
        <!-- 2026-11-28 用户要求：顶栏去掉「收藏夹」与「会员中心」两个入口，
             收藏在个人中心侧栏的「商品收藏」，会员中心在侧栏一级项 -->
        <!-- 在线客服 / 购物车：新开标签页（用户 2026-11-25 口径 —— 不打断正在浏览的商品页） -->
        <router-link to="/chat" target="_blank" rel="noopener" class="topbar__link topbar__chat">在线客服</router-link>
        <span class="topbar__sep"></span>
        <router-link to="/messages" target="_blank" rel="noopener" class="topbar__link topbar__msg">
          消息<i v-if="msg.unreadCount > 0" class="msg-badge">{{ msg.unreadCount > 99 ? '99+' : msg.unreadCount }}</i>
        </router-link>
        <span class="topbar__sep"></span>
        <router-link to="/cart" target="_blank" rel="noopener" class="topbar__link topbar__cart">
          购物车<i v-if="cartCount > 0" class="cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</i>
        </router-link>
        <span class="topbar__sep"></span>
        <div class="topbar__help">
          <router-link to="/help/category/shopping-guide" target="_blank" rel="noopener" class="topbar__link topbar__help-trigger">帮助中心<i class="topbar__help-arrow">▾</i></router-link>
          <div class="topbar__help-menu">
            <router-link class="topbar__help-item" data-category="shopping-guide" to="/help/category/shopping-guide" target="_blank" rel="noopener">购物指南</router-link>
            <router-link class="topbar__help-item" data-category="delivery" to="/help/category/delivery" target="_blank" rel="noopener">配送方式</router-link>
            <router-link class="topbar__help-item" data-category="after-sales" to="/help/category/after-sales" target="_blank" rel="noopener">售后服务</router-link>
            <router-link class="topbar__help-item" data-category="about" to="/help/category/about" target="_blank" rel="noopener">关于森特</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 站点顶栏（窄条）。站点 Layout 与个人中心 Layout 共用。
 *
 * 逻辑与原先 layout/index.vue 里的顶栏完全一致，只是搬了家：
 * - 账户名真源 GET /api/me（不读本地缓存）
 * - 购物车角标来自 useCartStore，站内信红点来自 useMessageStore（60s 轮询 + 可见性补拉）
 * - 退出登录走确认弹窗 → auth.logout() → /login
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { useCartStore } from '@/store/modules/useCartStore';
import { useMessageStore } from '@/store/modules/useMessageStore';
import { profileApi } from '@/api/profile';
import { unwrap } from '@/utils/http';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const cart = useCartStore();
const msg = useMessageStore();

/** 顶栏账户名：真源是 GET /api/me，不写死、不读本地缓存。 */
const accountName = ref('我的账户');

async function loadAccountName(): Promise<void> {
  if (!auth.isLoggedIn) {
    accountName.value = '我的账户';
    return;
  }
  try {
    const me = await unwrap(profileApi.me());
    accountName.value = me?.nickname || '我的账户';
  } catch {
    accountName.value = '我的账户';
  }
}

async function onLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await auth.logout();
  accountName.value = '我的账户';
  ElMessage.success('已退出登录');
  await router.replace('/login');
}

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

onMounted(() => {
  void loadAccountName();
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

/** 切回标签页时立刻补一次，避免「刚切回来还是旧数字」 */
function onVisible(): void {
  if (document.visibilityState === 'visible' && auth.isLoggedIn) void loadUnread();
}
window.addEventListener('visibilitychange', onVisible);

onBeforeUnmount(() => {
  stopUnreadPoll();
  window.removeEventListener('visibilitychange', onVisible);
});

/** 从消息中心返回时红点要跟着变；个人中心改昵称后顶栏也跟着刷新 */
watch(() => route.fullPath, (path) => {
  if (path.startsWith('/messages')) void loadUnread();
  if (path.startsWith('/mine') || path.startsWith('/profile')) void loadAccountName();
});
watch(() => auth.isLoggedIn, () => void loadAccountName());
</script>
