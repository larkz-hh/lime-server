package com.lzz.lime_server.dto.response;

import lombok.Data;

@Data
public class HotSearchWord {
    private String keyword;
    private long count;
}
