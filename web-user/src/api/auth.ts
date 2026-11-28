/**
 * 认证 API（用户端）。登录/刷新/登出为公开端点，skipAuth 防注入旧令牌、skipAuthFlow 防刷新递归。
 * 用户端与管理端共用同一个后端 AuthController（/api/auth/*），仅 aud 不同。
 */
import { http } from '@/utils/http';
import type { TokenVO } from '@sent/shared';

export interface LoginPayload {
  username: string;
  password: string;
  aud?: string;
  deviceId?: string;
}

/** 注册只提交手机号与两次密码；客户号由服务端分配，用户不可见。 */
export interface RegisterPayload {
  phone: string;
  password: string;
  confirmPassword: string;
}

/** 发送验证码结果：demoCode 仅开发环境非空（后端开关控制），生产恒为 null。 */
export interface SendCodeVO {
  demoCode: string | null;
}

/** 发送登录验证码：后端存内存并打印控制台，不接真实短信网关。 */
export interface SendCodePayload {
  phone: string;
}

/** 验证码登录：手机号 + 验证码，aud 由后端固定 user。 */
export interface LoginByCodePayload {
  phone: string;
  code: string;
  deviceId?: string;
}

/** 找回密码：发送验证码 / 提交重置，验证码由后端生成并一次性消费。 */
export interface SendResetCodePayload {
  phone: string;
}

export interface ResetPasswordPayload {
  phone: string;
  code: string;
  password: string;
  confirmPassword: string;
}

export interface VerifyResetCodePayload {
  phone: string;
  code: string;
}

export interface RegisterVO {
  customerId: number;
  phone: string;
  nickname: string;
  customerNo: string;
}

/** 企业注册：营业执照传 fileId，客户号由服务端分配且用户不可见。 */
export interface EnterpriseRegisterPayload {
  phone: string;
  password: string;
  confirmPassword: string;
  companyName: string;
  creditCode: string;
  licenseFileId: string;
  code: string;
}

export interface EnterpriseRegisterVO {
  phone: string;
  nickname: string;
  auditStatus: string;
}

/** 企业审核进度：未审核通过无法登录，用手机号 + 短信验证码匿名查询。 */
export interface AuditProgressCmd {
  phone: string;
  code: string;
}

export interface AuditProgressVO {
  customerId: number;
  phone: string;
  companyName: string;
  creditCode: string;
  licenseFileId: string;
  status: string;
  auditRemark: string | null;
  auditedAtMillis: number | null;
  submittedAtMillis: number | null;
}

export const authApi = {
  login: (payload: LoginPayload) =>
    http.post<TokenVO>('/auth/login', payload, { skipAuth: true, skipAuthFlow: true }),
  sendCode: (payload: SendCodePayload) =>
    http.post<SendCodeVO>('/auth/sms-code', payload, { skipAuth: true, skipAuthFlow: true }),
  loginByCode: (payload: LoginByCodePayload) =>
    http.post<TokenVO>('/auth/login/by-code', payload, { skipAuth: true, skipAuthFlow: true }),
  sendResetCode: (payload: SendResetCodePayload) =>
    http.post<SendCodeVO>('/auth/reset/sms-code', payload, { skipAuth: true, skipAuthFlow: true }),
  verifyResetCode: (payload: VerifyResetCodePayload) =>
    http.post<void>('/auth/reset/verify-code', payload, { skipAuth: true, skipAuthFlow: true }),
  resetPassword: (payload: ResetPasswordPayload) =>
    http.post<void>('/auth/reset/password', payload, { skipAuth: true, skipAuthFlow: true }),
  register: (payload: RegisterPayload) =>
    http.post<RegisterVO>('/auth/register', payload, { skipAuth: true, skipAuthFlow: true }),
  enterpriseRegister: (payload: EnterpriseRegisterPayload) =>
    http.post<EnterpriseRegisterVO>('/auth/enterprise/register', payload,
      { skipAuth: true, skipAuthFlow: true }),
  enterpriseAuditProgress: (payload: AuditProgressCmd) =>
    http.post<AuditProgressVO>('/auth/enterprise/audit-progress', payload,
      { skipAuth: true, skipAuthFlow: true }),
  logout: (refreshToken: string) =>
    http.post<void>('/auth/logout', { refreshToken }, { skipAuthFlow: true }),
};
