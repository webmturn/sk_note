import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware, adminMiddleware } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';

export const articleRoutes = new Hono<{ Bindings: Env }>();

// 获取文章列表（支持分页和分类筛选）
articleRoutes.get('/', edgeCache(120), async (c) => {
  const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
  const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '20') || 20));
  const categoryId = c.req.query('category_id');
  const search = c.req.query('search');
  const offset = (page - 1) * limit;

  let query = `
    SELECT a.id, a.title, a.summary, a.category_id, a.author_id, a.view_count, a.like_count, a.sort_order, a.created_at, a.updated_at,
    COALESCE(NULLIF(u.nickname,''), u.username) as author_name, u.avatar_url as author_avatar, c.name as category_name
    FROM articles a
    LEFT JOIN users u ON a.author_id = u.id
    LEFT JOIN categories c ON a.category_id = c.id
    WHERE a.is_published = 1
  `;
  const params: any[] = [];

  if (categoryId) {
    query += ' AND a.category_id = ?';
    params.push(categoryId);
  }
  if (search) {
    query += ' AND (a.title LIKE ? OR a.content LIKE ?)';
    params.push(`%${search}%`, `%${search}%`);
  }

  query += ' ORDER BY a.sort_order ASC, a.created_at DESC LIMIT ? OFFSET ?';
  params.push(limit, offset);

  const articles = await c.env.DB.prepare(query).bind(...params).all();

  // 获取总数
  let countQuery = 'SELECT COUNT(*) as total FROM articles WHERE is_published = 1';
  const countParams: any[] = [];
  if (categoryId) {
    countQuery += ' AND category_id = ?';
    countParams.push(categoryId);
  }
  if (search) {
    countQuery += ' AND (title LIKE ? OR content LIKE ?)';
    countParams.push(`%${search}%`, `%${search}%`);
  }
  const countResult = await c.env.DB.prepare(countQuery).bind(...countParams).first<{ total: number }>();

  return c.json({
    articles: articles.results,
    pagination: {
      page,
      limit,
      total: countResult?.total || 0,
      total_pages: Math.ceil((countResult?.total || 0) / limit),
    }
  });
});

// 获取单篇文章
articleRoutes.get('/:id', async (c) => {
  const id = c.req.param('id');

  const article = await c.env.DB.prepare(`
    SELECT a.*, COALESCE(NULLIF(u.nickname,''), u.username) as author_name, u.avatar_url as author_avatar, c.name as category_name
    FROM articles a
    LEFT JOIN users u ON a.author_id = u.id
    LEFT JOIN categories c ON a.category_id = c.id
    WHERE a.id = ?
  `).bind(id).first();

  if (!article) return c.json({ error: '文章不存在' }, 404);

  // 增加阅读量（去重）
  const viewerKey = c.req.header('x-forwarded-for')?.split(',')[0]?.trim()
    || c.req.header('x-real-ip')
    || `anon:${(c.req.header('user-agent') || 'unknown').slice(0, 64)}`;
  const viewResult = await c.env.DB.prepare(
    'INSERT OR IGNORE INTO content_views (viewer_key, target_type, target_id) VALUES (?, ?, ?)'
  ).bind(viewerKey, 'article', id).run();
  if (viewResult.meta.changes > 0) {
    await c.env.DB.prepare(
      'UPDATE articles SET view_count = view_count + 1 WHERE id = ?'
    ).bind(id).run();
  }

  return c.json({ article });
});

// 创建文章（需要编辑/管理员权限）
articleRoutes.post('/', authMiddleware(), adminMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };
  const { title, content, summary, category_id, sort_order } = await c.req.json();

  if (!title || !content || !category_id) {
    return c.json({ error: '标题、内容和分类不能为空' }, 400);
  }

  const result = await c.env.DB.prepare(`
    INSERT INTO articles (title, content, summary, category_id, author_id, sort_order)
    VALUES (?, ?, ?, ?, ?, ?)
  `).bind(title, content, summary || '', category_id, user.id, sort_order || 0).run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/articles`, `${baseUrl}/api/home`, `${baseUrl}/api/stats`]));
  return c.json({ id: result.meta.last_row_id, message: '发布成功' }, 201);
});

// 更新文章
articleRoutes.put('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const { title, content, summary, category_id, sort_order, is_published } = await c.req.json();

  await c.env.DB.prepare(`
    UPDATE articles SET title = ?, content = ?, summary = ?, category_id = ?,
    sort_order = ?, is_published = ?, updated_at = datetime('now') WHERE id = ?
  `).bind(title, content, summary || '', category_id, sort_order || 0, is_published ?? 1, id).run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/articles`, `${baseUrl}/api/articles/${id}`, `${baseUrl}/api/home`, `${baseUrl}/api/stats`]));
  return c.json({ message: '更新成功' });
});

// 删除文章
articleRoutes.delete('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  await c.env.DB.prepare('DELETE FROM articles WHERE id = ?').bind(id).run();
  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/articles`, `${baseUrl}/api/articles/${id}`, `${baseUrl}/api/home`, `${baseUrl}/api/stats`]));
  return c.json({ message: '删除成功' });
});

// 点赞文章
articleRoutes.post('/:id/like', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user' as never) as { id: number };

  try {
    await c.env.DB.prepare(
      'INSERT INTO likes (user_id, target_type, target_id) VALUES (?, ?, ?)'
    ).bind(user.id, 'article', id).run();

    await c.env.DB.prepare(
      'UPDATE articles SET like_count = like_count + 1 WHERE id = ?'
    ).bind(id).run();

    return c.json({ message: '点赞成功', liked: true });
  } catch (e: any) {
    if (!String(e?.message || '').includes('UNIQUE constraint failed')) throw e;
    // UNIQUE 约束冲突 = 已点赞，执行取消
    await c.env.DB.prepare(
      'DELETE FROM likes WHERE user_id = ? AND target_type = ? AND target_id = ?'
    ).bind(user.id, 'article', id).run();

    await c.env.DB.prepare(
      'UPDATE articles SET like_count = MAX(0, like_count - 1) WHERE id = ?'
    ).bind(id).run();

    return c.json({ message: '已取消点赞', liked: false });
  }
});
