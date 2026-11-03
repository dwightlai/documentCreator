<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import {
  createDataDictItem, createDataDictType, deleteDataDictItem, deleteDataDictType,
  listDataDictItems, listDataDictTypes, updateDataDictItem, updateDataDictType,
  type DataDictItem, type DataDictType,
} from '@/api/dataDict'
import { clearDictCache } from '@/composables/useDict'

const loading = ref(false)
const types = ref<DataDictType[]>([])
const items = ref<DataDictItem[]>([])
const selectedTypeCode = ref('')
const typeDialog = ref(false)
const itemDialog = ref(false)
const editingTypeId = ref('')
const editingItemId = ref('')
const typeForm = reactive({ typeCode: '', typeName: '', description: '', sortOrder: 100, enabled: true })
const itemForm = reactive({ itemCode: '', itemLabel: '', sortOrder: 10, enabled: true })
const selectedType = computed(() => types.value.find(item => item.type_code === selectedTypeCode.value))

async function loadTypes() {
  loading.value = true
  try {
    types.value = await listDataDictTypes()
    if (!types.value.some(item => item.type_code === selectedTypeCode.value)) {
      selectedTypeCode.value = types.value[0]?.type_code || ''
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '字典类型加载失败')
  } finally {
    loading.value = false
  }
}

async function loadItems() {
  if (!selectedTypeCode.value) { items.value = []; return }
  try {
    items.value = await listDataDictItems(selectedTypeCode.value, false)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '字典项加载失败')
  }
}

function openType(item?: DataDictType) {
  editingTypeId.value = item?.id || ''
  typeForm.typeCode = item?.type_code || ''
  typeForm.typeName = item?.type_name || ''
  typeForm.description = item?.description || ''
  typeForm.sortOrder = item?.sort_order ?? 100
  typeForm.enabled = item?.enabled ?? true
  typeDialog.value = true
}

function openItem(item?: DataDictItem) {
  if (!selectedTypeCode.value) { ElMessage.warning('请先选择字典类型'); return }
  editingItemId.value = item?.id || ''
  itemForm.itemCode = item?.item_code || ''
  itemForm.itemLabel = item?.item_label || ''
  itemForm.sortOrder = item?.sort_order ?? ((items.value.length + 1) * 10)
  itemForm.enabled = item?.enabled ?? true
  itemDialog.value = true
}

async function saveType() {
  if (!typeForm.typeCode.trim() || !typeForm.typeName.trim()) return ElMessage.warning('请填写类型编码和名称')
  try {
    const payload = {
      typeCode: typeForm.typeCode.trim(),
      typeName: typeForm.typeName.trim(),
      description: typeForm.description.trim() || undefined,
      sortOrder: typeForm.sortOrder,
      enabled: typeForm.enabled,
    }
    if (editingTypeId.value) await updateDataDictType(editingTypeId.value, payload)
    else await createDataDictType(payload)
    typeDialog.value = false
    clearDictCache()
    await loadTypes()
    ElMessage.success('字典类型已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function saveItem() {
  if (!itemForm.itemCode.trim() || !itemForm.itemLabel.trim()) return ElMessage.warning('请填写项编码和名称')
  try {
    const payload = {
      itemCode: itemForm.itemCode.trim(),
      itemLabel: itemForm.itemLabel.trim(),
      sortOrder: itemForm.sortOrder,
      enabled: itemForm.enabled,
    }
    if (editingItemId.value) await updateDataDictItem(editingItemId.value, payload)
    else await createDataDictItem(selectedTypeCode.value, payload)
    itemDialog.value = false
    clearDictCache(selectedTypeCode.value)
    await loadItems()
    ElMessage.success('字典项已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function removeType(item: DataDictType) {
  try {
    await ElMessageBox.confirm(`删除类型「${item.type_name}」将同时删除其全部字典项，确定吗？`, '删除字典类型', { type: 'warning' })
    await deleteDataDictType(item.id)
    clearDictCache()
    await loadTypes()
    ElMessage.success('已删除')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function removeItem(item: DataDictItem) {
  try {
    await ElMessageBox.confirm(`确定删除字典项「${item.item_label}」吗？`, '删除字典项', { type: 'warning' })
    await deleteDataDictItem(item.id)
    clearDictCache(selectedTypeCode.value)
    await loadItems()
    ElMessage.success('已删除')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

watch(selectedTypeCode, () => { void loadItems() })
onMounted(async () => { await loadTypes(); await loadItems() })
</script>

<template>
  <AppShell>
    <template #title>数据字典</template>
    <section class="dict-page" v-loading="loading">
      <header class="dict-hero">
        <div>
          <h2>数据字典</h2>
          <p>维护界面下拉选项。文种列表仍在「文种配置」中管理；审校词库保持独立。</p>
        </div>
        <button class="primary-button" type="button" @click="openType()">新增字典类型</button>
      </header>
      <div class="dict-grid">
        <aside class="dict-types">
          <header><span>字典类型</span><b>{{ types.length }}</b></header>
          <button v-for="item in types" :key="item.id" type="button" :class="{ active: item.type_code === selectedTypeCode }" @click="selectedTypeCode = item.type_code">
            <strong>{{ item.type_name }}</strong>
            <small>{{ item.type_code }} · {{ item.enabled ? '启用' : '停用' }}</small>
          </button>
          <p v-if="!types.length" class="dict-empty">尚无字典类型。</p>
        </aside>
        <main class="dict-items" v-if="selectedType">
          <header>
            <div>
              <h3>{{ selectedType.type_name }}</h3>
              <p>{{ selectedType.type_code }}{{ selectedType.description ? ` · ${selectedType.description}` : '' }}</p>
            </div>
            <div class="dict-actions">
              <button class="secondary-button" type="button" @click="openType(selectedType)">编辑类型</button>
              <button class="secondary-button" type="button" @click="removeType(selectedType)">删除类型</button>
              <button class="primary-button" type="button" @click="openItem()">新增字典项</button>
            </div>
          </header>
          <table>
            <thead><tr><th>编码</th><th>显示名</th><th>排序</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td>{{ item.item_code }}</td>
                <td>{{ item.item_label }}</td>
                <td>{{ item.sort_order }}</td>
                <td>{{ item.enabled ? '启用' : '停用' }}</td>
                <td><button type="button" @click="openItem(item)">编辑</button><button type="button" @click="removeItem(item)">删除</button></td>
              </tr>
            </tbody>
          </table>
          <p v-if="!items.length" class="dict-empty">该类型下暂无字典项。</p>
        </main>
        <main v-else class="dict-items dict-empty-pane">请选择左侧字典类型。</main>
      </div>
    </section>

    <el-dialog v-model="typeDialog" :title="editingTypeId ? '编辑字典类型' : '新增字典类型'" width="520px">
      <form class="dict-form" @submit.prevent="saveType">
        <label>类型编码<input v-model="typeForm.typeCode" required maxlength="50" :disabled="!!editingTypeId"></label>
        <label>类型名称<input v-model="typeForm.typeName" required maxlength="100"></label>
        <label>说明<textarea v-model="typeForm.description" rows="2" maxlength="500" /></label>
        <label>排序<input v-model.number="typeForm.sortOrder" type="number"></label>
        <label class="check"><input v-model="typeForm.enabled" type="checkbox"> 启用</label>
      </form>
      <template #footer>
        <button class="secondary-button" type="button" @click="typeDialog=false">取消</button>
        <button class="primary-button" type="button" @click="saveType">保存</button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialog" :title="editingItemId ? '编辑字典项' : '新增字典项'" width="480px">
      <form class="dict-form" @submit.prevent="saveItem">
        <label>项编码<input v-model="itemForm.itemCode" required maxlength="50"></label>
        <label>显示名<input v-model="itemForm.itemLabel" required maxlength="100"></label>
        <label>排序<input v-model.number="itemForm.sortOrder" type="number"></label>
        <label class="check"><input v-model="itemForm.enabled" type="checkbox"> 启用</label>
      </form>
      <template #footer>
        <button class="secondary-button" type="button" @click="itemDialog=false">取消</button>
        <button class="primary-button" type="button" @click="saveItem">保存</button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<style scoped>
.dict-page{padding:20px 24px 40px}
.dict-hero{display:flex;justify-content:space-between;gap:16px;align-items:flex-start;margin-bottom:16px}
.dict-hero h2{margin:0;font:600 22px/1.3 "Noto Sans SC",sans-serif}
.dict-hero p{margin:8px 0 0;color:#6f6a62;font-size:13px;max-width:640px}
.dict-grid{display:grid;grid-template-columns:280px minmax(0,1fr);gap:16px;min-height:520px}
.dict-types,.dict-items{background:#fff;border:1px solid #e4dfd6;border-radius:10px;padding:14px}
.dict-types header,.dict-items>header{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:12px}
.dict-types button{display:block;width:100%;text-align:left;border:1px solid transparent;background:transparent;border-radius:8px;padding:10px;margin-bottom:6px;cursor:pointer}
.dict-types button.active,.dict-types button:hover{border-color:#e6bbb5;background:#fff5f3}
.dict-types strong{display:block;font-size:14px}
.dict-types small{color:#888}
.dict-items h3{margin:0;font-size:18px}
.dict-items p{margin:6px 0 0;color:#777;font-size:12px}
.dict-actions{display:flex;flex-wrap:wrap;gap:8px}
.dict-items table{width:100%;border-collapse:collapse}
.dict-items th,.dict-items td{border-bottom:1px solid #eee;padding:10px 8px;text-align:left;font-size:13px}
.dict-items td button{margin-right:8px;border:0;background:none;color:#a33c32;cursor:pointer}
.dict-empty,.dict-empty-pane{color:#918b82;font-size:13px;padding:24px 8px;text-align:center}
.dict-form{display:grid;gap:12px}
.dict-form label{display:grid;gap:6px;font-size:13px;color:#555}
.dict-form input,.dict-form textarea{border:1px solid #d8d3c8;border-radius:6px;padding:8px 10px;font:inherit}
.dict-form .check{display:flex;align-items:center;gap:8px}
@media (max-width:900px){.dict-grid{grid-template-columns:1fr}}
</style>
