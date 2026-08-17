package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.HotSearchWord;
import com.lzz.lime_server.dto.response.NoteFeedResponse;
import com.lzz.lime_server.dto.response.UserSearchResult;

import java.util.List;

public interface SearchService {

    CursorPage<NoteFeedResponse> searchNotes(String keyword, String sort, String within, String cursor, int size, Long currentUserId);

    CursorPage<UserSearchResult> searchUsers(String keyword, String cursor, int size, Long currentUserId);

    List<String> suggest(String q, int size);

    List<HotSearchWord> hotSearchWords(int size);

    void reportSearch(String keyword);
}
