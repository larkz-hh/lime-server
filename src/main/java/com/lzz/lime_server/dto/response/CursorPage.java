package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class CursorPage<T> {

    private List<T> items;
    private String nextCursor;
    private boolean hasMore;

    /// cursor 为 Long 类型时自动转字符串（兼容笔记信息流等已有接口）
    public static <T> CursorPage<T> of(List<T> items, Long nextCursor, boolean hasMore) {
        return of(items, nextCursor != null ? String.valueOf(nextCursor) : null, hasMore);
    }

    /// cursor 为 String 类型（用于热度排序等复合游标场景）
    public static <T> CursorPage<T> of(List<T> items, String nextCursor, boolean hasMore) {
        CursorPage<T> page = new CursorPage<>();
        page.setItems(items);
        page.setNextCursor(nextCursor);
        page.setHasMore(hasMore);
        return page;
    }
}
