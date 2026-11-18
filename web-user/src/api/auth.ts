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
}

export interface EnterpriseRegisterVO {
  phone: string;
  nickname: string;
  auditStatus: string;
}

export const authApi = {
  login: (payload: LoginPayload) =>
    http.post<TokenVO>('/auth/login', payload, { skipAuth: true, skipAuthFlow: true }),
  register: (payload: RegisterPayload) =>
    http.post<RegisterVO>('/auth/register', payload, { skipAuth: true, skipAuthFlow: true }),
  enterpriseRegister: (payload: EnterpriseRegisterPayload) =>
    http.post<EnterpriseRegisterVO>('/auth/enterprise/register', payload,
      { skipAuth: true, skipAuthFlow: true }),
  refresh: (refreshToken: string) =>
    http.post<TokenVO>('/auth/refresh', { refreshToken }, { skipAuth: true, skipAuthFlow: true }),
  logout: (refreshToken: string) =>
    http.post<void>('/auth/logout', { refreshToken }, { skipAuthFlow: true }),
};
