package cn.gov.document.platform.document.application;

import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class HtmlExporter {
    public byte[] export(DocumentEntity document, List<DocumentParagraphEntity> paragraphs) {
        StringBuilder html = new StringBuilder(
                        "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>")
                .append(HtmlUtils.htmlEscape(document.getTitle()))
                .append(
                        "</title><style>body{max-width:820px;margin:64px auto;padding:0 48px;color:#111;font-family:'Noto Serif CJK SC','SimSun',serif}h1{text-align:center;font-size:30px;line-height:1.5;margin:0 0 42px}p{font-size:21px;line-height:1.9;text-indent:2em;margin:0 0 12px;overflow-wrap:anywhere}li p,td p,th p{text-indent:0;margin:0}table{width:100%;border-collapse:collapse;margin:0 0 12px}td,th{border:1px solid #333;padding:6px 10px;font-size:18px;vertical-align:top}footer{margin-top:54px;padding-top:12px;border-top:1px solid #bbb;color:#777;font:12px sans-serif;text-align:right}@media print{body{margin:0 auto}footer{position:running(footer)}}</style></head><body><main><h1>")
                .append(HtmlUtils.htmlEscape(document.getTitle()))
                .append("</h1>");
        for (DocumentParagraphEntity paragraph : paragraphs) {
            if (paragraph.getRichContent() != null) {
                html.append(RichContent.toHtml(RichContent.parse(paragraph.getRichContent())));
                continue;
            }
            html.append("<p>")
                    .append(HtmlUtils.htmlEscape(paragraph.getPlainText() == null ? "" : paragraph.getPlainText()))
                    .append("</p>");
        }
        html.append("</main><footer>AI 辅助生成 · 文稿版本 V")
                .append(document.getCurrentVersion())
                .append("</footer></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }
}
