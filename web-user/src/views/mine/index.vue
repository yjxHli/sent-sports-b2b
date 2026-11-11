<template>
  <div>
    <div class="mine-hero">
      <div class="mine-hero__avatar">👤</div>
      <div>
        <div class="mine-hero__id">{{ profile?.nickname || `用户 #${userId ?? '未登录'}` }}</div>
        <div class="mine-hero__sub">{{ profile?.customerNo || '个人中心' }}</div>
      </div>
    </div>

    <div class="sent-card mine-stats">
      <div class="mine-stat">
        <span>会员等级</span>
        <strong>{{ profile ? statusLabel(profile.levelCode) : '--' }}</strong>
      </div>
      <div class="mine-stat">
        <span>成长值</span>
        <strong>{{ profile?.growthValue ?? '--' }}</strong>
      </div>
      <div class="mine-stat">
        <span>可用积分</span>
        <strong>{{ profile?.pointsBalance ?? '--' }}</strong>
      </div>
    </div>

    <div class="sent-card">
      <div
        v-for="m in menus"
        :key="m.path"
        class="mine-item"
        @click="go(m.path)"
      >
        <span>{{ m.icon }} {{ m.title }}</span>
        <span class="mine-item__arrow">›</span>
      </div>
    </div>

    <div class="mine-logout">
      <el-button type="danger" plain @click="logout">退出登录</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 个人中心（收口阶段）：资料 / 等级 / 积分来自后端真实端点 GET /api/me，不再只用本地 userId 占位。 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { statusLabel, type UserMeVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { profileApi } from '@/api/profile';
import { useAuthStore } from '@/store/modules/useAuthStore';

const router = useRouter();
const auth = useAuthStore();
const userId = computed(() => auth.userId);
const profile = ref<UserMeVO | null>(null);

const menus = [
  { title: '我的订单', path: '/orders', icon: '📦' },
  { title: '我的售后', path: '/refunds', icon: '↩️' },
  { title: '会员中心', path: '/member', icon: '⭐' },
  { title: '我的定制', path: '/custom', icon: '🎨' },
  { title: '在线客服', path: '/chat', icon: '💬' },
];

async function loadProfile(): Promise<void> {
  if (!userId.value) return;
  try {
    profile.value = await unwrap(profileApi.me());
  } catch {
    profile.value = null;
  }
}

function go(path: string): void {
  router.push(path);
}

async function logout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await auth.logout();
  router.replace('/login');
}

onMounted(() => { void loadProfile(); });
</script>

<style scoped>
.mine-hero {
  background: linear-gradient(135deg, #12664f, #1a8c6a);
  color: #fff;
  padding: 28px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.mine-hero__avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}
.mine-hero__id {
  font-size: 16px;
  font-weight: 600;
}
.mine-hero__sub {
  font-size: 12px;
  opacity: 0.8;
  margin-top: 2px;
}
.mine-stats {
  display: flex;
  justify-content: space-around;
  padding: 14px 4px;
}
.mine-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}
.mine-stat strong {
  font-size: 15px;
  color: #303133;
}
.mine-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 4px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
  cursor: pointer;
}
.mine-item:last-child {
  border-bottom: none;
}
.mine-item__arrow {
  color: #c0c4cc;
}
.mine-logout {
  padding: 16px;
}
</style>
