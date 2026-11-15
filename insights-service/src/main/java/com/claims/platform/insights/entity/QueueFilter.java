package com.claims.platform.insights.entity;

import java.util.Set;

public record QueueFilter(QueueView view, Set<String> markets, String lineOfBusiness, String handlerId, String teamId) {
}
