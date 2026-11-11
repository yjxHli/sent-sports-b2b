package com.sent.sports.common.service;

import com.sent.sports.common.dto.admin.ConfirmDtos;

/**
 * 危险操作确认令牌服务接口（P4-58 契约，置于 common 供各模块复用）。
 * <p>语义：危险动作（大额退款、强制冲销、批量改价等）执行前，先凭「确认短语」换取一次性令牌，
 * 令牌绑定 {@code 动作 + 目标}，{@link #consume} 校验通过即焚毁（用后即焚），防止重放。
 * 存储走 {@link ConfirmTokenStore} 端口（P5-31）：默认进程内实现，多实例部署替换 Redis 即可。
 */
public interface ConfirmTokenService {

    /** 换取一次性确认令牌；短语错误抛出 {@code CONFIRM_TOKEN_INVALID}。 */
    ConfirmDtos.TokenVO issue(ConfirmDtos.ConfirmCmd cmd);

    /**
     * 校验并销毁令牌（用后即焚）。
     *
     * @return {@code true} 表示令牌有效且已被消费；{@code false} 表示无效 / 过期 / 已用过
     */
    boolean consume(String action, String targetId, String token);
}
