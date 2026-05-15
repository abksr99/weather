package com.example.weather.repo;

public interface AggregationRow {
    String getSensorId();
    String getMetric();
    Double getValue();
    Long getSampleCount();
}
