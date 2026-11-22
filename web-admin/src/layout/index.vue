<template>
  <el-container class="sent-layout">
    <el-aside width="248px" class="sent-aside">
      <div class="sent-brand">
        <span class="sent-brand__mark" aria-hidden="true">森</span>
        <span class="sent-brand__text">
          <strong class="sent-brand__name">森特体育</strong>
          <span class="sent-brand__role">管理端</span>
        </span>
      </div>

      <!-- 当前操作员：参照的排版里，侧栏顶部是一块「身份/组织」卡，
           让人随时知道自己以什么身份、什么数据范围在看这套数据。 -->
      <div class="sent-identity">
        <span class="sent-identity__avatar">{{ initial }}</span>
        <span class="sent-identity__meta">
          <strong class="sent-identity__name">{{ me?.name || '管理员' }}</strong>
          <span class="sent-identity__scope">数据范围 · {{ scopeLabel }}</span>
        </span>
      </div>

      <!--
        纯文本导航（去掉「方块 + 汉字」的占位图标）：汉字不是图标，且「审」在两个菜单上重复。
        当前路由所属分组默认展开，避免每次两次点击。
      -->
      <el-menu
        :key="openGroups.join('|')"
        :default-active="route.path"
        :default-openeds="openGroups"
        router
        unique-opened
        class="sent-menu"
      >
        <template v-for="group in menus" :key="group.path">
          <el-sub-menu v-if="group.children?.length" :index="group.path">
            <template #title>{{ group.title }}</template>
            <el-menu-item v-for="item in group.children" :key="item.path" :index="item.path">
              {{ item.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="group.path">
            {{ group.title }}
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="sent-header">
        <!-- 面包屑：分组是一枚浅色 pill，当前页是正文色文字；页面标题由内容区 h2 承担 -->
        <nav class="sent-crumb" aria-label="面包屑">
          <router-link class="sent-crumb__root" to="/dashboard">森特体育</router-link>
          <template v-if="crumbGroup">
            <span class="sent-crumb__sep" aria-hidden="true">/</span>
            <span class="sent-crumb__group">{{ crumbGroup }}</span>
          </template>
          <span class="sent-crumb__sep" aria-hidden="true">/</span>
          <span class="sent-crumb__current" aria-current="page">{{ crumbTitle }}</span>
        </nav>

        <div class="sent-user">
          <el-button link class="sent-logout" @click="onLogout">退出登录</el-button>
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

/** 面包屑：分组（若有）+ 当前页名；详情页同样取 meta 上的 title / group */
const crumbTitle = computed(() => (route.meta.title as string) || '工作台');
const crumbGroup = computed(() => (route.meta.group as string) || '');

/** 侧栏身份卡 */
const initial = computed(() => (me.value?.name || '管').trim().slice(0, 1));
const scopeLabel = computed(() => {
  const map: Record<string, string> = { ALL: '全部', TEAM: '本组', SELF: '本人' };
  return map[me.value?.dataScope ?? 'ALL'] ?? '全部';
});

/** 当前路由所属分组默认展开（详情页的 meta.group 也在，所以同样能定位） */
const openGroups = computed<string[]>(() => {
  const group = route.meta.group as string | undefined;
  return group ? [`group_${group}`] : [];
});

async function onLogout(): Promise<void> {
  permission.reset();
  await auth.logout();
  await router.replace('/login'); // location.replace 未生效时的兜底
}
</script>

<style scoped>
.sent-layout {
  height: 100vh;
}

/* ---------- 侧栏：248px，品牌块 + 身份卡 + 分组导航 ---------- */
.sent-aside {
  background: #0f4738;
  color: #eaf5f0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
}

.sent-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 18px;
  flex: none;
}
.sent-brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(255, 255, 255, 0.12);
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0;
}
.sent-brand__text {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}
.sent-brand__name {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #fff;
}
.sent-brand__role {
  font-size: 12px;
  color: rgba(234, 245, 240, 0.62);
}

/* 身份卡：与侧栏底色的 1 级表面差 + 发丝边 */
.sent-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 12px 12px;
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  flex: none;
}
.sent-identity__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(126, 211, 182, 0.22);
  font-size: 13px;
  font-weight: 500;
  color: #d8f0e7;
}
.sent-identity__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.sent-identity__name {
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sent-identity__scope {
  font-size: 12px;
  color: rgba(234, 245, 240, 0.62);
}

.sent-menu {
  border-right: none;
  background: transparent;
  padding: 0 0 16px;
  flex: 1 1 auto;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: rgba(234, 245, 240, 0.74);
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.06);
  --el-menu-active-color: #ffffff;
  --el-menu-item-height: 36px;
  --el-menu-sub-item-height: 36px;
}

/* 分组标题：13px、比条目更弱、不压条目；上方留白大于组内间距 */
.sent-menu :deep(.el-sub-menu__title) {
  height: 34px;
  line-height: 34px;
  margin: 18px 10px 4px;
  padding-left: 10px !important;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.01em;
  color: rgba(234, 245, 240, 0.66);
}
.sent-menu :deep(.el-sub-menu__title:hover) {
  background: transparent;
  color: rgba(234, 245, 240, 0.8);
}
.sent-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: rgba(234, 245, 240, 0.8);
}

/* 条目：36 高、10px 内缩、圆角 8；选中是一块品牌绿调填充，不再是白字加粗 */
.sent-menu :deep(.el-menu-item) {
  height: 38px;
  line-height: 38px;
  margin: 2px 10px;
  padding-left: 10px !important;
  padding-right: 10px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 400;
}
.sent-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}
.sent-menu :deep(.el-menu-item.is-active) {
  background: rgba(126, 211, 182, 0.18);
  color: #fff;
  font-weight: 500;
}
.sent-menu :deep(.el-sub-menu .el-menu-item) {
  padding-left: 22px !important;
  min-width: 0;
}

/* ---------- 顶栏：56px，面包屑 + 退出 ---------- */
.sent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 24px;
  background: var(--sent-surface);
  border-bottom: 1px solid var(--sent-line);
}

.sent-crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 13px;
  line-height: 1;
}
.sent-crumb__root {
  color: var(--sent-ink-3);
  transition: color var(--sent-ease);
}
.sent-crumb__root:hover {
  color: var(--sent-primary);
}
.sent-crumb__sep {
  color: #c3ccc6;
}
/* 分组做成浅色 pill，是这一版排版里的"层级标签" */
.sent-crumb__group {
  padding: 3px 8px;
  border: 1px solid var(--sent-line);
  border-radius: 6px;
  background: var(--sent-fill);
  color: var(--sent-ink-2);
  font-size: 12px;
}
.sent-crumb__current {
  color: var(--sent-ink);
  font-weight: 500;
  font-size: 14px;
}

.sent-user {
  display: flex;
  align-items: center;
  gap: 12px;
}
.sent-logout {
  font-size: 14px;
  color: var(--sent-ink-3);
}
.sent-logout:hover {
  color: var(--sent-danger);
}

/* ---------- 内容区 ---------- */
.sent-main {
  background: var(--sent-bg);
  padding: var(--sent-page-pad);
  overflow-y: auto;
}
</style>
