package cn.gov.document.platform.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, long page, long size, long total) {
    public PageResponse {
        items = List.copyOf(items);
    }
}
