import { http, type ApiEnvelope } from './http'
import type { AsyncJob } from './jobs'
export interface KnowledgeSource { id:string;sourceCode:string;sourceName:string;category:string;storageType:string;entryCount:number;active:boolean }
export interface KnowledgeDocument { id:string;sourceId:string;documentTitle:string;originalFileHash:string;parsedStatus:string;authorityLevel:string;permissionLevel:string;chunkCount:number;documentVersion:number }
export interface KnowledgeHit { sourceId:string;sourceName:string;documentId:string;documentTitle:string;chunkId:string;chunkOrder:number;snippet:string;authorityLevel:string;permissionLevel:string;score:number;fileHash:string }
const data=async<T>(request:Promise<{data:ApiEnvelope<T>}>)=>(await request).data.data
export const listKnowledgeSources=()=>data(http.get<ApiEnvelope<KnowledgeSource[]>>('/knowledge/sources'))
export const createKnowledgeSource=(payload:{sourceCode:string;sourceName:string;category:string;storageType:string;permissionScope:string})=>data(http.post<ApiEnvelope<KnowledgeSource>>('/knowledge/sources',payload))
export const listKnowledgeDocuments=(sourceId:string)=>data(http.get<ApiEnvelope<KnowledgeDocument[]>>(`/knowledge/sources/${sourceId}/documents`))
export const uploadKnowledgeDocument=(sourceId:string,file:File,fields:{title:string;authorityLevel:string;permissionLevel:string})=>{const form=new FormData();form.append('file',file);Object.entries(fields).forEach(([key,value])=>form.append(key,value));return data(http.post<ApiEnvelope<KnowledgeDocument>>(`/knowledge/sources/${sourceId}/documents/upload`,form,{headers:{'Content-Type':'multipart/form-data'}}))}
export const parseKnowledgeDocument=(sourceId:string,documentId:string)=>data(http.post<ApiEnvelope<AsyncJob>>(`/knowledge/sources/${sourceId}/documents/${documentId}/parse-async`,undefined,{headers:{'Idempotency-Key':`knowledge-parse-${documentId}`}}))
export const searchKnowledge=(q:string,sourceId?:string)=>data(http.get<ApiEnvelope<KnowledgeHit[]>>('/knowledge/search',{params:{q,sourceId:sourceId||undefined}}))
