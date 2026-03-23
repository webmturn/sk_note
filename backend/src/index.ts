import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { authRoutes } from './routes/auth';
import { articleRoutes } from './routes/articles';
import { discussionRoutes } from './routes/discussions';
import { categoryRoutes } from './routes/categories';
import { discussionCategoryRoutes } from './routes/discussionCategories';
import { referenceRoutes } from './routes/references';
import { notificationRoutes } from './routes/notifications';
import { snippetRoutes } from './routes/snippets';
import { bookmarkRoutes } from './routes/bookmarks';
import { shareRoutes } from './routes/shares';
import { appRoutes } from './routes/app';
import { aggregatedRoutes } from './routes/aggregated';
import { followRoutes } from './routes/follows';

export interface Env {
  DB: D1Database;
  JWT_SECRET: string;
}

export function setupApp(app: Hono<{ Bindings: Env }>) {
  // CORS 配置
  app.use('*', cors({
    origin: ['https://api.wsqh.cn', 'http://localhost:3000'],
    allowMethods: ['GET', 'POST', 'PUT', 'DELETE'],
    allowHeaders: ['Content-Type', 'Authorization'],
  }));

  // 健康检查
  app.get('/', (c) => c.json({ status: 'ok', service: 'Sketchware-Pro Manual API' }));

  // 路由挂载
  app.route('/api', aggregatedRoutes);
  app.route('/api/auth', authRoutes);
  app.route('/api/articles', articleRoutes);
  app.route('/api/discussions', discussionRoutes);
  app.route('/api/categories', categoryRoutes);
  app.route('/api/discussion-categories', discussionCategoryRoutes);
  app.route('/api/references', referenceRoutes);
  app.route('/api/notifications', notificationRoutes);
  app.route('/api/snippets', snippetRoutes);
  app.route('/api/bookmarks', bookmarkRoutes);
  app.route('/api/shares', shareRoutes);
  app.route('/api/app', appRoutes);
  app.route('/api/follows', followRoutes);

  return app;
}

// 默认导出（兼容 Cloudflare Workers 入口）
export default setupApp(new Hono<{ Bindings: Env }>());
