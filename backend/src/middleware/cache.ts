import { Context, Next } from 'hono';
import type { Env } from '../index';

// 检测是否运行在 Cloudflare Workers 环境
const isCloudflare = typeof caches !== 'undefined' && 'default' in (caches as any);

// Node.js 简易内存缓存
const memoryCache = new Map<string, { data: string; headers: Record<string, string>; expiry: number }>();

/**
 * 缓存中间件（自适应 Cloudflare 边缘缓存 / Node.js 内存缓存）
 */
export function edgeCache(maxAgeSec: number = 300) {
  return async (c: Context<{ Bindings: Env }>, next: Next) => {
    if (c.req.method !== 'GET') {
      await next();
      return;
    }

    if (isCloudflare) {
      // Cloudflare Workers: 使用边缘缓存
      const cache = (caches as any).default;
      const cacheKey = new Request(c.req.url, {
        method: 'GET',
        headers: { 'Accept': c.req.header('Accept') || '*/*' },
      });

      const cached = await cache.match(cacheKey);
      if (cached) {
        const response = new Response(cached.body, cached);
        response.headers.set('Access-Control-Allow-Origin', '*');
        response.headers.set('X-Cache', 'HIT');
        return response;
      }

      await next();

      const response = c.res;
      if (response.status === 200) {
        const responseToCache = response.clone();
        const headers = new Headers(responseToCache.headers);
        headers.set('Cache-Control', `public, s-maxage=${maxAgeSec}, max-age=0`);
        headers.set('X-Cache', 'MISS');
        const cachedResponse = new Response(responseToCache.body, { status: responseToCache.status, headers });
        c.executionCtx.waitUntil(cache.put(cacheKey, cachedResponse));
      }
    } else {
      // Node.js: 简易内存缓存
      const key = c.req.url;
      const now = Date.now();
      const hit = memoryCache.get(key);

      if (hit && hit.expiry > now) {
        const remaining = Math.ceil((hit.expiry - now) / 1000);
        c.header('Cache-Control', `public, max-age=${remaining}`);
        return c.json(JSON.parse(hit.data));
      }

      c.header('Cache-Control', `public, max-age=${maxAgeSec}`);
      await next();

      if (c.res.status === 200) {
        try {
          const clone = c.res.clone();
          const text = await clone.text();
          memoryCache.set(key, { data: text, headers: {}, expiry: now + maxAgeSec * 1000 });
        } catch (_) {}
      }
    }
  };
}

/**
 * 缓存清除工具函数
 */
export async function purgeCache(urls: string[]) {
  if (isCloudflare) {
    const cache = (caches as any).default;
    for (const url of urls) {
      const key = new Request(url, { method: 'GET' });
      await cache.delete(key);
    }
  } else {
    // Node.js: 清除内存缓存
    for (const url of urls) {
      memoryCache.delete(url);
    }
  }
}
