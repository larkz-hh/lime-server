package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileRequest {

    @Size(min = 1, max = 20, message = "昵称长度为 1-20 个字符")
    private String nickname;

    @Size(max = 200, message = "简介不超过 200 个字符")
    private String bio;

    /** 性别：0=未设置，1=男，2=女 */
    @Min(value = 0, message = "性别值无效")
    @Max(value = 2, message = "性别值无效")
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 地区，最多 50 个字符 */
    @Size(max = 50, message = "地区不超过 50 个字符")
    private String region;
}
