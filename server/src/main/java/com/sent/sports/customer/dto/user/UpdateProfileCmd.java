package com.sent.sports.customer.dto.user;

import jakarta.validation.constraints.NotBlank;

/** 用户端编辑个人资料入参：昵称必填，头像 fileId 可空（空表示恢复默认头像）。 */
public record UpdateProfileCmd(@NotBlank(message = "昵称不能为空") String nickname,
                               String avatarFileId) {
}
