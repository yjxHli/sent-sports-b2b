<template>
  <div>
    <h2 class="sent-page-title">{{ isEdit ? '编辑商品' : '新增商品' }}</h2>

    <div class="sent-panel">
      <el-form :model="form" label-width="100px">
        <el-form-item label="商品名称">
          <el-input v-model="form.name" placeholder="请输入商品名称" style="max-width: 420px" />
        </el-form-item>
        <el-form-item label="所属分类">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 240px">
            <el-option
              v-for="c in flatCategories"
              :key="String(c.categoryId)"
              :label="c.name"
              :value="Number(c.categoryId)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品描述">
          <el-input v-model="form.description" type="textarea" :rows="3" style="max-width: 620px" />
        </el-form-item>
      </el-form>
    </div>

    <div class="sent-panel">
      <div class="sent-form-row">
        <strong>商品规格</strong>
        <el-button size="small" @click="addSku">新增规格</el-button>
        <span class="sent-hint">「最低折后价」来自已保存折扣，保存折扣后自动刷新</span>
      </div>
      <el-table :data="skuRows" border>
        <el-table-column label="规格编码" width="190">
          <template #default="{ row }"><el-input v-model="row.skuCode" size="small" /></template>
        </el-table-column>
        <el-table-column label="规格名称" min-width="160">
          <template #default="{ row }"><el-input v-model="row.spec" size="small" /></template>
        </el-table-column>
        <el-table-column label="售价（元）" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.retailYuan" size="small" :min="0" :precision="2" />
          </template>
        </el-table-column>
        <el-table-column label="最低折后价（元）" width="180">
          <template #default="{ row }">
            <span :class="row.ladderYuan === null ? 'no-discount' : 'has-discount'">
              {{ row.ladderYuan === null ? '未配置折扣' : `¥${row.ladderYuan.toFixed(2)}` }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="可用库存" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.stock" size="small" :min="0" :precision="0" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="skuRows.splice($index, 1)">删除该规格</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="尚无规格，点击「新增规格」添加" :image-size="60" /></template>
      </el-table>
    </div>

    <div class="sent-panel">
      <div class="sent-form-row">
        <strong>批量折扣</strong>
        <el-select
          v-model="ladderSkuId"
          filterable
          placeholder="选择规格"
          style="width: 260px"
          @change="onLadderSkuChange"
        >
          <el-option
            v-for="row in skuRowsWithId"
            :key="row.skuId"
            :label="`${row.skuCode || '待保存'} · ${row.spec || '未命名规格'}`"
            :value="row.skuId"
          />
        </el-select>
        <el-button size="small" @click="addTier">新增折扣档</el-button>
        <el-button
          size="small"
          type="primary"
          plain
          :disabled="!ladderSkuId"
          :loading="savingLadder"
          @click="saveLadder"
        >保存折扣</el-button>
        <el-button size="small" type="danger" plain :disabled="!ladderSkuId" @click="clearLadder">
          清空折扣
        </el-button>
      </div>
      <el-table :data="tiers" border>
        <el-table-column label="购买数量从" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.minQty" size="small" :min="1" />
          </template>
        </el-table-column>
        <el-table-column label="购买数量到" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.maxQty" size="small" :min="-1" />
          </template>
        </el-table-column>
        <el-table-column label="折扣（%）" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.rate" size="small" :min="1" :max="100" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="tiers.splice($index, 1)">删除该折扣档</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="尚无折扣档" :image-size="60" /></template>
      </el-table>
      <p class="sent-hint">
        折扣按售价的百分比填写（90 表示九折）；购买数量到 -1 表示不设上限。
        保存或清空后，商品规格与商品列表同步刷新（规格数量不随折扣变化）。
      </p>
    </div>

    <div class="sent-form-row">
      <el-button type="primary" :loading="saving" @click="save">保存商品</el-button>
      <el-button @click="router.back()">返回</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 商品编辑（P6-26）：基础信息 + 商品规格 + 批量折扣；商品/规格与折扣均真落库。 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { useIdempotencyKey } from '@/utils/idempotent';
import { productApi, type LadderTier, type SkuForm } from '@/api';
import type { CategoryVO } from '@sent/shared';

interface SkuRow {
  skuId: string;
  skuCode: string;
  spec: string;
  retailYuan: number;
  ladderYuan: number | null;
  stock: number;
}

const route = useRoute();
const router = useRouter();
const idem = useIdempotencyKey('PRODUCT_CREATE');
const isEdit = computed(() => Boolean(route.query.id));

const form = reactive({ name: '', categoryId: 0, description: '' });
const skuRows = ref<SkuRow[]>([]);
const skuRowsWithId = computed(() => skuRows.value.filter((row) => row.skuId));
const flatCategories = ref<CategoryVO[]>([]);
const ladderSkuId = ref('');
const tiers = ref<LadderTier[]>([]);
const saving = ref(false);
const savingLadder = ref(false);

function flatten(list: CategoryVO[]): CategoryVO[] {
  return list.flatMap((item) => [item, ...(item.children ? flatten(item.children) : [])]);
}

function addSku(): void {
  skuRows.value.push({ skuId: '', skuCode: '', spec: '', retailYuan: 0, ladderYuan: null, stock: 0 });
}

function addTier(): void {
  tiers.value.push({ minQty: 1, maxQty: 10, rate: 100 });
}

function toSkuForms(): SkuForm[] {
  return skuRows.value.map((row) => ({
    ...(row.skuId ? { skuId: Number(row.skuId) } : {}),
    skuCode: row.skuCode,
    spec: row.spec,
    categoryId: form.categoryId,
    retailPriceCent: Math.round(row.retailYuan * 100),
    stock: row.stock,
  }));
}

/** 详情来自公开目录接口：零售价在 sku.price.retailPriceCent，折扣最低价在 sku.price.ladderPriceCent。 */
async function loadDetail(id: string): Promise<void> {
  const detail = await unwrap(productApi.detail(id));
  form.name = detail?.name ?? '';
  form.categoryId = Number(detail?.categoryId ?? 0);
  form.description = detail?.description ?? '';
  skuRows.value = (detail?.skus ?? []).map((sku) => ({
    skuId: String(sku.skuId ?? ''),
    skuCode: sku.skuCode ?? String(sku.skuId ?? ''),
    spec: sku.spec ?? '',
    retailYuan: Number(sku.price?.retailPriceCent ?? 0) / 100,
    ladderYuan: sku.price?.ladderPriceCent == null
      ? null
      : Number(sku.price.ladderPriceCent) / 100,
    stock: Number(sku.stockQty ?? 0),
  }));
}

async function loadLadder(skuId: number): Promise<void> {
  try {
    const vo = await unwrap(productApi.listLadderPrices(skuId));
    tiers.value = (vo?.tiers ?? []).map((tier) => ({
      minQty: tier.minQty,
      maxQty: tier.maxQty,
      rate: tier.rate,
    }));
  } catch (error) {
    tiers.value = [];
    ElMessage.error((error as Error)?.message || '折扣加载失败');
  }
}

function onLadderSkuChange(value: string): void {
  const skuId = Number(value);
  if (Number.isFinite(skuId) && skuId > 0) {
    void loadLadder(skuId);
  } else {
    tiers.value = [];
  }
}

async function save(): Promise<void> {
  if (!form.name.trim()) {
    ElMessage.warning('请填写商品名称');
    return;
  }
  if (!form.categoryId) {
    ElMessage.warning('请选择分类');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      name: form.name,
      categoryId: form.categoryId,
      description: form.description,
      skus: toSkuForms(),
    };
    if (isEdit.value) {
      await unwrap(productApi.update(Number(route.query.id), payload));
    } else {
      await unwrap(productApi.create(payload, { headers: idem.headers() }));
      idem.clear(); // 成功后换键，避免下次新建被判重放
    }
    ElMessage.success('保存成功');
    void router.push('/product/list');
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function saveLadder(): Promise<void> {
  const skuId = Number(ladderSkuId.value);
  if (!Number.isFinite(skuId) || skuId <= 0) {
    ElMessage.warning('请先选择一个规格');
    return;
  }
  savingLadder.value = true;
  try {
    await unwrap(productApi.configLadder(skuId, { skuId, tiers: tiers.value }));
    ElMessage.success('折扣已保存');
    if (isEdit.value) await loadDetail(String(route.query.id));
    await loadLadder(skuId);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '折扣保存失败');
  } finally {
    savingLadder.value = false;
  }
}

async function clearLadder(): Promise<void> {
  const skuId = Number(ladderSkuId.value);
  if (!Number.isFinite(skuId) || skuId <= 0) {
    ElMessage.warning('请先选择一个规格');
    return;
  }
  savingLadder.value = true;
  try {
    await unwrap(productApi.configLadder(skuId, { skuId, tiers: [] }));
    tiers.value = [];
    ElMessage.success('折扣已清空');
    if (isEdit.value) await loadDetail(String(route.query.id));
  } catch (error) {
    ElMessage.error((error as Error)?.message || '折扣清空失败');
  } finally {
    savingLadder.value = false;
  }
}

onMounted(async () => {
  try {
    flatCategories.value = flatten(await unwrap(productApi.categories()));
  } catch {
    flatCategories.value = [];
  }
  if (isEdit.value) {
    try {
      await loadDetail(String(route.query.id));
      const first = skuRowsWithId.value[0];
      if (first) {
        ladderSkuId.value = first.skuId;
        await loadLadder(Number(first.skuId));
      }
    } catch (error) {
      ElMessage.error((error as Error)?.message || '商品详情加载失败');
    }
  }
});
</script>

<style scoped>
.has-discount { color: #12664f; font-weight: 600; }
.no-discount { color: #909399; }
</style>
