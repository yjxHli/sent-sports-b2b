/**
 * 收货地址簿 API（用户端，对齐 `/api/addresses`）。
 *
 * 结算页要能选地址、加地址、删地址、设默认，所以这里是标准 CRUD；
 * 身份由后端从令牌取，前端不传 customerId（传了也不会被采信）。
 */
import { http } from '@/utils/http';

export interface AddressVO {
  /** 地址主键（后端 Long 会序列化成字符串，用前先 Number()） */
  id: string | number;
  receiver: string;
  phone: string;
  province?: string | null;
  city?: string | null;
  district?: string | null;
  detail: string;
  isDefault: boolean;
}

export interface AddressCmd {
  receiver: string;
  phone: string;
  province?: string | null;
  city?: string | null;
  district?: string | null;
  detail: string;
  isDefault: boolean;
}

export const addressApi = {
  list: () => http.get<AddressVO[]>('/addresses'),
  create: (cmd: AddressCmd) => http.post<AddressVO>('/addresses', cmd),
  update: (id: number, cmd: AddressCmd) => http.put<AddressVO>(`/addresses/${id}`, cmd),
  remove: (id: number) => http.delete<void>(`/addresses/${id}`),
  setDefault: (id: number) => http.put<void>(`/addresses/${id}/default`),
};
