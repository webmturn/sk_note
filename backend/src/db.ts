import Database from 'better-sqlite3';

/**
 * D1-compatible wrapper around better-sqlite3
 * 
 * Provides the same API as Cloudflare D1 so all route files
 * work identically in both Workers and Node.js environments.
 */

class D1PreparedStatement {
  public _db: Database.Database;
  public _sql: string;
  public _params: any[];

  constructor(db: Database.Database, sql: string) {
    this._db = db;
    this._sql = sql;
    this._params = [];
  }

  bind(...values: any[]): D1PreparedStatement {
    this._params = values;
    return this;
  }

  async first<T = any>(): Promise<T | null> {
    const stmt = this._db.prepare(this._sql);
    const row = this._params.length > 0 ? stmt.get(...this._params) : stmt.get();
    return (row as T) ?? null;
  }

  async all<T = any>(): Promise<{ results: T[] }> {
    const stmt = this._db.prepare(this._sql);
    const rows = this._params.length > 0 ? stmt.all(...this._params) : stmt.all();
    return { results: rows as T[] };
  }

  async run(): Promise<{ meta: { last_row_id: number; changes: number } }> {
    const stmt = this._db.prepare(this._sql);
    const result = this._params.length > 0 ? stmt.run(...this._params) : stmt.run();
    return {
      meta: {
        last_row_id: Number(result.lastInsertRowid),
        changes: result.changes,
      }
    };
  }
}

class D1DatabaseWrapper {
  private db: Database.Database;

  constructor(dbPath: string) {
    this.db = new Database(dbPath);
    this.db.pragma('journal_mode = WAL');
    this.db.pragma('foreign_keys = ON');
  }

  prepare(sql: string): D1PreparedStatement {
    return new D1PreparedStatement(this.db, sql);
  }

  async batch(statements: D1PreparedStatement[]): Promise<{ results: any[] }[]> {
    const batchResults: { results: any[] }[] = [];
    const txn = this.db.transaction(() => {
      for (const stmt of statements) {
        const s = this.db.prepare(stmt._sql);
        const sqlUpper = stmt._sql.trimStart().toUpperCase();
        if (sqlUpper.startsWith('SELECT') || sqlUpper.startsWith('WITH')) {
          const rows = stmt._params.length > 0 ? s.all(...stmt._params) : s.all();
          batchResults.push({ results: rows });
        } else {
          const result = stmt._params.length > 0 ? s.run(...stmt._params) : s.run();
          batchResults.push({ results: [{ last_row_id: Number(result.lastInsertRowid), changes: result.changes }] });
        }
      }
    });
    txn();
    return batchResults;
  }

  close() {
    this.db.close();
  }
}

export function createD1Database(dbPath: string): D1DatabaseWrapper {
  return new D1DatabaseWrapper(dbPath);
}
