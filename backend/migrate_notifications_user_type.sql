-- 迁移：扩展 notifications.related_type 允许 'user'（用于关注通知等）
-- SQLite 无法直接修改 CHECK 约束，按惯例先重命名旧表 -> 新建 -> 拷贝数据 -> 删除旧表

PRAGMA foreign_keys = OFF;

BEGIN TRANSACTION;

ALTER TABLE notifications RENAME TO notifications_old;

CREATE TABLE notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    type TEXT NOT NULL CHECK(type IN ('reply', 'like', 'mention', 'system')),
    title TEXT NOT NULL,
    content TEXT DEFAULT '',
    related_type TEXT DEFAULT NULL CHECK(related_type IS NULL OR related_type IN ('discussion', 'comment', 'article', 'snippet', 'share', 'user')),
    related_id INTEGER DEFAULT NULL,
    is_read INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO notifications (id, user_id, type, title, content, related_type, related_id, is_read, created_at)
SELECT id, user_id, type, title, content, related_type, related_id, is_read, created_at
FROM notifications_old;

DROP TABLE notifications_old;

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, is_read);

COMMIT;

PRAGMA foreign_keys = ON;
