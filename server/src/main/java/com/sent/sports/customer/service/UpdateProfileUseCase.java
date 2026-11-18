package com.sent.sports.customer.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.dto.user.MeVO;
import com.sent.sports.customer.mapper.CustomerMapper;
import org.springframework.stereotype.Service;

/**
 * 用户端修改昵称 / 头像（2026-11-17 用户要求：我的账号点头像进编辑页，保存即生效）。
 * <p>头像只存文件服务 fileId，不存二进制也不存完整 URL；上传由前端先调 POST /api/files 完成。
 */
@Service
public class UpdateProfileUseCase {

    /** 昵称长度上限：与 customer.nickname VARCHAR(64) 对齐但更保守，避免前端撑破版式。 */
    public static final int NICKNAME_MAX = 20;
    public static final int AVATAR_ID_MAX = 128;

    private final CustomerMapper customerMapper;
    private final CustomerProfileQueryService profileQueryService;

    public UpdateProfileUseCase(CustomerMapper customerMapper,
                                CustomerProfileQueryService profileQueryService) {
        this.customerMapper = customerMapper;
        this.profileQueryService = profileQueryService;
    }

    @UseCase
    public MeVO update(long customerId, String nickname, String avatarFileId) {
        String name = nickname == null ? "" : nickname.trim();
        if (name.isEmpty() || name.length() > NICKNAME_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "昵称长度需在 1~20 个字符之间");
        }
        String avatar = avatarFileId == null || avatarFileId.isBlank()
                ? null : avatarFileId.trim();
        if (avatar != null && avatar.length() > AVATAR_ID_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "头像参数不合法");
        }
        if (customerMapper.updateProfile(customerId, name, avatar) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return profileQueryService.profile(customerId);
    }
}
