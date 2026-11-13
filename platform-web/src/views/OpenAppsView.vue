<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import { addOpenAppGrant, createOpenApp, createOpenAppKey, deleteOpenApp, deleteOpenAppGrant, deleteOpenAppKey, getOpenApp, listOpenApps, setOpenAppEnabled, type OpenApp } from '@/api/openApps'

const loading = ref(false)
const apps = ref<OpenApp[]>([])
const creating = ref(false)
const form = reactive({ appCode: '', appName: '' })
const current = ref<OpenApp | null>(null)
const configOpen = ref(false)
const secret = ref('')
const scopeType = ref('TENANT')
const scopeCode = ref('writing')
const scopes = [
  ['writing', '写作'],
  ['summary', '摘要'],
  ['polish', '润色'],
  ['expand', '扩写'],
  ['review', '审校'],
] as const

const grantLabel = (type: string, code: string) => type === 'TENANT' ? '全部开放能力' : scopes.find(item => item[0] === code)?.[1] || code

async function load() {
  loading.value = true
  try { apps.value = await listOpenApps() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '加载失败') }
  finally { loading.value = false }
}

async function save() {
  try {
    const created = await createOpenApp(form.appCode.trim(), form.appName.trim())
    creating.value = false
    form.appCode = ''
    form.appName = ''
    await load()
    await open(created.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
}

async function open(id: string) {
  try { current.value = await getOpenApp(id); configOpen.value = true }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '加载应用失败') }
}

async function toggle(app: OpenApp) {
  try {
    await setOpenAppEnabled(app.id, app.status !== 'ACTIVE')
    await load()
    if (current.value?.id === app.id) await open(app.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '操作失败') }
}

async function remove(app: OpenApp) {
  try {
    await ElMessageBox.confirm(`删除应用 ${app.app_name}？已发出的密钥立即失效。`, '确认删除', { type: 'warning' })
    await deleteOpenApp(app.id)
    if (current.value?.id === app.id) { current.value = null; configOpen.value = false }
    await load()
  } catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

async function issueKey() {
  if (!current.value) return
  try {
    const created = await createOpenAppKey(current.value.id)
    secret.value = created.apiKey
    await open(current.value.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '生成失败') }
}

async function removeKey(keyId: string) {
  if (!current.value) return
  try {
    await deleteOpenAppKey(current.value.id, keyId)
    await open(current.value.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

async function addGrant() {
  if (!current.value) return
  try {
    await addOpenAppGrant(current.value.id, scopeType.value, scopeType.value === 'TENANT' ? '' : scopeCode.value)
    await open(current.value.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '添加失败') }
}

async function removeGrant(grantId: string) {
  if (!current.value) return
  try {
    await deleteOpenAppGrant(current.value.id, grantId)
    await open(current.value.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

async function copySecret() {
  await navigator.clipboard.writeText(secret.value)
  ElMessage.success('已复制')
}

const count = computed(() => apps.value.length)
onMounted(load)
</script>

<template>
  <AppShell>
    <template #title>第三方应用</template>
    <section class="open-apps" v-loading="loading">
      <div class="open-apps-head">
        <p>管理本租户的开放 API 接入应用、密钥与白名单。</p>
        <button type="button" @click="load">刷新</button>
      </div>
      <div class="open-apps-count"><span>应用数量</span><strong>{{ count }}</strong></div>
      <div class="open-apps-panel">
        <div class="open-apps-bar">
          <p>接入流程：新增应用 → 点击「配置 API Key 与白名单」→ 生成密钥并选择可调用的开放能力</p>
          <button class="primary-button" type="button" @click="creating = true">新增应用</button>
        </div>
        <table>
          <thead><tr><th>应用</th><th>类型</th><th>认证</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="app in apps" :key="app.id">
              <td><strong>{{ app.app_name }}</strong><small>{{ app.app_code }}</small></td>
              <td>业务系统</td>
              <td>API Key</td>
              <td><span :class="app.status === 'ACTIVE' ? 'on' : 'off'">{{ app.status === 'ACTIVE' ? '启用' : '停用' }}</span></td>
              <td>
                <button type="button" @click="open(app.id)">配置 API Key 与白名单</button>
                <button type="button" @click="toggle(app)">{{ app.status === 'ACTIVE' ? '停用' : '启用' }}</button>
                <button type="button" class="danger" @click="remove(app)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!apps.length" class="empty">暂无应用</p>
      </div>
      <el-dialog v-model="creating" title="新增第三方应用" width="480px">
        <p class="hint">保存后将自动打开应用配置，可在其中生成 API Key 并设置白名单。</p>
        <form class="open-form" @submit.prevent="save">
          <label>应用编码<input v-model="form.appCode" required maxlength="32" pattern="[A-Za-z][A-Za-z0-9_-]{1,31}" placeholder="例如 OA"></label>
          <label>应用名称<input v-model="form.appName" required maxlength="100"></label>
          <label>应用类型<select disabled><option>业务系统</option></select></label>
          <label>认证方式<select disabled><option>API Key</option></select></label>
          <footer><button type="button" @click="creating = false">取消</button><button class="primary-button" type="submit">保存</button></footer>
        </form>
      </el-dialog>
      <el-drawer v-model="configOpen" :title="current ? `应用配置：${current.app_name}` : '应用配置'" size="560px">
        <template v-if="current">
          <div class="hint">
            <strong>第三方系统调用开放 API 时使用以下请求头</strong>
            <p>X-Open-App-Code = {{ current.app_code }}</p>
            <p>X-Open-Api-Key = 下方生成的密钥</p>
            <p>X-Open-User-Id = 第三方用户 ID（可选，用于审计）</p>
            <p>X-Open-Unit-Id、X-Open-Department-Id、X-Open-Role-Ids 可选</p>
          </div>
          <dl class="meta">
            <div><dt>应用编码</dt><dd>{{ current.app_code }}</dd></div>
            <div><dt>认证方式</dt><dd>API Key</dd></div>
            <div><dt>状态</dt><dd>{{ current.status === 'ACTIVE' ? '启用' : '停用' }}</dd></div>
            <div><dt>类型</dt><dd>业务系统</dd></div>
          </dl>
          <h3>API Key</h3>
          <button class="primary-button" type="button" @click="issueKey">生成 API Key</button>
          <small>密钥只展示一次，请立即保存。</small>
          <table>
            <thead><tr><th>前缀</th><th>状态</th><th>创建时间</th><th></th></tr></thead>
            <tbody>
              <tr v-for="item in current.keys || []" :key="item.id">
                <td>{{ item.key_prefix }}…</td><td>启用</td><td>{{ item.created_at }}</td>
                <td><button type="button" class="danger" @click="removeKey(item.id)">删除</button></td>
              </tr>
            </tbody>
          </table>
          <h3>能力白名单</h3>
          <p class="note">未配置时无法调用开放 API。选择「全部开放能力」后可调用本租户全部开放接口。</p>
          <div class="grant-row">
            <select v-model="scopeType" aria-label="范围类型">
              <option value="TENANT">全部开放能力</option>
              <option value="CAPABILITY">指定能力</option>
            </select>
            <select v-if="scopeType === 'CAPABILITY'" v-model="scopeCode" aria-label="开放能力">
              <option v-for="item in scopes" :key="item[0]" :value="item[0]">{{ item[1] }}</option>
            </select>
            <button type="button" @click="addGrant">添加到白名单</button>
          </div>
          <ul>
            <li v-for="item in current.grants || []" :key="item.id">{{ grantLabel(item.scope_type, item.scope_code) }}<button type="button" @click="removeGrant(item.id)">移除</button></li>
          </ul>
        </template>
      </el-drawer>
      <el-dialog :model-value="!!secret" title="API Key 只展示一次" width="520px" :close-on-click-modal="false" @close="secret = ''">
        <p class="hint">请立即复制保存。关闭后无法再次查看完整密钥。</p>
        <input class="secret" :value="secret" readonly>
        <footer class="open-form"><button type="button" @click="copySecret">复制</button><button class="primary-button" type="button" @click="secret = ''">我已保存</button></footer>
      </el-dialog>
    </section>
  </AppShell>
</template>

<style scoped>
.open-apps { padding: 20px 24px; }
.open-apps-head, .open-apps-bar, .grant-row, .open-form footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.open-apps-head p, .note, .hint, small { color: var(--muted); }
.open-apps-count { width: 180px; margin: 12px 0; padding: 16px; background: #fff; border: 1px solid var(--line); border-radius: 10px; }
.open-apps-count span { display: block; color: var(--muted); }
.open-apps-count strong { font-size: 28px; }
.open-apps-panel, .hint, .meta { background: #fff; border: 1px solid var(--line); border-radius: 10px; }
.open-apps-panel { padding: 16px; }
.open-apps-bar p { margin: 0; padding: 8px 12px; background: #e8f3ff; border-radius: 8px; }
table { width: 100%; border-collapse: collapse; margin-top: 12px; }
th, td { padding: 12px; border-bottom: 1px solid var(--line); text-align: left; }
td small { display: block; color: var(--muted); }
td button, .grant-row button, li button { margin-right: 10px; border: 0; background: none; color: var(--red); cursor: pointer; }
.on { color: #067647; } .off { color: var(--muted); }
.danger { color: var(--red); }
.empty { text-align: center; color: var(--muted); }
.hint { margin-bottom: 16px; padding: 12px; background: #e8f3ff; }
.hint p, .meta { margin: 4px 0; }
.meta { display: grid; grid-template-columns: 1fr 1fr; padding: 12px; margin-bottom: 16px; }
.open-form { display: grid; gap: 14px; }
.open-form label { display: grid; gap: 6px; }
.open-form input, .open-form select, .grant-row select, .secret { padding: 9px; border: 1px solid var(--line); border-radius: 6px; }
.secret { width: 100%; margin: 8px 0 16px; }
ul { list-style: none; padding: 0; }
li { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid var(--line); }
</style>
