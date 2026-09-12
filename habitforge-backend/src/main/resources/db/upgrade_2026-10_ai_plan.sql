-- ============================================================
-- 增量升级: P2 AI 今日安排(每日计划/计划块/空闲时段/生成流水)
-- 日期: 2026-09-13
-- 适用: 已存在 20 张表(至 wrong_questions)的存量库
-- 前置: 必须先执行 upgrade_2026-09_study.sql
--       (plan_blocks FK 引用 subjects/chapters)
--       文件名字典序 2026-09 < 2026-10, initdb 顺序天然满足
--
-- 回滚(如需撤销本次升级, 按 FK 依赖序先删子表再删父表):
--   DROP TABLE IF EXISTS plan_generations;
--   DROP TABLE IF EXISTS plan_free_slots;
--   DROP TABLE IF EXISTS plan_blocks;
--   DROP TABLE IF EXISTS daily_plans;
-- ============================================================

USE habitforge;

-- 21. 每日计划表（一天一份 uk_user_date; 懒创建: 首次存时段或生成时建行）
CREATE TABLE IF NOT EXISTS daily_plans (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户',
    plan_date       DATE            NOT NULL COMMENT '计划日期(服务端 LocalDate)',
    gen_count       INT             DEFAULT 0 COMMENT '当日生成次数(冗余计数, 限流真值在 Redis)',
    last_model      VARCHAR(100)    NULL COMMENT '最近一次生成所用模型',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_date (user_id, plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日计划表';

-- 22. 计划块表（时间块; 引用列 FK SET NULL: 习惯/科目删了不连删块; 状态机 PROPOSED→ADOPTED→DONE→SKIPPED）
CREATE TABLE IF NOT EXISTS plan_blocks (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    plan_id         VARCHAR(36)     NOT NULL COMMENT '所属每日计划',
    habit_id        VARCHAR(36)     NULL COMMENT '关联习惯(AI 只能从候选清单挑选, 防幻觉白名单校验)',
    subject_id      VARCHAR(36)     NULL COMMENT '关联科目',
    chapter_id      VARCHAR(36)     NULL COMMENT '关联章节',
    block_type      VARCHAR(20)     NOT NULL COMMENT '类型: HABIT习惯/STUDY学习/REST休息/OTHER其他',
    title           VARCHAR(100)    NOT NULL COMMENT '标题(中文≤30字由 prompt+校验约束)',
    start_time      TIME            NOT NULL COMMENT '开始时间',
    end_time        TIME            NOT NULL COMMENT '结束时间',
    sort_order      INT             DEFAULT 0 COMMENT '排序(越小越靠前)',
    status          VARCHAR(20)     DEFAULT 'PROPOSED' COMMENT '状态: PROPOSED待采纳/ADOPTED已采纳/DONE已完成/SKIPPED已跳过',
    source          VARCHAR(10)     DEFAULT 'AI' COMMENT '来源: AI生成/MANUAL手动(手动块创建即 ADOPTED)',
    completed_at    DATETIME        NULL COMMENT '完成时间(仅 DONE 非空)',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id)    REFERENCES daily_plans(id) ON DELETE CASCADE,
    FOREIGN KEY (habit_id)   REFERENCES habits(id)      ON DELETE SET NULL,
    FOREIGN KEY (subject_id) REFERENCES subjects(id)    ON DELETE SET NULL,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id)    ON DELETE SET NULL,
    INDEX idx_plan (plan_id),
    INDEX idx_habit (habit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划块表';

-- 23. 计划空闲时段表（PUT 整体覆盖式保存: 事务内先删后插）
CREATE TABLE IF NOT EXISTS plan_free_slots (
    id              VARCHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    plan_id         VARCHAR(36)     NOT NULL COMMENT '所属每日计划',
    start_time      TIME            NOT NULL COMMENT '开始时间',
    end_time        TIME            NOT NULL COMMENT '结束时间',
    label           VARCHAR(50)     NULL COMMENT '标签(如: 午休/晚间, 纯前端快捷标签预设)',
    sort_order      INT             DEFAULT 0 COMMENT '排序(越小越靠前)',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES daily_plans(id) ON DELETE CASCADE,
    INDEX idx_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划空闲时段表';

-- 24. AI 生成流水表（成本审计+排障; raw_output 定期裁剪留 30 天）
CREATE TABLE IF NOT EXISTS plan_generations (
    id                VARCHAR(36)   PRIMARY KEY DEFAULT (UUID()),
    user_id           VARCHAR(36)   NOT NULL COMMENT '所属用户',
    plan_id           VARCHAR(36)   NULL COMMENT '目标计划(懒创建失败时可空)',
    model             VARCHAR(100)  NULL COMMENT '模型名',
    prompt_tokens     INT           NULL COMMENT '输入 token 用量',
    completion_tokens INT           NULL COMMENT '输出 token 用量',
    total_tokens      INT           NULL COMMENT '总 token 用量',
    success           TINYINT(1)    NOT NULL COMMENT '本次生成是否成功',
    error             VARCHAR(500)  NULL COMMENT '失败原因摘要',
    raw_output        MEDIUMTEXT    NULL COMMENT '模型原始输出(排障留痕)',
    created_at        DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)      ON DELETE CASCADE,
    FOREIGN KEY (plan_id) REFERENCES daily_plans(id) ON DELETE SET NULL,
    INDEX idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 生成流水表';
