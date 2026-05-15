package com.example.weather.service;

import com.example.weather.api.dto.QueryResponse;
import com.example.weather.api.dto.QueryResultItem;
import com.example.weather.domain.Stat;
import com.example.weather.repo.AggregationRow;
import com.example.weather.repo.SensorReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    private static final Duration MIN_RANGE = Duration.ofDays(1);
    private static final Duration MAX_RANGE = Duration.ofDays(31);
    private static final List<String> NO_SENSOR_FILTER = List.of("");

    private final SensorReadingRepository repository;
    private final Clock clock;

    public QueryService(SensorReadingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QueryResponse query(
            List<String> sensorIds,
            List<String> metrics,
            String statRaw,
            Instant from,
            Instant to
    ) {
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("At least one metric is required.");
        }

        Stat stat = Stat.fromString(statRaw);
        Window window = resolveWindow(from, to);
        boolean allSensors = sensorIds == null || sensorIds.isEmpty();

        log.debug("Query stat={} sensors={} metrics={} window={}..{}",
                stat, allSensors ? "all" : sensorIds, metrics, window.from, window.to);

        List<AggregationRow> rows = repository.aggregate(
                stat.name(),
                allSensors,
                allSensors ? NO_SENSOR_FILTER : sensorIds,
                metrics,
                window.from,
                window.to);

        List<QueryResultItem> items = rows.stream()
                .map(r -> new QueryResultItem(r.getSensorId(), r.getMetric(), r.getValue(), r.getSampleCount()))
                .toList();

        log.debug("Query returned {} rows", items.size());
        return new QueryResponse(stat.name().toLowerCase(Locale.ROOT), window.from, window.to, items);
    }

    private Window resolveWindow(Instant from, Instant to) {
        Instant now = clock.instant();

        if (from == null && to == null) {
            return new Window(now.minus(DEFAULT_WINDOW), now);
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "Both 'from' and 'to' must be provided together, or neither (defaults to last 24h).");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be strictly before 'to'.");
        }

        Duration range = Duration.between(from, to);
        if (range.compareTo(MIN_RANGE) < 0) {
            throw new IllegalArgumentException("Date range must be at least 1 day.");
        }
        if (range.compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("Date range must be at most 31 days.");
        }
        return new Window(from, to);
    }

    private record Window(Instant from, Instant to) {
    }
}
