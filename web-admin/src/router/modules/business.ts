/**
 * 管理端静态路由表（P6-08）。
 *
 * 设计要点：
 * - 全部路由静态声明，meta.perm 标权限码；**不采用**后端下发组件路径的动态路由
 *   （组件路径拼错只在运行时白屏，且后端被攻破会扩大攻击面，见 plan §9.4.4）。
 * - MENU_ROUTES 是路由与菜单的单一真源，避免两边漂移。
 * - 业务页尚未实现的先用 StubView 占位；后续 P6-23~ 只需把真实组件登记到 REAL_PAGES。
 */
import type { RouteRecordRaw, RouteRecordSingleView } from 'vue-router';
import Layout from '@/layout/index.vue';

export interface MenuEntry {
  path: string;
  title: string;
  perm?: string;
  /** 菜单分组；空表示一级菜单 */
  group?: string;
  icon?: string;
  /** 详情页/编辑页不在菜单显示 */
  hidden?: boolean;
}

export const LOGIN_PATH = '/login';
export const FORBIDDEN_PATH = '/error/403';
/** 路由守卫白名单（router 内部路径，不含 /admin base） */
export const WHITE_LIST = ['/login', '/error/403', '/error/404'];

export const MENU_ROUTES: MenuEntry[] = [
  { path: '/dashboard', title: '工作台', icon: '台' },

  { path: '/product/list', title: '商品列表', group: '商品', perm: 'product:read', icon: '列' },
  { path: '/product/edit', title: '商品编辑', group: '商品', perm: 'product:update', hidden: true },
  { path: '/product/category', title: '分类管理', group: '商品', perm: 'product:update', icon: '类' },
  { path: '/product/customer-price', title: '一客一价', group: '商品', perm: 'product:update', icon: '价' },

  { path: '/stock/list', title: '库存列表', group: '库存', perm: 'stock:read', icon: '存' },
  { path: '/stock/inbound', title: '手工入库', group: '库存', perm: 'stock:inbound', icon: '入' },
  { path: '/stock/loss', title: '报损', group: '库存', perm: 'stock:loss', icon: '损' },
  { path: '/stock/check', title: '盘点', group: '库存', perm: 'stock:check:approve', icon: '盘' },

  { path: '/order/list', title: '订单列表', group: '订单', perm: 'order:read', icon: '单' },
  { path: '/order/detail/:orderNo', title: '订单详情', group: '订单', perm: 'order:read', hidden: true },
  { path: '/order/shipment', title: '发货工作台', group: '订单', perm: 'order:ship', icon: '发' },

  { path: '/custom/review', title: '定制审核', group: '定制', perm: 'custom:review', icon: '审' },
  { path: '/custom/list', title: '定制列表', group: '定制', perm: 'custom:review', icon: '定' },

  { path: '/customer/list', title: '客户列表', group: '客户与会员', perm: 'customer:read', icon: '客' },
  { path: '/customer/detail/:id', title: '客户详情', group: '客户与会员', perm: 'customer:read', hidden: true },
  { path: '/customer/enterprise', title: '企业审核与授信', group: '客户与会员', perm: 'customer:audit', icon: '企' },
  { path: '/member/index', title: '会员与积分', group: '客户与会员', perm: 'member:config', icon: '会' },

  { path: '/chat/workbench', title: '客服工作台', group: '客服', perm: 'chat:serve', icon: '服' },

  { path: '/finance/refund', title: '退款审核', group: '财务', perm: 'refund:audit', icon: '退' },
  { path: '/finance/receivable', title: '应收台账', group: '财务', perm: 'finance:read', icon: '收' },
  { path: '/finance/payment-record', title: '回款登记与核销', group: '财务', perm: 'finance:payment', icon: '款' },
  { path: '/finance/invoice', title: '发票管理', group: '财务', perm: 'finance:invoice', icon: '票' },
  { path: '/finance/contract', title: '合同管理', group: '财务', perm: 'finance:contract', icon: '同' },

  { path: '/system/staff', title: '员工管理', group: '系统', perm: 'system:staff', icon: '员' },
  { path: '/system/role', title: '角色权限矩阵', group: '系统', perm: 'system:role', icon: '角' },
  { path: '/system/config', title: '业务参数', group: '系统', perm: 'system:config', icon: '参' },
  { path: '/system/log', title: '操作日志', group: '系统', perm: 'system:log', icon: '志' },

  { path: '/report/sales', title: '销售报表', group: '报表', perm: 'report:sales', icon: '销' },
  { path: '/report/stock-warning', title: '库存预警', group: '报表', perm: 'report:stock', icon: '警' },
];

/** 已落地的真实页面：P6-23~54 后 MENU_ROUTES 全部 30 条均已登记；StubView 仅作兜底 */
type RouteComponentLoader = RouteRecordSingleView['component'];
const REAL_PAGES: Record<string, RouteComponentLoader> = {
  '/dashboard': () => import('@/views/dashboard/index.vue'),

  '/product/list': () => import('@/views/product/list.vue'),
  '/product/edit': () => import('@/views/product/edit.vue'),
  '/product/category': () => import('@/views/product/category.vue'),
  '/product/customer-price': () => import('@/views/product/customer-price.vue'),

  '/stock/list': () => import('@/views/stock/list.vue'),
  '/stock/inbound': () => import('@/views/stock/inbound.vue'),
  '/stock/loss': () => import('@/views/stock/loss.vue'),
  '/stock/check': () => import('@/views/stock/check.vue'),

  '/order/list': () => import('@/views/order/list.vue'),
  '/order/detail/:orderNo': () => import('@/views/order/detail.vue'),
  '/order/shipment': () => import('@/views/order/shipment.vue'),

  '/custom/review': () => import('@/views/custom/review.vue'),
  '/custom/list': () => import('@/views/custom/list.vue'),

  '/customer/list': () => import('@/views/customer/list.vue'),
  '/customer/detail/:id': () => import('@/views/customer/detail.vue'),
  '/customer/enterprise': () => import('@/views/customer/enterprise.vue'),
  '/member/index': () => import('@/views/member/index.vue'),

  '/chat/workbench': () => import('@/views/chat/workbench.vue'),

  '/finance/refund': () => import('@/views/finance/refund.vue'),
  '/finance/receivable': () => import('@/views/finance/receivable.vue'),
  '/finance/payment-record': () => import('@/views/finance/payment-record.vue'),
  '/finance/invoice': () => import('@/views/finance/invoice.vue'),
  '/finance/contract': () => import('@/views/finance/contract.vue'),

  '/system/staff': () => import('@/views/system/staff.vue'),
  '/system/role': () => import('@/views/system/role.vue'),
  '/system/config': () => import('@/views/system/config.vue'),
  '/system/log': () => import('@/views/system/log.vue'),

  '/report/sales': () => import('@/views/report/sales.vue'),
  '/report/stock-warning': () => import('@/views/report/stock-warning.vue'),
};

const StubView: RouteComponentLoader = () => import('@/views/StubView.vue');

/** 公开路由：登录、403、404 */
export const staticRoutes: RouteRecordRaw[] = [
  {
    path: LOGIN_PATH,
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: FORBIDDEN_PATH,
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', hidden: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', hidden: true },
  },
];

/** 业务路由：统一挂在 Layout 下 */
export const businessRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: MENU_ROUTES.map((entry) => ({
      path: entry.path,
      name: `page_${entry.path.replace(/[^a-zA-Z0-9]/g, '_')}`,
      component: REAL_PAGES[entry.path] ?? StubView,
      meta: {
        title: entry.title,
        perm: entry.perm,
        group: entry.group,
        icon: entry.icon,
        hidden: entry.hidden,
      },
    })),
  },
];
