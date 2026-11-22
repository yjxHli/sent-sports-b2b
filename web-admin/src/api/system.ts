/**
 * 系统与报表 API（P6-21，对应 AdminSystemController）。
 * 报表数据用于工作台六个指标卡与菜单角标；菜单本身仍由静态路由表 + permissions[] 生成。
 */
import { http, type SentRequestConfig } from '@/utils/http';
import type { PageQuery, PageResult, StaffVO, RoleVO, SalesReportVO } from '@sent/shared';

export interface StaffForm {
  username: string;
  name: string;
  /** 手机号：sys_staff.phone 为 NOT NULL + 唯一键，新增必填 */
  phone?: string;
  roleCode?: string;
}

export interface RolePermForm {
  roleCode: string;
  permCodes: string[];
}

export interface ParamVO {
  paramKey: string;
  paramValue: string;
}

export interface ParamForm {
  paramKey: string;
  paramValue: string;
}

/* ---------------- 违禁词库（V46，系统管理 → 违禁词库） ---------------- */

/** 词条分类：与后端 sys_sensitive_word.category 一一对应，label 由后端下发。 */
export interface SensitiveWordVO {
  id: number;
  word: string;
  category: string;
  categoryLabel: string;
  enabled: boolean;
  remark: string;
  createdAtMillis: number;
  updatedAtMillis: number;
}

export interface SensitiveWordForm {
  word: string;
  category?: string;
  enabled?: boolean;
  remark?: string;
}

export interface SensitiveWordQuery extends PageQuery {
  keyword?: string;
  category?: string;
  /** 1 只看启用、0 只看停用、不传表示不限 */
  enabled?: number;
}

export interface SensitiveWordStatsVO {
  total: number;
  enabled: number;
  disabled: number;
}

export interface LogVO {
  logId: number;
  operator: string;
  action: string;
  timestampMillis: number;
  module: string;
  targetType: string;
  targetId: string;
  reason: string;
  ip: string;
  traceId: string;
  beforeJson?: string | null;
  afterJson?: string | null;
}

/** 操作日志查询条件：筛选在服务端 SQL 生效，不再由前端做展示层过滤。 */
export interface LogQuery extends PageQuery {
  module?: string;
  action?: string;
  staffId?: number;
  targetType?: string;
  targetId?: string;
  keyword?: string;
}

/** 销售业绩排行行（GET /admin/reports/sales-ranking，按归属销售聚合）。 */
export interface SalesRankVO {
  rank: number;
  staffId?: number;
  name: string;
  customerCount?: number;
  orderCount?: number;
  amountCent: number;
}

/** 热销商品行（GET /admin/reports/hot-products，按 sku_id 聚合）。 */
export interface HotProductItemVO {
  rank: number;
  /** Long 经 JacksonConfig 序列化为字符串后用 Number() 归一 */
  skuId: number | string;
  skuName: string;
  spec: string;
  qty: number | string;
  amountCent: number | string;
  amountYuan: string;
  orderCount: number;
  /** 占区间总成交金额的百分比，保留 1 位小数 */
  sharePct: string;
}

/** 热销商品报表（总额/件数/SKU 数恒为区间全量口径，不随 limit 变）。 */
export interface HotProductVO {
  fromDate: string;
  toDate: string;
  days: number;
  limit: number;
  totalAmountCent: number | string;
  totalAmountYuan: string;
  totalQty: number | string;
  skuCount: number;
  items: HotProductItemVO[];
}

/** 大客户采购排行行（GET /admin/reports/top-customers，按 customer_id 聚合）。 */
export interface TopCustomerItemVO {
  rank: number;
  customerId: number | string;
  customerName: string;
  /** 明文手机号，展示层脱敏 */
  phone: string;
  orderCount: number;
  amountCent: number | string;
  amountYuan: string;
  sharePct: string;
}

/** 大客户采购排行（总额/客户数/订单数恒为区间全量口径）。 */
export interface TopCustomerVO {
  fromDate: string;
  toDate: string;
  days: number;
  limit: number;
  totalAmountCent: number | string;
  totalAmountYuan: string;
  totalOrderCount: number | string;
  customerCount: number;
  items: TopCustomerItemVO[];
}

/** 工作台六卡 + 角色化待办数量（GET /admin/reports/dashboard，全部真库聚合）。 */
export interface DashboardSummaryVO {
  date: string;
  todayOrderCount: number;
  todayAmountCent: number;
  pendingShipCount: number;
  pendingCustomReviewCount: number;
  pendingChatCount: number;
  warningSkuCount: number;
  pendingRefundCount: number;
  overdueReceivableCount: number;
}

/** 销售趋势单日点（GET /admin/reports/sales-trend，批 4）。 */
export interface SalesTrendPointVO {
  date: string;
  amountCent: number | string;
  amountYuan: string;
  orderCount: number;
  avgOrderCent: number | string;
}

/** 近 N 天销售趋势（以 orders.paid_at 为时间基准，SUM(paid_cent) 聚合）。 */
export interface SalesTrendVO {
  fromDate: string;
  toDate: string;
  days: number;
  totalAmountCent: number | string;
  totalAmountYuan: string;
  totalOrderCount: number;
  avgOrderCent: number | string;
  avgOrderYuan: string;
  points: SalesTrendPointVO[];
}

export interface StockWarningVO {
  skuId: number;
  skuName: string;
  availableQty: number;
  threshold: number;
}

export const systemApi = {
  staffList: (query: PageQuery) => http.get<PageResult<StaffVO>>('/admin/staff', query),
  createStaff: (form: StaffForm) => http.post<StaffVO>('/admin/staff', form),
  updateStaff: (id: number, form: StaffForm) => http.put<StaffVO>(`/admin/staff/${id}`, form),
  deleteStaff: (id: number) => http.delete<void>(`/admin/staff/${id}`),
  /** 强制全端下线：plan §8.13 已定义，后端 P4 待补；危险操作带 X-Confirm-Token */
  logoutAll: (id: number, confirmToken: string) => http.post<void>(
    `/admin/staff/${id}/logout-all`, {},
    { headers: { 'X-Confirm-Token': confirmToken } },
  ),

  roles: () => http.get<RoleVO[]>('/admin/roles'),
  updateRolePerms: (id: string, form: RolePermForm) =>
    http.put<RoleVO>(`/admin/roles/${id}/permissions`, form),

  params: () => http.get<ParamVO[]>('/admin/params'),
  upsertParam: (form: ParamForm) => http.put<ParamVO>('/admin/params', form),

  /* 违禁词库（V46）：保存即热生效，作用于聊天消息 + 昵称/登录名 */
  sensitiveWords: (query: SensitiveWordQuery) =>
    http.get<PageResult<SensitiveWordVO>>('/admin/sensitive-words', query),
  sensitiveWordStats: () => http.get<SensitiveWordStatsVO>('/admin/sensitive-words/stats'),
  createSensitiveWord: (form: SensitiveWordForm) =>
    http.post<SensitiveWordVO>('/admin/sensitive-words', form),
  updateSensitiveWord: (id: number, form: SensitiveWordForm) =>
    http.put<SensitiveWordVO>(`/admin/sensitive-words/${id}`, form),
  toggleSensitiveWord: (id: number, enabled: boolean) =>
    http.put<SensitiveWordVO>(`/admin/sensitive-words/${id}/enabled`, { enabled }),
  deleteSensitiveWord: (id: number) =>
    http.delete<void>(`/admin/sensitive-words/${id}`),
  logs: (query: LogQuery) => http.get<PageResult<LogVO>>('/admin/logs', query),
  batchDeleteLogs: (ids: string[], config?: SentRequestConfig) =>
    http.post<{ deleted: number }>('/admin/logs/batch-delete', { ids }, config),

  salesReport: () => http.get<SalesReportVO>('/admin/reports/sales'),
  /** 销售趋势（真查 orders.paid_at + paid_cent，剔除全额退款订单）。 */
  salesTrend: (days = 30) => http.get<SalesTrendVO>('/admin/reports/sales-trend', { days }),
  /** 销售业绩排行：多行真实数据，统计区间由后端 SQL 下推。 */
  salesRanking: (days = 30) => http.get<SalesRankVO[]>('/admin/reports/sales-ranking', { days }),
  /**
   * 热销商品：按 sku_id 聚合成交数量与金额，数据范围由后端 @DataScope 下推
   * （主管看团队、财务/超管看全部）。
   */
  hotProducts: (days = 30, limit = 5) =>
    http.get<HotProductVO>('/admin/reports/hot-products', { days, limit }),
  /**
   * 大客户采购排行：按 customer_id 聚合实付金额，数据范围同样由后端 @DataScope 下推。
   */
  topCustomers: (days = 30, limit = 5) =>
    http.get<TopCustomerVO>('/admin/reports/top-customers', { days, limit }),
  /** 工作台六卡与待办数量，一次请求取齐。 */
  dashboard: () => http.get<DashboardSummaryVO>('/admin/reports/dashboard'),
  stockWarningReport: () => http.get<StockWarningVO>('/admin/reports/stock-warning'),
};
