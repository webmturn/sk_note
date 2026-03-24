import { Context, Next } from 'hono';
import type { AppEnv, JwtPayload } from '../index';

// base64url 编码/解码（RFC 7515）
function base64urlEncode(str: string): string {
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64urlDecode(str: string): string {
  let s = str.replace(/-/g, '+').replace(/_/g, '/');
  while (s.length % 4) s += '=';
  return atob(s);
}

function base64urlEncodeBytes(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64urlDecodeBytes(str: string): Uint8Array {
  let s = str.replace(/-/g, '+').replace(/_/g, '/');
  while (s.length % 4) s += '=';
  return Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
}

// JWT 实现（base64url，兼容旧 base64 token）
export type { JwtPayload };

export async function createToken(payload: Omit<JwtPayload, 'exp'>, secret: string): Promise<string> {
  const header = base64urlEncode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const exp = Math.floor(Date.now() / 1000) + 7 * 24 * 60 * 60; // 7天过期
  const body = base64urlEncode(JSON.stringify({ ...payload, exp }));
  const data = `${header}.${body}`;

  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data));
  const sig = base64urlEncodeBytes(new Uint8Array(signature));

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
    const signature = base64urlDecodeBytes(parts[2]);
    const valid = await crypto.subtle.verify('HMAC', key, signature, new TextEncoder().encode(data));

    if (!valid) return null;

    const payload: JwtPayload = JSON.parse(base64urlDecode(parts[1]));
    if (payload.exp < Math.floor(Date.now() / 1000)) return null;

    return payload;
  } catch {
    return null;
  }
}

export async function refreshCurrentUserRole(c: Context<AppEnv>): Promise<string | null> {
  const user = c.get('user');
  if (!user) return null;

  const dbUser = await c.env.DB.prepare(
    'SELECT role FROM users WHERE id = ?'
  ).bind(user.id).first<{ role: string }>();

  if (!dbUser) return null;
  user.role = dbUser.role;
  return dbUser.role;
}

export async function isOwnerOrAdmin(c: Context<AppEnv>, ownerId: number): Promise<boolean> {
  const user = c.get('user');
  if (!user) return false;
  if (user.id === ownerId) return true;
  const role = await refreshCurrentUserRole(c);
  return role === 'admin';
}

// 认证中间件
export function authMiddleware(required = true) {
  return async (c: Context<AppEnv>, next: Next) => {
    const authHeader = c.req.header('Authorization');
    const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : undefined;

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

    c.set('user', payload);
    await next();
  };
}

// 编辑/管理员权限中间件（从数据库验证最新角色，防止 JWT 过期角色）
export function editorMiddleware() {
  return async (c: Context<AppEnv>, next: Next) => {
    const role = await refreshCurrentUserRole(c);
    if (!role || (role !== 'admin' && role !== 'editor')) {
      return c.json({ error: '权限不足' }, 403);
    }
    await next();
  };
}

// 管理员权限中间件（从数据库验证最新角色）
export function adminMiddleware() {
  return async (c: Context<AppEnv>, next: Next) => {
    const role = await refreshCurrentUserRole(c);
    if (role !== 'admin') {
      return c.json({ error: '权限不足' }, 403);
    }
    await next();
  };
}
