import { Context } from 'hono';
import type { AppEnv } from './index';

type LikeTargetType = 'article' | 'comment' | 'snippet' | 'share';
type LikeCountTable = 'articles' | 'comments' | 'snippets' | 'shares';

interface ToggleLikeOptions {
  userId: number;
  targetId: string;
  targetType: LikeTargetType;
  countTable: LikeCountTable;
  likeSuccessMessage: string;
  unlikeSuccessMessage: string;
}

interface ToggleRelationOptions {
  existsSql: string;
  deleteSql: string;
  insertSql: string;
  bindings: Array<string | number>;
  activateSuccessMessage: string;
  deactivateSuccessMessage: string;
}

const COUNT_TABLES: Record<LikeCountTable, LikeCountTable> = {
  articles: 'articles',
  comments: 'comments',
  snippets: 'snippets',
  shares: 'shares',
};

function recountSql(countTable: LikeCountTable, targetType: LikeTargetType): string {
  const table = COUNT_TABLES[countTable];
  return `UPDATE ${table}
SET like_count = (
  SELECT COUNT(*) FROM likes WHERE target_type = '${targetType}' AND target_id = ?
)
WHERE id = ?`;
}

export async function toggleLike(c: Context<AppEnv>, options: ToggleLikeOptions): Promise<{ liked: boolean; message: string }> {
  const { userId, targetId, targetType, countTable, likeSuccessMessage, unlikeSuccessMessage } = options;

  const existing = await c.env.DB.prepare(
    'SELECT id FROM likes WHERE user_id = ? AND target_type = ? AND target_id = ?'
  ).bind(userId, targetType, targetId).first();

  if (existing) {
    await c.env.DB.prepare(
      'DELETE FROM likes WHERE user_id = ? AND target_type = ? AND target_id = ?'
    ).bind(userId, targetType, targetId).run();

    await c.env.DB.prepare(recountSql(countTable, targetType)).bind(targetId, targetId).run();

    return { liked: false, message: unlikeSuccessMessage };
  }

  await c.env.DB.prepare(
    'INSERT OR IGNORE INTO likes (user_id, target_type, target_id) VALUES (?, ?, ?)'
  ).bind(userId, targetType, targetId).run();

  await c.env.DB.prepare(recountSql(countTable, targetType)).bind(targetId, targetId).run();

  return { liked: true, message: likeSuccessMessage };
}

export async function toggleRelation(c: Context<AppEnv>, options: ToggleRelationOptions): Promise<{ active: boolean; message: string }> {
  const { existsSql, deleteSql, insertSql, bindings, activateSuccessMessage, deactivateSuccessMessage } = options;

  const existing = await c.env.DB.prepare(existsSql).bind(...bindings).first();

  if (existing) {
    await c.env.DB.prepare(deleteSql).bind(...bindings).run();
    return { active: false, message: deactivateSuccessMessage };
  }

  await c.env.DB.prepare(insertSql).bind(...bindings).run();
  return { active: true, message: activateSuccessMessage };
}
