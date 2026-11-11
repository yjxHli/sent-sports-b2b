package com.sent.sports.common.web;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应模型：{list, total, page, size, pages}。
 * @param <T> 行数据类型
 */
public final class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> list;
    private long total;
    private long page;
    private long size;
    private long pages;

    public PageResult() {
    }

    private PageResult(List<T> list, long total, long page, long size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size > 0 ? (long) Math.ceil((double) total / (double) size) : 0L;
    }

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        return new PageResult<>(list == null ? Collections.emptyList() : list, total, page, size);
    }

    public List<T> getList() {
        return list;
    }

    public long getTotal() {
        return total;
    }

    public long getPage() {
        return page;
    }

    public long getSize() {
        return size;
    }

    public long getPages() {
        return pages;
    }
}
