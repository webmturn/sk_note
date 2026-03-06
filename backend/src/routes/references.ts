import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware, adminMiddleware } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';

export const referenceRoutes = new Hono<{ Bindings: Env }>();

// 获取参考列表（支持类型/分类/搜索筛选）
referenceRoutes.get('/', edgeCache(600), async (c) => {
  const page = parseInt(c.req.query('page') || '1');
  const limit = parseInt(c.req.query('limit') || '50');
  const type = c.req.query('type');
  const category = c.req.query('category');
  const search = c.req.query('search');
  const offset = (page - 1) * limit;

  let query = 'SELECT * FROM references_doc WHERE 1=1';
  const params: any[] = [];

  if (type) {
    query += ' AND type = ?';
    params.push(type);
  }
  if (category) {
    query += ' AND category = ?';
    params.push(category);
  }
  if (search) {
    query += ' AND (name LIKE ? OR description LIKE ? OR category LIKE ?)';
    params.push(`%${search}%`, `%${search}%`, `%${search}%`);
  }

  query += ' ORDER BY type ASC, category ASC, name ASC LIMIT ? OFFSET ?';
  params.push(limit, offset);

  const results = await c.env.DB.prepare(query).bind(...params).all();

  // 总数
  let countQuery = 'SELECT COUNT(*) as total FROM references_doc WHERE 1=1';
  const countParams: any[] = [];
  if (type) { countQuery += ' AND type = ?'; countParams.push(type); }
  if (category) { countQuery += ' AND category = ?'; countParams.push(category); }
  if (search) {
    countQuery += ' AND (name LIKE ? OR description LIKE ? OR category LIKE ?)';
    countParams.push(`%${search}%`, `%${search}%`, `%${search}%`);
  }
  const countResult = await c.env.DB.prepare(countQuery).bind(...countParams).first<{ total: number }>();

  return c.json({
    references: results.results,
    pagination: {
      page,
      limit,
      total: countResult?.total || 0,
      total_pages: Math.ceil((countResult?.total || 0) / limit),
    }
  });
});

// 获取单个参考详情
referenceRoutes.get('/:id', edgeCache(600), async (c) => {
  const id = c.req.param('id');
  const ref = await c.env.DB.prepare('SELECT * FROM references_doc WHERE id = ?').bind(id).first();
  if (!ref) return c.json({ error: '参考条目不存在' }, 404);
  return c.json({ reference: ref });
});

// 创建参考条目（管理员）
referenceRoutes.post('/', authMiddleware(), adminMiddleware(), async (c) => {
  const { name, category, type, description, usage, parameters, example, icon, related_ids } = await c.req.json();
  if (!name || !type) return c.json({ error: '名称和类型不能为空' }, 400);

  const result = await c.env.DB.prepare(`
    INSERT INTO references_doc (name, category, type, description, usage, parameters, example, icon, related_ids)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(name, category || '', type, description || '', usage || '', parameters || '', example || '', icon || '', related_ids || '').run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/references`]));
  return c.json({ id: result.meta.last_row_id, message: '创建成功' }, 201);
});

// 更新参考条目（管理员）
referenceRoutes.put('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const { name, category, type, description, usage, parameters, example, icon, related_ids } = await c.req.json();

  await c.env.DB.prepare(`
    UPDATE references_doc SET name = ?, category = ?, type = ?, description = ?,
    usage = ?, parameters = ?, example = ?, icon = ?, related_ids = ?, updated_at = datetime('now')
    WHERE id = ?
  `).bind(name, category || '', type, description || '', usage || '', parameters || '', example || '', icon || '', related_ids || '', id).run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/references`, `${baseUrl}/api/references/${id}`]));
  return c.json({ message: '更新成功' });
});

// 删除参考条目（管理员）
referenceRoutes.delete('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  await c.env.DB.prepare('DELETE FROM references_doc WHERE id = ?').bind(id).run();
  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/references`, `${baseUrl}/api/references/${id}`]));
  return c.json({ message: '删除成功' });
});
