package com.example.weather.api.dto;

public record QueryResultItem(
        String sensorId,
        String metric,
        double value,
        long sampleCount
) {
}
