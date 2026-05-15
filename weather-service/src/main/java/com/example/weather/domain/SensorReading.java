package com.example.weather.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.Objects;

@Entity
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sensorId;

    @Column(nullable = false)
    private String metric;

    @Column(name = "measurement", nullable = false)
    private double value;

    @Column(nullable = false)
    private Instant recordedAt;

    protected SensorReading() {
    }

    public SensorReading(String sensorId, String metric, double value, Instant recordedAt) {
        this.sensorId = Objects.requireNonNull(sensorId);
        this.metric = Objects.requireNonNull(metric);
        this.value = value;
        this.recordedAt = Objects.requireNonNull(recordedAt);
    }

    public Long getId() { return id; }
    public String getSensorId() { return sensorId; }
    public String getMetric() { return metric; }
    public double getValue() { return value; }
    public Instant getRecordedAt() { return recordedAt; }
}
