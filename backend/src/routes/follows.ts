import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware } from '../middleware/auth';

export const followRoutes = new Hono<{ Bindings: Env }>();

function parsePositiveInt(value: string): number | null {
  const num = Number.parseInt(value, 10);
  if (!Number.isFinite(num) || Number.isNaN(num) || num <= 0) return null;
  return num;
}

// 关注/取消关注
followRoutes.post('/:userId', authMiddleware(), async (c) => {
  try {
    const currentUser = c.get('user' as never) as { id: number };
    const targetId = parsePositiveInt(c.req.param('userId'));
    if (!targetId) return c.json({ error: '无效的用户ID' }, 400);

    if (currentUser.id === targetId) {
      return c.json({ error: '不能关注自己' }, 400);
    }

    const target = await c.env.DB.prepare('SELECT id FROM users WHERE id = ?')
      .bind(targetId).first();
    if (!target) return c.json({ error: '用户不存在' }, 404);

    const existing = await c.env.DB.prepare(
      'SELECT id FROM follows WHERE follower_id = ? AND following_id = ?'
    ).bind(currentUser.id, targetId).first();

    if (existing) {
      await c.env.DB.prepare(
        'DELETE FROM follows WHERE follower_id = ? AND following_id = ?'
      ).bind(currentUser.id, targetId).run();
      return c.json({ message: '已取消关注', following: false });
    } else {
      await c.env.DB.prepare(
        'INSERT INTO follows (follower_id, following_id) VALUES (?, ?)'
      ).bind(currentUser.id, targetId).run();
      return c.json({ message: '已关注', following: true });
    }
  } catch (e: any) {
    return c.json({ error: '操作失败: ' + e.message }, 500);
  }
});

// 检查是否已关注
followRoutes.get('/check/:userId', authMiddleware(), async (c) => {
  const currentUser = c.get('user' as never) as { id: number };
  const targetId = parsePositiveInt(c.req.param('userId'));
  if (!targetId) return c.json({ error: '无效的用户ID' }, 400);

  const existing = await c.env.DB.prepare(
    'SELECT id FROM follows WHERE follower_id = ? AND following_id = ?'
  ).bind(currentUser.id, targetId).first();

  return c.json({ following: !!existing });
});

// 获取某用户的关注列表
followRoutes.get('/:userId/following', async (c) => {
  try {
    const userId = parsePositiveInt(c.req.param('userId'));
    if (!userId) return c.json({ error: '无效的用户ID' }, 400);
    const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
    const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '20') || 20));
    const offset = (page - 1) * limit;

    const countResult = await c.env.DB.prepare(
      'SELECT COUNT(*) as total FROM follows WHERE follower_id = ?'
    ).bind(userId).first<{ total: number }>();
    const total = countResult?.total || 0;

    const results = await c.env.DB.prepare(
      `SELECT u.id, u.username, COALESCE(NULLIF(u.nickname,''), u.username) as nickname, u.avatar_url, u.role, f.created_at as followed_at
       FROM follows f JOIN users u ON f.following_id = u.id
       WHERE f.follower_id = ? ORDER BY f.created_at DESC LIMIT ? OFFSET ?`
    ).bind(userId, limit, offset).all();

    return c.json({
      users: results.results,
      pagination: { page, limit, total, total_pages: Math.ceil(total / limit) }
    });
  } catch (e: any) {
    return c.json({ error: '获取关注列表失败: ' + e.message }, 500);
  }
});

// 获取某用户的粉丝列表
followRoutes.get('/:userId/followers', async (c) => {
  try {
    const userId = parsePositiveInt(c.req.param('userId'));
    if (!userId) return c.json({ error: '无效的用户ID' }, 400);
    const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
    const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '20') || 20));
    const offset = (page - 1) * limit;

    const countResult = await c.env.DB.prepare(
      'SELECT COUNT(*) as total FROM follows WHERE following_id = ?'
    ).bind(userId).first<{ total: number }>();
    const total = countResult?.total || 0;

    const results = await c.env.DB.prepare(
      `SELECT u.id, u.username, COALESCE(NULLIF(u.nickname,''), u.username) as nickname, u.avatar_url, u.role, f.created_at as followed_at
       FROM follows f JOIN users u ON f.follower_id = u.id
       WHERE f.following_id = ? ORDER BY f.created_at DESC LIMIT ? OFFSET ?`
    ).bind(userId, limit, offset).all();

    return c.json({
      users: results.results,
      pagination: { page, limit, total, total_pages: Math.ceil(total / limit) }
    });
  } catch (e: any) {
    return c.json({ error: '获取粉丝列表失败: ' + e.message }, 500);
  }
});

// 获取用户公开资料 (含关注/粉丝数和作品统计)
followRoutes.get('/profile/:userId', async (c) => {
  try {
    const userId = parsePositiveInt(c.req.param('userId'));
    if (!userId) return c.json({ error: '无效的用户ID' }, 400);

    const user = await c.env.DB.prepare(
      'SELECT id, username, nickname, avatar_url, bio, role, created_at FROM users WHERE id = ?'
    ).bind(userId).first();
    if (!user) return c.json({ error: '用户不存在' }, 404);

    const [followingResult, followersResult, discussionResult, snippetResult, shareResult] = await c.env.DB.batch([
      c.env.DB.prepare('SELECT COUNT(*) as count FROM follows WHERE follower_id = ?').bind(userId),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM follows WHERE following_id = ?').bind(userId),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM discussions WHERE author_id = ?').bind(userId),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM snippets WHERE author_id = ?').bind(userId),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM shares WHERE author_id = ?').bind(userId),
    ]);

    return c.json({
      user,
      stats: {
        following: (followingResult.results[0] as any)?.count || 0,
        followers: (followersResult.results[0] as any)?.count || 0,
        discussions: (discussionResult.results[0] as any)?.count || 0,
        snippets: (snippetResult.results[0] as any)?.count || 0,
        shares: (shareResult.results[0] as any)?.count || 0,
      }
    });
  } catch (e: any) {
    return c.json({ error: '获取资料失败: ' + e.message }, 500);
  }
});

// 获取用户的讨论列表
followRoutes.get('/profile/:userId/discussions', async (c) => {
  try {
    const userId = parsePositiveInt(c.req.param('userId'));
    if (!userId) return c.json({ error: '无效的用户ID' }, 400);
    const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
    const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '20') || 20));
    const offset = (page - 1) * limit;

    const countResult = await c.env.DB.prepare(
      'SELECT COUNT(*) as total FROM discussions WHERE author_id = ?'
    ).bind(userId).first<{ total: number }>();
    const total = countResult?.total || 0;

    const results = await c.env.DB.prepare(
      `SELECT d.*, COALESCE(NULLIF(u.nickname,''), u.username) as author_name
       FROM discussions d JOIN users u ON d.author_id = u.id
       WHERE d.author_id = ? ORDER BY d.created_at DESC LIMIT ? OFFSET ?`
    ).bind(userId, limit, offset).all();

    return c.json({
      discussions: results.results,
      pagination: { page, limit, total, total_pages: Math.ceil(total / limit) }
    });
  } catch (e: any) {
    return c.json({ error: '获取讨论列表失败: ' + e.message }, 500);
  }
});

// 获取用户的代码片段列表
followRoutes.get('/profile/:userId/snippets', async (c) => {
  try {
    const userId = parsePositiveInt(c.req.param('userId'));
    if (!userId) return c.json({ error: '无效的用户ID' }, 400);
    const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
    const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '20') || 20));
    const offset = (page - 1) * limit;

    const countResult = await c.env.DB.prepare(
      'SELECT COUNT(*) as total FROM snippets WHERE author_id = ?'
    ).bind(userId).first<{ total: number }>();
    const total = countResult?.total || 0;

    const results = await c.env.DB.prepare(
      'SELECT * FROM snippets WHERE author_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?'
    ).bind(userId, limit, offset).all();

    return c.json({
      snippets: results.results,
      pagination: { page, limit, total, total_pages: Math.ceil(total / limit) }
    });
  } catch (e: any) {
    return c.json({ error: '获取片段列表失败: ' + e.message }, 500);
  }
});

// 获取用户的分享列表
followRoutes.get('/profile/:userId/shares', async (c) => {
  try {
    const userId = parsePositiveInt(c.req.param('userId'));
    if (!userId) return c.json({ error: '无效的用户ID' }, 400);
    const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
    const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '20') || 20));
    const offset = (page - 1) * limit;

    const countResult = await c.env.DB.prepare(
      'SELECT COUNT(*) as total FROM shares WHERE author_id = ?'
    ).bind(userId).first<{ total: number }>();
    const total = countResult?.total || 0;

    const results = await c.env.DB.prepare(
      'SELECT * FROM shares WHERE author_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?'
    ).bind(userId, limit, offset).all();

    return c.json({
      shares: results.results,
      pagination: { page, limit, total, total_pages: Math.ceil(total / limit) }
    });
  } catch (e: any) {
    return c.json({ error: '获取分享列表失败: ' + e.message }, 500);
  }
});
