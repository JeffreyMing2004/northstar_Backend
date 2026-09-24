package com.ming.northstar_backend.dto;

import java.util.List;

/**
 * 简易分页结构。项目现有接口多返回全量 {@code List}，白名单/日志量可能较大，
 * 因此这两个接口统一用本结构返回。
 */
public class PageResult<T> {

    private long total;
    private int page;
    private int size;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, int page, int size, List<T> list) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.list = list;
    }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }
}
