# SkNote - Sketchware-Pro 手册 App

一个为 Sketchware-Pro 社区打造的手册 + 讨论 App。

## 架构

```
sk_note/
├── backend/          # Node.js API 后端（自托管 Ubuntu）
│   ├── src/
│   │   ├── index.ts              # 路由挂载 + Hono 应用配置
│   │   ├── server.ts             # Node.js 服务入口（@hono/node-server）
│   │   ├── db.ts                 # better-sqlite3 D1 兼容层
│   │   ├── middleware/
│   │   │   ├── auth.ts           # JWT 认证中间件
│   │   │   └── cache.ts          # 缓存中间件
│   │   └── routes/
│   │       ├── auth.ts                  # 注册/登录/用户信息/修改密码/图片上传/管理员
│   │       ├── articles.ts              # 文章 CRUD + 搜索 + 点赞
│   │       ├── categories.ts            # 手册分类管理
│   │       ├── discussionCategories.ts  # 讨论分类管理
│   │       ├── discussions.ts           # 讨论/评论/评论点赞/通知
│   │       ├── follows.ts               # 关注/粉丝/公开资料/用户作品
│   │       ├── shares.ts                # 分享 CRUD + 点赞 + 下载计数
│   │       ├── references.ts            # 参考手册
│   │       ├── notifications.ts         # 通知系统
│   │       ├── snippets.ts              # 代码片段 CRUD + 点赞
│   │       ├── bookmarks.ts             # 收藏 + 阅读历史
│   │       ├── aggregated.ts            # 合并首页/统计接口
│   │       └── app.ts                   # 应用更新检查与发布
│   ├── schema.sql                # SQLite 数据库表结构
│   ├── ecosystem.config.cjs      # PM2 进程管理配置
│   └── package.json
│
├── android/          # Android App (Kotlin)
│   ├── app/src/main/
│   │   ├── kotlin/com/sknote/app/
│   │   │   ├── SkNoteApp.kt             # Application
│   │   │   ├── MainActivity.kt          # 主界面 + 底部导航
│   │   │   ├── data/
│   │   │   │   ├── api/ApiService.kt     # Retrofit API 定义
│   │   │   │   ├── api/ApiClient.kt      # 网络客户端
│   │   │   │   ├── local/TokenManager.kt # 本地 Token 管理
│   │   │   │   └── model/               # 数据模型
│   │   │   ├── ui/
│   │   │   │   ├── home/                 # 首页（分类 + 文章列表）
│   │   │   │   ├── article/              # 文章详情（Markdown 渲染）
│   │   │   │   ├── discussion/           # 讨论列表 + 评论点赞
│   │   │   │   ├── auth/                 # 登录/注册/编辑资料
│   │   │   │   ├── profile/              # 个人资料/公开资料/关注列表
│   │   │   │   ├── notification/         # 通知列表
│   │   │   │   ├── reference/            # 参考手册
│   │   │   │   ├── snippet/              # 代码片段列表 + 详情
│   │   │   │   ├── share/                # 分享列表 + 详情
│   │   │   │   ├── bookmark/             # 收藏 + 阅读历史
│   │   │   │   ├── search/               # 搜索
│   │   │   │   └── admin/                # 管理后台
│   │   │   └── util/                     # 工具类
│   │   └── res/                          # 布局 + 资源文件
│   └── build.gradle.kts
│
└── README.md
```

## 技术栈

### 后端
- **Node.js** - 运行时
- **Hono** - 轻量 Web 框架
- **better-sqlite3** - SQLite 数据库
- **PM2** - 进程管理
- **Nginx** - 反向代理 + SSL
- **JWT** - 用户认证

### Android App
- **Kotlin** - 开发语言
- **Material Design 3** - UI 框架
- **Retrofit + OkHttp** - 网络请求
- **Navigation Component** - 页面导航
- **Markwon** - Markdown 渲染
- **Glide** - 图片加载
- **DataStore** - 本地存储
- **Coroutines + ViewModel** - 异步 + MVVM

## 部署步骤

### 1. 当前生产部署

当前线上环境为自托管部署：

- 后端目录：`/opt/sk-note-backend`
- 数据库目录：`/opt/sk-note-backend/data`
- PM2 进程名：`sk-note-api`
- 域名：`https://api.wsqh.cn`
- 反向代理：`Nginx`

### 2. 初始化新服务器（Ubuntu 22.04+）

```bash
sudo apt update
sudo apt install -y curl git build-essential python3 nginx certbot python3-certbot-nginx

curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
sudo npm install -g pm2

sudo mkdir -p /opt/sk-note-backend/data /opt/sk-note-backend/logs
sudo chown -R $USER:$USER /opt/sk-note-backend
```

### 3. 上传并启动后端

可先把 `backend/` 目录上传到服务器临时目录，再复制到正式目录：

```bash
rsync -av backend/ ubuntu@your-server:/tmp/backend/
```

登录服务器后执行：

```bash
rsync -av /tmp/backend/ /opt/sk-note-backend/
cd /opt/sk-note-backend
npm install

cp .env.example .env
# 编辑 .env，至少配置以下变量
# PORT=3000
# JWT_SECRET=你的随机密钥
# DB_PATH=./data/sk-note.db
# CORS_ORIGINS=https://wsqh.cn,https://www.wsqh.cn

pm2 start ecosystem.config.cjs
pm2 save
```

### 4. 迁移已有生产数据

如果是从旧服务器迁移，请优先迁移以下内容：

- `/opt/sk-note-backend/data/`
- `/opt/sk-note-backend/.env`

建议流程：

```bash
# 旧服务器停应用，避免 SQLite 双写
pm2 stop sk-note-api

# 备份数据库和环境文件
tar czf /tmp/sk-note-backend-data.tar.gz -C /opt/sk-note-backend data .env

# 传到新服务器后恢复
tar xzf /tmp/sk-note-backend-data.tar.gz -C /opt/sk-note-backend
```

恢复后在新服务器重启：

```bash
cd /opt/sk-note-backend
pm2 restart ecosystem.config.cjs --update-env
```

数据库结构迁移与维护脚本建议按以下顺序执行：

```bash
cd /opt/sk-note-backend

# 先备份数据库
cp data/sk-note.db data/sk-note.db.bak.$(date +%Y%m%d%H%M%S)

# 结构迁移：讨论分类表与讨论分类约束
sqlite3 data/sk-note.db < migrate_discussion_categories.sql

# 结构迁移：通知 related_type 支持 share
sqlite3 data/sk-note.db < migrate_notifications_share.sql

# 数据维护：清理过期浏览去重记录和超量阅读历史，可按月执行
sqlite3 data/sk-note.db < migrate_cleanup_old_data.sql
```

其中 `migrate_discussion_categories.sql` 与 `migrate_notifications_share.sql` 属于结构迁移，建议只在升级时执行；`migrate_cleanup_old_data.sql` 是周期性维护脚本，可重复执行。

### 5. 配置域名与 HTTPS

`api.wsqh.cn` 当前直接指向自托管服务器（DNS A 记录到源站 IP，未使用 CDN 代理）。完成服务启动后使用 certbot 签发 Let’s Encrypt 证书：

```bash
sudo certbot --nginx -d api.wsqh.cn
```

Nginx 反代需将 `api.wsqh.cn` 反向代理到 `127.0.0.1:3000`，例配置位于 `/etc/nginx/sites-available/sk-note-api`。

### 6. 构建 Android App

```bash
cd android

# 当前默认 API 地址已经指向生产域名 https://api.wsqh.cn

./gradlew assembleDebug
./gradlew installDebug
```

### 7. 创建管理员账号

注册一个账号后，通过 SQLite 手动提升为管理员：

```bash
cd /opt/sk-note-backend
sqlite3 data/sk-note.db "UPDATE users SET role = 'admin' WHERE username = 'your_username';"
```

## API 接口

### 首页与统计

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/home | 首页数据（分类 + 最新文章 + 最新分享） | 公开 |
| GET | /api/stats | 管理统计（未读通知/文章/讨论/片段/用户/分享数） | 登录 |

### 认证与用户

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/auth/register | 注册（支持昵称） | 公开 |
| POST | /api/auth/login | 登录 | 公开 |
| GET | /api/auth/me | 当前用户信息 | 登录 |
| PUT | /api/auth/me | 更新个人信息（昵称/头像/简介/用户名） | 登录 |
| PUT | /api/auth/password | 修改密码 | 登录 |
| POST | /api/auth/image/upload | 上传内容图片（ImgBB 代理，≤1MB） | 登录 |
| POST | /api/auth/avatar/upload | 上传头像图片（与 image/upload 同逻辑） | 登录 |
| GET | /api/auth/users | 用户列表 | 管理员 |
| PUT | /api/auth/users/:id/role | 修改用户角色 | 管理员 |
| PUT | /api/auth/users/:id/password | 重置用户密码 | 管理员 |
| DELETE | /api/auth/users/:id | 删除用户 | 管理员 |

### 分类与讨论分类

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/categories | 手册分类列表 | 公开 |
| GET | /api/categories/:id | 手册分类详情 | 公开 |
| POST | /api/categories | 创建手册分类 | 管理员 |
| PUT | /api/categories/:id | 更新手册分类 | 管理员 |
| DELETE | /api/categories/:id | 删除手册分类 | 管理员 |
| GET | /api/discussion-categories | 讨论分类列表 | 公开 |
| GET | /api/discussion-categories/:id | 讨论分类详情 | 公开 |
| POST | /api/discussion-categories | 创建讨论分类 | 管理员 |
| PUT | /api/discussion-categories/:id | 更新讨论分类 | 管理员 |
| DELETE | /api/discussion-categories/:id | 删除讨论分类（需无被使用） | 管理员 |

### 文章

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/articles | 文章列表（分页/分类/搜索） | 公开 |
| GET | /api/articles/:id | 文章详情（未发布仅编辑/管理员可见） | 公开 |
| POST | /api/articles | 创建文章 | 编辑/管理员 |
| PUT | /api/articles/:id | 更新文章 | 作者/管理员 |
| DELETE | /api/articles/:id | 删除文章 | 作者/管理员 |
| POST | /api/articles/:id/like | 点赞/取消点赞 | 登录 |

### 讨论与评论

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/discussions | 讨论列表（分页/分类/关联文章/搜索） | 公开 |
| GET | /api/discussions/:id | 讨论详情（含评论） | 公开 |
| POST | /api/discussions | 发起讨论 | 登录 |
| PUT | /api/discussions/:id | 编辑讨论 | 作者/管理员 |
| DELETE | /api/discussions/:id | 删除讨论 | 作者/编辑/管理员 |
| POST | /api/discussions/:id/comments | 回复讨论（自动通知） | 登录 |
| DELETE | /api/discussions/:id/comments/:cid | 删除评论 | 作者/编辑/管理员 |
| POST | /api/discussions/:id/comments/:cid/like | 评论点赞/取消 | 登录 |

### 参考手册

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/references | 参考条目列表（类型/分类/搜索） | 公开 |
| GET | /api/references/:id | 参考条目详情 | 公开 |
| POST | /api/references | 创建参考条目 | 管理员 |
| PUT | /api/references/:id | 更新参考条目 | 管理员 |
| DELETE | /api/references/:id | 删除参考条目 | 管理员 |

### 代码片段

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/snippets | 片段列表（分页/分类/搜索） | 公开 |
| GET | /api/snippets/categories | 片段分类统计 | 公开 |
| GET | /api/snippets/:id | 片段详情 | 公开 |
| POST | /api/snippets | 创建片段 | 登录 |
| PUT | /api/snippets/:id | 更新片段 | 作者/管理员 |
| DELETE | /api/snippets/:id | 删除片段 | 作者/管理员 |
| POST | /api/snippets/:id/like | 片段点赞/取消 | 登录 |

### 文件分享

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/shares | 分享列表（分页/分类/搜索） | 公开 |
| GET | /api/shares/categories | 分享分类统计 | 公开 |
| GET | /api/shares/:id | 分享详情 | 公开 |
| POST | /api/shares | 创建分享 | 登录 |
| PUT | /api/shares/:id | 更新分享 | 作者/管理员 |
| DELETE | /api/shares/:id | 删除分享 | 作者/管理员 |
| POST | /api/shares/:id/like | 分享点赞/取消 | 登录 |
| POST | /api/shares/:id/download | 记录下载次数（去重） | 公开 |

### 关注与公开资料

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/follows/:userId | 关注/取消关注 | 登录 |
| GET | /api/follows/check/:userId | 检查是否已关注 | 登录 |
| GET | /api/follows/:userId/following | 关注列表 | 公开 |
| GET | /api/follows/:userId/followers | 粉丝列表 | 公开 |
| GET | /api/follows/profile/:userId | 用户公开资料与作品统计 | 公开 |
| GET | /api/follows/profile/:userId/discussions | 用户讨论列表 | 公开 |
| GET | /api/follows/profile/:userId/snippets | 用户片段列表 | 公开 |
| GET | /api/follows/profile/:userId/shares | 用户分享列表 | 公开 |

### 收藏与阅读历史

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/bookmarks | 收藏列表 | 登录 |
| GET | /api/bookmarks/check/:articleId | 检查是否已收藏 | 登录 |
| POST | /api/bookmarks/:articleId | 添加/取消收藏 | 登录 |
| GET | /api/bookmarks/history | 阅读历史列表 | 登录 |
| POST | /api/bookmarks/history/:articleId | 记录阅读历史 | 登录 |
| DELETE | /api/bookmarks/history | 清空阅读历史 | 登录 |

### 通知

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/notifications | 通知列表 | 登录 |
| GET | /api/notifications/unread-count | 未读通知数 | 登录 |
| PUT | /api/notifications/:id/read | 标记已读 | 登录 |
| PUT | /api/notifications/read-all | 全部已读 | 登录 |
| DELETE | /api/notifications/:id | 删除单条通知 | 登录 |
| DELETE | /api/notifications/all | 清空所有通知 | 登录 |

### 应用更新

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/app/check-update | 检查应用更新 | 公开 |
| GET | /api/app/releases | 版本列表 | 管理员 |
| POST | /api/app/releases | 发布新版本 | 管理员 |
| DELETE | /api/app/releases/:id | 删除版本 | 管理员 |

## License

MIT
