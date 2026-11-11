<template>
  <div>
    <div class="sent-bar"><strong>会员中心</strong></div>

    <div v-if="loading" class="detail-hint">加载中…</div>
    <template v-else>
      <div class="member-hero">
        <div class="member-hero__level">{{ level?.level || '普通会员' }}</div>
        <div class="member-hero__growth">成长值 {{ level?.growthValue ?? 0 }}</div>
        <el-progress
          :percentage="Math.round((level?.progress ?? 0) * 100)"
          :stroke-width="8"
          color="#ff7a1a"
        />
      </div>

      <div class="sent-card">
        <div class="row"><span>可用积分</span><span class="sent-price">{{ points?.availablePoints ?? 0 }}</span></div>
        <div class="row"><span>授信额度</span><span>¥{{ credit?.creditLimitYuan || '0.00' }}</span></div>
        <div class="row"><span>应收余额</span><span>¥{{ credit?.receivableYuan || '0.00' }}</span></div>
      </div>

      <div class="sent-card">
        <div class="sent-title">专属商品</div>
        <el-empty v-if="exclusive.length === 0" description="暂无专属商品" :image-size="80" />
        <div v-for="p in exclusive" :key="p.productId" class="ex-item">
          <span>{{ p.productName }}</span>
          <span class="sent-price">¥{{ p.priceYuan }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { unwrap } from '@/utils/http';
import {
  memberApi, type LevelVO, type PointVO, type CreditVO, type ExclusiveProductVO,
} from '@/api/member';

const loading = ref(false);
const level = ref<LevelVO | null>(null);
const points = ref<PointVO | null>(null);
const credit = ref<CreditVO | null>(null);
const exclusive = ref<ExclusiveProductVO[]>([]);

onMounted(async () => {
  loading.value = true;
  try {
    const [lv, pt, cr, ex] = await Promise.all([
      unwrap(memberApi.level()),
      unwrap(memberApi.points()),
      unwrap(memberApi.credit()),
      unwrap(memberApi.exclusiveProducts()),
    ]);
    level.value = lv ?? null;
    points.value = pt ?? null;
    credit.value = cr ?? null;
    exclusive.value = ex ?? [];
  } catch {
    level.value = null;
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.detail-hint {
  text-align: center;
  padding: 40px 0;
  color: #b5b9c0;
}
.member-hero {
  background: linear-gradient(135deg, #12664f, #1a8c6a);
  color: #fff;
  padding: 20px 16px;
  text-align: center;
}
.member-hero__level {
  font-size: 20px;
  font-weight: 700;
}
.member-hero__growth {
  font-size: 12px;
  opacity: 0.85;
  margin: 6px 0 12px;
}
.row {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: #333;
  padding: 6px 0;
}
.ex-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 6px 0;
  border-bottom: 1px dashed #eee;
}
</style>
