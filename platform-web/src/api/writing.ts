import { http, type ApiEnvelope } from './http'
import { accessToken, usesOidc } from '@/auth/identity'

export interface WritingTask {
  id: string
  taskName: string
  documentTypeCode: string
  documentCategory: string
  writingTopic: string
  writingPurpose: string
  expectedLengthMin: number
  expectedLengthMax: number
  allowExternalKnowledge: boolean
  securityLevel: string
  status: string
  currentDocumentId?: string
  lockVersion?: number
  createdAt: string
  updatedAt: string
}

export interface PageData<T> { items: T[]; page: number; size: number; total: number }
export interface TypeSuggestion { documentType: string; reason: string; confidence: number }
export interface WritingIdea {
  id: string; taskId: string; version: number; mainTheme: string; coreViewpoints: string[]; writingSequence: string[]
  mustExpressFacts?: string; optionalContent?: string; missingMaterials?: string; riskWarnings?: string
  confirmed: boolean; confirmedBy?: string; confirmedAt?: string; lockVersion: number
}
export interface TitleCandidate {
  titleText: string; titleStructure: string; applicableScenario: string; includesIssuingAuthority: boolean
  includesSubject: boolean; includesDocumentType: boolean; riskWarnings: string[]; confidence: number
}
export interface QaSource { sourceId:string;sourceName:string;documentId:string;documentTitle:string;chunkId:string;snippet:string;authorityLevel:string;fileHash:string;score:number }
export interface WritingQa { id:string;taskId:string;question:string;answer:string;sources:QaSource[];answerType:string;inserted:boolean;insertedDocumentId?:string;insertedParagraphId?:string;lockVersion:number;createdAt:string }
export interface WritingMaterial {
  id:string;taskId:string;materialName:string;sourceType:string;sourceId?:string;authorityLevel:string;usageMode:string;citationPurpose:string
  effectiveDate?:string;expiryDate?:string;permissionStatus:string;selectedFragment?:string;locked:boolean;fileHash?:string;filePath?:string;lockVersion:number
}
export interface OutlineNode {
  id:string;parentNodeId?:string;order:number;title:string;purpose?:string;keyFacts?:string;referencedMaterialIds:string
  targetWordCount?:number;required:boolean;generationStatus:string;humanNote?:string;lockVersion:number
}
export interface WritingOutline {
  id:string;taskId:string;version:number;confirmed:boolean;confirmedBy?:string;confirmedAt?:string;structureCheckResult:string;lockVersion:number;nodes:OutlineNode[]
}
export interface DocumentParagraph {
  id:string;outlineNodeId:string;paragraphOrder:number;contentSourceLevel:string;sourceMaterialIds:string[];locked:boolean;plainText:string;lockVersion:number;contentOrigin?:string
}
export interface WritingDocument {
  id:string;taskId:string;title:string;contentType:string;totalWordCount:number;securityLevel:string;currentVersion:number;status:string;paragraphs:DocumentParagraph[];updatedAt:string
}

export type CreateWritingTask = Pick<WritingTask,
  'taskName' | 'documentTypeCode' | 'documentCategory' | 'writingTopic' | 'writingPurpose' |
  'expectedLengthMin' | 'expectedLengthMax' | 'securityLevel'> & { allowExternalKnowledge: boolean }

export async function listWritingTasks() {
  const response = await http.get<ApiEnvelope<PageData<WritingTask>>>('/writing-tasks')
  return response.data.data
}

export async function createWritingTask(payload: CreateWritingTask) {
  const response = await http.post<ApiEnvelope<WritingTask>>('/writing-tasks', payload)
  return response.data.data
}

export async function getWritingTask(id: string) {
  const response = await http.get<ApiEnvelope<WritingTask>>(`/writing-tasks/${id}`)
  return response.data.data
}

export async function recommendDocumentType(id: string) {
  const response = await http.post<ApiEnvelope<TypeSuggestion[]>>(`/writing-tasks/${id}/recommend-type`)
  return response.data.data
}

export async function generateOutline(id: string) {
  const response = await http.post<ApiEnvelope<unknown>>(`/writing-tasks/${id}/outlines`)
  return response.data.data
}

export async function generateWritingIdea(id: string) {
  const response = await http.post<ApiEnvelope<WritingIdea>>(`/writing-tasks/${id}/ideas`)
  return response.data.data
}

export async function confirmWritingIdea(id: string, idea: WritingIdea) {
  const response = await http.put<ApiEnvelope<WritingIdea>>(`/writing-tasks/${id}/ideas/${idea.version}`, {
    mainTheme: idea.mainTheme,
    coreViewpoints: idea.coreViewpoints,
    writingSequence: idea.writingSequence,
    mustExpressFacts: idea.mustExpressFacts,
    optionalContent: idea.optionalContent,
    missingMaterials: idea.missingMaterials,
    riskWarnings: idea.riskWarnings,
    confirmed: true,
    lockVersion: idea.lockVersion,
  })
  return response.data.data
}

export async function generateTitleCandidates(id: string) {
  const response = await http.post<ApiEnvelope<TitleCandidate[]>>(`/writing-tasks/${id}/title-candidates`)
  return response.data.data
}
export async function askWritingQuestion(id:string,question:string){const response=await http.post<ApiEnvelope<WritingQa>>(`/writing-tasks/${id}/qa`,{question});return response.data.data}
export async function insertWritingAnswer(id:string,qaId:string,afterParagraphId?:string){const response=await http.post<ApiEnvelope<unknown>>(`/writing-tasks/${id}/qa/${qaId}/insert`,{afterParagraphId,confirmed:true});return response.data.data}

const data = async <T>(request: Promise<{ data: ApiEnvelope<T> }>) => (await request).data.data
export const listWritingMaterials = (taskId:string) => data(http.get<ApiEnvelope<WritingMaterial[]>>(`/writing-tasks/${taskId}/materials`))
export const createWritingMaterial = (taskId:string,input:{materialName:string;sourceType:string;authorityLevel:string;usageMode:string;citationPurpose:string;permissionStatus:string;selectedFragment:string;locked:boolean}) => data(http.post<ApiEnvelope<WritingMaterial>>(`/writing-tasks/${taskId}/materials`,input))
export const uploadWritingMaterial = (taskId:string,file:File,input:{authorityLevel:string;usageMode:string;citationPurpose:string;permissionStatus:string}) => {
  const body=new FormData();body.append('file',file)
  return data(http.post<ApiEnvelope<WritingMaterial>>(`/writing-tasks/${taskId}/materials/upload`,body,{params:input}))
}
export const deleteWritingMaterial = async (taskId:string,materialId:string) => { await http.delete(`/writing-tasks/${taskId}/materials/${materialId}`) }
export const snapshotWritingMaterials = (taskId:string) => data(http.post<ApiEnvelope<{snapshotVersion:number;allowedCount:number;deniedCount:number}>>(`/writing-tasks/${taskId}/materials/snapshot`))

export const getCurrentOutline = (taskId:string) => data(http.get<ApiEnvelope<WritingOutline>>(`/writing-tasks/${taskId}/outlines/current`))
export const checkOutline = (taskId:string,version:number) => data(http.post<ApiEnvelope<WritingOutline>>(`/writing-tasks/${taskId}/outlines/${version}/structure-check`))
export const confirmOutline = (taskId:string,version:number) => data(http.post<ApiEnvelope<WritingOutline>>(`/writing-tasks/${taskId}/outlines/${version}/confirm`))
export const updateOutlineNode = (taskId:string,version:number,node:OutlineNode) => data(http.put<ApiEnvelope<WritingOutline>>(`/writing-tasks/${taskId}/outlines/${version}/nodes/${node.id}`,{
  order:node.order,title:node.title,purpose:node.purpose,keyFacts:node.keyFacts,referencedMaterialIds:parseReferencedIds(node.referencedMaterialIds),targetWordCount:node.targetWordCount,required:node.required,humanNote:node.humanNote,lockVersion:node.lockVersion,
}))
export const addOutlineNode = (taskId:string,version:number,order:number) => data(http.post<ApiEnvelope<WritingOutline>>(`/writing-tasks/${taskId}/outlines/${version}/nodes`,{order,title:'新增章节',purpose:'请补充本节写作目的',referencedMaterialIds:[],targetWordCount:300,required:false}))
export const deleteOutlineNode = (taskId:string,version:number,nodeId:string) => data(http.delete<ApiEnvelope<WritingOutline>>(`/writing-tasks/${taskId}/outlines/${version}/nodes/${nodeId}`))

export const getWritingDocument = (taskId:string) => data(http.get<ApiEnvelope<WritingDocument>>(`/writing-tasks/${taskId}/document`))
export const generateDocumentSection = (taskId:string,nodeId:string,instruction:string) => data(http.post<ApiEnvelope<WritingDocument>>(`/writing-tasks/${taskId}/generate/section`,{outlineNodeId:nodeId,instruction}))
class StreamServerError extends Error {}

async function recoverPersistedStream(taskId:string,baselineVersion:number,onEvent:(event:string,data:Record<string,unknown>)=>void) {
  const deadline=Date.now()+15_000
  while(Date.now()<deadline){
    try { const document=await getWritingDocument(taskId);if(document.currentVersion>baselineVersion){onEvent('recovered',{documentId:document.id,documentVersion:document.currentVersion});return} }
    catch { /* 生成事务可能仍在执行，继续按固定间隔确认持久化结果。 */ }
    await new Promise(resolve=>setTimeout(resolve,500))
  }
  throw new Error('流式连接中断，且未在 15 秒内确认已持久化的新文稿版本')
}

export async function streamDocumentSection(taskId:string,nodeId:string,instruction:string,onEvent:(event:string,data:Record<string,unknown>)=>void) {
  let baselineVersion=0
  try { baselineVersion=(await getWritingDocument(taskId)).currentVersion } catch { /* 首次生成尚无文稿。 */ }
  const headers:Record<string,string>={'Content-Type':'application/json','X-Request-Id':crypto.randomUUID().replaceAll('-','')}
  if(usesOidc()) headers.Authorization=`Bearer ${await accessToken()}`
  else { headers['X-Tenant-Id']=import.meta.env.VITE_TENANT_ID||'local-government';headers['X-User-Id']=import.meta.env.VITE_USER_ID||'local-user' }
  const base=import.meta.env.VITE_API_BASE_URL||'/api/v1'
  const response=await fetch(`${base}/writing-tasks/${taskId}/generate/section/stream`,{method:'POST',headers,body:JSON.stringify({outlineNodeId:nodeId,instruction})})
  if(!response.ok||!response.body) throw new Error(`流式生成请求失败（HTTP ${response.status}）`)
  const reader=response.body.getReader();const decoder=new TextDecoder();let buffer='';let completed=false
  try {
    while(true){const {done,value}=await reader.read();buffer+=decoder.decode(value,{stream:!done}).replaceAll('\r\n','\n');let boundary=buffer.indexOf('\n\n')
      while(boundary>=0){const block=buffer.slice(0,boundary);buffer=buffer.slice(boundary+2);let event='message';const dataLines:string[]=[]
        for(const line of block.split('\n')){if(line.startsWith('event:'))event=line.slice(6).trim();else if(line.startsWith('data:'))dataLines.push(line.slice(5).trim())}
        if(dataLines.length){let payload:Record<string,unknown>;try{payload=JSON.parse(dataLines.join('\n')) as Record<string,unknown>}catch{payload={text:dataLines.join('\n')}}
          onEvent(event,payload);if(event==='completed')completed=true;if(event==='error')throw new StreamServerError(String(payload.message??'流式生成失败'))}
        boundary=buffer.indexOf('\n\n')
      }
      if(done)break
    }
  } catch(reason) {
    if(reason instanceof StreamServerError)throw reason
    await recoverPersistedStream(taskId,baselineVersion,onEvent)
    return
  }
  if(!completed)await recoverPersistedStream(taskId,baselineVersion,onEvent)
}
export const updateDocumentParagraph = (taskId:string,paragraph:DocumentParagraph) => data(http.put<ApiEnvelope<WritingDocument>>(`/writing-tasks/${taskId}/document/paragraphs/${paragraph.id}`,{plainText:paragraph.plainText,lockVersion:paragraph.lockVersion,changeDescription:'正文工作台人工编辑'}))
export const setParagraphLock = (taskId:string,paragraphId:string,locked:boolean) => data(locked?http.post<ApiEnvelope<WritingDocument>>(`/writing-tasks/${taskId}/document/paragraphs/${paragraphId}/lock`):http.delete<ApiEnvelope<WritingDocument>>(`/writing-tasks/${taskId}/document/paragraphs/${paragraphId}/lock`))
export interface EditLease { paragraphId:string;holderId:string;expiresAt:string }
export const acquireEditLease = (taskId:string,paragraphId:string) => data(http.post<ApiEnvelope<EditLease>>(`/writing-tasks/${taskId}/document/paragraphs/${paragraphId}/edit-lease`))
export const releaseEditLease = (taskId:string,paragraphId:string) => data(http.delete<ApiEnvelope<null>>(`/writing-tasks/${taskId}/document/paragraphs/${paragraphId}/edit-lease`))
export interface DocumentVersion { versionNumber:number;versionType:string;changeSource:string;changeDescription?:string;wordCount:number;createdAt:string;retained:boolean;protectedVersion:boolean;contentPurged:boolean }
export const listDocumentVersions = (taskId:string) => data(http.get<ApiEnvelope<DocumentVersion[]>>(`/writing-tasks/${taskId}/document/versions`))
export const restoreDocumentVersion = (taskId:string,versionNumber:number) => data(http.post<ApiEnvelope<WritingDocument>>(`/writing-tasks/${taskId}/document/versions/${versionNumber}/restore`))
export const protectDocumentVersion = (taskId:string,versionNumber:number,protectedVersion:boolean) => data(http.put<ApiEnvelope<DocumentVersion>>(`/writing-tasks/${taskId}/document/versions/${versionNumber}/protection`,{protectedVersion}))
export interface AiDisclosure { aiGeneratedRatio:number;modelVersions:string[];generationRecordsCount:number }
export const getAiDisclosure = (taskId:string) => data(http.get<ApiEnvelope<AiDisclosure>>(`/writing-tasks/${taskId}/document/ai-disclosure`))
export const downloadWritingDocument = async (taskId:string,format:'DOCX'|'PDF',aiDisclosure=false) => (await http.post(`/writing-tasks/${taskId}/document/export`,{format,aiDisclosure},{responseType:'blob'})).data as Blob

function parseReferencedIds(value:string) {
  try { const parsed:unknown = JSON.parse(value || '[]'); return Array.isArray(parsed) ? parsed.map(String) : [] }
  catch { return [] }
}
