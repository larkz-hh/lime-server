package com.lzz.lime_server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzz.lime_server.entity.NoteComment;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NoteCommentMapper extends BaseMapper<NoteComment> {

    /**
     * 查询一级评论——热度排序（热度降序，游标基于 hot_score+id 复合键）
     * cursor=null 表示第一页；否则取 cursorScore 和 cursorId 做分页
     */
    @Select("""
            SELECT nc.id, nc.note_id, nc.user_id, nc.content, nc.voice_url, nc.voice_duration,
                   nc.like_count, nc.reply_count, nc.hot_score, nc.create_time,
                   u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note_comment nc
            JOIN `user` u ON u.id = nc.user_id AND u.deleted = 0
            WHERE nc.note_id = #{noteId}
              AND nc.parent_id IS NULL
              AND nc.deleted = 0
              AND (
                #{cursorScore} IS NULL
                OR nc.hot_score < #{cursorScore}
                OR (nc.hot_score = #{cursorScore} AND nc.id < #{cursorId})
              )
            ORDER BY nc.hot_score DESC, nc.id DESC
            LIMIT #{size}
            """)
    List<CommentRow> selectByHot(
            @Param("noteId") Long noteId,
            @Param("cursorScore") Integer cursorScore,
            @Param("cursorId") Long cursorId,
            @Param("size") int size);

    /**
     * 查询一级评论——时间倒序（最新在前，游标基于 id）
     */
    @Select("""
            SELECT nc.id, nc.note_id, nc.user_id, nc.content, nc.voice_url, nc.voice_duration,
                   nc.like_count, nc.reply_count, nc.hot_score, nc.create_time,
                   u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note_comment nc
            JOIN `user` u ON u.id = nc.user_id AND u.deleted = 0
            WHERE nc.note_id = #{noteId}
              AND nc.parent_id IS NULL
              AND nc.deleted = 0
              AND (#{cursor} IS NULL OR nc.id < #{cursor})
            ORDER BY nc.id DESC
            LIMIT #{size}
            """)
    List<CommentRow> selectByTime(
            @Param("noteId") Long noteId,
            @Param("cursor") Long cursor,
            @Param("size") int size);

    /**
     * 查询某条一级评论的回复列表——时间正序（最早在前），游标基于 id
     */
    @Select("""
            SELECT nc.id, nc.note_id, nc.user_id, nc.parent_id, nc.reply_to_user_id,
                   nc.content, nc.voice_url, nc.voice_duration,
                   nc.like_count, nc.create_time,
                   u.nickname AS author_nickname, u.avatar AS author_avatar,
                   ru.nickname AS reply_to_nickname
            FROM note_comment nc
            JOIN `user` u ON u.id = nc.user_id AND u.deleted = 0
            LEFT JOIN `user` ru ON ru.id = nc.reply_to_user_id AND ru.deleted = 0
            WHERE nc.parent_id = #{parentId}
              AND nc.deleted = 0
              AND (#{cursor} IS NULL OR nc.id > #{cursor})
            ORDER BY nc.id ASC
            LIMIT #{size}
            """)
    List<CommentRow> selectReplies(
            @Param("parentId") Long parentId,
            @Param("cursor") Long cursor,
            @Param("size") int size);

    /**
     * 查询一级评论的前 N 条回复预览（时间正序），用于评论列表接口附带预览
     */
    @Select("""
            SELECT nc.id, nc.note_id, nc.user_id, nc.parent_id, nc.reply_to_user_id,
                   nc.content, nc.voice_url, nc.voice_duration,
                   nc.like_count, nc.create_time,
                   u.nickname AS author_nickname, u.avatar AS author_avatar,
                   ru.nickname AS reply_to_nickname
            FROM note_comment nc
            JOIN `user` u ON u.id = nc.user_id AND u.deleted = 0
            LEFT JOIN `user` ru ON ru.id = nc.reply_to_user_id AND ru.deleted = 0
            WHERE nc.parent_id = #{parentId}
              AND nc.deleted = 0
            ORDER BY nc.id ASC
            LIMIT #{size}
            """)
    List<CommentRow> selectTopReplies(
            @Param("parentId") Long parentId,
            @Param("size") int size);

    /**
     * 评论查询结果的扁平化投影
     */
    @Data
    class CommentRow {
        private Long id;
        private Long noteId;
        private Long userId;
        private Long parentId;
        private Long replyToUserId;
        private String content;
        private String voiceUrl;
        private Integer voiceDuration;
        private Integer likeCount;
        private Integer replyCount;
        private Integer hotScore;
        private LocalDateTime createTime;
        // 作者信息
        private String authorNickname;
        private String authorAvatar;
        // 被回复用户昵称（仅回复时有值）
        private String replyToNickname;
    }
}
