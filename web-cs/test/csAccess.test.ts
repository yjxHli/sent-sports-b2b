/**
 * web-cs 准入规则单测（TDD 红→绿）。
 *
 * 覆盖「客服身份」这一处真正的业务判定：权限点判定、登录后落地页、以及
 * Token 前缀隔离的键名。规则本身一旦写错，会把管理员放进工作台、或让
 * 客服端与管理端互相顶掉登录态，因此必须先有红用例。
 *
 * 跑法（与 packages/shared/test 同一套 Node 内置 test runner，不引入新框架）：
 *   node --experimental-strip-types --test web-cs/test/csAccess.test.ts
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  CHAT_SERVE_PERM,
  canServeChat,
  resolvePostLoginPath,
  csStorageKeys,
} from '../src/utils/csAccess.ts';

test('拥有 chat:serve 的账号才能进工作台', () => {
  assert.equal(canServeChat(['chat:serve']), true);
  assert.equal(canServeChat(['chat:assign', 'chat:serve', 'chat:close']), true);
});

test('缺少 chat:serve 一律拒绝（即使有其它 chat 权限）', () => {
  assert.equal(canServeChat([]), false);
  assert.equal(canServeChat(['chat:assign', 'chat:transfer', 'order:list']), false);
  assert.equal(canServeChat(['chat:serve:read']), false);
});

test('通配符 * 视为超级管理员，可进工作台', () => {
  assert.equal(canServeChat(['*']), true);
});

test('权限数组为 null/undefined/脏数据时不抛异常且拒绝', () => {
  assert.equal(canServeChat(null), false);
  assert.equal(canServeChat(undefined), false);
  assert.equal(canServeChat([null as unknown as string, 123 as unknown as string]), false);
});

test('权限码大小写与空白不敏感（后端曾下发带空格的码）', () => {
  assert.equal(canServeChat([' chat:serve ']), true);
  assert.equal(canServeChat(['CHAT:SERVE']), true);
});

test('登录成功后一律进工作台，回跳地址只接受站内路径', () => {
  assert.equal(resolvePostLoginPath(undefined), '/workbench');
  assert.equal(resolvePostLoginPath('/workbench'), '/workbench');
  assert.equal(resolvePostLoginPath('/workbench?tab=2'), '/workbench?tab=2');
  // 开放重定向防护：跨站 / 协议相对地址一律丢弃
  assert.equal(resolvePostLoginPath('https://evil.example.com'), '/workbench');
  assert.equal(resolvePostLoginPath('//evil.example.com'), '/workbench');
  assert.equal(resolvePostLoginPath('/login'), '/workbench');
});

test('Token 键名走 cs-token 前缀，与 sent_admin / sent_user 不重叠', () => {
  const keys = csStorageKeys('cs-token');
  assert.deepEqual(keys, {
    access: 'cs-token_access_token',
    refresh: 'cs-token_refresh_token',
    expires: 'cs-token_expires_at',
    device: 'cs-token_device_id',
  });
  assert.equal(CHAT_SERVE_PERM, 'chat:serve');
});
