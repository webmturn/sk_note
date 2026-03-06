import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware } from '../middleware/auth';
import { edgeCache, purgeCache } from '../middleware/cache';
import { createNotification } from './notifications';

export const discussionRoutes = new Hono<{ Bindings: Env }>();

// 获取讨论列表
discussionRoutes.get('/', edgeCache(120), async (c) => {
  const page = parseInt(c.req.query('page') || '1');
  const limit = parseInt(c.req.query('limit') || '20');
  const category = c.req.query('category');
  const articleId = c.req.query('article_id');
  const search = c.req.query('search');
  const offset = (page - 1) * limit;

  let query = `
    SELECT d.*, u.username as author_name, u.avatar_url as author_avatar
    FROM discussions d
    LEFT JOIN users u ON d.author_id = u.id
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
    query += ' AND (d.title LIKE ? OR d.content LIKE ?)';
    params.push(`%${search}%`, `%${search}%`);
  }

  query += ' ORDER BY d.is_pinned DESC, d.updated_at DESC LIMIT ? OFFSET ?';
  params.push(limit, offset);

  const discussions = await c.env.DB.prepare(query).bind(...params).all();

  let countQuery = 'SELECT COUNT(*) as total FROM discussions WHERE 1=1';
  const countParams: any[] = [];
  if (category) { countQuery += ' AND category = ?'; countParams.push(category); }
  if (articleId) { countQuery += ' AND article_id = ?'; countParams.push(articleId); }
  if (search) {
    countQuery += ' AND (title LIKE ? OR content LIKE ?)';
    countParams.push(`%${search}%`, `%${search}%`);
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

// 获取单个讨论（含评论）
discussionRoutes.get('/:id', async (c) => {
  const id = c.req.param('id');

  const discussion = await c.env.DB.prepare(`
    SELECT d.*, u.username as author_name, u.avatar_url as author_avatar
    FROM discussions d
    LEFT JOIN users u ON d.author_id = u.id
    WHERE d.id = ?
  `).bind(id).first();

  if (!discussion) return c.json({ error: '讨论不存在' }, 404);

  // 增加阅读量
  await c.env.DB.prepare(
    'UPDATE discussions SET view_count = view_count + 1 WHERE id = ?'
  ).bind(id).run();

  // 获取评论
  const comments = await c.env.DB.prepare(`
    SELECT cm.*, u.username as author_name, u.avatar_url as author_avatar
    FROM comments cm
    LEFT JOIN users u ON cm.author_id = u.id
    WHERE cm.discussion_id = ?
    ORDER BY cm.created_at ASC
  `).bind(id).all();

  return c.json({ discussion, comments: comments.results });
});

// 发起讨论（需要登录）
discussionRoutes.post('/', authMiddleware(), async (c) => {
  const user = c.get('user' as never) as { id: number };
  const { title, content, category, article_id } = await c.req.json();

  if (!title || !content) {
    return c.json({ error: '标题和内容不能为空' }, 400);
  }

  const result = await c.env.DB.prepare(`
    INSERT INTO discussions (title, content, author_id, category, article_id)
    VALUES (?, ?, ?, ?, ?)
  `).bind(title, content, user.id, category || 'general', article_id || null).run();

  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/stats`]));
  return c.json({ id: result.meta.last_row_id, message: '发布成功' }, 201);
});

// 回复讨论
discussionRoutes.post('/:id/comments', authMiddleware(), async (c) => {
  const discussionId = c.req.param('id');
  const user = c.get('user' as never) as { id: number };
  const { content, parent_id } = await c.req.json();

  if (!content) return c.json({ error: '回复内容不能为空' }, 400);

  const result = await c.env.DB.prepare(`
    INSERT INTO comments (content, author_id, discussion_id, parent_id)
    VALUES (?, ?, ?, ?)
  `).bind(content, user.id, discussionId, parent_id || null).run();

  // 更新回复数和更新时间
  await c.env.DB.prepare(`
    UPDATE discussions SET reply_count = reply_count + 1, updated_at = datetime('now')
    WHERE id = ?
  `).bind(discussionId).run();

  // 发送通知给讨论作者（如果不是自己回复自己的）
  try {
    const discussion = await c.env.DB.prepare(
      'SELECT author_id, title FROM discussions WHERE id = ?'
    ).bind(discussionId).first<{ author_id: number; title: string }>();

    const commenter = await c.env.DB.prepare(
      'SELECT username FROM users WHERE id = ?'
    ).bind(user.id).first<{ username: string }>();

    const commenterName = commenter?.username || '有人';

    if (discussion && discussion.author_id !== user.id) {
      await createNotification(
        c.env.DB,
        discussion.author_id,
        'reply',
        `${commenterName} 回复了你的讨论`,
        content.substring(0, 100),
        'discussion',
        parseInt(discussionId)
      );
    }

    // 如果是回复某条评论，也通知被回复的人
    if (parent_id) {
      const parentComment = await c.env.DB.prepare(
        'SELECT author_id FROM comments WHERE id = ?'
      ).bind(parent_id).first<{ author_id: number }>();

      if (parentComment && parentComment.author_id !== user.id && parentComment.author_id !== discussion?.author_id) {
        await createNotification(
          c.env.DB,
          parentComment.author_id,
          'reply',
          `${commenterName} 回复了你的评论`,
          content.substring(0, 100),
          'discussion',
          parseInt(discussionId)
        );
      }
    }
  } catch (e) {
    // 通知发送失败不影响评论功能
    console.error('通知发送失败:', e);
  }

  return c.json({ id: result.meta.last_row_id, message: '回复成功' }, 201);
});

// 删除讨论（仅作者或管理员）
discussionRoutes.delete('/:id', authMiddleware(), async (c) => {
  const id = c.req.param('id');
  const user = c.get('user' as never) as { id: number; role: string };

  const discussion = await c.env.DB.prepare(
    'SELECT author_id FROM discussions WHERE id = ?'
  ).bind(id).first<{ author_id: number }>();

  if (!discussion) return c.json({ error: '讨论不存在' }, 404);
  if (discussion.author_id !== user.id && user.role !== 'admin') {
    return c.json({ error: '无权删除' }, 403);
  }

  await c.env.DB.prepare('DELETE FROM discussions WHERE id = ?').bind(id).run();
  const baseUrl = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(purgeCache([`${baseUrl}/api/discussions`, `${baseUrl}/api/discussions/${id}`, `${baseUrl}/api/stats`]));
  return c.json({ message: '删除成功' });
});

// 删除评论（仅作者或管理员）
discussionRoutes.delete('/:id/comments/:commentId', authMiddleware(), async (c) => {
  const commentId = c.req.param('commentId');
  const discussionId = c.req.param('id');
  const user = c.get('user' as never) as { id: number; role: string };

  const comment = await c.env.DB.prepare(
    'SELECT author_id FROM comments WHERE id = ?'
  ).bind(commentId).first<{ author_id: number }>();

  if (!comment) return c.json({ error: '评论不存在' }, 404);
  if (comment.author_id !== user.id && user.role !== 'admin') {
    return c.json({ error: '无权删除' }, 403);
  }

  await c.env.DB.prepare('DELETE FROM comments WHERE id = ?').bind(commentId).run();
  await c.env.DB.prepare(
    'UPDATE discussions SET reply_count = MAX(0, reply_count - 1) WHERE id = ?'
  ).bind(discussionId).run();

  return c.json({ message: '删除成功' });
});

// 点赞评论
discussionRoutes.post('/:id/comments/:commentId/like', authMiddleware(), async (c) => {
  const commentId = c.req.param('commentId');
  const user = c.get('user' as never) as { id: number };

  try {
    await c.env.DB.prepare(
      'INSERT INTO likes (user_id, target_type, target_id) VALUES (?, ?, ?)'
    ).bind(user.id, 'comment', commentId).run();

    await c.env.DB.prepare(
      'UPDATE comments SET like_count = like_count + 1 WHERE id = ?'
    ).bind(commentId).run();

    return c.json({ message: '点赞成功', liked: true });
  } catch {
    // UNIQUE 约束冲突 = 已点赞，执行取消
    await c.env.DB.prepare(
      'DELETE FROM likes WHERE user_id = ? AND target_type = ? AND target_id = ?'
    ).bind(user.id, 'comment', commentId).run();

    await c.env.DB.prepare(
      'UPDATE comments SET like_count = MAX(0, like_count - 1) WHERE id = ?'
    ).bind(commentId).run();

    return c.json({ message: '已取消点赞', liked: false });
  }
});
