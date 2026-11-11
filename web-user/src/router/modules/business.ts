/**
 * 商城端路由表（P6-55）。
 *
 * 设计要点（对齐 web-admin P6-08，但商城无角色权限矩阵）：
 * - MENU_ROUTES 是路由与底部 Tab 的单一真源，避免两边漂移。
 * - 业务页一次性登记到 REAL_PAGES；详情页标 hidden 不进 Tab。
 * - 守卫只做登录态校验（无 perm 过滤），未登录跳登录并带回跳地址。
 */
import type { RouteRecordRaw, RouteRecordSingleView } from 'vue-router';
import Layout from '@/layout/index.vue';

export interface AppRoute {
  path: string;
  title: string;
  icon?: string;
  /** 是否出现在底部 Tab 栏 */
  tab?: boolean;
  /** 不在 Tab 显示（详情页等） */
  hidden?: boolean;
}

export const LOGIN_PATH = '/login';
/** 路由守卫白名单（不含 base） */
export const WHITE_LIST = ['/login', '/:pathMatch(.*)*'];

export const MENU_ROUTES: AppRoute[] = [
  { path: '/home', title: '首页', icon: '🏠', tab: true },
  { path: '/products', title: '商品', icon: '🛍️', tab: true },
  { path: '/cart', title: '购物车', icon: '🛒', tab: true },
  { path: '/mine', title: '我的', icon: '👤', tab: true },

  { path: '/product/:productId', title: '商品详情', hidden: true },
  { path: '/checkout', title: '确认订单', hidden: true },
  { path: '/orders', title: '我的订单', hidden: true },
  { path: '/order/:orderNo', title: '订单详情', hidden: true },
  { path: '/custom', title: '我的定制', hidden: true },
  { path: '/chat', title: '在线客服', hidden: true },
  { path: '/member', title: '会员中心', hidden: true },
  { path: '/refunds', title: '我的售后', hidden: true },
  { path: '/refund/:refundNo', title: '售后详情', hidden: true },
];

type RouteComponentLoader = RouteRecordSingleView['component'];
const REAL_PAGES: Record<string, RouteComponentLoader> = {
  '/home': () => import('@/views/home/index.vue'),
  '/products': () => import('@/views/products/index.vue'),
  '/cart': () => import('@/views/cart/index.vue'),
  '/mine': () => import('@/views/mine/index.vue'),

  '/product/:productId': () => import('@/views/product/detail.vue'),
  '/checkout': () => import('@/views/checkout/index.vue'),
  '/orders': () => import('@/views/order/list.vue'),
  '/order/:orderNo': () => import('@/views/order/detail.vue'),
  '/custom': () => import('@/views/custom/index.vue'),
  '/chat': () => import('@/views/chat/index.vue'),
  '/member': () => import('@/views/member/index.vue'),
  '/refunds': () => import('@/views/refund/list.vue'),
  '/refund/:refundNo': () => import('@/views/refund/detail.vue'),
};

const NotFound: RouteComponentLoader = () => import('@/views/error/404.vue');

/** 公开路由：登录、404 */
export const staticRoutes: RouteRecordRaw[] = [
  {
    path: LOGIN_PATH,
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: NotFound,
    meta: { title: '页面不存在', hidden: true },
  },
];

/** 业务路由：统一挂在 Layout（底部 Tab）下 */
export const businessRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: MENU_ROUTES.map((entry) => ({
      path: entry.path,
      name: `page_${entry.path.replace(/[^a-zA-Z0-9]/g, '_')}`,
      component: REAL_PAGES[entry.path] ?? NotFound,
      meta: { title: entry.title, tab: entry.tab, hidden: entry.hidden },
    })),
  },
];
