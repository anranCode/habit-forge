-- ============================================================
-- 数据库: habitforge
-- 字符集: utf8mb4 (支持emoji)
-- ============================================================

CREATE DATABASE IF NOT EXISTS habitforge
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE habitforge;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    username        VARCHAR(50)     NOT NULL UNIQUE COMMENT '用户名',
    email           VARCHAR(100)    NOT NULL UNIQUE COMMENT '邮箱',
    password_hash   VARCHAR(255)    NOT NULL COMMENT '密码哈希(BCrypt)',
    identity_goal   VARCHAR(500)    NULL COMMENT '身份设定: 我想成为...',
    avatar_url      VARCHAR(500)    NULL COMMENT '头像URL',
    points          INT             DEFAULT 0 COMMENT '积分',
    level           INT             DEFAULT 1 COMMENT '等级',
    is_active       TINYINT(1)      DEFAULT 1 COMMENT '是否启用',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at   DATETIME        NULL,
    INDEX idx_email (email),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================
-- 2. 习惯表（新增: habit_type / frequency_* / deleted）
-- ============================================================
CREATE TABLE IF NOT EXISTS habits (
    id                  VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id             VARCHAR(36)     NOT NULL COMMENT '所属用户',
    name                VARCHAR(100)    NOT NULL COMMENT '习惯名称',
    identity_tag        VARCHAR(100)    NULL COMMENT '身份标签(如: 读者,跑步者)',
    category            VARCHAR(20)     DEFAULT 'OTHER' COMMENT '分类: HEALTH/LEARNING/WORK/LIFE/OTHER',
    habit_type          VARCHAR(20)     DEFAULT 'GOOD' COMMENT 'GOOD好习惯/BAD坏习惯(预留)',
    frequency_type      VARCHAR(20)     DEFAULT 'DAILY' COMMENT '频率: DAILY每日/WEEKLY_DAYS每周指定几天/WEEKLY_COUNT每周N次',
    frequency_days      VARCHAR(20)     NULL COMMENT 'WEEKLY_DAYS时每周哪几天,如1,3,5(1=周一)',
    frequency_target    INT             DEFAULT 1 COMMENT 'WEEKLY_COUNT时每周目标次数',
    two_minute_version  VARCHAR(200)    NULL COMMENT '两分钟微习惯版本',
    exec_time           VARCHAR(50)     NULL COMMENT '执行时间(如: 07:00)',
    exec_place          VARCHAR(100)    NULL COMMENT '执行地点(如: 客厅)',
    stack_after         VARCHAR(100)    NULL COMMENT '习惯叠加: 继[某习惯]之后',
    is_active           TINYINT(1)      DEFAULT 1 COMMENT '是否进行中',
    deleted             TINYINT(1)      DEFAULT 0 COMMENT '逻辑删除: 0正常 1已删除',
    priority            INT             DEFAULT 0 COMMENT '优先级(数字越大越靠前)',
    created_at          DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_category (category),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='习惯表';

-- ============================================================
-- 3. 打卡记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS checkins (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    habit_id        VARCHAR(36)     NOT NULL COMMENT '习惯ID',
    check_date      DATE            NOT NULL COMMENT '打卡日期',
    is_completed    TINYINT(1)      DEFAULT 1 COMMENT '是否完成',
    note            VARCHAR(500)    NULL COMMENT '备注/感想',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (habit_id) REFERENCES habits(id) ON DELETE CASCADE,
    UNIQUE KEY uk_habit_date (habit_id, check_date),
    INDEX idx_habit_id (habit_id),
    INDEX idx_check_date (check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='打卡记录表';

-- ============================================================
-- 4. 习惯链表
-- ============================================================
CREATE TABLE IF NOT EXISTS streaks (
    id                  VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    habit_id            VARCHAR(36)     NOT NULL COMMENT '习惯ID',
    current_streak      INT             DEFAULT 0 COMMENT '当前连续天数',
    longest_streak      INT             DEFAULT 0 COMMENT '最长连续天数',
    last_check_date     DATE            NULL COMMENT '最后打卡日期',
    updated_at          DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (habit_id) REFERENCES habits(id) ON DELETE CASCADE,
    UNIQUE KEY uk_habit_id (habit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='习惯链表';

-- ============================================================
-- 5. 环境设置表
-- ============================================================
CREATE TABLE IF NOT EXISTS environment_settings (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    type            VARCHAR(20)     NOT NULL COMMENT '类型: PROMPT/RESISTANCE/COMMITMENT',
    description     VARCHAR(500)    NOT NULL COMMENT '设置描述',
    target_habit_id VARCHAR(36)     NULL COMMENT '关联的习惯ID',
    is_active       TINYINT(1)      DEFAULT 1 COMMENT '是否生效',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='环境设置表';

-- ============================================================
-- 6. 习惯契约表（单用户场景: 问责伙伴改为手填姓名）
-- ============================================================
CREATE TABLE IF NOT EXISTS contracts (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '发起用户',
    habit_id        VARCHAR(36)     NOT NULL COMMENT '关联习惯',
    partner_name    VARCHAR(100)    NOT NULL COMMENT '问责伙伴姓名',
    penalty         VARCHAR(500)    NOT NULL COMMENT '违约代价描述',
    is_public       TINYINT(1)      DEFAULT 0 COMMENT '是否公开',
    status          VARCHAR(20)     DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/COMPLETED/BROKEN',
    signed_at       DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (habit_id) REFERENCES habits(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='习惯契约表';

-- ============================================================
-- 7. 复盘记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS reviews (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    type            VARCHAR(20)     NOT NULL COMMENT '类型: DAILY/WEEKLY/MONTHLY/QUARTERLY',
    title           VARCHAR(200)    NULL COMMENT '标题',
    good_things     TEXT            NULL COMMENT '做得好的事',
    bad_things      TEXT            NULL COMMENT '做得不好的事',
    learnings       TEXT            NULL COMMENT '学到的东西',
    review_date     DATE            NOT NULL COMMENT '复盘日期',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_review_date (review_date),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='复盘记录表';

-- ============================================================
-- 8. 成就表（链里程碑: 7/30/100天）
-- ============================================================
CREATE TABLE IF NOT EXISTS achievements (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    name            VARCHAR(100)    NOT NULL COMMENT '成就名称',
    icon            VARCHAR(50)     NULL COMMENT '成就图标',
    description     VARCHAR(500)    NULL COMMENT '成就描述',
    achieved_at     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_name (user_id, name),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成就表';

-- ============================================================
-- 9. 日记表（每日记录: user × day 一天一篇, journal_date 可补写过去）
-- ============================================================
CREATE TABLE IF NOT EXISTS journals (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    journal_date    DATE            NOT NULL COMMENT '日记日期(区别于created_at,允许补写)',
    title           VARCHAR(200)    NULL COMMENT '标题',
    mood            TINYINT         NULL COMMENT '心情: 1😊好 2😐一般 3😫疲惫',
    content         TEXT            NULL COMMENT '正文(自由文本)',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_date (user_id, journal_date),
    INDEX idx_journal_date (journal_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日记表';

-- ============================================================
-- 10. 日记-习惯关联表（一篇日记关联多个习惯, 防重复）
-- ============================================================
CREATE TABLE IF NOT EXISTS journal_habits (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    journal_id      VARCHAR(36)     NOT NULL COMMENT '日记ID',
    habit_id        VARCHAR(36)     NOT NULL COMMENT '习惯ID',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (journal_id) REFERENCES journals(id) ON DELETE CASCADE,
    FOREIGN KEY (habit_id)   REFERENCES habits(id)   ON DELETE CASCADE,
    UNIQUE KEY uk_journal_habit (journal_id, habit_id),
    INDEX idx_habit_id (habit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日记-习惯关联表';

-- ============================================================
-- 11. 日记图片表（MySQL 只存 objectKey+元数据, 字节在 MinIO）
-- ============================================================
CREATE TABLE IF NOT EXISTS journal_images (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    journal_id      VARCHAR(36)     NOT NULL COMMENT '日记ID',
    object_key      VARCHAR(255)    NOT NULL COMMENT 'MinIO对象键: habitforge/journal/{yyyy}/{MM}/{dd}/{uuid}.{ext}',
    original_name   VARCHAR(255)    NULL COMMENT '上传时原始文件名',
    content_type    VARCHAR(50)     NOT NULL COMMENT 'MIME类型',
    file_size       INT             DEFAULT 0 COMMENT '字节数',
    width           INT             NULL COMMENT '图片宽度(读不出为NULL)',
    height          INT             NULL COMMENT '图片高度(读不出为NULL)',
    sort_order      INT             DEFAULT 0 COMMENT '排序(越小越靠前)',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (journal_id) REFERENCES journals(id) ON DELETE CASCADE,
    INDEX idx_journal_id (journal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日记图片表';

-- ============================================================
-- 12. 习惯心得表（每个 Journal × Habit 至多一条; 关联当日打卡）
-- ============================================================
CREATE TABLE IF NOT EXISTS habit_reflections (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    journal_id      VARCHAR(36)     NOT NULL COMMENT '所属日记ID',
    habit_id        VARCHAR(36)     NOT NULL COMMENT '习惯ID',
    checkin_id      VARCHAR(36)     NULL COMMENT '关联打卡记录(未完成时为NULL)',
    result          TINYINT         NOT NULL COMMENT '完成情况: 1完成 0未完成',
    feeling         TINYINT         NULL COMMENT '今日感受: 1😊好 2😐一般 3😫糟糕',
    difficulty      TINYINT         NULL COMMENT '难度: 1-5星',
    reason          VARCHAR(500)    NULL COMMENT '为什么今天能做到/没做到',
    obstacle        VARCHAR(500)    NULL COMMENT '遇到了什么困难',
    learning        VARCHAR(500)    NULL COMMENT '今天学到了什么',
    adjustment      VARCHAR(500)    NULL COMMENT '明天准备怎么调整',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (journal_id) REFERENCES journals(id) ON DELETE CASCADE,
    FOREIGN KEY (habit_id)   REFERENCES habits(id)   ON DELETE CASCADE,
    FOREIGN KEY (checkin_id) REFERENCES checkins(id) ON DELETE SET NULL,
    UNIQUE KEY uk_journal_habit (journal_id, habit_id),
    INDEX idx_habit_id (habit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='习惯心得表';

-- ============================================================
-- 13. 科目表（学习模块: 自考科目; deleted 逻辑删除防误删连带笔记/题目, 沿 habits 先例）
-- ============================================================
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

-- ============================================================
-- 14. 章节表（科目下树形章节: parent_id 自引用, 建议≤3层; FK CASCADE 删父即删子树）
-- ============================================================
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
