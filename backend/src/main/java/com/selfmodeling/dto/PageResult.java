package com.selfmodeling.dto;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, List<T> list) {
        this.total = total;
        this.list = list;
    }

    public static <T> PageResult<T> of(long total, List<T> list) {
        return new PageResult<>(total, list);
    }
}