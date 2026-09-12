package com.habitforge.modules.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.study.dto.ChapterCreateRequest;
import com.habitforge.modules.study.dto.ChapterResponse;
import com.habitforge.modules.study.dto.ChapterUpdateRequest;
import com.habitforge.modules.study.entity.Chapter;
import com.habitforge.modules.study.entity.Subject;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.service.ChapterService;
import com.habitforge.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements ChapterService {

    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_DONE = "DONE";

    private final ChapterMapper chapterMapper;
    private final SubjectMapper subjectMapper;
    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterResponse create(String userId, ChapterCreateRequest request) {
        requireOwnedSubject(userId, request.getSubjectId());
        if (request.getParentId() != null) {
            // 父章节必须存在且与科目一致, 否则 7003
            validateParent(request.getParentId(), request.getSubjectId());
        }

        Chapter chapter = new Chapter();
        chapter.setSubjectId(request.getSubjectId());
        chapter.setParentId(request.getParentId());
        chapter.setName(request.getName());
        chapter.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        chapter.setStatus(STATUS_NOT_STARTED);
        chapterMapper.insert(chapter);
        log.info("创建章节 user={}, subject={}, name={}", userId, request.getSubjectId(), chapter.getName());
        return ChapterResponse.from(chapter);
    }

    @Override
    public List<ChapterResponse> listBySubject(String userId, String subjectId) {
        requireOwnedSubject(userId, subjectId);
        return chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getSubjectId, subjectId)
                        .orderByAsc(Chapter::getSortOrder)
                        .orderByAsc(Chapter::getCreatedAt))
                .stream().map(ChapterResponse::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterResponse update(String userId, String chapterId, ChapterUpdateRequest request) {
        Chapter chapter = loadOwned(userId, chapterId);
        if (request.getName() != null && !request.getName().isBlank()) {
            chapter.setName(request.getName());
        }
        if (request.getSortOrder() != null) {
            chapter.setSortOrder(request.getSortOrder());
        }
        // parentId: null=不改; 换父时沿 parent 链上溯防环(命中自身/不存在/跨科目 → 7003)
        if (request.getParentId() != null && !request.getParentId().equals(chapter.getParentId())) {
            validateNewParent(chapterId, chapter.getSubjectId(), request.getParentId());
            chapter.setParentId(request.getParentId());
        }
        chapterMapper.updateById(chapter);
        return ChapterResponse.from(chapter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterResponse updateStatus(String userId, String chapterId, String status) {
        if (!STATUS_NOT_STARTED.equals(status) && !STATUS_IN_PROGRESS.equals(status) && !STATUS_DONE.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态取值不合法");
        }
        Chapter chapter = loadOwned(userId, chapterId);
        boolean wasDone = STATUS_DONE.equals(chapter.getStatus());

        if (STATUS_DONE.equals(status)) {
            LocalDateTime now = LocalDateTime.now();
            // 首次 非DONE→DONE 才加分; 重复置 DONE 不重复加分;
            // 级联置 DONE 的子孙不发分(用户显式完成的只有本章节, 防整树一键刷分)
            if (!wasDone) {
                chapter.setStatus(STATUS_DONE);
                chapter.setDoneAt(now);
                chapterMapper.updateById(chapter);
                userService.addPoints(userId, AppConstant.POINTS_PER_CHAPTER_DONE);
                log.info("章节完成加分 user={}, chapter={}, points={}",
                        userId, chapter.getName(), AppConstant.POINTS_PER_CHAPTER_DONE);
            } else {
                chapterMapper.updateById(chapter);
            }
            cascadeDone(chapter.getId(), chapter.getSubjectId(), now);
        } else {
            chapter.setStatus(status);
            // 改回非 DONE: 清 doneAt; 已发放的积分不回收
            chapter.setDoneAt(null);
            chapterMapper.updateById(chapter);
        }
        return ChapterResponse.from(chapter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, String chapterId) {
        Chapter chapter = loadOwned(userId, chapterId);
        // 物理删除; chapters.parent_id 自引用 FK ON DELETE CASCADE, DB 层连带删除整棵子树
        chapterMapper.deleteById(chapter.getId());
        log.info("删除章节 user={}, chapter={}(含子树 DB CASCADE)", userId, chapter.getName());
    }

    // ================= 私有方法 =================

    /** 章节归属校验: 章节不存在 → 7002; 所属科目不存在/非本人 → 7001 */
    private Chapter loadOwned(String userId, String chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        requireOwnedSubject(userId, chapter.getSubjectId());
        return chapter;
    }

    private Subject requireOwnedSubject(String userId, String subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null || !subject.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        return subject;
    }

    private void validateParent(String parentId, String subjectId) {
        Chapter parent = chapterMapper.selectById(parentId);
        if (parent == null || !parent.getSubjectId().equals(subjectId)) {
            throw new BusinessException(ErrorCode.CHAPTER_PARENT_INVALID);
        }
    }

    /** 新父沿 parent 链上溯: 命中自身(成环)/不存在/跨科目/存量脏数据环 → 7003 */
    private void validateNewParent(String chapterId, String subjectId, String newParentId) {
        if (newParentId.equals(chapterId)) {
            throw new BusinessException(ErrorCode.CHAPTER_PARENT_INVALID);
        }
        Set<String> visited = new HashSet<>();
        String cur = newParentId;
        while (cur != null) {
            if (cur.equals(chapterId) || !visited.add(cur)) {
                throw new BusinessException(ErrorCode.CHAPTER_PARENT_INVALID);
            }
            Chapter parent = chapterMapper.selectById(cur);
            if (parent == null || !parent.getSubjectId().equals(subjectId)) {
                throw new BusinessException(ErrorCode.CHAPTER_PARENT_INVALID);
            }
            cur = parent.getParentId();
        }
    }

    /** 子孙级联置 DONE(仅本章节首次完成路径调用; 已 DONE 的子孙保留原 doneAt) */
    private void cascadeDone(String chapterId, String subjectId, LocalDateTime now) {
        List<Chapter> all = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getSubjectId, subjectId));
        Map<String, List<Chapter>> childrenOf = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Chapter::getParentId));
        Deque<String> queue = new ArrayDeque<>();
        queue.add(chapterId);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (Chapter child : childrenOf.getOrDefault(cur, List.of())) {
                if (!STATUS_DONE.equals(child.getStatus())) {
                    child.setStatus(STATUS_DONE);
                    child.setDoneAt(now);
                    chapterMapper.updateById(child);
                }
                queue.add(child.getId());
            }
        }
    }
}
