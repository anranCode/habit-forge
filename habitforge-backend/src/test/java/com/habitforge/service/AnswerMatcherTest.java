package com.habitforge.service;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.study.service.support.AnswerMatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 客观题判分纯函数：MULTI 的双向归一化是重点（存量答案可能手输乱序, 风险备忘 1）。
 */
class AnswerMatcherTest {

    @Test
    void singleIsCaseAndSpaceInsensitive() {
        assertTrue(AnswerMatcher.match("SINGLE", "A", "a"));
        assertTrue(AnswerMatcher.match("SINGLE", "A", " A "));
        assertFalse(AnswerMatcher.match("SINGLE", "A", "B"));
        // 多敲字母 = 答错, 不是接口错误(刷题误点不该炸)
        assertFalse(AnswerMatcher.match("SINGLE", "A", "AB"));
    }

    @Test
    void judgeMatchesStoredContract() {
        assertTrue(AnswerMatcher.match("JUDGE", "T", "t"));
        assertFalse(AnswerMatcher.match("JUDGE", "T", "F"));
        assertFalse(AnswerMatcher.match("JUDGE", "T", "TRUE"));
    }

    @Test
    void multiNormalizesBothSides() {
        assertTrue(AnswerMatcher.match("MULTI", "A,C", "C,A"));   // 存量乱序 × 用户乱序
        assertTrue(AnswerMatcher.match("MULTI", "C, A", "a c"));   // 空格/大小写混输
        assertTrue(AnswerMatcher.match("MULTI", "A,B", "A,B,B"));  // 重复字母去重
        assertFalse(AnswerMatcher.match("MULTI", "A,B,C", "A,B")); // 漏选
        assertFalse(AnswerMatcher.match("MULTI", "A,B", "A,B,C")); // 多选
    }

    @Test
    void shortDoesNotPassThroughMatcher() {
        assertThrows(BusinessException.class, () -> AnswerMatcher.match("SHORT", "参考答案", "我的答案"));
    }
}
