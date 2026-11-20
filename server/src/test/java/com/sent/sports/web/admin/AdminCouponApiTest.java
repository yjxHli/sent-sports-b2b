package com.sent.sports.web.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.coupon.controller.admin.AdminCouponBatchDeleteController;
import com.sent.sports.coupon.controller.admin.AdminCouponController;
import com.sent.sports.coupon.service.dto.CreateCouponTemplateCmd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * 券管理权限矩阵回归（R15）：建模板与发券必须是两个权限点。
 *
 * <p>缺陷原状：6 个端点共用 {@code customer:grant}，于是「能授信客户」等于「能造券 + 全量发券」。
 * 这里用反射钉住注解，防止后续有人图省事把新权限点改回去。</p>
 */
class AdminCouponApiTest {

    private static String[] permsOf(Class<?> controller, String methodName, Class<?>... params)
            throws NoSuchMethodException {
        Method method = controller.getMethod(methodName, params);
        RequirePerm perm = method.getAnnotation(RequirePerm.class);
        org.junit.jupiter.api.Assertions.assertNotNull(perm, methodName + " 必须声明权限注解");
        return perm.value();
    }

    @Test
    @DisplayName("券模板分页 / 新建：独立权限 coupon:template:manage，不得复用 customer:grant")
    void templateManagementUsesDedicatedPermission() throws Exception {
        assertArrayEquals(new String[]{"coupon:template:manage"},
                permsOf(AdminCouponController.class, "templatePage",
                        com.sent.sports.common.web.PageQuery.class, String.class, String.class));
        assertArrayEquals(new String[]{"coupon:template:manage"},
                permsOf(AdminCouponController.class, "createTemplate",
                        CreateCouponTemplateCmd.class, com.sent.sports.common.web.CurrentUser.class));
        assertArrayEquals(new String[]{"coupon:template:manage"},
                permsOf(AdminCouponBatchDeleteController.class, "deleteTemplates",
                        com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd.class));
    }

    @Test
    @DisplayName("发券链路仍走 customer:grant：下拉 / 发放 / 发放记录 / 删记录")
    void issuingKeepsCustomerGrantPermission() throws Exception {
        assertArrayEquals(new String[]{"customer:grant"},
                permsOf(AdminCouponController.class, "templates"));
        assertArrayEquals(new String[]{"customer:grant"},
                permsOf(AdminCouponController.class, "issue", AdminCouponController.IssueCmd.class));
        assertArrayEquals(new String[]{"customer:grant"},
                permsOf(AdminCouponController.class, "issues", int.class));
        assertArrayEquals(new String[]{"customer:grant"},
                permsOf(AdminCouponBatchDeleteController.class, "deleteIssues",
                        com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd.class));
    }
}
