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

    if (!version_name || version_code === undefined || version_code === null || version_code === '') {
      return c.json({ error: '版本号和版本代码不能为空' }, 400);
    }

    // 去除前导 v/V，确保 DB 中保存的版本号能被字符串数字比较器（isNewerVersion）正确解析
    const normalizedVersionName = String(version_name).trim().replace(/^[vV]/, '');
    if (!normalizedVersionName) {
      return c.json({ error: '版本号不能为空' }, 400);
    }
    if (!/^\d+(\.\d+)*$/.test(normalizedVersionName)) {
      return c.json({ error: '版本号格式无效，请使用如 1.2.3 的形式' }, 400);
    }

    const versionCodeNum = Number.parseInt(String(version_code), 10);
    if (!Number.isInteger(versionCodeNum) || versionCodeNum <= 0) {
      return c.json({ error: '版本代码必须是正整数' }, 400);
    }

    const fileSizeNum = file_size === undefined || file_size === null || file_size === ''
      ? 0
      : Number.parseInt(String(file_size), 10);
    if (!Number.isInteger(fileSizeNum) || fileSizeNum < 0) {
      return c.json({ error: '文件大小必须是非负整数（字节）' }, 400);
    }

    // 拒绝重复 version_name，避免 admin 连发两次同号版本造成列表脏数据
    const duplicate = await c.env.DB.prepare(
      'SELECT id FROM app_releases WHERE version_name = ? LIMIT 1'
    ).bind(normalizedVersionName).first();
    if (duplicate) {
      return c.json({ error: `版本号 ${normalizedVersionName} 已存在，请先删除旧记录或换号` }, 409);
    }

    const result = await c.env.DB.prepare(
      `INSERT INTO app_releases (version_name, version_code, changelog, download_url, file_size, release_url)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(
      normalizedVersionName,
      versionCodeNum,
      changelog || '',
      download_url || '',
      fileSizeNum,
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

// 管理员：上架 / 下架版本（保留记录但不让客户端拉取）
appRoutes.put('/releases/:id/active', authMiddleware(), adminMiddleware(), async (c) => {
  const id = c.req.param('id');
  const idNum = Number.parseInt(id, 10);
  if (!Number.isInteger(idNum) || idNum <= 0) {
    return c.json({ error: '无效的版本ID' }, 400);
  }

  const body = await c.req.json().catch(() => ({}));
  const active = body.is_active === undefined ? 1 : (body.is_active ? 1 : 0);

  const existing = await c.env.DB.prepare(
    'SELECT id FROM app_releases WHERE id = ?'
  ).bind(idNum).first();
  if (!existing) return c.json({ error: '版本不存在' }, 404);

  await c.env.DB.prepare(
    'UPDATE app_releases SET is_active = ? WHERE id = ?'
  ).bind(active, idNum).run();

  const baseUrl = new URL(c.req.url).origin;
  await purgeCache([`${baseUrl}/api/app/check-update`]);
  return c.json({ message: active ? '已上架' : '已下架', is_active: active });
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
