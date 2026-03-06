import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware, adminMiddleware } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';

export const snippetRoutes = new Hono<{ Bindings: Env }>();

// 获取代码片段列表（支持分页、分类、搜索）
snippetRoutes.get('/', edgeCache(120), async (c) => {
  const page = parseInt(c.req.query('page') || '1');
  const limit = parseInt(c.req.query('limit') || '20');
  const category = c.req.query('category');
  const search = c.req.query('search');
  const offset = (page - 1) * limit;

  let query = 'SELECT * FROM snippets WHERE is_approved = 1';
  const params: any[] = [];

  if (category) {
    query += ' AND category = ?';
    params.push(category);
  }
  if (search) {
    query += ' AND (title LIKE ? OR description LIKE ? OR tags LIKE ? OR code LIKE ?)';
    params.push(`%${search}%`, `%${search}%`, `%${search}%`, `%${search}%`);
  }

  query += ' ORDER BY created_at DESC LIMIT ? OFFSET ?';
  params.push(limit, offset);

  const snippets = await c.env.DB.prepare(query).bind(...params).all();

  let countQuery = 'SELECT COUNT(*) as total FROM snippets WHERE is_approved = 1';
  const countParams: any[] = [];
  if (category) {
    countQuery += ' AND category = ?';
    countParams.push(category);
  }
  if (search) {
    countQuery += ' AND (title LIKE ? OR description LIKE ? OR tags LIKE ? OR code LIKE ?)';
    countParams.push(`%${search}%`, `%${search}%`, `%${search}%`, `%${search}%`);
  }
  const countResult = await c.env.DB.prepare(countQuery).bind(...countParams).first<{ total: number }>();

  return c.json({
    snippets: snippets.results,
    pagination: {
      page,
      limit,
      total: countResult?.total || 0,
      total_pages: Math.ceil((countResult?.total || 0) / limit),
    }
  });
});

// 获取分类列表
snippetRoutes.get('/categories', edgeCache(300), async (c) => {
  const result = await c.env.DB.prepare(
    'SELECT category, COUNT(*) as count FROM snippets WHERE is_approved = 1 GROUP BY category ORDER BY count DESC'
  ).all();
  return c.json({ categories: result.results });
});

// 获取单个代码片段
snippetRoutes.get('/:id', async (c) => {
  const id = c.req.param('id');
  await c.env.DB.prepare('UPDATE snippets SET view_count = view_count + 1 WHERE id = ?').bind(id).run();
  const snippet = await c.env.DB.prepare('SELECT * FROM snippets WHERE id = ?').bind(id).first();
  if (!snippet) return c.json({ error: '未找到' }, 404);
  return c.json({ snippet });
});

// 创建代码片段（需要登录）
snippetRoutes.post('/', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number; username: string };
  const { title, description, code, language, category, tags } = await c.req.json();

  if (!title || !code) {
    return c.json({ error: '标题和代码不能为空' }, 400);
  }

  const result = await c.env.DB.prepare(
    `INSERT INTO snippets (title, description, code, language, category, tags, author_id, author_name)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    title,
    description || '',
    code,
    language || 'java',
    category || 'general',
    tags || '',
    user.id,
    user.username || ''
  ).run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/snippets`, `${baseUrl}/api/snippets/categories`, `${baseUrl}/api/stats`]));
  return c.json({ message: '创建成功', id: result.meta.last_row_id }, 201);
});

// 删除代码片段（作者或管理员）
snippetRoutes.delete('/:id', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user' as never) as { id: number; role: string };

  const snippet = await c.env.DB.prepare('SELECT author_id FROM snippets WHERE id = ?').bind(id).first<{ author_id: number }>();
  if (!snippet) return c.json({ error: '未找到' }, 404);
  if (snippet.author_id !== user.id && user.role !== 'admin') {
    return c.json({ error: '无权限' }, 403);
  }

  await c.env.DB.prepare('DELETE FROM snippets WHERE id = ?').bind(id).run();
  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/snippets`, `${baseUrl}/api/snippets/${id}`, `${baseUrl}/api/snippets/categories`, `${baseUrl}/api/stats`]));
  return c.json({ message: '已删除' });
});

// 点赞代码片段
snippetRoutes.post('/:id/like', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user' as never) as { id: number };

  const existing = await c.env.DB.prepare(
    'SELECT id FROM likes WHERE user_id = ? AND target_type = ? AND target_id = ?'
  ).bind(user.id, 'snippet', id).first();

  if (existing) {
    await c.env.DB.prepare('DELETE FROM likes WHERE user_id = ? AND target_type = ? AND target_id = ?')
      .bind(user.id, 'snippet', id).run();
    await c.env.DB.prepare('UPDATE snippets SET like_count = like_count - 1 WHERE id = ?').bind(id).run();
    return c.json({ message: '取消点赞', liked: false });
  } else {
    await c.env.DB.prepare('INSERT INTO likes (user_id, target_type, target_id) VALUES (?, ?, ?)')
      .bind(user.id, 'snippet', id).run();
    await c.env.DB.prepare('UPDATE snippets SET like_count = like_count + 1 WHERE id = ?').bind(id).run();
    return c.json({ message: '已点赞', liked: true });
  }
});
