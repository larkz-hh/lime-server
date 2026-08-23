package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class DanmakuListResponse {

    private List<DanmakuResponse> items;
    private int count;

    public static DanmakuListResponse of(List<DanmakuResponse> items) {
        DanmakuListResponse resp = new DanmakuListResponse();
        resp.setItems(items);
        resp.setCount(items.size());
        return resp;
    }
}
