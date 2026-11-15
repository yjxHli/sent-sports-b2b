package com.sent.sports.system.mapper.po;

/**
 * 业务参数行（{@code sys_config} 投影）：键值对，值一律按字符串传输，
 * 单位解释权归读取方（金额按「分」、时长按分钟/天等），不在读侧做类型转换。
 */
public class ParamRow {

    private String paramKey;

    private String paramValue;

    private String remark;

    public String getParamKey() {
        return paramKey;
    }

    public void setParamKey(String paramKey) {
        this.paramKey = paramKey;
    }

    public String getParamValue() {
        return paramValue;
    }

    public void setParamValue(String paramValue) {
        this.paramValue = paramValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
