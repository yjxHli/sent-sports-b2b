package com.sent.sports.review.service.impl;

/**
 * 评价人展示名（用户端 / 管理端共用）。
 *
 * <p>两端口径必须一致：匿名的连首字都不给（「张**」本身就泄露了姓氏），
 * 非匿名只保留首字。抽出来避免治理页哪天改成全量昵称而漏改一处。</p>
 */
final class ReviewDisplayNames {

    static final String ANONYMOUS = "匿名用户";

    private ReviewDisplayNames() {
    }

    static String of(String nickname, boolean anonymous) {
        if (anonymous) {
            return ANONYMOUS;
        }
        if (nickname == null || nickname.isBlank()) {
            return ANONYMOUS;
        }
        return nickname.trim().substring(0, 1) + "**";
    }
}
