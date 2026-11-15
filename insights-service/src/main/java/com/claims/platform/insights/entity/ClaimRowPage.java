package com.claims.platform.insights.entity;

import java.util.List;

/** A keyset-paginated page of the work queue; {@code nextCursor} is null on the last page. */
public record ClaimRowPage(List<ClaimRow> items, String nextCursor) {
}
