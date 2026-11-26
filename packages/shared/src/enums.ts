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

/**
 * 消息发送方：CUSTOMER 客户 / STAFF 客服 / BOT 智能助手 / SYSTEM 系统旁白。
 * <p>BOT 与 SYSTEM 必须分开：BOT 是「有人格的话」，客户端渲染成带头像的普通气泡；
 * SYSTEM 是「会话已结束」「客服已接入」这类旁白，渲染成居中浅灰提示。</p>
 */
export const SenderType = { CUSTOMER: 'CUSTOMER', STAFF: 'STAFF', BOT: 'BOT', SYSTEM: 'SYSTEM' } as const;
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
  // 客服质检小结
  DONE: '已完成', UNRESOLVED: '未解决',
  // 客户
  PERSON: '个人', ENTERPRISE: '企业',
  // 优惠券（券实例状态）
  UNUSED: '待使用', USED: '已使用',
  // 发票 / 合同（收口阶段管理端列表）
  APPLIED: '待开票', ISSUED: '已开票', EXPIRED: '已到期', SOON: '即将到期',
  TERMINATED: '已终止',
};

/**
 * 域内覆盖字典：同一个状态码在不同业务里中文不同（ACTIVE 在会话是「进行中」、
 * 在企业认证是「已认证」；FAILED 在支付是「支付失败」、在售后是「退款失败」）。
 * 基础字典只放通用语义，这类冲突由各域覆盖，避免每个页面各抄一份。
 */
/**
 * 企业认证状态：真库 enterprise_profile.status 里 ACTIVE=已认证、REJECTED=已驳回，
 * 演示数据与历史档案用的是 NORMAL / PENDING（都按「待审核」展示）。
 * 注意 ACTIVE 在会话语境是「进行中」，所以这里必须覆盖而不能只靠基础字典。
 */
export const ENTERPRISE_STATUS_LABELS: Record<string, string> = {
  PENDING: '待审核',
  NORMAL: '待审核',
  ACTIVE: '已认证',
  REJECTED: '已驳回',
};

export const REFUND_STATUS_LABELS: Record<string, string> = {
  PENDING: '待审核',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  CLOSED: '已关闭',
  FAILED: '退款失败',
};

export const REFUND_STATUS_TONES: Record<string, StatusTone> = {
  PENDING: 'warning',
  REFUNDING: 'warning',
  REFUNDED: 'success',
  CLOSED: 'info',
  FAILED: 'danger',
};

/** 企业注册审核进度页：面向企业的口吻（审核中 / 已审核 / 审核未通过），与后台审核台措辞不同 */
export const AUDIT_PROGRESS_LABELS: Record<string, string> = {
  PENDING: '审核中',
  ACTIVE: '已审核',
  APPROVED: '已审核',
  REJECTED: '审核未通过',
};

/** 客服小结的问题分类 / 信息充分度（质检台与工作台历史弹窗共用一份） */
export const CHAT_CATEGORY_LABELS: Record<string, string> = {
  QUALITY: '商品品质问题',
  INQUIRY: '商品咨询',
  UNCLEAR: '未明确',
};

export const CHAT_INFO_LABELS: Record<string, string> = {
  CLEAR: '明确信息',
  NO_REPLY: '用户未回复',
};

/** 满意度评分（客服评价卡 / 用户端评价弹窗共用） */
export const SATISFY_LABELS: Record<string, string> = {
  SATISFIED: '满意',
  NORMAL: '一般',
  UNSATISFIED: '不满意',
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

/**
 * 状态码 -> 中文文案。
 * @param dict 域内覆盖字典（如 ENTERPRISE_STATUS_LABELS）：命中优先，未命中回落基础字典，
 *             这样页面只需要指明「这是哪个域」，不用自己再抄一份 switch。
 */
export function statusLabel(
  status: string | null | undefined,
  dict?: Record<string, string>,
): string {
  // 统一按大写匹配：后端个别历史接口会下发小写状态，页面不必各写一次 toUpperCase
  const key = status ? status.toUpperCase() : '';
  const text = (key && (dict?.[key] ?? STATUS_LABELS[key])) || status;
  return text || '-';
}

export function statusTone(
  status: string | null | undefined,
  dict?: Record<string, StatusTone>,
): StatusTone {
  const key = status ? status.toUpperCase() : '';
  return (key && (dict?.[key] ?? TONE_MAP[key])) || 'info';
}

// ---------------------------------------------------------------------------
// 定制退款三档（与后端 customize/service/CustomRefundPolicy 同口径，2026-11-21）
// ---------------------------------------------------------------------------
//
// 三档规则：审核通过前随便退、审核通过未排产要人工确认、一旦排产绝对不退。
// **前端只做展示层预判**（按钮能不能点、点了说什么），判定真源永远在后端
// （申请退款时逐行联查定制单状态，已排产回 CUSTOM_REFUND_NOT_ALLOWED 40980）。
// 放在 shared 里是因为「订单详情」与「定制单详情」两处都要用，各抄一份必然漂。

/** 已进入生产链路：绝对不可退（IN_PRODUCTION 生产中 / FINISHED 已完工 / SHIPPED 已发货）。 */
export const CUSTOM_REFUND_BLOCKED_STATUS: readonly string[] = ['IN_PRODUCTION', 'FINISHED', 'SHIPPED'];

/** 图纸已过审未排产：可申请，但必须由客服人工确认是否已备料。 */
export const CUSTOM_REFUND_MANUAL_STATUS: readonly string[] = ['APPROVED', 'ESCALATED'];

/** 定制已排产时的统一提示语（弹窗与提示条共用一份，别再各写一句）。 */
export const CUSTOM_REFUND_BLOCK_TEXT = '定制商品已开始生产，无法申请退款。如有质量问题请联系客服。';

/** 该定制状态是否已进入生产链路（不可退）。 */
export function isCustomRefundBlocked(status?: string | null): boolean {
  return CUSTOM_REFUND_BLOCKED_STATUS.includes(String(status ?? '').toUpperCase());
}

/** 该定制状态是否属于「可申请但需人工复核」。 */
export function isCustomRefundManual(status?: string | null): boolean {
  return CUSTOM_REFUND_MANUAL_STATUS.includes(String(status ?? '').toUpperCase());
}
