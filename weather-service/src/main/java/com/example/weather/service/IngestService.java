package com.example.weather.service;

import com.example.weather.api.dto.IngestRequest;
import com.example.weather.domain.SensorReading;
import com.example.weather.repo.SensorReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final SensorReadingRepository repository;
    private final Clock clock;

    public IngestService(SensorReadingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SensorReading ingest(IngestRequest request) {
        Instant when = request.recordedAt() != null ? request.recordedAt() : clock.instant();
        SensorReading saved = repository.save(
                new SensorReading(request.sensorId(), request.metric(), request.value(), when));

        log.debug("Ingested id={} sensor={} metric={} value={} at={}",
                saved.getId(), saved.getSensorId(), saved.getMetric(), saved.getValue(), saved.getRecordedAt());
        return saved;
    }
}
