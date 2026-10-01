-- ============================================================
-- 增量升级: P0 学习时长 + 每周 AI 复盘
-- 日期: 2026-11-06
-- 适用: 已存在 24 张表(至 plan_generations)的存量库
-- 前置: 必须先执行 upgrade_2026-09_study.sql
--       (study_sessions FK 引用 subjects/chapters)
--       文件名 2026-09 < 2026-11, initdb 顺序天然满足
--
-- 幂等说明（与前三份脚本的关键差异）:
--   本脚本含 ALTER TABLE, 而 MySQL 8 **不支持** ADD COLUMN / ADD KEY IF NOT EXISTS。
--   为了满足两条硬约束, 这里先用 information_schema 判断、再 PREPARE 动态执行:
--     ① 新库场景 —— 根 docker-compose 把整个 db/ 目录挂成 initdb, schema.sql 先跑且已含这些列,
--        若直接 ALTER 会因「Duplicate column name」报错, 在 set -e 的 entrypoint 里会中断容器初始化;
--     ② 重复执行 —— deploy/README-部署说明.md 教的是「按顺序把所有 upgrade_*.sql 都跑一遍」,
--        前一版脚本靠 CREATE TABLE IF NOT EXISTS 做到可重跑, 本脚本必须同样可重跑。
--   守卫读的是 information_schema, 因此无论表刚建好还是早已升级过, 重复执行都安全。
--
-- 回滚(如需撤销本次升级):
--   DROP TABLE IF EXISTS study_sessions;
--   ALTER TABLE plan_generations DROP COLUMN kind;
--   ALTER TABLE reviews DROP INDEX uk_user_type_period,
--                       DROP COLUMN suggestions, DROP COLUMN score,
--                       DROP COLUMN period_start, DROP COLUMN period_end,
--                       DROP COLUMN stats_snapshot, DROP COLUMN ai_generated,
--                       DROP COLUMN model, DROP COLUMN total_tokens;
-- ============================================================

USE habitforge;

SET @hf_db := DATABASE();

-- 25. 学习计时表（P0 学习时长: 一段专注一行; session_date 冗余便于按日聚合）
--     进行中 = ended_at IS NULL; 同一用户至多一段进行中(服务层保证)
CREATE TABLE IF NOT EXISTS study_sessions (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    subject_id      VARCHAR(36)     NULL COMMENT '关联科目(可选)',
    chapter_id      VARCHAR(36)     NULL COMMENT '关联章节(可选)',
    session_date    DATE            NOT NULL COMMENT '归属日期(取 started_at 自然日, 冗余便于聚合)',
    started_at      DATETIME        NOT NULL COMMENT '开始时间',
    ended_at        DATETIME        NULL COMMENT '结束时间(NULL=进行中)',
    minutes         INT             NULL COMMENT '时长(分钟, 结束时服务端计算)',
    source          VARCHAR(20)     DEFAULT 'TIMER' COMMENT '来源: TIMER计时/MANUAL补录',
    note            VARCHAR(200)    NULL COMMENT '备注',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE SET NULL,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL,
    INDEX idx_user_date (user_id, session_date),
    INDEX idx_user_open (user_id, ended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习计时表';

-- reviews 扩展为「周报」载体（原表为复盘预留: type=WEEKLY + good/bad/learnings 直接复用）
-- 8 列一次性补齐: 只要 suggestions 已存在就认为整组已应用, 不再逐列判断(它们同生共死)
SET @hf_done := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @hf_db AND TABLE_NAME = 'reviews' AND COLUMN_NAME = 'suggestions');
SET @hf_ddl := IF(@hf_done = 0,
    'ALTER TABLE reviews
        ADD COLUMN suggestions    TEXT         NULL COMMENT ''改进建议(周报: AI 生成后用户可编辑)'',
        ADD COLUMN score          TINYINT      NULL COMMENT ''综合评分 0-100(AI 生成, 用户可改)'',
        ADD COLUMN period_start   DATE         NULL COMMENT ''统计区间起(WEEKLY=周一)'',
        ADD COLUMN period_end     DATE         NULL COMMENT ''统计区间止(WEEKLY=周日)'',
        ADD COLUMN stats_snapshot TEXT         NULL COMMENT ''客观数据快照 JSON(供审计/复现, 与喂给 AI 的同源)'',
        ADD COLUMN ai_generated   TINYINT(1)   DEFAULT 0 COMMENT ''内容是否由 AI 生成(生成后仍可编辑)'',
        ADD COLUMN model          VARCHAR(100) NULL COMMENT ''生成所用模型'',
        ADD COLUMN total_tokens   INT          NULL COMMENT ''本次生成 token 用量''',
    'DO 0');
PREPARE hf_stmt FROM @hf_ddl;
EXECUTE hf_stmt;
DEALLOCATE PREPARE hf_stmt;

-- uk_user_type_period: 一人一周一报（period_start 为 NULL 的旧行不受唯一约束影响, MySQL 允许多 NULL）
SET @hf_done := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = @hf_db AND TABLE_NAME = 'reviews' AND INDEX_NAME = 'uk_user_type_period');
SET @hf_ddl := IF(@hf_done = 0,
    'ALTER TABLE reviews ADD UNIQUE KEY uk_user_type_period (user_id, type, period_start)',
    'DO 0');
PREPARE hf_stmt FROM @hf_ddl;
EXECUTE hf_stmt;
DEALLOCATE PREPARE hf_stmt;

-- plan_generations 复用为通用 AI 生成流水（周报与排程共用; 周报行 plan_id 为 NULL）
SET @hf_done := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @hf_db AND TABLE_NAME = 'plan_generations' AND COLUMN_NAME = 'kind');
SET @hf_ddl := IF(@hf_done = 0,
    'ALTER TABLE plan_generations ADD COLUMN kind VARCHAR(20) DEFAULT ''PLAN'' COMMENT ''类型: PLAN今日安排/WEEKLY_REPORT周报''',
    'DO 0');
PREPARE hf_stmt FROM @hf_ddl;
EXECUTE hf_stmt;
DEALLOCATE PREPARE hf_stmt;
