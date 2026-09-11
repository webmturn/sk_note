-- 通知表增加 actor_id：记录触发通知的用户（用于点赞/关注通知去重与取消时清理）
-- 幂等：可重复执行（ADD COLUMN 已存在时报错可忽略）
ALTER TABLE notifications ADD COLUMN actor_id INTEGER DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_notifications_actor ON notifications(user_id, actor_id, type, is_read);
