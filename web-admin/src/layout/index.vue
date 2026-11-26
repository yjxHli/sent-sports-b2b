<template>
  <el-container class="sent-layout">
    <el-aside width="248px" class="sent-aside">
      <div class="sent-brand">
        <img class="sent-brand__mark" :src="brandIcon" alt="" />
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
        导航内容（文字/路径/权限/展开策略）完全沿用 MENU_ROUTES，只是给每行补上 SVG 图标：
        图标 12px < 文字 14px，图文间距由 .nav-row 的 gap 统一控制，不再用汉字当占位图标
        （「审」曾在两个菜单上重复，且汉字不是图标）。
      -->
      <!--
        2026-11-26：取消手风琴（unique-opened）。多组可同时展开，跨模块切换效率更高。
        不绑定 :key —— 路由变化若重挂整张菜单，用户手动展开的其它组会被清掉；
        改为在路由变化时只「追加」展开当前组（见下方 watch(route.path)）。
      -->
      <el-menu
        ref="menuRef"
        :default-active="route.path"
        :default-openeds="initialOpenGroups"
        router
        class="sent-menu"
      >
        <template v-for="group in menus" :key="group.path">
          <el-sub-menu v-if="group.children?.length" :index="group.path">
            <template #title>
              <span class="nav-row">
                <el-icon class="nav-icon"><component :is="iconForGroup(group.title)" /></el-icon>
                <span class="nav-text">{{ group.title }}</span>
              </span>
            </template>
            <el-menu-item v-for="item in group.children" :key="item.path" :index="item.path">
              <span class="nav-row nav-row--sub">
                <el-icon class="nav-icon"><component :is="iconForGroup(group.title)" /></el-icon>
                <span class="nav-text">{{ item.title }}</span>
              </span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="group.path">
            <span class="nav-row">
              <el-icon class="nav-icon"><component :is="iconForRoot(group.title)" /></el-icon>
              <span class="nav-text">{{ group.title }}</span>
            </span>
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
import { computed, nextTick, ref, watch } from 'vue';
import type { Component } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Box, DataBoard, Document, Money, OfficeBuilding, Service, Setting, ShoppingCart,
  TakeawayBox, Ticket, Tools, TrendCharts, User,
} from '@element-plus/icons-vue';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { usePermissionStore } from '@/store/modules/permission';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const permission = usePermissionStore();
const brandIcon = `${import.meta.env.BASE_URL}sent-icon-192.png`;

const menus = computed(() => permission.menus);
const me = computed(() => permission.me);

/**
 * 图标映射只按「分组/一级模块」取，子项继承本组图标：
 * 既能给每行一致的视觉锚点，又不给 30+ 条菜单各配一个图标造成维护负担。
 * 新增分组没配图标时退回通用 Document，不会出现空白行。
 */
const NAV_ICONS: Record<string, Component> = {
  工作台: DataBoard,
  业务报表: TrendCharts,
  商品: Box,
  库存: TakeawayBox,
  订单: ShoppingCart,
  定制: Tools,
  客户与会员: User,
  // 2026-11-26 新分组：优惠券（营销）与企业（B 端）各自独立，避免混在客户菜单里
  优惠券: Ticket,
  企业: OfficeBuilding,
  客服: Service,
  财务: Money,
  系统: Setting,
};

function iconForRoot(title: string): Component {
  return NAV_ICONS[title] ?? Document;
}

function iconForGroup(title: string): Component {
  return NAV_ICONS[title] ?? Document;
}

/** 面包屑：分组（若有）+ 当前页名；详情页同样取 meta 上的 title / group */
const crumbTitle = computed(() => (route.meta.title as string) || '工作台');
const crumbGroup = computed(() => (route.meta.group as string) || '');

/** 侧栏身份卡 */
const initial = computed(() => (me.value?.name || '管').trim().slice(0, 1));
const scopeLabel = computed(() => {
  const map: Record<string, string> = { ALL: '全部', TEAM: '本组', SELF: '本人' };
  return map[me.value?.dataScope ?? 'ALL'] ?? '全部';
});

/** 当前路由所属分组的菜单 index（详情页的 meta.group 也在，所以同样能定位） */
function currentGroupIndex(): string | null {
  const group = route.meta.group as string | undefined;
  return group ? `group_${group}` : null;
}

/**
 * 首屏 / 刷新 / 外部跳转的默认展开组。
 * Element Plus 只在 el-menu 挂载时读一次 default-openeds；后续展开集合由组件自己维护，
 * 因此这里必须是「一次性快照」语义，不能拿它当受控状态去覆盖用户的手动展开。
 */
const initialOpenGroups = computed<string[]>(() => {
  const index = currentGroupIndex();
  return index ? [index] : [];
});

/** el-menu 暴露的 open()：只打开目标组，不影响其它已展开组 */
const menuRef = ref<{ open: (index: string) => void } | null>(null);

/**
 * 路由切换只追加展开当前组，不重置整张菜单。
 * 没有这一步就得靠 :key 重挂菜单，用户先展开「订单」再跳「客户」时订单组会被收起。
 */
watch(() => route.path, async () => {
  await nextTick();
  const index = currentGroupIndex();
  if (!index || !menus.value.some((item) => item.path === index)) return;
  menuRef.value?.open(index);
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
  /* 侧栏排版令牌（2026-11-25）：同级行共用同一套圆角/行高/留白/间距，
     改版式只动这里，不再在各选择器里散落 34/36/38px 和 8/10px 的魔法数。 */
  --nav-side: 10px;   /* 侧栏左右留白 */
  --nav-pad: 12px;    /* 行内左右内边距 */
  --nav-radius: 8px;  /* 同级面板圆角 */
  --nav-row: 40px;    /* 行高：文字 + 上下留白，点击区域 ≥40 */
  --nav-gap: 8px;     /* 图标与文字间距 */
  --nav-inner: 2px;   /* 组内间距 */
  --nav-outer: 18px;  /* 组间间距，明显大于组内 */
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
  display: block;
  width: 28px;
  height: 28px;
  object-fit: contain;
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
  /* 允许菜单在 flex 列里收缩，展开很多组时由 .sent-aside 自己出滚动条，不撑整页 */
  min-height: 0;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: rgba(234, 245, 240, 0.74);
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.06);
  --el-menu-active-color: #ffffff;
  --el-menu-item-height: var(--nav-row);
  --el-menu-sub-item-height: var(--nav-row);
}

/* 行内容器：图标固定 12px（略小于 14px 文字），图文间距统一由 gap 控制；
   子项缩进放在内容层，行本身仍左右等宽 —— 点击区域和两侧留白都不被层级破坏。 */
.nav-row {
  display: flex;
  align-items: center;
  gap: var(--nav-gap);
  width: 100%;
  min-width: 0;
  height: 100%;
}
.nav-row--sub {
  margin-left: 10px;
}
.nav-icon {
  flex: none;
  /* Element Plus 给菜单里的 .el-icon 留了额外宽度（实测 24px），
     不显式收成 12 会让「图文间距」变成 12+8=20px，和其它行对不上。 */
  width: 12px !important;
  height: 12px;
  margin-right: 0 !important;
  font-size: 12px;
  line-height: 1;
}
.nav-icon svg {
  width: 12px;
  height: 12px;
}
.nav-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 分组标题与条目：同一套 40 高 / 8 圆角 / 14 字号 / 10px 侧边距；
   配色与 hover、active 交互完全沿用原值，只统一几何。 */
.sent-menu :deep(.el-sub-menu__title) {
  height: var(--nav-row);
  line-height: var(--nav-row);
  margin: var(--nav-outer) var(--nav-side) var(--nav-inner);
  padding: 0 var(--nav-pad) !important;
  border-radius: var(--nav-radius);
  font-size: 14px;
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

/* 条目：40 高，圆角/字号/侧边距与分组标题一致；选中仍是同一块品牌绿填充 */
.sent-menu :deep(.el-menu-item) {
  height: var(--nav-row);
  line-height: var(--nav-row);
  margin: 0 var(--nav-side) var(--nav-inner);
  padding: 0 var(--nav-pad) !important;
  border-radius: var(--nav-radius);
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
