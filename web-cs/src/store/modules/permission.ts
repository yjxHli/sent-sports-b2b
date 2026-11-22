/**
 * 客服端身份 Store（`/admin/me` 的唯一消费者）。
 *
 * 与管理端的 permission store 差异：**不做网络失败降级**。
 * 管理端是「后端异常先放行、由后端二次校验兜底」；客服台不行——
 * 拿不到 permissions 就无法判断是不是客服，此时放行等于把工作台交给任何人。
 * 因此这里失败即抛错，由调用方（登录页 / 路由守卫）清 Token 并退回登录。
 */
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { ErrorCode, type MeVO } from '@sent/shared';
import { http } from '@/utils/http';
import { canServeChat } from '@/utils/csAccess';

export const usePermissionStore = defineStore('cs-permission', () => {
  const me = ref<MeVO | null>(null);
  const permissions = ref<string[]>([]);
  /** 已成功拉过一次；force=true 时忽略并重拉 */
  const loaded = ref(false);

  const userId = computed(() => Number(me.value?.userId ?? 0));
  const displayName = computed(() => me.value?.name || me.value?.username || '客服');
  /** 头像用工号后两位生成文字头像，避免再引一份图片资源 */
  const initial = computed(() => {
    const name = me.value?.name?.trim();
    if (name) return name.slice(0, 1);
    return String(userId.value).slice(-2) || 'CS';
  });
  /** 是否客服人员（chat:serve）——布局与守卫共用这一处判定 */
  const isAgent = computed(() => canServeChat(permissions.value));

  /**
   * 拉取当前员工资料与权限。
   * @throws HttpError 或普通 Error：网络异常、令牌失效、业务码非 0 均抛出，绝不降级放行。
   */
  async function load(force = false): Promise<void> {
    if (loaded.value && !force) return;
    const res = await http.get<MeVO>('/admin/me');
    if (res.code !== ErrorCode.SUCCESS) {
      throw new Error(res.message || '无法获取当前账号权限');
    }
    const data = res.data;
    me.value = data ?? null;
    permissions.value = Array.isArray(data?.permissions) ? data.permissions : [];
    loaded.value = true;
  }

  function has(code?: string): boolean {
    if (!code) return true;
    return permissions.value.includes('*') || permissions.value.includes(code);
  }

  function reset(): void {
    me.value = null;
    permissions.value = [];
    loaded.value = false;
  }

  return { me, permissions, loaded, userId, displayName, initial, isAgent, load, has, reset };
});
