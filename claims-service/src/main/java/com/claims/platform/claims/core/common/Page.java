package com.claims.platform.claims.core.common;

import java.util.List;
import java.util.function.Function;

/** One page of a keyset-paginated list; {@code nextCursor} is null on the last page. */
public record Page<T>(List<T> items, String nextCursor) {
    public Page {
        items = List.copyOf(items);
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), nextCursor);
    }
}
