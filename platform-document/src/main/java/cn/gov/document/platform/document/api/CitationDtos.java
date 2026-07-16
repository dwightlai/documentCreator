package cn.gov.document.platform.document.api;

import cn.gov.document.platform.document.domain.DocumentCitationEntity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CitationDtos {
    private CitationDtos() { }
    public record CreateRequest(@NotBlank String paragraphId, @NotBlank String materialId, @NotBlank String citationType,
        @NotBlank @Size(max=10000) String citedFragment, @NotNull @Min(0) Integer positionStart,
        @NotNull @Min(1) Integer positionEnd, String displayMode) { }
    public record Response(String id,String documentId,String paragraphId,String materialId,String citationType,
        String citedFragment,Integer positionStart,Integer positionEnd,String displayMode) {
        public static Response from(DocumentCitationEntity e){return new Response(e.getId(),e.getDocumentId(),e.getParagraphId(),
            e.getMaterialId(),e.getCitationType(),e.getCitedFragment(),e.getPositionStart(),e.getPositionEnd(),e.getDisplayMode());}
    }
}
