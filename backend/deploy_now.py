import paramiko
import os

host = '211.159.157.164'
user = 'ubuntu'
password = 'qwe123456...'
remote_base = '/opt/sk-note-backend'

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(host, username=user, password=password)

sftp = ssh.open_sftp()

# 确保远程目录存在（rateLimit.ts 是新文件）
for d in ['src/middleware', 'src/routes']:
    try:
        sftp.stat(remote_base + '/' + d)
    except IOError:
        sftp.mkdir(remote_base + '/' + d)

local_base = r'c:\Users\Administrator\Desktop\laster\sk_note\backend'

files = [
    'src/index.ts',
    'src/server.ts',
    'src/middleware/auth.ts',
    'src/middleware/rateLimit.ts',
    'src/routes/snippets.ts',
    'src/routes/shares.ts',
    'src/routes/discussions.ts',
    'src/routes/discussionCategories.ts',
    'src/routes/auth.ts',
    'src/routes/articles.ts',
    'src/routes/references.ts',
    'src/routes/notifications.ts',
    'src/routes/categories.ts',
    'src/routes/app.ts',
    'src/routes/follows.ts',
    'src/routes/bookmarks.ts',
    'src/routes/aggregated.ts',
    'schema.sql',
    'migrate_discussion_categories.sql',
    'migrate_content_views.sql',
    'migrate_references_columns.sql',
]

for f in files:
    local_path = os.path.join(local_base, f)
    remote_path = remote_base + '/' + f.replace('\\', '/')
    print(f'Uploading {f}...')
    sftp.put(local_path, remote_path)

print('All files uploaded.')
sftp.close()

# Run migrations - upload a temp JS file to avoid quoting issues
sftp2 = ssh.open_sftp()
migrate_js = """const Database = require('better-sqlite3');
const fs = require('fs');
const db = new Database('./data/sk-note.db');
db.pragma('foreign_keys = ON');
try {
  db.exec(fs.readFileSync('./migrate_discussion_categories.sql', 'utf-8'));
  console.log('discussion categories migration done');
} catch(e) {
  console.log('discussion categories: ' + e.message);
}
try {
  db.exec(fs.readFileSync('./migrate_content_views.sql', 'utf-8'));
  console.log('content_views migration done');
} catch(e) {
  console.log('content_views: ' + e.message);
}
try {
  db.exec(fs.readFileSync('./migrate_references_columns.sql', 'utf-8'));
  console.log('references columns migration done');
} catch(e) {
  console.log('references columns: ' + e.message);
}
db.close();
"""
with sftp2.open(remote_base + '/_migrate_tmp.js', 'w') as f:
    f.write(migrate_js)
sftp2.close()

print('Running migrations...')
cmd = f'cd {remote_base} && node _migrate_tmp.js && rm _migrate_tmp.js'
stdin, stdout, stderr = ssh.exec_command(cmd)
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print('STDERR:', err)

# Restart pm2
print('Restarting pm2...')
stdin, stdout, stderr = ssh.exec_command(f'cd {remote_base} && pm2 restart sk-note-api')
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print('PM2 STDERR:', err)

ssh.close()
print('Deploy complete!')
