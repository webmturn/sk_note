import { Hono } from 'hono';
import type { Env } from '../index';
import { createToken, authMiddleware, adminMiddleware } from '../middleware/auth';

export const authRoutes = new Hono<{ Bindings: Env }>();

// 简单的密码哈希（Cloudflare Workers 兼容）
async function hashPassword(password: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(password);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return btoa(String.fromCharCode(...new Uint8Array(hash)));
}

// 注册
authRoutes.post('/register', async (c) => {
  try {
    const { username, email, password } = await c.req.json();

    if (!username || !email || !password) {
      return c.json({ error: '用户名、邮箱和密码不能为空' }, 400);
    }
    if (password.length < 6) {
      return c.json({ error: '密码至少6位' }, 400);
    }

    const existing = await c.env.DB.prepare(
      'SELECT id FROM users WHERE username = ? OR email = ?'
    ).bind(username, email).first();

    if (existing) {
      return c.json({ error: '用户名或邮箱已被注册' }, 409);
    }

    const passwordHash = await hashPassword(password);
    const result = await c.env.DB.prepare(
      'INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)'
    ).bind(username, email, passwordHash).run();

    const userId = result.meta.last_row_id;
    const token = await createToken(
      { id: userId as number, username, role: 'user' },
      c.env.JWT_SECRET
    );

    return c.json({
      token,
      user: { id: userId, username, email, role: 'user' }
    }, 201);
  } catch (e: any) {
    return c.json({ error: '注册失败: ' + e.message }, 500);
  }
});

// 登录
authRoutes.post('/login', async (c) => {
  try {
    const { username, password } = await c.req.json();

    if (!username || !password) {
      return c.json({ error: '用户名和密码不能为空' }, 400);
    }

    const user = await c.env.DB.prepare(
      'SELECT id, username, email, password_hash, role, avatar_url FROM users WHERE username = ? OR email = ?'
    ).bind(username, username).first<{
      id: number; username: string; email: string;
      password_hash: string; role: string; avatar_url: string;
    }>();

    if (!user) {
      return c.json({ error: '用户不存在' }, 404);
    }

    const passwordHash = await hashPassword(password);
    if (passwordHash !== user.password_hash) {
      return c.json({ error: '密码错误' }, 401);
    }

    const token = await createToken(
      { id: user.id, username: user.username, role: user.role },
      c.env.JWT_SECRET
    );

    return c.json({
      token,
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        role: user.role,
        avatar_url: user.avatar_url,
      }
    });
  } catch (e: any) {
    return c.json({ error: '登录失败: ' + e.message }, 500);
  }
});

// 获取当前用户信息
authRoutes.get('/me', authMiddleware(), async (c) => {
  const payload = c.get('user' as never) as { id: number };
  const user = await c.env.DB.prepare(
    'SELECT id, username, email, role, avatar_url, created_at FROM users WHERE id = ?'
  ).bind(payload.id).first();

  if (!user) return c.json({ error: '用户不存在' }, 404);
  return c.json({ user });
});

// 更新个人信息
authRoutes.put('/me', authMiddleware(), async (c) => {
  const payload = c.get('user' as never) as { id: number };
  const { avatar_url } = await c.req.json();

  await c.env.DB.prepare(
    'UPDATE users SET avatar_url = ?, updated_at = datetime("now") WHERE id = ?'
  ).bind(avatar_url || '', payload.id).run();

  return c.json({ message: '更新成功' });
});

// ============ 管理员：用户管理 ============

// 获取用户列表（管理员）
authRoutes.get('/users', authMiddleware(), adminMiddleware(), async (c) => {
  try {
    const page = parseInt(c.req.query('page') || '1');
    const limit = parseInt(c.req.query('limit') || '50');
    const search = c.req.query('search') || '';
    const offset = (page - 1) * limit;

    let countQuery = 'SELECT COUNT(*) as total FROM users';
    let dataQuery = 'SELECT id, username, email, role, avatar_url, created_at FROM users';
    const bindings: any[] = [];

    if (search) {
      const where = ' WHERE username LIKE ? OR email LIKE ?';
      countQuery += where;
      dataQuery += where;
      bindings.push(`%${search}%`, `%${search}%`);
    }

    dataQuery += ' ORDER BY id DESC LIMIT ? OFFSET ?';

    const countResult = await c.env.DB.prepare(countQuery)
      .bind(...bindings).first<{ total: number }>();
    const total = countResult?.total || 0;

    const users = await c.env.DB.prepare(dataQuery)
      .bind(...bindings, limit, offset).all();

    return c.json({
      users: users.results,
      pagination: {
        page, limit, total,
        total_pages: Math.ceil(total / limit)
      }
    });
  } catch (e: any) {
    return c.json({ error: '获取用户列表失败: ' + e.message }, 500);
  }
});

// 更新用户角色（管理员）
authRoutes.put('/users/:id/role', authMiddleware(), adminMiddleware(), async (c) => {
  try {
    const userId = parseInt(c.req.param('id'));
    const { role } = await c.req.json();
    const currentUser = c.get('user' as never) as { id: number };

    if (userId === currentUser.id) {
      return c.json({ error: '不能修改自己的角色' }, 400);
    }

    if (!['user', 'admin', 'editor'].includes(role)) {
      return c.json({ error: '无效的角色，可选: user, admin, editor' }, 400);
    }

    const target = await c.env.DB.prepare('SELECT id FROM users WHERE id = ?')
      .bind(userId).first();
    if (!target) return c.json({ error: '用户不存在' }, 404);

    await c.env.DB.prepare(
      'UPDATE users SET role = ?, updated_at = datetime("now") WHERE id = ?'
    ).bind(role, userId).run();

    return c.json({ message: '角色更新成功' });
  } catch (e: any) {
    return c.json({ error: '更新角色失败: ' + e.message }, 500);
  }
});

// 删除用户（管理员）
authRoutes.delete('/users/:id', authMiddleware(), adminMiddleware(), async (c) => {
  try {
    const userId = parseInt(c.req.param('id'));
    const currentUser = c.get('user' as never) as { id: number };

    if (userId === currentUser.id) {
      return c.json({ error: '不能删除自己的账号' }, 400);
    }

    const target = await c.env.DB.prepare('SELECT id, role FROM users WHERE id = ?')
      .bind(userId).first<{ id: number; role: string }>();
    if (!target) return c.json({ error: '用户不存在' }, 404);
    if (target.role === 'admin') {
      return c.json({ error: '不能删除其他管理员账号' }, 400);
    }

    await c.env.DB.batch([
      c.env.DB.prepare('DELETE FROM notifications WHERE user_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM comments WHERE author_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM discussions WHERE author_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM snippets WHERE author_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM bookmarks WHERE user_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM reading_history WHERE user_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM users WHERE id = ?').bind(userId),
    ]);

    return c.json({ message: '用户已删除' });
  } catch (e: any) {
    return c.json({ error: '删除用户失败: ' + e.message }, 500);
  }
});

// 重置用户密码（管理员）
authRoutes.put('/users/:id/password', authMiddleware(), adminMiddleware(), async (c) => {
  try {
    const userId = parseInt(c.req.param('id'));
    const { new_password } = await c.req.json();

    if (!new_password || new_password.length < 6) {
      return c.json({ error: '新密码至少6位' }, 400);
    }

    const target = await c.env.DB.prepare('SELECT id FROM users WHERE id = ?')
      .bind(userId).first();
    if (!target) return c.json({ error: '用户不存在' }, 404);

    const newHash = await hashPassword(new_password);
    await c.env.DB.prepare(
      'UPDATE users SET password_hash = ?, updated_at = datetime("now") WHERE id = ?'
    ).bind(newHash, userId).run();

    return c.json({ message: '密码重置成功' });
  } catch (e: any) {
    return c.json({ error: '重置密码失败: ' + e.message }, 500);
  }
});

// 修改密码
authRoutes.put('/password', authMiddleware(), async (c) => {
  const payload = c.get('user' as never) as { id: number };
  const { old_password, new_password } = await c.req.json();

  if (!old_password || !new_password) {
    return c.json({ error: '旧密码和新密码不能为空' }, 400);
  }
  if (new_password.length < 6) {
    return c.json({ error: '新密码至少6位' }, 400);
  }

  const user = await c.env.DB.prepare(
    'SELECT password_hash FROM users WHERE id = ?'
  ).bind(payload.id).first<{ password_hash: string }>();

  if (!user) return c.json({ error: '用户不存在' }, 404);

  const oldHash = await hashPassword(old_password);
  if (oldHash !== user.password_hash) {
    return c.json({ error: '旧密码错误' }, 401);
  }

  const newHash = await hashPassword(new_password);
  await c.env.DB.prepare(
    'UPDATE users SET password_hash = ?, updated_at = datetime("now") WHERE id = ?'
  ).bind(newHash, payload.id).run();

  return c.json({ message: '密码修改成功' });
});
