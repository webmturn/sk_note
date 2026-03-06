import { Context, Next } from 'hono';
import type { Env } from '../index';

/**
 * CDN 边缘缓存中间件（适用于公开 GET 接口）
 * 
 * 工作原理：
 * 1. Worker 收到请求时先查 Cloudflare 边缘缓存
 * 2. 命中缓存 → 直接返回，Worker CPU 时间极短
 * 3. 未命中 → 执行业务逻辑，将结果写入边缘缓存
 * 
 * 注意：使用 Cache API 而非单纯 Cache-Control 头，
 * 因为带 Authorization 头的请求 Cloudflare 默认不缓存。
 */
export function edgeCache(maxAgeSec: number = 300) {
  return async (c: Context<{ Bindings: Env }>, next: Next) => {
    // 只缓存 GET 请求
    if (c.req.method !== 'GET') {
      await next();
      return;
    }

    const cache = caches.default;
    // 用不带 Authorization 的 URL 作为缓存 key
    const cacheKey = new Request(c.req.url, {
      method: 'GET',
      headers: { 'Accept': c.req.header('Accept') || '*/*' },
    });

    // 检查边缘缓存
    const cached = await cache.match(cacheKey);
    if (cached) {
      // 命中缓存，直接返回（Worker 调用仍计数，但 CPU 极短 + D1 零查询）
      const response = new Response(cached.body, cached);
      // 保留 CORS 头
      response.headers.set('Access-Control-Allow-Origin', '*');
      response.headers.set('X-Cache', 'HIT');
      return response;
    }

    // 未命中，执行业务逻辑
    await next();

    // 只缓存成功的响应
    const response = c.res;
    if (response.status === 200) {
      const responseToCache = response.clone();
      const headers = new Headers(responseToCache.headers);
      headers.set('Cache-Control', `public, s-maxage=${maxAgeSec}, max-age=0`);
      headers.set('X-Cache', 'MISS');

      const cachedResponse = new Response(responseToCache.body, {
        status: responseToCache.status,
        headers,
      });
      // 异步写入缓存，不阻塞响应
      c.executionCtx.waitUntil(cache.put(cacheKey, cachedResponse));
    }
  };
}

/**
 * 缓存清除工具函数
 * 当数据变更时（POST/PUT/DELETE）调用，清除相关缓存
 */
export async function purgeCache(urls: string[]) {
  const cache = caches.default;
  for (const url of urls) {
    const key = new Request(url, { method: 'GET' });
    await cache.delete(key);
  }
}
