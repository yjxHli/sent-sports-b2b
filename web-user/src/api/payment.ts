/**
 * 支付 API（用户端，对齐 PaymentController P4-19）。
 * 发起支付需幂等键。
 *
 * 本地演示环境没有真实第三方渠道，因此发起支付后由 scheduleMockPayCallback 延迟触发
 * POST /payment/mock/callback，等价复现「渠道服务器异步通知」把订单推进到 WAIT_SHIP。
 *
 * 回调必须带签名：发起支付时后端会把「渠道受理回执」的签名参数（notifyParams + sign）下发，
 * 前端只能原样回放这一份。签名密钥在服务端，前端无法为别的订单/金额伪造通知，
 * 后端会把验签失败的回调直接拦成 40001。
 */
import { http } from '@/utils/http';
import type { SentRequestConfig } from '@sent/shared/http';

export interface PayResultVO {
  orderNo: string;
  status: string | null;
  txnNo?: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
  /** 渠道异步通知参数（发起支付时下发，键值均为字符串） */
  notifyParams?: Record<string, string> | null;
  /** 上述参数的通道签名 */
  sign?: string | null;
}

/** 渠道异步通知报文：参数表 + 签名（缺签名一律被后端拒签） */
export type ChannelNotify = Record<string, string>;

export const paymentApi = {
  create: (cmd: {
    orderNo: string;
    amountCent: number;
    channel: string;
    idemKey: string;
  }, config?: SentRequestConfig) => http.post<PayResultVO>('/payments', cmd, config),
  /** 模拟渠道回调：真实环境由支付渠道服务器调用，本地演示由前端延迟 1~2s 复现该时序 */
  mockCallback: (notify: ChannelNotify) =>
    http.post<PayResultVO>('/payment/mock/callback', notify),
};

/**
 * 发起支付成功后调用：延迟 1.2 秒自动触发模拟回调，把订单从 WAIT_PAY 推进到 WAIT_SHIP。
 * 回调是「异步通知」，不能阻塞支付结果弹窗；失败时由订单列表刷新/详情查询兜底展示。
 */
export function scheduleMockPayCallback(pay: PayResultVO, delayMs = 1200): void {
  const { notifyParams, sign } = pay;
  // 没拿到渠道受理回执就不发通知：演示通道以外（真实通道）由渠道自己回调
  if (!notifyParams || !sign) return;
  window.setTimeout(() => {
    void paymentApi.mockCallback({ ...notifyParams, sign }).catch(() => {
      // 渠道通知可能重复或迟到，后端 markPaid 条件更新天然幂等；此处静默，由列表刷新兜底
    });
  }, delayMs);
}
