module.exports = {
  apps: [{
    name: 'sk-note-api',
    script: 'src/server.ts',
    interpreter: 'node',
    interpreter_args: '--import tsx',
    cwd: '/opt/sk-note-backend',
    env: {
      NODE_ENV: 'production',
      PORT: 3000,
    },
    max_memory_restart: '256M',
    error_file: '/opt/sk-note-backend/logs/error.log',
    out_file: '/opt/sk-note-backend/logs/out.log',
    merge_logs: true,
    time: true,
  }]
};
