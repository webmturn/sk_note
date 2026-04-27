import { serve } from '@hono/node-server';
import { Hono } from 'hono';
import { setupApp, type AppEnv } from './index';
import { createD1Database } from './db';
import dotenv from 'dotenv';
import { existsSync, mkdirSync } from 'fs';
import { dirname } from 'path';

dotenv.config();

const dbPath = process.env.DB_PATH || './data/sk-note.db';
const port = parseInt(process.env.PORT || '3000');
const jwtSecret = process.env.JWT_SECRET || '';
const corsOrigins = process.env.CORS_ORIGINS || '';
const imgbbApiKey = process.env.IMGBB_API_KEY || '';

if (!jwtSecret) {
  console.error('❌ JWT_SECRET 未配置，请在 .env 文件中设置');
  process.exit(1);
}

// 确保数据库目录存在
const dbDir = dirname(dbPath);
if (!existsSync(dbDir)) {
  mkdirSync(dbDir, { recursive: true });
}

const db = createD1Database(dbPath);

const app = new Hono<AppEnv>();

// 注入环境变量（替代 Cloudflare Workers 的 Bindings）
app.use('*', async (c, next) => {
  (c.env as any).DB = db;
  (c.env as any).JWT_SECRET = jwtSecret;
  (c.env as any).CORS_ORIGINS = corsOrigins;
  (c.env as any).IMGBB_API_KEY = imgbbApiKey;
  // Polyfill Cloudflare Workers executionCtx
  try { c.executionCtx; } catch {
    Object.defineProperty(c, 'executionCtx', {
      value: {
        waitUntil: (p: Promise<any>) => { p.catch(() => {}); },
        passThroughOnException: () => {},
      },
    });
  }
  await next();
});

// 挂载所有路由
setupApp(app);

// 定期清理 content_views 过期记录（保留最近 30 天）
async function cleanupContentViews() {
  try {
    await db.prepare("DELETE FROM content_views WHERE viewed_at < datetime('now', '-30 days')").run();
  } catch (e) {
    console.error('content_views 清理失败:', e);
  }
}
cleanupContentViews();
setInterval(cleanupContentViews, 24 * 60 * 60 * 1000).unref(); // 每 24 小时清理一次

serve({ fetch: app.fetch, port, hostname: '0.0.0.0' }, (info) => {
  console.log(`🚀 Server running on http://0.0.0.0:${info.port}`);
});
