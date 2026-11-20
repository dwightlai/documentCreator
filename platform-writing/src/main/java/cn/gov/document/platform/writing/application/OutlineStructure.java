package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.writing.domain.WritingOutlineNodeEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OutlineStructure {
    public static final int MAX_LEVEL = 3;
    public static final Set<String> LONG_TYPES =
            Set.of("意见", "报告", "工作方案", "工作计划", "工作总结", "调研报告", "汇报材料", "领导讲话稿");
    public static final Set<String> SINGLE_LEVEL_TYPES = Set.of("请示", "批复", "函", "命令", "公告", "通告", "议案");
    private static final String[] NUMERALS = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

    private final Map<String, WritingOutlineNodeEntity> byId = new LinkedHashMap<>();
    private final Map<String, List<WritingOutlineNodeEntity>> children = new HashMap<>();
    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final List<WritingOutlineNodeEntity> ordered = new ArrayList<>();

    public OutlineStructure(List<WritingOutlineNodeEntity> nodes) {
        nodes.forEach(node -> byId.put(node.getId(), node));
        for (WritingOutlineNodeEntity node : nodes) {
            String parent = byId.containsKey(node.getParentNodeId()) ? node.getParentNodeId() : null;
            children.computeIfAbsent(parent, key -> new ArrayList<>()).add(node);
        }
        children.values().forEach(list -> list.sort(Comparator.comparing(WritingOutlineNodeEntity::getNodeOrder)));
        walk(null, 1);
    }

    private void walk(String parentId, int level) {
        List<WritingOutlineNodeEntity> list = children.getOrDefault(parentId, List.of());
        for (int index = 0; index < list.size(); index++) {
            WritingOutlineNodeEntity node = list.get(index);
            levels.put(node.getId(), level);
            labels.put(node.getId(), label(level, index + 1));
            ordered.add(node);
            walk(node.getId(), level + 1);
        }
    }

    public static boolean usesHeadings(String documentType, List<WritingOutlineNodeEntity> nodes) {
        return LONG_TYPES.contains(documentType) || nodes.stream().anyMatch(node -> node.getParentNodeId() != null);
    }

    public static String label(int level, int index) {
        return switch (level) {
            case 1 -> numeral(index) + "、";
            case 2 -> "（" + numeral(index) + "）";
            case 3 -> index + ".";
            default -> "（" + index + "）";
        };
    }

    private static String numeral(int value) {
        if (value <= 10) return NUMERALS[value - 1];
        if (value < 20) return "十" + NUMERALS[value - 11];
        return NUMERALS[value / 10 - 1] + "十" + (value % 10 == 0 ? "" : NUMERALS[value % 10 - 1]);
    }

    public List<WritingOutlineNodeEntity> ordered() {
        return ordered;
    }

    public int level(String nodeId) {
        return levels.getOrDefault(nodeId, 1);
    }

    public boolean hasChildren(String nodeId) {
        return !children.getOrDefault(nodeId, List.of()).isEmpty();
    }

    public List<WritingOutlineNodeEntity> children(String nodeId) {
        return children.getOrDefault(nodeId, List.of());
    }

    public String heading(WritingOutlineNodeEntity node) {
        return labels.get(node.getId()) + node.getNodeTitle();
    }

    public String path(WritingOutlineNodeEntity node) {
        List<String> parts = new ArrayList<>();
        WritingOutlineNodeEntity cursor = node;
        while (cursor != null) {
            parts.add(0, heading(cursor));
            cursor = byId.get(cursor.getParentNodeId());
        }
        return String.join(" > ", parts);
    }
}
