/**
 * 客服工作台准入规则（纯函数，无 Vue / axios 依赖，便于单测）。
 *
 * 为什么单独成文件：这条判定决定「谁可以坐在客服席上」，属于权限边界，
 * 不能散落在登录页、路由守卫、布局三处各写一份 if。三处统一引用这里，
 * 单一真源，改动只需要改一个地方。
 */

/** 客服席位权限点（后端 V16__service_chat_permissions.sql 授予 CUSTOMER_SERVICE 角色） */
export const CHAT_SERVE_PERM = 'chat:serve';

/** 工作台首页：登录后唯一落地页 */
export const CS_HOME_PATH = '/workbench';

/** 归一权限码：去空白 + 小写，容忍后端下发 ' chat:serve ' 这类脏数据 */
function normalize(code: string): string {
  return code.trim().toLowerCase();
}

/**
 * 是否为客服人员。
 *
 * - `permissions` 缺失 / 非数组 → 拒绝（宁可不放行，也不把管理员误放进工作台）。
 * - `*` 通配符 → 放行（超级管理员可进）。
 * - 仅认 `chat:serve`；`chat:assign` / `chat:transfer` 等相邻权限不能替代它。
 */
export function canServeChat(permissions: readonly unknown[] | null | undefined): boolean {
  if (!Array.isArray(permissions)) return false;
  return permissions.some((code) => {
    if (typeof code !== 'string') return false;
    const value = normalize(code);
    return value === CHAT_SERVE_PERM || value === '*';
  });
}

/**
 * 登录成功后的落地地址。
 *
 * `redirect` 来自 URL query，属于外部输入，必须做开放重定向防护：
 * 只接受以单个 `/` 开头的站内路径；`//evil.com`、`https://...`、`/login` 一律回落地页。
 */
export function resolvePostLoginPath(redirect?: string | null): string {
  const raw = (redirect ?? '').trim();
  if (!raw) return CS_HOME_PATH;
  if (!raw.startsWith('/') || raw.startsWith('//')) return CS_HOME_PATH;
  // 已登录还回登录页会死循环
  if (raw === '/login' || raw.startsWith('/login?')) return CS_HOME_PATH;
  return raw;
}

/**
 * 客服端 Token 键名。
 *
 * 与 createTokenStorage 的拼接口径保持一致（`${prefix}_access_token` 等），
 * 单测据此锁死「同域下不与管理端 sent_admin_*、商城端 sent_user_* 冲突」。
 */
export function csStorageKeys(prefix: string): {
  access: string;
  refresh: string;
  expires: string;
  device: string;
} {
  return {
    access: `${prefix}_access_token`,
    refresh: `${prefix}_refresh_token`,
    expires: `${prefix}_expires_at`,
    device: `${prefix}_device_id`,
  };
}
