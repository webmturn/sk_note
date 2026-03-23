import { Hono } from 'hono';
import type { Env } from '../index';
import { authMiddleware } from '../middleware/auth';
import { edgeCache } from '../middleware/cache';

export const aggregatedRoutes = new Hono<{ Bindings: Env }>();

// 首页数据：分类 + 最新文章（合并 2 次调用为 1 次）
aggregatedRoutes.get('/home', edgeCache(300), async (c) => {
  try {
    const limit = Math.min(50, Math.max(1, parseInt(c.req.query('limit') || '10') || 10));

    const [catResult, artResult, shareResult] = await c.env.DB.batch([
      c.env.DB.prepare('SELECT * FROM categories ORDER BY sort_order ASC, id ASC'),
      c.env.DB.prepare(
        `SELECT a.id, a.title, a.summary, a.category_id, a.author_id, a.view_count, a.like_count, a.sort_order, a.created_at,
         COALESCE(NULLIF(u.nickname,''), u.username) as author_name, c.name as category_name
         FROM articles a
         LEFT JOIN users u ON a.author_id = u.id
         LEFT JOIN categories c ON a.category_id = c.id
         ORDER BY a.created_at DESC LIMIT ?`
      ).bind(limit),
      c.env.DB.prepare(
        'SELECT * FROM shares WHERE is_approved = 1 ORDER BY created_at DESC LIMIT 5'
      ),
    ]);

    return c.json({
      categories: catResult.results,
      articles: artResult.results,
      latest_shares: shareResult.results,
    });
  } catch (e: any) {
    return c.json({ error: '加载首页数据失败: ' + e.message }, 500);
  }
});

// 管理面板统计：未读通知数 + 文章总数 + 讨论总数（合并 3 次调用为 1 次）
aggregatedRoutes.get('/stats', authMiddleware(), async (c) => {
  try {
    const user = c.get('user' as never) as { id: number };

    const [unreadResult, articleResult, discussionResult, snippetResult, userResult, shareResult, myDiscussionResult, mySnippetResult, myArticleResult] = await c.env.DB.batch([
      c.env.DB.prepare('SELECT COUNT(*) as count FROM notifications WHERE user_id = ? AND is_read = 0').bind(user.id),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM articles'),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM discussions'),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM snippets'),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM users'),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM shares'),
      c.env.DB.prepare('SELECT COUNT(DISTINCT d.id) as count FROM discussions d LEFT JOIN comments c ON c.discussion_id = d.id WHERE d.author_id = ? OR c.author_id = ?').bind(user.id, user.id),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM snippets WHERE author_id = ?').bind(user.id),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM articles WHERE author_id = ?').bind(user.id),
    ]);

    return c.json({
      unread_notifications: (unreadResult.results[0] as any)?.count || 0,
      total_articles: (articleResult.results[0] as any)?.count || 0,
      total_discussions: (discussionResult.results[0] as any)?.count || 0,
      total_snippets: (snippetResult.results[0] as any)?.count || 0,
      total_users: (userResult.results[0] as any)?.count || 0,
      total_shares: (shareResult as any)?.results?.[0]?.count || 0,
      my_discussions: (myDiscussionResult.results[0] as any)?.count || 0,
      my_snippets: (mySnippetResult.results[0] as any)?.count || 0,
      my_articles: (myArticleResult.results[0] as any)?.count || 0,
    });
  } catch (e: any) {
    return c.json({ error: '加载统计数据失败: ' + e.message }, 500);
  }
});
