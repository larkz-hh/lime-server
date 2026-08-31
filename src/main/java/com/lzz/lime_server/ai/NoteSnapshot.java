package com.lzz.lime_server.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 笔记上下文快照。
 * 用户引用笔记提问时，发送当轮检索一次并序列化存入 ai_message.note_snapshot，
 * 后续追问轮次直接复用快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteSnapshot {

    private Long noteId;

    /** 标题 */
    private String title;

    /** 正文（截断） */
    private String content;

    /** 笔记图片/视频封面 URL，最多 4 张 */
    private List<String> images;

    private Integer likeCount;

    private Integer favCount;

    private Integer viewCount;

    private Integer commentCount;

    /** 精选评论文字，最多 20 条 */
    private List<String> comments;
}
