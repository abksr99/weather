package com.example.weather.api.dto;

import java.time.Instant;
import java.util.List;

public record QueryResponse(
        String stat,
        Instant from,
        Instant to,
        List<QueryResultItem> results
) {
}
