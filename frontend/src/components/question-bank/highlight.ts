// 极简手写高亮:不引入 highlight.js/prismjs 这类重型依赖,
// 只覆盖题库场景里出现的几种语言(java/bash/sql/lua/text),够用且可控。

const KEYWORDS: Record<string, string[]> = {
  java: [
    'public', 'private', 'protected', 'static', 'final', 'class', 'interface', 'extends',
    'implements', 'new', 'return', 'if', 'else', 'for', 'while', 'try', 'catch', 'finally',
    'throw', 'throws', 'void', 'synchronized', 'volatile', 'this', 'super', 'import', 'package',
    'true', 'false', 'null',
  ],
  bash: ['if', 'then', 'else', 'fi', 'for', 'do', 'done', 'echo'],
  sql: [
    'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'CREATE', 'TABLE', 'PRIMARY', 'KEY', 'NOT', 'NULL',
    'BETWEEN', 'FOR', 'UPDATE', 'INSERT', 'INTO', 'VALUES',
  ],
  lua: ['if', 'then', 'else', 'end', 'return', 'local', 'function'],
  text: [],
}

const LINE_COMMENT: Record<string, string | null> = {
  java: '//',
  bash: '#',
  sql: '--',
  lua: '--',
  text: null,
}

function escapeHtml(raw: string): string {
  return raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

export function highlightLine(line: string, lang: string): string {
  const commentToken = LINE_COMMENT[lang]
  let code = line
  let comment = ''

  if (commentToken) {
    const idx = code.indexOf(commentToken)
    // 简单起见不处理"字符串里恰好包含注释符号"这种边界情况——mock 代码块不需要那么严谨
    if (idx !== -1) {
      comment = code.slice(idx)
      code = code.slice(0, idx)
    }
  }

  let html = escapeHtml(code)

  // 顺序很重要:关键字/数字必须先处理,字符串最后包裹,
  // 否则字符串 span 的 class="tok-string" 属性文本会被关键字正则二次误伤
  const keywords = KEYWORDS[lang] ?? []
  if (keywords.length) {
    const pattern = new RegExp(`\\b(${keywords.join('|')})\\b`, 'g')
    html = html.replace(pattern, '<span class="tok-keyword">$1</span>')
  }

  html = html.replace(/\b(\d+(\.\d+)?)\b/g, '<span class="tok-number">$1</span>')

  html = html.replace(/"([^"]*)"/g, (m) => `<span class="tok-string">${m}</span>`)
  html = html.replace(/'([^']*)'/g, (m) => `<span class="tok-string">${m}</span>`)

  if (comment) {
    html += `<span class="tok-comment">${escapeHtml(comment)}</span>`
  }

  return html || '&nbsp;'
}
