import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus, { ElMessage } from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/dist/index.css';
import './style/theme.css';
import App from './App.vue';
import router from './router';
import { setHttpErrorHandler } from '@/utils/http';

// 基础设施类错误（40301 无权限 / 42901 限流 / 50001 系统错误）统一提示。
// 40102（刷新重放）与 40103（清令牌跳登录）由 HTTP 层内部消化，不进这里。
setHttpErrorHandler((error) => {
  ElMessage.error(error.message || '请求失败');
});

const app = createApp(App);
app.use(createPinia()); // 必须先于 router：守卫里要用 Pinia store
// 分页器/日期选择等组件内置文案统一中文，避免列表里出现 "Total 62 / Go to"
app.use(ElementPlus, { locale: zhCn });
app.use(router);
app.mount('#app');
