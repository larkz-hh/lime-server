package com.lzz.lime_server.service.impl;

import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.PublishNoteRequest;
import com.lzz.lime_server.dto.response.NoteResponse;
import com.lzz.lime_server.entity.Note;
import com.lzz.lime_server.entity.NoteImage;
import com.lzz.lime_server.mapper.NoteImageMapper;
import com.lzz.lime_server.mapper.NoteMapper;
import com.lzz.lime_server.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;


/**
 * 笔记接口实现类
 * <p>负责笔记的发布、更新等</p>
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;
    private final NoteImageMapper noteImageMapper;

    /**
     * 发布图文笔记
     * <p>事务控制:确保笔记主体与图片数据同时成功或同时回滚</p>
     *
     * @param userId  当前登录用户的ID（从JWT中解析获取）
     * @param request 发布笔记的请求参数
     * @return 发布成功后的笔记响应数据
     * @throws BusinessException 当标题和正文同时为空时抛出业务异常
     */
    @Override
    @Transactional
    public NoteResponse publishNote(Long userId, PublishNoteRequest request) {
        if (!StringUtils.hasText(request.getTitle()) && !StringUtils.hasText(request.getContent())) {
            throw new BusinessException("标题和正文不能同时为空");
        }

        // 构建并保存笔记主体信息
        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        note.setLikeCount(0);
        note.setFavCount(0);
        note.setViewCount(0);
        noteMapper.insert(note);

        // 批量构建并保存笔记关联的图片数据
        List<NoteImage> images = request.getImages().stream().map(item -> {
            NoteImage img = new NoteImage();
            img.setNoteId(note.getId());// 绑定刚生成的笔记ID
            img.setUrl(item.getUrl());
            img.setSortOrder(item.getSortOrder());
            return img;
        }).toList();
        images.forEach(noteImageMapper::insert);// 逐条插入图片

        return toResponse(note, images);
    }

    /**
     * 将笔记实体和图片列表转换为前端响应DTO
     *
     * @param note   笔记实体
     * @param images 笔记关联的图片列表
     * @return 组装好的 NoteResponse 对象
     */
    private NoteResponse toResponse(Note note, List<NoteImage> images) {
        NoteResponse resp = new NoteResponse();
        resp.setId(note.getId());
        resp.setUserId(note.getUserId());
        resp.setTitle(note.getTitle());
        resp.setContent(note.getContent());
        resp.setStatus(note.getStatus());
        resp.setCreateTime(note.getCreateTime());
        resp.setUpdateTime(note.getUpdateTime());
        // 转换图片列表
        resp.setImages(images.stream().map(img -> {
            NoteResponse.ImageItem item = new NoteResponse.ImageItem();
            item.setId(img.getId());
            item.setUrl(img.getUrl());
            item.setSortOrder(img.getSortOrder());
            return item;
        }).toList());
        return resp;
    }
}
