package com.habitforge.modules.reflection.service;

import com.habitforge.modules.reflection.dto.ReflectionRequest;
import com.habitforge.modules.reflection.dto.ReflectionResponse;
import com.habitforge.modules.reflection.dto.ReflectionUpdateRequest;

import java.util.List;

public interface ReflectionService {

    /** 创建心得（自动关联 journal_habits + 自动回填 checkin_id） */
    ReflectionResponse create(String userId, ReflectionRequest request);

    ReflectionResponse update(String userId, String id, ReflectionUpdateRequest request);

    void delete(String userId, String id);

    /** 某日记下的全部心得（附带习惯名） */
    List<ReflectionResponse> listByJournal(String userId, String journalId);

    /** 某习惯的全部心得（时间倒序，附带日记日期/标题） */
    List<ReflectionResponse> listByHabit(String userId, String habitId);
}
