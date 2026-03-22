const Database = require('better-sqlite3');
const db = new Database('./data/sk-note.db');
try {
  db.exec("ALTER TABLE users ADD COLUMN bio TEXT DEFAULT ''");
  console.log('Migration successful: bio column added');
} catch(e) {
  console.log('Migration result:', e.message);
}
db.close();
