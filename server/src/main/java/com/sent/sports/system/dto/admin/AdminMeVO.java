package com.sent.sports.system.dto.admin;

import java.util.List;

/**
 * 管理端当前员工视图（收口阶段）：{@code GET /api/admin/me} 响应。
 * <p>字段与 {@code packages/shared/src/types.ts} 的 {@code MeVO} 一一对应，供权限 store 直接消费；
 * 不含密码散列 / tokenVersion 等敏感字段。</p>
 *
 * @param userId      员工主键（即登录令牌中的 userId）
 * @param username    工号
 * @param name        姓名
 * @param aud         固定 admin
 * @param roleCode    角色代码（无角色为空串）
 * @param permissions 权限码集合（无角色为空集合）
 * @param dataScope   数据范围 ALL / TEAM / SELF，未知一律按最严的 SELF
 */
public record AdminMeVO(long userId, String username, String name, String aud, String roleCode,
                        List<String> permissions, String dataScope) {
}
