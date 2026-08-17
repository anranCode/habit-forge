package com.habitforge.modules.journal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.checkin.service.CheckinService;
import com.habitforge.modules.habit.entity.Habit;
import com.habitforge.modules.habit.mapper.HabitMapper;
import com.habitforge.modules.journal.dto.JournalCreateRequest;
import com.habitforge.modules.journal.dto.JournalDetailResponse;
import com.habitforge.modules.journal.dto.JournalSummaryResponse;
import com.habitforge.modules.journal.dto.JournalUpdateRequest;
import com.habitforge.modules.journal.entity.Journal;
import com.habitforge.modules.journal.entity.JournalHabit;
import com.habitforge.modules.journal.entity.JournalImage;
import com.habitforge.modules.journal.mapper.JournalHabitMapper;
import com.habitforge.modules.journal.mapper.JournalImageMapper;
import com.habitforge.modules.journal.mapper.JournalMapper;
import com.habitforge.modules.journal.service.JournalService;
import com.habitforge.modules.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalServiceImpl implements JournalService {

    private static final DateTimeFormatter PATH_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final JournalMapper journalMapper;
    private final JournalHabitMapper journalHabitMapper;
    private final JournalImageMapper journalImageMapper;
    private final HabitMapper habitMapper;
    private final CheckinService checkinService;
    private final StorageService storageService;
    private final RedisUtil redisUtil;

    // ================= 创建 / 查询 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JournalDetailResponse create(String userId, JournalCreateRequest request) {
        // 简单限流：Redis 异常时放行（fail-open）
        try {
            if (!redisUtil.tryAcquire("journal:" + userId, AppConstant.JOURNAL_RATE_LIMIT, Duration.ofSeconds(1))) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("限流组件不可用，放行: {}", e.getMessage());
        }

        LocalDate date = request.getJournalDate() != null ? request.getJournalDate() : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.JOURNAL_DATE_INVALID);
        }

        Journal journal = new Journal();
        journal.setUserId(userId);
        journal.setJournalDate(date);
        journal.setTitle(request.getTitle());
        journal.setMood(request.getMood());
        journal.setContent(request.getContent());
        try {
            journalMapper.insert(journal);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.JOURNAL_DUPLICATE);
        }

        // 关联习惯：null = 自动播种当日已打卡习惯；空列表 = 不关联
        List<String> habitIds = request.getHabitIds() != null
                ? request.getHabitIds()
                : checkinService.listCheckedHabitIds(userId, date);
        for (String habitId : habitIds) {
            linkHabitInternal(userId, journal.getId(), habitId);
        }

        log.info("日记创建成功 user={}, date={}", userId, date);
        return getDetail(userId, journal.getId());
    }

    @Override
    public JournalDetailResponse getToday(String userId) {
        Journal journal = journalMapper.selectOne(new LambdaQueryWrapper<Journal>()
                .eq(Journal::getUserId, userId)
                .eq(Journal::getJournalDate, LocalDate.now())
                .last("LIMIT 1"));
        return journal == null ? null : buildDetail(journal);
    }

    @Override
    public List<JournalSummaryResponse> listByRange(String userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() > 366) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "查询区间最长 366 天");
        }
        List<Journal> journals = journalMapper.selectList(new LambdaQueryWrapper<Journal>()
                .eq(Journal::getUserId, userId)
                .between(Journal::getJournalDate, from, to)
                .orderByDesc(Journal::getJournalDate));
        return buildSummaries(journals);
    }

    @Override
    public JournalDetailResponse getDetail(String userId, String id) {
        return buildDetail(getOwned(userId, id));
    }

    // ================= 更新 / 删除 =================

    @Override
    public JournalDetailResponse update(String userId, String id, JournalUpdateRequest request) {
        Journal journal = getOwned(userId, id);
        if (request.getTitle() != null) {
            journal.setTitle(request.getTitle());
        }
        if (request.getMood() != null) {
            journal.setMood(request.getMood());
        }
        if (request.getContent() != null) {
            journal.setContent(request.getContent());
        }
        journalMapper.updateById(journal);
        return buildDetail(journal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, String id) {
        Journal journal = getOwned(userId, id);
        // 先记下对象键，删行（CASCADE）后 best-effort 清理 MinIO
        List<String> keys = journalImageMapper.selectList(new LambdaQueryWrapper<JournalImage>()
                        .eq(JournalImage::getJournalId, id)
                        .select(JournalImage::getObjectKey))
                .stream().map(JournalImage::getObjectKey).toList();
        journalMapper.deleteById(id);
        keys.forEach(storageService::deleteObjectQuietly);
        log.info("日记删除成功 user={}, journalId={}", userId, id);
    }

    // ================= 习惯关联 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JournalDetailResponse addHabit(String userId, String journalId, String habitId) {
        Journal journal = getOwned(userId, journalId);
        linkHabitInternal(userId, journal.getId(), habitId);
        return buildDetail(journal);
    }

    @Override
    public void removeHabit(String userId, String journalId, String habitId) {
        getOwned(userId, journalId);
        journalHabitMapper.delete(new LambdaQueryWrapper<JournalHabit>()
                .eq(JournalHabit::getJournalId, journalId)
                .eq(JournalHabit::getHabitId, habitId));
    }

    @Override
    public List<JournalSummaryResponse> listByHabit(String userId, String habitId) {
        Habit habit = habitMapper.selectById(habitId);
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HABIT_NOT_FOUND);
        }
        List<String> journalIds = journalHabitMapper.selectList(new LambdaQueryWrapper<JournalHabit>()
                        .eq(JournalHabit::getHabitId, habitId)
                        .select(JournalHabit::getJournalId))
                .stream().map(JournalHabit::getJournalId).toList();
        if (journalIds.isEmpty()) {
            return List.of();
        }
        List<Journal> journals = journalMapper.selectList(new LambdaQueryWrapper<Journal>()
                .in(Journal::getId, journalIds)
                .orderByDesc(Journal::getJournalDate));
        return buildSummaries(journals);
    }

    // ================= 图片 =================

    @Override
    public JournalDetailResponse.ImageInfo uploadImage(String userId, String journalId, MultipartFile file) {
        try {
            if (!redisUtil.tryAcquire("upload:" + userId, AppConstant.UPLOAD_RATE_LIMIT, Duration.ofMinutes(1))) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("限流组件不可用，放行: {}", e.getMessage());
        }

        Journal journal = getOwned(userId, journalId);

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

        // 宽高：ImageIO 尝试读取，webp 等读不出置 null
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
        String objectKey = "habitforge/journal/" + LocalDate.now().format(PATH_FMT) + "/" + UUID.randomUUID() + "." + ext;

        // 先传 MinIO 再落库，落库失败则回收对象
        storageService.putObject(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
        JournalImage image = new JournalImage();
        image.setJournalId(journal.getId());
        image.setObjectKey(objectKey);
        image.setOriginalName(file.getOriginalFilename());
        image.setContentType(contentType);
        image.setFileSize((int) file.getSize());
        image.setWidth(width);
        image.setHeight(height);
        image.setSortOrder(journalImageMapper.selectCount(new LambdaQueryWrapper<JournalImage>()
                .eq(JournalImage::getJournalId, journal.getId())).intValue());
        try {
            journalImageMapper.insert(image);
        } catch (Exception e) {
            storageService.deleteObjectQuietly(objectKey);
            throw e;
        }
        log.info("图片上传成功 user={}, journalId={}, objectKey={}", userId, journalId, objectKey);

        return toImageInfo(image);
    }

    @Override
    public void deleteImage(String userId, String journalId, String imageId) {
        getOwned(userId, journalId);
        JournalImage image = journalImageMapper.selectById(imageId);
        if (image == null || !image.getJournalId().equals(journalId)) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }
        journalImageMapper.deleteById(imageId);
        storageService.deleteObjectQuietly(image.getObjectKey());
    }

    // ================= 内部方法 =================

    @Override
    public Journal getOwned(String userId, String journalId) {
        Journal journal = journalMapper.selectById(journalId);
        if (journal == null || !journal.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.JOURNAL_NOT_FOUND);
        }
        return journal;
    }

    /** 关联习惯（校验习惯归属，唯一键防重复） */
    private void linkHabitInternal(String userId, String journalId, String habitId) {
        Habit habit = habitMapper.selectById(habitId);
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HABIT_NOT_FOUND);
        }
        JournalHabit link = new JournalHabit();
        link.setJournalId(journalId);
        link.setHabitId(habitId);
        try {
            journalHabitMapper.insert(link);
        } catch (DuplicateKeyException ignored) {
            // 重复关联幂等
        }
    }

    private JournalDetailResponse buildDetail(Journal journal) {
        // 关联习惯
        List<String> habitIds = journalHabitMapper.selectList(new LambdaQueryWrapper<JournalHabit>()
                        .eq(JournalHabit::getJournalId, journal.getId())
                        .select(JournalHabit::getHabitId))
                .stream().map(JournalHabit::getHabitId).toList();
        List<JournalDetailResponse.HabitBrief> habits = habitIds.isEmpty() ? List.of()
                : habitMapper.selectBatchIds(habitIds).stream()
                .map(h -> JournalDetailResponse.HabitBrief.builder()
                        .id(h.getId())
                        .name(h.getName())
                        .category(h.getCategory())
                        .identityTag(h.getIdentityTag())
                        .build())
                .toList();

        // 图片列表
        List<JournalDetailResponse.ImageInfo> images = journalImageMapper.selectList(new LambdaQueryWrapper<JournalImage>()
                        .eq(JournalImage::getJournalId, journal.getId())
                        .orderByAsc(JournalImage::getSortOrder))
                .stream().map(this::toImageInfo).toList();

        return JournalDetailResponse.builder()
                .id(journal.getId())
                .journalDate(journal.getJournalDate())
                .title(journal.getTitle())
                .mood(journal.getMood())
                .content(journal.getContent())
                .createdAt(journal.getCreatedAt())
                .updatedAt(journal.getUpdatedAt())
                .habits(habits)
                .images(images)
                .build();
    }

    private List<JournalSummaryResponse> buildSummaries(List<Journal> journals) {
        if (journals.isEmpty()) {
            return List.of();
        }
        List<String> ids = journals.stream().map(Journal::getId).toList();
        // 两次聚合查询统计图片数/习惯数，避免 N+1
        Map<String, Long> imageCounts = journalImageMapper.selectList(new LambdaQueryWrapper<JournalImage>()
                        .in(JournalImage::getJournalId, ids)
                        .select(JournalImage::getJournalId))
                .stream().collect(Collectors.groupingBy(JournalImage::getJournalId, Collectors.counting()));
        Map<String, Long> habitCounts = journalHabitMapper.selectList(new LambdaQueryWrapper<JournalHabit>()
                        .in(JournalHabit::getJournalId, ids)
                        .select(JournalHabit::getJournalId))
                .stream().collect(Collectors.groupingBy(JournalHabit::getJournalId, Collectors.counting()));
        return journals.stream()
                .map(j -> JournalSummaryResponse.builder()
                        .id(j.getId())
                        .journalDate(j.getJournalDate())
                        .title(j.getTitle())
                        .mood(j.getMood())
                        .hasContent(j.getContent() != null && !j.getContent().isBlank())
                        .imageCount(imageCounts.getOrDefault(j.getId(), 0L).intValue())
                        .habitCount(habitCounts.getOrDefault(j.getId(), 0L).intValue())
                        .build())
                .sorted(Comparator.comparing(JournalSummaryResponse::getJournalDate).reversed())
                .toList();
    }

    private JournalDetailResponse.ImageInfo toImageInfo(JournalImage image) {
        return JournalDetailResponse.ImageInfo.builder()
                .id(image.getId())
                .objectKey(image.getObjectKey())
                .originalName(image.getOriginalName())
                .contentType(image.getContentType())
                .fileSize(image.getFileSize())
                .width(image.getWidth())
                .height(image.getHeight())
                .sortOrder(image.getSortOrder())
                .build();
    }
}
