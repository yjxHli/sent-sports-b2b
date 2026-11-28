/**
 * 管理端 API 聚合出口：页面统一从 `@/api` 引入，避免散落的 http 直接调用。
 *
 * 2026-11-28 P2：auth / order / file 三份实现上提到 `@sent/shared/api`。
 * 这里用**本端** http 实例化（TokenStorage 前缀 / aud / 登出跳转都不是别端能共用的），
 * 再按原名导出，页面与测试的 import 路径完全不变。
 */
import { http } from '@/utils/http';
import { createSharedAdminApi } from '@sent/shared/api';

const sharedApis = createSharedAdminApi(http);
export const { authApi, orderApi, fileApi } = sharedApis;

export type {
  LoginPayload, FileVO, ShipLineCmd, ShipCmd, AgentOrderItemCmd, AgentCreateCmd,
  ShipOrderResult, AdminCustomerOrderVO, SharedApiHttp, SharedAdminApis,
} from '@sent/shared/api';

export * from './product';
export * from './stock';
export * from './custom';
export * from './customer';
export * from './coupon';
export * from './finance';
export * from './chat';
export * from './system';
export * from './review';
export * from './faq';
export * from './customerTag';
