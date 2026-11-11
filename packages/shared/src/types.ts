/**
 * 前后端共享类型（P6-03）。
 * 字段与 server 侧 R / PageResult / 各模块 DTO 的 record 一一对应；
 * 后端用 Long 的金额一律是「分」，字段名带 Cent 后缀，禁止在页面里 /100（用 fmtCent）。
 *
 * 约定：列表 / 详情接口返回 R<PageResult<T>> 或 R<T>；ID 后端有 String 与 Long 两种，
 * 统一用 Id = string | number，页面只做透传展示，不参与算术。
 */

export type Id = string | number;

/** common/web/PageQuery.java：page 从 1 开始，size 上限 100 */
export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
}

/** common/web/PageResult.java：{ list, total, page, size, pages } */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
  pages: number;
}

/** auth/dto/AuthDtos.TokenVO：双令牌 + 访问令牌有效期（秒） */
export interface TokenVO {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/** 登录后 GET /api/me（admin 端为当前员工） */
export interface MeVO {
  userId: number;
  username: string;
  name: string;
  aud: 'admin' | 'user';
  roleCode: string;
  permissions: string[];
  dataScope: 'ALL' | 'TEAM' | 'SELF';
}

/** 用户端 GET /api/me：当前客户个人资料（收口阶段补齐） */
export interface UserMeVO {
  customerId: number;
  customerNo: string;
  nickname: string;
  phone: string;
  email: string;
  type: 'PERSON' | 'ENTERPRISE';
  levelCode: string;
  growthValue: number;
  pointsBalance: number;
  status: string;
}

/** 权限树节点（菜单由静态路由 + permissions[] 过滤生成，后端不下发组件路径） */
export interface MenuItem {
  path: string;
  title: string;
  icon?: string;
  perm?: string;
  children?: MenuItem[];
}

// ---------------------------------------------------------------------------
// 商品 / 库存
// ---------------------------------------------------------------------------

export interface PriceVO {
  retailPriceCent: number;
  floorPriceCent: number;
  agreementPriceCent?: number;
  ladderPriceCent?: number;
  customerPriceCent?: number;
  source: string;
}

export interface SkuVO {
  skuId: Id;
  skuCode?: string;
  spec: string;
  stockQty?: number;
  onShelf?: boolean;
  price?: PriceVO;
  retailPriceCent?: number;
}

export interface ProductVO {
  productId: Id;
  name: string;
  categoryId?: Id;
  categoryName?: string;
  onShelf: boolean;
  skus?: SkuVO[];
  skuCount?: number;
}

export interface CategoryVO {
  categoryId: Id;
  name: string;
  parentId?: Id | null;
  sort?: number;
  children?: CategoryVO[];
}

export interface CustomerPriceVO {
  id: Id;
  skuId: Id;
  customerId: Id;
  priceCent: number;
  validFromMillis?: number;
  validToMillis?: number;
  /** 以下为列表联查字段：客户名/手机号、商品名、规格编码与规格名、零售价（分） */
  customerName?: string | null;
  customerPhone?: string | null;
  productId?: Id | null;
  productName?: string | null;
  skuCode?: string | null;
  spec?: string | null;
  retailPriceCent?: number;
}

/** 库存四口径：physical / locked / pending / frozen / available / safe */
export interface StockVO {
  skuId: Id;
  skuName?: string;
  physicalQty: number;
  lockedQty: number;
  pendingQty: number;
  frozenQty: number;
  availableQty: number;
  safeQty: number;
  warning?: boolean;
}

export interface StockFlowVO {
  flowId: Id;
  bizType: string;
  bizNo: string;
  skuId: Id;
  qty: number;
  beforeQty?: number;
  afterQty?: number;
  reason?: string;
  createdAt?: number;
}

// ---------------------------------------------------------------------------
// 订单 / 支付 / 售后
// ---------------------------------------------------------------------------

export interface OrderVO {
  orderNo: string;
  status: string;
  payStatus: string;
  shipStatus?: string;
  refundStatus?: string;
  payableCent: number;
  goodsAmountCent: number;
  freightCent: number;
  pointDeductCent: number;
  customerId?: Id;
  createdAt?: number;
}

export interface AdminOrderVO {
  orderNo: string;
  status: string;
  payableCent: number;
  payableYuan?: string;
}

export interface OrderLineVO {
  lineId: Id;
  skuId: Id;
  skuName: string;
  spec?: string;
  qty: number;
  unitPriceCent: number;
  shippedQty?: number;
  customStatus?: string;
}

/** 管理端订单详情行：后端 Long 序列化为字符串，数值字段用 Id / number|string 兜住。 */
export interface AdminOrderLineVO {
  skuId: Id;
  skuName: string;
  spec?: string;
  qty: number;
  unitPriceCent: Id;
  shippedQty?: number;
  customStatus?: string;
}

/**
 * 管理端订单详情（GET /admin/orders/{orderNo}）：四维度状态 + 金额 + 下单时间 + 订单行。
 * 与用户端 OrderVO 同名字段保持一致，页面可复用 fmtMoney / fmtDateTime / StatusTag。
 */
export interface AdminOrderDetailVO extends OrderVO {
  paidCent?: Id;
  lines: AdminOrderLineVO[];
}

export interface RefundVO {
  refundNo: string;
  orderNo: string;
  status: string;
  amountCent: number;
  reason?: string;
  createdAt?: number;
}

// ---------------------------------------------------------------------------
// 客户 / 会员 / 财务
// ---------------------------------------------------------------------------

export interface CustomerVO {
  customerId: Id;
  name: string;
  phone?: string;
  type: 'PERSON' | 'ENTERPRISE';
  level?: string;
  ownerStaffId?: Id;
  creditLimitCent?: number;
  availableCent?: number;
  /** 已占用授信（分），来自 enterprise_profile.credit_used_cent */
  creditUsedCent?: number;
  /** 企业认证档案状态：ACTIVE / REJECTED / NORMAL / PENDING，个人客户为 undefined */
  enterpriseStatus?: string;
  createdAt?: number;
}

export interface MemberLevelVO {
  customerId: Id;
  level: string;
  growthValue: number;
  availablePoints?: number;
  /** 当前等级成长值门槛（后端按 NORMAL/LV1/LV2/LV3 机算） */
  currentThreshold?: number;
  /** 下一等级门槛；已是最高档时与 currentThreshold 相同 */
  nextThreshold?: number;
  /** 升级进度 0~1 */
  progress?: number;
  /** 人工授级有效期截止毫秒，0 表示无期限 */
  validToMillis?: number;
}

export interface PointVO {
  customerId: Id;
  availablePoints: number;
  beforePoints?: number;
  afterPoints?: number;
  delta?: number;
}

export interface ReceivableVO {
  receivableId: Id;
  orderNo: string;
  customerId: Id;
  amountCent: number;
  writtenOffCent: number;
  dueAt?: number;
  status: string;
  overdueDays?: number;
}

export interface PaymentRecordVO {
  paymentNo: string;
  customerId: Id;
  amountCent: number;
  serialNo?: string;
  status: string;
  createdAt?: number;
}

export interface WriteOffVO {
  paymentNo: string;
  totalWrittenOff: number;
  closedReceivableIds: Id[];
  status: string;
}

// ---------------------------------------------------------------------------
// 定制 / 客服 / 系统
// ---------------------------------------------------------------------------

export interface CustomVO {
  customOrderId: string;
  status: string;
  version: number;
  rejectCount?: number;
  reviewDeadlineMillis?: number;
  fileId?: Id;
}

export interface DraftVO {
  customOrderId: string;
  version: number;
  status: string;
  reviewDeadlineMillis: number;
}

export interface ChatSessionVO {
  sessionId: number;
  status: string;
  staffId?: number | null;
  customerId?: Id;
  waitingSinceMillis?: number;
  reused?: boolean;
}

export interface ChatMessageVO {
  messageId: string;
  sessionId: number;
  senderType: string;
  content: string;
  clientMsgId?: string;
  createdAt?: number;
}

export interface StaffVO {
  staffId: Id;
  username: string;
  name: string;
  roleCode: string;
  enabled?: boolean;
}

export interface RoleVO {
  roleCode: string;
  roleName: string;
  permCodes: string[];
}

export interface SalesReportVO {
  date: string;
  amountCent: number;
  orderCount: number;
}

/** POST /api/admin/confirm-token 的响应 */
export interface ConfirmTokenVO {
  token: string;
  expireAt: number;
}
