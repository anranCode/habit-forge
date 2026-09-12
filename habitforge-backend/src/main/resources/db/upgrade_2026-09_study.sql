-- ============================================================
-- 增量升级: 学习模块 P0（科目 Subject + 章节 Chapter）
-- 日期: 2026-09-12
-- 适用: 已存在 12 张表(至 habit_reflections)的存量库
--
-- 回滚(如需撤销本次升级):
--   DROP TABLE IF EXISTS chapters;
--   DROP TABLE IF EXISTS subjects;
--   ( chapters 自引用与外键指向 subjects, 必须先删 chapters 再删 subjects )
-- ============================================================

USE habitforge;

-- 13. 科目表（学习模块: 自考科目; deleted 逻辑删除防误删连带笔记/题目, 沿 habits 先例）
CREATE TABLE IF NOT EXISTS subjects (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    name            VARCHAR(100)    NOT NULL COMMENT '科目名称',
    exam_date       DATE            NULL COMMENT '考试日期(倒计时用,NULL=无考期)',
    exam_session    VARCHAR(50)     NULL COMMENT '考试学期(如: 2026上半年)',
    description     VARCHAR(500)    NULL COMMENT '科目说明',
    sort_order      INT             DEFAULT 0 COMMENT '排序(越小越靠前)',
    is_active       TINYINT(1)      DEFAULT 1 COMMENT '是否进行中',
    deleted         TINYINT(1)      DEFAULT 0 COMMENT '逻辑删除: 0正常 1已删除',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='科目表';

-- 14. 章节表（科目下树形章节: parent_id 自引用, 建议≤3层; FK CASCADE 删父即删子树）
CREATE TABLE IF NOT EXISTS chapters (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    subject_id      VARCHAR(36)     NOT NULL COMMENT '所属科目',
    parent_id       VARCHAR(36)     NULL COMMENT '父章节ID(NULL=顶层)',
    name            VARCHAR(200)    NOT NULL COMMENT '章节名称',
    sort_order      INT             DEFAULT 0 COMMENT '排序(越小越靠前)',
    status          VARCHAR(20)     DEFAULT 'NOT_STARTED' COMMENT '状态: NOT_STARTED未开始/IN_PROGRESS进行中/DONE已完成',
    done_at         DATETIME        NULL COMMENT '首次完成时间(改回非DONE时置NULL)',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES chapters(id) ON DELETE CASCADE,
    INDEX idx_subject_status (subject_id, status),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节表';

-- P1 表将追加于此（flashcards / card_review_logs / notes / note_images / questions / wrong_questions）
