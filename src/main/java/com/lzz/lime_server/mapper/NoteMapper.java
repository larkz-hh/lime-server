package com.lzz.lime_server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzz.lime_server.entity.Note;
import lombok.Data;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    @Select("""
            <script>
            SELECT n.id, n.title, n.like_count, n.note_type,
                   COALESCE(nv.cover_url, ni.url) AS cover_image,
                   COALESCE(nv.cover_width, ni.width) AS cover_width,
                   COALESCE(nv.cover_height, ni.height) AS cover_height,
                   nv.duration_ms AS video_duration_ms, nv.video_width, nv.video_height,
                   nv.original_url AS video_play_url,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note n
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN note_video nv ON nv.note_id = n.id
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
            @Result(property = "noteType",       column = "note_type"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "coverWidth",     column = "cover_width"),
            @Result(property = "coverHeight",    column = "cover_height"),
            @Result(property = "videoDurationMs", column = "video_duration_ms"),
            @Result(property = "videoWidth",     column = "video_width"),
            @Result(property = "videoHeight",    column = "video_height"),
            @Result(property = "videoPlayUrl",   column = "video_play_url"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectFeed(@Param("cursor") Long cursor, @Param("size") int size);

    /// 视频流查询：仅视频笔记，id 倒序；seedNoteId 用于从指定笔记开始取（含自身，进视频页首屏定位）
    /// orientation 可选过滤横竖屏：LANDSCAPE=宽>高（全屏横屏会话用），PORTRAIT=宽<=高
    @Select("""
            <script>
            SELECT n.id, n.title, n.like_count, n.note_type,
                   COALESCE(nv.cover_url, ni.url) AS cover_image,
                   COALESCE(nv.cover_width, ni.width) AS cover_width,
                   COALESCE(nv.cover_height, ni.height) AS cover_height,
                   nv.duration_ms AS video_duration_ms, nv.video_width, nv.video_height,
                   nv.original_url AS video_play_url,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note n
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN note_video nv ON nv.note_id = n.id
            LEFT JOIN `user` u ON u.id = n.user_id
            WHERE n.status = 1 AND n.deleted = 0 AND n.note_type = 2
            <if test="cursor != null">AND n.id &lt; #{cursor}</if>
            <if test="seedNoteId != null">AND n.id &lt;= #{seedNoteId}</if>
            <if test="orientation == 'LANDSCAPE'">AND nv.video_width &gt; nv.video_height</if>
            <if test="orientation == 'PORTRAIT'">AND nv.video_width &lt;= nv.video_height</if>
            ORDER BY n.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "videoFeedResultMap", value = {
            @Result(property = "id",             column = "id"),
            @Result(property = "title",          column = "title"),
            @Result(property = "likeCount",      column = "like_count"),
            @Result(property = "noteType",       column = "note_type"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "coverWidth",     column = "cover_width"),
            @Result(property = "coverHeight",    column = "cover_height"),
            @Result(property = "videoDurationMs", column = "video_duration_ms"),
            @Result(property = "videoWidth",     column = "video_width"),
            @Result(property = "videoHeight",    column = "video_height"),
            @Result(property = "videoPlayUrl",   column = "video_play_url"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectVideoFeed(@Param("cursor") Long cursor, @Param("seedNoteId") Long seedNoteId,
                                      @Param("orientation") String orientation, @Param("size") int size);

    /// 增加浏览量
    @Update("UPDATE note SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(@Param("id") Long id);

    @Select("""
            <script>
            SELECT n.id, n.title, n.like_count, n.status, n.view_count, n.note_type,
                   COALESCE(nv.cover_url, ni.url) AS cover_image,
                   COALESCE(nv.cover_width, ni.width) AS cover_width,
                   COALESCE(nv.cover_height, ni.height) AS cover_height,
                   nv.duration_ms AS video_duration_ms, nv.video_width, nv.video_height,
                   nv.original_url AS video_play_url,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note n
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN note_video nv ON nv.note_id = n.id
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
            @Result(property = "viewCount",      column = "view_count"),
            @Result(property = "noteType",       column = "note_type"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "coverWidth",     column = "cover_width"),
            @Result(property = "coverHeight",    column = "cover_height"),
            @Result(property = "videoDurationMs", column = "video_duration_ms"),
            @Result(property = "videoWidth",     column = "video_width"),
            @Result(property = "videoHeight",    column = "video_height"),
            @Result(property = "videoPlayUrl",   column = "video_play_url"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectUserNotes(@Param("userId") Long userId, @Param("statusVal") int statusVal,
                                      @Param("cursor") Long cursor, @Param("size") int size);

    /**
     * selectFeed / selectVideoFeed / selectUserNotes 等方法返回的扁平化投影对象，
     * 在 Service 层中被转换为 NoteFeedResponse
     */
    @Data
    class NoteFeedRow {
        private Long id;
        private String title;
        private Integer likeCount;
        private Integer status;
        // selectUserNotes 时填充，其他查询为 null
        private Integer viewCount;
        private Integer noteType;
        private String coverImage;
        // 封面宽高（客户端上报；图文=第一张图宽高，视频=封面图宽高；历史数据可能为 null）
        private Integer coverWidth;
        private Integer coverHeight;
        // 视频笔记字段（视频行才有值，图文行为 null）
        private Long videoDurationMs;
        private Integer videoWidth;
        private Integer videoHeight;
        private String videoPlayUrl;
        private Long authorId;
        private String authorNickname;
        private String authorAvatar;
        // 点赞/收藏列表查询时填充，作为游标使用；其他查询为 null
        private Long cursorId;
        // 浏览历史查询时填充；其他查询为 null
        private LocalDateTime viewTime;
        // 搜索查询时填充：排序分值（composite=综合分，likes/comments/favs=对应计数，latest=发布时间 epoch 毫秒）
        private Long sortScore;
        // 搜索查询时填充：发布时间 epoch 毫秒，作为二级排序键与游标
        private Long createTimeMs;
    }

    @Select("""
            <script>
            SELECT nl.id AS cursor_id, n.id, n.title, n.like_count, n.note_type,
                   COALESCE(nv.cover_url, ni.url) AS cover_image,
                   COALESCE(nv.cover_width, ni.width) AS cover_width,
                   COALESCE(nv.cover_height, ni.height) AS cover_height,
                   nv.duration_ms AS video_duration_ms, nv.video_width, nv.video_height,
                   nv.original_url AS video_play_url,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note_like nl
            JOIN note n ON n.id = nl.note_id AND n.deleted = 0 AND n.status = 1
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN note_video nv ON nv.note_id = n.id
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
            @Result(property = "noteType",       column = "note_type"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "coverWidth",     column = "cover_width"),
            @Result(property = "coverHeight",    column = "cover_height"),
            @Result(property = "videoDurationMs", column = "video_duration_ms"),
            @Result(property = "videoWidth",     column = "video_width"),
            @Result(property = "videoHeight",    column = "video_height"),
            @Result(property = "videoPlayUrl",   column = "video_play_url"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectLikedNotes(@Param("userId") Long userId,
                                       @Param("cursor") Long cursor,
                                       @Param("size") int size);

    @Select("""
            <script>
            SELECT nf.id AS cursor_id, n.id, n.title, n.like_count, n.note_type,
                   COALESCE(nv.cover_url, ni.url) AS cover_image,
                   COALESCE(nv.cover_width, ni.width) AS cover_width,
                   COALESCE(nv.cover_height, ni.height) AS cover_height,
                   nv.duration_ms AS video_duration_ms, nv.video_width, nv.video_height,
                   nv.original_url AS video_play_url,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note_fav nf
            JOIN note n ON n.id = nf.note_id AND n.deleted = 0 AND n.status = 1
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN note_video nv ON nv.note_id = n.id
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
            @Result(property = "noteType",       column = "note_type"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "coverWidth",     column = "cover_width"),
            @Result(property = "coverHeight",    column = "cover_height"),
            @Result(property = "videoDurationMs", column = "video_duration_ms"),
            @Result(property = "videoWidth",     column = "video_width"),
            @Result(property = "videoHeight",    column = "video_height"),
            @Result(property = "videoPlayUrl",   column = "video_play_url"),
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
                   nv.create_time AS view_time,
                   n.id, n.title, n.like_count, n.note_type,
                   COALESCE(v.cover_url, ni.url) AS cover_image,
                   COALESCE(v.cover_width, ni.width) AS cover_width,
                   COALESCE(v.cover_height, ni.height) AS cover_height,
                   v.duration_ms AS video_duration_ms, v.video_width, v.video_height,
                   v.original_url AS video_play_url,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar
            FROM note_view nv
            JOIN note n ON n.id = nv.note_id AND n.deleted = 0 AND n.status = 1
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN note_video v ON v.note_id = n.id
            LEFT JOIN `user` u ON u.id = n.user_id
            WHERE nv.user_id = #{userId}
            <if test="cursor != null">AND nv.create_time &lt; FROM_UNIXTIME(#{cursor} / 1000.0)</if>
            ORDER BY nv.create_time DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "viewedNotesResultMap", value = {
            @Result(property = "cursorId",       column = "cursor_id"),
            @Result(property = "viewTime",       column = "view_time"),
            @Result(property = "id",             column = "id"),
            @Result(property = "title",          column = "title"),
            @Result(property = "likeCount",      column = "like_count"),
            @Result(property = "noteType",       column = "note_type"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "coverWidth",     column = "cover_width"),
            @Result(property = "coverHeight",    column = "cover_height"),
            @Result(property = "videoDurationMs", column = "video_duration_ms"),
            @Result(property = "videoWidth",     column = "video_width"),
            @Result(property = "videoHeight",    column = "video_height"),
            @Result(property = "videoPlayUrl",   column = "video_play_url"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar")
    })
    List<NoteFeedRow> selectViewedNotes(@Param("userId") Long userId,
                                        @Param("cursor") Long cursor,
                                        @Param("size") int size);

    /**
     * 关键词搜索笔记，标题/正文匹配（FULLTEXT ngram）或作者昵称/handle 匹配（召回其已发布笔记）。
     * 5 种排序模式统一用 sort_score 排序；kw 为 null 时走 LIKE 兜底（单字或含空格关键词）。
     * 排序：sort_score DESC, 发布时间 DESC, id DESC；游标统一为 "{sortScore}:{createTimeMs}:{id}"。
     * create_time 历史数据可能为 NULL，统一用 COALESCE(create_time, update_time) 兜底。
     */
    @Select("""
            <script>
            SELECT n.id, n.title, n.like_count, n.note_type,
                   COALESCE(nv.cover_url, ni.url) AS cover_image,
                   COALESCE(nv.cover_width, ni.width) AS cover_width,
                   COALESCE(nv.cover_height, ni.height) AS cover_height,
                   nv.duration_ms AS video_duration_ms, nv.video_width, nv.video_height,
                   nv.original_url AS video_play_url,
                   u.id AS author_id, u.nickname AS author_nickname, u.avatar AS author_avatar,
                   UNIX_TIMESTAMP(COALESCE(n.create_time, n.update_time)) * 1000 AS create_time_ms,
                   <choose>
                     <when test="sort == 'composite'">
                       <if test="kw != null">CAST(MATCH(n.title, n.content) AGAINST(#{kw} IN NATURAL LANGUAGE MODE) * 10000 AS SIGNED) * 5 +</if>
                       LEAST(n.like_count * 2 + n.comment_count * 3 + n.fav_count * 2, 100000)
                       - GREATEST(DATEDIFF(#{today}, DATE(COALESCE(n.create_time, n.update_time))), 0)
                     </when>
                     <when test="sort == 'likes'">n.like_count</when>
                     <when test="sort == 'comments'">n.comment_count</when>
                     <when test="sort == 'favs'">n.fav_count</when>
                     <otherwise>UNIX_TIMESTAMP(COALESCE(n.create_time, n.update_time)) * 1000</otherwise>
                   </choose> AS sort_score
            FROM note n
            LEFT JOIN note_image ni ON ni.note_id = n.id
                AND ni.sort_order = (SELECT MIN(sort_order) FROM note_image WHERE note_id = n.id)
            LEFT JOIN note_video nv ON nv.note_id = n.id
            LEFT JOIN `user` u ON u.id = n.user_id AND u.deleted = 0
            WHERE n.status = 1 AND n.deleted = 0
              <if test="noteTypeFilter != null">AND n.note_type = #{noteTypeFilter}</if>
              <if test="fromTime != null">AND COALESCE(n.create_time, n.update_time) &gt;= #{fromTime}</if>
              AND (
                <choose>
                  <when test="kw != null">
                    MATCH(n.title, n.content) AGAINST(#{kw} IN NATURAL LANGUAGE MODE) &gt; 0
                    OR u.nickname LIKE #{likePattern}
                    OR u.handle LIKE #{likePattern}
                  </when>
                  <otherwise>
                    n.title LIKE #{likePattern} OR n.content LIKE #{likePattern}
                    OR u.nickname LIKE #{likePattern} OR u.handle LIKE #{likePattern}
                  </otherwise>
                </choose>
              )
              AND (
                #{cursorScore} IS NULL
                OR (<choose>
                          <when test="sort == 'composite'">
                            <if test="kw != null">CAST(MATCH(n.title, n.content) AGAINST(#{kw} IN NATURAL LANGUAGE MODE) * 10000 AS SIGNED) * 5 +</if>
                            LEAST(n.like_count * 2 + n.comment_count * 3 + n.fav_count * 2, 100000)
                            - GREATEST(DATEDIFF(#{today}, DATE(COALESCE(n.create_time, n.update_time))), 0)
                          </when>
                          <when test="sort == 'likes'">n.like_count</when>
                          <when test="sort == 'comments'">n.comment_count</when>
                          <when test="sort == 'favs'">n.fav_count</when>
                          <otherwise>UNIX_TIMESTAMP(COALESCE(n.create_time, n.update_time)) * 1000</otherwise>
                        </choose> &lt; #{cursorScore})
                OR (<choose>
                          <when test="sort == 'composite'">
                            <if test="kw != null">CAST(MATCH(n.title, n.content) AGAINST(#{kw} IN NATURAL LANGUAGE MODE) * 10000 AS SIGNED) * 5 +</if>
                            LEAST(n.like_count * 2 + n.comment_count * 3 + n.fav_count * 2, 100000)
                            - GREATEST(DATEDIFF(#{today}, DATE(COALESCE(n.create_time, n.update_time))), 0)
                          </when>
                          <when test="sort == 'likes'">n.like_count</when>
                          <when test="sort == 'comments'">n.comment_count</when>
                          <when test="sort == 'favs'">n.fav_count</when>
                          <otherwise>UNIX_TIMESTAMP(COALESCE(n.create_time, n.update_time)) * 1000</otherwise>
                        </choose> = #{cursorScore}
                        AND UNIX_TIMESTAMP(COALESCE(n.create_time, n.update_time)) * 1000 &lt; #{cursorTimeMs})
                OR (<choose>
                          <when test="sort == 'composite'">
                            <if test="kw != null">CAST(MATCH(n.title, n.content) AGAINST(#{kw} IN NATURAL LANGUAGE MODE) * 10000 AS SIGNED) * 5 +</if>
                            LEAST(n.like_count * 2 + n.comment_count * 3 + n.fav_count * 2, 100000)
                            - GREATEST(DATEDIFF(#{today}, DATE(COALESCE(n.create_time, n.update_time))), 0)
                          </when>
                          <when test="sort == 'likes'">n.like_count</when>
                          <when test="sort == 'comments'">n.comment_count</when>
                          <when test="sort == 'favs'">n.fav_count</when>
                          <otherwise>UNIX_TIMESTAMP(COALESCE(n.create_time, n.update_time)) * 1000</otherwise>
                        </choose> = #{cursorScore}
                        AND UNIX_TIMESTAMP(COALESCE(n.create_time, n.update_time)) * 1000 = #{cursorTimeMs}
                        AND n.id &lt; #{cursorId})
              )
            ORDER BY sort_score DESC, create_time_ms DESC, n.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "searchResultMap", value = {
            @Result(property = "id",             column = "id"),
            @Result(property = "title",          column = "title"),
            @Result(property = "likeCount",      column = "like_count"),
            @Result(property = "noteType",       column = "note_type"),
            @Result(property = "coverImage",     column = "cover_image"),
            @Result(property = "coverWidth",     column = "cover_width"),
            @Result(property = "coverHeight",    column = "cover_height"),
            @Result(property = "videoDurationMs", column = "video_duration_ms"),
            @Result(property = "videoWidth",     column = "video_width"),
            @Result(property = "videoHeight",    column = "video_height"),
            @Result(property = "videoPlayUrl",   column = "video_play_url"),
            @Result(property = "authorId",       column = "author_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "authorAvatar",   column = "author_avatar"),
            @Result(property = "sortScore",      column = "sort_score"),
            @Result(property = "createTimeMs",   column = "create_time_ms")
    })
    List<NoteFeedRow> selectSearch(@Param("kw") String kw,
                                   @Param("likePattern") String likePattern,
                                   @Param("today") LocalDate today,
                                   @Param("fromTime") LocalDateTime fromTime,
                                   @Param("sort") String sort,
                                   @Param("noteTypeFilter") Integer noteTypeFilter,
                                   @Param("cursorScore") Long cursorScore,
                                   @Param("cursorTimeMs") Long cursorTimeMs,
                                   @Param("cursorId") Long cursorId,
                                   @Param("size") int size);

    /**
     * 搜索联想，从已发布笔记标题中按前缀匹配取热度较高的标题。
     */
    @Select("""
            SELECT title
            FROM note
            WHERE status = 1 AND deleted = 0
              AND title IS NOT NULL AND title <> ''
              AND title LIKE #{prefix}
            GROUP BY title
            ORDER BY MAX(like_count) DESC, MAX(id) DESC
            LIMIT #{size}
            """)
    List<String> selectSuggestTitles(@Param("prefix") String prefix, @Param("size") int size);

    /**
     * 用户主页统计，已发布笔记数 + 笔记收到的总点赞/收藏。
     */
    @Select("SELECT COUNT(*) AS note_count, " +
            "COALESCE(SUM(like_count), 0) AS like_total, " +
            "COALESCE(SUM(fav_count), 0) AS fav_total " +
            "FROM note WHERE user_id = #{userId} AND status = 1 AND deleted = 0")
    UserNoteStats selectUserNoteStats(@Param("userId") Long userId);

    // 用户主页统计投影
    @Data
    class UserNoteStats {
        private Long noteCount;
        private Long likeTotal;
        private Long favTotal;
    }
}
