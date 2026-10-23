package cn.gov.document.platform.document.template;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;

/**
 * 内置标准版式（§6.3 内置模板的通用兜底）：文种尚无已发布的输出模板时，按 GB/T 9704—2012 生成通用公文版式——
 * A4 纸、天头 37mm、订口 28mm、版心 156mm×225mm；红色发文机关标志与红色分隔线；二号小标宋标题；三号仿宋正文、首行缩进两字、
 * 固定行距 28 磅；成文日期右空四字；版记抄送与印发机关、印发日期；页码四号“— 1 —”。
 * 占位符仅使用 §6.3.1 统一清单，发文机关署名由用印处理，不在版式中生成。各单位应上传本单位的正式模板替代。
 */
public final class StandardOfficialTemplate {
    public static final String NAME = "内置标准版式（GB/T 9704—2012）";
    private static final String SONG = "宋体";
    private static final String FANGSONG = "仿宋_GB2312";
    private static final String XIAOBIAOSONG = "方正小标宋简体";
    private static final int BODY_SIZE = 16;
    private static final int TWO_CHARS = 640;
    /** 版心宽度 156mm 扣除版记左右各空一字后的右对齐制表位。 */
    private static final int IMPRINT_TAB = 8845 - 2 * 320;

    private StandardOfficialTemplate() {}

    public static byte[] build() {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CTSectPr section = document.getDocument().getBody().addNewSectPr();
            section.addNewPgSz().setW(BigInteger.valueOf(11906));
            section.getPgSz().setH(BigInteger.valueOf(16838));
            var margin = section.addNewPgMar();
            margin.setTop(BigInteger.valueOf(2098));
            margin.setBottom(BigInteger.valueOf(1984));
            margin.setLeft(BigInteger.valueOf(1587));
            margin.setRight(BigInteger.valueOf(1474));
            margin.setFooter(BigInteger.valueOf(992));

            XWPFParagraph sender = centered(document);
            run(sender, "{sender}", XIAOBIAOSONG, 36).setColor("FF0000");
            blank(document);
            blank(document);
            XWPFParagraph number = centered(document);
            run(number, "{doc_number}{#has_signer}　　签发人：{signer}{/has_signer}", FANGSONG, BODY_SIZE);
            border(number, false, "FF0000", 12);
            blank(document);
            blank(document);
            run(centered(document), "{title}", XIAOBIAOSONG, 22);
            blank(document);
            run(body(document, 0), "{recipient}", FANGSONG, BODY_SIZE);
            run(body(document, TWO_CHARS), "{content}", FANGSONG, BODY_SIZE);
            run(body(document, TWO_CHARS), "{closing}", FANGSONG, BODY_SIZE);
            blank(document);
            run(
                    body(document, TWO_CHARS),
                    "{#has_attachment}附件：{attachment_desc}{/has_attachment}",
                    FANGSONG,
                    BODY_SIZE);
            blank(document);
            XWPFParagraph date = body(document, 0);
            date.setAlignment(ParagraphAlignment.RIGHT);
            date.setIndentationRight(4 * 320);
            run(date, "{date}", FANGSONG, BODY_SIZE);
            blank(document);

            XWPFParagraph copyTo = imprint(document);
            run(copyTo, "{#has_copy_to}抄送：{copy_to}。{/has_copy_to}", FANGSONG, 14);
            border(copyTo, true, "000000", 8);
            XWPFParagraph issue = imprint(document);
            issue.getCTP().getPPr().addNewTabs().addNewTab().setVal(STTabJc.RIGHT);
            issue.getCTP().getPPr().getTabs().getTabArray(0).setPos(BigInteger.valueOf(IMPRINT_TAB));
            run(issue, "{issue_unit}\t{issue_date}印发", FANGSONG, 14);
            border(issue, true, "000000", 4);
            border(issue, false, "000000", 8);

            pageNumber(document);
            document.write(output);
            return output.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("内置标准版式生成失败", impossible);
        }
    }

    private static XWPFParagraph centered(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        return paragraph;
    }

    /** 正文段落：固定行距 28 磅（GB/T 9704 每页 22 行）。 */
    private static XWPFParagraph body(XWPFDocument document, int firstLineIndent) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        if (firstLineIndent > 0) paragraph.setFirstLineIndent(firstLineIndent);
        paragraph.setSpacingBetween(28, LineSpacingRule.EXACT);
        return paragraph;
    }

    private static void blank(XWPFDocument document) {
        run(body(document, 0), "", FANGSONG, BODY_SIZE);
    }

    /** 版记：左右各空一字，四号仿宋。 */
    private static XWPFParagraph imprint(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(320);
        paragraph.setIndentationRight(320);
        paragraph.setSpacingBetween(28, LineSpacingRule.EXACT);
        return paragraph;
    }

    private static XWPFRun run(XWPFParagraph paragraph, String text, String font, int size) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(font);
        run.setFontFamily(font, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        run.setText(text);
        return run;
    }

    private static void border(XWPFParagraph paragraph, boolean top, String color, int size) {
        var properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr()
                : paragraph.getCTP().addNewPPr();
        CTPBdr borders = properties.isSetPBdr() ? properties.getPBdr() : properties.addNewPBdr();
        CTBorder border = top ? borders.addNewTop() : borders.addNewBottom();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(size));
        border.setSpace(BigInteger.valueOf(4));
        border.setColor(color);
    }

    /** 页码：四号半角宋体阿拉伯数字，两侧各一条一字线，居外侧；通用版式统一居右。 */
    private static void pageNumber(XWPFDocument document) {
        XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        paragraph.setIndentationRight(320);
        run(paragraph, "— ", SONG, 14);
        var field = paragraph.getCTP().addNewFldSimple();
        field.setInstr("PAGE");
        var number = field.addNewR();
        number.addNewRPr().addNewSz().setVal(BigInteger.valueOf(28));
        number.addNewT().setStringValue("1");
        run(paragraph, " —", SONG, 14);
    }
}
