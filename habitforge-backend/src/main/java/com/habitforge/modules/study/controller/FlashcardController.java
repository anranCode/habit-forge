package com.habitforge.modules.study.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.FlashcardCreateRequest;
import com.habitforge.modules.study.dto.FlashcardQueueResponse;
import com.habitforge.modules.study.dto.FlashcardResponse;
import com.habitforge.modules.study.dto.FlashcardReviewRequest;
import com.habitforge.modules.study.dto.FlashcardReviewResultResponse;
import com.habitforge.modules.study.dto.FlashcardStatsResponse;
import com.habitforge.modules.study.dto.FlashcardUpdateRequest;
import com.habitforge.modules.study.service.FlashcardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flashcards")
@RequiredArgsConstructor
public class FlashcardController {

    private final FlashcardService flashcardService;

    @PostMapping
    public Result<FlashcardResponse> create(@Valid @RequestBody FlashcardCreateRequest request) {
        return Result.success(flashcardService.create(SecurityUtils.getCurrentUserId(), request), "闪卡创建成功");
    }

    /** 复习队列(due&lt;=today ACTIVE, 新卡自然混入; 附 dueTotal/newTotal) */
    @GetMapping("/queue")
    public Result<FlashcardQueueResponse> queue(@RequestParam(required = false) String subjectId,
                                                @RequestParam(required = false) Integer limit) {
        return Result.success(flashcardService.queue(SecurityUtils.getCurrentUserId(), subjectId, limit));
    }

    /** 评分(1-4; 当日累计达标发一次 +10) */
    @PostMapping("/{id}/review")
    public Result<FlashcardReviewResultResponse> review(@PathVariable String id,
                                                        @Valid @RequestBody FlashcardReviewRequest request) {
        return Result.success(flashcardService.review(SecurityUtils.getCurrentUserId(), id, request.getRating()));
    }

    @GetMapping("/stats")
    public Result<FlashcardStatsResponse> stats() {
        return Result.success(flashcardService.stats(SecurityUtils.getCurrentUserId()));
    }

    /** 各字段可选, null 表示不修改 */
    @PutMapping("/{id}")
    public Result<FlashcardResponse> update(@PathVariable String id, @Valid @RequestBody FlashcardUpdateRequest request) {
        return Result.success(flashcardService.update(SecurityUtils.getCurrentUserId(), id, request), "更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        flashcardService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "删除成功");
    }
}
