-- ============================================================
-- 增量升级: P2 手机节制（注意力日志 + 环境设计清单 + 问责契约）
-- 日期: 2026-12-01
-- 适用: 已存在 25 张表(至 study_sessions)的存量库
-- 前置: 必须先执行 upgrade_2026-11_study_time.sql（本文件在其之后, 字典序 2026-11 < 2026-12）
--
-- 幂等说明（与 upgrade_2026-11 同一套做法）:
--   本脚本含 ALTER TABLE, 而 MySQL 8 不支持 ADD COLUMN IF NOT EXISTS,
--   因此先用 information_schema 判断、再 PREPARE 动态执行。两种场景都安全:
--     ① 新库场景 —— 根 docker-compose 把整个 db/ 目录挂成 initdb, schema.sql 先跑且已含这些列,
--        直接 ALTER 会因「Duplicate column name」在 set -e 的 entrypoint 里中断容器初始化;
--     ② 重复执行 —— deploy/README-部署说明.md 教的是「按顺序把所有 upgrade_*.sql 都跑一遍」。
--
-- 说明: environment_settings / contracts 两张表在前四份脚本中已存在, 本次只是**开始使用**它们,
--       其中 environment_settings 需要补一个 category 列来区分「手机节制」与「习惯环境」。
--
-- 回滚(如需撤销本次升级):
--   DROP TABLE IF EXISTS focus_logs;
--   ALTER TABLE environment_settings DROP COLUMN category;
--   ALTER TABLE users DROP COLUMN focus_daily_limit;
-- ============================================================

USE habitforge;

SET @hf_db := DATABASE();

-- 26. 注意力日志表（P2 手机节制: user × day 一天一行, 与 journals 同构）
--     娱乐时长 ≤ 用户上限 记为「节制达标」, 达标/撤销按差量结算积分
CREATE TABLE IF NOT EXISTS focus_logs (
    id                    VARCHAR(36)  PRIMARY KEY DEFAULT (UUID()),
    user_id               VARCHAR(36)  NOT NULL COMMENT '所属用户',
    log_date              DATE         NOT NULL COMMENT '日志日期(不允许未来)',
    entertainment_minutes INT          NULL COMMENT '娱乐/短视频时长(分钟; NULL=当日尚未录入, 不算达标)',
    pickups               INT          NULL COMMENT '拿起手机次数(可选)',
    urge_total            INT          NOT NULL DEFAULT 0 COMMENT '当日冲动次数(想刷的瞬间)',
    urge_resisted         INT          NOT NULL DEFAULT 0 COMMENT '其中忍住没刷的次数',
    note                  VARCHAR(200) NULL COMMENT '备注',
    created_at            DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_date (user_id, log_date),
    INDEX idx_user_date (user_id, log_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='注意力日志表(手机节制)';

-- environment_settings 补 category: 区分「手机节制清单」与「习惯环境设计」
SET @hf_done := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @hf_db AND TABLE_NAME = 'environment_settings' AND COLUMN_NAME = 'category');
SET @hf_ddl := IF(@hf_done = 0,
    'ALTER TABLE environment_settings ADD COLUMN category VARCHAR(20) DEFAULT ''OTHER'' COMMENT ''类别: PHONE手机节制/HABIT习惯环境/OTHER其他''',
    'DO 0');
PREPARE hf_stmt FROM @hf_ddl;
EXECUTE hf_stmt;
DEALLOCATE PREPARE hf_stmt;

-- users 补 focus_daily_limit: 每日娱乐时长上限（分钟, NULL=用系统默认值）
SET @hf_done := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @hf_db AND TABLE_NAME = 'users' AND COLUMN_NAME = 'focus_daily_limit');
SET @hf_ddl := IF(@hf_done = 0,
    'ALTER TABLE users ADD COLUMN focus_daily_limit INT NULL COMMENT ''每日娱乐时长上限(分钟, NULL=用系统默认值)''',
    'DO 0');
PREPARE hf_stmt FROM @hf_ddl;
EXECUTE hf_stmt;
DEALLOCATE PREPARE hf_stmt;

-- contracts 表已存在且字段够用（partner_name/penalty/is_public/status/signed_at）, 本次无需 DDL。
-- 仅提示: habit_id 为 NOT NULL + FK CASCADE, 契约必须挂在一个习惯上。
