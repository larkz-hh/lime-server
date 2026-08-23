package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.SearchReportRequest;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.HotSearchWord;
import com.lzz.lime_server.dto.response.NoteFeedResponse;
import com.lzz.lime_server.dto.response.UserSearchResult;
import com.lzz.lime_server.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private static final Set<String> SORTS = Set.of("composite", "latest", "likes", "comments", "favs");
    private static final Set<String> WITHIN = Set.of("all", "day", "week", "halfYear");
    private static final Set<String> TYPES = Set.of("all", "image", "video");

    /// 关键词搜索笔记，Cursor 分页；sort / within（发布时间范围）/ type（笔记类型）可自由组合
    @GetMapping("/notes")
    public Result<CursorPage<NoteFeedResponse>> searchNotes(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "composite") String sort,
            @RequestParam(defaultValue = "all") String within,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size) {
        if (!SORTS.contains(sort)) {
            throw new BusinessException("sort 参数非法，可选值：composite / latest / likes / comments / favs");
        }
        if (!WITHIN.contains(within)) {
            throw new BusinessException("within 参数非法，可选值：all / day / week / halfYear");
        }
        if (!TYPES.contains(type)) {
            throw new BusinessException("type 参数非法，可选值：all / image / video");
        }
        size = Math.min(size, 50);
        return Result.success(searchService.searchNotes(keyword, sort, within, type, cursor, size, currentUserId()));
    }

    /// 搜索用户（昵称/handle 匹配），匹配度优先排序，Cursor 分页
    @GetMapping("/users")
    public Result<CursorPage<UserSearchResult>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(searchService.searchUsers(keyword, cursor, size, currentUserId()));
    }

    /// 搜索联想,输入前缀实时返回提示词（已发布笔记标题 + 当日热搜词，去重）
    @GetMapping("/suggest")
    public Result<List<String>> suggest(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 20);
        return Result.success(searchService.suggest(q, size));
    }

    /// 当日热搜榜，按点击次数降序
    @GetMapping("/hot")
    public Result<List<HotSearchWord>> hot(@RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(searchService.hotSearchWords(size));
    }

    /// 上报一次搜索点击（前端在用户确认搜索时调用，避免翻页重复计数）
    @PostMapping("/report")
    public Result<Void> report(@Valid @RequestBody SearchReportRequest request) {
        searchService.reportSearch(request.getKeyword());
        return Result.success();
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
