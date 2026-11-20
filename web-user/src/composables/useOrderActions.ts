/**
 * 用户端订单动作门禁（P3-F2）：一单能做什么，只在这里判断一次。
 *
 * 重构前「能不能开票」在列表页和详情页各写了一份，改口径要改两处，
 * 一旦漏改就会出现「列表给入口、详情不给」或者反过来点进去 409。
 * 这里把判断拆成纯函数（列表页按行调用）+ 一个 `useOrderActions` 包装（详情页用 computed），
 * 两边共用同一份状态口径。
 *
 * 状态口径以后端 OrderStatus / PayStatus 为准，前端不臆造「可退可开票」的中间态。
 * 注意：后端 Long 会序列化成字符串，凡涉及时间的字段一律先 Number() 再判断。
 */
import { computed, type ComputedRef, type Ref } from 'vue';

/** 判断动作所需的最小订单形状：列表行与详情 VO 都满足，不绑死某一个 DTO */
export interface OrderActionInput {
  status?: string | null;
  payStatus?: string | null;
  trackingNo?: string | null;
  shippedAtMillis?: number | string | null;
}

/**
 * 申请开票：口径由用户 2026-11-15 收紧为**订单已完成**。
 * 在途订单货还没到手、可能退换，票面金额与实际成交不一致，财务无法核销；
 * 未付款 / 整单退款后也没有票面依据，只认 PAID 与 PART_REFUNDED 两种付费态。
 */
export function canInvoiceOrder(order?: OrderActionInput | null): boolean {
  if (order?.status !== 'COMPLETED') return false;
  return order.payStatus === 'PAID' || order.payStatus === 'PART_REFUNDED';
}

/** 申请退款 / 售后：已付款但还没走完流程的单才可以发起（终态只能走客服） */
export function canRefundOrder(order?: OrderActionInput | null): boolean {
  const status = order?.status;
  return status === 'WAIT_SHIP' || status === 'WAIT_RECEIVE' || status === 'PART_SHIPPED';
}

/** 确认收货：发货后（含部分发货）才可操作 */
export function canConfirmOrder(order?: OrderActionInput | null): boolean {
  const status = order?.status;
  return status === 'WAIT_RECEIVE' || status === 'PART_SHIPPED';
}

/** 再买一单 / 加入购物车：只在终态出现，在途单加购容易重复下单 */
export function canRebuyOrder(order?: OrderActionInput | null): boolean {
  const status = order?.status;
  return status === 'COMPLETED' || status === 'CANCELLED' || status === 'CLOSED';
}

/** 删除订单：与后端 DeleteOrderUseCase 的白名单一致（仅终态） */
export function canDeleteOrder(order?: OrderActionInput | null): boolean {
  return canRebuyOrder(order);
}

/** 查看物流：已发货才有物流单号，否则点进去只有一个空态 */
export function canTraceOrder(order?: OrderActionInput | null): boolean {
  return Boolean(order?.trackingNo) || Number(order?.shippedAtMillis ?? 0) > 0;
}

export interface OrderActions {
  canInvoice: ComputedRef<boolean>;
  canRefund: ComputedRef<boolean>;
  canConfirm: ComputedRef<boolean>;
  canRebuy: ComputedRef<boolean>;
  canDelete: ComputedRef<boolean>;
  canTrace: ComputedRef<boolean>;
}

/** 详情页用：把当前订单包成一组 computed，模板里直接 `v-if="canInvoice"` */
export function useOrderActions(order: Ref<OrderActionInput | null | undefined>): OrderActions {
  return {
    canInvoice: computed(() => canInvoiceOrder(order.value)),
    canRefund: computed(() => canRefundOrder(order.value)),
    canConfirm: computed(() => canConfirmOrder(order.value)),
    canRebuy: computed(() => canRebuyOrder(order.value)),
    canDelete: computed(() => canDeleteOrder(order.value)),
    canTrace: computed(() => canTraceOrder(order.value)),
  };
}
