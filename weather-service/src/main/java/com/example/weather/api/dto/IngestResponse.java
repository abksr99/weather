package com.example.weather.api.dto;

import com.example.weather.domain.SensorReading;

import java.time.Instant;

public record IngestResponse(
        Long id,
        String sensorId,
        String metric,
        double value,
        Instant recordedAt
) {

    public static IngestResponse from(SensorReading entity) {
        return new IngestResponse(
                entity.getId(),
                entity.getSensorId(),
                entity.getMetric(),
                entity.getValue(),
                entity.getRecordedAt());
    }
}
