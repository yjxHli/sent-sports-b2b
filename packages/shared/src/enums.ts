/**
 * 前后端同源状态枚举（P6-01）。
 *
 * 一一对应 server/src/main/java/com/sent/sports/**\/domain/*Status.java，
 * 修改后端枚举时必须同步本文件（后续由生成脚本从 Java 枚举产出，禁止手抄漂移）。
 * 采用 const 对象 + 同名联合类型，而非 TS enum：可 tree-shaking、能直接 JSON 序列化给后端。
 */

/** 订单主状态（order/domain/OrderStatus.java） */
export const OrderStatus = {
  WAIT_PAY: 'WAIT_PAY',
  WAIT_SHIP: 'WAIT_SHIP',
  PART_SHIPPED: 'PART_SHIPPED',
  WAIT_RECEIVE: 'WAIT_RECEIVE',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  CLOSED: 'CLOSED',
} as const;
export type OrderStatus = (typeof OrderStatus)[keyof typeof OrderStatus];

/** 订单支付状态（order/domain/PayStatus.java） */
export const PayStatus = {
  UNPAID: 'UNPAID',
  PAYING: 'PAYING',
  PAID: 'PAID',
  PART_REFUNDED: 'PART_REFUNDED',
  FULL_REFUNDED: 'FULL_REFUNDED',
} as const;
export type PayStatus = (typeof PayStatus)[keyof typeof PayStatus];

/** 支付流水状态（payment/domain/PaymentTxnStatus.java） */
export const PaymentTxnStatus = {
  CREATED: 'CREATED',
  PAID: 'PAID',
  FAILED: 'FAILED',
  CLOSED: 'CLOSED',
  REFUNDED: 'REFUNDED',
} as const;
export type PaymentTxnStatus = (typeof PaymentTxnStatus)[keyof typeof PaymentTxnStatus];

/** 定制状态（customize/domain/CustomStatus.java） */
export const CustomStatus = {
  NONE: 'NONE',
  WAIT_DRAFT: 'WAIT_DRAFT',
  WAIT_REVIEW: 'WAIT_REVIEW',
  REJECTED: 'REJECTED',
  APPROVED: 'APPROVED',
  IN_PRODUCTION: 'IN_PRODUCTION',
  FINISHED: 'FINISHED',
  SHIPPED: 'SHIPPED',
  CANCELLED: 'CANCELLED',
  ESCALATED: 'ESCALATED',
} as const;
export type CustomStatus = (typeof CustomStatus)[keyof typeof CustomStatus];

/** 售后/退款状态（refund/domain/RefundStatus.java） */
export const RefundStatus = {
  PENDING: 'PENDING',
  REFUNDING: 'REFUNDING',
  REFUNDED: 'REFUNDED',
  FAILED: 'FAILED',
  CLOSED: 'CLOSED',
} as const;
export type RefundStatus = (typeof RefundStatus)[keyof typeof RefundStatus];

/** 应收状态（finance/domain/ReceivableStatus.java） */
export const ReceivableStatus = {
  NORMAL: 'NORMAL',
  OVERDUE: 'OVERDUE',
  CLOSED: 'CLOSED',
} as const;
export type ReceivableStatus = (typeof ReceivableStatus)[keyof typeof ReceivableStatus];

/** 客服会话状态 / 在线状态 / 发送方 / 客户类型（辅助枚举） */
export const ChatSessionStatus = { WAITING: 'WAITING', ACTIVE: 'ACTIVE', CLOSED: 'CLOSED' } as const;
export type ChatSessionStatus = (typeof ChatSessionStatus)[keyof typeof ChatSessionStatus];

export const PresenceStatus = { ONLINE: 'ONLINE', BUSY: 'BUSY', OFFLINE: 'OFFLINE' } as const;
export type PresenceStatus = (typeof PresenceStatus)[keyof typeof PresenceStatus];

export const SenderType = { CUSTOMER: 'CUSTOMER', STAFF: 'STAFF', SYSTEM: 'SYSTEM' } as const;
export type SenderType = (typeof SenderType)[keyof typeof SenderType];

export const CustomerType = { PERSON: 'PERSON', ENTERPRISE: 'ENTERPRISE' } as const;
export type CustomerType = (typeof CustomerType)[keyof typeof CustomerType];

/** 数据范围（common/mybatis/DataScopeContext.Scope） */
export const DataScopeLevel = { ALL: 'ALL', TEAM: 'TEAM', SELF: 'SELF' } as const;
export type DataScopeLevel = (typeof DataScopeLevel)[keyof typeof DataScopeLevel];

/** 库存流水类型（stock_flow.biz_type） */
export const StockFlowType = {
  LOCK: 'LOCK',
  RELEASE: 'RELEASE',
  INBOUND: 'INBOUND',
  LOSS: 'LOSS',
  CHECK: 'CHECK',
  ADJUST: 'ADJUST',
} as const;
export type StockFlowType = (typeof StockFlowType)[keyof typeof StockFlowType];

// ---------------------------------------------------------------------------
// 展示层映射：中文文案 + 语义色（StatusTag 统一消费，页面禁止自己 if/else 配色）
// ---------------------------------------------------------------------------

/** ElementPlus 语义色 */
export type StatusTone = 'primary' | 'success' | 'warning' | 'danger' | 'info';

export const STATUS_LABELS: Record<string, string> = {
  // 订单
  WAIT_PAY: '待付款', WAIT_SHIP: '待发货', PART_SHIPPED: '部分发货',
  WAIT_RECEIVE: '待收货', COMPLETED: '已完成', CANCELLED: '已取消', CLOSED: '已关闭',
  // 支付
  UNPAID: '未支付', PAYING: '支付中', PAID: '已支付',
  PART_REFUNDED: '部分退款', FULL_REFUNDED: '全额退款',
  // 支付流水
  CREATED: '待支付', FAILED: '支付失败', REFUNDED: '已退款',
  // 定制
  NONE: '无定制', WAIT_DRAFT: '待提交图稿', WAIT_REVIEW: '待审核',
  REJECTED: '审核驳回', APPROVED: '审核通过', IN_PRODUCTION: '生产中',
  FINISHED: '已完工', SHIPPED: '已发货', ESCALATED: '待主管处理',
  // 售后
  PENDING: '待审核', REFUNDING: '退款中',
  // 应收
  NORMAL: '正常', OVERDUE: '逾期',
  // 客服
  WAITING: '待接入', ACTIVE: '进行中', ONLINE: '在线', BUSY: '忙碌', OFFLINE: '离线',
  CUSTOMER: '客户', STAFF: '客服', SYSTEM: '系统',
  // 客户
  PERSON: '个人', ENTERPRISE: '企业',
  // 发票 / 合同（收口阶段管理端列表）
  APPLIED: '待开票', ISSUED: '已开票', EXPIRED: '已到期', SOON: '即将到期',
  TERMINATED: '已终止',
};

const TONE_MAP: Record<string, StatusTone> = {
  COMPLETED: 'success', PAID: 'success', APPROVED: 'success', FINISHED: 'success',
  REFUNDED: 'success', NORMAL: 'success', ONLINE: 'success', ACTIVE: 'success',
  ISSUED: 'success',
  WAIT_PAY: 'warning', WAIT_SHIP: 'warning', PART_SHIPPED: 'warning',
  WAIT_RECEIVE: 'warning', WAIT_DRAFT: 'warning', WAIT_REVIEW: 'warning',
  IN_PRODUCTION: 'warning', ESCALATED: 'warning', PENDING: 'warning',
  REFUNDING: 'warning', PAYING: 'warning', WAITING: 'warning',
  APPLIED: 'warning', SOON: 'warning',
  OVERDUE: 'danger', REJECTED: 'danger', FAILED: 'danger', CANCELLED: 'danger', EXPIRED: 'danger',
  CLOSED: 'info', SHIPPED: 'info', OFFLINE: 'info', CREATED: 'info', NONE: 'info',
  TERMINATED: 'info',
};

export function statusLabel(status: string | null | undefined): string {
  return (status && STATUS_LABELS[status]) || status || '-';
}

export function statusTone(status: string | null | undefined): StatusTone {
  return (status && TONE_MAP[status]) || 'info';
}
