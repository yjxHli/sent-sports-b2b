/**
 * 系统与报表 API（P6-21，对应 AdminSystemController）。
 * 报表数据用于工作台六个指标卡与菜单角标；菜单本身仍由静态路由表 + permissions[] 生成。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult, StaffVO, RoleVO, SalesReportVO } from '@sent/shared';

export interface StaffForm {
  username: string;
  name: string;
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

export interface SalesRankVO {
  rank: number;
  name: string;
  amountCent: number;
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
  logs: (query: LogQuery) => http.get<PageResult<LogVO>>('/admin/logs', query),

  salesReport: () => http.get<SalesReportVO>('/admin/reports/sales'),
  /** 销售趋势（真查 orders.paid_at + paid_cent，剔除全额退款订单）。 */
  salesTrend: (days = 30) => http.get<SalesTrendVO>('/admin/reports/sales-trend', { days }),
  salesRankReport: () => http.get<SalesRankVO>('/admin/reports/sales-rank'),
  stockWarningReport: () => http.get<StockWarningVO>('/admin/reports/stock-warning'),
};
