-- ============================================================
-- 增量升级: 学习模块 P0(科目/章节) + P1(闪卡/复习日志/笔记/笔记图片/题库/错题)
-- 日期: 2026-09-12
-- 适用: 已存在 12 张表(至 habit_reflections)的存量库
--
-- 回滚(如需撤销本次升级, 按 FK 依赖序先删子表再删父表):
--   DROP TABLE IF EXISTS wrong_questions;
--   DROP TABLE IF EXISTS questions;
--   DROP TABLE IF EXISTS note_images;
--   DROP TABLE IF EXISTS notes;
--   DROP TABLE IF EXISTS card_review_logs;
--   DROP TABLE IF EXISTS flashcards;
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

-- 15. 闪卡表（SM-2 间隔重复: subject_id 冗余, 到期队列单表零 join; front/back 可含 Markdown）
CREATE TABLE IF NOT EXISTS flashcards (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    subject_id      VARCHAR(36)     NOT NULL COMMENT '所属科目(冗余, 到期队列单表零join)',
    chapter_id      VARCHAR(36)     NULL COMMENT '所属章节(NULL=不挂章节, 删章节置NULL不删卡)',
    front           TEXT            NOT NULL COMMENT '卡面(可含Markdown)',
    back            TEXT            NOT NULL COMMENT '卡背(可含Markdown)',
    ease_factor     DECIMAL(4,2)    DEFAULT 2.50 COMMENT '难度因子EF(SM-2, 下限1.30/上限3.00)',
    interval_days   INT             DEFAULT 0 COMMENT '当前复习间隔(天)',
    repetition      INT             DEFAULT 0 COMMENT '连续记得次数(评1忘记归零)',
    lapses          INT             DEFAULT 0 COMMENT '累计忘记次数',
    due_date        DATE            NOT NULL COMMENT '到期日(初始=创建日)',
    last_reviewed_at DATETIME       NULL COMMENT '最近复习时间',
    status          VARCHAR(20)     DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE有效/SUSPENDED暂停',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL,
    INDEX idx_user_due (user_id, due_date),
    INDEX idx_subject (subject_id),
    INDEX idx_chapter (chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闪卡表';

-- 16. 卡片复习日志表（每次评分一行, 前后快照; 唯一快增长表, 留年度归档运维项）
CREATE TABLE IF NOT EXISTS card_review_logs (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    card_id         VARCHAR(36)     NOT NULL COMMENT '闪卡ID',
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户(冗余, 按人按时间查询)',
    rating          TINYINT         NOT NULL COMMENT '评分: 1忘记/2模糊/3记得/4轻松',
    interval_before INT             NOT NULL COMMENT '复习前间隔(天)',
    interval_after  INT             NOT NULL COMMENT '复习后间隔(天)',
    ease_before     DECIMAL(4,2)    NOT NULL COMMENT '复习前EF',
    ease_after      DECIMAL(4,2)    NOT NULL COMMENT '复习后EF',
    reviewed_at     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '复习时间',
    FOREIGN KEY (card_id) REFERENCES flashcards(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)      ON DELETE CASCADE,
    INDEX idx_card (card_id),
    INDEX idx_user_time (user_id, reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='卡片复习日志表';

-- 17. 笔记表（Markdown 原文入库, 前端渲染, 不落 HTML）
CREATE TABLE IF NOT EXISTS notes (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    subject_id      VARCHAR(36)     NOT NULL COMMENT '所属科目',
    chapter_id      VARCHAR(36)     NULL COMMENT '所属章节(NULL=不挂章节)',
    title           VARCHAR(200)    NOT NULL COMMENT '标题',
    content         MEDIUMTEXT      NULL COMMENT '正文(Markdown原文)',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL,
    INDEX idx_subject (subject_id),
    INDEX idx_chapter (chapter_id),
    INDEX idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记表';

-- 18. 笔记图片表（完全镜像 journal_images 约定; objectKey 前缀 habitforge/note/{yyyy}/{MM}/{dd}/{uuid}.{ext}）
CREATE TABLE IF NOT EXISTS note_images (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    note_id         VARCHAR(36)     NOT NULL COMMENT '笔记ID',
    object_key      VARCHAR(255)    NOT NULL COMMENT 'MinIO对象键: habitforge/note/{yyyy}/{MM}/{dd}/{uuid}.{ext}',
    original_name   VARCHAR(255)    NULL COMMENT '上传时原始文件名',
    content_type    VARCHAR(50)     NOT NULL COMMENT 'MIME类型',
    file_size       INT             DEFAULT 0 COMMENT '字节数',
    width           INT             NULL COMMENT '图片宽度(读不出为NULL)',
    height          INT             NULL COMMENT '图片高度(读不出为NULL)',
    sort_order      INT             DEFAULT 0 COMMENT '排序(越小越靠前)',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,
    INDEX idx_note_id (note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记图片表';

-- 19. 题目表（题库: options 存 JSON 数组字符串, 服务层 hutool JSONUtil 校验, 不用 TypeHandler）
CREATE TABLE IF NOT EXISTS questions (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    subject_id      VARCHAR(36)     NOT NULL COMMENT '所属科目',
    chapter_id      VARCHAR(36)     NULL COMMENT '所属章节(NULL=不挂章节)',
    question_type   VARCHAR(20)     DEFAULT 'SINGLE' COMMENT '题型: SINGLE单选/MULTI多选/JUDGE判断/SHORT简答',
    stem            TEXT            NOT NULL COMMENT '题干(可含Markdown)',
    options         TEXT            NULL COMMENT '选项JSON数组字符串(简答/判断可空)',
    answer          VARCHAR(1000)   NOT NULL COMMENT '标准答案',
    analysis        TEXT            NULL COMMENT '解析',
    source_type     VARCHAR(20)     DEFAULT 'CUSTOM' COMMENT '来源: PAST_EXAM真题/TEXTBOOK教材/CUSTOM自编/AI生成',
    source_detail   VARCHAR(100)    NULL COMMENT '来源详情(如: 2025年10月真题)',
    difficulty      TINYINT         NULL COMMENT '难度1-5',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL,
    INDEX idx_subject (subject_id),
    INDEX idx_chapter (chapter_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';

-- 20. 错题表（uk 一人一题一行; 摘除只置 mastered=1 不删行）
CREATE TABLE IF NOT EXISTS wrong_questions (
    id                VARCHAR(36)   PRIMARY KEY DEFAULT (UUID()),
    user_id           VARCHAR(36)   NOT NULL COMMENT '所属用户',
    question_id       VARCHAR(36)   NOT NULL COMMENT '题目ID',
    wrong_count       INT           DEFAULT 1 COMMENT '累计答错次数',
    correct_streak    INT           DEFAULT 0 COMMENT '连对次数(达 WRONG_MASTER_STREAK 自动摘除)',
    mastered          TINYINT(1)    DEFAULT 0 COMMENT '是否已摘除/掌握: 0未摘除 1已摘除',
    last_wrong_at     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '最近答错时间',
    last_practiced_at DATETIME      NULL COMMENT '最近重练时间',
    created_at        DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)     REFERENCES users(id)     ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_question (user_id, question_id),
    INDEX idx_user_mastered (user_id, mastered)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题表';
