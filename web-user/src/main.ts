import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus, { ElMessage } from 'element-plus';
import 'element-plus/dist/index.css';
import './style.css';
import App from './App.vue';
import router from './router';
import { setHttpErrorHandler } from '@/utils/http';

// 基础设施类错误统一提示；40102（刷新重放）与 40103（清令牌跳登录）由 HTTP 层内部消化。
setHttpErrorHandler((error) => {
  ElMessage.error(error.message || '请求失败');
});

const app = createApp(App);
app.use(createPinia()); // 必须先于 router：守卫里要用 Pinia store
app.use(ElementPlus);
app.use(router);
app.mount('#app');
