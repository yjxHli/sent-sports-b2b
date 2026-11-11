package com.sent.sports.customize.domain;

/** 定制单状态（spec §6.6.2 / §15.2）。NONE 表示订单行无定制。 */
public enum CustomStatus {
    NONE("无定制", false),
    WAIT_DRAFT("待提交图稿", false),
    WAIT_REVIEW("待审核", false),
    REJECTED("审核驳回", false),
    APPROVED("审核通过", false),
    IN_PRODUCTION("生产中", false),
    FINISHED("已完工", false),
    SHIPPED("已发货", true),
    CANCELLED("已取消", true),
    ESCALATED("待主管处理", false);

    private final String chineseName;
    private final boolean terminal;

    CustomStatus(String chineseName, boolean terminal) {
        this.chineseName = chineseName;
        this.terminal = terminal;
    }

    public String getChineseName() {
        return chineseName;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
