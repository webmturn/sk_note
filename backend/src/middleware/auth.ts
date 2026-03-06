import { Context, Next } from 'hono';
import type { Env } from '../index';

interface JwtPayload {
  id: number;
  username: string;
  role: string;
  exp: number;
}

// 简单的 JWT 实现（适用于 Cloudflare Workers）
export async function createToken(payload: Omit<JwtPayload, 'exp'>, secret: string): Promise<string> {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const exp = Math.floor(Date.now() / 1000) + 7 * 24 * 60 * 60; // 7天过期
  const body = btoa(JSON.stringify({ ...payload, exp }));
  const data = `${header}.${body}`;

  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data));
  const sig = btoa(String.fromCharCode(...new Uint8Array(signature)));

  return `${data}.${sig}`;
}

export async function verifyToken(token: string, secret: string): Promise<JwtPayload | null> {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const key = await crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(secret),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['verify']
    );

    const data = `${parts[0]}.${parts[1]}`;
    const signature = Uint8Array.from(atob(parts[2]), (c) => c.charCodeAt(0));
    const valid = await crypto.subtle.verify('HMAC', key, signature, new TextEncoder().encode(data));

    if (!valid) return null;

    const payload: JwtPayload = JSON.parse(atob(parts[1]));
    if (payload.exp < Math.floor(Date.now() / 1000)) return null;

    return payload;
  } catch {
    return null;
  }
}

// 认证中间件
export function authMiddleware(required = true) {
  return async (c: Context<{ Bindings: Env }>, next: Next) => {
    const authHeader = c.req.header('Authorization');
    const token = authHeader?.replace('Bearer ', '');

    if (!token) {
      if (required) return c.json({ error: '请先登录' }, 401);
      await next();
      return;
    }

    const payload = await verifyToken(token, c.env.JWT_SECRET);
    if (!payload) {
      if (required) return c.json({ error: '登录已过期，请重新登录' }, 401);
      await next();
      return;
    }

    c.set('user' as never, payload);
    await next();
  };
}

// 管理员权限中间件
export function adminMiddleware() {
  return async (c: Context<{ Bindings: Env }>, next: Next) => {
    const user = c.get('user' as never) as JwtPayload | undefined;
    if (!user || (user.role !== 'admin' && user.role !== 'editor')) {
      return c.json({ error: '权限不足' }, 403);
    }
    await next();
  };
}
