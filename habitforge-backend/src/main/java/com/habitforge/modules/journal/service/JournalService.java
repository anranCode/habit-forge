package com.habitforge.modules.journal.service;

import com.habitforge.modules.journal.dto.JournalCreateRequest;
import com.habitforge.modules.journal.dto.JournalDetailResponse;
import com.habitforge.modules.journal.dto.JournalSummaryResponse;
import com.habitforge.modules.journal.dto.JournalUpdateRequest;
import com.habitforge.modules.journal.entity.Journal;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface JournalService {

    /** 创建日记（habitIds=null 时自动关联当日已打卡习惯） */
    JournalDetailResponse create(String userId, JournalCreateRequest request);

    /** 今日日记，未写返回 null */
    JournalDetailResponse getToday(String userId);

    /** 按日期范围查询摘要列表（journalDate 倒序） */
    List<JournalSummaryResponse> listByRange(String userId, LocalDate from, LocalDate to);

    /** 详情（含关联习惯 + 图片） */
    JournalDetailResponse getDetail(String userId, String id);

    /** 近 N 个自然日含正文的日记（AI 上下文只读; journalDate 倒序, 截断由调用方做） */
    List<Journal> listRecentWithContent(String userId, int days);

    JournalDetailResponse update(String userId, String id, JournalUpdateRequest request);

    /** 物理删除 + 子表 CASCADE + MinIO 对象 best-effort 清理 */
    void delete(String userId, String id);

    /** 关联习惯（双归属校验，重复关联幂等） */
    JournalDetailResponse addHabit(String userId, String journalId, String habitId);

    /** 取消关联（幂等） */
    void removeHabit(String userId, String journalId, String habitId);

    /** 某习惯关联的日记（摘要列表） */
    List<JournalSummaryResponse> listByHabit(String userId, String habitId);

    /** 上传图片：验类型→验大小→传 MinIO→存表 */
    JournalDetailResponse.ImageInfo uploadImage(String userId, String journalId, MultipartFile file);

    /** 删除图片（先删 DB 行，MinIO best-effort） */
    void deleteImage(String userId, String journalId, String imageId);

    /** 归属校验后的实体查询，不存在/非本人抛 JOURNAL_NOT_FOUND（供心得模块复用） */
    Journal getOwned(String userId, String journalId);
}
