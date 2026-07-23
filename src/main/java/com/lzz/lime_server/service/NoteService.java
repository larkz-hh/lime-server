package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.request.PublishNoteRequest;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.NoteFeedResponse;
import com.lzz.lime_server.dto.response.NoteResponse;

public interface NoteService {
    NoteResponse publishNote(Long userId, PublishNoteRequest request);

    CursorPage<NoteFeedResponse> getFeed(Long cursor, int size);
}
