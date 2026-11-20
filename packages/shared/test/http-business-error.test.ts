/**
 * HTTP 内核：后端用 HTTP 4xx/422 + 统一 R 结构返回业务错误时，
 * 必须把 body.code/body.message 透传到 HttpError，不能退化成 axios 的
 * "Request failed with status code 422"（否则登录页识别不到「未注册手机号」）。
 *
 * TDD 红：修复前 onRejected 丢弃 response.data，本测试失败。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import axios, { AxiosError } from 'axios';
import { createSentHttp, HttpError, type TokenStorage } from '../src/http.ts';

function fakeStorage(): TokenStorage {
  return {
    getAccessToken: () => null,
    getRefreshToken: () => null,
    isAccessTokenExpired: () => false,
    setTokens: () => {},
    clearTokens: () => {},
    applyNewAccessToken: () => {},
    getDeviceId: () => 'device-test',
    redirectToLogin: () => {},
    logoutToLogin: () => {},
    onTokenChanged: () => () => {},
  } as unknown as TokenStorage;
}

/** 模拟后端 HTTP 422 + 统一 R 响应体：axios 会 reject 一个带 response 的 AxiosError */
function httpWithError(body: unknown, status: number) {
  const http = createSentHttp({
    baseURL: '/api',
    storage: fakeStorage(),
    onLogout: () => {},
  });
  const adapter = async (config: any) => {
    const response = { data: body, status, statusText: 'error', headers: {}, config };
    throw new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_RESPONSE', config, undefined, response);
  };
  (http as unknown as { instance: { defaults: { adapter: unknown } } }).instance.defaults.adapter = adapter;
  return http;
}

test('4xx 业务错误透传业务码与文案（未注册手机号 42202）', async () => {
  const http = httpWithError({
    code: 42202,
    message: '该手机号尚未注册，请先注册',
    data: null,
    traceId: 'trace-1',
  }, 422);

  await assert.rejects(
    () => http.post('/auth/login', { username: '13900009999', password: 'x' }, { skipAuth: true }),
    (error: unknown) => {
      assert.ok(error instanceof HttpError);
      assert.equal((error as HttpError).code, 42202, '业务码必须从响应体透传');
      assert.equal((error as HttpError).message, '该手机号尚未注册，请先注册');
      return true;
    },
  );
});

test('4xx 限流错误（42901）同样透传业务码', async () => {
  const http = httpWithError({
    code: 42901, message: '验证码发送过于频繁，请 60 秒后重试', data: null, traceId: 'trace-3',
  }, 429);
  await assert.rejects(
    () => http.post('/auth/sms-code', {}, { skipAuth: true }),
    (error: unknown) => {
      assert.ok(error instanceof HttpError);
      assert.equal((error as HttpError).code, 42901);
      return true;
    },
  );
});
