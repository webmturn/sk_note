import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { bodyLimit } from 'hono/body-limit';
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
  CORS_ORIGINS?: string;
  IMGBB_API_KEY?: string;
}

export interface JwtPayload {
  id: number;
  username: string;
  role: string;
  exp: number;
}

export type AppEnv = { Bindings: Env; Variables: { user?: JwtPayload } };

export function setupApp(app: Hono<AppEnv>) {
  const fallbackOrigins = ['http://localhost:3000', 'https://wsqh.cn', 'https://www.wsqh.cn'];
  // CORS 配置
  app.use('*', cors({
    origin: (origin, c) => {
      if (!origin) return origin;
      const configuredOrigins = (c.env.CORS_ORIGINS || '')
        .split(',')
        .map((item: string) => item.trim())
        .filter(Boolean);
      const allowedOrigins = configuredOrigins.length > 0 ? configuredOrigins : fallbackOrigins;
      return allowedOrigins.includes(origin) ? origin : null;
    },
    allowMethods: ['GET', 'POST', 'PUT', 'DELETE'],
    allowHeaders: ['Content-Type', 'Authorization'],
  }));

  // 请求体大小限制（1MB）
  app.use('*', bodyLimit({ maxSize: 1024 * 1024, onError: (c) => c.json({ error: '请求体过大，最大 1MB' }, 413) }));

  // 全局错误处理（捕获未处理的异常，如 JSON 解析失败）
  app.onError((err, c) => {
    if (err.message?.includes('JSON')) {
      return c.json({ error: '请求体格式错误' }, 400);
    }
    console.error('未处理的错误:', err);
    return c.json({ error: '服务器内部错误' }, 500);
  });

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
export default setupApp(new Hono<AppEnv>());
