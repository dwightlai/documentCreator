export type DiffPart = { kind: 'same' | 'added' | 'removed'; text: string }

/**
 * 字符级差异（最长公共子序列），用于候选与原文对比时高亮增删。划词内容不超过 1000 字，候选通常在同一量级，
 * 超出 MAX_CELLS 时退化为整段替换，避免大文本占满主线程。
 */
const MAX_CELLS = 4_000_000

export function diffText(before: string, after: string): DiffPart[] {
  const a = Array.from(before)
  const b = Array.from(after)
  if (a.length * b.length > MAX_CELLS) return merge([{ kind: 'removed', text: before }, { kind: 'added', text: after }])
  const width = b.length + 1
  const lcs = new Uint16Array((a.length + 1) * width)
  for (let i = a.length - 1; i >= 0; i--)
    for (let j = b.length - 1; j >= 0; j--)
      lcs[i * width + j] = a[i] === b[j] ? lcs[(i + 1) * width + j + 1] + 1 : Math.max(lcs[(i + 1) * width + j], lcs[i * width + j + 1])
  const parts: DiffPart[] = []
  let i = 0
  let j = 0
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) { parts.push({ kind: 'same', text: a[i] }); i++; j++ }
    else if (lcs[(i + 1) * width + j] >= lcs[i * width + j + 1]) parts.push({ kind: 'removed', text: a[i++] })
    else parts.push({ kind: 'added', text: b[j++] })
  }
  while (i < a.length) parts.push({ kind: 'removed', text: a[i++] })
  while (j < b.length) parts.push({ kind: 'added', text: b[j++] })
  return merge(parts)
}

function merge(parts: DiffPart[]): DiffPart[] {
  const merged: DiffPart[] = []
  for (const part of parts) {
    if (!part.text) continue
    const last = merged.at(-1)
    if (last?.kind === part.kind) last.text += part.text
    else merged.push({ ...part })
  }
  return merged
}
