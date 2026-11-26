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

/** 员工列表查询条件：关键词一次覆盖员工号 / 登录名 / 姓名 / 手机号。 */
export interface StaffQuery extends PageQuery {
  keyword?: string;
  /** true=只看启用中、false=只看已停用、不传=不限 */
  enabled?: boolean;
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

/**
 * 系统配置中心行（2026-11-25，对应后端 ConfigVO）。
 *
 * 中文名 / 说明 / 单位 / 类型全部由后端字典（SystemConfigCatalog）下发：
 * 前端不许再维护一份映射，否则后端改了说明、页面还是老文案，运营会照着错的说明填值。
 */
export interface ConfigVO {
  /** 原始键名，列里降级为小字灰底展示 */
  configKey: string;
  /** 中文名（字典没登记的键回落为键名） */
  configName: string;
  /** 一句话说明「改这个值会发生什么」，编辑弹窗必须展示 */
  description: string;
  /** 业务分组：订单与交易 / 库存与商品 / 财务与开票 / 会员与积分 / 其它 */
  groupName: string;
  /** 原始值，弹窗回填与提交都用它 */
  configValue: string;
  /** 展示值：30 分钟 / ¥50,000.00 / 开启 */
  displayValue: string;
  unit: string;
  /** NUMBER / MONEY_CENT / PERCENT / BOOLEAN / TEXT，决定弹窗给什么控件 */
  valueType: string;
  remark: string;
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

/* ---------------- 拓展报表（2026-11-23） ---------------- */

/** 客户采购结构：企业 / 个人 / 未分类三类固定齐全，空区间给 0 值。 */
export interface CustomerMixItemVO {
  type: 'ENTERPRISE' | 'PERSON' | 'UNKNOWN';
  label: string;
  customerCount: number;
  orderCount: number;
  amountCent: number | string;
  amountYuan: string;
  sharePct: string;
}
export interface CustomerMixVO {
  fromDate: string;
  toDate: string;
  days: number;
  totalAmountCent: number | string;
  totalAmountYuan: string;
  items: CustomerMixItemVO[];
}

/** 商品销售明细：汇总口径恒为全量，items 为 limit 截断后的列表。 */
export interface ProductDetailItemVO {
  rank: number;
  skuId: number | string;
  skuName: string;
  spec: string;
  qty: number | string;
  amountCent: number | string;
  amountYuan: string;
  profitCent: number | string;
  profitYuan: string;
  grossMarginPct: string;
  priceSources: string;
  stockQty: number;
  orderCount: number;
}
export interface ProductDetailVO {
  fromDate: string;
  toDate: string;
  days: number;
  limit: number;
  totalAmountCent: number | string;
  totalAmountYuan: string;
  totalQty: number | string;
  totalProfitCent: number | string;
  grossMarginPct: string;
  itemCount: number;
  items: ProductDetailItemVO[];
}

/** 复购频次档位：复购客按历史总订单数分 2 单 / 3–5 单 / 6 单以上。 */
export interface FrequencyBucketVO {
  label: string;
  customerCount: number | string;
  amountCent: number | string;
  amountYuan: string;
  sharePct: string;
}

/** 复购 / 新老客结构：以区间前是否已有成交区分。 */
export interface RepeatCustomerVO {
  fromDate: string;
  toDate: string;
  days: number;
  totalCustomerCount: number | string;
  newCustomerCount: number | string;
  repeatCustomerCount: number | string;
  repeatRatePct: string;
  newAmountCent: number | string;
  newAmountYuan: string;
  repeatAmountCent: number | string;
  repeatAmountYuan: string;
  avgOrderCount: string;
  frequencyBuckets: FrequencyBucketVO[];
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
  staffList: (query: StaffQuery) => http.get<PageResult<StaffVO>>('/admin/staff', query),
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

  /* 系统配置中心（2026-11-25）：在键值对之上补中文名 / 说明 / 单位 / 展示值 */
  configs: () => http.get<ConfigVO[]>('/admin/configs'),
  /** 单项更新：键名走路径，值是纯字符串，类型校验由后端字典负责 */
  updateConfigValue: (configKey: string, configValue: string) =>
    http.put<ConfigVO>(`/admin/configs/${encodeURIComponent(configKey)}`, { configValue }),

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
  /** 客户采购结构（企业 / 个人 / 未分类），数据范围由后端 @DataScope 下推。 */
  customerMix: (days = 30) => http.get<CustomerMixVO>('/admin/reports/customer-mix', { days }),
  /** 商品销售明细：销量 / 金额 / 价格来源 / 毛利 / 可用库存。 */
  productDetails: (days = 30, limit = 10) =>
    http.get<ProductDetailVO>('/admin/reports/product-details', { days, limit }),
  /** 复购 / 新老客结构。 */
  repeatCustomers: (days = 30) =>
    http.get<RepeatCustomerVO>('/admin/reports/repeat-customers', { days }),
  /** 工作台六卡与待办数量，一次请求取齐。 */
  dashboard: () => http.get<DashboardSummaryVO>('/admin/reports/dashboard'),
  stockWarningReport: () => http.get<StockWarningVO>('/admin/reports/stock-warning'),
};
