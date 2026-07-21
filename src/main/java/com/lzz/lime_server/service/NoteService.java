package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.request.PublishNoteRequest;
import com.lzz.lime_server.dto.response.NoteResponse;

public interface NoteService {
    NoteResponse publishNote(Long userId, PublishNoteRequest request);
}
