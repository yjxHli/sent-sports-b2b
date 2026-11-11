<template>
  <el-container class="sent-layout">
    <el-aside width="224px" class="sent-aside">
      <div class="sent-brand">森特体育<span>管理端</span></div>
      <el-menu :default-active="route.path" router unique-opened class="sent-menu">
        <template v-for="group in menus" :key="group.path">
          <el-sub-menu v-if="group.children?.length" :index="group.path">
            <template #title>{{ group.title }}</template>
            <el-menu-item v-for="item in group.children" :key="item.path" :index="item.path">
              <span class="sent-icon">{{ item.icon || '·' }}</span>{{ item.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="group.path">
            <span class="sent-icon">{{ group.icon || '·' }}</span>{{ group.title }}
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="sent-header">
        <div class="sent-crumb">{{ route.meta.title || '森特体育' }}</div>
        <div class="sent-user">
          <span class="sent-username">{{ me?.name || '管理员' }}</span>
          <el-button link type="primary" @click="onLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="sent-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { usePermissionStore } from '@/store/modules/permission';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const permission = usePermissionStore();

const menus = computed(() => permission.menus);
const me = computed(() => permission.me);

async function onLogout(): Promise<void> {
  permission.reset();
  await auth.logout();
  await router.replace('/login'); // location.replace 未生效时的兜底
}
</script>

<style scoped>
.sent-layout { height: 100vh; }
.sent-aside {
  background: #0f4738;
  color: #eaf5f0;
  overflow-y: auto;
}
.sent-brand {
  height: 60px;
  line-height: 60px;
  padding-left: 20px;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}
.sent-brand span {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  opacity: 0.7;
}
.sent-menu {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #cfe3da;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: #ffb77a;
}
.sent-icon {
  display: inline-block;
  width: 20px;
  height: 20px;
  line-height: 20px;
  margin-right: 8px;
  text-align: center;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 11px;
}
.sent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8eeeb;
}
.sent-crumb { font-size: 15px; font-weight: 600; color: #1f2d28; }
.sent-user { display: flex; align-items: center; gap: 12px; }
.sent-username { font-size: 13px; color: #5c6b65; }
.sent-main { background: #f4f7f5; padding: 20px; }
</style>
