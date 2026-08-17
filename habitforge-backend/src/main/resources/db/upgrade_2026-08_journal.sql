-- ============================================================
-- 增量升级: 每日记录(Journal) + 习惯心得(Reflection)
-- 日期: 2026-08-17
-- 适用: 已存在 8 张表(users/habits/checkins/streaks/environment_settings/
--       contracts/reviews/achievements)的存量库
--
-- 回滚(如需撤销本次升级):
--   DROP TABLE IF EXISTS habit_reflections;
--   DROP TABLE IF EXISTS journal_images;
--   DROP TABLE IF EXISTS journal_habits;
--   DROP TABLE IF EXISTS journals;
-- ============================================================

USE habitforge;

-- 9. 日记表（每日记录: user × day 一天一篇, journal_date 可补写过去）
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

-- 10. 日记-习惯关联表（一篇日记关联多个习惯, 防重复）
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

-- 11. 日记图片表（MySQL 只存 objectKey+元数据, 字节在 MinIO）
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

-- 12. 习惯心得表（每个 Journal × Habit 至多一条; 关联当日打卡）
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
