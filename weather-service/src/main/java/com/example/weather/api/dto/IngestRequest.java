package com.example.weather.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record IngestRequest(
        @NotBlank @Size(max = 64) String sensorId,
        @NotBlank @Size(max = 64) String metric,
        @NotNull Double value,
        Instant recordedAt
) {
}
