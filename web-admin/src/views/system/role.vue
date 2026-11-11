<template>
  <div>
    <h2 class="sent-page-title">角色权限矩阵</h2>
    <el-row :gutter="16">
      <el-col :xs="24" :md="7">
        <div class="sent-panel">
          <h3 class="section-title">角色</h3>
          <el-table
            :data="roles"
            border
            size="small"
            highlight-current-row
            v-loading="loading"
            @current-change="selectRole"
          >
            <el-table-column prop="roleCode" label="编码" min-width="120" />
            <el-table-column prop="roleName" label="名称" min-width="120" />
            <el-table-column label="权限数" width="90" align="center">
              <template #default="scope">{{ scope.row.permCodes?.length ?? 0 }}</template>
            </el-table-column>
            <template #empty><el-empty description="暂无角色" :image-size="50" /></template>
          </el-table>
        </div>
      </el-col>

      <el-col :xs="24" :md="17">
        <div class="sent-panel">
          <div class="sent-form-row">
            <h3 class="section-title">
              权限配置
              <span v-if="current" class="sent-hint">{{ current.roleName }}（{{ current.roleCode }}）</span>
            </h3>
            <el-button size="small" :disabled="!current" @click="selectAll">全选</el-button>
            <el-button size="small" :disabled="!current" @click="clearAll">清空</el-button>
            <el-button type="primary" size="small" :disabled="!current" :loading="submitting" @click="save">
              保存
            </el-button>
          </div>

          <el-empty v-if="!current" description="请从左侧选择角色" :image-size="70" />
          <div v-else class="perm-groups">
            <div v-for="group in PERMISSION_GROUPS" :key="group.label" class="perm-group">
              <div class="perm-group-title">
                <strong>{{ group.label }}</strong>
                <el-checkbox
                  :model-value="isGroupChecked(group)"
                  :indeterminate="isGroupIndeterminate(group)"
                  @change="(v: boolean) => toggleGroup(group, v)"
                >
                  全选本组
                </el-checkbox>
              </div>
              <el-checkbox-group v-model="checked" class="perm-items">
                <el-checkbox v-for="code in group.codes" :key="code" :value="code" :label="code">
                  {{ code }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
          <p class="sent-hint">
            权限码目录来自 server 侧 @RequirePerm 注解与 MENU_ROUTES.meta.perm（constants/permissions.ts）；
            后端仍对每个写端点二次校验，前端只负责配置体验。
          </p>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/** 角色权限矩阵（P6-49）：按业务域分组勾选，权限码目录与后端注解同源。 */
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { systemApi } from '@/api';
import type { RoleVO } from '@sent/shared';
import { ALL_PERMISSIONS, PERMISSION_GROUPS, type PermissionGroup } from '@/constants/permissions';

const roles = ref<RoleVO[]>([]);
const current = ref<RoleVO | null>(null);
const checked = ref<string[]>([]);
const loading = ref(false);
const submitting = ref(false);

async function load(): Promise<void> {
  loading.value = true;
  try {
    roles.value = await unwrap(systemApi.roles());
  } catch (error) {
    roles.value = [];
    ElMessage.error((error as Error)?.message || '角色列表加载失败');
  } finally {
    loading.value = false;
  }
}

function selectRole(row: RoleVO | null): void {
  if (!row) return;
  current.value = row;
  checked.value = [...(row.permCodes ?? [])];
}

function selectAll(): void { checked.value = [...ALL_PERMISSIONS]; }
function clearAll(): void { checked.value = []; }

function isGroupChecked(group: PermissionGroup): boolean {
  return group.codes.every((code) => checked.value.includes(code));
}
function isGroupIndeterminate(group: PermissionGroup): boolean {
  const hit = group.codes.filter((code) => checked.value.includes(code)).length;
  return hit > 0 && hit < group.codes.length;
}
function toggleGroup(group: PermissionGroup, on: boolean): void {
  const set = new Set(checked.value);
  for (const code of group.codes) {
    if (on) set.add(code);
    else set.delete(code);
  }
  checked.value = [...set];
}

async function save(): Promise<void> {
  if (!current.value) return;
  submitting.value = true;
  try {
    await unwrap(systemApi.updateRolePerms(current.value.roleCode, {
      roleCode: current.value.roleCode,
      permCodes: [...checked.value],
    }));
    ElMessage.success('权限已保存');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    submitting.value = false;
  }
}

onMounted(() => { void load(); });
</script>

<style scoped>
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
.perm-groups { margin-top: 8px; }
.perm-group { border: 1px solid #e6ece9; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; }
.perm-group-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.perm-items { display: flex; flex-wrap: wrap; gap: 6px 18px; }
</style>
