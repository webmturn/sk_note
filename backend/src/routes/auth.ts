import { Hono } from 'hono';
import type { Env } from '../index';
import { createToken, authMiddleware, adminMiddleware } from '../middleware/auth';

export const authRoutes = new Hono<{ Bindings: Env }>();

// 密码哈希（加盐 SHA-256，Cloudflare Workers 兼容）
function generateSalt(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return [...bytes].map(b => b.toString(16).padStart(2, '0')).join('');
}

async function hashPassword(password: string, salt?: string): Promise<string> {
  const s = salt || generateSalt();
  const encoder = new TextEncoder();
  const data = encoder.encode(s + password);
  const hash = await crypto.subtle.digest('SHA-256', data);
  const hashStr = btoa(String.fromCharCode(...new Uint8Array(hash)));
  return `${s}:${hashStr}`;
}

async function verifyPassword(password: string, storedHash: string): Promise<boolean> {
  if (storedHash.includes(':')) {
    // 新格式: salt:hash
    const [salt] = storedHash.split(':');
    const computed = await hashPassword(password, salt);
    return computed === storedHash;
  }
  // 旧格式: 无盐 SHA-256（向后兼容）
  const encoder = new TextEncoder();
  const data = encoder.encode(password);
  const hash = await crypto.subtle.digest('SHA-256', data);
  const legacyHash = btoa(String.fromCharCode(...new Uint8Array(hash)));
  return legacyHash === storedHash;
}

// 注册
authRoutes.post('/register', async (c) => {
  try {
    const { username, email, password, nickname } = await c.req.json();

    if (!username || !email || !password) {
      return c.json({ error: '账号、邮箱和密码不能为空' }, 400);
    }
    if (password.length < 6) {
      return c.json({ error: '密码至少6位' }, 400);
    }

    const existing = await c.env.DB.prepare(
      'SELECT id FROM users WHERE username = ? OR email = ?'
    ).bind(username, email).first();

    if (existing) {
      return c.json({ error: '账号或邮箱已被注册' }, 409);
    }

    const displayName = (nickname || '').trim() || username;
    const passwordHash = await hashPassword(password);
    const result = await c.env.DB.prepare(
      'INSERT INTO users (username, email, password_hash, nickname) VALUES (?, ?, ?, ?)'
    ).bind(username, email, passwordHash, displayName).run();

    const userId = result.meta.last_row_id;
    const token = await createToken(
      { id: userId as number, username, role: 'user' },
      c.env.JWT_SECRET
    );

    return c.json({
      token,
      user: { id: userId, username, nickname: displayName, email, role: 'user' }
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
      'SELECT id, username, nickname, email, password_hash, role, avatar_url, bio FROM users WHERE username = ? OR email = ?'
    ).bind(username, username).first<{
      id: number; username: string; nickname: string; email: string;
      password_hash: string; role: string; avatar_url: string; bio: string;
    }>();

    if (!user) {
      return c.json({ error: '用户不存在' }, 404);
    }

    const passwordMatch = await verifyPassword(password, user.password_hash);
    if (!passwordMatch) {
      return c.json({ error: '密码错误' }, 401);
    }

    // 自动升级旧格式（无盐）为新格式（加盐）
    if (!user.password_hash.includes(':')) {
      const upgradedHash = await hashPassword(password);
      await c.env.DB.prepare(
        'UPDATE users SET password_hash = ? WHERE id = ?'
      ).bind(upgradedHash, user.id).run();
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
        nickname: user.nickname || user.username,
        email: user.email,
        role: user.role,
        avatar_url: user.avatar_url,
        bio: user.bio || '',
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
    'SELECT id, username, nickname, email, role, avatar_url, bio, created_at FROM users WHERE id = ?'
  ).bind(payload.id).first();

  if (!user) return c.json({ error: '用户不存在' }, 404);
  return c.json({ user });
});

// 更新个人信息
authRoutes.put('/me', authMiddleware(), async (c) => {
  const payload = c.get('user' as never) as { id: number };
  const body = await c.req.json();

  const fields: string[] = [];
  const values: any[] = [];

  if (body.avatar_url !== undefined) {
    fields.push('avatar_url = ?');
    values.push(body.avatar_url || '');
  }
  if (body.bio !== undefined) {
    fields.push('bio = ?');
    values.push(body.bio || '');
  }
  if (body.nickname !== undefined) {
    const newNickname = (body.nickname || '').trim();
    if (newNickname.length < 1) {
      return c.json({ error: '昵称不能为空' }, 400);
    }
    if (newNickname.length > 20) {
      return c.json({ error: '昵称最长20个字符' }, 400);
    }
    fields.push('nickname = ?');
    values.push(newNickname);
  }
  if (body.username !== undefined) {
    const newUsername = (body.username || '').trim();
    if (newUsername.length < 2) {
      return c.json({ error: '用户名至少2个字符' }, 400);
    }
    const existing = await c.env.DB.prepare(
      'SELECT id FROM users WHERE username = ? AND id != ?'
    ).bind(newUsername, payload.id).first();
    if (existing) {
      return c.json({ error: '用户名已被占用' }, 409);
    }
    fields.push('username = ?');
    values.push(newUsername);
  }

  if (fields.length === 0) {
    return c.json({ error: '没有要更新的字段' }, 400);
  }

  fields.push("updated_at = datetime('now')");
  values.push(payload.id);

  await c.env.DB.prepare(
    `UPDATE users SET ${fields.join(', ')} WHERE id = ?`
  ).bind(...values).run();

  return c.json({ message: '更新成功' });
});

// ============ 管理员：用户管理 ============

// 获取用户列表（管理员）
authRoutes.get('/users', authMiddleware(), adminMiddleware(), async (c) => {
  try {
    const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
    const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '50') || 50));
    const search = c.req.query('search') || '';
    const offset = (page - 1) * limit;

    let countQuery = 'SELECT COUNT(*) as total FROM users';
    let dataQuery = 'SELECT id, username, nickname, email, role, avatar_url, bio, created_at FROM users';
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
    if (isNaN(userId) || userId <= 0) return c.json({ error: '无效的用户ID' }, 400);
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
    if (isNaN(userId) || userId <= 0) return c.json({ error: '无效的用户ID' }, 400);
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
      c.env.DB.prepare('DELETE FROM likes WHERE user_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM comments WHERE author_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM discussions WHERE author_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM snippets WHERE author_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM shares WHERE author_id = ?').bind(userId),
      c.env.DB.prepare('DELETE FROM follows WHERE follower_id = ? OR following_id = ?').bind(userId, userId),
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
    if (isNaN(userId) || userId <= 0) return c.json({ error: '无效的用户ID' }, 400);
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

  const oldMatch = await verifyPassword(old_password, user.password_hash);
  if (!oldMatch) {
    return c.json({ error: '旧密码错误' }, 401);
  }

  const newHash = await hashPassword(new_password);
  await c.env.DB.prepare(
    'UPDATE users SET password_hash = ?, updated_at = datetime("now") WHERE id = ?'
  ).bind(newHash, payload.id).run();

  return c.json({ message: '密码修改成功' });
});
