-- 创建讨论分类表并为现有数据迁移 discussion.category 的固定枚举约束
PRAGMA foreign_keys = OFF;

BEGIN TRANSACTION;

CREATE TABLE IF NOT EXISTS discussion_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    icon TEXT DEFAULT '',
    sort_order INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO discussion_categories (slug, name, description, icon, sort_order)
VALUES
    ('general', '综合', '通用讨论', 'default', 0),
    ('question', '提问', '问题求助与答疑', 'question', 10),
    ('feedback', '反馈', '体验反馈与意见', 'feedback', 20),
    ('bug', 'Bug', '缺陷和异常问题', 'bug', 30),
    ('feature', '功能建议', '新功能和改进建议', 'feature', 40);

CREATE TABLE IF NOT EXISTS discussions_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    author_id INTEGER NOT NULL,
    article_id INTEGER DEFAULT NULL,
    category TEXT NOT NULL DEFAULT 'general',
    is_pinned INTEGER DEFAULT 0,
    is_closed INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    reply_count INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE SET NULL,
    FOREIGN KEY (category) REFERENCES discussion_categories(slug) ON DELETE RESTRICT ON UPDATE CASCADE
);

INSERT INTO discussions_new (id, title, content, author_id, article_id, category, is_pinned, is_closed, view_count, reply_count, created_at, updated_at)
SELECT id, title, content, author_id, article_id, category, is_pinned, is_closed, view_count, reply_count, created_at, updated_at
FROM discussions;

DROP TABLE discussions;
ALTER TABLE discussions_new RENAME TO discussions;

CREATE INDEX IF NOT EXISTS idx_discussion_categories_slug ON discussion_categories(slug);
CREATE INDEX IF NOT EXISTS idx_discussions_category ON discussions(category);
CREATE INDEX IF NOT EXISTS idx_discussions_author ON discussions(author_id);
CREATE INDEX IF NOT EXISTS idx_discussions_article ON discussions(article_id);

COMMIT;

PRAGMA foreign_keys = ON;
