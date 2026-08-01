package com.lzz.lime_server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzz.lime_server.entity.Note;
import lombok.Data;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    @Select("""
            <script>
            SELECT n.id, n.title, n.like_count,
                   ni.url AS cover_image,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note n
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN `user` u ON u.id = n.user_id
            WHERE n.status = 1 AND n.deleted = 0
            <if test="cursor != null">AND n.id &lt; #{cursor}</if>
            ORDER BY n.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "feedResultMap", value = {
            @Result(property = "id",             column = "id"),
            @Result(property = "title",          column = "title"),
            @Result(property = "likeCount",      column = "like_count"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectFeed(@Param("cursor") Long cursor, @Param("size") int size);

    /// 增加浏览量
    @Update("UPDATE note SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(@Param("id") Long id);

    @Select("""
            <script>
            SELECT n.id, n.title, n.like_count, n.status,
                   ni.url AS cover_image,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note n
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN `user` u ON u.id = n.user_id
            WHERE n.user_id = #{userId} AND n.status = #{statusVal} AND n.deleted = 0
            <if test="cursor != null">AND n.id &lt; #{cursor}</if>
            ORDER BY n.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "userNotesResultMap", value = {
            @Result(property = "id",             column = "id"),
            @Result(property = "title",          column = "title"),
            @Result(property = "likeCount",      column = "like_count"),
            @Result(property = "status",         column = "status"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectUserNotes(@Param("userId") Long userId, @Param("statusVal") int statusVal,
                                      @Param("cursor") Long cursor, @Param("size") int size);

    /**
     * selectFeed / selectUserNotes 方法返回的扁平化投影对象，
     * 在 Service 层中被转换为 NoteFeedResponse
     */
    @Data
    class NoteFeedRow {
        private Long id;
        private String title;
        private Integer likeCount;
        private Integer status;
        private String coverImage;
        private Long authorId;
        private String authorNickname;
        private String authorAvatar;
        // 点赞/收藏列表查询时填充，作为游标使用；其他查询为 null
        private Long cursorId;
    }

    @Select("""
            <script>
            SELECT nl.id AS cursor_id, n.id, n.title, n.like_count,
                   ni.url AS cover_image,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note_like nl
            JOIN note n ON n.id = nl.note_id AND n.deleted = 0 AND n.status = 1
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN `user` u ON u.id = n.user_id
            WHERE nl.user_id = #{userId}
            <if test="cursor != null">AND nl.id &lt; #{cursor}</if>
            ORDER BY nl.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "likedNotesResultMap", value = {
            @Result(property = "cursorId",       column = "cursor_id"),
            @Result(property = "id",             column = "id"),
            @Result(property = "title",          column = "title"),
            @Result(property = "likeCount",      column = "like_count"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectLikedNotes(@Param("userId") Long userId,
                                       @Param("cursor") Long cursor,
                                       @Param("size") int size);

    @Select("""
            <script>
            SELECT nf.id AS cursor_id, n.id, n.title, n.like_count,
                   ni.url AS cover_image,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note_fav nf
            JOIN note n ON n.id = nf.note_id AND n.deleted = 0 AND n.status = 1
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN `user` u ON u.id = n.user_id
            WHERE nf.user_id = #{userId}
            <if test="cursor != null">AND nf.id &lt; #{cursor}</if>
            ORDER BY nf.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "favoritedNotesResultMap", value = {
            @Result(property = "cursorId",       column = "cursor_id"),
            @Result(property = "id",             column = "id"),
            @Result(property = "title",          column = "title"),
            @Result(property = "likeCount",      column = "like_count"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectFavoritedNotes(@Param("userId") Long userId,
                                           @Param("cursor") Long cursor,
                                           @Param("size") int size);

    /**
     * 查询当前用户的浏览历史，按浏览时间倒序排列。
     * cursor 为上一页最后一条记录的浏览时间（epoch 毫秒），
     * cursorId 字段返回当前行浏览时间的 epoch 毫秒值，供下一次请求使用。
     */
    @Select("""
            <script>
            SELECT CAST(UNIX_TIMESTAMP(nv.create_time) * 1000 AS UNSIGNED) AS cursor_id,
                   n.id, n.title, n.like_count,
                   ni.url AS cover_image,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note_view nv
            JOIN note n ON n.id = nv.note_id AND n.deleted = 0 AND n.status = 1
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN `user` u ON u.id = n.user_id
            WHERE nv.user_id = #{userId}
            <if test="cursor != null">AND nv.create_time &lt; FROM_UNIXTIME(#{cursor} / 1000.0)</if>
            ORDER BY nv.create_time DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "viewedNotesResultMap", value = {
            @Result(property = "cursorId",       column = "cursor_id"),
            @Result(property = "id",             column = "id"),
            @Result(property = "title",          column = "title"),
            @Result(property = "likeCount",      column = "like_count"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectViewedNotes(@Param("userId") Long userId,
                                        @Param("cursor") Long cursor,
                                        @Param("size") int size);
}
