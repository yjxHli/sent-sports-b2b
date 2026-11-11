<template>
  <div class="cat-page">
    <div class="cat-head">
      <div>
        <h2 class="sent-page-title">分类管理</h2>
        <p class="sent-hint cat-sub">
          两级分类结构总览：一级分类下挂二级分类，商品归属到具体分类后，前台目录与后台筛选都按此归类。
        </p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="cat-stats">
      <div class="cat-stat">
        <span class="cat-stat-label">一级分类</span>
        <strong class="cat-stat-value">{{ categories.length }}</strong>
        <span class="cat-stat-foot">个</span>
      </div>
      <div class="cat-stat">
        <span class="cat-stat-label">二级分类</span>
        <strong class="cat-stat-value">{{ secondLevelCount }}</strong>
        <span class="cat-stat-foot">个</span>
      </div>
      <div class="cat-stat">
        <span class="cat-stat-label">已归类商品</span>
        <strong class="cat-stat-value cat-stat-value--accent">{{ classifiedCount }}</strong>
        <span class="cat-stat-foot">件</span>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="10">
        <div class="sent-panel cat-tree-panel">
          <div class="cat-tree-head">
            <el-input
              v-model="keyword"
              placeholder="搜索分类名称"
              clearable
              :prefix-icon="Search"
            />
          </div>
          <el-tree
            ref="treeRef"
            :data="treeData"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            :props="{ label: 'label', children: 'children' }"
            :filter-node-method="filterNode"
            @node-click="onSelect"
          >
            <template #default="{ data }">
              <div class="cat-node" :class="{ 'cat-node--leaf': !data.children?.length }">
                <span class="cat-node-dot" />
                <span class="cat-node-label">{{ data.label }}</span>
                <span class="cat-node-meta">
                  <template v-if="data.childCount">{{ data.childCount }} 子类</template>
                  {{ data.productTotal }} 商品
                </span>
              </div>
            </template>
          </el-tree>
          <el-empty
            v-if="!treeData.length && !loading"
            description="暂无分类数据"
            :image-size="64"
          />
        </div>
      </el-col>

      <el-col :xs="24" :md="14">
        <div class="sent-panel cat-detail">
          <template v-if="current">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item>全部分类</el-breadcrumb-item>
              <el-breadcrumb-item v-if="current.parentName">{{ current.parentName }}</el-breadcrumb-item>
              <el-breadcrumb-item>{{ current.label }}</el-breadcrumb-item>
            </el-breadcrumb>

            <div class="cat-title-row">
              <h3 class="cat-title">{{ current.label }}</h3>
              <el-tag size="small" effect="plain" :type="current.parentId ? 'info' : 'primary'">
                {{ current.parentId ? '二级分类' : '一级分类' }}
              </el-tag>
            </div>

            <div class="cat-metrics">
              <div class="cat-metric">
                <span class="cat-metric-label">分类 ID</span>
                <span class="cat-metric-value">#{{ current.id }}</span>
              </div>
              <div class="cat-metric">
                <span class="cat-metric-label">直接商品</span>
                <span class="cat-metric-value">{{ current.productCount }} 件</span>
              </div>
              <div class="cat-metric">
                <span class="cat-metric-label">含子类商品</span>
                <span class="cat-metric-value">{{ current.productTotal }} 件</span>
              </div>
            </div>

            <div v-if="current.children?.length" class="cat-children">
              <p class="cat-section-title">子分类（{{ current.children.length }}）</p>
              <div class="cat-chip-row">
                <button
                  v-for="child in current.children"
                  :key="child.id"
                  class="cat-chip"
                  type="button"
                  @click="selectChild(child)"
                >
                  {{ child.label }}
                  <span class="cat-chip-count">{{ child.productTotal }}</span>
                </button>
              </div>
            </div>
            <div v-else class="cat-children">
              <p class="cat-section-title">子分类</p>
              <p class="sent-hint">
                这是一级分类的末级节点；如需细分，可在后台由管理员在分类表中新增二级分类。
              </p>
            </div>

            <div class="cat-actions">
              <el-button type="primary" @click="goProducts(current.id)">查看该分类商品</el-button>
              <el-button
                v-if="current.parentId"
                @click="selectParent(current.parentId)"
              >
                返回上级分类
              </el-button>
            </div>
          </template>
          <el-empty v-else description="从左侧选择一个分类查看详情" :image-size="70" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/**
 * 分类管理（重做）：分类结构总览 + 每个分类的商品覆盖情况。
 * 后端目前只提供只读分类接口（GET /api/categories），因此本页定位为「结构浏览 + 归类健康度」，
 * 不再提供刷新即丢失的本地假排序；商品跳转会把 categoryId 带到商品列表页。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import { unwrap } from '@/utils/http';
import { productApi } from '@/api';
import type { CategoryVO } from '@sent/shared';

interface TreeNode {
  id: string;
  label: string;
  parentId: string | null;
  parentName: string | null;
  productCount: number;
  productTotal: number;
  childCount: number;
  children?: TreeNode[];
  raw: CategoryVO;
}

const router = useRouter();
const treeRef = ref<any>(null);
const categories = ref<CategoryVO[]>([]);
const treeData = ref<TreeNode[]>([]);
const keyword = ref('');
const current = ref<TreeNode | null>(null);
const loading = ref(false);
const productCountByCategory = ref<Record<string, number>>({});

const secondLevelCount = computed(() =>
  categories.value.reduce((sum, c) => sum + (c.children?.length ?? 0), 0),
);
const classifiedCount = computed(() => {
  const counts = productCountByCategory.value;
  return Object.values(counts).reduce((sum, n) => sum + n, 0);
});

function buildTree(list: CategoryVO[], parentId: string | null, parentName: string | null): TreeNode[] {
  return list.map((item) => {
    const id = String(item.categoryId);
    const children = item.children?.length
      ? buildTree(item.children, id, item.name)
      : undefined;
    const own = productCountByCategory.value[id] ?? 0;
    const fromChildren = (children ?? []).reduce((sum, c) => sum + c.productTotal, 0);
    return {
      id,
      label: item.name,
      parentId,
      parentName,
      productCount: own,
      productTotal: own + fromChildren,
      childCount: children?.length ?? 0,
      children,
      raw: item,
    };
  });
}

function filterNode(value: string, data: TreeNode): boolean {
  if (!value) return true;
  return data.label.includes(value.trim());
}

watch(keyword, (value) => {
  treeRef.value?.filter(value);
});

function onSelect(node: TreeNode): void {
  current.value = node;
}

function selectChild(child: TreeNode): void {
  current.value = child;
  treeRef.value?.setCurrentKey(child.id);
}

function selectParent(parentId: string): void {
  const found = findNode(treeData.value, parentId);
  if (found) {
    current.value = found;
    treeRef.value?.setCurrentKey(found.id);
  }
}

function findNode(nodes: TreeNode[], id: string): TreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    const hit = node.children ? findNode(node.children, id) : null;
    if (hit) return hit;
  }
  return null;
}

function goProducts(categoryId: string): void {
  void router.push({ path: '/product/list', query: { categoryId } });
}

/** 商品归类统计：一次拉最多 100 条商品，按 categoryId 聚合；不足 100 条的库内数据完全覆盖。 */
async function loadProductCounts(): Promise<void> {
  const counts: Record<string, number> = {};
  try {
    const page = await unwrap(productApi.list({ page: 1, size: 100 }));
    for (const row of page?.list ?? []) {
      const id = row.categoryId == null ? '' : String(row.categoryId);
      if (!id) continue;
      counts[id] = (counts[id] ?? 0) + 1;
    }
  } catch {
    // 分类统计失败不影响结构展示
  }
  productCountByCategory.value = counts;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [list] = await Promise.all([unwrap(productApi.categories()), loadProductCounts()]);
    categories.value = list ?? [];
    treeData.value = buildTree(categories.value, null, null);
    if (current.value) {
      const again = findNode(treeData.value, current.value.id);
      current.value = again;
    }
  } catch (error) {
    categories.value = [];
    treeData.value = [];
    ElMessage.error((error as Error)?.message || '分类加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.cat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.cat-sub {
  margin: -8px 0 14px;
  max-width: 760px;
  line-height: 1.7;
}
.cat-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.cat-stat {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  align-items: baseline;
  gap: 6px;
  border-left: 3px solid #12664f;
}
.cat-stat-label { font-size: 13px; color: #6b7a74; flex: 1; }
.cat-stat-value { font-size: 24px; line-height: 1; color: #1f2d28; }
.cat-stat-value--accent { color: #ff7a1a; }
.cat-stat-foot { font-size: 12px; color: #9aa8a2; }

.cat-tree-panel { min-height: 360px; }
.cat-tree-head { margin-bottom: 10px; }

.cat-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding-right: 6px;
}
.cat-node-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #12664f;
  flex: none;
}
.cat-node--leaf .cat-node-dot { background: #b9c9c3; }
.cat-node-label { flex: 1; color: #1f2d28; }
.cat-node-meta {
  font-size: 12px;
  color: #9aa8a2;
  display: flex;
  gap: 8px;
}

.cat-detail { min-height: 360px; }
.cat-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0 16px;
}
.cat-title { margin: 0; font-size: 20px; color: #1f2d28; }

.cat-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}
.cat-metric {
  background: #f6faf8;
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cat-metric-label { font-size: 12px; color: #9aa8a2; }
.cat-metric-value { font-size: 15px; color: #1f2d28; font-weight: 600; }

.cat-section-title { margin: 0 0 10px; font-size: 13px; color: #6b7a74; }
.cat-children { margin-bottom: 18px; }
.cat-chip-row { display: flex; flex-wrap: wrap; gap: 8px; }
.cat-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #dfe8e4;
  background: #fff;
  color: #1f2d28;
  border-radius: 14px;
  padding: 5px 12px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.cat-chip:hover {
  border-color: #12664f;
  color: #12664f;
  background: #f2f9f6;
}
.cat-chip-count {
  font-size: 11px;
  color: #9aa8a2;
  background: #f0f4f2;
  border-radius: 8px;
  padding: 0 6px;
}
.cat-actions { display: flex; gap: 8px; flex-wrap: wrap; }

@media (max-width: 1100px) {
  .cat-stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
</style>
