import { Hono } from 'hono';
import type { AppEnv } from '../index';
import { authMiddleware, isOwnerOrAdmin } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';
import { rateLimit, userOrIpIdentifier } from '../middleware/rateLimit';
import { toggleLike } from '../likeUtils';

export const shareRoutes = new Hono<AppEnv>();

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
  const share = await c.env.DB.prepare('SELECT * FROM shares WHERE id = ? AND is_approved = 1').bind(id).first();
  if (!share) return c.json({ error: '未找到' }, 404);

  const viewerKey = c.req.header('x-forwarded-for')?.split(',')[0]?.trim()
    || c.req.header('x-real-ip')
    || `anon:${(c.req.header('user-agent') || 'unknown').slice(0, 64)}`;
  const viewResult = await c.env.DB.prepare(
    'INSERT OR IGNORE INTO content_views (viewer_key, target_type, target_id) VALUES (?, ?, ?)' 
  ).bind(viewerKey, 'share', id).run();
  if (viewResult.meta.changes > 0) {
    await c.env.DB.prepare('UPDATE shares SET view_count = view_count + 1 WHERE id = ?').bind(id).run();
    if (typeof (share as any).view_count === 'number') {
      (share as any).view_count += 1;
    }
  }
  return c.json({ share });
});

// 记录下载次数（IP 去重）
shareRoutes.post('/:id/download', async (c) => {
  const id = c.req.param('id');
  const share = await c.env.DB.prepare('SELECT id FROM shares WHERE id = ? AND is_approved = 1').bind(id).first();
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
  const user = c.get('user')!;
  const { title, description, category, download_url, download_pwd, file_size } = await c.req.json();

  if (!title || !download_url) {
    return c.json({ error: '标题和下载链接不能为空' }, 400);
  }
  if (title.length > 200) {
    return c.json({ error: '标题最长200个字符' }, 400);
  }
  if (download_url.length > 500) {
    return c.json({ error: '下载链接最长500个字符' }, 400);
  }
  if (description && description.length > 2000) {
    return c.json({ error: '描述最长2000个字符' }, 400);
  }

  const validCategories = ['general', 'apk', 'mod', 'resource', 'plugin', 'tool', 'other'];
  if (category && !validCategories.includes(category)) {
    return c.json({ error: `无效的分类，可选: ${validCategories.join(', ')}` }, 400);
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
  await purgeCache([`${baseUrl}/api/shares`, `${baseUrl}/api/shares/categories`, `${baseUrl}/api/stats`]);
  return c.json({ message: '分享成功', id: result.meta.last_row_id }, 201);
});

// 更新分享（作者或管理员）
shareRoutes.put('/:id', authMiddleware(), async (c) => {
  const id = c.req.param('id');

  const share = await c.env.DB.prepare('SELECT author_id FROM shares WHERE id = ?').bind(id).first<{ author_id: number }>();
  if (!share) return c.json({ error: '未找到' }, 404);
  if (!(await isOwnerOrAdmin(c, share.author_id))) {
    return c.json({ error: '无权限' }, 403);
  }

  const { title, description, category, download_url, download_pwd, file_size } = await c.req.json();

  if (!title || !download_url) {
    return c.json({ error: '标题和下载链接不能为空' }, 400);
  }
  if (title.length > 200) {
    return c.json({ error: '标题最长200个字符' }, 400);
  }
  if (download_url.length > 500) {
    return c.json({ error: '下载链接最长500个字符' }, 400);
  }
  if (description && description.length > 2000) {
    return c.json({ error: '描述最长2000个字符' }, 400);
  }

  const validCategories = ['general', 'apk', 'mod', 'resource', 'plugin', 'tool', 'other'];
  if (category && !validCategories.includes(category)) {
    return c.json({ error: `无效的分类，可选: ${validCategories.join(', ')}` }, 400);
  }

  await c.env.DB.prepare(
    `UPDATE shares SET title = ?, description = ?, category = ?, download_url = ?, download_pwd = ?, file_size = ?, updated_at = datetime('now') WHERE id = ?`
  ).bind(
    title,
    description || '',
    category || 'general',
    download_url,
    download_pwd || '',
    file_size || '',
    id
  ).run();

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/shares`, `${baseUrl}/api/shares/${id}`, `${baseUrl}/api/shares/categories`]);
  return c.json({ message: '更新成功' });
});

// 删除分享（作者或管理员）
shareRoutes.delete('/:id', authMiddleware(), async (c) => {
  const id = c.req.param('id');

  const share = await c.env.DB.prepare('SELECT author_id FROM shares WHERE id = ?').bind(id).first<{ author_id: number }>();
  if (!share) return c.json({ error: '未找到' }, 404);
  if (!(await isOwnerOrAdmin(c, share.author_id))) {
    return c.json({ error: '无权限' }, 403);
  }

  await c.env.DB.prepare('DELETE FROM shares WHERE id = ?').bind(id).run();
  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/shares`, `${baseUrl}/api/shares/${id}`, `${baseUrl}/api/shares/categories`, `${baseUrl}/api/stats`]);
  return c.json({ message: '已删除' });
});

// 点赞分享
shareRoutes.post('/:id/like', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user')!;

  const share = await c.env.DB.prepare('SELECT id FROM shares WHERE id = ?').bind(id).first();
  if (!share) return c.json({ error: '分享不存在' }, 404);

  const result = await toggleLike(c, {
    userId: user.id,
    targetId: id,
    targetType: 'share',
    countTable: 'shares',
    likeSuccessMessage: '已点赞',
    unlikeSuccessMessage: '取消点赞',
  });

  return c.json(result);
});
