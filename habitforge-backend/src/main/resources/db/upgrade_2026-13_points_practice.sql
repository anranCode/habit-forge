-- ============================================================
-- 增量升级: 考点细化 + 刷题判分（功能A/B）
-- 日期: 2026-10-13
-- 适用: 已存在 26 张表(至 focus_logs)的存量库
-- 前置: 必须先执行 upgrade_2026-09_study.sql
--       (question_grades FK 引用 questions; plan_generations 的 kind 列由 2026-11 引入)
--       文件名 2026-09 < 2026-11 < 2026-13, initdb 顺序天然满足
--
-- 幂等说明（沿袭 upgrade_2026-11 的守卫模板, 理由相同）:
--   MySQL 8 不支持 ADD COLUMN IF NOT EXISTS。两条硬约束:
--     ① 新库场景 —— 根 docker-compose 把整个 db/ 挂成 initdb, schema.sql 先跑且已含
--        这些列/表, 直接 ALTER 会 Duplicate column name 中断容器初始化;
--     ② 重复执行 —— 部署教的是「按顺序把所有 upgrade_*.sql 都跑一遍」, 本脚本必须可重跑。
--   加列用 information_schema 判断 + PREPARE 动态执行; 建表用 CREATE TABLE IF NOT EXISTS;
--   MODIFY COLUMN(改 kind 注释)本身幂等, 无需守卫。
--
-- 回滚(如需撤销本次升级):
--   DROP TABLE IF EXISTS question_grades;
--   ALTER TABLE plan_generations DROP COLUMN ref_id;
--   ALTER TABLE questions DROP COLUMN knowledge_points;
-- ============================================================

USE habitforge;

SET @hf_db := DATABASE();

-- 题目表加「考点标签」列（JSON 字符串数组, 服务层 hutool 序列化, 仿 options 惯例不用 TypeHandler）
SET @hf_done := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @hf_db AND TABLE_NAME = 'questions' AND COLUMN_NAME = 'knowledge_points');
SET @hf_ddl := IF(@hf_done = 0,
    'ALTER TABLE questions ADD COLUMN knowledge_points TEXT NULL COMMENT ''考点标签JSON字符串数组(如 ["中值定理","极限计算"])''',
    'DO 0');
PREPARE hf_stmt FROM @hf_ddl;
EXECUTE hf_stmt;
DEALLOCATE PREPARE hf_stmt;

-- AI 流水表加通用引用列（POINTS/GRADE 流为 question_id; 旧 PLAN/WEEKLY_REPORT 行留 NULL;
-- 刻意不加 FK —— 流水表要保持通用、且删题后流水还该留着审计）
SET @hf_done := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @hf_db AND TABLE_NAME = 'plan_generations' AND COLUMN_NAME = 'ref_id');
SET @hf_ddl := IF(@hf_done = 0,
    'ALTER TABLE plan_generations ADD COLUMN ref_id VARCHAR(36) NULL COMMENT ''通用引用对象(POINTS/GRADE=question_id)''',
    'DO 0');
PREPARE hf_stmt FROM @hf_ddl;
EXECUTE hf_stmt;
DEALLOCATE PREPARE hf_stmt;

-- kind 注释扩为四态（列定义与 2026-11 引入时一致, 只改 COMMENT, 幂等）
ALTER TABLE plan_generations
    MODIFY COLUMN kind VARCHAR(20) DEFAULT 'PLAN'
    COMMENT '类型: PLAN今日安排/WEEKLY_REPORT周报/POINTS考点识别/GRADE主观题判分';

-- 27. 主观题 AI 判分记录（一次判分一行; 重答即重判、追加不覆盖; 删题连带删留痕）
CREATE TABLE IF NOT EXISTS question_grades (
    id           VARCHAR(36)   PRIMARY KEY DEFAULT (UUID()),
    user_id      VARCHAR(36)   NOT NULL COMMENT '所属用户',
    question_id  VARCHAR(36)   NOT NULL COMMENT '题目ID',
    user_answer  VARCHAR(2000) NOT NULL COMMENT '学生作答全文(服务层截断后落库)',
    correct      TINYINT(1)    NOT NULL COMMENT '判定结果(阈值换算在服务端, 非模型输出)',
    points       TEXT          NULL COMMENT 'AI 要点拆解JSON: [{"point":"...","hit":true}]',
    covered      INT           NULL COMMENT '命中要点数',
    total        INT           NULL COMMENT '要点总数',
    ratio        DECIMAL(4,2)  NULL COMMENT '覆盖率 covered/total(审计冗余; 判定不读它, 见 SHORT_GRADE_PASS_RATIO)',
    model        VARCHAR(100)  NULL COMMENT '判分所用模型',
    total_tokens INT           NULL COMMENT '本次判分 token 用量',
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)     REFERENCES users(id)     ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    INDEX idx_user_question (user_id, question_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主观题AI判分记录表';
