package com.lzz.lime_server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzz.lime_server.entity.NoteView;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface NoteViewMapper extends BaseMapper<NoteView> {

    /// 同一用户对同一笔记已有记录则更新时间，使其重新出现在历史顶部
    @Insert("INSERT INTO note_view (user_id, note_id) VALUES (#{userId}, #{noteId}) " +
            "ON DUPLICATE KEY UPDATE create_time = CURRENT_TIMESTAMP(3)")
    void upsertView(@Param("userId") Long userId, @Param("noteId") Long noteId);

    /// 删除早于指定时间的浏览记录，用于定时清理过期历史
    @Delete("DELETE FROM note_view WHERE create_time < #{threshold}")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
