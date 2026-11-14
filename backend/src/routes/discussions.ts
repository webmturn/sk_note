import { Hono } from 'hono';
import type { AppEnv } from '../index';
import { adminMiddleware, authMiddleware, isOwnerOrAdmin, isOwnerOrEditorOrAdmin, refreshCurrentUserRole } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';
import { rateLimit, userOrIpIdentifier } from '../middleware/rateLimit';
import { buildViewerKey } from '../clientIp';
import { escapeLike } from '../sqlUtils';
import { toggleLike } from '../likeUtils';
import { createNotification, deleteActorNotification, deleteNotificationsByRelatedTargets, getActorDisplayName, notifyActor } from './notifications';

export const discussionRoutes = new Hono<AppEnv>();

/**
 * 标签规范化。客户端把标签当自由文本输入（与片段页的「标签（用逗号分隔，可选）」同一套
 * 输入方式），所以入库前统一收拾一遍，避免脏数据直接变成详情页上的 chips：
 * 按逗号/顿号/空白切分、去空、去重（忽略大小写）、单个最长 20 字符、最多 5 个。
 * 存储仍是逗号分隔的纯文本列，与 snippets.tags 一致（不做独立的标签表）。
 */
function normalizeTags(raw: unknown): string {
  if (typeof raw !== 'string') return '';
  const seen = new Set<string>();
  const out: string[] = [];
  for (const part of raw.split(/[,，、\s]+/)) {
    const tag = part.trim().replace(/^#+/, '');
    if (!tag) continue;
    const clipped = tag.slice(0, 20);
    const key = clipped.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(clipped);
    if (out.length >= 5) break;
  }
  return out.join(',');
}

// 获取讨论列表
discussionRoutes.get('/', edgeCache(120), async (c) => {
  const page = Math.max(1, parseInt(c.req.query('page') || '1') || 1);
  const limit = Math.min(100, Math.max(1, parseInt(c.req.query('limit') || '20') || 20));
  const category = c.req.query('category');
  const articleId = c.req.query('article_id');
  const search = (c.req.query('search') || '').slice(0, 100);
  // 排序。默认 latest（= 现状：按最后活动时间），向后兼容。
  // 公式刻意做得一眼能看懂，不做时间衰减：hot = 阅读数、replies = 回复数。
  // （Discourse 的 Top 带时间窗、Reddit 的 hot 带时间衰减，我们这体量不值得引入
  //  一个说不清的评分。）三个分支都在白名单内，不拼接用户输入。
  const sortParam = (c.req.query('sort') || 'latest').toLowerCase();
  const sort = ['latest', 'hot', 'replies'].includes(sortParam) ? sortParam : 'latest';
  const offset = (page - 1) * limit;

  let query = `
    SELECT d.id, d.title, d.content, d.tags, d.author_id, d.article_id, d.category,
    COALESCE(dc.name, d.category) as category_name, COALESCE(dc.icon, '') as category_icon,
    d.is_pinned, d.is_closed, d.view_count, d.reply_count, d.created_at, d.updated_at,
    COALESCE(NULLIF(u.nickname,''), u.username) as author_name, u.avatar_url as author_avatar
    FROM discussions d
    LEFT JOIN users u ON d.author_id = u.id
    LEFT JOIN discussion_categories dc ON d.category = dc.slug
    WHERE 1=1
  `;
  const params: any[] = [];

  if (category) {
    query += ' AND d.category = ?';
    params.push(category);
  }
  if (articleId) {
    query += ' AND d.article_id = ?';
    params.push(articleId);
  }
  if (search) {
    query += " AND (d.title LIKE ? ESCAPE '\\' OR d.content LIKE ? ESCAPE '\\' OR d.tags LIKE ? ESCAPE '\\')";
    params.push(`%${escapeLike(search)}%`, `%${escapeLike(search)}%`, `%${escapeLike(search)}%`);
  }

  const orderBy = sort === 'hot'
    ? 'd.view_count DESC, d.updated_at DESC'
    : sort === 'replies'
      ? 'd.reply_count DESC, d.updated_at DESC'
      : 'd.updated_at DESC';
  query += ` ORDER BY d.is_pinned DESC, ${orderBy} LIMIT ? OFFSET ?`;
  params.push(limit, offset);

  const discussions = await c.env.DB.prepare(query).bind(...params).all();

  let countQuery = 'SELECT COUNT(*) as total FROM discussions WHERE 1=1';
  const countParams: any[] = [];
  if (category) { countQuery += ' AND category = ?'; countParams.push(category); }
  if (articleId) { countQuery += ' AND article_id = ?'; countParams.push(articleId); }
  if (search) {
    countQuery += " AND (title LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\' OR tags LIKE ? ESCAPE '\\')";
    countParams.push(`%${escapeLike(search)}%`, `%${escapeLike(search)}%`, `%${escapeLike(search)}%`);
  }
  const countResult = await c.env.DB.prepare(countQuery).bind(...countParams).first<{ total: number }>();

  return c.json({
    discussions: discussions.results,
    pagination: {
      page, limit,
      total: countResult?.total || 0,
      total_pages: Math.ceil((countResult?.total || 0) / limit),
    }
  });
});

// 我的未读讨论（需要登录）
//
// 为什么单独开一个接口、而不是把 new_reply_count 塞进上面的列表响应：
// 列表路由挂着 edgeCache(120)，缓存键就是 URL —— 一旦响应里带上「按用户不同」的字段，
// 先访问的那个人的未读状态会被缓存并展示给所有人。把这个信息独立出来，
// 列表继续对所有用户共享缓存，未读状态各自取各自的。
discussionRoutes.get('/unread', authMiddleware(), async (c) => {
  const user = c.get('user')!;

  // 只算「读过之后又有新回复」的：没有阅读记录（从没打开过）不算未读，
  // 否则新用户第一次进讨论区会看到满屏红点，反而没有信息量。
  const rows = await c.env.DB.prepare(`
    SELECT dr.discussion_id,
           (SELECT COUNT(*) FROM comments c
             WHERE c.discussion_id = dr.discussion_id
               AND c.created_at > dr.last_read_at) AS new_reply_count
    FROM discussion_reads dr
    JOIN discussions d ON d.id = dr.discussion_id
    WHERE dr.user_id = ?
    ORDER BY d.updated_at DESC
    LIMIT 200
  `).bind(user.id).all<{ discussion_id: number; new_reply_count: number }>();

  const items = (rows.results || [])
    .map(r => ({ discussion_id: r.discussion_id, new_reply_count: r.new_reply_count || 0 }))
    .filter(r => r.new_reply_count > 0);

  return c.json({
    items,
    total_unread: items.length,
    total_new_replies: items.reduce((sum, r) => sum + r.new_reply_count, 0),
  });
});

// 标记讨论已读（需要登录）。详情页打开后调用，幂等。
discussionRoutes.post('/:id/read', authMiddleware(), async (c) => {
  const discussionId = Number.parseInt(c.req.param('id'), 10);
  if (!Number.isInteger(discussionId) || discussionId <= 0) {
    return c.json({ error: '无效的讨论ID' }, 400);
  }
  const user = c.get('user')!;

  const exists = await c.env.DB.prepare('SELECT id FROM discussions WHERE id = ?').bind(discussionId).first();
  if (!exists) return c.json({ error: '讨论不存在' }, 404);

  await c.env.DB.prepare(`
    INSERT INTO discussion_reads (user_id, discussion_id, last_read_at)
    VALUES (?, ?, datetime('now'))
    ON CONFLICT(user_id, discussion_id) DO UPDATE SET last_read_at = datetime('now')
  `).bind(user.id, discussionId).run();

  return c.json({ message: '已标记为已读' });
});

// 获取单个讨论（含评论）
discussionRoutes.get('/:id', authMiddleware(false), async (c) => {
  const id = c.req.param('id');

  // 先检查是否存在
  const exists = await c.env.DB.prepare('SELECT id FROM discussions WHERE id = ?').bind(id).first();
  if (!exists) return c.json({ error: '讨论不存在' }, 404);

  // 增加阅读量（去重：登录用户按用户 ID，匿名按客户端真实 IP） —— 在读取详情之前完成，确保返回值包含最新 view_count
  const viewerKey = buildViewerKey(c);
  const viewResult = await c.env.DB.prepare(
    'INSERT OR IGNORE INTO content_views (viewer_key, target_type, target_id) VALUES (?, ?, ?)'
  ).bind(viewerKey, 'discussion', id).run();
  if (viewResult.meta.changes > 0) {
    await c.env.DB.prepare(
      'UPDATE discussions SET view_count = view_count + 1 WHERE id = ?'
    ).bind(id).run();
  }

  // 读取完整讨论（此时 view_count 已是最新值）
  const discussion = await c.env.DB.prepare(`
    SELECT d.*, COALESCE(dc.name, d.category) as category_name, COALESCE(dc.icon, '') as category_icon,
    COALESCE(NULLIF(u.nickname,''), u.username) as author_name, u.avatar_url as author_avatar
    FROM discussions d
    LEFT JOIN users u ON d.author_id = u.id
    LEFT JOIN discussion_categories dc ON d.category = dc.slug
    WHERE d.id = ?
  `).bind(id).first();

  if (!discussion) return c.json({ error: '讨论不存在' }, 404);

  // 当前用户是否赞过每条评论。未登录时 viewerId=0，所有 liked 均为 false。
  const viewerId = c.get('user')?.id ?? 0;

  // 获取评论
  const comments = await c.env.DB.prepare(`
    SELECT cm.*, COALESCE(NULLIF(u.nickname,''), u.username) as author_name, u.avatar_url as author_avatar,
      COALESCE(NULLIF(pu.nickname,''), pu.username) as parent_author_name,
      EXISTS(
        SELECT 1 FROM likes l
        WHERE l.user_id = ? AND l.target_type = 'comment' AND l.target_id = cm.id
      ) as liked
    FROM comments cm
    LEFT JOIN users u ON cm.author_id = u.id
    LEFT JOIN comments pcm ON cm.parent_id = pcm.id
    LEFT JOIN users pu ON pcm.author_id = pu.id
    WHERE cm.discussion_id = ?
    ORDER BY cm.created_at ASC
  `).bind(viewerId, id).all();

  // SQLite 的 EXISTS 返回 0/1，显式转成 JSON 布尔值，避免客户端布尔反序列化差异。
  const commentRows = (comments.results as Array<Record<string, unknown>>).map((row) => ({
    ...row,
    liked: Boolean(row.liked),
  }));

  return c.json({ discussion, comments: commentRows });
});

// 发起讨论（需要登录）
discussionRoutes.post('/', authMiddleware(), rateLimit({ key: 'discussion:create', maxRequests: 10, windowMs: 10 * 60 * 1000, identifier: userOrIpIdentifier }), async (c) => {
  const user = c.get('user')!;
  const { title, content, category, article_id, tags } = await c.req.json();

  if (!title || !content) {
    return c.json({ error: '标题和内容不能为空' }, 400);
  }
  if (title.length > 200) {
    return c.json({ error: '标题最长200个字符' }, 400);
  }
  if (content.length > 20000) {
    return c.json({ error: '内容最长20000个字符' }, 400);
  }

  let resolvedCategory = typeof category === 'string' && category.trim().length > 0
    ? category.trim()
    : '';

  if (resolvedCategory) {
    const existingCategory = await c.env.DB.prepare(
      'SELECT slug FROM discussion_categories WHERE slug = ?'
    ).bind(resolvedCategory).first<{ slug: string }>();

    if (!existingCategory) {
      return c.json({ error: '讨论分类不存在' }, 400);
    }
  } else {
    const defaultCategory = await c.env.DB.prepare(
      'SELECT slug FROM discussion_categories ORDER BY sort_order ASC, id ASC LIMIT 1'
    ).first<{ slug: string }>();

    if (!defaultCategory) {
      return c.json({ error: '暂无可用的讨论分类，请先创建分类' }, 400);
    }
    resolvedCategory = defaultCategory.slug;
  }

  let articleIdForInsert: number | null = null;
  if (article_id !== undefined && article_id !== null && String(article_id).trim() !== '') {
    const articleIdNum = Number.parseInt(String(article_id), 10);
    if (!Number.isInteger(articleIdNum) || articleIdNum <= 0) {
      return c.json({ error: '无效的关联文章ID' }, 400);
    }

    const article = await c.env.DB.prepare(
      'SELECT id FROM articles WHERE id = ?'
    ).bind(articleIdNum).first();
    if (!article) {
      return c.json({ error: '关联文章不存在' }, 404);
    }

    articleIdForInsert = articleIdNum;
  }

  const result = await c.env.DB.prepare(`
    INSERT INTO discussions (title, content, author_id, category, article_id, tags)
    VALUES (?, ?, ?, ?, ?, ?)
  `).bind(title, content, user.id, resolvedCategory, articleIdForInsert, normalizeTags(tags)).run();

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/stats`]);
  return c.json({ id: result.meta.last_row_id, message: '发布成功' }, 201);
});

// 更新讨论（仅作者或管理员）
discussionRoutes.put('/:id', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const { title, content, category, article_id, tags } = await c.req.json();

  if (!title || !content) {
    return c.json({ error: '标题和内容不能为空' }, 400);
  }
  if (title.length > 200) {
    return c.json({ error: '标题最长200个字符' }, 400);
  }
  if (content.length > 20000) {
    return c.json({ error: '内容最长20000个字符' }, 400);
  }

  const discussion = await c.env.DB.prepare(
    'SELECT author_id FROM discussions WHERE id = ?'
  ).bind(id).first<{ author_id: number }>();

  if (!discussion) {
    return c.json({ error: '讨论不存在' }, 404);
  }
  // 编辑限于作者或管理员；编辑角色不能改写其他用户的讨论内容
  if (!(await isOwnerOrAdmin(c, discussion.author_id))) {
    return c.json({ error: '无权编辑' }, 403);
  }

  let resolvedCategory = typeof category === 'string' && category.trim().length > 0
    ? category.trim()
    : '';

  if (resolvedCategory) {
    const existingCategory = await c.env.DB.prepare(
      'SELECT slug FROM discussion_categories WHERE slug = ?'
    ).bind(resolvedCategory).first<{ slug: string }>();

    if (!existingCategory) {
      return c.json({ error: '讨论分类不存在' }, 400);
    }
  } else {
    const defaultCategory = await c.env.DB.prepare(
      'SELECT slug FROM discussion_categories ORDER BY sort_order ASC, id ASC LIMIT 1'
    ).first<{ slug: string }>();

    if (!defaultCategory) {
      return c.json({ error: '暂无可用的讨论分类，请先创建分类' }, 400);
    }
    resolvedCategory = defaultCategory.slug;
  }

  let articleIdForUpdate: number | null = null;
  if (article_id !== undefined && article_id !== null && String(article_id).trim() !== '') {
    const articleIdNum = Number.parseInt(String(article_id), 10);
    if (!Number.isInteger(articleIdNum) || articleIdNum <= 0) {
      return c.json({ error: '无效的关联文章ID' }, 400);
    }

    const article = await c.env.DB.prepare(
      'SELECT id FROM articles WHERE id = ?'
    ).bind(articleIdNum).first();
    if (!article) {
      return c.json({ error: '关联文章不存在' }, 404);
    }

    articleIdForUpdate = articleIdNum;
  }

  await c.env.DB.prepare(`
    UPDATE discussions
    SET title = ?, content = ?, category = ?, article_id = ?, tags = ?, updated_at = datetime('now')
    WHERE id = ?
  `).bind(title, content, resolvedCategory, articleIdForUpdate, normalizeTags(tags), id).run();

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/discussions/${id}`, `${baseUrl}/api/stats`]);
  return c.json({ message: '更新成功' });
});

// 回复讨论
discussionRoutes.post('/:id/comments', authMiddleware(), rateLimit({ key: 'discussion:comment', maxRequests: 20, windowMs: 10 * 60 * 1000, identifier: userOrIpIdentifier }), async (c) => {
  const discussionId = c.req.param('id');
  const discussionIdNum = Number.parseInt(discussionId, 10);
  const user = c.get('user')!;
  const { content, parent_id } = await c.req.json();

  if (!Number.isInteger(discussionIdNum) || discussionIdNum <= 0) {
    return c.json({ error: '无效的讨论ID' }, 400);
  }
  if (!content) return c.json({ error: '回复内容不能为空' }, 400);
  if (content.length > 5000) {
    return c.json({ error: '回复内容最长5000个字符' }, 400);
  }

  const discussionExists = await c.env.DB.prepare(
    'SELECT id, is_closed FROM discussions WHERE id = ?'
  ).bind(discussionIdNum).first<{ id: number; is_closed: number }>();
  if (!discussionExists) {
    return c.json({ error: '讨论不存在' }, 404);
  }

  // 已锁定的讨论：仅 admin/editor 可继续回复
  if (discussionExists.is_closed === 1) {
    const role = await refreshCurrentUserRole(c);
    if (role !== 'admin' && role !== 'editor') {
      return c.json({ error: '该讨论已锁定，无法回复' }, 403);
    }
  }

  let parentIdForInsert: number | null = null;
  let parentCommentAuthorId: number | null = null;
  if (parent_id !== undefined && parent_id !== null) {
    const parentIdNum = Number.parseInt(String(parent_id), 10);
    if (!Number.isInteger(parentIdNum) || parentIdNum <= 0) {
      return c.json({ error: '无效的父评论ID' }, 400);
    }

    const parentComment = await c.env.DB.prepare(
      'SELECT discussion_id, author_id FROM comments WHERE id = ?'
    ).bind(parentIdNum).first<{ discussion_id: number; author_id: number }>();

    if (!parentComment) {
      return c.json({ error: '父评论不存在' }, 400);
    }
    if (parentComment.discussion_id !== discussionIdNum) {
      return c.json({ error: '不能跨讨论回复' }, 400);
    }

    parentIdForInsert = parentIdNum;
    parentCommentAuthorId = parentComment.author_id;
  }

  const result = await c.env.DB.prepare(`
    INSERT INTO comments (content, author_id, discussion_id, parent_id)
    VALUES (?, ?, ?, ?)
  `).bind(content, user.id, discussionIdNum, parentIdForInsert).run();

  // 更新回复数和更新时间
  await c.env.DB.prepare(`
    UPDATE discussions SET reply_count = reply_count + 1, updated_at = datetime('now')
    WHERE id = ?
  `).bind(discussionIdNum).run();

  // 发送通知给讨论作者（如果不是自己回复自己的）
  try {
    const discussion = await c.env.DB.prepare(
      'SELECT author_id, title FROM discussions WHERE id = ?'
    ).bind(discussionIdNum).first<{ author_id: number; title: string }>();

    const commenter = await c.env.DB.prepare(
      'SELECT username, nickname FROM users WHERE id = ?'
    ).bind(user.id).first<{ username: string; nickname: string }>();

    const commenterName = commenter?.nickname || commenter?.username || '有人';

    if (discussion && discussion.author_id !== user.id) {
      await createNotification(
        c.env.DB,
        discussion.author_id,
        'reply',
        `${commenterName} 回复了你的讨论`,
        content.substring(0, 100),
        'discussion',
        discussionIdNum,
        user.id
      );
    }

    // 如果是回复某条评论，也通知被回复的人
    if (parentCommentAuthorId && parentCommentAuthorId !== user.id && parentCommentAuthorId !== discussion?.author_id) {
        await createNotification(
          c.env.DB,
          parentCommentAuthorId,
          'reply',
          `${commenterName} 回复了你的评论`,
          content.substring(0, 100),
          'discussion',
          discussionIdNum,
          user.id
        );
    }
  } catch (e) {
    // 通知发送失败不影响评论功能
    console.error('通知发送失败:', e);
  }

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/discussions/${discussionId}`]);
  return c.json({ id: result.meta.last_row_id, message: '回复成功' }, 201);
});

// 删除讨论（作者/编辑/管理员）
discussionRoutes.delete('/:id', authMiddleware(), async (c) => {
  const id = c.req.param('id');

  const discussion = await c.env.DB.prepare(
    'SELECT author_id FROM discussions WHERE id = ?'
  ).bind(id).first<{ author_id: number }>();

  if (!discussion) return c.json({ error: '讨论不存在' }, 404);
  if (!(await isOwnerOrEditorOrAdmin(c, discussion.author_id))) {
    return c.json({ error: '无权删除' }, 403);
  }

  // 收集该讨论下所有评论 ID（ON DELETE CASCADE 会连带删除，其点赞记录需手动清理）
  const discussionIdNum = Number.parseInt(id, 10);
  const commentIdsResult = await c.env.DB.prepare(
    'SELECT id FROM comments WHERE discussion_id = ?'
  ).bind(id).all<{ id: number }>();
  const commentIds = commentIdsResult.results.map((row) => row.id);

  await c.env.DB.prepare('DELETE FROM discussions WHERE id = ?').bind(id).run();

  // 清理讨论及其评论的点赞/浏览去重孤儿记录
  const cleanupStmts = [
    c.env.DB.prepare("DELETE FROM likes WHERE target_type = 'discussion' AND target_id = ?").bind(discussionIdNum),
    c.env.DB.prepare("DELETE FROM content_views WHERE target_type = 'discussion' AND target_id = ?").bind(discussionIdNum),
  ];
  for (const commentId of commentIds) {
    cleanupStmts.push(c.env.DB.prepare("DELETE FROM likes WHERE target_type = 'comment' AND target_id = ?").bind(commentId));
  }
  await c.env.DB.batch(cleanupStmts);

  await deleteNotificationsByRelatedTargets(c.env.DB, 'discussion', [Number.parseInt(id, 10)]);
  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/discussions/${id}`, `${baseUrl}/api/stats`]);
  return c.json({ message: '删除成功' });
});

// 管理员：置顶 / 取消置顶
discussionRoutes.put('/:id/pin', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const idNum = Number.parseInt(id, 10);
  if (!Number.isInteger(idNum) || idNum <= 0) {
    return c.json({ error: '无效的讨论ID' }, 400);
  }

  const body = await c.req.json().catch(() => ({}));
  const pinned = body.is_pinned === undefined ? 1 : (body.is_pinned ? 1 : 0);

  const existing = await c.env.DB.prepare(
    'SELECT id FROM discussions WHERE id = ?'
  ).bind(idNum).first();
  if (!existing) return c.json({ error: '讨论不存在' }, 404);

  await c.env.DB.prepare(
    'UPDATE discussions SET is_pinned = ?, updated_at = datetime(\'now\') WHERE id = ?'
  ).bind(pinned, idNum).run();

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/discussions/${idNum}`]);
  return c.json({ message: pinned ? '已置顶' : '已取消置顶', is_pinned: pinned });
});

// 管理员：锁定 / 解锁
discussionRoutes.put('/:id/close', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const idNum = Number.parseInt(id, 10);
  if (!Number.isInteger(idNum) || idNum <= 0) {
    return c.json({ error: '无效的讨论ID' }, 400);
  }

  const body = await c.req.json().catch(() => ({}));
  const closed = body.is_closed === undefined ? 1 : (body.is_closed ? 1 : 0);

  const existing = await c.env.DB.prepare(
    'SELECT id FROM discussions WHERE id = ?'
  ).bind(idNum).first();
  if (!existing) return c.json({ error: '讨论不存在' }, 404);

  await c.env.DB.prepare(
    'UPDATE discussions SET is_closed = ?, updated_at = datetime(\'now\') WHERE id = ?'
  ).bind(closed, idNum).run();

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/discussions/${idNum}`]);
  return c.json({ message: closed ? '已锁定' : '已解锁', is_closed: closed });
});

// 编辑评论（仅作者或管理员）
discussionRoutes.put('/:id/comments/:commentId', authMiddleware(), async (c) => {
  const discussionId = c.req.param('id');
  const commentId = c.req.param('commentId');
  const { content } = await c.req.json();

  const discussionIdNum = Number.parseInt(discussionId, 10);
  if (!Number.isInteger(discussionIdNum) || discussionIdNum <= 0) {
    return c.json({ error: '无效的讨论ID' }, 400);
  }

  if (!content || typeof content !== 'string') {
    return c.json({ error: '回复内容不能为空' }, 400);
  }
  if (content.length > 5000) {
    return c.json({ error: '回复内容最长5000个字符' }, 400);
  }

  const comment = await c.env.DB.prepare(
    'SELECT author_id, discussion_id FROM comments WHERE id = ?'
  ).bind(commentId).first<{ author_id: number; discussion_id: number }>();

  if (!comment) return c.json({ error: '评论不存在' }, 404);
  if (comment.discussion_id !== discussionIdNum) {
    return c.json({ error: '评论不属于当前讨论' }, 400);
  }
  // 评论编辑限于作者或管理员；编辑角色不能改写他人评论
  if (!(await isOwnerOrAdmin(c, comment.author_id))) {
    return c.json({ error: '无权编辑' }, 403);
  }

  await c.env.DB.prepare(
    `UPDATE comments SET content = ?, updated_at = datetime('now') WHERE id = ?`
  ).bind(content, commentId).run();

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/discussions/${discussionId}`]);
  return c.json({ message: '更新成功' });
});

// 删除评论（仅作者或管理员）
discussionRoutes.delete('/:id/comments/:commentId', authMiddleware(), async (c) => {
  const commentId = c.req.param('commentId');
  const discussionId = c.req.param('id');

  const discussionIdNum = Number.parseInt(discussionId, 10);
  if (!Number.isInteger(discussionIdNum) || discussionIdNum <= 0) {
    return c.json({ error: '无效的讨论ID' }, 400);
  }

  const comment = await c.env.DB.prepare(
    'SELECT author_id, discussion_id FROM comments WHERE id = ?'
  ).bind(commentId).first<{ author_id: number; discussion_id: number }>();

  if (!comment) return c.json({ error: '评论不存在' }, 404);
  if (comment.discussion_id !== discussionIdNum) {
    return c.json({ error: '评论不属于当前讨论' }, 400);
  }
  if (!(await isOwnerOrEditorOrAdmin(c, comment.author_id))) {
    return c.json({ error: '无权删除' }, 403);
  }

  // 统计该评论及所有子孙评论数量（ON DELETE CASCADE 会级联删除）
  const countResult = await c.env.DB.prepare(`
    WITH RECURSIVE descendants(id) AS (
      SELECT id FROM comments WHERE id = ?
      UNION ALL
      SELECT c.id FROM comments c JOIN descendants d ON c.parent_id = d.id
    )
    SELECT COUNT(*) as total FROM descendants
  `).bind(commentId).first<{ total: number }>();
  const deleteCount = countResult?.total || 1;

  // 先收集此次删除涉及的评论 ID（父+所有后代），用于清理对应通知
  const descendantRows = await c.env.DB.prepare(`
    WITH RECURSIVE descendants(id) AS (
      SELECT id FROM comments WHERE id = ?
      UNION ALL
      SELECT c.id FROM comments c JOIN descendants d ON c.parent_id = d.id
    )
    SELECT id FROM descendants
  `).bind(commentId).all<{ id: number }>();
  const deletedCommentIds = descendantRows.results.map((row) => row.id);

  await c.env.DB.prepare('DELETE FROM comments WHERE id = ?').bind(commentId).run();
  await c.env.DB.prepare(
    'UPDATE discussions SET reply_count = MAX(0, reply_count - ?) WHERE id = ?'
  ).bind(deleteCount, discussionIdNum).run();

  // 清理被删评论（含子孙评论）的点赞记录
  if (deletedCommentIds.length > 0) {
    await c.env.DB.batch(
      deletedCommentIds.map((cid) =>
        c.env.DB.prepare("DELETE FROM likes WHERE target_type = 'comment' AND target_id = ?").bind(cid)
      )
    );
  }

  if (deletedCommentIds.length > 0) {
    await deleteNotificationsByRelatedTargets(c.env.DB, 'comment', deletedCommentIds);
  }

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/discussions/${discussionId}`]);
  return c.json({ message: '删除成功' });
});

// 点赞评论
discussionRoutes.post('/:id/comments/:commentId/like', authMiddleware(), rateLimit({ key: 'like:toggle', maxRequests: 30, windowMs: 60 * 1000, identifier: userOrIpIdentifier }), async (c) => {
  const discussionId = c.req.param('id');
  const commentId = c.req.param('commentId');
  const user = c.get('user')!;

  const discussionIdNum = Number.parseInt(discussionId, 10);
  if (!Number.isInteger(discussionIdNum) || discussionIdNum <= 0) {
    return c.json({ error: '无效的讨论ID' }, 400);
  }

  const comment = await c.env.DB.prepare(
    'SELECT id, discussion_id, author_id, content FROM comments WHERE id = ?'
  ).bind(commentId).first<{ id: number; discussion_id: number; author_id: number; content: string }>();
  if (!comment) return c.json({ error: '评论不存在' }, 404);
  if (comment.discussion_id !== discussionIdNum) {
    return c.json({ error: '评论不属于当前讨论' }, 400);
  }

  const result = await toggleLike(c, {
    userId: user.id,
    targetId: commentId,
    targetType: 'comment',
    countTable: 'comments',
    likeSuccessMessage: '点赞成功',
    unlikeSuccessMessage: '已取消点赞',
  });

  if (result.liked) {
    const actorName = await getActorDisplayName(c.env.DB, user.id);
    await notifyActor(c.env.DB, {
      ownerId: comment.author_id,
      actorId: user.id,
      type: 'like',
      title: `${actorName} 点赞了你的评论`,
      content: (comment.content || '').slice(0, 100),
      relatedType: 'discussion',
      relatedId: comment.discussion_id,
    });
  } else {
    // 取消点赞时移除对应未读通知，防止 toggle 循环刷通知
    await deleteActorNotification(c.env.DB, {
      ownerId: comment.author_id,
      actorId: user.id,
      type: 'like',
      relatedType: 'discussion',
      relatedId: comment.discussion_id,
    });
  }

  return c.json(result);
});
