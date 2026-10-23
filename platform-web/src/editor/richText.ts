import type { JSONContent } from '@tiptap/vue-3'

/**
 * 段落富文本与纯文本的换算，规则与服务端 RichContent 保持一致：块之间换行，列表项带编号前缀，表格单元格以制表符分隔。
 * 服务端保存时会以富文本重新推导纯文本，这里只用于编辑中的字数显示和冲突比对。
 */
export function toEditorDoc(richContent: string | undefined, plainText: string): JSONContent {
  if (richContent) {
    try {
      const parsed = JSON.parse(richContent) as JSONContent
      if (parsed?.type === 'doc') return parsed
    } catch { /* 损坏的富文本按纯文本展示，保存时会重新生成 */ }
  }
  const lines = plainText.split('\n')
  return {
    type: 'doc',
    content: lines.map((line) => (line ? { type: 'paragraph', content: [{ type: 'text', text: line }] } : { type: 'paragraph' })),
  }
}

export function plainTextOf(doc: JSONContent): string {
  const out: string[] = []
  writeBlocks(doc, '\n', '', out)
  return out.join('').trim()
}

function writeBlocks(container: JSONContent, separator: string, firstPrefix: string, out: string[]) {
  ;(container.content ?? []).forEach((child, index) => {
    if (index > 0) out.push(separator)
    writeBlock(child, index === 0 ? firstPrefix : '', out)
  })
}

function writeBlock(node: JSONContent, prefix: string, out: string[]) {
  switch (node.type) {
    case 'paragraph':
      out.push(prefix)
      for (const child of node.content ?? []) out.push(child.type === 'hardBreak' ? '\n' : child.text ?? '')
      break
    case 'bulletList':
    case 'orderedList': {
      const start = Number(node.attrs?.start ?? 1)
      ;(node.content ?? []).forEach((item, index) => {
        if (index > 0) out.push('\n')
        writeBlocks(item, '\n', node.type === 'orderedList' ? `${start + index}. ` : '• ', out)
      })
      break
    }
    case 'table':
      ;(node.content ?? []).forEach((row, rowIndex) => {
        if (rowIndex > 0) out.push('\n')
        ;(row.content ?? []).forEach((cell, cellIndex) => {
          if (cellIndex > 0) out.push('\t')
          writeBlocks(cell, ' ', '', out)
        })
      })
      break
  }
}
