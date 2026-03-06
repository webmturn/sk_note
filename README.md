# SkNote - Sketchware-Pro 手册 App

一个为 Sketchware-Pro 社区打造的手册 + 讨论 App。

## 架构

```
sk_note/
├── backend/          # Cloudflare Workers + D1 (API 后端)
│   ├── src/
│   │   ├── index.ts              # 入口，路由挂载 + 合并接口
│   │   ├── middleware/
│   │   │   ├── auth.ts           # JWT 认证中间件
│   │   │   └── cache.ts          # 边缘缓存中间件
│   │   └── routes/
│   │       ├── auth.ts           # 注册/登录/用户信息/修改密码
│   │       ├── articles.ts       # 文章 CRUD + 搜索 + 点赞
│   │       ├── categories.ts     # 分类管理
│   │       ├── discussions.ts    # 讨论/评论/评论点赞
│   │       ├── references.ts     # 参考手册
│   │       ├── notifications.ts  # 通知系统
│   │       ├── snippets.ts       # 代码片段 CRUD + 点赞
│   │       └── bookmarks.ts      # 收藏 + 阅读历史
│   ├── schema.sql                # D1 数据库表结构
│   ├── wrangler.toml             # Cloudflare 配置
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
│   │   │   │   └── model/Models.kt       # 数据模型
│   │   │   └── ui/
│   │   │       ├── home/                 # 首页（分类 + 文章列表）
│   │   │       ├── article/              # 文章详情（Markdown 渲染）
│   │   │       ├── discussion/           # 讨论列表 + 评论点赞
│   │   │       ├── auth/                 # 登录/注册/编辑资料
│   │   │       ├── notification/         # 通知列表
│   │   │       ├── reference/            # 参考手册
│   │   │       ├── snippet/              # 代码片段列表 + 详情
│   │   │       ├── bookmark/             # 收藏 + 阅读历史
│   │   │       ├── search/               # 搜索
│   │   │       └── admin/                # 管理后台
│   │   └── res/                          # 布局 + 资源文件
│   └── build.gradle.kts
│
└── README.md
```

## 技术栈

### 后端
- **Cloudflare Workers** - Serverless 运行时（免费额度：10万请求/天）
- **Cloudflare D1** - SQLite 数据库（免费额度：5GB 存储）
- **Hono** - 轻量 Web 框架
- **JWT** - 用户认证

### Android App
- **Kotlin** - 开发语言
- **Material Design 3** - UI 框架
- **Retrofit + OkHttp** - 网络请求
- **Navigation Component** - 页面导航
- **Markwon** - Markdown 渲染
- **DataStore** - 本地存储
- **Coroutines + ViewModel** - 异步 + MVVM

## 部署步骤

### 1. 部署后端

```bash
# 安装依赖
cd backend
npm install

# 登录 Cloudflare
npx wrangler login

# 创建 D1 数据库
npx wrangler d1 create sk-note-db
# 将返回的 database_id 填入 wrangler.toml

# 设置 JWT 密钥
npx wrangler secret put JWT_SECRET
# 输入一个随机字符串作为密钥

# 初始化数据库表
npx wrangler d1 execute sk-note-db --remote --file=schema.sql

# 部署
npx wrangler deploy
```

部署成功后会得到一个 URL，如：`https://sk-note-api.xxx.workers.dev`

### 2. 构建 Android App

```bash
cd android

# 修改 API 地址
# 编辑 app/build.gradle.kts 中的 API_BASE_URL 为你的 Workers URL

# 用 Android Studio 打开项目并构建
# 或命令行构建：
./gradlew assembleDebug
```

### 3. 创建管理员账号

注册一个账号后，通过 D1 控制台手动提升为管理员：

```sql
UPDATE users SET role = 'admin' WHERE username = 'your_username';
```

## API 接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/home | 首页数据（分类 + 最新文章合并） | 公开 |
| GET | /api/stats | 管理统计（通知/文章/讨论/片段/用户数） | 登录 |
| POST | /api/auth/register | 注册 | 公开 |
| POST | /api/auth/login | 登录 | 公开 |
| GET | /api/auth/me | 当前用户信息 | 登录 |
| PUT | /api/auth/me | 更新个人信息（头像） | 登录 |
| PUT | /api/auth/password | 修改密码 | 登录 |
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
| GET | /api/snippets | 代码片段列表（分页/分类/搜索） | 公开 |
| GET | /api/snippets/categories | 片段分类统计 | 公开 |
| GET | /api/snippets/:id | 代码片段详情 | 公开 |
| POST | /api/snippets | 创建代码片段 | 登录 |
| DELETE | /api/snippets/:id | 删除代码片段 | 作者/管理员 |
| POST | /api/snippets/:id/like | 片段点赞/取消 | 登录 |
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

## Cloudflare 免费额度

| 资源 | 免费额度 |
|------|----------|
| Workers 请求 | 10万次/天 |
| D1 存储 | 5GB |
| D1 读取 | 500万次/天 |
| D1 写入 | 10万次/天 |
| R2 存储（可选） | 10GB |

## License

MIT
