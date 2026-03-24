import { Context, Next } from 'hono';
import type { AppEnv } from '../index';

type IdentifierResolver = (c: Context<AppEnv>) => string;

interface RateLimitOptions {
  key: string;
  maxRequests: number;
  windowMs: number;
  identifier?: IdentifierResolver;
}

const buckets = new Map<string, number[]>();
const MAX_WINDOW_MS = 30 * 60 * 1000; // 所有限流窗口中的最大值，用于全局清理

// 定期清理不再活跃的 bucket，防止内存泄漏
setInterval(() => {
  const now = Date.now();
  for (const [key, timestamps] of buckets) {
    const pruned = prune(now, timestamps, MAX_WINDOW_MS);
    if (pruned.length === 0) buckets.delete(key);
    else buckets.set(key, pruned);
  }
}, 5 * 60 * 1000).unref();

function defaultIdentifier(c: Context<AppEnv>): string {
  return c.req.header('x-forwarded-for')?.split(',')[0]?.trim()
    || c.req.header('x-real-ip')
    || `anon:${(c.req.header('user-agent') || 'unknown').slice(0, 64)}`;
}

function prune(now: number, timestamps: number[], windowMs: number): number[] {
  const threshold = now - windowMs;
  let start = 0;
  while (start < timestamps.length && timestamps[start] <= threshold) {
    start += 1;
  }
  return start > 0 ? timestamps.slice(start) : timestamps;
}

export function rateLimit(options: RateLimitOptions) {
  const identifier = options.identifier || defaultIdentifier;

  return async (c: Context<AppEnv>, next: Next) => {
    const now = Date.now();
    const bucketKey = `${options.key}:${identifier(c)}`;
    const current = prune(now, buckets.get(bucketKey) || [], options.windowMs);

    if (current.length >= options.maxRequests) {
      const retryAfterSeconds = Math.max(1, Math.ceil((current[0] + options.windowMs - now) / 1000));
      c.header('Retry-After', String(retryAfterSeconds));
      c.header('X-RateLimit-Limit', String(options.maxRequests));
      c.header('X-RateLimit-Remaining', '0');
      return c.json({ error: '请求过于频繁，请稍后再试' }, 429);
    }

    current.push(now);
    buckets.set(bucketKey, current);

    c.header('X-RateLimit-Limit', String(options.maxRequests));
    c.header('X-RateLimit-Remaining', String(Math.max(0, options.maxRequests - current.length)));

    await next();
  };
}

export function userOrIpIdentifier(c: Context<AppEnv>): string {
  const user = c.get('user');
  if (user?.id) {
    return `user:${user.id}`;
  }
  return defaultIdentifier(c);
}
