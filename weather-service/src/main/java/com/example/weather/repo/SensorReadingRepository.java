package com.example.weather.repo;

import com.example.weather.domain.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    @Query("""
            select r.sensorId as sensorId,
                   r.metric   as metric,
                   case
                       when :stat = 'AVG' then avg(r.value)
                       when :stat = 'MIN' then min(r.value)
                       when :stat = 'MAX' then max(r.value)
                       when :stat = 'SUM' then sum(r.value)
                   end        as value,
                   count(r)   as sampleCount
            from SensorReading r
            where (:allSensors = true or r.sensorId in :sensorIds)
              and r.metric in :metrics
              and r.recordedAt between :from and :to
            group by r.sensorId, r.metric
            order by r.sensorId, r.metric
            """)
    List<AggregationRow> aggregate(
            @Param("stat") String stat,
            @Param("allSensors") boolean allSensors,
            @Param("sensorIds") Collection<String> sensorIds,
            @Param("metrics") Collection<String> metrics,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
