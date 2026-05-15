package com.example.weather.repo;

import com.example.weather.domain.SensorReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
class SensorReadingRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-05-14T12:00:00Z");
    private static final Instant WINDOW_FROM = T0.minus(Duration.ofDays(7));
    private static final Instant WINDOW_TO = T0.plus(Duration.ofHours(1));

    private static final List<String> ANY_SENSOR = List.of("");

    @Autowired
    private SensorReadingRepository repository;

    @BeforeEach
    void seed() {
        repository.saveAll(List.of(
                // s-1 / temperature: avg=21.333, min=18, max=24, sum=64, count=3
                new SensorReading("s-1", "temperature", 18.0, T0),
                new SensorReading("s-1", "temperature", 22.0, T0),
                new SensorReading("s-1", "temperature", 24.0, T0),
                // s-2 / temperature: avg=20, min=10, max=30, sum=40, count=2
                new SensorReading("s-2", "temperature", 10.0, T0),
                new SensorReading("s-2", "temperature", 30.0, T0),
                // s-1 / humidity: alone, count=1
                new SensorReading("s-1", "humidity", 50.0, T0),
                // Out-of-window reading: 60 days old. Must be excluded by all queries.
                new SensorReading("s-1", "temperature", 999.0, T0.minus(Duration.ofDays(60)))
        ));
    }

    @Test
    void avgForAllSensorsOneMetric() {
        List<AggregationRow> rows = repository.aggregate(
                "AVG", true, ANY_SENSOR, List.of("temperature"), WINDOW_FROM, WINDOW_TO);

        assertThat(rows).hasSize(2);

        assertThat(rows.get(0).getSensorId()).isEqualTo("s-1");
        assertThat(rows.get(0).getValue()).isCloseTo(21.333, within(0.01));
        assertThat(rows.get(0).getSampleCount()).isEqualTo(3L);

        assertThat(rows.get(1).getSensorId()).isEqualTo("s-2");
        assertThat(rows.get(1).getValue()).isEqualTo(20.0);
        assertThat(rows.get(1).getSampleCount()).isEqualTo(2L);
    }

    @Test
    void minMaxSumProduceCorrectValues() {
        List<AggregationRow> mins = repository.aggregate(
                "MIN", true, ANY_SENSOR, List.of("temperature"), WINDOW_FROM, WINDOW_TO);
        List<AggregationRow> maxs = repository.aggregate(
                "MAX", true, ANY_SENSOR, List.of("temperature"), WINDOW_FROM, WINDOW_TO);
        List<AggregationRow> sums = repository.aggregate(
                "SUM", true, ANY_SENSOR, List.of("temperature"), WINDOW_FROM, WINDOW_TO);

        assertThat(mins.get(0).getValue()).isEqualTo(18.0);
        assertThat(mins.get(1).getValue()).isEqualTo(10.0);

        assertThat(maxs.get(0).getValue()).isEqualTo(24.0);
        assertThat(maxs.get(1).getValue()).isEqualTo(30.0);

        assertThat(sums.get(0).getValue()).isEqualTo(64.0);
        assertThat(sums.get(1).getValue()).isEqualTo(40.0);
    }

    @Test
    void filtersBySensorIds() {
        List<AggregationRow> rows = repository.aggregate(
                "AVG", false, List.of("s-1"), List.of("temperature"), WINDOW_FROM, WINDOW_TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSensorId()).isEqualTo("s-1");
        assertThat(rows.get(0).getSampleCount()).isEqualTo(3L);
    }

    @Test
    void filtersByMetricList() {
        List<AggregationRow> rows = repository.aggregate(
                "AVG", true, ANY_SENSOR, List.of("humidity"), WINDOW_FROM, WINDOW_TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSensorId()).isEqualTo("s-1");
        assertThat(rows.get(0).getMetric()).isEqualTo("humidity");
        assertThat(rows.get(0).getValue()).isEqualTo(50.0);
    }

    @Test
    void groupsByEachSensorAndMetricCombo() {
        List<AggregationRow> rows = repository.aggregate(
                "AVG", true, ANY_SENSOR, List.of("temperature", "humidity"), WINDOW_FROM, WINDOW_TO);

        assertThat(rows)
                .hasSize(3)
                .extracting(AggregationRow::getSensorId, AggregationRow::getMetric)
                .containsExactly(
                        tuple("s-1", "humidity"),
                        tuple("s-1", "temperature"),
                        tuple("s-2", "temperature")
                );
    }

    @Test
    void excludesReadingsOutsideTheWindow() {
        List<AggregationRow> rows = repository.aggregate(
                "AVG", true, ANY_SENSOR, List.of("temperature"), WINDOW_FROM, WINDOW_TO);

        assertThat(rows.get(0).getSensorId()).isEqualTo("s-1");
        assertThat(rows.get(0).getSampleCount()).isEqualTo(3L);
        assertThat(rows.get(0).getValue()).isLessThan(100.0);
    }
}
