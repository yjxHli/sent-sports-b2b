/**
 * 权限 Store（P6-10）：按 permissions[] 过滤静态路由生成菜单。
 *
 * 明确不调 `/get-async-routes`：32 个页面是固定集合，后端下发组件路径既无法编译期检查，
 * 又会在后端被攻破时扩大攻击面；后端仍会对每个写端点做二次权限校验，前端只负责体验。
 */
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { ErrorCode, type MenuItem, type MeVO } from '@sent/shared';
import { http } from '@/utils/http';
import { MENU_ROUTES } from '@/router/modules/business';

export const usePermissionStore = defineStore('sent-admin-permission', () => {
  const permissions = ref<string[]>([]);
  const me = ref<MeVO | null>(null);
  const loaded = ref(false);
  /** 仅当 /admin/me 请求异常时降级：不拦截前端路由，由后端兜底二次校验 */
  const degraded = ref(false);

  /** 登录后拉取当前用户权限；force=true 用于登录成功后强制刷新 */
  async function load(force = false): Promise<void> {
    if (loaded.value && !force) return;
    loaded.value = true; // 先置位，避免并发守卫重复请求
    try {
      // 管理端当前员工端点（收口阶段补齐）：权限点与数据范围以此为真源
      const res = await http.get<MeVO>('/admin/me');
      if (res.code !== ErrorCode.SUCCESS) throw new Error(res.message);
      me.value = res.data;
      permissions.value = Array.isArray(res.data?.permissions) ? res.data.permissions : [];
      degraded.value = false;
    } catch {
      // 网络异常 / 服务未启动时才降级放行；后端对每个写端点仍做二次权限校验
      degraded.value = true;
      permissions.value = [];
    }
  }

  function has(code?: string): boolean {
    if (!code) return true;
    if (degraded.value) return true;
    return permissions.value.includes('*') || permissions.value.includes(code);
  }

  /** 侧边栏菜单：无 group 的为一级项，其余按 group 聚合成二级 */
  const menus = computed<MenuItem[]>(() => {
    const roots: MenuItem[] = [];
    const groups = new Map<string, MenuItem>();
    for (const entry of MENU_ROUTES) {
      if (entry.hidden || !has(entry.perm)) continue;
      const item: MenuItem = {
        path: entry.path, title: entry.title, icon: entry.icon, perm: entry.perm,
      };
      if (!entry.group) {
        roots.push(item);
        continue;
      }
      let group = groups.get(entry.group);
      if (!group) {
        group = { path: `group_${entry.group}`, title: entry.group, children: [] };
        groups.set(entry.group, group);
        roots.push(group);
      }
      group.children?.push(item);
    }
    return roots;
  });

  function reset(): void {
    permissions.value = [];
    me.value = null;
    loaded.value = false;
    degraded.value = false;
  }

  return { permissions, me, loaded, degraded, menus, load, has, reset };
});
