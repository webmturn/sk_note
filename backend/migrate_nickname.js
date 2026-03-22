// Migration: Add nickname column to users table
// Run: node migrate_nickname.js

const Database = require('better-sqlite3');
const path = require('path');

const dbPath = path.join(__dirname, 'data', 'sk-note.db');
const db = new Database(dbPath);

console.log('Adding nickname column to users table...');

try {
  // Check if column already exists
  const columns = db.pragma('table_info(users)');
  const hasNickname = columns.some(col => col.name === 'nickname');
  
  if (hasNickname) {
    console.log('nickname column already exists, skipping ALTER.');
  } else {
    db.exec("ALTER TABLE users ADD COLUMN nickname TEXT DEFAULT ''");
    console.log('nickname column added.');
  }

  // Backfill: set nickname = username for existing users where nickname is empty
  const result = db.prepare("UPDATE users SET nickname = username WHERE nickname IS NULL OR nickname = ''").run();
  console.log(`Backfilled ${result.changes} users with nickname = username.`);

  console.log('Migration complete!');
} catch (e) {
  console.error('Migration failed:', e.message);
  process.exit(1);
} finally {
  db.close();
}
