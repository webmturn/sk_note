import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware, adminMiddleware } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';

export const categoryRoutes = new Hono<{ Bindings: Env }>();

// 获取所有分类
categoryRoutes.get('/', edgeCache(600), async (c) => {
  const categories = await c.env.DB.prepare(
    'SELECT * FROM categories ORDER BY sort_order ASC, id ASC'
  ).all();
  return c.json({ categories: categories.results });
});

// 获取单个分类
categoryRoutes.get('/:id', edgeCache(600), async (c) => {
  const id = c.req.param('id');
  const category = await c.env.DB.prepare(
    'SELECT * FROM categories WHERE id = ?'
  ).bind(id).first();

  if (!category) return c.json({ error: '分类不存在' }, 404);
  return c.json({ category });
});

// 创建分类（需要管理员权限）
categoryRoutes.post('/', authMiddleware(), adminMiddleware(), async (c) => {
  const { name, description, icon, sort_order, parent_id } = await c.req.json();

  if (!name) return c.json({ error: '分类名称不能为空' }, 400);

  const result = await c.env.DB.prepare(
    'INSERT INTO categories (name, description, icon, sort_order, parent_id) VALUES (?, ?, ?, ?, ?)'
  ).bind(name, description || '', icon || '', sort_order || 0, parent_id || null).run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/categories`, `${baseUrl}/api/home`]));
  return c.json({ id: result.meta.last_row_id, message: '创建成功' }, 201);
});

// 更新分类（需要管理员权限）
categoryRoutes.put('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const { name, description, icon, sort_order, parent_id } = await c.req.json();

  await c.env.DB.prepare(
    'UPDATE categories SET name = ?, description = ?, icon = ?, sort_order = ?, parent_id = ? WHERE id = ?'
  ).bind(name, description || '', icon || '', sort_order || 0, parent_id || null, id).run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/categories`, `${baseUrl}/api/categories/${id}`, `${baseUrl}/api/home`]));
  return c.json({ message: '更新成功' });
});

// 删除分类（需要管理员权限）
categoryRoutes.delete('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  await c.env.DB.prepare('DELETE FROM categories WHERE id = ?').bind(id).run();
  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/categories`, `${baseUrl}/api/categories/${id}`, `${baseUrl}/api/home`]));
  return c.json({ message: '删除成功' });
});
