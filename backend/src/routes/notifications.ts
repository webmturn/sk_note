import { Hono } from 'hono';
import type { AppEnv } from '../index';
import { authMiddleware } from '../middleware/auth';

export const notificationRoutes = new Hono<AppEnv>();

// 获取通知列表
notificationRoutes.get('/', authMiddleware(), async (c) => {
  const user = c.get('user')!;
  const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
  const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '20') || 20));
  const unreadOnly = c.req.query('unread') === '1';
  const offset = (page - 1) * limit;

  let query = 'SELECT * FROM notifications WHERE user_id = ?';
  const params: any[] = [user.id];

  if (unreadOnly) {
    query += ' AND is_read = 0';
  }

  query += ' ORDER BY created_at DESC LIMIT ? OFFSET ?';
  params.push(limit, offset);

  const notifications = await c.env.DB.prepare(query).bind(...params).all();

  // 未读数量
  const unreadCount = await c.env.DB.prepare(
    'SELECT COUNT(*) as count FROM notifications WHERE user_id = ? AND is_read = 0'
  ).bind(user.id).first<{ count: number }>();

  // 总数
  let countQuery = 'SELECT COUNT(*) as total FROM notifications WHERE user_id = ?';
  const countParams: any[] = [user.id];
  if (unreadOnly) {
    countQuery += ' AND is_read = 0';
  }
  const countResult = await c.env.DB.prepare(countQuery).bind(...countParams).first<{ total: number }>();

  return c.json({
    notifications: notifications.results,
    unread_count: unreadCount?.count || 0,
    pagination: {
      page,
      limit,
      total: countResult?.total || 0,
      total_pages: Math.ceil((countResult?.total || 0) / limit),
    }
  });
});

// 获取未读通知数量
notificationRoutes.get('/unread-count', authMiddleware(), async (c) => {
  const user = c.get('user')!;

  const result = await c.env.DB.prepare(
    'SELECT COUNT(*) as count FROM notifications WHERE user_id = ? AND is_read = 0'
  ).bind(user.id).first<{ count: number }>();

  return c.json({ count: result?.count || 0 });
});

// 标记所有通知为已读（必须在 /:id/read 之前注册，否则 "read-all" 会匹配为 :id）
notificationRoutes.put('/read-all', authMiddleware(), async (c) => {
  const user = c.get('user')!;

  await c.env.DB.prepare(
    'UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0'
  ).bind(user.id).run();

  return c.json({ message: '全部已读' });
});

// 标记单条通知为已读
notificationRoutes.put('/:id/read', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user')!;

  await c.env.DB.prepare(
    'UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?'
  ).bind(id, user.id).run();

  return c.json({ message: '已标记为已读' });
});

// 删除全部通知（必须在 /:id 之前注册，否则 "all" 会匹配为 :id）
notificationRoutes.delete('/all', authMiddleware(), async (c) => {
  const user = c.get('user')!;
  await c.env.DB.prepare('DELETE FROM notifications WHERE user_id = ?').bind(user.id).run();
  return c.json({ message: '已清空全部通知' });
});

// 删除通知
notificationRoutes.delete('/:id', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user')!;

  await c.env.DB.prepare(
    'DELETE FROM notifications WHERE id = ? AND user_id = ?'
  ).bind(id, user.id).run();

  return c.json({ message: '删除成功' });
});

// 辅助函数：创建通知（供其他模块调用）
export async function createNotification(
  db: D1Database,
  userId: number,
  type: string,
  title: string,
  content: string,
  relatedType?: string,
  relatedId?: number
) {
  await db.prepare(`
    INSERT INTO notifications (user_id, type, title, content, related_type, related_id)
    VALUES (?, ?, ?, ?, ?, ?)
  `).bind(userId, type, title, content, relatedType || null, relatedId || null).run();
}

export async function deleteNotificationsByRelatedTargets(
  db: D1Database,
  relatedType: string,
  relatedIds: number[]
) {
  const ids = relatedIds.filter((id, index, arr) => Number.isInteger(id) && id > 0 && arr.indexOf(id) === index);
  if (ids.length === 0) return;

  const placeholders = ids.map(() => '?').join(', ');
  await db.prepare(
    `DELETE FROM notifications WHERE related_type = ? AND related_id IN (${placeholders})`
  ).bind(relatedType, ...ids).run();
}

// 获取用户的展示名（昵称优先，否则用户名，最后兜底为“有人”）
export async function getActorDisplayName(db: D1Database, actorId: number): Promise<string> {
  if (!Number.isInteger(actorId) || actorId <= 0) return '有人';
  const row = await db.prepare(
    'SELECT username, nickname FROM users WHERE id = ?'
  ).bind(actorId).first<{ username: string | null; nickname: string | null }>();
  const nickname = (row?.nickname || '').trim();
  return nickname || (row?.username || '').trim() || '有人';
}

// 互动类通知的统一封装：跳过自我通知，失败只记录日志不阻断主流程
export async function notifyActor(
  db: D1Database,
  params: {
    ownerId: number;
    actorId: number;
    type: string;
    title: string;
    content?: string;
    relatedType?: string;
    relatedId?: number;
  }
): Promise<void> {
  const { ownerId, actorId, type, title, content, relatedType, relatedId } = params;
  if (!Number.isInteger(ownerId) || ownerId <= 0) return;
  if (ownerId === actorId) return;
  try {
    await createNotification(db, ownerId, type, title, content || '', relatedType, relatedId);
  } catch (e) {
    console.error('通知创建失败:', e);
  }
}
