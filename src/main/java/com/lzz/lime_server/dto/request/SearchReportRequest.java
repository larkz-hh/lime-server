package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchReportRequest {

    @NotBlank(message = "搜索关键词不能为空")
    @Size(max = 50, message = "搜索关键词不能超过 50 个字符")
    private String keyword;
}
