/**
 * 图片地址解析（V54）：商品图集 / 详情长图 / 款式图统一走这里。
 *
 * 业务表存的是文件服务的相对路径（fileId，形如 `2026/11/ab/abcd.png`），
 * 读接口匿名可访问，所以直接拼 `/api/files/` 即可塞进 `<img src>`。
 * 单独抽出来的原因：详情页有 3 处图源（图集、SKU 款式图、详情长图），
 * 各自写一遍 if/else 必然漏掉某一种（比如忘了过滤演示域名）。
 *
 * 三条规则：
 * 1. 绝对地址原样返回（后端可能存了外部 CDN）；
 * 2. `demo.sent.test` 是种子数据的虚构域名 → 返回空串，交给占位图；
 * 3. 其余视为 fileId，拼文件服务前缀。
 */
export function resolveImageUrl(src?: string | null): string {
  const s = (src ?? '').trim();
  if (!s) return '';
  if (/^https?:\/\//i.test(s)) return s;
  if (/demo\.sent\.test/i.test(s)) return '';
  return `/api/files/${s.replace(/^\/+/, '')}`;
}
