package com.example.weather.api;

import com.example.weather.api.dto.IngestRequest;
import com.example.weather.api.dto.IngestResponse;
import com.example.weather.api.dto.QueryResponse;
import com.example.weather.domain.SensorReading;
import com.example.weather.repo.SensorReadingRepository;
import com.example.weather.service.IngestService;
import com.example.weather.service.QueryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/readings")
public class ReadingController {

    private static final Logger log = LoggerFactory.getLogger(ReadingController.class);

    private final IngestService ingestService;
    private final QueryService queryService;
    private final SensorReadingRepository repository;

    public ReadingController(
            IngestService ingestService,
            QueryService queryService,
            SensorReadingRepository repository
    ) {
        this.ingestService = ingestService;
        this.queryService = queryService;
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<IngestResponse> create(@Valid @RequestBody IngestRequest req) {
        SensorReading saved = ingestService.ingest(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(IngestResponse.from(saved));
    }

    @GetMapping
    public List<SensorReading> list() {
        return repository.findAll();
    }

    @GetMapping("/query")
    public QueryResponse query(
            @RequestParam(required = false) List<String> sensorIds,
            @RequestParam(required = false) List<String> metrics,
            @RequestParam(required = false, defaultValue = "avg") String stat,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return queryService.query(sensorIds, metrics, stat, from, to);
    }

    // ---- Exception handling ----

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validationFailed(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ProblemDetail badParameter(Exception ex) {
        log.warn("Bad parameter: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
}
