/**
 * 订单 API（用户端，对齐 OrderController P4-16）。
 * 下单需幂等键；取价参数（会员/协议折扣率、阶梯价）后端为桩，UI 传 0 默认值。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';
import type { SentRequestConfig } from '@sent/shared/http';

export interface OrderItemCmd {
  skuId: number;
  qty: number;
  customerTypeCode?: string | null;
  memberDiscountRate?: number;
  agreementDiscountRate?: number;
  allowStacking?: boolean;
  ladderTiers?: { minQty: number; rateOff: number }[];
  /** UV 定制件：后端下单时据此生成 custom_order，客户随后上传图稿走审图。 */
  customFlag?: boolean;
}

export interface OrderVO {
  orderNo: string;
  status: string | null;
  payStatus: string | null;
  shipStatus?: string | null;
  refundStatus?: string | null;
  payableCent?: number | null;
  payableYuan?: string | null;
  goodsAmountCent?: number | null;
  goodsAmountYuan?: string | null;
  freightCent?: number | null;
  freightYuan?: string | null;
  pointDeductCent?: number | null;
  pointDeductYuan?: string | null;
  /** 商品行：申请退款要逐行 skuId + 行实付，详情接口与列表同口径返回 */
  items?: OrderItemLine[];
  /** 下单 / 付款 / 成交时间（毫秒，0/null = 未发生）——详情页时间轴据此点亮步骤 */
  createdAt?: number | null;
  paidAt?: number | null;
  completedAt?: number | null;
  /** 优惠与定制费（分），付款详情里拆开展示 */
  discountCent?: number | null;
  customFeeCent?: number | null;
  /** 收货地址快照（原始 JSON 串，历史单可能为空） */
  addressSnapshotJson?: string | null;
  /** 最近一次发货：承运商 / 单号 / 发货时间（毫秒） */
  carrier?: string | null;
  trackingNo?: string | null;
  shippedAtMillis?: number | null;
  /** 支付摘要：渠道（BALANCE/ALIPAY/WECHAT/MOCK）/ 平台流水号 / 渠道交易号，未支付时全为 null */
  payChannel?: string | null;
  payTxnNo?: string | null;
  tradeNo?: string | null;
}

/** 订单商品行（标题/规格取快照，后端已做空快照兜底） */
export interface OrderItemLine {
  skuId?: string | null;
  /**
   * 商品（SPU）主键。
   * 点商品名跳详情必须用它拼 `/product/{productId}`：商品详情接口按 SPU 查，
   * 拿 skuId 拼出来的链接会落到「商品不存在或已下架」。
   */
  productId?: string | null;
  title?: string | null;
  spec?: string | null;
  qty?: number | null;
  unitPriceCent?: number | null;
  unitPriceYuan?: string | null;
  /** 行实付（分）= 行金额 − 分摊优惠，退款行金额上限 */
  linePaidCent?: number | null;
  linePaidYuan?: string | null;
}

export interface OrderListItem {
  orderNo: string;
  status: string | null;
  payStatus: string | null;
  refundStatus?: string | null;
  payableCent?: number | null;
  payableYuan?: string | null;
  /** 下单时间（毫秒） */
  createdAt?: number | null;
  /** 商品行（列表页一次返回，无需逐单再查详情） */
  items?: OrderItemLine[];
}

export const orderApi = {
  create: (cmd: {
    customerId: number;
    /** 收货地址主键：下单必填，后端据此固化 address_snapshot_json */
    addressId: number;
    idemKey: string;
    availablePoints: number;
    freightFreeThresholdCent: number;
    freightFeeCent: number;
    items: OrderItemCmd[];
  }, config?: SentRequestConfig) => http.post<OrderVO>('/orders', cmd, config),
  list: (query: PageQuery & { status?: string | null }) =>
    http.get<PageResult<OrderListItem>>('/orders', query),
  detail: (orderNo: string) =>
    http.get<OrderVO>(`/orders/${orderNo}`),
  cancel: (orderNo: string) =>
    http.post<void>(`/orders/${orderNo}/cancel`),
  /** 删除订单：客户侧软删，只从「我的订单」里移除；在途订单后端回 40901 */
  remove: (orderNo: string) =>
    http.delete<void>(`/orders/${orderNo}`),
  confirm: (orderNo: string, lines: { skuId: number; qty: number }[]) =>
    http.post<OrderVO>(`/orders/${orderNo}/confirm`, { lines }),
};
