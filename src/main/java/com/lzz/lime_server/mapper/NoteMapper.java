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

    /**
     * selectFeed 方法返回的扁平化投影对象，
     * 在 Service 层中被转换为 NoteFeedResponse
     */
    @Data
    class NoteFeedRow {
        private Long id;
        private String title;
        private Integer likeCount;
        private String coverImage;
        private Long authorId;
        private String authorNickname;
        private String authorAvatar;
    }
}
