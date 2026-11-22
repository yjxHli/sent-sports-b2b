<template>
  <div class="cs-shell">
    <!-- 顶栏 60px：品牌 + 坐席身份 + 在线状态开关 + 退出，业务区完全让给三栏 -->
    <header class="cs-topbar">
      <div class="cs-topbar__left">
        <span class="cs-topbar__logo">S</span>
        <div class="cs-topbar__titles">
          <!-- 用 h1 而不是纯 <b>：工作台需要唯一的一级标题（可访问性 + E2E 用 heading 定位页面） -->
          <h1 class="cs-topbar__title">森特客服工作台</h1>
          <span class="cs-topbar__sub">Service Desk</span>
        </div>
      </div>

      <div class="cs-topbar__right">
        <!-- 在线 / 忙碌 / 离线：坐席状态是「接单开关」，放在最外层而不是藏在列表里 -->
        <div class="cs-presence" role="group" aria-label="坐席在线状态">
          <button
            v-for="option in PRESENCE_OPTIONS"
            :key="option.value"
            type="button"
            class="cs-presence__btn"
            :class="[
              `cs-presence__btn--${option.tone}`,
              { 'is-active': presenceStore.presence === option.value },
            ]"
            :disabled="presenceStore.saving"
            :data-testid="`presence-${option.value.toLowerCase()}`"
            @click="changePresence(option.value)"
          >
            <i class="cs-presence__dot" />
            {{ option.label }}
          </button>
        </div>

        <div class="cs-topbar__divider" aria-hidden="true" />

        <div class="cs-user" :title="`${permission.displayName}（${permission.me?.username || '--'}）`">
          <span class="cs-user__avatar">{{ permission.initial }}</span>
          <div class="cs-user__meta">
            <b class="cs-user__name">{{ permission.displayName }}</b>
            <span class="cs-user__role">{{ permission.me?.roleCode || 'CUSTOMER_SERVICE' }}</span>
          </div>
        </div>

        <button class="cs-logout" type="button" @click="onLogout">退出登录</button>
      </div>
    </header>

    <!-- 业务区：高度 = 视口 - 60px，交给内部三栏自行滚动，页面本身永不出现整页滚动条 -->
    <main class="cs-body">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
/**
 * 客服工作台外壳布局。
 *
 * 只有一条约束需要死守：顶栏 60px 固定，业务区吃满剩余高度（100vh - 60px），
 * 且 `.cs-body` 自己 overflow: hidden——滚动交给三栏内部的列表/消息区。
 * 否则窗口一小，整页会出现两条滚动条，聊天输入框被推出可视区域。
 */
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { PresenceStatus } from '@sent/shared';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { usePermissionStore } from '@/store/modules/permission';
import { presenceLabel, usePresenceStore } from '@/store/modules/presence';

const router = useRouter();
const auth = useAuthStore();
const permission = usePermissionStore();
const presenceStore = usePresenceStore();

const PRESENCE_OPTIONS = [
  { value: PresenceStatus.ONLINE, label: '在线', tone: 'online' },
  { value: PresenceStatus.BUSY, label: '忙碌', tone: 'busy' },
  { value: PresenceStatus.OFFLINE, label: '离线', tone: 'offline' },
] as const;

onMounted(async () => {
  // 刷新页面（没走登录流程）时 store 里没有 me，这里补一次；
  // 失败不跳转：守卫已经放过，跳转交给下一次路由或接口 40103。
  if (!permission.loaded) {
    try {
      await permission.load();
    } catch {
      /* ignore：顶栏降级显示默认昵称 */
    }
  }
});

async function changePresence(next: string): Promise<void> {
  if (presenceStore.presence === next || presenceStore.saving) return;
  try {
    const released = await presenceStore.setPresence(next);
    if (released > 0) {
      ElMessage.warning(`已切换为${presenceLabel(next)}，${released} 个会话已释放回排队池`);
    } else {
      ElMessage.success(`在线状态已切换为${presenceLabel(next)}`);
    }
  } catch (cause) {
    ElMessage.error((cause as Error)?.message || '状态切换失败');
  }
}

async function onLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('退出后当前会话将不再接收新客户进线，确认退出？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    });
  } catch {
    return; // 用户取消
  }
  // 离线优先：先把坐席摘出分配池，再吊销令牌，避免退出瞬间还被塞进新会话
  try {
    await presenceStore.setPresence(PresenceStatus.OFFLINE);
  } catch {
    /* 状态上报失败不阻塞退出 */
  }
  await auth.logout();
  await router.replace('/login'); // location.replace 未生效时的兜底
}
</script>

<style scoped>
.cs-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f4f7f5;
  overflow: hidden;
}

/* ---------- 顶栏 ---------- */
.cs-topbar {
  flex: none;
  height: var(--cs-topbar-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 18px;
  background: linear-gradient(100deg, var(--cs-shell-2), var(--cs-shell) 62%, #123029);
  border-bottom: 1px solid var(--cs-shell-line);
  color: var(--cs-shell-text);
  box-sizing: border-box;
}

.cs-topbar__left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.cs-topbar__logo {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(160deg, #1b8a6b, var(--sent-primary));
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  flex: none;
}
.cs-topbar__titles {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.cs-topbar__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.4px;
  white-space: nowrap;
  line-height: 1.2;
}
.cs-topbar__sub {
  font-size: 11px;
  color: var(--cs-shell-text-dim);
  letter-spacing: 1.2px;
  text-transform: uppercase;
}

.cs-topbar__right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: none;
}
.cs-topbar__divider {
  width: 1px;
  height: 22px;
  background: var(--cs-shell-line);
}

/* ---------- 状态开关 ---------- */
.cs-presence {
  display: inline-flex;
  padding: 3px;
  gap: 2px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.cs-presence__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 12px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--cs-shell-text-dim);
  font-size: 12.5px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.cs-presence__btn:hover:not(:disabled) {
  color: var(--cs-shell-text);
  background: rgba(255, 255, 255, 0.07);
}
.cs-presence__btn:disabled {
  cursor: progress;
  opacity: 0.75;
}
.cs-presence__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7b8c87;
  transition: background 0.15s ease, box-shadow 0.15s ease;
}
.cs-presence__btn--online .cs-presence__dot { background: #3ddc97; }
.cs-presence__btn--busy .cs-presence__dot { background: #ffb020; }
.cs-presence__btn--offline .cs-presence__dot { background: #8fa3a0; }

.cs-presence__btn.is-active {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-weight: 600;
}
.cs-presence__btn--online.is-active {
  background: rgba(61, 220, 151, 0.18);
  color: #a9f3cd;
}
.cs-presence__btn--busy.is-active {
  background: rgba(255, 176, 32, 0.18);
  color: #ffd58a;
}
.cs-presence__btn--offline.is-active {
  background: rgba(143, 163, 160, 0.2);
  color: #d3dfdb;
}

/* ---------- 坐席身份 ---------- */
.cs-user {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.cs-user__avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.16);
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  flex: none;
}
.cs-user__meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}
.cs-user__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--cs-shell-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cs-user__role {
  font-size: 10.5px;
  color: var(--cs-shell-text-dim);
  letter-spacing: 0.4px;
}

.cs-logout {
  height: 30px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: transparent;
  color: var(--cs-shell-text);
  font-size: 12.5px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.cs-logout:hover {
  background: rgba(255, 90, 90, 0.14);
  border-color: rgba(255, 90, 90, 0.45);
  color: #ffc9c9;
}

/* ---------- 业务区 ---------- */
.cs-body {
  flex: 1;
  min-height: 0; /* flex 子项默认 min-height:auto，不置 0 会导致内部滚动失效 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: calc(100vh - var(--cs-topbar-h));
}
</style>
