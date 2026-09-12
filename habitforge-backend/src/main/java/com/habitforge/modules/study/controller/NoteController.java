package com.habitforge.modules.study.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.NoteCreateRequest;
import com.habitforge.modules.study.dto.NoteImageResponse;
import com.habitforge.modules.study.dto.NoteResponse;
import com.habitforge.modules.study.dto.NoteSummaryResponse;
import com.habitforge.modules.study.dto.NoteUpdateRequest;
import com.habitforge.modules.study.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public Result<NoteResponse> create(@Valid @RequestBody NoteCreateRequest request) {
        return Result.success(noteService.create(SecurityUtils.getCurrentUserId(), request), "笔记创建成功");
    }

    /** 摘要列表(可按科目/章节过滤, keyword 命中 title/content) */
    @GetMapping
    public Result<List<NoteSummaryResponse>> list(@RequestParam(required = false) String subjectId,
                                                  @RequestParam(required = false) String chapterId,
                                                  @RequestParam(required = false) String keyword) {
        return Result.success(noteService.listMine(SecurityUtils.getCurrentUserId(), subjectId, chapterId, keyword));
    }

    @GetMapping("/{id}")
    public Result<NoteResponse> detail(@PathVariable String id) {
        return Result.success(noteService.getDetail(SecurityUtils.getCurrentUserId(), id));
    }

    /** 各字段可选, null 表示不修改 */
    @PutMapping("/{id}")
    public Result<NoteResponse> update(@PathVariable String id, @Valid @RequestBody NoteUpdateRequest request) {
        return Result.success(noteService.update(SecurityUtils.getCurrentUserId(), id, request), "更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        noteService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "删除成功");
    }

    /** 上传图片(multipart, ≤5MB, jpeg/png/webp/gif; 访问走 /images/{objectKey} 代理) */
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<NoteImageResponse> uploadImage(@PathVariable String id,
                                                 @RequestParam("file") MultipartFile file) {
        return Result.success(noteService.uploadImage(SecurityUtils.getCurrentUserId(), id, file), "上传成功");
    }

    @GetMapping("/{id}/images")
    public Result<List<NoteImageResponse>> listImages(@PathVariable String id) {
        return Result.success(noteService.listImages(SecurityUtils.getCurrentUserId(), id));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public Result<Void> deleteImage(@PathVariable String id, @PathVariable String imageId) {
        noteService.deleteImage(SecurityUtils.getCurrentUserId(), id, imageId);
        return Result.success(null, "图片已删除");
    }
}
