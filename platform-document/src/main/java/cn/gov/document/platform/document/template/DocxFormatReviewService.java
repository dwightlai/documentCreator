package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.error.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Service;

@Service
public class DocxFormatReviewService {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(?:#|/)?[a-z][a-z0-9_]*}");
    private final OutputTemplateService templateService;

    public DocxFormatReviewService(OutputTemplateService templateService) {
        this.templateService = templateService;
    }

    public Result compare(String templateId, byte[] documentBytes) {
        byte[] templateBytes = templateService.fileContent(templateId);
        try (XWPFDocument standard = new XWPFDocument(new ByteArrayInputStream(templateBytes));
                XWPFDocument actual = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            List<Deviation> deviations = new ArrayList<>();
            CTSectPr standardSection = standard.getDocument().getBody().getSectPr();
            CTSectPr actualSection = actual.getDocument().getBody().getSectPr();
            CTPageSz standardSize = standardSection == null ? null : standardSection.getPgSz();
            CTPageSz actualSize = actualSection == null ? null : actualSection.getPgSz();
            compare(deviations, "PAGE_SIZE", "重要", "页面纸幅", pageSize(standardSize), pageSize(actualSize), null);
            CTPageMar standardMargin = standardSection == null ? null : standardSection.getPgMar();
            CTPageMar actualMargin = actualSection == null ? null : actualSection.getPgMar();
            compare(deviations, "MARGIN_TOP", "重要", "上边距", top(standardMargin), top(actualMargin), null);
            compare(deviations, "MARGIN_BOTTOM", "重要", "下边距", bottom(standardMargin), bottom(actualMargin), null);
            compare(deviations, "MARGIN_LEFT", "重要", "左边距", left(standardMargin), left(actualMargin), null);
            compare(deviations, "MARGIN_RIGHT", "重要", "右边距", right(standardMargin), right(actualMargin), null);

            List<XWPFParagraph> standardParagraphs = meaningful(standard);
            List<XWPFParagraph> actualParagraphs = meaningful(actual);
            XWPFParagraph standardTitle = paragraph(standardParagraphs, 0);
            XWPFParagraph actualTitle = paragraph(actualParagraphs, 0);
            compare(deviations, "TITLE_FONT", "重要", "标题字体", font(standardTitle), font(actualTitle), 1);
            compare(deviations, "TITLE_SIZE", "重要", "标题字号", size(standardTitle), size(actualTitle), 1);
            XWPFParagraph standardBody =
                    paragraph(standardParagraphs, Math.min(2, Math.max(0, standardParagraphs.size() - 1)));
            XWPFParagraph actualBody =
                    paragraph(actualParagraphs, Math.min(2, Math.max(0, actualParagraphs.size() - 1)));
            compare(deviations, "BODY_FONT", "重要", "正文字体", font(standardBody), font(actualBody), 3);
            compare(deviations, "BODY_SIZE", "重要", "正文字号", size(standardBody), size(actualBody), 3);
            compare(deviations, "LINE_SPACING", "一般", "正文行距", spacing(standardBody), spacing(actualBody), 3);
            compare(deviations, "FIRST_LINE_INDENT", "一般", "正文首行缩进", indent(standardBody), indent(actualBody), 3);
            compare(
                    deviations,
                    "HEADER_PRESENCE",
                    "重要",
                    "页眉/版头结构",
                    Boolean.toString(!standard.getHeaderList().isEmpty()),
                    Boolean.toString(!actual.getHeaderList().isEmpty()),
                    null);
            compare(
                    deviations,
                    "FOOTER_PRESENCE",
                    "重要",
                    "页脚/版记结构",
                    Boolean.toString(!standard.getFooterList().isEmpty()),
                    Boolean.toString(!actual.getFooterList().isEmpty()),
                    null);

            List<String> residual = actualParagraphs.stream()
                    .map(XWPFParagraph::getText)
                    .filter(text -> PLACEHOLDER.matcher(text).find())
                    .toList();
            if (!residual.isEmpty()) {
                deviations.add(new Deviation(
                        "PLACEHOLDER_RESIDUE", "严重", "占位符残留", "无占位符", String.join("；", residual), null, "存在未替换的模板占位符"));
            }
            return new Result(templateId, deviations.size(), List.copyOf(deviations));
        } catch (IOException invalidDocument) {
            throw BusinessException.unprocessable("待审文稿不是有效 DOCX 文件");
        }
    }

    private static final Pattern DOC_NUMBER = Pattern.compile("\\p{IsHan}{1,10}〔\\d{4}〕\\d+号");
    private static final Pattern DOC_NUMBER_WRONG_BRACKET =
            Pattern.compile("\\p{IsHan}{1,10}[\\[［(（【]\\d{4}[\\]］)）】]\\d+号");
    private static final Pattern IMPRINT = Pattern.compile("\\d{4}年\\d{1,2}月\\d{1,2}日\\s*印发");
    private static final Pattern CHINESE_DATE =
            Pattern.compile("[〇零一二三四五六七八九]{4}年[一二三四五六七八九十]{1,2}月[一二三四五六七八九十]{1,3}日");
    private static final String GB_9704 = "GB/T 9704—2012《党政机关公文格式》";

    /**
     * L4 增强：不依赖输出模板的公文结构要素检查——上行文签发人、发文字号及六角括号、版记印发要素与抄送位置、成文日期数字写法。
     */
    public List<Deviation> inspectStructure(byte[] documentBytes, String direction) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            List<String> head = new ArrayList<>();
            document.getHeaderList().forEach(header -> header.getParagraphs().forEach(p -> head.add(p.getText())));
            List<String> body = meaningful(document).stream()
                    .map(XWPFParagraph::getText)
                    .map(String::strip)
                    .toList();
            head.addAll(body.subList(0, Math.min(10, body.size())));
            List<String> tail = new ArrayList<>(body.subList(Math.max(0, body.size() - 8), body.size()));
            document.getFooterList().forEach(footer -> footer.getParagraphs().forEach(p -> tail.add(p.getText())));
            String headText = String.join("\n", head);
            String tailText = String.join("\n", tail);
            List<Deviation> result = new ArrayList<>();

            if ("上行".equals(direction) && !headText.contains("签发人")) {
                result.add(new Deviation(
                        "SIGNER", "重要", "签发人", "上行文标注签发人姓名", "未标注", null, "上行文应在发文字号右侧标注签发人姓名（" + GB_9704 + " 7.2.4）"));
            }
            if (!DOC_NUMBER.matcher(headText).find()) {
                Matcher wrong = DOC_NUMBER_WRONG_BRACKET.matcher(headText);
                if (wrong.find()) {
                    result.add(new Deviation(
                            "DOC_NUMBER_BRACKET",
                            "一般",
                            "发文字号年份括号",
                            "六角括号〔〕",
                            wrong.group(),
                            null,
                            "发文字号中的年份应使用六角括号“〔〕”（" + GB_9704 + " 7.2.3）"));
                } else {
                    result.add(new Deviation(
                            "DOC_NUMBER",
                            "提示",
                            "发文字号",
                            "机关代字〔年份〕序号",
                            "未识别",
                            null,
                            "版头中未识别到发文字号，请确认是否需要编排（" + GB_9704 + " 7.2.3）"));
                }
            }
            int imprintIndex = indexOf(tail, IMPRINT);
            if (imprintIndex < 0) {
                result.add(new Deviation(
                        "IMPRINT",
                        "一般",
                        "版记印发要素",
                        "印发机关和印发日期",
                        "未识别",
                        null,
                        "版记中未识别到印发机关和印发日期（" + GB_9704 + " 7.4.2）"));
            } else {
                int copyIndex = indexOf(tail, Pattern.compile("^抄送[：:]"));
                if (copyIndex > imprintIndex) {
                    result.add(new Deviation(
                            "COPY_TO_POSITION",
                            "一般",
                            "抄送位置",
                            "位于印发机关和印发日期之上",
                            "位于其下",
                            null,
                            "抄送机关应编排在印发机关和印发日期之上（" + GB_9704 + " 7.4）"));
                }
            }
            Matcher chineseDate = CHINESE_DATE.matcher(tailText + "\n" + String.join("\n", body));
            if (chineseDate.find()) {
                result.add(new Deviation(
                        "DATE_NUMERALS",
                        "一般",
                        "成文日期写法",
                        "阿拉伯数字",
                        chineseDate.group(),
                        null,
                        "成文日期应使用阿拉伯数字将年、月、日标全（" + GB_9704 + " 7.3.5）"));
            }
            return result;
        } catch (IOException invalidDocument) {
            throw BusinessException.unprocessable("待审文稿不是有效 DOCX 文件");
        }
    }

    private static int indexOf(List<String> lines, Pattern pattern) {
        for (int i = 0; i < lines.size(); i++)
            if (pattern.matcher(lines.get(i).strip()).find()) return i;
        return -1;
    }

    private void compare(
            List<Deviation> result,
            String code,
            String risk,
            String name,
            String expected,
            String actual,
            Integer paragraphIndex) {
        if (!Objects.equals(expected, actual)) {
            result.add(new Deviation(
                    code,
                    risk,
                    name,
                    expected,
                    actual,
                    paragraphIndex,
                    name + "应为“" + expected + "”，实际为“" + actual + "”"));
        }
    }

    private List<XWPFParagraph> meaningful(XWPFDocument document) {
        return document.getParagraphs().stream()
                .filter(value -> !value.getText().isBlank())
                .toList();
    }

    private XWPFParagraph paragraph(List<XWPFParagraph> paragraphs, int index) {
        return paragraphs.isEmpty() ? null : paragraphs.get(Math.min(index, paragraphs.size() - 1));
    }

    private XWPFRun firstRun(XWPFParagraph paragraph) {
        return paragraph == null
                ? null
                : paragraph.getRuns().stream()
                        .filter(value -> value.text() != null && !value.text().isBlank())
                        .findFirst()
                        .orElse(null);
    }

    private String font(XWPFParagraph paragraph) {
        XWPFRun run = firstRun(paragraph);
        return run == null || run.getFontFamily() == null ? "未设置" : run.getFontFamily();
    }

    private String size(XWPFParagraph paragraph) {
        XWPFRun run = firstRun(paragraph);
        Double value = run == null ? null : run.getFontSizeAsDouble();
        return value == null ? "未设置" : value + "pt";
    }

    private String spacing(XWPFParagraph paragraph) {
        return paragraph == null ? "未设置" : Double.toString(paragraph.getSpacingBetween());
    }

    private String indent(XWPFParagraph paragraph) {
        return paragraph == null ? "未设置" : paragraph.getFirstLineIndent() + "twip";
    }

    private String pageSize(CTPageSz value) {
        return value == null ? "未设置" : value.getW() + "x" + value.getH() + "twip";
    }

    private String top(CTPageMar value) {
        return value == null ? "未设置" : value.getTop() + "twip";
    }

    private String bottom(CTPageMar value) {
        return value == null ? "未设置" : value.getBottom() + "twip";
    }

    private String left(CTPageMar value) {
        return value == null ? "未设置" : value.getLeft() + "twip";
    }

    private String right(CTPageMar value) {
        return value == null ? "未设置" : value.getRight() + "twip";
    }

    public record Result(String templateId, int deviationCount, List<Deviation> deviations) {}

    public record Deviation(
            String dimensionCode,
            String riskLevel,
            String dimensionName,
            String expectedValue,
            String actualValue,
            Integer paragraphIndex,
            String description) {}
}
