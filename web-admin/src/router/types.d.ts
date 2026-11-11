import 'vue-router';

/** 业务路由 meta（静态路由表 + permissions[] 过滤，后端不下发组件路径） */
declare module 'vue-router' {
  interface RouteMeta {
    /** 菜单/页签名 */
    title?: string;
    /** 访问该路由所需权限码；缺省表示登录即可访问 */
    perm?: string;
    /** 菜单分组名；为空表示一级菜单 */
    group?: string;
    /** 菜单图标位（单汉字，避免引入整套图标库） */
    icon?: string;
    /** 不在侧边栏显示（详情/编辑/错误页） */
    hidden?: boolean;
  }
}
