-- 为讨论帖添加标签列（逗号分隔），与 snippets.tags 同一做法。
-- 非破坏性：只追加一列，不动任何现有列与数据。
-- 重复执行会报 "duplicate column name: tags"，deploy_now.py 的迁移段落是逐条
-- try/catch，只会打印该信息、不会中断部署，所以这个文件可以一直留在清单里。
ALTER TABLE discussions ADD COLUMN tags TEXT DEFAULT '';
