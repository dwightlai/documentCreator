import { http, randomId, type ApiEnvelope } from './http'

export interface OutputTemplate {
  id:string;templateCode:string;templateName:string;documentTypeCode:string;templateFileHash:string
  hasRedHeader:boolean;hasFooterMark:boolean;placeholderMap:Record<string,string>;direction?:string
  description?:string;currentVersion:number;lifecycleStatus:string;lockVersion:number;updatedAt:string
}
export interface TemplateVersion { id:string;versionNumber:number;templateFileHash:string;changeNote:string;lifecycleStatus:string;approvedBy?:string;approvedAt?:string;createdAt:string }
const data=async<T>(request:Promise<{data:ApiEnvelope<T>}>)=>(await request).data.data
export const listOutputTemplates=()=>data(http.get<ApiEnvelope<OutputTemplate[]>>('/output-templates'))
export const listTemplateVersions=(id:string)=>data(http.get<ApiEnvelope<TemplateVersion[]>>(`/output-templates/${id}/versions`))
export const createOutputTemplate=(file:File,metadata:{templateCode:string;templateName:string;documentTypeCode:string;hasRedHeader:boolean;hasFooterMark:boolean;direction:string;description:string})=>{const form=new FormData();form.append('file',file);form.append('metadata',new Blob([JSON.stringify(metadata)],{type:'application/json'}));return data(http.post<ApiEnvelope<OutputTemplate>>('/output-templates',form,{headers:{'Idempotency-Key':`template-create-${randomId()}`}}))}
export const publishOutputTemplate=(id:string)=>data(http.post<ApiEnvelope<OutputTemplate>>(`/output-templates/${id}/publish`,undefined,{headers:{'Idempotency-Key':`template-publish-${id}`}}))
export const testOutputTemplate=async(id:string)=>{const response=await http.post<Blob>(`/output-templates/${id}/test`,{values:{title:'关于推进数字政务平台建设的请示',recipient:'省人民政府：',sender:'示范市人民政府',doc_number:'示政〔2026〕18号',signer:'张某',content:'为提升政务服务质效，现申请建设统一数字政务平台。',closing:'以上请示，妥否，请批示。',attachment_desc:'1. 项目建设方案',copy_to:'市发展改革委',issue_unit:'示范市人民政府办公室',issue_date:'2026年7月15日',date:'2026年7月15日'},sections:[]},{responseType:'blob',headers:{'Idempotency-Key':`template-test-${id}`}});return response.data}
