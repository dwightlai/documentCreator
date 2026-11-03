<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import { deleteIdentity, listOrganizations, listRoles, listUsers, saveOrganization, saveRole, saveUser, type Organization, type Role, type User } from '@/api/identityDirectory'

type Section = 'users' | 'organizations' | 'roles'
const section = ref<Section>('users')
const loading = ref(false)
const organizations = ref<Organization[]>([])
const roles = ref<Role[]>([])
const users = ref<User[]>([])
const editingId = ref('')
const dialog = ref(false)
const form = reactive({ code: '', name: '', description: '', parentId: '', orgId: '', enabled: true, roleIds: [] as string[] })

async function load() {
  loading.value = true
  try {
    ;[organizations.value, roles.value, users.value] = await Promise.all([listOrganizations(), listRoles(), listUsers()])
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '身份目录加载失败') }
  finally { loading.value = false }
}

function open(item?: Organization | Role | User) {
  editingId.value = item?.id || ''
  form.code = 'username' in (item || {}) ? (item as User).username : 'org_code' in (item || {}) ? (item as Organization).org_code : 'role_code' in (item || {}) ? (item as Role).role_code : ''
  form.name = 'display_name' in (item || {}) ? (item as User).display_name : 'org_name' in (item || {}) ? (item as Organization).org_name : 'role_name' in (item || {}) ? (item as Role).role_name : ''
  form.description = 'description' in (item || {}) ? (item as Role).description || '' : ''
  form.parentId = 'parent_id' in (item || {}) ? (item as Organization).parent_id || '' : ''
  form.orgId = 'org_id' in (item || {}) ? (item as User).org_id || '' : ''
  form.enabled = item?.enabled ?? true
  form.roleIds = 'roleIds' in (item || {}) ? [...((item as User).roleIds || [])] : []
  dialog.value = true
}

async function save() {
  if (!form.code.trim() || !form.name.trim()) return ElMessage.warning('请填写编码和名称')
  try {
    if (section.value === 'organizations') await saveOrganization({ orgCode: form.code.trim(), orgName: form.name.trim(), parentId: form.parentId || null, enabled: form.enabled }, editingId.value || undefined)
    else if (section.value === 'roles') await saveRole({ roleCode: form.code.trim(), roleName: form.name.trim(), description: form.description, enabled: form.enabled }, editingId.value || undefined)
    else await saveUser({ username: form.code.trim(), displayName: form.name.trim(), orgId: form.orgId || null, roleIds: form.roleIds, enabled: form.enabled }, editingId.value || undefined)
    dialog.value = false
    ElMessage.success('已保存')
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
}

async function remove(id: string) {
  try {
    await ElMessageBox.confirm('确定删除这条记录？已被引用的组织和角色不能删除。', '确认删除', { type: 'warning' })
    await deleteIdentity(section.value, id)
    ElMessage.success('已删除')
    await load()
  } catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

function orgName(id?: string) { return organizations.value.find(item => item.id === id)?.org_name || '未分配' }
function roleNames(ids: string[]) { return ids?.map(id => roles.value.find(role => role.id === id)?.role_name).filter(Boolean).join('、') || '未分配' }
onMounted(load)
</script>

<template>
  <AppShell>
    <template #title>用户与权限</template>
    <section class="identity-page" v-loading="loading">
      <div class="identity-toolbar">
        <nav aria-label="身份目录"><button v-for="item in ([['users','用户'],['organizations','组织'],['roles','角色']] as const)" :key="item[0]" type="button" :class="{ active: section === item[0] }" @click="section = item[0]">{{ item[1] }}</button></nav>
        <button class="primary-button" type="button" @click="open()">新增{{ section === 'users' ? '用户' : section === 'organizations' ? '组织' : '角色' }}</button>
      </div>
      <div class="identity-table">
        <table v-if="section === 'users'"><thead><tr><th>账号</th><th>姓名</th><th>所属组织</th><th>角色</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in users" :key="item.id"><td>{{ item.username }}</td><td>{{ item.display_name }}</td><td>{{ orgName(item.org_id) }}</td><td>{{ roleNames(item.roleIds) }}</td><td>{{ item.enabled ? '启用' : '停用' }}</td><td><button @click="open(item)">编辑</button><button @click="remove(item.id)">删除</button></td></tr></tbody></table>
        <table v-else-if="section === 'organizations'"><thead><tr><th>编码</th><th>组织名称</th><th>上级组织</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in organizations" :key="item.id"><td>{{ item.org_code }}</td><td>{{ item.org_name }}</td><td>{{ orgName(item.parent_id) }}</td><td>{{ item.enabled ? '启用' : '停用' }}</td><td><button @click="open(item)">编辑</button><button @click="remove(item.id)">删除</button></td></tr></tbody></table>
        <table v-else><thead><tr><th>角色编码</th><th>角色名称</th><th>说明</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in roles" :key="item.id"><td>{{ item.role_code }}</td><td>{{ item.role_name }}</td><td>{{ item.description }}</td><td>{{ item.enabled ? '启用' : '停用' }}</td><td><button @click="open(item)">编辑</button><button @click="remove(item.id)">删除</button></td></tr></tbody></table>
        <p v-if="section === 'users' && !users.length || section === 'organizations' && !organizations.length || section === 'roles' && !roles.length" class="identity-empty">暂无记录，点击右上角新增。</p>
      </div>
      <el-dialog v-model="dialog" :title="`${editingId ? '编辑' : '新增'}${section === 'users' ? '用户' : section === 'organizations' ? '组织' : '角色'}`" width="520px">
        <form class="identity-form" @submit.prevent="save">
          <label>{{ section === 'users' ? '账号' : '编码' }}<input v-model="form.code" required maxlength="100"></label>
          <label>{{ section === 'users' ? '姓名' : '名称' }}<input v-model="form.name" required maxlength="200"></label>
          <label v-if="section === 'organizations'">上级组织<select v-model="form.parentId"><option value="">无</option><option v-for="item in organizations.filter(org => org.id !== editingId)" :key="item.id" :value="item.id">{{ item.org_name }}</option></select></label>
          <label v-if="section === 'users'">所属组织<select v-model="form.orgId"><option value="">未分配</option><option v-for="item in organizations" :key="item.id" :value="item.id">{{ item.org_name }}</option></select></label>
          <fieldset v-if="section === 'users'"><legend>分配角色</legend><label v-for="item in roles" :key="item.id" class="identity-check"><input v-model="form.roleIds" type="checkbox" :value="item.id">{{ item.role_name }}</label></fieldset>
          <label v-if="section === 'roles'">说明<textarea v-model="form.description" maxlength="500" rows="3" /></label>
          <label class="identity-check"><input v-model="form.enabled" type="checkbox">启用</label>
          <footer><button type="button" @click="dialog = false">取消</button><button class="primary-button" type="submit">保存</button></footer>
        </form>
      </el-dialog>
    </section>
  </AppShell>
</template>

<style scoped>
.identity-page{padding:20px 24px}.identity-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}.identity-toolbar nav{display:flex;gap:8px}.identity-toolbar nav button{border:0;background:transparent;padding:10px 18px;cursor:pointer}.identity-toolbar nav button.active{color:var(--red);border-bottom:2px solid var(--red);font-weight:700}.identity-table{background:#fff;border:1px solid var(--line);border-radius:10px;overflow:hidden}.identity-table table{width:100%;border-collapse:collapse;text-align:left}.identity-table th,.identity-table td{padding:14px 18px;border-bottom:1px solid var(--line)}.identity-table th{background:#f8f9fb;color:var(--muted)}.identity-table td button{margin-right:12px;border:0;background:none;color:var(--red);cursor:pointer}.identity-empty{padding:30px;text-align:center;color:var(--muted)}.identity-form{display:grid;gap:16px}.identity-form>label{display:grid;gap:6px}.identity-form input:not([type=checkbox]),.identity-form select,.identity-form textarea{padding:9px;border:1px solid var(--line);border-radius:6px}.identity-form fieldset{border:1px solid var(--line);display:flex;gap:14px;flex-wrap:wrap}.identity-form .identity-check{display:flex;align-items:center;gap:6px}.identity-form footer{display:flex;justify-content:flex-end;gap:10px}.identity-form footer button{padding:8px 16px;cursor:pointer}
</style>
