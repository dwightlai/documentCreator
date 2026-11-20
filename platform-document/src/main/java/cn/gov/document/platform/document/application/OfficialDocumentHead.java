package cn.gov.document.platform.document.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/** 识别公文版头（份号、密级、紧急程度、发文机关标志、发文字号、签发人）和可能跨多段的标题。 */
public final class OfficialDocumentHead {
    public record Line(String text, String alignment, int fontSize, boolean red) {
        public static Line of(String text) {
            return new Line(text, null, 0, false);
        }

        public static Line of(XWPFParagraph paragraph) {
            XWPFRun run = paragraph.getRuns().stream()
                    .filter(item -> item.getText(0) != null && !item.getText(0).isBlank())
                    .findFirst()
                    .orElse(null);
            String alignment = paragraph.getAlignment() == null ? "LEFT" : paragraph.getAlignment().name();
            int size = run == null || run.getFontSizeAsDouble() == null ? 0 : (int) Math.round(run.getFontSizeAsDouble() * 2);
            return new Line(paragraph.getText(), alignment, size, run != null && isRed(run.getColor()));
        }
    }

    public record Result(List<Integer> head, int titleStart, int titleEnd, String title) {
        public boolean isHead(int index) {
            return head.contains(index);
        }

        public boolean inTitle(int index) {
            return index >= titleStart && index < titleEnd;
        }

        public boolean hasTitle() {
            return titleStart >= 0;
        }
    }

    private static final int SCAN = 12;
    private static final Pattern COPY_NO = Pattern.compile("^(份号[：:]?\\s*)?\\d{1,6}$");
    private static final Pattern MARK = Pattern.compile(
            "^(公开|内部|普通|秘密|机密|绝密|特急|加急|平急|特提)(\\s*★\\s*\\S*)?$|^(密级|保密期限|紧急程度)[：:].*");
    private static final Pattern ISSUER = Pattern.compile("^[^，。；：:]{2,40}文件$");
    private static final Pattern DOC_NO =
            Pattern.compile("[〔\\[［(（【]\\s*\\d{4}\\s*[〕\\]］)）】]\\s*\\d+\\s*号");
    private static final Pattern SIGNER = Pattern.compile("^签发人[：:]");
    private static final Pattern RULE = Pattern.compile("^[\\-—_─━=＊*★\\s]+$");
    private static final Pattern TYPE_END = Pattern.compile(
            "(通知|通报|报告|请示|批复|函|意见|决定|纪要|公告|通告|命令|令|决议|公报|议案|方案|计划|总结|讲话|讲话稿|办法|规定|细则|规划|要点|简报|说明)$");
    private static final Pattern SUBTITLE = Pattern.compile("^[（(].{1,18}[）)]$|^——.{2,40}$");
    private static final Pattern HEADING = Pattern.compile(
            "^([一二三四五六七八九十]+、|[（(][一二三四五六七八九十]+[）)]|\\d+[.．、]|第[一二三四五六七八九十]+[章条])");

    private OfficialDocumentHead() {}

    public static Result detectText(List<String> lines) {
        return detect(lines.stream().map(Line::of).toList());
    }

    public static Result detect(List<Line> lines) {
        List<Integer> head = new ArrayList<>();
        int i = 0;
        while (i < lines.size() && i < SCAN && isHead(lines.get(i))) head.add(i++);
        if (i >= lines.size() || !titleLike(text(lines.get(i)))) return new Result(List.copyOf(head), -1, -1, null);
        int start = i;
        StringBuilder title = new StringBuilder(text(lines.get(i)));
        int end = i + 1;
        while (end < lines.size() && end - start < 4 && continues(lines.get(start), title.toString(), lines.get(end)))
            title.append(text(lines.get(end++)));
        return new Result(List.copyOf(head), start, end, title.toString());
    }

    private static boolean isHead(Line line) {
        String s = text(line);
        if (s.isEmpty() || RULE.matcher(s).matches()) return true;
        if (COPY_NO.matcher(s).matches() || MARK.matcher(s).matches() || SIGNER.matcher(s).find()) return true;
        if (ISSUER.matcher(s).matches() && !s.startsWith("关于")) return true;
        if (s.length() <= 60 && DOC_NO.matcher(s).find() && !s.startsWith("关于") && !s.contains("《")) return true;
        return line.red() && s.length() <= 40 && !s.startsWith("关于");
    }

    private static boolean titleLike(String s) {
        return !s.isEmpty() && s.length() <= 80 && !s.endsWith("：") && !s.endsWith(":") && !s.matches(".*[。；！？].*")
                && !HEADING.matcher(s).find();
    }

    private static boolean continues(Line first, String sofar, Line next) {
        String s = text(next);
        if (s.isEmpty()) return false;
        if (SUBTITLE.matcher(s).matches()) return true;
        if (TYPE_END.matcher(sofar).find()) return false;
        if (s.endsWith("：") || s.endsWith(":") || s.length() > 40 || s.matches(".*[。；！？，].*")) return false;
        if (HEADING.matcher(s).find()) return false;
        if (first.alignment() != null && next.alignment() != null) {
            if (!first.alignment().equals(next.alignment())) return false;
            if (first.fontSize() > 0 && next.fontSize() > 0 && first.fontSize() != next.fontSize()) return false;
        }
        return true;
    }

    private static String text(Line line) {
        return line.text() == null ? "" : line.text().strip();
    }

    private static boolean isRed(String hex) {
        if (hex == null || !hex.matches("(?i)[0-9a-f]{6}")) return false;
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return r >= 0xB0 && g < 0x70 && b < 0x70;
    }
}
