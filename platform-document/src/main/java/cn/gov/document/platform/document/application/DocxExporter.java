package cn.gov.document.platform.document.application;

import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFComments;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

@Component
public class DocxExporter {
    private static final String COMMENT_AUTHOR = "政文智枢·内容来源";

    public byte[] export(DocumentEntity document, List<DocumentParagraphEntity> paragraphs) {
        return export(document, paragraphs, null, false);
    }

    /**
     * §33 双轨标识：AI 参与生成的段落附加批注说明来源类型和材料 ID；文档自定义属性写入 AI 生成占比、模型版本和生成记录数。
     *
     * @param disclosure 为 null 时不写入 AI 标识属性
     * @param sourceComments 是否为 AI 参与生成的段落添加来源批注
     */
    public byte[] export(
            DocumentEntity document,
            List<DocumentParagraphEntity> paragraphs,
            DocumentService.AiDisclosure disclosure,
            boolean sourceComments) {
        try (XWPFDocument word = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = word.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText(document.getTitle());
            titleRun.setBold(true);
            titleRun.setFontFamily("方正小标宋简体");
            titleRun.setFontSize(22);
            XWPFComments comments = sourceComments ? word.createComments() : null;
            int commentId = 0;
            for (DocumentParagraphEntity item : paragraphs) {
                XWPFParagraph paragraph = word.createParagraph();
                paragraph.setFirstLineIndent(840);
                paragraph.setSpacingBetween(1.75);
                String note = comments == null ? null : sourceNote(item);
                BigInteger id = BigInteger.valueOf(commentId);
                if (note != null) paragraph.getCTP().addNewCommentRangeStart().setId(id);
                XWPFRun run = paragraph.createRun();
                run.setText(item.getPlainText() == null ? "" : item.getPlainText());
                run.setFontFamily("仿宋_GB2312");
                run.setFontSize(16);
                if (note != null) {
                    paragraph.getCTP().addNewCommentRangeEnd().setId(id);
                    paragraph.createRun().getCTR().addNewCommentReference().setId(id);
                    XWPFComment comment = comments.createComment(id);
                    comment.setAuthor(COMMENT_AUTHOR);
                    comment.createParagraph().createRun().setText(note);
                    commentId++;
                }
            }
            var properties = word.getProperties().getCustomProperties();
            properties.addProperty("ai_assisted", "true");
            properties.addProperty("document_version", document.getCurrentVersion());
            if (disclosure != null) {
                properties.addProperty(
                        "ai_generated_ratio", disclosure.aiGeneratedRatio().toPlainString());
                properties.addProperty("model_versions", String.join(",", disclosure.modelVersions()));
                properties.addProperty("generation_records_count", String.valueOf(disclosure.generationRecordsCount()));
            }
            word.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("DOCX 导出失败", exception);
        }
    }

    /** 只为 AI 参与的段落添加批注；人工录入段落不打标，避免干扰正式文稿阅读。 */
    private static String sourceNote(DocumentParagraphEntity paragraph) {
        String origin = paragraph.getContentOrigin();
        if (origin == null || "人工录入".equals(origin)) return null;
        String materials = paragraph.getSourceMaterialIds() == null
                        || paragraph.getSourceMaterialIds().isBlank()
                        || "[]".equals(paragraph.getSourceMaterialIds())
                ? "无"
                : paragraph.getSourceMaterialIds().replaceAll("[\\[\\]\"]", "");
        return "来源类型：" + origin + "；证据层级：" + paragraph.getContentSourceLevel() + "；引用材料：" + materials;
    }
}
