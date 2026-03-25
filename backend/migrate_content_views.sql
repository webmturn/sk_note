-- 阅读去重表（防止同一用户/IP重复计数）
CREATE TABLE IF NOT EXISTS content_views (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    viewer_key TEXT NOT NULL,
    target_type TEXT NOT NULL CHECK(target_type IN ('article', 'discussion', 'snippet', 'share')),
    target_id INTEGER NOT NULL,
    viewed_at TEXT DEFAULT (datetime('now')),
    UNIQUE(viewer_key, target_type, target_id)
);

CREATE INDEX IF NOT EXISTS idx_content_views_target ON content_views(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_content_views_viewed_at ON content_views(viewed_at);
