package com.sent.sports.common.service.impl;

import com.sent.sports.common.dto.admin.ConfirmDtos;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.service.ConfirmTokenStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 确认令牌服务实现（P5-31）：短语校验 → 生成/复用令牌 → 交给 {@link ConfirmTokenStore} 存储。
 * <ul>
 *   <li><b>绑定</b>：令牌键为 {@code 动作|目标}；同一动作对同一目标在有效期内复用同一令牌，
 *       避免前端重复点击堆出一串令牌；</li>
 *   <li><b>有效期</b>：5 分钟（与 plan 的 300s 一致），过期即无效；</li>
 *   <li><b>用后即焚</b>：{@link #consume} 委托存储做「匹配则删除」的原子动作，
 *       服务层不再二次读写，杜绝先查后删之间被重复消费。</li>
 * </ul>
 * 短语支持按动作覆盖（构造注入 {@code phrasesByAction}），未配置的动作回落到统一短语。
 */
@Service
public class ConfirmTokenServiceImpl implements ConfirmTokenService {

    /** 未按动作配置短语时的统一短语。 */
    static final String DEFAULT_PHRASE = "CONFIRM";

    static final Duration TTL = Duration.ofMinutes(5);

    private final ConfirmTokenStore store;
    private final Clock clock;
    private final Map<String, String> phrasesByAction;

    /** Spring 装配入口：使用统一短语；按动作定制短语时走三参构造（测试或后续配置化场景）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public ConfirmTokenServiceImpl(ConfirmTokenStore store, Clock clock) {
        this(store, clock, Map.of());
    }

    public ConfirmTokenServiceImpl(ConfirmTokenStore store, Clock clock, Map<String, String> phrasesByAction) {
        this.store = store;
        this.clock = clock;
        this.phrasesByAction = phrasesByAction == null ? Map.of() : Map.copyOf(phrasesByAction);
    }

    @Override
    public ConfirmDtos.TokenVO issue(ConfirmDtos.ConfirmCmd cmd) {
        if (!expectedPhrase(cmd.action()).equals(cmd.confirmText())) {
            throw new BizException(ErrorCode.CONFIRM_TOKEN_INVALID);
        }
        String key = keyOf(cmd.action(), cmd.targetId());
        ConfirmTokenStore.Stored existing = store.find(key);
        if (existing != null) {
            return new ConfirmDtos.TokenVO(existing.token(), existing.expireAtMillis());
        }
        long expireAt = clock.instant().toEpochMilli() + TTL.toMillis();
        String token = UUID.randomUUID().toString();
        store.save(key, token, expireAt);
        return new ConfirmDtos.TokenVO(token, expireAt);
    }

    @Override
    public boolean consume(String action, String targetId, String token) {
        if (action == null || token == null) {
            return false;
        }
        return store.consume(keyOf(action, targetId), token);
    }

    private String expectedPhrase(String action) {
        String override = phrasesByAction.get(action);
        if (override != null) {
            return override;
        }
        // 未显式配置时按 ConfirmAction 的规范短语校验（如 PAYMENT_WRITE_OFF = 确认核销）；
        // 历史上 Spring 装配走空 Map → 一切动作都要求字面量 CONFIRM，与前端提示的短语不一致，
        // 表现为「输入正确短语仍 40302」。
        if (action != null) {
            try {
                return com.sent.sports.common.domain.ConfirmAction.valueOf(action).phrase();
            } catch (IllegalArgumentException ignored) {
                // 非枚举内的自由动作名（如 refund:audit）沿用统一短语
            }
        }
        return DEFAULT_PHRASE;
    }

    private String keyOf(String action, String targetId) {
        return action + "|" + (targetId == null ? "" : targetId);
    }
}
