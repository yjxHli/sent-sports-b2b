/**
 * 商城端路由与登录守卫（P6-55）。
 * base 取 VITE_PUBLIC_PATH（默认 /），与 .env.development 一致。
 */
import { createRouter, createWebHistory } from 'vue-router';
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

export default router;
