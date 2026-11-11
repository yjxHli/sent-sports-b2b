/**
 * 管理端权限码目录（P6-49）。
 *
 * 真源：server 侧 `@RequirePerm("...")` 注解 + router/modules/business.ts 的 MENU_ROUTES.meta.perm。
 * 这里按业务域分组供角色权限矩阵渲染；后续可由脚本从 Java 注解自动生成，避免手抄漂移
 * （与 packages/shared/enums.ts 的同源策略一致）。
 */
export interface PermissionGroup {
  label: string;
  codes: string[];
}

export const PERMISSION_GROUPS: PermissionGroup[] = [
  { label: '商品', codes: ['product:read', 'product:create', 'product:update', 'product:down'] },
  { label: '库存', codes: ['stock:read', 'stock:inbound', 'stock:loss', 'stock:check', 'stock:check:approve'] },
  { label: '订单', codes: [
    'order:read', 'order:create', 'order:cancel', 'order:confirm',
    'order:agent-create', 'order:split', 'order:ship', 'order:close',
  ] },
  { label: '定制', codes: ['custom:review', 'custom:produce', 'custom:upload'] },
  { label: '客户与会员', codes: [
    'customer:read', 'customer:audit', 'customer:transfer', 'customer:grant', 'customer:adjust',
    'member:config', 'member:grant', 'member:adjust',
  ] },
  { label: '客服', codes: [
    'chat:serve', 'chat:presence', 'chat:assign', 'chat:transfer', 'chat:close',
    'chat:create', 'chat:send',
  ] },
  { label: '财务', codes: [
    'finance:read', 'finance:payment', 'finance:record', 'finance:writeoff', 'finance:reverse',
    'finance:credit', 'finance:invoice', 'finance:contract',
  ] },
  { label: '售后', codes: ['refund:audit', 'refund:apply', 'refund:appeal'] },
  { label: '系统与报表', codes: [
    'system:staff', 'system:role', 'system:config', 'system:log',
    'system:staff:manage', 'system:role:manage', 'system:param:manage',
    'system:log:view', 'system:report:view',
    'report:sales', 'report:stock', 'confirm:token:issue',
  ] },
];

/** 扁平全集，用于全选 / 清空 */
export const ALL_PERMISSIONS: string[] = PERMISSION_GROUPS.flatMap((group) => group.codes);
