import { http, randomId, type ApiEnvelope } from './http'
import { templateExample, templateExampleValues } from '@/data/templateExample'

export interface OutputTemplate {
  id:string;templateCode:string;templateName:string;documentTypeCode:string;templateFileHash:string
  hasRedHeader:boolean;hasFooterMark:boolean;placeholderMap:Record<string,string>;direction?:string
  description?:string;currentVersion:number;lifecycleStatus:string;lockVersion:number;updatedAt:string
}
export interface TemplateVersion { id:string;versionNumber:number;templateFileHash:string;changeNote:string;lifecycleStatus:string;approvedBy?:string;approvedAt?:string;createdAt:string }
export interface TemplatePreviewBlock { type:'paragraph'|'table';text?:string;align?:string;fontSize?:number;bold:boolean;color?:string;rows?:string[][] }
const data=async<T>(request:Promise<{data:ApiEnvelope<T>}>)=>(await request).data.data
export interface TemplateUsage { kind:string;refId:string;title:string;status:string }
export const listOutputTemplates=()=>data(http.get<ApiEnvelope<OutputTemplate[]>>('/output-templates'))
export const listTemplateVersions=(id:string)=>data(http.get<ApiEnvelope<TemplateVersion[]>>(`/output-templates/${id}/versions`))
export const listTemplateUsages=(id:string)=>data(http.get<ApiEnvelope<TemplateUsage[]>>(`/output-templates/${id}/usages`))
export const previewOutputTemplate=(id:string)=>data(http.get<ApiEnvelope<TemplatePreviewBlock[]>>(`/output-templates/${id}/preview`))
export const downloadOutputTemplate=async(id:string)=>{const response=await http.get<Blob>(`/output-templates/${id}/file`,{responseType:'blob'});return response.data}
export const deleteOutputTemplate=async(id:string)=>{await http.delete(`/output-templates/${id}`)}
export const createOutputTemplate=(file:File,metadata:{templateCode:string;templateName:string;documentTypeCode:string;hasRedHeader:boolean;hasFooterMark:boolean;direction:string;description:string})=>{const form=new FormData();form.append('file',file);form.append('metadata',new Blob([JSON.stringify(metadata)],{type:'application/json'}));return data(http.post<ApiEnvelope<OutputTemplate>>('/output-templates',form,{headers:{'Idempotency-Key':`template-create-${randomId()}`}}))}
export const updateOutputTemplate=(id:string,metadata:Pick<OutputTemplate,'templateName'|'documentTypeCode'|'hasRedHeader'|'hasFooterMark'|'direction'|'description'|'lockVersion'>)=>data(http.put<ApiEnvelope<OutputTemplate>>(`/output-templates/${id}`,metadata))
export const replaceOutputTemplateFile=(id:string,file:File,changeNote:string)=>{const form=new FormData();form.append('file',file);return data(http.put<ApiEnvelope<OutputTemplate>>(`/output-templates/${id}/file`,form,{params:{changeNote}}))}
export const publishOutputTemplate=(id:string)=>data(http.post<ApiEnvelope<OutputTemplate>>(`/output-templates/${id}/publish`,undefined,{headers:{'Idempotency-Key':`template-publish-${id}`}}))
export const testOutputTemplate=async(id:string)=>{const response=await http.post<Blob>(`/output-templates/${id}/test`,{values:templateExampleValues,sections:[...templateExample.paragraphs]},{responseType:'blob',headers:{'Idempotency-Key':`template-test-${randomId()}`}});return response.data}
export const downloadStandardTemplate=async()=>{const response=await http.get<Blob>('/output-templates/standard-template',{responseType:'blob'});return response.data}
export const downloadCompleteExample=async()=>{const response=await http.post<Blob>('/output-templates/complete-example',{values:templateExampleValues,sections:[...templateExample.paragraphs]},{responseType:'blob'});return response.data}
