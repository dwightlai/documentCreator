package cn.gov.document.platform.document.api;

import cn.gov.document.platform.document.application.DocumentService;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** 导出时可选追加的“AI 辅助生成说明”段落（§33）。只影响导出文件，不写入文稿版本。 */
final class DisclosureParagraph {
    private DisclosureParagraph() {}

    static List<DocumentParagraphEntity> append(
            List<DocumentParagraphEntity> paragraphs, DocumentService.AiDisclosure disclosure) {
        if (disclosure.aiGeneratedRatio().signum() == 0) return paragraphs;
        String percent = disclosure
                .aiGeneratedRatio()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
        String models = disclosure.modelVersions().isEmpty() ? "平台规则生成" : String.join("、", disclosure.modelVersions());
        DocumentParagraphEntity statement = new DocumentParagraphEntity();
        statement.setParagraphOrder(paragraphs.size() + 1);
        statement.setContentOrigin("人工录入");
        statement.setContentSourceLevel("手工");
        statement.setPlainText("AI辅助生成说明：本文约 " + percent + "% 的内容由人工智能辅助生成（" + models + "）。");
        List<DocumentParagraphEntity> result = new ArrayList<>(paragraphs);
        result.add(statement);
        return result;
    }
}
