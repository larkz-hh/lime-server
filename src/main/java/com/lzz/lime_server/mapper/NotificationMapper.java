package com.lzz.lime_server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzz.lime_server.entity.Notification;
import lombok.Data;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    // 查询某用户的通知列表
    @Select("""
            <script>
            SELECT n.id, n.type, n.note_id, n.comment_id, n.content, n.is_read, n.create_time,
                   u.id AS sender_id, u.nickname AS sender_nickname, u.avatar AS sender_avatar,
                   (SELECT img.url FROM note_image img
                     WHERE img.note_id = n.note_id
                     ORDER BY img.sort_order ASC LIMIT 1) AS note_cover,
                   pc.id AS parent_comment_id,
                   LEFT(pc.content, 200) AS reply_to_content
            FROM notification n
            JOIN `user` u ON u.id = n.sender_id
            LEFT JOIN note_comment c ON c.id = n.comment_id
            LEFT JOIN note_comment pc ON pc.id = c.parent_id
            WHERE n.receiver_id = #{receiverId}
            <if test="types != null and types.size() > 0">
              AND n.type IN
              <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
            </if>
            <if test="cursor != null">AND n.id &lt; #{cursor}</if>
            ORDER BY n.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "notificationResultMap", value = {
            @Result(property = "id",             column = "id"),
            @Result(property = "type",           column = "type"),
            @Result(property = "noteId",         column = "note_id"),
            @Result(property = "commentId",      column = "comment_id"),
            @Result(property = "content",        column = "content"),
            @Result(property = "isRead",         column = "is_read"),
            @Result(property = "createTime",     column = "create_time"),
            @Result(property = "senderId",       column = "sender_id"),
            @Result(property = "senderNickname", column = "sender_nickname"),
            @Result(property = "senderAvatar",   column = "sender_avatar"),
            @Result(property = "noteCover",      column = "note_cover"),
            @Result(property = "parentCommentId",  column = "parent_comment_id"),
            @Result(property = "replyToContent",   column = "reply_to_content")
    })
    List<NotificationRow> selectNotifications(@Param("receiverId") Long receiverId,
                                              @Param("cursor") Long cursor,
                                              @Param("size") int size,
                                              @Param("types") List<Integer> types);

    // 未读数（总数）
    @Select("SELECT COUNT(*) FROM notification WHERE receiver_id = #{receiverId} AND is_read = 0")
    long countUnread(@Param("receiverId") Long receiverId);

    // 按类型统计未读数
    @Select("SELECT type, COUNT(*) AS cnt FROM notification WHERE receiver_id = #{receiverId} AND is_read = 0 GROUP BY type")
    List<TypeUnreadRow> countUnreadByType(@Param("receiverId") Long receiverId);

    // 标记单条已读
    @Update("UPDATE notification SET is_read = 1 WHERE id = #{id} AND receiver_id = #{receiverId}")
    int markRead(@Param("id") Long id, @Param("receiverId") Long receiverId);

    // 标记已读（types 为空则全部已读；传 types 只清对应类型，供按信箱已读）
    @Update("""
            <script>
            UPDATE notification SET is_read = 1
            WHERE receiver_id = #{receiverId} AND is_read = 0
            <if test="types != null and types.size() > 0">
              AND type IN
              <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
            </if>
            </script>
            """)
    int markAllRead(@Param("receiverId") Long receiverId, @Param("types") List<Integer> types);

    // 删除单条
    @Delete("DELETE FROM notification WHERE id = #{id} AND receiver_id = #{receiverId}")
    int deleteById(@Param("id") Long id, @Param("receiverId") Long receiverId);

    // 清空当前用户全部通知
    @Delete("DELETE FROM notification WHERE receiver_id = #{receiverId}")
    int clearAll(@Param("receiverId") Long receiverId);

    // 通知列表查询投影
    @Data
    class NotificationRow {
        private Long id;
        private Integer type;
        private Long noteId;
        private Long commentId;
        private String content;
        private Boolean isRead;
        private LocalDateTime createTime;
        private Long senderId;
        private String senderNickname;
        private String senderAvatar;
        // 关联笔记封面图 URL
        private String noteCover;
        // 被回复的父评论 id（type=4 时有值）
        private Long parentCommentId;
        // 被回复的原评论正文（type=4 时有值）
        private String replyToContent;
    }

    // 按类型未读数统计投影
    @Data
    class TypeUnreadRow {
        private Integer type;
        private Long cnt;
    }
}
