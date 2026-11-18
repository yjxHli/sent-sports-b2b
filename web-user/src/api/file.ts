/**
 * 文件服务 API（商城端）：定制图稿 / 退款凭证 / 客服图片共用。
 *
 * 上传走 multipart（后端落盘为内容寻址的相对路径 = fileId），读接口允许匿名，
 * 因此 `url(fileId)` 可以直接塞进 `<img src>`。
 */
import { http } from '@/utils/http';

export interface FileVO {
  /** 相对路径（业务表保存这个，如 2026/11/ab/abcd....png） */
  fileId: string;
  /** 可直接渲染的访问地址 */
  url: string;
  fileName: string;
  size: number;
}

export const fileApi = {
  upload: (file: File) => {
    const form = new FormData();
    form.append('file', file, file.name);
    return http.post<FileVO>('/files', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60_000,
    });
  },
  /**
   * 企业注册营业执照：注册发生在登录前，没有令牌，走匿名专用端点。
   * 后端只放行 JPG / PNG 且 ≤ 2MB，通用上传仍要求登录。
   */
  uploadEnterpriseLicense: (file: File) => {
    const form = new FormData();
    form.append('file', file, file.name);
    return http.post<FileVO>('/auth/enterprise/license', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60_000,
      skipAuth: true,
      skipAuthFlow: true,
    });
  },
  /** fileId → 访问地址；空值返回空串，模板里用 v-if 控制即可 */
  url: (fileId?: string | null): string => (fileId ? `/api/files/${fileId}` : ''),
};
