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
import MineLayout from '@/layout/mine.vue';

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
export const WHITE_LIST = [
  '/login', '/register', '/forgot-password', '/enterprise/login', '/enterprise/register',
  '/enterprise/audit-progress', '/:pathMatch(.*)*',
];

export const MENU_ROUTES: AppRoute[] = [
  { path: '/home', title: '首页', icon: '🏠', tab: true },
  { path: '/products', title: '商品', icon: '🛍️', tab: true },
  { path: '/cart', title: '购物车', icon: '🛒', tab: true },
  { path: '/mine', title: '我的', icon: '👤', tab: true },

  { path: '/product/:productId', title: '商品详情', hidden: true },
  { path: '/checkout', title: '确认订单', hidden: true },
  { path: '/orders', title: '我的订单', hidden: true },
  { path: '/order/:orderNo', title: '订单详情', hidden: true },
  { path: '/pay/:orderNo', title: '订单支付', hidden: true },
  { path: '/favorites', title: '我的收藏', hidden: true },
  { path: '/footprints', title: '我的足迹', hidden: true },
  { path: '/custom', title: '我的定制', hidden: true },
  { path: '/custom/:customOrderId', title: '定制单详情', hidden: true },
  { path: '/group-buy', title: '团购专区', hidden: true },
  { path: '/chat', title: '在线客服', hidden: true },
  { path: '/messages', title: '消息中心', hidden: true },
  { path: '/member', title: '会员中心', hidden: true },
  { path: '/coupons', title: '我的优惠券', hidden: true },
  { path: '/coupon/center', title: '领券中心', hidden: true },
  { path: '/profile/edit', title: '编辑个人信息', hidden: true },
  { path: '/refunds', title: '我的售后', hidden: true },
  { path: '/refund/:refundNo', title: '售后详情', hidden: true },
  { path: '/refund/apply/:orderNo', title: '申请退款', hidden: true },
  { path: '/help/category/:category', title: '帮助中心分类', hidden: true },
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
  '/pay/:orderNo': () => import('@/views/pay/index.vue'),
  '/favorites': () => import('@/views/favorites/index.vue'),
  '/footprints': () => import('@/views/footprints/index.vue'),
  '/custom': () => import('@/views/custom/index.vue'),
  '/custom/:customOrderId': () => import('@/views/custom/detail.vue'),
  '/group-buy': () => import('@/views/group-buy/index.vue'),
  '/chat': () => import('@/views/chat/index.vue'),
  '/messages': () => import('@/views/message/index.vue'),
  '/member': () => import('@/views/member/index.vue'),
  '/coupons': () => import('@/views/coupon/index.vue'),
  '/coupon/center': () => import('@/views/coupon/center.vue'),
  '/profile/edit': () => import('@/views/mine/profile-edit.vue'),
  '/refunds': () => import('@/views/refund/list.vue'),
  '/refund/:refundNo': () => import('@/views/refund/detail.vue'),
  '/refund/apply/:orderNo': () => import('@/views/refund/apply.vue'),
  '/help/category/:category': () => import('@/views/help/category.vue'),
};

const NotFound: RouteComponentLoader = () => import('@/views/error/404.vue');

/** 公开路由：登录、注册（复用登录卡片）、404 */
export const staticRoutes: RouteRecordRaw[] = [
  {
    path: LOGIN_PATH,
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    // 未注册手机号被拦截后跳到这里，query.phone 自动回填注册表单
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '注册', hidden: true },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/login/forgot.vue'),
    meta: { title: '忘记密码', hidden: true },
  },
  {
    path: '/enterprise/login',
    name: 'EnterpriseLogin',
    component: () => import('@/views/enterprise/login.vue'),
    meta: { title: '企业登录', hidden: true },
  },
  {
    path: '/enterprise/register',
    name: 'EnterpriseRegister',
    component: () => import('@/views/enterprise/register.vue'),
    meta: { title: '企业注册', hidden: true },
  },
  {
    path: '/enterprise/audit-progress',
    name: 'EnterpriseAuditProgress',
    component: () => import('@/views/enterprise/audit-progress.vue'),
    meta: { title: '企业审核进度', hidden: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: NotFound,
    meta: { title: '页面不存在', hidden: true },
  },
];

/**
 * 个人中心簇（2026-11-27）：这些路径全部渲染在 MineLayout 外壳内，
 * URL 保持不变（`/cart` 还是 `/cart`），只是不再套站点大版式。
 *
 * 判定用前缀：`/refund` 同时覆盖 `/refunds`、`/refund/:refundNo`、`/refund/apply/:orderNo`；
 * `/order/` 只覆盖订单详情，`/orders` 单独列出（避免前缀误伤）；
 * 2026-11-28 起 `/group-buy` 也走个人中心外壳，团购专区页保留侧边栏。
 */
const MINE_PREFIXES = ['/mine', '/cart', '/orders', '/order/', '/custom', '/coupons', '/footprints', '/favorites', '/refund', '/profile/', '/member', '/group-buy'];
function isMinePath(path: string): boolean {
  return MINE_PREFIXES.some((p) => path === p || path.startsWith(p));
}

/** 业务路由：站点版式（浏览/购买流程）与个人中心版式（我的 xx）两组 */
export const businessRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    // 个人中心簇已挪到下方 MineLayout，这里只留商品浏览、下单支付、客服、帮助等站点页
    children: MENU_ROUTES.filter((entry) => !isMinePath(entry.path)).map((entry) => ({
      path: entry.path,
      name: `page_${entry.path.replace(/[^a-zA-Z0-9]/g, '_')}`,
      component: REAL_PAGES[entry.path] ?? NotFound,
      meta: { title: entry.title, tab: entry.tab, hidden: entry.hidden },
    })),
  },
  {
    // 绝对子路径（vue-router 会把以 / 开头的子路径当根路径处理），
    // 因此 `/cart` `/orders` 等既有 URL 与 `location.href`、E2E 断言完全不变。
    path: '/mine',
    component: MineLayout,
    children: [
      ...MENU_ROUTES.filter((entry) => isMinePath(entry.path)).map((entry) => ({
        path: entry.path,
        name: `mine_${entry.path.replace(/[^a-zA-Z0-9]/g, '_')}`,
        component: REAL_PAGES[entry.path] ?? NotFound,
        // `/mine` 在菜单表里叫「我的」，个人中心标题行要显示「我的森特」
        meta: { title: entry.path === '/mine' ? '我的森特' : entry.title, hidden: entry.hidden },
      })),
      {
        path: '/mine/invoices',
        name: 'MineInvoices',
        component: () => import('@/views/mine/invoices.vue'),
        meta: { title: '我的发票' },
      },
      {
        // 评价中心（2026-11-28）：订单中心 → 评价中心；「我的森特」的待评价也落到这里
        path: '/mine/reviews',
        name: 'MineReviews',
        component: () => import('@/views/mine/reviews.vue'),
        meta: { title: '评价中心' },
      },
      {
        path: '/mine/enterprise/procurement',
        name: 'MineEnterpriseProcurement',
        component: () => import('@/views/mine/enterprise.vue'),
        meta: { title: '企业采购', tool: 'procurement' },
      },
      {
        path: '/mine/enterprise/credit',
        name: 'MineEnterpriseCredit',
        component: () => import('@/views/mine/enterprise.vue'),
        meta: { title: '我的授信', tool: 'credit' },
      },
      {
        path: '/mine/enterprise/pay-later',
        name: 'MineEnterprisePayLater',
        component: () => import('@/views/mine/enterprise.vue'),
        meta: { title: '先采后付', tool: 'pay-later' },
      },
      {
        path: '/mine/enterprise/contracts',
        name: 'MineEnterpriseContracts',
        component: () => import('@/views/mine/enterprise.vue'),
        meta: { title: '我的合同', tool: 'contracts' },
      },
    ],
  },
];
