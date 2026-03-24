import { Hono } from 'hono';
import type { AppEnv } from '../index';
import { authMiddleware, adminMiddleware } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';

export const categoryRoutes = new Hono<AppEnv>();

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

  let parentIdForInsert: number | null = null;
  if (parent_id !== undefined && parent_id !== null && String(parent_id).trim() !== '') {
    const parentIdNum = Number.parseInt(String(parent_id), 10);
    if (!Number.isInteger(parentIdNum) || parentIdNum <= 0) {
      return c.json({ error: '无效的父分类ID' }, 400);
    }

    const parent = await c.env.DB.prepare(
      'SELECT id FROM categories WHERE id = ?'
    ).bind(parentIdNum).first();
    if (!parent) {
      return c.json({ error: '父分类不存在' }, 404);
    }

    parentIdForInsert = parentIdNum;
  }

  const result = await c.env.DB.prepare(
    'INSERT INTO categories (name, description, icon, sort_order, parent_id) VALUES (?, ?, ?, ?, ?)'
  ).bind(name, description || '', icon || '', sort_order || 0, parentIdForInsert).run();

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/categories`, `${baseUrl}/api/home`]);
  return c.json({ id: result.meta.last_row_id, message: '创建成功' }, 201);
});

// 更新分类（需要管理员权限）
categoryRoutes.put('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const { name, description, icon, sort_order, parent_id } = await c.req.json();

  if (!name) return c.json({ error: '分类名称不能为空' }, 400);

  const existing = await c.env.DB.prepare(
    'SELECT id FROM categories WHERE id = ?'
  ).bind(id).first();
  if (!existing) {
    return c.json({ error: '分类不存在' }, 404);
  }

  let parentIdForUpdate: number | null = null;
  if (parent_id !== undefined && parent_id !== null && String(parent_id).trim() !== '') {
    const parentIdNum = Number.parseInt(String(parent_id), 10);
    const idNum = Number.parseInt(String(id), 10);
    if (!Number.isInteger(parentIdNum) || parentIdNum <= 0) {
      return c.json({ error: '无效的父分类ID' }, 400);
    }
    if (Number.isInteger(idNum) && parentIdNum === idNum) {
      return c.json({ error: '父分类不能是自己' }, 400);
    }

    const parent = await c.env.DB.prepare(
      'SELECT id FROM categories WHERE id = ?'
    ).bind(parentIdNum).first();
    if (!parent) {
      return c.json({ error: '父分类不存在' }, 404);
    }

    parentIdForUpdate = parentIdNum;
  }

  await c.env.DB.prepare(
    'UPDATE categories SET name = ?, description = ?, icon = ?, sort_order = ?, parent_id = ? WHERE id = ?'
  ).bind(name, description || '', icon || '', sort_order || 0, parentIdForUpdate, id).run();

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/categories`, `${baseUrl}/api/categories/${id}`, `${baseUrl}/api/home`]);
  return c.json({ message: '更新成功' });
});

// 删除分类（需要管理员权限）
categoryRoutes.delete('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');

  const existing = await c.env.DB.prepare(
    'SELECT id FROM categories WHERE id = ?'
  ).bind(id).first();
  if (!existing) {
    return c.json({ error: '分类不存在' }, 404);
  }

  const articleCount = await c.env.DB.prepare(
    'SELECT COUNT(*) as count FROM articles WHERE category_id = ?'
  ).bind(id).first<{ count: number }>();

  if (articleCount && articleCount.count > 0) {
    return c.json({ error: `该分类下还有 ${articleCount.count} 篇文章，请先移动或删除这些文章` }, 400);
  }

  const result = await c.env.DB.prepare('DELETE FROM categories WHERE id = ?').bind(id).run();
  if (result.meta.changes === 0) {
    return c.json({ error: '分类不存在' }, 404);
  }
  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/categories`, `${baseUrl}/api/categories/${id}`, `${baseUrl}/api/home`]);
  return c.json({ message: '删除成功' });
});
