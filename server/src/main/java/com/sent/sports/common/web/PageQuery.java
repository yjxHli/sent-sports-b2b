package com.sent.sports.common.web;

/**
 * 分页查询对象（P4-04）：统一接收 page / size / sort 入参并做归一化。
 * <ul>
 *   <li>page 从 1 开始；小于 1 时回退为 {@link #DEFAULT_PAGE}。</li>
 *   <li>size 默认 {@link #DEFAULT_SIZE}，单页上限 {@link #MAX_SIZE}（100），防止过大分页拖垮库。</li>
 *   <li>sort 透传，由调用方解析为 ORDER BY（如 "createdAt,desc"）。</li>
 * </ul>
 * 控制器直接复用 {@link #offset()} / {@link #limit()} 拼接到分页 SQL。
 */
public class PageQuery {

    public static final int DEFAULT_PAGE = 1;

    public static final int DEFAULT_SIZE = 20;

    public static final int MAX_SIZE = 100;

    private int page = DEFAULT_PAGE;

    private int size = DEFAULT_SIZE;

    private String sort;

    public int getPage() {
        return Math.max(DEFAULT_PAGE, page);
    }

    public int getSize() {
        return Math.min(MAX_SIZE, Math.max(1, size));
    }

    public String getSort() {
        return sort;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    /** 偏移量（0 起），供 SQL LIMIT ?,? 的第一个参数 */
    public int offset() {
        return (getPage() - 1) * getSize();
    }

    /** 单页条数，供 SQL LIMIT 的第二个参数 */
    public int limit() {
        return getSize();
    }
}
