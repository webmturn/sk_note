import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware, adminMiddleware } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';

export const discussionCategoryRoutes = new Hono<{ Bindings: Env }>();

const slugPattern = /^[a-z0-9_-]+$/;

function normalizeSlug(input: unknown): string {
  return String(input || '').trim().toLowerCase();
}

discussionCategoryRoutes.get('/', edgeCache(600), async (c) => {
  const categories = await c.env.DB.prepare(
    'SELECT * FROM discussion_categories ORDER BY sort_order ASC, id ASC'
  ).all();
  return c.json({ categories: categories.results });
});

discussionCategoryRoutes.get('/:id', edgeCache(600), async (c) => {
  const id = c.req.param('id');
  const category = await c.env.DB.prepare(
    'SELECT * FROM discussion_categories WHERE id = ?'
  ).bind(id).first();

  if (!category) return c.json({ error: '讨论分类不存在' }, 404);
  return c.json({ category });
});

discussionCategoryRoutes.post('/', authMiddleware(), adminMiddleware(), async (c) => {
  const { slug, name, description, icon, sort_order } = await c.req.json();
  const normalizedSlug = normalizeSlug(slug);

  if (!name) return c.json({ error: '分类名称不能为空' }, 400);
  if (!normalizedSlug || !slugPattern.test(normalizedSlug)) {
    return c.json({ error: 'Slug 只能包含小写字母、数字、下划线和中划线' }, 400);
  }

  try {
    const result = await c.env.DB.prepare(
      'INSERT INTO discussion_categories (slug, name, description, icon, sort_order) VALUES (?, ?, ?, ?, ?)'
    ).bind(normalizedSlug, String(name).trim(), description || '', icon || '', sort_order || 0).run();

    const baseUrl = new URL(c.req.url).origin;
    c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/discussion-categories`, `${baseUrl}/api/discussions`]));
    return c.json({ id: result.meta.last_row_id, message: '创建成功' }, 201);
  } catch {
    return c.json({ error: `Slug「${normalizedSlug}」已存在` }, 409);
  }
});

discussionCategoryRoutes.put('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const { slug, name, description, icon, sort_order } = await c.req.json();
  const normalizedSlug = normalizeSlug(slug);

  if (!name) return c.json({ error: '分类名称不能为空' }, 400);
  if (!normalizedSlug || !slugPattern.test(normalizedSlug)) {
    return c.json({ error: 'Slug 只能包含小写字母、数字、下划线和中划线' }, 400);
  }

  const existing = await c.env.DB.prepare(
    'SELECT slug FROM discussion_categories WHERE id = ?'
  ).bind(id).first<{ slug: string }>();

  if (!existing) return c.json({ error: '讨论分类不存在' }, 404);

  try {
    await c.env.DB.prepare(
      'UPDATE discussion_categories SET slug = ?, name = ?, description = ?, icon = ?, sort_order = ?, updated_at = datetime(\'now\') WHERE id = ?'
    ).bind(normalizedSlug, String(name).trim(), description || '', icon || '', sort_order || 0, id).run();

    const baseUrl = new URL(c.req.url).origin;
    c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/discussion-categories`, `${baseUrl}/api/discussions`]));
    return c.json({ message: '更新成功' });
  } catch {
    return c.json({ error: `Slug「${normalizedSlug}」已存在` }, 409);
  }
});

discussionCategoryRoutes.delete('/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const category = await c.env.DB.prepare(
    'SELECT slug, name FROM discussion_categories WHERE id = ?'
  ).bind(id).first<{ slug: string; name: string }>();

  if (!category) return c.json({ error: '讨论分类不存在' }, 404);

  const usage = await c.env.DB.prepare(
    'SELECT COUNT(*) as total FROM discussions WHERE category = ?'
  ).bind(category.slug).first<{ total: number }>();

  if ((usage?.total || 0) > 0) {
    return c.json({ error: `分类「${category.name}」正在被讨论使用，无法删除` }, 400);
  }

  await c.env.DB.prepare('DELETE FROM discussion_categories WHERE id = ?').bind(id).run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/discussion-categories`, `${baseUrl}/api/discussions`]));
  return c.json({ message: '删除成功' });
});
