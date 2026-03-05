package com.cet46.vocab.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

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

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(
                page.getTotal(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getRecords()
        );
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }
}
