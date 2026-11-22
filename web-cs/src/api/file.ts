/**
 * 文件服务 API（管理端）：审图 / 退款凭证 / 客服图片读取与上传。
 *
 * 与商城端同一套接口：上传 multipart，返回 fileId；读接口允许匿名，可直接给 `<img src>`。
 */
import { http } from '@/utils/http';

export interface FileVO {
  fileId: string;
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
  url: (fileId?: string | null): string => (fileId ? `/api/files/${fileId}` : ''),
};
