/**
 * 客服端路由与权限守卫。
 *
 * 只有两条路由：登录页 + 工作台（布局内嵌 workbench）。刻意不做懒加载分包的
 * 花活，但保留 `() => import()`——客服台首屏只有工作台，登录页拆出去反而更快。
 *
 * 守卫口径（与 web-admin 的差异）：
 * - 未登录 → /login（带 redirect 回跳）。
 * - 已登录但拉不到权限 / 不是客服 → 清 Token 后回 /login?reason=forbidden，
 *   由登录页显示红字「权限不足，您不是客服人员」。绝不「先放行、后端兜底」。
 */
import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { usePermissionStore } from '@/store/modules/permission';
import { CS_HOME_PATH } from '@/utils/csAccess';

export const LOGIN_ROUTE = '/login';

const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_PUBLIC_PATH || '/'),
  routes: [
    {
      path: LOGIN_ROUTE,
      name: 'cs-login',
      component: () => import('@/views/login/index.vue'),
      meta: { public: true, title: '客服登录' },
    },
    {
      path: '/',
      component: () => import('@/layout/index.vue'),
      redirect: CS_HOME_PATH,
      children: [
        {
          path: 'workbench',
          name: 'cs-workbench',
          component: () => import('@/views/chat/workbench.vue'),
          meta: { title: '客服工作台', perm: 'chat:serve' },
        },
      ],
    },
    // 未匹配一律回工作台：客服不需要 404 页，多一个页面就多一次误点机会
    { path: '/:pathMatch(.*)*', redirect: CS_HOME_PATH },
  ],
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();

  // 已登录用户不该停留在登录页（但带 reason=forbidden 的除外：那是被踢回来的）
  if (to.path === LOGIN_ROUTE) {
    return auth.accessToken && !to.query.reason ? { path: CS_HOME_PATH } : true;
  }

  if (!auth.accessToken) {
    return { path: LOGIN_ROUTE, query: { redirect: to.fullPath } };
  }

  const permission = usePermissionStore();
  if (!permission.loaded) {
    try {
      await permission.load();
    } catch {
      // 令牌失效 / 后端不可用：清干净，回登录页重新走一遍
      auth.resetSession();
      return { path: LOGIN_ROUTE, query: { reason: 'expired', redirect: to.fullPath } };
    }
  }

  if (!permission.isAgent) {
    auth.resetSession();
    return { path: LOGIN_ROUTE, query: { reason: 'forbidden' } };
  }

  return true;
});

export default router;
