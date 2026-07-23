package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class CursorPage<T> {

    private List<T> items;
    private Long nextCursor;
    private boolean hasMore;

    public static <T> CursorPage<T> of(List<T> items, Long nextCursor, boolean hasMore) {
        CursorPage<T> page = new CursorPage<>();
        page.setItems(items);
        page.setNextCursor(nextCursor);
        page.setHasMore(hasMore);
        return page;
    }
}
