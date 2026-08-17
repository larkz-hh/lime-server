package com.lzz.lime_server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzz.lime_server.entity.User;
import lombok.Data;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 搜索用户（昵称/handle 匹配），匹配度优先排序：
     * 精确匹配 &gt; 前缀匹配 &gt; 包含匹配，同精度按已发布笔记数降序，再按 id 降序。
     * 游标为 "{matchRank}:{noteCount}:{id}" 复合游标。
     * 用派生表避免 match_rank / note_count 表达式在 WHERE 与 ORDER BY 中重复出现。
     */
    @Select("""
            <script>
            SELECT t.id, t.nickname, t.handle, t.avatar, t.note_count, t.match_rank
            FROM (
                SELECT u.id, u.nickname, u.handle, u.avatar,
                       (SELECT COUNT(*) FROM note n
                         WHERE n.user_id = u.id AND n.status = 1 AND n.deleted = 0) AS note_count,
                       CASE
                         WHEN u.handle = #{kw} OR u.nickname = #{kw} THEN 3
                         WHEN u.handle LIKE #{prefix} OR u.nickname LIKE #{prefix} THEN 2
                         ELSE 1
                       END AS match_rank
                FROM `user` u
                WHERE u.deleted = 0
                  AND (u.nickname LIKE #{likePattern} OR u.handle LIKE #{likePattern})
            ) t
            WHERE #{cursorRank} IS NULL
               OR t.match_rank &lt; #{cursorRank}
               OR (t.match_rank = #{cursorRank} AND t.note_count &lt; #{cursorNoteCount})
               OR (t.match_rank = #{cursorRank} AND t.note_count = #{cursorNoteCount} AND t.id &lt; #{cursorId})
            ORDER BY t.match_rank DESC, t.note_count DESC, t.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "userSearchResultMap", value = {
            @Result(property = "id",        column = "id"),
            @Result(property = "nickname",  column = "nickname"),
            @Result(property = "handle",    column = "handle"),
            @Result(property = "avatar",    column = "avatar"),
            @Result(property = "noteCount", column = "note_count"),
            @Result(property = "matchRank", column = "match_rank")
    })
    List<UserSearchRow> selectSearchUsers(@Param("kw") String kw,
                                          @Param("prefix") String prefix,
                                          @Param("likePattern") String likePattern,
                                          @Param("cursorRank") Integer cursorRank,
                                          @Param("cursorNoteCount") Long cursorNoteCount,
                                          @Param("cursorId") Long cursorId,
                                          @Param("size") int size);

    /**
     * selectSearchUsers 返回的扁平投影对象，Service 层转换为 UserSearchResult。
     * noteCount / matchRank 仅作排序与游标使用，不对外暴露。
     */
    @Data
    class UserSearchRow {
        private Long id;
        private String nickname;
        private String handle;
        private String avatar;
        private Long noteCount;
        private Integer matchRank;
    }
}
