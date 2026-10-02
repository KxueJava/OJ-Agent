-- 讨论区（阶段一/二）：主题帖 + 楼层回复。
--
-- 设计要点：
--  1) id 全部由服务端雪花式生成（与 submissions 等一致），不使用固定 id 段 —— 避免历史上"生成器 id 段撞车"那类问题；
--  2) 帖子可关联题目（problem_id 可空）：关联后，若该题属于"我正在参加且进行中的比赛"，服务端会拒绝发布/回复
--     —— 与 Agent 赛中锁同一套判定（ContestService.lockedContestSlugForProblem）；
--  3) reply_count / last_reply_at 冗余存储，列表排序不必聚合子表；views 为浏览计数。
CREATE TABLE IF NOT EXISTS discussions (
    id             BIGINT       NOT NULL PRIMARY KEY,
    category       VARCHAR(16)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    body_md        MEDIUMTEXT   NOT NULL,
    problem_id     BIGINT       NULL,
    author_id      BIGINT       NOT NULL,
    pinned         TINYINT(1)   NOT NULL DEFAULT 0,
    locked         TINYINT(1)   NOT NULL DEFAULT 0,
    accepted_post_id BIGINT     NULL,
    views          INT          NOT NULL DEFAULT 0,
    reply_count    INT          NOT NULL DEFAULT 0,
    last_reply_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_discussions_last (last_reply_at),
    KEY idx_discussions_category (category),
    KEY idx_discussions_problem (problem_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS discussion_posts (
    id            BIGINT     NOT NULL PRIMARY KEY,
    discussion_id BIGINT     NOT NULL,
    author_id     BIGINT     NOT NULL,
    body_md       MEDIUMTEXT NOT NULL,
    quoted_post_id BIGINT    NULL,
    upvotes       INT        NOT NULL DEFAULT 0,
    accepted      TINYINT(1) NOT NULL DEFAULT 0,
    created_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_posts_discussion (discussion_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
