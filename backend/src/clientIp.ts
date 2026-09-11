import { Context } from 'hono';
import type { AppEnv } from './index';

/**
 * 解析客户端真实 IP（用于限流与阅读量/下载量去重）：
 * 1. 优先 Cloudflare 注入的 CF-Connecting-IP（客户端不可伪造）
 * 2. 其次取 X-Forwarded-For 的最后一段（由最接近服务端的可信反向代理追加；
 *    第一段可被客户端任意伪造，绝不能使用）
 * 3. 最后回退 X-Real-IP
 */
export function getClientIp(c: Context<AppEnv>): string | null {
  const cfIp = c.req.header('cf-connecting-ip')?.trim();
  if (cfIp) return cfIp;

  const xff = c.req.header('x-forwarded-for');
  if (xff) {
    const last = xff.split(',').pop()?.trim();
    if (last) return last;
  }

  const realIp = c.req.header('x-real-ip')?.trim();
  return realIp || null;
}

/**
 * 构造阅读量/下载量去重键：登录用户按用户 ID 去重，匿名用户按客户端 IP 去重。
 */
export function buildViewerKey(c: Context<AppEnv>): string {
  const user = c.get('user');
  if (user?.id) return `user:${user.id}`;
  return getClientIp(c) || `anon:${(c.req.header('user-agent') || 'unknown').slice(0, 64)}`;
}
