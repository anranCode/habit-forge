package com.habitforge.modules.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.study.dto.NoteCreateRequest;
import com.habitforge.modules.study.dto.NoteImageResponse;
import com.habitforge.modules.study.dto.NoteResponse;
import com.habitforge.modules.study.dto.NoteSummaryResponse;
import com.habitforge.modules.study.dto.NoteUpdateRequest;
import com.habitforge.modules.study.entity.Chapter;
import com.habitforge.modules.study.entity.Note;
import com.habitforge.modules.study.entity.NoteImage;
import com.habitforge.modules.study.entity.Subject;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.NoteImageMapper;
import com.habitforge.modules.study.mapper.NoteMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.service.NoteService;
import com.habitforge.modules.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    /** objectKey 日期段: yyyy/MM/dd(与 journal 的 PATH_FMT 完全一致) */
    private static final DateTimeFormatter PATH_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 摘要长度 */
    private static final int EXCERPT_LENGTH = 100;

    private final NoteMapper noteMapper;
    private final NoteImageMapper noteImageMapper;
    private final SubjectMapper subjectMapper;
    private final ChapterMapper chapterMapper;
    private final StorageService storageService;
    private final RedisUtil redisUtil;

    // ================= 创建 / 查询 / 更新 / 删除 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteResponse create(String userId, NoteCreateRequest request) {
        requireOwnedSubject(userId, request.getSubjectId());
        requireChapterInSubject(request.getChapterId(), request.getSubjectId());

        Note note = new Note();
        note.setUserId(userId);
        note.setSubjectId(request.getSubjectId());
        note.setChapterId(request.getChapterId());
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        noteMapper.insert(note);
        log.info("创建笔记 user={}, subject={}, title={}", userId, request.getSubjectId(), note.getTitle());
        return NoteResponse.from(note);
    }

    @Override
    public List<NoteSummaryResponse> listMine(String userId, String subjectId, String chapterId, String keyword) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(subjectId != null, Note::getSubjectId, subjectId)
                .eq(chapterId != null, Note::getChapterId, chapterId)
                .orderByDesc(Note::getUpdatedAt);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Note::getTitle, kw).or().like(Note::getContent, kw));
        }
        return noteMapper.selectList(wrapper).stream()
                .map(n -> NoteSummaryResponse.from(n, excerpt(n.getContent())))
                .toList();
    }

    @Override
    public NoteResponse getDetail(String userId, String id) {
        return NoteResponse.from(loadOwned(userId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteResponse update(String userId, String id, NoteUpdateRequest request) {
        Note note = loadOwned(userId, id);
        if (request.getChapterId() != null) {
            requireChapterInSubject(request.getChapterId(), note.getSubjectId());
            note.setChapterId(request.getChapterId());
        }
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            note.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            note.setContent(request.getContent());
        }
        noteMapper.updateById(note);
        return NoteResponse.from(note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, String id) {
        loadOwned(userId, id);
        // 先记下对象键, 删行(FK CASCADE 删 note_images)后 best-effort 清理 MinIO
        List<String> keys = noteImageMapper.selectList(new LambdaQueryWrapper<NoteImage>()
                        .eq(NoteImage::getNoteId, id)
                        .select(NoteImage::getObjectKey))
                .stream().map(NoteImage::getObjectKey).toList();
        noteMapper.deleteById(id);
        keys.forEach(storageService::deleteObjectQuietly);
        log.info("删除笔记 user={}, noteId={}", userId, id);
    }

    // ================= 图片(完全照搬 journal 流程) =================

    @Override
    public NoteImageResponse uploadImage(String userId, String noteId, MultipartFile file) {
        try {
            if (!redisUtil.tryAcquire("upload:" + userId, AppConstant.UPLOAD_RATE_LIMIT, Duration.ofMinutes(1))) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("限流组件不可用，放行: {}", e.getMessage());
        }

        Note note = loadOwned(userId, noteId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !AppConstant.IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ErrorCode.IMAGE_TYPE_INVALID);
        }
        if (file.getSize() > AppConstant.IMAGE_MAX_SIZE) {
            throw new BusinessException(ErrorCode.IMAGE_SIZE_EXCEEDED);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }

        // 宽高：ImageIO 尝试读取, webp 等读不出置 null
        Integer width = null;
        Integer height = null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
                width = img.getWidth();
                height = img.getHeight();
            }
        } catch (Exception ignored) {
            // 读不出宽高不影响上传
        }

        String ext = switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
        String objectKey = "habitforge/note/" + LocalDate.now().format(PATH_FMT) + "/" + UUID.randomUUID() + "." + ext;

        // 先传 MinIO 再落库, 落库失败则回收对象
        storageService.putObject(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
        NoteImage image = new NoteImage();
        image.setNoteId(note.getId());
        image.setObjectKey(objectKey);
        image.setOriginalName(file.getOriginalFilename());
        image.setContentType(contentType);
        image.setFileSize((int) file.getSize());
        image.setWidth(width);
        image.setHeight(height);
        image.setSortOrder(noteImageMapper.selectCount(new LambdaQueryWrapper<NoteImage>()
                .eq(NoteImage::getNoteId, note.getId())).intValue());
        try {
            noteImageMapper.insert(image);
        } catch (Exception e) {
            storageService.deleteObjectQuietly(objectKey);
            throw e;
        }
        log.info("笔记图片上传成功 user={}, noteId={}, objectKey={}", userId, noteId, objectKey);
        return NoteImageResponse.from(image);
    }

    @Override
    public List<NoteImageResponse> listImages(String userId, String noteId) {
        loadOwned(userId, noteId); // 归属校验, 与 uploadImage/deleteImage 一致
        return noteImageMapper.selectList(new LambdaQueryWrapper<NoteImage>()
                        .eq(NoteImage::getNoteId, noteId)
                        .orderByAsc(NoteImage::getSortOrder))
                .stream().map(NoteImageResponse::from).toList();
    }

    @Override
    public void deleteImage(String userId, String noteId, String imageId) {
        loadOwned(userId, noteId);
        NoteImage image = noteImageMapper.selectById(imageId);
        if (image == null || !image.getNoteId().equals(noteId)) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }
        noteImageMapper.deleteById(imageId);
        storageService.deleteObjectQuietly(image.getObjectKey());
    }

    // ================= 私有方法 =================

    /** 摘要: 去 Markdown 符号后取前 100 字(简单截断) */
    public static String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String plain = content
                // 图片 ![alt](url) / 链接 [text](url) → 保留文字
                .replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                // 行首标题/引用/列表符号
                .replaceAll("(?m)^\\s*(#{1,6}\\s*|>\\s*|[-*+]\\s+|\\d+\\.\\s+)", "")
                // 行内强调与代码标记
                .replaceAll("[*_`~]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() <= EXCERPT_LENGTH ? plain : plain.substring(0, EXCERPT_LENGTH);
    }

    /** 笔记归属校验(不存在或非本人 → 7006) */
    private Note loadOwned(String userId, String noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
        }
        return note;
    }

    private Subject requireOwnedSubject(String userId, String subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null || !subject.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        return subject;
    }

    private void requireChapterInSubject(String chapterId, String subjectId) {
        if (chapterId == null) {
            return;
        }
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getSubjectId().equals(subjectId)) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
    }
}
