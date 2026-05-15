package com.example.weather.service;

import com.example.weather.api.dto.QueryResponse;
import com.example.weather.repo.AggregationRow;
import com.example.weather.repo.SensorReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

    private SensorReadingRepository repository;
    private QueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(SensorReadingRepository.class);
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new QueryService(repository, fixedClock);
    }

    @Test
    void defaultsToLast24HoursWhenFromAndToOmitted() {
        when(repository.aggregate(anyString(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of(row("s-1", "temperature", 21.0, 3L)));

        QueryResponse response = service.query(null, List.of("temperature"), "avg", null, null);

        assertThat(response.from()).isEqualTo(NOW.minus(Duration.ofHours(24)));
        assertThat(response.to()).isEqualTo(NOW);
        assertThat(response.stat()).isEqualTo("avg");
        assertThat(response.results()).hasSize(1);
    }

    @Test
    void usesAllSensorsModeWhenSensorIdsOmitted() {
        when(repository.aggregate(anyString(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.query(null, List.of("temperature"), "avg", null, null);

        verify(repository).aggregate(eq("AVG"), eq(true), any(), eq(List.of("temperature")), any(), any());
    }

    @Test
    void usesFilteredModeWhenSensorIdsProvided() {
        when(repository.aggregate(anyString(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.query(List.of("s-1", "s-2"), List.of("temperature"), "avg", null, null);

        verify(repository).aggregate(
                eq("AVG"),
                eq(false),
                eq(List.of("s-1", "s-2")),
                eq(List.of("temperature")),
                any(),
                any());
    }

    @Test
    void passesEachStatThroughToRepository() {
        when(repository.aggregate(anyString(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.query(null, List.of("temperature"), "min", null, null);
        verify(repository).aggregate(eq("MIN"), eq(true), any(), any(), any(), any());

        service.query(null, List.of("temperature"), "max", null, null);
        verify(repository).aggregate(eq("MAX"), eq(true), any(), any(), any(), any());

        service.query(null, List.of("temperature"), "sum", null, null);
        verify(repository).aggregate(eq("SUM"), eq(true), any(), any(), any(), any());

        service.query(null, List.of("temperature"), "avg", null, null);
        verify(repository).aggregate(eq("AVG"), eq(true), any(), any(), any(), any());
    }

    @Test
    void rejectsMissingMetrics() {
        assertThatThrownBy(() -> service.query(null, null, "avg", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metric");

        assertThatThrownBy(() -> service.query(null, List.of(), "avg", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metric");

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsUnknownStat() {
        assertThatThrownBy(() -> service.query(null, List.of("temperature"), "median", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("median");
    }

    @Test
    void rejectsHalfOpenWindow() {
        assertThatThrownBy(() -> service.query(null, List.of("temperature"), "avg", NOW.minus(Duration.ofDays(2)), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Both 'from' and 'to'");

        assertThatThrownBy(() -> service.query(null, List.of("temperature"), "avg", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Both 'from' and 'to'");
    }

    @Test
    void rejectsFromNotBeforeTo() {
        assertThatThrownBy(() -> service.query(null, List.of("temperature"), "avg", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly before");

        assertThatThrownBy(() -> service.query(null, List.of("temperature"), "avg", NOW, NOW.minus(Duration.ofDays(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly before");
    }

    @Test
    void rejectsRangeShorterThanOneDay() {
        Instant from = NOW.minus(Duration.ofHours(23));
        assertThatThrownBy(() -> service.query(null, List.of("temperature"), "avg", from, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1 day");
    }

    @Test
    void rejectsRangeLongerThan31Days() {
        Instant from = NOW.minus(Duration.ofDays(32));
        assertThatThrownBy(() -> service.query(null, List.of("temperature"), "avg", from, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 31 days");
    }

    @Test
    void acceptsExactlyOneDayRange() {
        when(repository.aggregate(anyString(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of());

        Instant from = NOW.minus(Duration.ofDays(1));
        QueryResponse response = service.query(null, List.of("temperature"), "avg", from, NOW);

        assertThat(response.from()).isEqualTo(from);
        assertThat(response.to()).isEqualTo(NOW);
    }

    @Test
    void acceptsExactly31DayRange() {
        when(repository.aggregate(anyString(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of());

        Instant from = NOW.minus(Duration.ofDays(31));
        QueryResponse response = service.query(null, List.of("temperature"), "avg", from, NOW);

        assertThat(response.from()).isEqualTo(from);
        assertThat(response.to()).isEqualTo(NOW);
    }

    @Test
    void mapsRepositoryRowsToResponseItems() {
        when(repository.aggregate(anyString(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        row("s-1", "temperature", 21.5, 4L),
                        row("s-2", "humidity", 60.0, 2L)
                ));

        QueryResponse response = service.query(null, List.of("temperature", "humidity"), "avg", null, null);

        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).sensorId()).isEqualTo("s-1");
        assertThat(response.results().get(0).metric()).isEqualTo("temperature");
        assertThat(response.results().get(0).value()).isEqualTo(21.5);
        assertThat(response.results().get(0).sampleCount()).isEqualTo(4L);
    }

    private static AggregationRow row(String sensorId, String metric, double value, long sampleCount) {
        return new AggregationRow() {
            @Override public String getSensorId()    { return sensorId; }
            @Override public String getMetric()      { return metric; }
            @Override public Double getValue()       { return value; }
            @Override public Long   getSampleCount() { return sampleCount; }
        };
    }
}
