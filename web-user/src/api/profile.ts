/**
 * 用户端个人资料 API（收口阶段）。GET /api/me 由后端 CustomerProfileQueryService 提供真实查询，
 * 返回基本资料 / 会员等级 / 积分（见 shared 的 UserMeVO）。
 */
import { http } from '@/utils/http';
import type { UserMeVO } from '@sent/shared';

export const profileApi = {
  me: () => http.get<UserMeVO>('/me'),
  /** 编辑昵称 / 头像；avatarFileId 传空串表示恢复默认头像 */
  update: (data: { nickname: string; avatarFileId?: string | null }) =>
    http.put<UserMeVO>('/me/profile', data),
};
