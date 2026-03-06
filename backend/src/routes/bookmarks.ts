import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware } from '../middleware/auth';

export const bookmarkRoutes = new Hono<{ Bindings: Env }>();

// 获取收藏列表
bookmarkRoutes.get('/', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };
  const page = parseInt(c.req.query('page') || '1');
  const limit = parseInt(c.req.query('limit') || '20');
  const offset = (page - 1) * limit;

  const result = await c.env.DB.prepare(
    `SELECT b.id, b.created_at as bookmarked_at, a.id as article_id, a.title, a.summary, 
     a.author_name, a.category_name, a.view_count, a.like_count, a.created_at
     FROM bookmarks b
     JOIN articles a ON b.article_id = a.id
     WHERE b.user_id = ?
     ORDER BY b.created_at DESC
     LIMIT ? OFFSET ?`
  ).bind(user.id, limit, offset).all();

  const countResult = await c.env.DB.prepare(
    'SELECT COUNT(*) as total FROM bookmarks WHERE user_id = ?'
  ).bind(user.id).first<{ total: number }>();

  return c.json({
    bookmarks: result.results,
    pagination: {
      page, limit,
      total: countResult?.total || 0,
      total_pages: Math.ceil((countResult?.total || 0) / limit),
    }
  });
});

// 检查是否已收藏
bookmarkRoutes.get('/check/:articleId', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };
  const articleId = c.req.param('articleId');

  const existing = await c.env.DB.prepare(
    'SELECT id FROM bookmarks WHERE user_id = ? AND article_id = ?'
  ).bind(user.id, articleId).first();

  return c.json({ bookmarked: !!existing });
});

// 添加/取消收藏（toggle）
bookmarkRoutes.post('/:articleId', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };
  const articleId = c.req.param('articleId');

  const existing = await c.env.DB.prepare(
    'SELECT id FROM bookmarks WHERE user_id = ? AND article_id = ?'
  ).bind(user.id, articleId).first();

  if (existing) {
    await c.env.DB.prepare(
      'DELETE FROM bookmarks WHERE user_id = ? AND article_id = ?'
    ).bind(user.id, articleId).run();
    return c.json({ message: '已取消收藏', bookmarked: false });
  } else {
    await c.env.DB.prepare(
      'INSERT INTO bookmarks (user_id, article_id) VALUES (?, ?)'
    ).bind(user.id, articleId).run();
    return c.json({ message: '已收藏', bookmarked: true });
  }
});

// 阅读历史列表
bookmarkRoutes.get('/history', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };
  const page = parseInt(c.req.query('page') || '1');
  const limit = parseInt(c.req.query('limit') || '20');
  const offset = (page - 1) * limit;

  const result = await c.env.DB.prepare(
    `SELECT rh.id, rh.read_at, a.id as article_id, a.title, a.summary,
     a.author_name, a.category_name, a.view_count, a.created_at
     FROM reading_history rh
     JOIN articles a ON rh.article_id = a.id
     WHERE rh.user_id = ?
     ORDER BY rh.read_at DESC
     LIMIT ? OFFSET ?`
  ).bind(user.id, limit, offset).all();

  const countResult = await c.env.DB.prepare(
    'SELECT COUNT(*) as total FROM reading_history WHERE user_id = ?'
  ).bind(user.id).first<{ total: number }>();

  return c.json({
    history: result.results,
    pagination: {
      page, limit,
      total: countResult?.total || 0,
      total_pages: Math.ceil((countResult?.total || 0) / limit),
    }
  });
});

// 记录阅读历史
bookmarkRoutes.post('/history/:articleId', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };
  const articleId = c.req.param('articleId');

  // 删除旧记录（避免重复），再插入新记录（更新时间）
  await c.env.DB.prepare(
    'DELETE FROM reading_history WHERE user_id = ? AND article_id = ?'
  ).bind(user.id, articleId).run();

  await c.env.DB.prepare(
    'INSERT INTO reading_history (user_id, article_id) VALUES (?, ?)'
  ).bind(user.id, articleId).run();

  return c.json({ message: '已记录' });
});

// 清空阅读历史
bookmarkRoutes.delete('/history', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };
  await c.env.DB.prepare('DELETE FROM reading_history WHERE user_id = ?').bind(user.id).run();
  return c.json({ message: '已清空' });
});
