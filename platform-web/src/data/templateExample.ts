/** 演示文本使用虚构机关、文号和事项，不代表真实发文。 */
export const templateExample = {
  sender: '示范市人民政府',
  docNumber: '示政〔2026〕18号',
  signer: '张某',
  title: '示范市人民政府关于建设一体化政务服务平台的请示',
  recipient: '某省人民政府：',
  paragraphs: [
    '为解决政务服务入口分散、办事材料重复提交、部门间办理进度难以衔接等问题，进一步提高政务服务便利度，我市拟建设一体化政务服务平台。现将有关事项请示如下。',
    '一、建设目标。以群众和企业办事需求为导向，统一网上办事入口、事项清单和办理进度查询方式，推动线上线下服务衔接。平台建设遵循分步实施、集约利用现有资源的原则。',
    '二、主要内容。整合现有政务服务入口，完善统一身份认证、电子材料共享、事项办理和结果反馈等功能；建立跨部门协同办理机制，减少重复填报；同步落实数据分类分级、访问控制和操作留痕要求。',
    '三、实施安排。由市政务服务管理部门牵头，组织有关部门梳理事项和数据需求，形成建设方案、实施计划和安全保障方案。项目实施前按规定履行论证、审批和采购程序，建成后开展试运行和验收。',
    '四、请示事项。恳请省人民政府同意我市按程序推进一体化政务服务平台建设，并指导省级业务系统对接和数据共享。',
  ],
  closing: '妥否，请批示。',
  attachment: '一体化政务服务平台建设方案（示例）',
  date: '2026年7月15日',
  copyTo: '市发展改革委、市财政局、市政务服务管理部门',
  issueUnit: '示范市人民政府办公室',
  issueDate: '2026年7月15日',
} as const

export const templateExampleValues = {
  title: templateExample.title,
  recipient: templateExample.recipient,
  sender: templateExample.sender,
  doc_number: templateExample.docNumber,
  signer: templateExample.signer,
  content: templateExample.paragraphs.join('\n'),
  closing: templateExample.closing,
  attachment_desc: `1. ${templateExample.attachment}`,
  copy_to: templateExample.copyTo,
  issue_unit: templateExample.issueUnit,
  issue_date: templateExample.issueDate,
  date: templateExample.date,
}
