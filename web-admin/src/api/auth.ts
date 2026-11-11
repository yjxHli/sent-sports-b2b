/**
 * 认证 API（P6-22）。登录/刷新/登出为公开端点，skipAuth 防止注入旧令牌、skipAuthFlow 防刷新递归。
 */
import { http } from '@/utils/http';
import type { MeVO, TokenVO } from '@sent/shared';

export interface LoginPayload {
  username: string;
  password: string;
  aud?: string;
  deviceId?: string;
}

export const authApi = {
  login: (payload: LoginPayload) => http.post<TokenVO>(
    '/auth/login', payload, { skipAuth: true, skipAuthFlow: true },
  ),
  refresh: (refreshToken: string) => http.post<TokenVO>(
    '/auth/refresh', { refreshToken }, { skipAuth: true, skipAuthFlow: true },
  ),
  logout: (refreshToken: string) => http.post<void>(
    '/auth/logout', { refreshToken }, { skipAuthFlow: true },
  ),
  /** 当前员工资料 + permissions[] + dataScope（收口阶段已补齐 GET /api/admin/me） */
  me: () => http.get<MeVO>('/admin/me'),
};
