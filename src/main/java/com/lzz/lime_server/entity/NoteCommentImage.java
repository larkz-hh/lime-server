package com.lzz.lime_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_comment_image")
public class NoteCommentImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long commentId;

    private String url;

    private Integer sortOrder;

    private LocalDateTime createTime;
}
