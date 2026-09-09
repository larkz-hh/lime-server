package com.lzz.lime_server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzz.lime_server.entity.UserFollow;
import lombok.Data;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    // 查询某用户是否已关注目标用户
    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    long existsFollow(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    // 某用户的关注数
    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{userId}")
    long countFollowing(@Param("userId") Long userId);

    // 某用户的粉丝数
    @Select("SELECT COUNT(*) FROM user_follow WHERE followee_id = #{userId}")
    long countFollowers(@Param("userId") Long userId);

    // 关注列表（游标分页，按关注时间倒序）
    @Select("""
            <script>
            SELECT uf.id AS cursor_id, u.id, u.nickname, u.handle, u.avatar, u.bio
            FROM user_follow uf
            JOIN `user` u ON u.id = uf.followee_id AND u.deleted = 0
            WHERE uf.follower_id = #{userId}
            <if test="cursor != null">AND uf.id &lt; #{cursor}</if>
            ORDER BY uf.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "followUserBriefMap", value = {
            @Result(property = "cursorId", column = "cursor_id"),
            @Result(property = "id",       column = "id"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "handle",   column = "handle"),
            @Result(property = "avatar",   column = "avatar"),
            @Result(property = "bio",      column = "bio")
    })
    List<UserBriefRow> selectFollowing(@Param("userId") Long userId,
                                       @Param("cursor") Long cursor,
                                       @Param("size") int size);

    // 粉丝列表（游标分页，按被关注时间倒序）
    @Select("""
            <script>
            SELECT uf.id AS cursor_id, u.id, u.nickname, u.handle, u.avatar, u.bio
            FROM user_follow uf
            JOIN `user` u ON u.id = uf.follower_id AND u.deleted = 0
            WHERE uf.followee_id = #{userId}
            <if test="cursor != null">AND uf.id &lt; #{cursor}</if>
            ORDER BY uf.id DESC
            LIMIT #{size}
            </script>
            """)
    @Results(id = "followerUserBriefMap", value = {
            @Result(property = "cursorId", column = "cursor_id"),
            @Result(property = "id",       column = "id"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "handle",   column = "handle"),
            @Result(property = "avatar",   column = "avatar"),
            @Result(property = "bio",      column = "bio")
    })
    List<UserBriefRow> selectFollowers(@Param("userId") Long userId,
                                       @Param("cursor") Long cursor,
                                       @Param("size") int size);

    // 互关好友（双向关注），供群聊拉人
    @Select("""
            <script>
            SELECT u.id, u.nickname, u.handle, u.avatar, u.bio
            FROM user_follow f1
            JOIN user_follow f2
                 ON f2.follower_id = f1.followee_id
                AND f2.followee_id = f1.follower_id
            JOIN `user` u ON u.id = f1.followee_id AND u.deleted = 0
            WHERE f1.follower_id = #{userId}
            ORDER BY f1.id DESC
            </script>
            """)
    @ResultMap("followUserBriefMap")
    List<UserBriefRow> selectMutualFriends(@Param("userId") Long userId);

    // 批量统计粉丝数供列表回填，避免逐行 COUNT
    @Select("""
            <script>
            SELECT followee_id AS userId, COUNT(*) AS cnt
            FROM user_follow
            WHERE followee_id IN
            <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
            GROUP BY followee_id
            </script>
            """)
    List<FollowerCountRow> countFollowersBatch(@Param("userIds") List<Long> userIds);

    // 关注/粉丝列表的用户简要投影
    @Data
    class UserBriefRow {
        private Long cursorId;
        private Long id;
        private String nickname;
        private String handle;
        private String avatar;
        private String bio;
    }

    @Data
    class FollowerCountRow {
        private Long userId;
        private Long cnt;
    }
}
