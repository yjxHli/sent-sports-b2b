<template>
  <div class="container help-category">
    <div class="help-category__crumb">
      <router-link to="/home">首页</router-link>
      <span>/</span>
      <span>帮助中心</span>
      <span>/</span>
      <span>{{ category?.title || '帮助中心' }}</span>
    </div>

    <template v-if="category">
      <div class="help-category__head">
        <span class="help-category__badge">帮助中心</span>
        <h1 class="help-category__title">{{ category.title }}</h1>
        <p class="help-category__lead">{{ category.lead }}</p>
      </div>

      <div class="help-category__body">
        <article v-for="(mod, i) in category.modules" :key="i" class="help-module">
          <div class="help-module__head">
            <i class="help-module__no">{{ String(i + 1).padStart(2, '0') }}</i>
            <h2 class="help-module__title">{{ mod.title }}</h2>
          </div>
          <div class="help-module__body">
            <p v-for="(para, j) in mod.paragraphs" :key="j">{{ para }}</p>
          </div>
        </article>
      </div>

      <div class="help-category__foot">
        <router-link to="/home" class="help-category__back">← 返回商城首页</router-link>
        <router-link to="/chat" class="help-category__service">仍有疑问？联系在线客服 →</router-link>
      </div>
    </template>

    <div v-else class="help-category__body">
      <div class="help-category__empty">
        <h1 class="help-category__title">帮助中心</h1>
        <p>该分类不存在，请从顶部「帮助中心」选择需要了解的内容。</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

interface Module {
  title: string;
  paragraphs: string[];
}
interface Category {
  title: string;
  lead: string;
  modules: Module[];
}

const route = useRoute();
const key = computed(() => String(route.params.category ?? ''));

const CATEGORIES: Record<string, Category> = {
  'shopping-guide': {
    title: '购物指南',
    lead: '从挑选商品到收货，了解森特体育商城的标准采购流程与会员权益。',
    modules: [
      {
        title: '购物流程',
        paragraphs: [
          '浏览商品或按分类、关键词检索，进入商品详情确认规格与价格后加入购物车；结算时选择收货地址、配送方式与支付方式，提交订单即完成下单。企业客户还可使用账期结算，先货后款。',
          '订单提交后按所选方式完成支付。发货后可在订单详情查看物流信息，签收时请核对商品数量与包装完好。',
        ],
      },
      {
        title: '会员介绍',
        paragraphs: [
          '会员等级由成长值决定，成长值随有效消费累计，达到下一档门槛后自动升级。不同等级对应会员专属价、积分加速、专属客服优先接入与定制订单优先排产等权益。',
          '可在会员中心查看当前等级、成长值与升级进度；人工授予的等级到期后会按成长值重新计算。',
        ],
      },
      {
        title: '常见问题',
        paragraphs: [
          '订单完成后可在订单详情申请开票，支持增值税普通发票与专用发票。未发货订单可联系在线客服申请修改或取消，已发货订单需按退换货政策处理。',
          '账期客户可在会员中心查看授信额度与应收余额，完成回款核销后应收款减少、额度同步释放。',
        ],
      },
    ],
  },
  delivery: {
    title: '配送方式',
    lead: '多种配送渠道覆盖个人与企业采购，按需选择，全程可追踪。',
    modules: [
      {
        title: '顺丰速运',
        paragraphs: [
          '顺丰速运覆盖全国主要城市，时效稳定、配送快捷，适合对个人与紧急补货订单。标准商品通常 1–2 个工作日内发出，发出后可在订单详情查看运单号与实时物流轨迹。',
          '签收时请核对商品数量与包装完好，如有异常可当场拒收并联系在线客服处理。',
        ],
      },
      {
        title: '中通 / 圆通',
        paragraphs: [
          '中通与圆通为经济型配送渠道，覆盖网络广、性价比高，适合对时效要求相对宽松的常规采购。仓库会根据收货地址自动匹配可达的快递网点。',
          '物流信息在发货后同步至订单详情，可在「我的订单」中随时查看包裹进度。',
        ],
      },
      {
        title: '企业专送',
        paragraphs: [
          '面向企业团购与批量采购客户，符合条件的大宗订单可安排专车或专线配送，按约定时间送达指定收货地点，支持按批次、分地点交货。',
          '企业专送由专属客服对接排车与到货时间，具体覆盖范围与时效可在下单前联系在线客服确认。',
        ],
      },
    ],
  },
  'after-sales': {
    title: '售后服务',
    lead: '规范的退换货、定制保障与账期开票服务，保障你的采购权益。',
    modules: [
      {
        title: '退换货政策',
        paragraphs: [
          '标准现货商品在签收后 7 日内、保持商品完好且不影响二次销售的情况下，可申请无理由退换。定制商品、已使用或人为损坏的商品不适用无理由退换。',
          '退款按原支付渠道退回，通常 1–7 个工作日到账；账期订单的退款会同步冲减应收款。',
        ],
      },
      {
        title: '定制商品说明',
        paragraphs: [
          '定制队服需先提交设计图稿，经审核通过后进入生产，完工后安排发货。图稿被驳回时可根据审核意见重新上传，每一版图稿与审核记录都可在定制单详情中查看。',
          '交期自图稿审核通过之日起计算，进入生产后一般不再支持内容修改，请在上图前仔细确认。',
        ],
      },
      {
        title: '发票与对账',
        paragraphs: [
          '订单完成后可在订单详情申请开票，支持增值税普通发票与专用发票，开票信息请填写准确。企业账期客户按月生成对账单，汇总当期订单、回款与应收余额。',
          '完成对公转账后财务按订单核销，核销后释放占用的授信额度，如需协助核对到账情况可联系在线客服。',
        ],
      },
    ],
  },
  about: {
    title: '关于森特',
    lead: '了解森特体育的品牌理念、企业采购服务与联系方式。',
    modules: [
      {
        title: '品牌故事',
        paragraphs: [
          '森特体育用品有限公司深耕运动装备领域，为个人与企事业单位提供球类、健身、瑜伽、户外等专业运动装备，并提供队服定制与企业团购一站式采购服务。',
          '我们坚持甄选优质面料与工艺，支持 UV 印花、烫印队徽等定制工艺，以客户为中心提供专业选品、灵活账期与贴心售后。',
        ],
      },
      {
        title: '企业采购',
        paragraphs: [
          '企业客户可进入团购专区选购商品，享受阶梯定价——采购量越大单价越优惠，覆盖球队装备、训练器材、定制队服等多品类。',
          '通过企业认证后可开通授信额度，享受先货后款的账期结算；企业客户还享有专属客服优先接入与增值税专用发票等权益。',
        ],
      },
      {
        title: '联系我们',
        paragraphs: [
          '工作时间内可点击顶部「在线客服」发起咨询，支持文字与图片沟通，客服会协助处理订单、定制与售后问题。订单状态与系统通知会同步发送至消息中心。',
          '企业团购、定制合作与账期开通等事宜，可通过在线客服登记需求，我们将安排专属客服与您对接。',
        ],
      },
    ],
  },
};

const category = computed<Category | null>(() => CATEGORIES[key.value] ?? null);
</script>

<style scoped>
.help-category {
  padding-top: 16px;
  padding-bottom: 32px;
}
.help-category__crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--sent-muted);
  margin-bottom: 16px;
}
.help-category__crumb a:hover {
  color: var(--sent-primary);
}
.help-category__head {
  background: linear-gradient(115deg, #0d4d3b, #1c8a6b);
  border-radius: 12px;
  padding: 28px 32px;
  color: #fff;
  margin-bottom: 16px;
}
.help-category__badge {
  display: inline-block;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.18);
  margin-bottom: 12px;
}
.help-category__title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
}
.help-category__lead {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
  margin: 0;
  line-height: 1.6;
}
.help-category__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.help-module {
  background: var(--sent-card);
  border: 1px solid var(--sent-border);
  border-radius: var(--sent-radius);
  padding: 20px 24px;
}
.help-module__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--sent-border);
}
.help-module__no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-size: 13px;
  font-style: normal;
  font-weight: 600;
  flex: none;
}
.help-module__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--sent-text);
  margin: 0;
}
.help-module__body p {
  font-size: 13px;
  color: var(--sent-text-2);
  line-height: 1.9;
  margin: 0 0 8px;
}
.help-module__body p:last-child {
  margin-bottom: 0;
}
.help-category__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 4px;
  font-size: 13px;
}
.help-category__back {
  color: var(--sent-text-2);
}
.help-category__back:hover,
.help-category__service:hover {
  color: var(--sent-primary);
}
.help-category__service {
  color: var(--sent-primary);
}
.help-category__empty {
  background: var(--sent-card);
  border: 1px solid var(--sent-border);
  border-radius: var(--sent-radius);
  padding: 40px;
  text-align: center;
}
.help-category__empty p {
  color: var(--sent-muted);
  font-size: 13px;
}
</style>
