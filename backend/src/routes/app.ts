import { Hono } from 'hono';
import type { AppEnv } from '../index';
import { edgeCache, purgeCache } from '../middleware/cache';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

export const appRoutes = new Hono<AppEnv>();

// 检查更新（从数据库读取最新版本信息，缓存 5 分钟）
appRoutes.get('/check-update', edgeCache(300), async (c) => {
  try {
    const currentVersion = c.req.query('current_version') || '';

    const release = await c.env.DB.prepare(
      'SELECT * FROM app_releases WHERE is_active = 1 ORDER BY created_at DESC LIMIT 1'
    ).first();

    if (!release) {
      return c.json({
        has_update: false,
        message: '暂无版本信息'
      });
    }

    const hasUpdate = currentVersion ? isNewerVersion(release.version_name as string, currentVersion) : false;

    return c.json({
      has_update: hasUpdate,
      latest_version: release.version_name,
      version_code: release.version_code,
      changelog: release.changelog || '',
      download_url: release.download_url || '',
      file_size: release.file_size || 0,
      release_url: release.release_url || '',
      released_at: release.created_at
    });
  } catch (e: any) {
    console.error('检查更新失败:', e);
    return c.json({ error: '检查更新失败' }, 500);
  }
});

// 管理员：发布新版本
appRoutes.post('/releases', authMiddleware(), adminMiddleware(), async (c) => {
  try {
    const { version_name, version_code, changelog, download_url, file_size, release_url } = await c.req.json();

    if (!version_name || !version_code) {
      return c.json({ error: '版本号和版本代码不能为空' }, 400);
    }

    const result = await c.env.DB.prepare(
      `INSERT INTO app_releases (version_name, version_code, changelog, download_url, file_size, release_url)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(
      version_name,
      version_code,
      changelog || '',
      download_url || '',
      file_size || 0,
      release_url || ''
    ).run();

    const baseUrl = new URL(c.req.url).origin;
    await purgeCache([`${baseUrl}/api/app/check-update`]);
    return c.json({ message: '版本发布成功', id: result.meta.last_row_id }, 201);
  } catch (e: any) {
    console.error('发布失败:', e);
    return c.json({ error: '发布失败' }, 500);
  }
});

// 管理员：获取所有版本列表
appRoutes.get('/releases', authMiddleware(), adminMiddleware(), async (c) => {
  try {
    const result = await c.env.DB.prepare(
      'SELECT * FROM app_releases ORDER BY created_at DESC'
    ).all();
    return c.json({ releases: result.results });
  } catch (e: any) {
    console.error('获取失败:', e);
    return c.json({ error: '获取失败' }, 500);
  }
});

// 管理员：删除版本
appRoutes.delete('/releases/:id', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const result = await c.env.DB.prepare('DELETE FROM app_releases WHERE id = ?').bind(id).run();
  if (result.meta.changes === 0) {
    return c.json({ error: '版本不存在' }, 404);
  }
  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/app/check-update`]);
  return c.json({ message: '已删除' });
});

function isNewerVersion(remote: string, current: string): boolean {
  const r = remote.split('.').map(v => parseInt(v) || 0);
  const cur = current.split('.').map(v => parseInt(v) || 0);
  const len = Math.max(r.length, cur.length);
  for (let i = 0; i < len; i++) {
    const rv = r[i] || 0;
    const cv = cur[i] || 0;
    if (rv > cv) return true;
    if (rv < cv) return false;
  }
  return false;
}
