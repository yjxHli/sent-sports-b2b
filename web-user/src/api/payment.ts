/**
 * 支付 API（用户端，对齐 PaymentController P4-19）。
 * 发起支付需幂等键；payResult 用于轮询支付结果。
 * 本地演示环境没有真实第三方渠道，因此发起支付后由 scheduleMockPayCallback 延迟触发
 * POST /payment/mock/callback，等价复现「渠道服务器异步通知」把订单推进到 WAIT_SHIP。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';
import type { SentRequestConfig } from '@sent/shared/http';

export interface PayResultVO {
  orderNo: string;
  status: string | null;
  txnNo?: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
}

export interface MockCallbackCmd {
  txnNo: string;
  orderNo: string;
  amountCent: number;
  channel: string;
  payChannelTradeNo: string;
  paidAtMillis: number;
}

export const paymentApi = {
  create: (cmd: {
    orderNo: string;
    amountCent: number;
    channel: string;
    idemKey: string;
  }, config?: SentRequestConfig) => http.post<PayResultVO>('/payments', cmd, config),
  payResult: (orderNo: string) =>
    http.get<PayResultVO>(`/orders/${orderNo}/pay-result`),
  list: (query: PageQuery) =>
    http.get<PageResult<PayResultVO>>('/payments', query),
  /** 模拟渠道回调：真实环境由支付渠道服务器调用，本地演示由前端延迟 1~2s 复现该时序 */
  mockCallback: (cmd: MockCallbackCmd) =>
    http.post<PayResultVO>('/payment/mock/callback', cmd),
};

/**
 * 发起支付成功后调用：延迟 1.2 秒自动触发模拟回调，把订单从 WAIT_PAY 推进到 WAIT_SHIP。
 * 回调是「异步通知」，不能阻塞支付结果弹窗；失败时由订单列表刷新/详情查询兜底展示。
 */
export function scheduleMockPayCallback(cmd: MockCallbackCmd, delayMs = 1200): void {
  window.setTimeout(() => {
    void paymentApi.mockCallback(cmd).catch(() => {
      // 渠道通知可能重复或迟到，后端 markPaid 条件更新天然幂等；此处静默，由列表刷新兜底
    });
  }, delayMs);
}
