/**
 * 站内信未读数 Store（2026-11-16）。
 *
 * 为什么要有这个 store：未读红点出现在**顶栏**（Layout），而「标记已读」发生在**消息页**。
 * 以前两边各存一份 `unreadCount`，消息页点「全部已读」只改自己的数字，顶栏红点要等到
 * 路由切走（`watch(route.fullPath)`）或 60s 轮询才更新——用户看到的就是「已读了但红点还在」。
 *
 * 现在把未读数收口到一处：
 * - 顶栏与消息页都读 `unreadCount`；
 * - 任何会改变未读数的动作（单条已读 / 全部已读 / 删除单条 / 一键清空）都 `await refresh()`，
 *   两个位置同时变化，无需等轮询。
 *
 * 未登录时恒为 0，且不发请求（未登录也没有「我的站内信」）。
 */
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { messageApi } from '@/api/message';
import { unwrap } from '@/utils/http';

export const useMessageStore = defineStore('sent-user-message', () => {
  const unread = ref(0);
  /** 防止并发 refresh 互相覆盖（同一时刻多处触发时只让最后一次生效） */
  let inFlight = 0;

  const unreadCount = computed(() => unread.value);

  /** 拉一次未读数；失败静默归零（红点错一个数字不值得弹错误提示）。 */
  async function refresh(): Promise<number> {
    const seq = ++inFlight;
    try {
      const res = await unwrap(messageApi.unreadCount());
      const next = Number(res?.unreadCount ?? 0);
      if (seq === inFlight) unread.value = Number.isFinite(next) && next > 0 ? next : 0;
      return unread.value;
    } catch {
      if (seq === inFlight) unread.value = 0;
      return 0;
    }
  }

  /** 本地直接扣减（单条已读时用）：比再发一次请求更快，随后仍以 refresh 为准。 */
  function decrease(step = 1): void {
    unread.value = Math.max(0, unread.value - Math.max(0, step));
  }

  /** 全部已读 / 清空后本地清零，避免红点闪一下旧数字。 */
  function reset(): void {
    unread.value = 0;
  }

  return { unreadCount, refresh, decrease, reset };
});
