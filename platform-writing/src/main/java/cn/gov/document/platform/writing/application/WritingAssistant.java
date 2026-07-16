package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.writing.api.WritingOutlineDtos.NodeDraft;
import cn.gov.document.platform.writing.api.WritingPlanningDtos.TitleCandidate;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;

import java.util.List;

public interface WritingAssistant {
    List<DocumentTypeSuggestion> recommendDocumentTypes(String purpose);
    List<NodeDraft> generateOutline(WritingTaskEntity task, int materialCount);
    IdeaDraft generateIdea(WritingTaskEntity task, int materialCount);
    List<TitleCandidate> generateTitleCandidates(WritingTaskEntity task);

    record DocumentTypeSuggestion(String documentType, String reason, double confidence) {
    }
    record IdeaDraft(String mainTheme,List<String> coreViewpoints,List<String> writingSequence,
                     String mustExpressFacts,String optionalContent,String missingMaterials,String riskWarnings) { }
}
