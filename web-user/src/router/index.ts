/**
 * 商城端路由与登录守卫（P6-55）。
 * base 取 VITE_PUBLIC_PATH（默认 /），与 .env.development 一致。
 */
import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router';
import { businessRoutes, staticRoutes, WHITE_LIST, LOGIN_PATH } from './modules/business';
import { useAuthStore } from '@/store/modules/useAuthStore';

const base = (import.meta.env.VITE_PUBLIC_PATH || '/').replace(/\/?$/, '/');

const router = createRouter({
  history: createWebHistory(base),
  routes: [...staticRoutes, ...businessRoutes],
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach((to) => {
  const auth = useAuthStore();

  // 已登录用户不应停留在登录页
  if (to.path === LOGIN_PATH) {
    return auth.isLoggedIn ? { path: '/home' } : true;
  }
  if (WHITE_LIST.includes(to.path)) return true;

  // 无 token：跳登录并带回跳地址（base 之外写相对路径即可）
  if (!auth.isLoggedIn) {
    return { path: LOGIN_PATH, query: { redirect: to.fullPath } };
  }
  return true;
});

/**
 * 浏览器标签标题（2026-11-26 用户要求）：随页面变化，一眼能看出在哪个页面。
 *
 * - 搜索：`森特体育 · 哑铃`（关键词优先于「商品」这个泛标题）；
 * - 其余页面：`森特体育 · <路由标题>`（购物车 / 我的订单 / 会员中心 …）；
 * - 商品详情页先落「商品详情」，组件拿到商品名后再覆盖成真实商品名（见 product/detail.vue）。
 *
 * 同路由 query 变化（搜索词改一个词、商品列表筛选）也会触发 afterEach，标题跟着更新。
 */
const BRAND = '森特体育';

function titleOf(to: RouteLocationNormalized): string {
  const keyword = typeof to.query.keyword === 'string' ? to.query.keyword.trim() : '';
  if (to.path === '/products' && keyword) return keyword;
  return (to.meta.title as string | undefined) || '';
}

router.afterEach((to) => {
  const page = titleOf(to);
  document.title = page ? `${BRAND} · ${page}` : BRAND;
});

export default router;
