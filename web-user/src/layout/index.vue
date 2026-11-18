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
            <div class="topbar__account" data-testid="topbar-account">
              <span class="topbar__link topbar__account-trigger" data-testid="topbar-account-name">
                {{ accountName }}<i class="topbar__account-arrow">▾</i>
              </span>
              <div class="topbar__account-menu">
                <router-link to="/mine" class="topbar__account-item" data-testid="topbar-account-link">个人账户</router-link>
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
          <router-link to="/favorites" class="topbar__link topbar__fav">收藏夹</router-link>
          <span class="topbar__sep"></span>
          <div class="topbar__orders">
            <router-link to="/orders" class="topbar__link">我的订单<i class="topbar__orders-arrow">▾</i></router-link>
            <div class="topbar__orders-menu">
              <router-link class="topbar__orders-item" data-nav="goods-orders" to="/orders">商品订单</router-link>
              <router-link class="topbar__orders-item" data-nav="custom-orders" to="/custom">定制订单</router-link>
            </div>
          </div>
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
          <span class="topbar__sep"></span>
          <div class="topbar__help">
            <span class="topbar__link topbar__help-trigger">帮助中心<i class="topbar__help-arrow">▾</i></span>
            <div class="topbar__help-menu">
              <router-link class="topbar__help-item" data-category="shopping-guide" to="/help/category/shopping-guide">购物指南</router-link>
              <router-link class="topbar__help-item" data-category="delivery" to="/help/category/delivery">配送方式</router-link>
              <router-link class="topbar__help-item" data-category="after-sales" to="/help/category/after-sales">售后服务</router-link>
              <router-link class="topbar__help-item" data-category="about" to="/help/category/about">关于森特</router-link>
            </div>
          </div>
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
              data-testid="search-input"
              placeholder="搜索商品 / 品牌 / 分类，如「篮球」「瑜伽垫」"
              @focus="onFocus"
              @blur="onBlur"
              @input="onInput"
              @keyup.enter="onSearch"
            />
            <button class="searchbox__btn" @click="onSearch">搜 索</button>

            <!-- 联想下拉：有输入显示相似商品名；空输入显示历史搜索（参考图） -->
            <div v-if="dropdownOpen" class="search-suggest" data-testid="search-suggest">
              <template v-if="keyword.trim()">
                <button
                  v-for="item in suggestions"
                  :key="item.productId"
                  class="search-suggest__item"
                  data-testid="search-suggest-item"
                  @mousedown.prevent="clickSuggestion(item)"
                >
                  {{ item.name }}
                  <small v-if="item.categoryName">{{ item.categoryName }}</small>
                </button>
                <div v-if="!suggestions.length" class="search-suggest__empty">没有找到相似商品</div>
              </template>
              <template v-else>
                <div class="search-suggest__head">
                  <span>历史搜索</span>
                  <button data-testid="clear-history" @mousedown.prevent="clearHistory">清除</button>
                </div>
                <div v-if="history.length" class="search-suggest__history">
                  <button
                    v-for="word in history"
                    :key="word"
                    data-testid="history-item"
                    @mousedown.prevent="searchWord(word)"
                  >{{ word }}</button>
                </div>
                <div v-else class="search-suggest__empty">暂无历史搜索</div>
              </template>
            </div>
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
        <div class="footer__copy">© 2026 森特体育用品有限公司 · 专业运动装备 B2B 商城 · 演示环境</div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { useCartStore } from '@/store/modules/useCartStore';
import { useMessageStore } from '@/store/modules/useMessageStore';
import { productApi } from '@/api/product';
import { profileApi } from '@/api/profile';
import { unwrap } from '@/utils/http';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const cart = useCartStore();
const msg = useMessageStore();

const keyword = ref('');

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

const HOT_WORDS = ['篮球', '瑜伽垫', '跑步鞋', '定制球衣', '哑铃', '泳镜'];

/**
 * 搜索联想 + 历史搜索（2026-11-17 用户要求）。
 * 历史存 localStorage，按「最新在前」去重保留 10 条；登录与否都能用。
 */
const HISTORY_KEY = 'sent_search_history_user';
const HISTORY_MAX = 10;
interface SuggestItem { productId: string; name: string; categoryId: number | null; categoryName?: string | null }
const suggestions = ref<SuggestItem[]>([]);
const history = ref<string[]>(loadHistory());
const dropdownOpen = ref(false);
let suggestTimer: ReturnType<typeof setTimeout> | undefined;
let blurTimer: ReturnType<typeof setTimeout> | undefined;

function loadHistory(): string[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'string').slice(0, HISTORY_MAX) : [];
  } catch {
    return [];
  }
}

function rememberSearch(word: string): void {
  const kw = word.trim();
  if (!kw) return;
  history.value = [kw, ...history.value.filter((h) => h !== kw)].slice(0, HISTORY_MAX);
  try { localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value)); } catch { /* 隐私模式忽略 */ }
}

function clearHistory(): void {
  history.value = [];
  try { localStorage.removeItem(HISTORY_KEY); } catch { /* 隐私模式忽略 */ }
}

function onFocus(): void {
  if (blurTimer) clearTimeout(blurTimer);
  dropdownOpen.value = true;
  if (!keyword.value.trim()) suggestions.value = [];
}

function onBlur(): void {
  // 选项用 mousedown.prevent，不会先触发 blur；这里延迟关，给点击留时间
  blurTimer = setTimeout(() => { dropdownOpen.value = false; }, 150);
}

function onInput(): void {
  if (suggestTimer) clearTimeout(suggestTimer);
  const kw = keyword.value.trim();
  if (!kw) { suggestions.value = []; return; }
  suggestTimer = setTimeout(async () => {
    try {
      // 必须 unwrap：http.get 返回 { code, message, data }，直接当数组赋值会让 v-for
      // 遍历 code/message/data 三个键，渲染出 3 个空选项（点击后 keyword 变成 undefined）
      const r = await unwrap(productApi.suggest(kw, 10));
      suggestions.value = kw === keyword.value.trim() ? (r ?? []) : [];
    } catch {
      suggestions.value = [];
    }
  }, 250);
}

function clickSuggestion(item: SuggestItem): void {
  dropdownOpen.value = false;
  rememberSearch(item.name);
  const query: Record<string, string> = { keyword: item.name };
  if (item.categoryId != null) query.categoryId = String(item.categoryId);
  router.push({ path: '/products', query });
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

function onSearch(): void {
  const kw = keyword.value.trim();
  rememberSearch(kw);
  dropdownOpen.value = false;
  router.push({ path: '/products', query: kw ? { keyword: kw } : {} });
}

function searchWord(word: string): void {
  keyword.value = word;
  onSearch();
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

onBeforeUnmount(stopUnreadPoll);
onBeforeUnmount(() => {
  if (suggestTimer) clearTimeout(suggestTimer);
  if (blurTimer) clearTimeout(blurTimer);
});

/** 切回标签页时立刻补一次，避免「刚切回来还是旧数字」 */
function onVisible(): void {
  if (document.visibilityState === 'visible' && auth.isLoggedIn) void loadUnread();
}
window.addEventListener('visibilitychange', onVisible);
onBeforeUnmount(() => window.removeEventListener('visibilitychange', onVisible));

/** 从消息中心返回时红点要跟着变；个人中心改昵称后顶栏也跟着刷新 */
watch(() => route.fullPath, (path) => {
  if (path.startsWith('/messages')) void loadUnread();
  if (path.startsWith('/mine') || path.startsWith('/profile')) void loadAccountName();
});
watch(() => auth.isLoggedIn, () => void loadAccountName());
</script>
