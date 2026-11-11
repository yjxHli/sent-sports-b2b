/**
 * 用户端个人资料 API（收口阶段）。GET /api/me 由后端 CustomerProfileQueryService 提供真实查询，
 * 返回基本资料 / 会员等级 / 积分（见 shared 的 UserMeVO）。
 */
import { http } from '@/utils/http';
import type { UserMeVO } from '@sent/shared';

export const profileApi = {
  me: () => http.get<UserMeVO>('/me'),
};
