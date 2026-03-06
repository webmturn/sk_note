import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware } from '../middleware/auth';

export const notificationRoutes = new Hono<{ Bindings: Env }>();

// 获取通知列表
notificationRoutes.get('/', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };
  const page = parseInt(c.req.query('page') || '1');
  const limit = parseInt(c.req.query('limit') || '20');
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
  const user = c.get('user' as never) as { id: number };

  const result = await c.env.DB.prepare(
    'SELECT COUNT(*) as count FROM notifications WHERE user_id = ? AND is_read = 0'
  ).bind(user.id).first<{ count: number }>();

  return c.json({ count: result?.count || 0 });
});

// 标记单条通知为已读
notificationRoutes.put('/:id/read', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user' as never) as { id: number };

  await c.env.DB.prepare(
    'UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?'
  ).bind(id, user.id).run();

  return c.json({ message: '已标记为已读' });
});

// 标记所有通知为已读
notificationRoutes.put('/read-all', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };

  await c.env.DB.prepare(
    'UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0'
  ).bind(user.id).run();

  return c.json({ message: '全部已读' });
});

// 删除通知
notificationRoutes.delete('/:id', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user' as never) as { id: number };

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
