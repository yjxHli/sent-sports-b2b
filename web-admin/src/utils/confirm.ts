/**
 * 危险操作确认（P6-12，plan §10.1 / §9.4.5）。
 *
 * 流程：展示操作摘要 → 用户输入与 action 完全一致的短语 → 换取一次性确认令牌
 * → 返回带 X-Confirm-Token 的可提交凭证。后端校验短语与令牌、用后即焚。
 *
 * 短语表与后端 common/domain/ConfirmAction.java 一一对应，禁止页面自行手写。
 * 纯逻辑（校验/换令牌）与 UI 弹窗分区放置，便于单测与后续替换弹窗实现。
 */
import { h } from 'vue';
import { ElMessageBox } from 'element-plus';
import type { ConfirmTokenVO } from '@sent/shared';
import { http, unwrap } from '@/utils/http';

export const CONFIRM_ACTIONS = {
  ORDER_CLOSE: { label: '关闭订单', phrase: '确认关闭' },
  PAYMENT_WRITE_OFF: { label: '回款核销', phrase: '确认核销' },
  PAYMENT_REVERSE: { label: '回款冲销', phrase: '确认冲销' },
  ENTERPRISE_CREDIT: { label: '企业授信', phrase: '确认授信' },
  STOCK_CHECK_APPROVE: { label: '盘点调整审批', phrase: '确认调整' },
  REFUND_APPROVE: { label: '退款审核通过', phrase: '确认退款' },
  STAFF_LOGOUT_ALL: { label: '强制全端下线', phrase: '确认下线' },
} as const;

export type ConfirmAction = keyof typeof CONFIRM_ACTIONS;

export function phraseOf(action: ConfirmAction): string {
  return CONFIRM_ACTIONS[action].phrase;
}

/** 精确匹配，区分大小写 */
export function matchesPhrase(action: ConfirmAction, input: string | null | undefined): boolean {
  return typeof input === 'string' && input === phraseOf(action);
}

// ---------------------------------------------------------------------------
// 纯逻辑：换取一次性令牌
// ---------------------------------------------------------------------------

export interface ConfirmGrant {
  action: ConfirmAction;
  targetId?: string;
  token: string;
  /** 过期时间戳（毫秒）；过期必须重新确认，不允许静默重试 */
  expireAt: number;
  /** 直接作为 axios 请求的 headers 使用 */
  headers: Record<string, string>;
  isExpired(): boolean;
}

export async function issueConfirmToken(
  action: ConfirmAction,
  targetId: string | undefined,
  confirmText: string,
): Promise<ConfirmTokenVO> {
  if (!matchesPhrase(action, confirmText)) {
    throw new Error(`确认短语不正确，请准确输入「${phraseOf(action)}」`);
  }
  return unwrap(http.post<ConfirmTokenVO>('/admin/confirm-token', {
    action, targetId, confirmText,
  }));
}

// ---------------------------------------------------------------------------
// UI 弹窗：摘要 + 短语 + 取令牌
// ---------------------------------------------------------------------------

export interface OpenConfirmOptions {
  action: ConfirmAction;
  targetId?: string;
  title?: string;
  /** 业务摘要（如订单号、金额），由调用方传入，不做 HTML 拼接 */
  summary?: string;
}

/**
 * 打开确认弹窗并换取令牌。用户取消 / 输入错误 / 换令牌失败时抛错，调用方据此不提交。
 */
export async function openConfirm(options: OpenConfirmOptions): Promise<ConfirmGrant> {
  const { action, targetId, summary } = options;
  const phrase = phraseOf(action);
  const label = CONFIRM_ACTIONS[action].label;

  // 用 VNode 构造而非 HTML 字符串，避免业务摘要里的 <> 造成注入
  const message = h('div', [
    h('p', { style: 'margin:0 0 8px' }, `即将执行「${label}」，该操作不可撤销。`),
    ...(summary ? [h('p', { style: 'margin:0 0 8px;color:#5c6b65' }, summary)] : []),
    h('p', { style: 'margin:0' }, `请准确输入「${phrase}」以继续：`),
  ]);

  const { value } = await ElMessageBox.prompt(message, options.title ?? '危险操作确认', {
    confirmButtonText: '确认继续',
    cancelButtonText: '取消',
    inputPlaceholder: phrase,
    inputValidator: (input: string) =>
      (matchesPhrase(action, input) ? true : `请准确输入「${phrase}」`),
    type: 'warning',
  });

  const vo = await issueConfirmToken(action, targetId, String(value));
  return {
    action,
    targetId,
    token: vo.token,
    expireAt: vo.expireAt,
    headers: { 'X-Confirm-Token': vo.token },
    isExpired: () => Date.now() >= vo.expireAt,
  };
}
