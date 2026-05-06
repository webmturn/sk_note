import { Hono } from 'hono';
import type { AppEnv } from '../index';
import { authMiddleware, adminMiddleware, isOwnerOrAdmin, refreshCurrentUserRole } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';
import { rateLimit, userOrIpIdentifier } from '../middleware/rateLimit';
import { toggleLike } from '../likeUtils';
import { deleteNotificationsByRelatedTargets, getActorDisplayName, notifyActor } from './notifications';

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
shareRoutes.get('/:id', authMiddleware(false), async (c) => {
  const id = c.req.param('id');
  const share = await c.env.DB.prepare('SELECT * FROM shares WHERE id = ?').bind(id).first<any>();
  if (!share) return c.json({ error: '未找到' }, 404);

  // 未审核分享：仅作者或管理员可预览
  const isApproved = (share as any).is_approved === 1;
  if (!isApproved) {
    const user = c.get('user');
    if (!user) {
      return c.json({ error: '未找到' }, 404);
    }
    const role = await refreshCurrentUserRole(c);
    const isOwner = (share as any).author_id === user.id;
    if (role !== 'admin' && !isOwner) {
      return c.json({ error: '未找到' }, 404);
    }
    // 预览不计入浏览量，直接返回
    return c.json({ share });
  }

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
  const share = await c.env.DB.prepare(
    'SELECT id, title, author_id FROM shares WHERE id = ? AND is_approved = 1'
  ).bind(id).first<{ id: number; title: string; author_id: number }>();
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

    // 分享下载通知：基于 viewer_key 天然去重，避免同一用户/IP 重复通知
    const currentUser = c.get('user');
    const actorId = currentUser?.id ?? 0;
    const actorName = actorId > 0 ? await getActorDisplayName(c.env.DB, actorId) : '有人';
    await notifyActor(c.env.DB, {
      ownerId: share.author_id,
      actorId,
      type: 'system',
      title: `${actorName} 下载了你的分享`,
      content: (share.title || '').slice(0, 100),
      relatedType: 'share',
      relatedId: share.id,
    });
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
  await deleteNotificationsByRelatedTargets(c.env.DB, 'share', [Number.parseInt(id, 10)]);
  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/shares`, `${baseUrl}/api/shares/${id}`, `${baseUrl}/api/shares/categories`, `${baseUrl}/api/stats`]);
  return c.json({ message: '已删除' });
});

// 管理员：获取所有分享（含未批准），用于后台管理
shareRoutes.get('/manage/list', authMiddleware(), adminMiddleware(), async (c) => {
  try {
    const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
    const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '50') || 50));
    const status = c.req.query('status'); // 'all' | 'approved' | 'pending'
    const search = c.req.query('search') || '';
    const offset = (page - 1) * limit;

    let query = 'SELECT * FROM shares WHERE 1=1';
    const params: any[] = [];

    if (status === 'pending') {
      query += ' AND is_approved = 0';
    } else if (status === 'approved') {
      query += ' AND is_approved = 1';
    }

    if (search) {
      query += ' AND (title LIKE ? OR description LIKE ? OR author_name LIKE ?)';
      params.push(`%${search}%`, `%${search}%`, `%${search}%`);
    }

    query += ' ORDER BY is_approved ASC, created_at DESC LIMIT ? OFFSET ?';
    params.push(limit, offset);

    const shares = await c.env.DB.prepare(query).bind(...params).all();

    let countQuery = 'SELECT COUNT(*) as total FROM shares WHERE 1=1';
    const countParams: any[] = [];
    if (status === 'pending') {
      countQuery += ' AND is_approved = 0';
    } else if (status === 'approved') {
      countQuery += ' AND is_approved = 1';
    }
    if (search) {
      countQuery += ' AND (title LIKE ? OR description LIKE ? OR author_name LIKE ?)';
      countParams.push(`%${search}%`, `%${search}%`, `%${search}%`);
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
  } catch (e: any) {
    console.error('获取分享管理列表失败:', e);
    return c.json({ error: '获取列表失败' }, 500);
  }
});

// 管理员：审核分享（批准/取消批准）
shareRoutes.put('/:id/approve', authMiddleware(), adminMiddleware(), async (c) => {
  try {
    const id = c.req.param('id');
    const body = await c.req.json().catch(() => ({}));
    const approved = body.is_approved === undefined ? 1 : (body.is_approved ? 1 : 0);

    const existing = await c.env.DB.prepare(
      'SELECT id, author_id, title, is_approved FROM shares WHERE id = ?'
    ).bind(id).first<{ id: number; author_id: number; title: string; is_approved: number }>();
    if (!existing) return c.json({ error: '分享不存在' }, 404);

    await c.env.DB.prepare(
      `UPDATE shares SET is_approved = ?, updated_at = datetime('now') WHERE id = ?`
    ).bind(approved, id).run();

    // 0 -> 1 时通知作者审核通过；1 -> 0 不通知（admin 主动隐藏，作者刷新即可看到）
    const wasUnapproved = existing.is_approved === 0;
    const adminUser = c.get('user')!;
    if (approved === 1 && wasUnapproved && existing.author_id !== adminUser.id) {
      await notifyActor(c.env.DB, {
        ownerId: existing.author_id,
        actorId: adminUser.id,
        type: 'system',
        title: '你的分享已通过审核',
        content: (existing.title || '').slice(0, 100),
        relatedType: 'share',
        relatedId: existing.id,
      });
    }

    const baseUrl = new URL(c.req.url).origin;
    await purgeCache([`${baseUrl}/api/shares`, `${baseUrl}/api/shares/${id}`, `${baseUrl}/api/shares/categories`, `${baseUrl}/api/home`, `${baseUrl}/api/stats`]);
    return c.json({ message: approved ? '已批准' : '已取消批准', is_approved: approved });
  } catch (e: any) {
    console.error('审核分享失败:', e);
    return c.json({ error: '审核失败' }, 500);
  }
});

// 点赞分享
shareRoutes.post('/:id/like', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user')!;

  const share = await c.env.DB.prepare(
    'SELECT id, title, author_id FROM shares WHERE id = ?'
  ).bind(id).first<{ id: number; title: string; author_id: number }>();
  if (!share) return c.json({ error: '分享不存在' }, 404);

  const result = await toggleLike(c, {
    userId: user.id,
    targetId: id,
    targetType: 'share',
    countTable: 'shares',
    likeSuccessMessage: '已点赞',
    unlikeSuccessMessage: '取消点赞',
  });

  if (result.liked) {
    const actorName = await getActorDisplayName(c.env.DB, user.id);
    await notifyActor(c.env.DB, {
      ownerId: share.author_id,
      actorId: user.id,
      type: 'like',
      title: `${actorName} 点赞了你的分享`,
      content: (share.title || '').slice(0, 100),
      relatedType: 'share',
      relatedId: share.id,
    });
  }

  return c.json(result);
});
