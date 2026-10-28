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

## Run

```bash
java -jar target/calculator.jar
```
Starts the server on `http://localhost:8080`.

## API

| Method | Endpoint              | Description                  |
|--------|-----------------------|-------------------------------|
| GET    | `/api/v1/add`         | Add two numbers               |
| GET    | `/api/v1/subtract`    | Subtract two numbers          |
| GET    | `/api/v1/multiply`    | Multiply two numbers          |
| GET    | `/api/v1/divide`      | Divide two numbers            |
| GET    | `/actuator/health`    | Health check (`{"status":"UP"}`) |

All arithmetic endpoints take two query params, `a` and `b`. Missing/non-numeric
params and divide-by-zero return HTTP 400.

### Example

```bash
curl "http://localhost:8080/api/v1/add?a=2&b=3"
# {"operation":"add","a":2.0,"b":3.0,"result":5.0}
```

---
*Docker, Docker Compose, and Jenkins pipeline instructions will be added here
as those parts of the project are completed.*