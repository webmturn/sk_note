/**
 * LIKE 模式转义：用户搜索词中的 %、_、\ 不再被当作 SQL 通配符。
 * 需配合 `LIKE ? ESCAPE '\'` 使用。
 */
export function escapeLike(input: string): string {
  return input.replace(/[\\%_]/g, (m) => '\\' + m);
}
