/**
 * 管理端路由与权限守卫（P6-09）。
 * base 取 VITE_PUBLIC_PATH（/admin/），与 .env.development 及 Nginx try_files 配置一致。
 */
import { createRouter, createWebHistory } from 'vue-router';
import {
  businessRoutes,
  staticRoutes,
  WHITE_LIST,
  LOGIN_PATH,
  FORBIDDEN_PATH,
} from './modules/business';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { usePermissionStore } from '@/store/modules/permission';

const base = (import.meta.env.VITE_PUBLIC_PATH || '/admin/').replace(/\/?$/, '/');
const basePrefix = base.replace(/\/$/, '');

const router = createRouter({
  history: createWebHistory(base),
  routes: [...staticRoutes, ...businessRoutes],
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach(async (to) => {
  // 兼容历史脏地址（旧书签 / 早期 401 回跳）把 /admin 拼了两次的情况：
  // 浏览器是 /admin/admin/product/list，router 内部路径是 /admin/product/list，
  // 剥掉多余前缀后再导航，避免直接落到 404 页。
  if (basePrefix && to.path.startsWith(`${basePrefix}/`)) {
    return { path: to.path.slice(basePrefix.length), query: to.query, hash: to.hash };
  }

  const auth = useAuthStore();

  // 已登录用户不应停留在登录页
  if (to.path === LOGIN_PATH) {
    return auth.accessToken ? { path: '/dashboard' } : true;
  }
  if (WHITE_LIST.includes(to.path)) return true;

  // 无 token：跳登录并带回跳地址
  if (!auth.accessToken) {
    return { path: LOGIN_PATH, query: { redirect: to.fullPath } };
  }

  // 有 token：加载权限后按 meta.perm 过滤；后端仍会二次校验
  const permission = usePermissionStore();
  await permission.load();
  if (to.meta.perm && !permission.has(to.meta.perm)) {
    return { path: FORBIDDEN_PATH, query: { from: to.fullPath } };
  }
  return true;
});

export default router;
