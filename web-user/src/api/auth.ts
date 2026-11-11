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

export const authApi = {
  login: (payload: LoginPayload) =>
    http.post<TokenVO>('/auth/login', payload, { skipAuth: true, skipAuthFlow: true }),
  refresh: (refreshToken: string) =>
    http.post<TokenVO>('/auth/refresh', { refreshToken }, { skipAuth: true, skipAuthFlow: true }),
  logout: (refreshToken: string) =>
    http.post<void>('/auth/logout', { refreshToken }, { skipAuthFlow: true }),
};
