import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { authRoutes } from './routes/auth';
import { articleRoutes } from './routes/articles';
import { discussionRoutes } from './routes/discussions';
import { categoryRoutes } from './routes/categories';
import { referenceRoutes } from './routes/references';
import { notificationRoutes } from './routes/notifications';
import { snippetRoutes } from './routes/snippets';
import { bookmarkRoutes } from './routes/bookmarks';
import { appRoutes } from './routes/app';
import { authMiddleware } from './middleware/auth';
import { edgeCache } from './middleware/cache';

export interface Env {
  DB: D1Database;
  JWT_SECRET: string;
}

const app = new Hono<{ Bindings: Env }>();

// CORS 配置
app.use('*', cors({
  origin: '*',
  allowMethods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowHeaders: ['Content-Type', 'Authorization'],
}));

// 健康检查
app.get('/', (c) => c.json({ status: 'ok', service: 'Sketchware-Pro Manual API' }));

// ============ 合并接口（减少请求次数）============

// 首页数据：分类 + 最新文章（合并 2 次调用为 1 次）
app.get('/api/home', edgeCache(300), async (c) => {
  try {
    const limit = parseInt(c.req.query('limit') || '10');

    const [catResult, artResult] = await c.env.DB.batch([
      c.env.DB.prepare('SELECT * FROM categories ORDER BY sort_order ASC, id ASC'),
      c.env.DB.prepare(
        `SELECT a.*, u.username as author_name, c.name as category_name
         FROM articles a
         LEFT JOIN users u ON a.author_id = u.id
         LEFT JOIN categories c ON a.category_id = c.id
         ORDER BY a.created_at DESC LIMIT ?`
      ).bind(limit),
    ]);

    return c.json({
      categories: catResult.results,
      articles: artResult.results,
    });
  } catch (e: any) {
    return c.json({ error: '加载首页数据失败: ' + e.message }, 500);
  }
});

// 管理面板统计：未读通知数 + 文章总数 + 讨论总数（合并 3 次调用为 1 次）
app.get('/api/stats', authMiddleware(), async (c) => {
  try {
    const user = c.get('user' as never) as { id: number };

    const [unreadResult, articleResult, discussionResult, snippetResult, userResult] = await c.env.DB.batch([
      c.env.DB.prepare('SELECT COUNT(*) as count FROM notifications WHERE user_id = ? AND is_read = 0').bind(user.id),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM articles'),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM discussions'),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM snippets'),
      c.env.DB.prepare('SELECT COUNT(*) as count FROM users'),
    ]);

    return c.json({
      unread_notifications: (unreadResult.results[0] as any)?.count || 0,
      total_articles: (articleResult.results[0] as any)?.count || 0,
      total_discussions: (discussionResult.results[0] as any)?.count || 0,
      total_snippets: (snippetResult.results[0] as any)?.count || 0,
      total_users: (userResult.results[0] as any)?.count || 0,
    });
  } catch (e: any) {
    return c.json({ error: '加载统计数据失败: ' + e.message }, 500);
  }
});

// 路由挂载
app.route('/api/auth', authRoutes);
app.route('/api/articles', articleRoutes);
app.route('/api/discussions', discussionRoutes);
app.route('/api/categories', categoryRoutes);
app.route('/api/references', referenceRoutes);
app.route('/api/notifications', notificationRoutes);
app.route('/api/snippets', snippetRoutes);
app.route('/api/bookmarks', bookmarkRoutes);
app.route('/api/app', appRoutes);

export default app;
