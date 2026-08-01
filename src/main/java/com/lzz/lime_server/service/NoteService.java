package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.request.PublishNoteRequest;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.NoteDetailResponse;
import com.lzz.lime_server.dto.response.NoteFeedResponse;
import com.lzz.lime_server.dto.response.NoteResponse;

import java.util.List;

public interface NoteService {
    NoteResponse publishNote(Long userId, PublishNoteRequest request);

    CursorPage<NoteFeedResponse> getFeed(Long cursor, int size, Long userId);

    CursorPage<NoteFeedResponse> getUserNotes(Long targetUserId, int statusVal, Long cursor, int size, Long currentUserId);

    void likeNote(Long noteId, Long userId);

    NoteDetailResponse getNoteDetail(Long noteId, Long currentUserId);

    void unlikeNote(Long noteId, Long userId);

    void favoriteNote(Long noteId, Long userId);

    void unfavoriteNote(Long noteId, Long userId);

    CursorPage<NoteFeedResponse> getLikedNotes(Long targetUserId, Long cursor, int size, Long currentUserId);

    CursorPage<NoteFeedResponse> getFavoritedNotes(Long targetUserId, Long cursor, int size, Long currentUserId);

    CursorPage<NoteFeedResponse> getViewedNotes(Long userId, Long cursor, int size);

    void deleteViewRecords(Long userId, List<Long> noteIds);

    void clearViewHistory(Long userId);
}
