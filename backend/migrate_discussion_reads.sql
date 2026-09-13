-- 讨论已读位置表：记录「某用户在某讨论里读到了什么时候」，
-- 用来在列表上标出「读完之后又有了新回复」。
--
-- 为什么不能复用 content_views：
--   1) 它写的是 INSERT OR IGNORE —— 首次浏览就定型，viewed_at 永不更新；
--   2) server.ts 里有个定时清理，30 天前的行会被删掉。
--   阅读位置需要「最后一次读」且不能过期，所以单开一张表。
--
-- 非破坏性：只建新表（IF NOT EXISTS），不动任何现有表。
-- 重复执行是安全的（IF NOT EXISTS），不需要 try/catch 兜底。
CREATE TABLE IF NOT EXISTS discussion_reads (
    user_id INTEGER NOT NULL,
    discussion_id INTEGER NOT NULL,
    last_read_at TEXT DEFAULT (datetime('now')),
    PRIMARY KEY (user_id, discussion_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (discussion_id) REFERENCES discussions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_discussion_reads_user ON discussion_reads(user_id);
