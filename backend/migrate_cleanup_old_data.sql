-- 周期性清理脚本（建议每月手动执行一次或加入 cron）
-- content_views: 用作浏览去重，保留最近 30 天即可
-- reading_history: 每个用户仅保留最近 200 条阅读记录

-- 1) 清理超过 30 天的浏览去重记录
DELETE FROM content_views
WHERE viewed_at < datetime('now', '-30 days');

-- 2) 仅保留每个用户最近 200 条阅读历史
DELETE FROM reading_history
WHERE id NOT IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY read_at DESC) AS rn
        FROM reading_history
    )
    WHERE rn <= 200
);

-- 3) 清理孤儿浏览记录（关联内容已删除）
DELETE FROM content_views
WHERE (target_type = 'article'    AND target_id NOT IN (SELECT id FROM articles))
   OR (target_type = 'discussion' AND target_id NOT IN (SELECT id FROM discussions))
   OR (target_type = 'snippet'    AND target_id NOT IN (SELECT id FROM snippets))
   OR (target_type = 'share'      AND target_id NOT IN (SELECT id FROM shares));

-- 4) 整理空间
VACUUM;
