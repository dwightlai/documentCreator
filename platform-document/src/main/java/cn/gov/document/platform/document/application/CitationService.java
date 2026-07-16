package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.api.CitationDtos;
import cn.gov.document.platform.document.domain.DocumentCitationEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.document.persistence.DocumentCitationMapper;
import cn.gov.document.platform.writing.application.WritingMaterialService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;

@Service
public class CitationService {
    private final DocumentCitationMapper mapper; private final DocumentService documentService; private final WritingMaterialService materialService;
    public CitationService(DocumentCitationMapper mapper,DocumentService documentService,WritingMaterialService materialService){this.mapper=mapper;this.documentService=documentService;this.materialService=materialService;}

    @Transactional
    public CitationDtos.Response create(String taskId,CitationDtos.CreateRequest request){
        DocumentService.ExportData data=documentService.exportData(taskId);
        DocumentParagraphEntity paragraph=data.paragraphs().stream().filter(p->p.getId().equals(request.paragraphId())).findFirst()
            .orElseThrow(()->BusinessException.notFound("当前文稿版本中不存在该段落"));
        materialService.requireUsable(taskId,request.materialId());
        if(request.positionEnd()<=request.positionStart()||request.positionEnd()>paragraph.getPlainText().length())
            throw BusinessException.unprocessable("引用字符区间无效");
        String positioned=paragraph.getPlainText().substring(request.positionStart(),request.positionEnd());
        if("原文摘录".equals(request.citationType())&&!positioned.equals(request.citedFragment()))
            throw BusinessException.unprocessable("原文摘录必须与段落定位内容一致");
        DocumentCitationEntity e=new DocumentCitationEntity(); e.setTenantId(RequestContext.tenantId());
        e.setDocumentId(data.document().getId());e.setParagraphId(paragraph.getId());e.setMaterialId(request.materialId());
        e.setCitationType(request.citationType());e.setCitedFragment(request.citedFragment());e.setPositionStart(request.positionStart());
        e.setPositionEnd(request.positionEnd());e.setDisplayMode(StringUtils.hasText(request.displayMode())?request.displayMode():"角标");mapper.insert(e);
        return CitationDtos.Response.from(e);
    }
    public List<CitationDtos.Response> list(String taskId){DocumentService.ExportData data=documentService.exportData(taskId);
        if(data.paragraphs().isEmpty()) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<DocumentCitationEntity>().eq(DocumentCitationEntity::getDocumentId,data.document().getId())
            .in(DocumentCitationEntity::getParagraphId,data.paragraphs().stream().map(DocumentParagraphEntity::getId).toList())
            .orderByAsc(DocumentCitationEntity::getParagraphId).orderByAsc(DocumentCitationEntity::getPositionStart)).stream().map(CitationDtos.Response::from).toList();}
    @Transactional public void delete(String taskId,String citationId){DocumentService.ExportData data=documentService.exportData(taskId);DocumentCitationEntity e=mapper.selectById(citationId);
        if(e==null||!data.document().getId().equals(e.getDocumentId()))throw BusinessException.notFound("引用不存在");mapper.deleteById(citationId);}
}
