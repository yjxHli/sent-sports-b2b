package com.sent.sports.customer;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.dto.user.MeVO;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.service.CustomerProfileQueryService;
import com.sent.sports.customer.service.UpdateProfileUseCase;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 用户端编辑昵称/头像（2026-11-17）：参数校验 + 头像可清空 + 越权客户不存在。 */
class UpdateProfileUseCaseTest extends BaseUnitTest {

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private CustomerProfileQueryService profileQueryService;

    @InjectMocks
    private UpdateProfileUseCase useCase;

    private MeVO vo(String nickname, String avatar) {
        return new MeVO(9002L, "C9002", nickname, avatar, "13800138000", null,
                "PERSON", "NORMAL", 0L, 0L, "ACTIVE");
    }

    @Test
    void updatesNicknameAndAvatar() {
        when(customerMapper.updateProfile(9002L, "新昵称", "FILE-1")).thenReturn(1);
        when(profileQueryService.profile(9002L)).thenReturn(vo("新昵称", "FILE-1"));

        MeVO result = useCase.update(9002L, "  新昵称  ", " FILE-1 ");

        assertEquals("新昵称", result.nickname());
        assertEquals("FILE-1", result.avatarFileId());
        verify(customerMapper).updateProfile(9002L, "新昵称", "FILE-1");
    }

    @Test
    void avatarCanBeCleared() {
        when(customerMapper.updateProfile(9002L, "昵称", null)).thenReturn(1);
        when(profileQueryService.profile(9002L)).thenReturn(vo("昵称", null));

        useCase.update(9002L, "昵称", "  ");

        // 必须显式写 NULL：MyBatis-Plus 的 updateById 会跳过 null，头像永远清不掉
        verify(customerMapper).updateProfile(9002L, "昵称", null);
    }

    @Test
    void blankOrTooLongNicknameRejected() {
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> useCase.update(9002L, "   ", null)).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> useCase.update(9002L, "x".repeat(21), null))
                        .getErrorCode());
        verify(customerMapper, never()).updateProfile(anyLong(), anyString(), anyString());
    }

    @Test
    void missingCustomerRejected() {
        when(customerMapper.updateProfile(9999L, "昵称", null)).thenReturn(0);

        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BizException.class, () -> useCase.update(9999L, "昵称", null)).getErrorCode());
    }
}
