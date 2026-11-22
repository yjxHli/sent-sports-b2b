/**
 * 坐席在线状态 Store（布局顶栏开关与工作台共用一份）。
 *
 * 为什么抽成 store：顶栏在 60px 里放切换开关，工作台左侧还要显示「在线 · 自动接入」，
 * 两处各持一个 ref 必然不同步（顶栏切忙碌、左侧还写在线）。状态收在这里，
 * 两边只读同一个 ref；写操作统一走 setPresence()，前端不做乐观更新，
 * 以免后端拒绝（如离线释放会话失败）时界面显示与实际坐席状态不一致。
 */
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { PresenceStatus } from '@sent/shared';
import { chatApi } from '@/api/chat';
import { unwrap } from '@/utils/http';
import { usePermissionStore } from '@/store/modules/permission';

/** 三种状态的中文文案（顶栏与工作台共用，避免各写一份 if） */
export const PRESENCE_LABELS: Record<string, string> = {
  [PresenceStatus.ONLINE]: '在线',
  [PresenceStatus.BUSY]: '忙碌',
  [PresenceStatus.OFFLINE]: '离线',
};

export function presenceLabel(value?: string | null): string {
  return PRESENCE_LABELS[String(value ?? '')] ?? '离线';
}

export const usePresenceStore = defineStore('cs-presence', () => {
  /**
   * 默认离线（2026-11-21 用户要求）：
   * 客服登录进系统后不会自动接单，必须自己点「在线」才开始分配客户。
   * 之前的默认 ONLINE + 进工作台就上报，等于一登录就把人塞进接待池。
   */
  const presence = ref<string>(PresenceStatus.OFFLINE);
  const saving = ref(false);

  const permission = usePermissionStore();
  const staffId = computed(() => Number(permission.userId ?? 0) || 0);
  const label = computed(() => presenceLabel(presence.value));
  const isOnline = computed(() => presence.value === PresenceStatus.ONLINE);

  /**
   * 从后端同步当前坐席状态（刷新页面时保持原状，没有记录即离线）。
   *
   * <p>只读不写：登录时已经落库过离线，刷新后如果读到在线说明是客服自己切的，
   * 不能再用一个前端默认值把它覆盖回去。</p>
   */
  async function sync(): Promise<void> {
    const id = staffId.value;
    if (id <= 0) return;
    try {
      const list = await unwrap(chatApi.workload());
      const mine = (list ?? []).find((w) => Number(w.staffId) === id);
      presence.value = mine?.presence ? String(mine.presence) : PresenceStatus.OFFLINE;
    } catch {
      /* 读不到就保持当前值 */
    }
  }

  /**
   * 切换坐席状态。后端会把「离线」坐席名下的会话释放回排队池，
   * 因此调用方（布局）拿到 releasedSessions>0 时应提示客服，避免他以为会话凭空消失。
   */
  async function setPresence(next: string): Promise<number> {
    const id = staffId.value;
    if (id <= 0) throw new Error('无法识别当前客服工号，请重新登录');
    if (saving.value) return 0;
    saving.value = true;
    try {
      const result = await unwrap(chatApi.presence(id, next));
      presence.value = next;
      return Number(result?.releasedSessions ?? 0);
    } finally {
      saving.value = false;
    }
  }

  /**
   * 登录/退出时复位为「离线」并落到后端。
   *
   * <p>只改前端 ref 不够：后端仍认为该坐席在线的话，客户进线照样会被派给一个
   * 刚登录还没点「在线」的人。落库失败不阻塞登录（顶栏会如实显示离线）。</p>
   */
  async function reset(): Promise<void> {
    presence.value = PresenceStatus.OFFLINE;
    saving.value = false;
    const id = staffId.value;
    if (id <= 0) return;
    try {
      await unwrap(chatApi.presence(id, PresenceStatus.OFFLINE));
    } catch {
      /* 静默：状态上报失败不影响登录 */
    }
  }

  return { presence, saving, staffId, label, isOnline, sync, setPresence, reset };
});
