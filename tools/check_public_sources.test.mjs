import { test } from 'node:test';
import assert from 'node:assert/strict';
import { allowedUrl, inspect } from './check_public_sources.mjs';

const url = 'https://example.gov.cn/public/notice.html';
const pack = { version: 'v1', categories: [{ entries: [{ id: 'N-1', title: '公开通知', url }] }] };

test('only government HTTPS URLs are accepted', () => {
  assert.equal(allowedUrl(url), true);
  assert.equal(allowedUrl('http://example.gov.cn/a'), false);
  assert.equal(allowedUrl('https://example.gov.cn.evil.test/a'), false);
  assert.equal(allowedUrl('https://intranet.local/a'), false);
  assert.equal(allowedUrl('https://example.gov.cn:8443/a'), false);
});

test('change remains pending and baseline is not advanced', async () => {
  const first = await inspect(pack, {}, async () => '公开通知 第一版正文，已公开发布，请各单位按照原文要求办理。');
  assert.equal(first.report.results[0].status, '基线建立');
  const baseline = first.state.sources[url].baselineSha256;
  const second = await inspect(pack, first.state, async () => '公开通知 第二版正文，已公开发布，请各单位按照更新后的要求办理。');
  assert.equal(second.report.results[0].status, '待复核');
  assert.equal(second.state.sources[url].baselineSha256, baseline);
});

test('login page is rejected as inaccessible', async () => {
  const result = await inspect(pack, {}, async () => '请登录后查看系统，您没有访问权限。');
  assert.equal(result.report.results[0].status, '访问失败');
});
