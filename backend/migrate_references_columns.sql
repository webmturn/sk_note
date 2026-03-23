-- 为 references_doc 表添加缺失的列（color, shape, spec, code）
ALTER TABLE references_doc ADD COLUMN color TEXT DEFAULT '';
ALTER TABLE references_doc ADD COLUMN shape TEXT DEFAULT 's';
ALTER TABLE references_doc ADD COLUMN spec TEXT DEFAULT '';
ALTER TABLE references_doc ADD COLUMN code TEXT DEFAULT '';
