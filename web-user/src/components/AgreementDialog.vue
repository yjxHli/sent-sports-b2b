<template>
  <el-dialog
    v-model="visible"
    :title="doc.title"
    width="720px"
    top="8vh"
    class="agreement-dialog"
    destroy-on-close
    data-testid="agreement-dialog"
  >
    <div class="agreement-body" data-testid="agreement-body">
      <p class="agreement-meta">
        生效日期：2026 年 1 月 1 日 · 适用主体：江苏凤凰森特体育用品有限公司
      </p>
      <section v-for="(section, index) in doc.sections" :key="section.title" class="agreement-section">
        <h3>{{ index + 1 }}. {{ section.title }}</h3>
        <p v-for="line in section.paragraphs" :key="line">{{ line }}</p>
      </section>
      <p class="agreement-tail">
        如对本协议有任何疑问，可通过「在线客服」或邮箱 service@sent-sports.example 与我们联系。
      </p>
    </div>
    <template #footer>
      <el-button type="primary" data-testid="agreement-close" @click="visible = false">
        我已阅读
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 登录页协议阅读弹窗（2026-11-18 用户需求 1）。
 *
 * 之前《用户服务协议》《隐私权政策》只是勾选框里的一段纯文字，点不动也看不到内容。
 * 现在两段文字都是真链接，点开即读全文，勾选框仍作为「已阅读并同意」的确认动作。
 *
 * 文案按电商商城的通行条款撰写（账号、订单、支付、发货、退换、优惠券、隐私、未成年人保护等），
 * 演示环境用同一份文本，不做版本管理。
 */
import { computed } from 'vue';
import type { AgreementType } from './agreement';

const props = defineProps<{ type: AgreementType }>();
const visible = defineModel<boolean>({ default: false });

interface AgreementSection {
  title: string;
  paragraphs: string[];
}

interface AgreementDoc {
  title: string;
  sections: AgreementSection[];
}

const SERVICE: AgreementDoc = {
  title: '森特商城用户服务协议',
  sections: [
    {
      title: '协议的范围',
      paragraphs: [
        '本协议是您与森特体育商城（以下简称「本商城」）之间就注册、登录、浏览、下单、支付、售后等全部服务所订立的协议。',
        '您勾选「已阅读并同意」并完成注册或登录，即表示您已充分理解并接受本协议全部内容。',
      ],
    },
    {
      title: '账号注册与使用',
      paragraphs: [
        '您应使用本人真实、有效的手机号注册，并妥善保管账号与密码；因您主动泄露或出借账号造成的损失由您自行承担。',
        '一个手机号仅可注册一个商城账号。发现账号被盗用或存在异常登录时，请立即修改密码并联系在线客服冻结账号。',
        '企业客户应以营业执照主体信息完成企业认证，认证通过后方可使用企业协议价、账期与团购阶梯价。',
      ],
    },
    {
      title: '商品信息与订单成立',
      paragraphs: [
        '商品页面展示的规格、库存、价格以下单时系统校验结果为准；因页面缓存导致的展示差异，以最终订单金额为准。',
        '您提交订单并完成支付后，订单即成立。本商城在库存不足、价格明显错误等情形下有权取消订单并全额退款。',
        '定制类商品（含印号、印字、来图定制）在您确认图稿后进入生产，除质量问题外不支持无理由退货。',
      ],
    },
    {
      title: '价格、优惠与优惠券',
      paragraphs: [
        '商品价格以人民币标价，含税价与不含税价以结算页展示为准；企业协议价仅对已认证企业账号生效。',
        '优惠券按券面说明使用，单笔订单限用一张，不与其他优惠叠加（页面另有说明的除外）；优惠券不可提现、不可转赠。',
        '以恶意刷券、虚假交易等方式获取优惠的，本商城有权撤销优惠、取消订单并限制账号使用。',
      ],
    },
    {
      title: '支付与发票',
      paragraphs: [
        '本商城支持余额支付等线上支付方式，支付结果以支付渠道回调为准。',
        '您可在订单完成后申请电子发票，发票抬头与税号以您提交的信息为准；发票开具后如需换开，请联系客服。',
      ],
    },
    {
      title: '配送与验收',
      paragraphs: [
        '现货商品一般在付款后 48 小时内发出，偏远地区配送时效以物流实际为准。',
        '签收时请当场验货；发现外包装破损、商品缺失或明显质量问题的，请拒收并联系客服处理。',
      ],
    },
    {
      title: '退换货与售后',
      paragraphs: [
        '非定制类商品自签收之日起 7 日内，在商品完好、不影响二次销售的前提下可申请无理由退货。',
        '质量问题按国家三包规定处理；使用不当、人为损坏、超出质保期的情形不在免费售后范围内。',
        '退款按原支付路径退回，使用优惠券的订单按实付金额退款，优惠券是否退回以券面规则为准。',
      ],
    },
    {
      title: '用户行为规范',
      paragraphs: [
        '您不得利用本商城从事任何违法违规活动，不得发布虚假评价、恶意攻击他人或干扰系统正常运行。',
        '违反上述规范的，本商城有权删除相关内容、限制功能直至终止服务。',
      ],
    },
    {
      title: '协议变更与争议解决',
      paragraphs: [
        '本商城可能根据业务调整更新本协议，更新后在页面公示；您继续使用服务即视为接受更新后的协议。',
        '因本协议引起的争议，双方应友好协商解决；协商不成的，可向本商城所在地人民法院提起诉讼。',
      ],
    },
  ],
};

const PRIVACY: AgreementDoc = {
  title: '隐私权政策',
  sections: [
    {
      title: '我们收集哪些信息',
      paragraphs: [
        '账号信息：手机号、密码（加密存储）、昵称与头像。',
        '交易信息：订单商品、金额、收货地址与联系方式、支付与退款记录、发票抬头与税号。',
        '服务信息：在线客服会话记录、售后服务记录，以及您主动上传的定制图稿与发票附件。',
        '设备信息：为保障账号安全，我们会记录登录时间、IP 与设备标识，用于异常登录识别。',
      ],
    },
    {
      title: '我们如何使用这些信息',
      paragraphs: [
        '用于完成注册登录、下单支付、发货配送、开票与售后等您所请求的服务。',
        '用于会员等级、积分与优惠券的发放与核销，以及订单与物流状态通知。',
        '用于安全风控与服务改进，例如识别异常登录、统计页面使用情况；此类统计不针对个人身份。',
      ],
    },
    {
      title: '信息的共享与委托处理',
      paragraphs: [
        '为完成配送，我们会向承运物流商提供收货人姓名、电话与地址；为完成支付，我们会向支付机构传递必要的订单与金额信息。',
        '除法律法规要求或您另行授权外，我们不会向其他第三方提供您的个人信息，也不会出售个人信息。',
      ],
    },
    {
      title: '信息的存储与保护',
      paragraphs: [
        '您的个人信息存储于中华人民共和国境内，保存期限为实现服务目的所必需的最短时间。',
        '我们采用传输加密、密码单向加密、最小权限访问等措施保护您的信息；发生安全事件时将依法告知并上报。',
      ],
    },
    {
      title: '您的权利',
      paragraphs: [
        '您可以随时查看、修改个人资料与收货地址，也可以删除浏览足迹、收藏与部分订单记录。',
        '您可以申请注销账号；注销后除法律法规要求保留的交易凭证外，我们将删除或匿名化您的个人信息。',
      ],
    },
    {
      title: 'Cookie 与同类技术',
      paragraphs: [
        '我们使用本地存储保存登录状态与列表分页偏好，用于免去重复登录与保持浏览位置。',
        '您可以在浏览器中清除这些数据；清除后需要重新登录，部分页面偏好会恢复默认。',
      ],
    },
    {
      title: '未成年人保护',
      paragraphs: [
        '本商城面向具备完全民事行为能力的用户提供服务；未成年人应在监护人陪同下阅读本政策并使用本商城。',
        '若监护人发现未成年人未经同意注册或消费，可联系我们协助处理。',
      ],
    },
    {
      title: '政策更新与联系我们',
      paragraphs: [
        '本政策更新后将在本页面公示，重大变更会通过站内信或弹窗提示。',
        '如您对个人信息处理有疑问、投诉或行使权利，可通过在线客服或 service@sent-sports.example 联系我们。',
      ],
    },
  ],
};

const doc = computed<AgreementDoc>(() => (props.type === 'privacy' ? PRIVACY : SERVICE));
</script>

<style scoped>
.agreement-body {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
  color: var(--sent-text-2);
  font-size: 13px;
  line-height: 1.75;
}
.agreement-meta {
  margin: 0 0 14px;
  color: var(--sent-muted);
  font-size: 12px;
}
.agreement-section { margin-bottom: 14px; }
.agreement-section h3 {
  margin: 0 0 6px;
  font-size: 14px;
  color: var(--sent-text);
}
.agreement-section p { margin: 0 0 6px; }
.agreement-tail {
  margin: 18px 0 0;
  padding-top: 12px;
  border-top: 1px dashed var(--sent-border);
  color: var(--sent-muted);
  font-size: 12px;
}
</style>
