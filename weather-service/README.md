# weather-service

Small Spring Boot app that ingests weather sensor readings and lets you query
aggregates (`avg` / `min` / `max` / `sum`) over them. Uses H2 in file mode, so
there's nothing extra to spin up.

Needs Java 21+ and Maven 3.9+.

## Run it

```bash
mvn spring-boot:run
```

Listens on `:8080`, DB file lives at `./data/weather.mv.db` (created on first
run). Smoke check:

```bash
curl http://localhost:8080/ping
# pong
```

## Endpoints

Ingest a reading (`recordedAt` is optional — omit and the server uses "now" UTC):

```bash
curl -X POST http://localhost:8080/api/v1/readings \
  -H 'Content-Type: application/json' \
  -d '{"sensorId":"s-1","metric":"temperature","value":21.4}'
```

Query aggregates:

```bash
curl 'http://localhost:8080/api/v1/readings/query?metrics=temperature&stat=avg'
```

Query params: `sensorIds` (optional, comma-separated), `metrics` (required),
`stat` (`min|max|sum|avg`, default `avg`), `from` / `to` (ISO-8601, both or
neither — defaults to last 24h, window must be 1–31 days).

Errors come back as RFC 7807 `application/problem+json`.

## Tests

```bash
mvn test
```
