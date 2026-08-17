package com.habitforge.modules.journal.controller;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.journal.dto.HabitLinkRequest;
import com.habitforge.modules.journal.dto.JournalCreateRequest;
import com.habitforge.modules.journal.dto.JournalDetailResponse;
import com.habitforge.modules.journal.dto.JournalSummaryResponse;
import com.habitforge.modules.journal.dto.JournalUpdateRequest;
import com.habitforge.modules.journal.service.JournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/journals")
@RequiredArgsConstructor
public class JournalController {

    private final JournalService journalService;

    @PostMapping
    public Result<JournalDetailResponse> create(@Valid @RequestBody JournalCreateRequest request) {
        return Result.success(journalService.create(SecurityUtils.getCurrentUserId(), request), "日记保存成功");
    }

    /** 今日日记（未写返回 null） */
    @GetMapping("/today")
    public Result<JournalDetailResponse> today() {
        return Result.success(journalService.getToday(SecurityUtils.getCurrentUserId()));
    }

    /** 按日期查询（date 单天）或按范围查询（from/to，≤366天） */
    @GetMapping
    public Result<List<JournalSummaryResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        if (date != null) {
            return Result.success(journalService.listByRange(userId, date, date));
        }
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供 date 或 from/to 参数");
        }
        return Result.success(journalService.listByRange(userId, from, to));
    }

    /** 某习惯关联的日记 */
    @GetMapping("/habit/{habitId}")
    public Result<List<JournalSummaryResponse>> listByHabit(@PathVariable String habitId) {
        return Result.success(journalService.listByHabit(SecurityUtils.getCurrentUserId(), habitId));
    }

    @GetMapping("/{id}")
    public Result<JournalDetailResponse> detail(@PathVariable String id) {
        return Result.success(journalService.getDetail(SecurityUtils.getCurrentUserId(), id));
    }

    @PutMapping("/{id}")
    public Result<JournalDetailResponse> update(@PathVariable String id, @Valid @RequestBody JournalUpdateRequest request) {
        return Result.success(journalService.update(SecurityUtils.getCurrentUserId(), id, request), "更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        journalService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "删除成功");
    }

    /** 关联习惯（重复关联幂等） */
    @PostMapping("/{id}/habits")
    public Result<JournalDetailResponse> addHabit(@PathVariable String id, @Valid @RequestBody HabitLinkRequest request) {
        return Result.success(journalService.addHabit(SecurityUtils.getCurrentUserId(), id, request.getHabitId()), "关联成功");
    }

    @DeleteMapping("/{id}/habits/{habitId}")
    public Result<Void> removeHabit(@PathVariable String id, @PathVariable String habitId) {
        journalService.removeHabit(SecurityUtils.getCurrentUserId(), id, habitId);
        return Result.success(null, "已取消关联");
    }

    /** 上传图片（multipart，≤5MB，jpeg/png/webp/gif） */
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<JournalDetailResponse.ImageInfo> uploadImage(@PathVariable String id,
                                                               @RequestParam("file") MultipartFile file) {
        return Result.success(journalService.uploadImage(SecurityUtils.getCurrentUserId(), id, file), "上传成功");
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public Result<Void> deleteImage(@PathVariable String id, @PathVariable String imageId) {
        journalService.deleteImage(SecurityUtils.getCurrentUserId(), id, imageId);
        return Result.success(null, "图片已删除");
    }
}
