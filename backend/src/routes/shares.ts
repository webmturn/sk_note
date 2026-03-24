import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';
import { rateLimit, userOrIpIdentifier } from '../middleware/rateLimit';

export const shareRoutes = new Hono<{ Bindings: Env }>();

// 获取分享列表（支持分页、分类、搜索）
shareRoutes.get('/', edgeCache(120), async (c) => {
  const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
  const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '20') || 20));
  const category = c.req.query('category');
  const search = c.req.query('search');
  const offset = (page - 1) * limit;

  let query = 'SELECT * FROM shares WHERE is_approved = 1';
  const params: any[] = [];

  if (category) {
    query += ' AND category = ?';
    params.push(category);
  }
  if (search) {
    query += ' AND (title LIKE ? OR description LIKE ?)';
    params.push(`%${search}%`, `%${search}%`);
  }

  query += ' ORDER BY created_at DESC LIMIT ? OFFSET ?';
  params.push(limit, offset);

  const shares = await c.env.DB.prepare(query).bind(...params).all();

  let countQuery = 'SELECT COUNT(*) as total FROM shares WHERE is_approved = 1';
  const countParams: any[] = [];
  if (category) {
    countQuery += ' AND category = ?';
    countParams.push(category);
  }
  if (search) {
    countQuery += ' AND (title LIKE ? OR description LIKE ?)';
    countParams.push(`%${search}%`, `%${search}%`);
  }
  const countResult = await c.env.DB.prepare(countQuery).bind(...countParams).first<{ total: number }>();

  return c.json({
    shares: shares.results,
    pagination: {
      page,
      limit,
      total: countResult?.total || 0,
      total_pages: Math.ceil((countResult?.total || 0) / limit),
    }
  });
});

// 获取分类列表
shareRoutes.get('/categories', edgeCache(300), async (c) => {
  const result = await c.env.DB.prepare(
    'SELECT category, COUNT(*) as count FROM shares WHERE is_approved = 1 GROUP BY category ORDER BY count DESC'
  ).all();
  return c.json({ categories: result.results });
});

// 获取单个分享详情
shareRoutes.get('/:id', async (c) => {
  const id = c.req.param('id');
  const share = await c.env.DB.prepare('SELECT * FROM shares WHERE id = ?').bind(id).first();
  if (!share) return c.json({ error: '未找到' }, 404);

  const viewerKey = c.req.header('x-forwarded-for')?.split(',')[0]?.trim()
    || c.req.header('x-real-ip')
    || `anon:${(c.req.header('user-agent') || 'unknown').slice(0, 64)}`;
  const viewResult = await c.env.DB.prepare(
    'INSERT OR IGNORE INTO content_views (viewer_key, target_type, target_id) VALUES (?, ?, ?)' 
  ).bind(viewerKey, 'share', id).run();
  if (viewResult.meta.changes > 0) {
    await c.env.DB.prepare('UPDATE shares SET view_count = view_count + 1 WHERE id = ?').bind(id).run();
  }
  return c.json({ share });
});

// 记录下载次数（IP 去重）
shareRoutes.post('/:id/download', async (c) => {
  const id = c.req.param('id');
  const share = await c.env.DB.prepare('SELECT id FROM shares WHERE id = ?').bind(id).first();
  if (!share) return c.json({ error: '分享不存在' }, 404);

  const viewerKey = 'dl:' + (
    c.req.header('x-forwarded-for')?.split(',')[0]?.trim()
    || c.req.header('x-real-ip')
    || `anon:${(c.req.header('user-agent') || 'unknown').slice(0, 64)}`
  );
  const result = await c.env.DB.prepare(
    'INSERT OR IGNORE INTO content_views (viewer_key, target_type, target_id) VALUES (?, ?, ?)'
  ).bind(viewerKey, 'share', id).run();
  if (result.meta.changes > 0) {
    await c.env.DB.prepare('UPDATE shares SET download_count = download_count + 1 WHERE id = ?').bind(id).run();
  }
  return c.json({ message: '已记录' });
});

// 创建分享（需要登录）
shareRoutes.post('/', authMiddleware(), rateLimit({ key: 'share:create', maxRequests: 10, windowMs: 10 * 60 * 1000, identifier: userOrIpIdentifier }), async (c) => {
  const user = c.get('user' as never) as { id: number; username: string };
  const { title, description, category, download_url, download_pwd, file_size } = await c.req.json();

  if (!title || !download_url) {
    return c.json({ error: '标题和下载链接不能为空' }, 400);
  }

  const userInfo = await c.env.DB.prepare('SELECT nickname, username FROM users WHERE id = ?').bind(user.id).first<{ nickname: string; username: string }>();
  const displayName = (userInfo?.nickname || '').trim() || userInfo?.username || user.username || '';

  const result = await c.env.DB.prepare(
    `INSERT INTO shares (title, description, category, download_url, download_pwd, file_size, author_id, author_name)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    title,
    description || '',
    category || 'general',
    download_url,
    download_pwd || '',
    file_size || '',
    user.id,
    displayName
  ).run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/shares`, `${baseUrl}/api/shares/categories`, `${baseUrl}/api/stats`]));
  return c.json({ message: '分享成功', id: result.meta.last_row_id }, 201);
});

// 删除分享（作者或管理员）
shareRoutes.delete('/:id', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user' as never) as { id: number; role: string };

  const share = await c.env.DB.prepare('SELECT author_id FROM shares WHERE id = ?').bind(id).first<{ author_id: number }>();
  if (!share) return c.json({ error: '未找到' }, 404);
  if (share.author_id !== user.id && user.role !== 'admin') {
    return c.json({ error: '无权限' }, 403);
  }

  await c.env.DB.prepare('DELETE FROM shares WHERE id = ?').bind(id).run();
  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/shares`, `${baseUrl}/api/shares/${id}`, `${baseUrl}/api/shares/categories`, `${baseUrl}/api/stats`]));
  return c.json({ message: '已删除' });
});

// 点赞分享
shareRoutes.post('/:id/like', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user' as never) as { id: number };

  const results = await c.env.DB.batch([
    c.env.DB.prepare(
      'SELECT id FROM shares WHERE id = ?'
    ).bind(id),
    c.env.DB.prepare(
      'DELETE FROM likes WHERE user_id = ? AND target_type = ? AND target_id = ? AND EXISTS (SELECT 1 FROM shares WHERE id = ?)'
    ).bind(user.id, 'share', id, id),
    c.env.DB.prepare(
      'INSERT INTO likes (user_id, target_type, target_id) SELECT ?, ?, ? WHERE EXISTS (SELECT 1 FROM shares WHERE id = ?) AND changes() = 0'
    ).bind(user.id, 'share', id, id),
    c.env.DB.prepare(
      'UPDATE shares SET like_count = MAX(0, like_count + CASE WHEN changes() > 0 THEN 1 ELSE -1 END) WHERE id = ?'
    ).bind(id),
  ]);

  const exists = (results[0]?.results?.length || 0) > 0;
  if (!exists) {
    return c.json({ error: '分享不存在' }, 404);
  }

  const inserted = ((results[2]?.results?.[0] as any)?.changes || 0) > 0;
  return c.json({ message: inserted ? '已点赞' : '取消点赞', liked: inserted });
});
