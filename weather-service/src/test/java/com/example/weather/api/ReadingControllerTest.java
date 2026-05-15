package com.example.weather.api;

import com.example.weather.api.dto.QueryResponse;
import com.example.weather.api.dto.QueryResultItem;
import com.example.weather.domain.SensorReading;
import com.example.weather.repo.SensorReadingRepository;
import com.example.weather.service.IngestService;
import com.example.weather.service.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingController.class)
class ReadingControllerTest {

    private static final Instant T = Instant.parse("2026-05-14T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestService ingestService;

    @MockBean
    private QueryService queryService;

    @MockBean
    private SensorReadingRepository repository;


    @Test
    void postCreate_returns201WithIngestResponse() throws Exception {
        when(ingestService.ingest(any()))
                .thenReturn(new SensorReading("s-1", "temperature", 21.4, T));

        mockMvc.perform(post("/api/v1/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sensorId":"s-1","metric":"temperature","value":21.4}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sensorId").value("s-1"))
                .andExpect(jsonPath("$.metric").value("temperature"))
                .andExpect(jsonPath("$.value").value(21.4))
                .andExpect(jsonPath("$.recordedAt").value("2026-05-14T12:00:00Z"));
    }

    @Test
    void postCreate_blankSensorId_returns400WithFieldDetail() throws Exception {
        mockMvc.perform(post("/api/v1/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sensorId":"","metric":"temperature","value":21.4}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("sensorId")));
    }

    @Test
    void postCreate_missingValue_returns400WithFieldDetail() throws Exception {
        mockMvc.perform(post("/api/v1/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sensorId":"s-1","metric":"temperature"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("value")));
    }


    @Test
    void getList_returnsAllReadings() throws Exception {
        when(repository.findAll())
                .thenReturn(List.of(new SensorReading("s-1", "temperature", 21.4, T)));

        mockMvc.perform(get("/api/v1/readings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sensorId").value("s-1"))
                .andExpect(jsonPath("$[0].value").value(21.4));
    }


    @Test
    void getQuery_returnsQueryResponseEnvelope() throws Exception {
        when(queryService.query(any(), any(), any(), any(), any()))
                .thenReturn(new QueryResponse(
                        "avg",
                        T.minus(Duration.ofDays(1)),
                        T,
                        List.of(new QueryResultItem("s-1", "temperature", 21.0, 3L))));

        mockMvc.perform(get("/api/v1/readings/query").param("metrics", "temperature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stat").value("avg"))
                .andExpect(jsonPath("$.results[0].sensorId").value("s-1"))
                .andExpect(jsonPath("$.results[0].value").value(21.0))
                .andExpect(jsonPath("$.results[0].sampleCount").value(3));
    }

    @Test
    void getQuery_passesAllParametersToService() throws Exception {
        when(queryService.query(any(), any(), any(), any(), any()))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/readings/query")
                        .param("sensorIds", "s-1", "s-2")
                        .param("metrics", "temperature", "humidity")
                        .param("stat", "max")
                        .param("from", "2026-05-13T00:00:00Z")
                        .param("to", "2026-05-14T00:00:00Z"))
                .andExpect(status().isOk());

        verify(queryService).query(
                eq(List.of("s-1", "s-2")),
                eq(List.of("temperature", "humidity")),
                eq("max"),
                eq(Instant.parse("2026-05-13T00:00:00Z")),
                eq(Instant.parse("2026-05-14T00:00:00Z")));
    }

    @Test
    void getQuery_serviceThrowsIllegalArgument_returns400ProblemDetail() throws Exception {
        when(queryService.query(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Date range must be at least 1 day."));

        mockMvc.perform(get("/api/v1/readings/query").param("metrics", "temperature"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Date range must be at least 1 day."));
    }

    @Test
    void getQuery_badDateFormat_returns400ProblemDetail() throws Exception {
        // MethodArgumentTypeMismatchException is mapped to 400 by GlobalExceptionHandler.badParameter.
        mockMvc.perform(get("/api/v1/readings/query")
                        .param("metrics", "temperature")
                        .param("from", "yesterday")
                        .param("to", "today"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void getQuery_unexpectedException_returns500WithoutLeakingDetails() throws Exception {
        when(queryService.query(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB connection lost: jdbc://internal-host:5432"));

        mockMvc.perform(get("/api/v1/readings/query").param("metrics", "temperature"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value(not(containsString("DB connection"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("internal-host"))));
    }

    private static QueryResponse emptyResponse() {
        return new QueryResponse("avg", T.minus(Duration.ofDays(1)), T, List.of());
    }
}
