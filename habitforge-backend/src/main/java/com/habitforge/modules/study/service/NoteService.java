package com.habitforge.modules.study.service;

import com.habitforge.modules.study.dto.NoteCreateRequest;
import com.habitforge.modules.study.dto.NoteImageResponse;
import com.habitforge.modules.study.dto.NoteResponse;
import com.habitforge.modules.study.dto.NoteSummaryResponse;
import com.habitforge.modules.study.dto.NoteUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NoteService {

    NoteResponse create(String userId, NoteCreateRequest request);

    /** 摘要列表(keyword 命中 title/content), 按 updatedAt desc */
    List<NoteSummaryResponse> listMine(String userId, String subjectId, String chapterId, String keyword);

    NoteResponse getDetail(String userId, String id);

    /** 各字段 null=不改 */
    NoteResponse update(String userId, String id, NoteUpdateRequest request);

    /** 删除笔记并 best-effort 清理 MinIO 图片 */
    void delete(String userId, String id);

    /** 上传图片(完全照搬 journal 流程: 限流/类型大小/ImageIO 宽高/先传后落库失败回收) */
    NoteImageResponse uploadImage(String userId, String noteId, MultipartFile file);

    List<NoteImageResponse> listImages(String userId, String noteId);

    void deleteImage(String userId, String noteId, String imageId);
}
