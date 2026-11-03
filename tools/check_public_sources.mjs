/** Monitor only registered public government pages; never publish remote text. */
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const packPath = resolve(root, 'platform-bootstrap/src/main/resources/public-data/public-source-pack.json');
const defaultState = resolve(root, 'data/public-source-monitor/state.json');
const defaultReport = resolve(root, 'data/public-source-monitor/report.json');
const maxBytes = 2 * 1024 * 1024;

export function allowedUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password && (!url.port || url.port === '443') &&
      (url.hostname === 'gov.cn' || url.hostname.endsWith('.gov.cn'));
  } catch { return false; }
}

function textFromHtml(html) {
  const withoutHidden = html.replace(/<(script|style|noscript)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, ' ');
  return withoutHidden.replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;|&#160;/gi, ' ').replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"').replace(/&#39;/gi, "'")
    .replace(/\s+/g, ' ').trim();
}

export async function fetchVisibleText(value) {
  if (!allowedUrl(value)) throw new Error(`非公开政府站点：${value}`);
  let url = value;
  const registeredHost = new URL(value).hostname;
  for (let redirects = 0; redirects <= 3; redirects++) {
    const response = await fetch(url, {
      redirect: 'manual', signal: AbortSignal.timeout(15000),
      headers: { 'user-agent': 'DocumentCreatorPublicSourceMonitor/1.0' },
    });
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      const next = new URL(response.headers.get('location') ?? '', url).toString();
      if (!allowedUrl(next) || new URL(next).hostname !== registeredHost)
        throw new Error(`重定向离开登记的公开站点：${next}`);
      url = next;
      continue;
    }
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const contentType = response.headers.get('content-type') ?? '';
    if (!/^text\/(html|plain)\b/i.test(contentType)) throw new Error(`不支持的内容类型：${contentType}`);
    const reader = response.body.getReader();
    const chunks = [];
    let length = 0;
    try {
      while (true) {
        const { done, value: chunk } = await reader.read();
        if (done) break;
        length += chunk.length;
        if (length > maxBytes) throw new Error('页面超过 2MB 限制');
        chunks.push(chunk);
      }
    } finally { reader.releaseLock(); }
    const bytes = Buffer.concat(chunks);
    const charset = /charset=([\w-]+)/i.exec(contentType)?.[1] ?? 'utf-8';
    const decoded = new TextDecoder(charset, { fatal: false }).decode(bytes);
    return contentType.includes('html') ? textFromHtml(decoded) : decoded.replace(/\s+/g, ' ').trim();
  }
  throw new Error('重定向次数过多');
}

export async function inspect(pack, state = {}, fetcher = fetchVisibleText, pauseMs = 0) {
  const urls = new Map();
  for (const category of pack.categories) {
    for (const entry of category.entries) {
      if (!allowedUrl(entry.url)) throw new Error(`清单包含非公开政府站点：${entry.url}`);
      if (!urls.has(entry.url)) urls.set(entry.url, []);
      urls.get(entry.url).push({ id: entry.id, title: entry.title, checkText: entry.checkText ?? entry.title });
    }
  }
  state.sources ??= {};
  const results = [];
  for (const [url, entries] of urls) {
    const result = { url, entries };
    try {
      const body = await fetcher(url);
      if (body.length < 30) throw new Error('页面文字过短，无法可靠比较');
      if (!entries.some(entry => body.includes(entry.checkText)))
        throw new Error('页面缺少登记的核验文字，请检查页面是否可公开访问');
      const digest = createHash('sha256').update(body, 'utf8').digest('hex');
      const baseline = state.sources[url]?.baselineSha256;
      result.observedSha256 = digest;
      result.baselineSha256 = baseline ?? digest;
      result.status = !baseline ? '基线建立' : digest === baseline ? '未变更' : '待复核';
      state.sources[url] = { baselineSha256: baseline ?? digest, lastObservedSha256: digest };
    } catch (error) {
      result.status = '访问失败';
      result.error = String(error.message ?? error);
    }
    results.push(result);
    if (pauseMs > 0) await new Promise(done => setTimeout(done, pauseMs));
  }
  return { state, report: { packVersion: pack.version, totalUrls: urls.size, results } };
}

async function readJson(path, fallback) {
  try { return JSON.parse(await readFile(path, 'utf8')); }
  catch (error) { if (error.code === 'ENOENT') return fallback; throw error; }
}

async function main() {
  const options = Object.fromEntries(process.argv.slice(2).map((item, index, all) =>
    item.startsWith('--') ? [item.slice(2), all[index + 1]] : null).filter(Boolean));
  const statePath = options.state ?? defaultState;
  const reportPath = options.report ?? defaultReport;
  const pack = await readJson(options.pack ?? packPath, null);
  if (!pack) throw new Error('来源包不存在');
  const state = await readJson(statePath, {});
  if (options['accept-url']) {
    const url = options['accept-url'];
    if (!allowedUrl(url) || !pack.categories.some(c => c.entries.some(e => e.url === url)))
      throw new Error('只能接受来源包中登记的公开政府页面');
    const record = state.sources?.[url];
    if (!record?.lastObservedSha256) throw new Error('没有可接受的核验结果');
    record.baselineSha256 = record.lastObservedSha256;
    await mkdir(dirname(statePath), { recursive: true });
    await writeFile(statePath, JSON.stringify(state, null, 2) + '\n');
    console.log(`已更新人工核验基线：${url}`);
    return;
  }
  const result = await inspect(pack, state, fetchVisibleText, 1000);
  await mkdir(dirname(statePath), { recursive: true });
  await mkdir(dirname(reportPath), { recursive: true });
  await writeFile(statePath, JSON.stringify(result.state, null, 2) + '\n');
  await writeFile(reportPath, JSON.stringify(result.report, null, 2) + '\n');
  const counts = Object.fromEntries(['基线建立', '未变更', '待复核', '访问失败']
    .map(status => [status, result.report.results.filter(item => item.status === status).length]));
  console.log(JSON.stringify({ urls: result.report.totalUrls, ...counts, report: reportPath }));
  if (counts['待复核'] || counts['访问失败']) process.exitCode = 1;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(error); process.exitCode = 1; });
}
