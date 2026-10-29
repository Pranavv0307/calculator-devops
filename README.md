# Calculator REST API

A small Java HTTP API that performs basic arithmetic, built as part of the
CSE 816 mini-project. Built with plain Java (`com.sun.net.httpserver.HttpServer`)
and Maven — no external frameworks.

## Build

```bash
./mvnw package
```
Produces `target/calculator.jar`.

## Test

```bash
./mvnw test
```
Runs unit tests (`CalculatorTest`) and an HTTP integration test (`CalculatorServerIntegrationTest`).

## Run (locally, without Docker)

```bash
java -jar target/calculator.jar
```
Starts the server on `http://localhost:8080`.

## Run with Docker (single container)

```bash
docker build -t calculator:local .
docker run -p 8080:8080 calculator:local
```
The image is built in two stages: a `maven:3.9-eclipse-temurin-17` stage compiles
and packages the JAR, and a minimal `eclipse-temurin:17-jre-alpine` stage runs it
as a non-root user. Only the final JAR is copied into the runtime image.

## Run with Docker Compose (full stack)

```bash
docker compose up -d --build
```
This starts two containers on a shared `calc-net` network:
- **calculator** — the app itself, not published to the host directly
- **nginx** — a reverse proxy published on host port 8080, forwarding to the app

The `calculator` service has a health check against `/actuator/health`; nginx
waits for it to report healthy before accepting traffic. Both containers use
`restart: unless-stopped`.

```bash
docker ps                                      # confirm both containers are up/healthy
curl "http://localhost:8080/api/v1/add?a=2&b=3"  # now going through nginx
docker compose logs -f                          # watch logs
docker compose down                             # tear down
```

## API

| Method | Endpoint              | Description                        |
|--------|-----------------------|-------------------------------------|
| GET    | `/api/v1/add`         | Add two numbers                     |
| GET    | `/api/v1/subtract`    | Subtract two numbers                |
| GET    | `/api/v1/multiply`    | Multiply two numbers                |
| GET    | `/api/v1/divide`      | Divide two numbers                  |
| GET    | `/actuator/health`    | Health check (`{"status":"UP"}`)    |

All arithmetic endpoints take two query params, `a` and `b`. Missing/non-numeric
params and divide-by-zero return HTTP 400. Non-GET requests to any endpoint
return HTTP 405.

### Example

```bash
curl "http://localhost:8080/api/v1/add?a=2&b=3"
# {"operation":"add","a":2.0,"b":3.0,"result":5.0}
```

---
*Jenkins pipeline instructions will be added here once the CI/CD setup is complete.*