# SkNote - Sketchware-Pro 手册 App

一个为 Sketchware-Pro 社区打造的手册 + 讨论 App。

## 架构

```
sk_note/
├── backend/          # Node.js API 后端（自托管服务器）
│   ├── src/
│   │   ├── index.ts              # 路由挂载 + Hono 应用配置
│   │   ├── server.ts             # Node.js 服务入口（@hono/node-server）
│   │   ├── db.ts                 # better-sqlite3 D1 兼容层
│   │   ├── middleware/
│   │   │   ├── auth.ts           # JWT 认证中间件
│   │   │   └── cache.ts          # 缓存中间件
│   │   └── routes/
│   │       ├── auth.ts           # 注册/登录/用户信息/修改密码/管理员
│   │       ├── articles.ts       # 文章 CRUD + 搜索 + 点赞
│   │       ├── categories.ts     # 分类管理
│   │       ├── discussions.ts    # 讨论/评论/评论点赞/通知
│   │       ├── follows.ts        # 关注/粉丝/公开资料/用户作品
│   │       ├── shares.ts         # 分享 CRUD + 点赞
│   │       ├── references.ts     # 参考手册
│   │       ├── notifications.ts  # 通知系统
│   │       ├── snippets.ts       # 代码片段 CRUD + 点赞
│   │       ├── bookmarks.ts      # 收藏 + 阅读历史
│   │       ├── aggregated.ts     # 合并首页接口
│   │       └── app.ts            # 应用更新检查
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

### 5. 配置域名与 HTTPS

`api.wsqh.cn` 当前指向自托管服务器。完成服务启动后：

```bash
sudo certbot --nginx -d api.wsqh.cn
```

建议在 `Cloudflare` 中：

- 先把记录切到新源站
- 证书签发成功后再按需开启代理
- `SSL/TLS` 模式使用 `Full (strict)`

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

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/home | 首页数据（分类 + 最新文章合并） | 公开 |
| GET | /api/stats | 管理统计（通知/文章/讨论/片段/用户数） | 登录 |
| POST | /api/auth/register | 注册（支持昵称） | 公开 |
| POST | /api/auth/login | 登录 | 公开 |
| GET | /api/auth/me | 当前用户信息 | 登录 |
| PUT | /api/auth/me | 更新个人信息（昵称/头像/简介） | 登录 |
| PUT | /api/auth/password | 修改密码 | 登录 |
| GET | /api/auth/users | 用户列表 | 管理员 |
| GET | /api/categories | 分类列表 | 公开 |
| POST | /api/categories | 创建分类 | 管理员 |
| GET | /api/articles | 文章列表（支持分页/搜索） | 公开 |
| GET | /api/articles/:id | 文章详情 | 公开 |
| POST | /api/articles | 创建文章 | 管理员 |
| POST | /api/articles/:id/like | 点赞/取消点赞 | 登录 |
| GET | /api/discussions | 讨论列表 | 公开 |
| GET | /api/discussions/:id | 讨论详情（含评论） | 公开 |
| POST | /api/discussions | 发起讨论 | 登录 |
| POST | /api/discussions/:id/comments | 回复讨论（自动通知） | 登录 |
| POST | /api/discussions/:id/comments/:cid/like | 评论点赞/取消 | 登录 |
| DELETE | /api/discussions/:id | 删除讨论 | 作者/管理员 |
| DELETE | /api/discussions/:id/comments/:cid | 删除评论 | 作者/管理员 |
| GET | /api/snippets | 代码片段列表（分页/分类/搜索） | 公开 |
| GET | /api/snippets/categories | 片段分类统计 | 公开 |
| GET | /api/snippets/:id | 代码片段详情 | 公开 |
| POST | /api/snippets | 创建代码片段 | 登录 |
| DELETE | /api/snippets/:id | 删除代码片段 | 作者/管理员 |
| POST | /api/snippets/:id/like | 片段点赞/取消 | 登录 |
| GET | /api/shares | 分享列表 | 公开 |
| GET | /api/shares/:id | 分享详情 | 公开 |
| POST | /api/shares | 创建分享 | 登录 |
| DELETE | /api/shares/:id | 删除分享 | 作者/管理员 |
| POST | /api/shares/:id/like | 分享点赞/取消 | 登录 |
| POST | /api/follows/:userId | 关注/取消关注 | 登录 |
| GET | /api/follows/check/:userId | 检查是否已关注 | 登录 |
| GET | /api/follows/:userId/following | 关注列表 | 公开 |
| GET | /api/follows/:userId/followers | 粉丝列表 | 公开 |
| GET | /api/follows/profile/:userId | 用户公开资料 | 公开 |
| GET | /api/follows/profile/:userId/discussions | 用户讨论列表 | 公开 |
| GET | /api/follows/profile/:userId/snippets | 用户片段列表 | 公开 |
| GET | /api/follows/profile/:userId/shares | 用户分享列表 | 公开 |
| GET | /api/bookmarks | 收藏列表 | 登录 |
| GET | /api/bookmarks/check/:articleId | 检查是否已收藏 | 登录 |
| POST | /api/bookmarks/:articleId | 添加/取消收藏 | 登录 |
| GET | /api/bookmarks/history | 阅读历史列表 | 登录 |
| POST | /api/bookmarks/history/:articleId | 记录阅读历史 | 登录 |
| DELETE | /api/bookmarks/history | 清空阅读历史 | 登录 |
| GET | /api/notifications | 通知列表 | 登录 |
| GET | /api/notifications/unread-count | 未读通知数 | 登录 |
| PUT | /api/notifications/:id/read | 标记已读 | 登录 |
| PUT | /api/notifications/read-all | 全部已读 | 登录 |
| DELETE | /api/notifications/:id | 删除通知 | 登录 |
| GET | /api/app/check-update | 检查应用更新 | 公开 |

## License

MIT
